package com.miniv.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import com.miniv.rendering.Renderer;
import com.miniv.ui.ChatSystem;
import com.miniv.ui.UIManager;
import com.miniv.ui.HotbarLayout;
import com.miniv.ui.InventoryUI;
import com.miniv.world.PerlinNoise;
import com.miniv.world.Voxel;
import com.miniv.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    private long window;
    private static boolean[] keys = new boolean[GLFW_KEY_LAST];

    private Renderer renderer;
    private Camera camera;
    private Player player;
    private World world;
    private ChatSystem chat;
    private float mpBroadcastTimer = 0f;
    private float mpHeartbeatTimer = 0f;

    public void run() {
        init();
        loop();

        SupabaseMultiplayer.disconnect(); // tutup koneksi WebSocket sebelum exit

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required on Mac
        glfwWindowHint(GLFW_DEPTH_BITS, 24);

        window = glfwCreateWindow(1024, 768, "MiniVenture", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (UIManager.hasActiveUI()) {
                UIManager.getActiveUI().handleKey(key, action);
                return;
            }

            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    if (chat.isTyping) {
                        chat.isTyping = false;
                        chat.dirty = true;
                    } else {
                        chat.isSettingsOpen = !chat.isSettingsOpen;
                        chat.dirty = true;
                    }
                } else if (key == GLFW_KEY_F5 && !chat.isTyping) {
                    SupabaseMultiplayer.remotePlayers.clear();
                    SupabaseMultiplayer.requestWorldSync();
                    chat.addSystemMessage("Refreshing players...");
                    chat.dirty = true;
                }
                
                if (chat.isSettingsOpen) {
                    if (key == GLFW_KEY_F) {
                        Config.fpsCapIndex = (Config.fpsCapIndex + 1) % Config.fpsCaps.length;
                        glfwSwapInterval(Config.getFpsCap() == -1 ? 1 : 0);
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_Q) {
                        Config.shadeQuality = (Config.shadeQuality + 1) % 5;
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_R) {
                        Config.renderDistance = (Config.renderDistance + 1) % 3;
                        // Clamp zoom to new max
                        if (Config.zoom > Config.getMaxZoom()) {
                            Config.zoom = Config.getMaxZoom();
                            try (MemoryStack stack = stackPush()) {
                                IntBuffer pW = stack.mallocInt(1);
                                IntBuffer pH = stack.mallocInt(1);
                                glfwGetFramebufferSize(window, pW, pH);
                                camera.setZoom(Config.zoom, pW.get(0), pH.get(0));
                            }
                        }
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_P) {
                        Config.progressiveLoading = !Config.progressiveLoading;
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_L) {
                        int step = Math.round(Config.brightness * 4.0f);
                        step = (step + 1) % 5;
                        Config.brightness = step / 4.0f;
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_B) {
                        Config.activeBlockIndex = (Config.activeBlockIndex + 1) % Config.placeableBlocks.length;
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_UP) {
                        if (Config.worldHeight < Config.MAX_WORLD_HEIGHT) {
                            Config.worldHeight += 8;
                            if (Config.worldHeight > Config.MAX_WORLD_HEIGHT) Config.worldHeight = Config.MAX_WORLD_HEIGHT;
                            chat.addSystemMessage("World height set to " + Config.worldHeight + ". Restart game to apply.");
                            chat.dirty = true;
                        }
                    } else if (key == GLFW_KEY_DOWN) {
                        if (Config.worldHeight > Config.MIN_WORLD_HEIGHT) {
                            Config.worldHeight -= 8;
                            if (Config.worldHeight < Config.MIN_WORLD_HEIGHT) Config.worldHeight = Config.MIN_WORLD_HEIGHT;
                            chat.addSystemMessage("World height set to " + Config.worldHeight + ". Restart game to apply.");
                            chat.dirty = true;
                        }
                    }
                }
                // Number keys 1-9 select hotbar slot (blocks beyond 9 are assigned via E inventory)
                else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    int slot = key - GLFW_KEY_1;
                    if (slot < Config.HOTBAR_SIZE) {
                        Config.activeBlockIndex = slot;
                        chat.dirty = true;
                    }
                }
                else if (key == GLFW_KEY_T && !chat.isTyping) {
                    chat.isTyping = true;
                    chat.currentInput.setLength(0);
                    chat.dirty = true;
                }
                else if (key == GLFW_KEY_H && !chat.isTyping) {
                    chat.toggleHelp();
                }
                else if (key == GLFW_KEY_E && !chat.isTyping) {
                    UIManager.setActiveUI(new InventoryUI());
                }
                else if (key == GLFW_KEY_V && !chat.isTyping) {
                    // Rotate camera on the currently-active axis
                    String result = camera.rotate();
                    chat.addSystemMessage("[Cam] Rotated: " + result);
                    chat.dirty = true;
                }
                else if (key == GLFW_KEY_X && !chat.isTyping) {
                    // Cycle which axis V rotates
                    String axis = camera.cycleAxis();
                    chat.addSystemMessage("[Cam] Rotate axis: " + axis);
                    chat.dirty = true;
                }
                else if ((key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) {
                    if (chat.isTyping) {
                        chat.isTyping = false;
                        if (chat.currentInput.length() > 0) {
                            String msg = chat.currentInput.toString();
                            chat.addPlayerMessage(SupabaseMultiplayer.localName + ": " + msg);
                            SupabaseMultiplayer.broadcastChat(msg);
                        }
                        chat.dirty = true;
                    }
                }
                else if (key == GLFW_KEY_BACKSPACE && chat.isTyping) {
                    if (chat.currentInput.length() > 0) {
                        chat.currentInput.setLength(chat.currentInput.length() - 1);
                        chat.dirty = true;
                    }
                }
            }
            if (key >= 0 && key < GLFW_KEY_LAST) {
                if (!chat.isTyping) {
                    if (action == GLFW_PRESS) keys[key] = true;
                    else if (action == GLFW_RELEASE) keys[key] = false;
                } else {
                    keys[GLFW_KEY_W] = false; keys[GLFW_KEY_A] = false;
                    keys[GLFW_KEY_S] = false; keys[GLFW_KEY_D] = false;
                    keys[GLFW_KEY_SPACE] = false;
                }
            }
        });

        glfwSetCharCallback(window, (window, codepoint) -> {
            if (UIManager.hasActiveUI()) {
                UIManager.getActiveUI().handleChar(codepoint);
                return;
            }

            if (chat.isTyping) {
                if (chat.currentInput.length() == 0 && (codepoint == 't' || codepoint == 'T')) {
                    return; // Ignore the 't' that opened the chat
                }
                chat.currentInput.appendCodePoint(codepoint);
                chat.dirty = true;
            }
        });

        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            glViewport(0, 0, width, height);
            if (camera != null) camera.updateProjectionMatrix(width, height);
        });

        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            if (camera != null && keys[GLFW_KEY_C]) {
                try (MemoryStack stack = stackPush()) {
                    IntBuffer pWidth = stack.mallocInt(1);
                    IntBuffer pHeight = stack.mallocInt(1);
                    glfwGetFramebufferSize(window, pWidth, pHeight);
                    camera.zoom((float) yoffset, pWidth.get(0), pHeight.get(0));
                }
            }
        });

        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                try (MemoryStack stack = stackPush()) {
                    DoubleBuffer xpos = stack.mallocDouble(1);
                    DoubleBuffer ypos = stack.mallocDouble(1);
                    glfwGetCursorPos(window, xpos, ypos);

                    IntBuffer winW = stack.mallocInt(1);
                    IntBuffer winH = stack.mallocInt(1);
                    IntBuffer fbW  = stack.mallocInt(1);
                    IntBuffer fbH  = stack.mallocInt(1);
                    glfwGetWindowSize(window, winW, winH);
                    glfwGetFramebufferSize(window, fbW, fbH);

                    if (UIManager.hasActiveUI()) {
                        // UI is drawn at framebuffer resolution (Retina = 2x window size)
                        double scaleX = fbW.get(0) / (double) Math.max(1, winW.get(0));
                        double scaleY = fbH.get(0) / (double) Math.max(1, winH.get(0));
                        int mx = (int) (xpos.get(0) * scaleX);
                        int my = (int) (ypos.get(0) * scaleY);
                        UIManager.getActiveUI().handleClick(mx, my, button, fbW.get(0), fbH.get(0));
                        return; // Block raycast
                    }

                    IntBuffer pWidth = winW;
                    IntBuffer pHeight = winH;

                    if (chat.isTyping) return; // Don't raycast if typing

                    float ndcX = (float)(2.0 * xpos.get(0) / pWidth.get(0)  - 1.0);
                    float ndcY = (float)(1.0 - 2.0 * ypos.get(0) / pHeight.get(0));

                    boolean blockHandled = false;

                    // Build inverse combined matrix: first proj*view, then invert
                    Matrix4f projView = new Matrix4f(camera.getProjectionMatrix())
                            .mul(camera.getViewMatrix());
                    Matrix4f invProjView = projView.invert(new Matrix4f());

                    // Unproject near and far clip-space points into world space (column-vector transform)
                    Vector4f nearVec = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
                    Vector4f farVec  = new Vector4f(ndcX, ndcY,  1.0f, 1.0f);
                    invProjView.transform(nearVec);
                    invProjView.transform(farVec);
                    // Perspective divide
                    if (nearVec.w != 0) { nearVec.x /= nearVec.w; nearVec.y /= nearVec.w; nearVec.z /= nearVec.w; }
                    if (farVec.w  != 0) { farVec.x  /= farVec.w;  farVec.y  /= farVec.w;  farVec.z  /= farVec.w;  }

                    Vector3f rayPos = new Vector3f(nearVec.x, nearVec.y, nearVec.z);
                    Vector3f rayDir = new Vector3f(farVec.x - nearVec.x, farVec.y - nearVec.y, farVec.z - nearVec.z).normalize();

                    // Shift start point relative to player center
                    Vector3f center = new Vector3f(player.position.x + 0.5f, player.position.y + 0.5f, player.position.z + 0.5f);
                    Vector3f toPlayer = new Vector3f(center).sub(rayPos);
                    float t = toPlayer.dot(rayDir);
                    rayPos.add(new Vector3f(rayDir).mul(t - 10.0f)); // Start 10 units before player center

                    float step = 0.05f;
                    Vector3f currentPos = new Vector3f(rayPos);
                    Vector3f lastAirPos = new Vector3f(rayPos);
                    int maxSteps = (int)(16.0f / step); // Traces 16 units total (covers 10 before + 5.5 after player)

                    for (int i = 0; i < maxSteps; i++) {
                        currentPos.add(rayDir.x * step, rayDir.y * step, rayDir.z * step);

                        int bx = (int) Math.floor(currentPos.x);
                        int by = (int) Math.floor(currentPos.y);
                        int bz = (int) Math.floor(currentPos.z);

                        byte block = world.getBlock(bx, by, bz);
                        if (block != Voxel.AIR) {
                            if (currentPos.distance(center) > 5.5f) {
                                block = Voxel.AIR; // Treat foreground occlusion block as air
                            }
                        }

                        if (block != Voxel.AIR) {
                            // Hit a solid block. Check if it's within player's reach.
                            float dist = currentPos.distance(center);
                            if (dist <= 5.5f) {
                                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                    if (NPCManager.removeNear(currentPos.x, currentPos.y, currentPos.z)) {
                                        blockHandled = true;
                                        break;
                                    }
                                    if (block == Voxel.QUIZ_BLOCK) QuizBlockManager.removeQuiz(bx, by, bz);
                                    if (block == Voxel.INFO_BLOCK) InfoBlockManager.remove(bx, by, bz);
                                    if (block == Voxel.TELEPORT_BLOCK) TeleportBlockManager.remove(bx, by, bz);
                                    if (block == Voxel.NPC_SPAWNER) NPCManager.removeAtBlock(bx, by, bz);

                                    world.setBlock(bx, by, bz, Voxel.AIR);
                                    SupabaseMultiplayer.broadcastBlock(bx, by, bz, Voxel.AIR);
                                } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                                    if (block == Voxel.QUIZ_BLOCK) { QuizBlockManager.interact(bx, by, bz); blockHandled = true; break; }
                                    if (block == Voxel.INFO_BLOCK) {
                                        if (isKeyPressed(GLFW_KEY_LEFT_SHIFT) || isKeyPressed(GLFW_KEY_RIGHT_SHIFT)) {
                                            InfoBlockManager.view(bx, by, bz);
                                        } else {
                                            InfoBlockManager.edit(bx, by, bz);
                                        }
                                        blockHandled = true; break;
                                    }
                                    if (block == Voxel.TELEPORT_BLOCK) { TeleportBlockManager.interact(bx, by, bz, player); blockHandled = true; break; }
                                    if (block == Voxel.ATTENDANCE_BLOCK) { AttendanceBlockManager.interact(chat); blockHandled = true; break; }

                                    int px = (int) Math.floor(lastAirPos.x);
                                    int py = (int) Math.floor(lastAirPos.y);
                                    int pz = (int) Math.floor(lastAirPos.z);

                                    // AABB collision check with player
                                    float pMinX = player.position.x + 0.5f - 0.98f / 2.0f;
                                    float pMaxX = player.position.x + 0.5f + 0.98f / 2.0f;
                                    float pMinY = player.position.y;
                                    float pMaxY = player.position.y + 0.98f;
                                    float pMinZ = player.position.z + 0.5f - 0.98f / 2.0f;
                                    float pMaxZ = player.position.z + 0.5f + 0.98f / 2.0f;

                                    boolean intersects = (px + 1.0f > pMinX && px < pMaxX) &&
                                                         (py + 1.0f > pMinY && py < pMaxY) &&
                                                         (pz + 1.0f > pMinZ && pz < pMaxZ);

                                    if (!intersects) {
                                        byte placed = Config.getActiveBlock();
                                        if (placed == Voxel.NPC_SPAWNER) {
                                            // Spawn NPC only — no pedestal block (same 1-block height as player)
                                            NPCManager.spawnAtBlock(px, py, pz);
                                        } else {
                                            world.setBlock(px, py, pz, placed);
                                            SupabaseMultiplayer.broadcastBlock(px, py, pz, placed);
                                        }
                                    }
                                }
                            }
                            break; // Stop raycast immediately when hitting any solid block
                        }
                        
                        lastAirPos.set(currentPos);
                    }

                    if (button == GLFW_MOUSE_BUTTON_LEFT && !blockHandled) {
                        float cx = player.position.x + 0.5f;
                        float cy = player.position.y + 0.5f;
                        float cz = player.position.z + 0.5f;
                        if (NPCManager.removeNear(cx, cy, cz)) return;
                    }

                    if (button == GLFW_MOUSE_BUTTON_RIGHT && !blockHandled) {
                        float cx = player.position.x + 0.5f;
                        float cy = player.position.y + 0.5f;
                        float cz = player.position.z + 0.5f;
                        boolean shift = isKeyPressed(GLFW_KEY_LEFT_SHIFT) || isKeyPressed(GLFW_KEY_RIGHT_SHIFT);
                        if (shift) {
                            if (NPCManager.tryEdit(cx, cy, cz)) return;
                        } else if (NPCManager.tryInteract(cx, cy, cz)) {
                            return;
                        }
                    }
                }
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // Vsync
        glfwShowWindow(window);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.53f, 0.81f, 0.92f, 1.0f);

        camera = new Camera();
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pWidth, pHeight);
            camera.updateProjectionMatrix(pWidth.get(0), pHeight.get(0));
        }

        Texture.init();
        renderer = new Renderer();
        renderer.init();

        // Init seed-based noise
        PerlinNoise.setSeed(Config.worldSeed);

        world = new World();

        chat = new ChatSystem();
        chat.addGuideMessage("Welcome to MiniStudy!");
        chat.addGuideMessage("Walk: W A S D  |  Jump: Space  |  Look: move mouse");
        chat.addGuideMessage("Camera: V = rotate  |  X = change axis  |  C + scroll = zoom");
        chat.addGuideMessage("Break a block: Left-click  |  Place a block: Right-click");
        chat.addGuideMessage("Hotbar: keys 1-9  |  More blocks: E  |  Chat: T");
        chat.addGuideMessage("Help: H  |  Settings: ESC (Q/L/R/P/B, Up/Down inside)");
        chat.addGuideMessage("Special blocks: Quiz, Info, Teleport, Attendance, NPC — Right-click");
        chat.addGuideMessage("NPC: Shift+right-click to edit, ENTER saves & locks");
        chat.addSystemMessage("Tip: Press H anytime to show or hide the controls panel.");

        player = new Player();
        // Initial chunk load so we can find the actual surface
        world.updateChunks(8.5f, 8.5f);
        // Scan down from max height to find the surface block
        int spawnY = Config.worldHeight - 1;
        for (int y = Config.worldHeight - 1; y >= 1; y--) {
            if (world.getBlock(8, y, 8) != com.miniv.world.Voxel.AIR) {
                spawnY = y + 1; // Stand on top of the surface block
                break;
            }
        }
        player.position.set(8.5f, spawnY, 8.5f);
        chat.addSystemMessage("You spawned here. Explore and build with your class!");

        NPCManager.initDefaults(player, chat, world);

        // ── MULTIPLAYER: Connect ke Supabase Realtime ─────────────────────────
        String mpId   = java.util.UUID.randomUUID().toString().substring(0, 8);
        int randNum = new java.util.Random().nextInt(100) + 1;
        String mpName = String.format("Player_%03d", randNum);
        SupabaseMultiplayer.connectAsync(mpId, mpName);
        chat.addSystemMessage("Connecting to online class...");
        
        QuizBlockManager.fetchQuizzesFromDB();
    }

    private void loop() {
        long lastTime = System.nanoTime();
        double timer = System.currentTimeMillis();
        int frames = 0;
        float worldTime = 0.5f;

        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float delta = (float) ((now - lastTime) / 1000000000.0);
            if (delta > 0.05f) delta = 0.05f; 
            lastTime = now;
            
            long dayMillis = (long) (Config.dayLengthSeconds * 1000L);
            worldTime = (System.currentTimeMillis() % dayMillis) / (float) dayMillis;
            
            float light;
            if (worldTime < 0.2f || worldTime > 0.8f) light = 0.2f; 
            else if (worldTime < 0.3f) light = 0.2f + 0.8f * ((worldTime - 0.2f) / 0.1f); 
            else if (worldTime > 0.7f) light = 1.0f - 0.8f * ((worldTime - 0.7f) / 0.1f); 
            else light = 1.0f;

            light = Config.applyBrightness(light);

            glClearColor(0.53f * light, 0.81f * light, 0.92f * light, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            world.updateChunks(player.position.x, player.position.z);
            chat.updateTimers();
            player.update(delta, world);
            NPCManager.updateFacing(player);
            TeleportBlockManager.checkStep(player, world);
            camera.follow(player.position);

            if (SupabaseMultiplayer.isConnected()) {
                SupabaseMultiplayer.requestWorldSync();
            }
            SupabaseMultiplayer.pruneStalePlayers();

            // ── MULTIPLAYER: Update received blocks ───────────────────────────
            while (!SupabaseMultiplayer.pendingBlocks.isEmpty()) {
                SupabaseMultiplayer.BlockUpdate bu = SupabaseMultiplayer.pendingBlocks.poll();
                world.setBlock(bu.x, bu.y, bu.z, bu.b);
                if (bu.b == Voxel.AIR) {
                    NPCManager.removeAtBlock(bu.x, bu.y, bu.z);
                }
            }

            // ── MULTIPLAYER: Join/leave notices ───────────────────────────────
            while (!SupabaseMultiplayer.pendingNotifications.isEmpty()) {
                chat.addSystemMessage(SupabaseMultiplayer.pendingNotifications.poll());
                chat.dirty = true;
            }

            // ── MULTIPLAYER: Update received chats ────────────────────────────
            while (!SupabaseMultiplayer.pendingChats.isEmpty()) {
                chat.addPlayerMessage(SupabaseMultiplayer.pendingChats.poll());
                chat.dirty = true;
            }

            // ── MULTIPLAYER: Broadcast posisi (throttle 20x/detik & heartbeat 3s) ────────────
            mpBroadcastTimer += delta;
            mpHeartbeatTimer += delta;
            boolean moving = isKeyPressed(GLFW_KEY_W) || isKeyPressed(GLFW_KEY_S)
                           || isKeyPressed(GLFW_KEY_A) || isKeyPressed(GLFW_KEY_D);
                           
            if (mpBroadcastTimer >= 0.05f) {
                if (moving || mpHeartbeatTimer >= 3.0f) {
                    mpBroadcastTimer = 0f;
                    if (moving) mpHeartbeatTimer = 0f;
                    String[] dirs   = {"front", "back", "left", "right"};
                    String   mpDir  = dirs[Math.min(player.facing, 3)];
                    SupabaseMultiplayer.broadcastPosition(
                        player.position.x, player.position.y, player.position.z,
                        mpDir, moving ? "walk" : "idle"
                    );
                }
            }
            
            SupabaseMultiplayer.updatePlayers(delta);

            try (MemoryStack stack = stackPush()) {
                IntBuffer pW = stack.mallocInt(1);
                IntBuffer pH = stack.mallocInt(1);
                glfwGetFramebufferSize(window, pW, pH);
                updateUIMouse(pW.get(0), pH.get(0));
                renderer.render(camera, world, player, chat, light, pW.get(0), pH.get(0));
            }

            glfwSwapBuffers(window);
            glfwPollEvents();

            if (Config.getFpsCap() > 0) {
                long frameTime = System.nanoTime() - now;
                long targetTime = 1000000000L / Config.getFpsCap();
                if (frameTime < targetTime) {
                    try {
                        Thread.sleep((targetTime - frameTime) / 1000000L);
                    } catch (InterruptedException e) {}
                }
            }

            frames++;
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                glfwSetWindowTitle(window, "MiniVenture | FPS: " + frames);
                frames = 0;
            }
        }
        
        renderer.cleanup();
    }

    private void updateUIMouse(int fbW, int fbH) {
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer xpos = stack.mallocDouble(1);
            DoubleBuffer ypos = stack.mallocDouble(1);
            glfwGetCursorPos(window, xpos, ypos);

            IntBuffer winW = stack.mallocInt(1);
            IntBuffer winH = stack.mallocInt(1);
            glfwGetWindowSize(window, winW, winH);

            double scaleX = fbW / (double) Math.max(1, winW.get(0));
            double scaleY = fbH / (double) Math.max(1, winH.get(0));
            int mx = (int) (xpos.get(0) * scaleX);
            int my = (int) (ypos.get(0) * scaleY);
            UIManager.updateMouse(mx, my);

            int hover = -1;
            if (!chat.isSettingsOpen && !chat.isTyping) {
                if (UIManager.getActiveUI() instanceof InventoryUI) {
                    hover = InventoryUI.hitTest(mx, my, fbW, fbH);
                } else if (!UIManager.hasActiveUI()) {
                    hover = HotbarLayout.hitTest(mx, my, fbW, fbH);
                }
            }
            UIManager.setHoveredBlock(hover);
        }
    }

    public static boolean isKeyPressed(int keyCode) {
        return keys[keyCode];
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        new Main().run();
    }
}
