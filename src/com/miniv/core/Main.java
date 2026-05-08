package com.miniv.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import com.miniv.rendering.Renderer;
import com.miniv.ui.ChatSystem;
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

    public void run() {
        init();
        loop();

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
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (chat.isTyping) {
                    chat.isTyping = false;
                    chat.dirty = true;
                } else {
                    chat.isSettingsOpen = !chat.isSettingsOpen;
                    chat.dirty = true;
                }
            }
            if (action == GLFW_PRESS) {
                if (chat.isSettingsOpen) {
                    if (key == GLFW_KEY_Q) {
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
                    } else if (key == GLFW_KEY_B) {
                        Config.activeBlockIndex = (Config.activeBlockIndex + 1) % Config.placeableBlocks.length;
                        chat.dirty = true;
                    } else if (key == GLFW_KEY_UP) {
                        if (Config.worldHeight < Config.MAX_WORLD_HEIGHT) {
                            Config.worldHeight += 8;
                            if (Config.worldHeight > Config.MAX_WORLD_HEIGHT) Config.worldHeight = Config.MAX_WORLD_HEIGHT;
                            chat.addMessage("World height set to " + Config.worldHeight + ". Restart world to apply.");
                            chat.dirty = true;
                        }
                    } else if (key == GLFW_KEY_DOWN) {
                        if (Config.worldHeight > Config.MIN_WORLD_HEIGHT) {
                            Config.worldHeight -= 8;
                            if (Config.worldHeight < Config.MIN_WORLD_HEIGHT) Config.worldHeight = Config.MIN_WORLD_HEIGHT;
                            chat.addMessage("World height set to " + Config.worldHeight + ". Restart world to apply.");
                            chat.dirty = true;
                        }
                    }
                }
                // Number keys 1-9 always select hotbar slot (even outside settings)
                else if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    int slot = key - GLFW_KEY_1;
                    if (slot < Config.placeableBlocks.length) {
                        Config.activeBlockIndex = slot;
                        chat.dirty = true;
                    }
                }
                else if (key == GLFW_KEY_T && !chat.isTyping) {
                    chat.isTyping = true;
                    chat.currentInput.setLength(0);
                    chat.dirty = true;
                }
                else if ((key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) {
                    if (chat.isTyping) {
                        chat.isTyping = false;
                        if (chat.currentInput.length() > 0) {
                            chat.addMessage(chat.currentInput.toString());
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
            if (action == GLFW_PRESS && !chat.isTyping) {
                try (MemoryStack stack = stackPush()) {
                    DoubleBuffer xpos = stack.mallocDouble(1);
                    DoubleBuffer ypos = stack.mallocDouble(1);
                    glfwGetCursorPos(window, xpos, ypos);

                    // Use WINDOW size (logical pixels) — glfwGetCursorPos returns logical px.
                    // Framebuffer size is 2× on Retina, which would incorrectly halve the NDC range.
                    IntBuffer pWidth  = stack.mallocInt(1);
                    IntBuffer pHeight = stack.mallocInt(1);
                    glfwGetWindowSize(window, pWidth, pHeight);

                    float ndcX = (float)(2.0 * xpos.get(0) / pWidth.get(0)  - 1.0);
                    float ndcY = (float)(1.0 - 2.0 * ypos.get(0) / pHeight.get(0));

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

                    float step = 0.075f;
                    Vector3f currentPos = new Vector3f(rayPos);
                    Vector3f lastAirPos = null;
                    int maxSteps = (int)(7.0f / step); // 7-block max range

                    for (int i = 0; i < maxSteps; i++) {
                        currentPos.add(rayDir.x * step, rayDir.y * step, rayDir.z * step);

                        int bx = (int) Math.floor(currentPos.x);
                        int by = (int) Math.floor(currentPos.y);
                        int bz = (int) Math.floor(currentPos.z);

                        float dist = currentPos.distance(player.position.x + 0.5f, player.position.y + 0.5f, player.position.z + 0.5f);
                        if (dist > 5.5f) break; // Stop when outside 5-block player radius

                        byte block = world.getBlock(bx, by, bz);
                        if (block != Voxel.AIR) {
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                world.setBlock(bx, by, bz, Voxel.AIR);
                            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && lastAirPos != null) {
                                int px = (int) Math.floor(lastAirPos.x);
                                int py = (int) Math.floor(lastAirPos.y);
                                int pz = (int) Math.floor(lastAirPos.z);
                                world.setBlock(px, py, pz, Config.getActiveBlock());
                            }
                            break;
                        } else {
                            lastAirPos = new Vector3f(currentPos);
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
        chat.addMessage("Welcome to MiniVenture!");
        chat.addMessage("ESC=Settings  T=Chat  LMB=Break  RMB=Place");

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
        chat.addMessage("Spawned at Y=" + spawnY);
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
            
            worldTime += delta / Config.dayLengthSeconds;
            if (worldTime > 1.0f) worldTime -= 1.0f;
            
            float light;
            if (worldTime < 0.2f || worldTime > 0.8f) light = 0.2f; 
            else if (worldTime < 0.3f) light = 0.2f + 0.8f * ((worldTime - 0.2f) / 0.1f); 
            else if (worldTime > 0.7f) light = 1.0f - 0.8f * ((worldTime - 0.7f) / 0.1f); 
            else light = 1.0f; 
            
            glClearColor(0.53f * light, 0.81f * light, 0.92f * light, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            world.updateChunks(player.position.x, player.position.z);
            chat.updateTimers();
            player.update(delta, world);
            camera.follow(player.position);

            try (MemoryStack stack = stackPush()) {
                IntBuffer pW = stack.mallocInt(1);
                IntBuffer pH = stack.mallocInt(1);
                glfwGetFramebufferSize(window, pW, pH);
                renderer.render(camera, world, player, chat, pW.get(0), pH.get(0));
            }

            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                glfwSetWindowTitle(window, "MiniVenture | FPS: " + frames);
                frames = 0;
            }
        }
        
        renderer.cleanup();
    }

    public static boolean isKeyPressed(int keyCode) {
        return keys[keyCode];
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        new Main().run();
    }
}
