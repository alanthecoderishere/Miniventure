package com.miniv.rendering;

import com.miniv.core.Camera;
import com.miniv.core.Config;
import com.miniv.core.Player;
import com.miniv.core.Texture;
import com.miniv.world.Chunk;
import com.miniv.world.Voxel;
import com.miniv.world.World;
import org.joml.Matrix4f;

import com.miniv.ui.ChatSystem;
import com.miniv.ui.TextRenderer;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private Shader3D shader;
    private Shader2D shader2D;
    private TextRenderer textRenderer;
    private FrustumCuller frustumCuller;

    private Matrix4f modelMatrix;

    /** One mesh per facing (same layout as local player). */
    private final int[] playerMeshVaos = new int[4];
    private final int[] playerMeshVbos = new int[4];
    private boolean playerMeshesReady = false;

    public void init() {
        shader = new Shader3D();
        shader2D = new Shader2D();
        textRenderer = new TextRenderer();
        frustumCuller = new FrustumCuller();
        textRenderer.init(800, 600);

        modelMatrix = new Matrix4f();
        buildAllPlayerMeshes();
    }

    /** Called when a single block breaks/places — marks the relevant chunk dirty */
    public void rebuildChunk() {
        // No longer pre-builds; chunks are rebuilt lazily via isDirty flag
    }

    public void render(Camera camera, World world, Player player, ChatSystem chat, float skyLight, int screenWidth, int screenHeight) {
        shader.bind();
        Texture.bind();

        shader.setUniform("projection", camera.getProjectionMatrix());
        shader.setUniform("view", camera.getViewMatrix());
        shader.setUniform("skyLight", skyLight);

        // Upload player world position so the fragment shader can detect which
        // blocks lie between the camera and the player
        shader.setUniform("playerPos",
            player.position.x, player.position.y, player.position.z);

        // Update frustum culler once per frame
        Matrix4f projView = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
        frustumCuller.update(projView);
        
        // ── Clean up pruned chunks ──
        while (!world.prunedChunks.isEmpty()) {
            Chunk pruned = world.prunedChunks.poll();
            if (pruned != null) {
                if (pruned.vao >= 0) org.lwjgl.opengl.GL30.glDeleteVertexArrays(pruned.vao);
                if (pruned.vbo >= 0) org.lwjgl.opengl.GL15.glDeleteBuffers(pruned.vbo);
            }
        }

        // ── Render all loaded chunks (isPlayer = 0 → occlusion dithering enabled) ──
        shader.setUniform("isPlayer", 0.0f);
        int H = Config.worldHeight;
        int S = World.CHUNK_SIZE;
        for (Chunk chunk : world.getActiveChunks()) {
            // Frustum cull: skip chunk if its AABB is outside the camera frustum
            float minX = chunk.cx * S;
            float minZ = chunk.cz * S;
            if (!frustumCuller.isBoxVisible(minX, 0, minZ, minX + S, H, minZ + S)) continue;

            if (chunk.isDirty) {
                buildChunkMesh(chunk, world);
                chunk.isDirty = false;
            }
            if (chunk.vao < 0 || chunk.vertexCount == 0) continue;

            modelMatrix.identity().translate(chunk.cx * S, 0, chunk.cz * S);
            shader.setUniform("model", modelMatrix);

            glBindVertexArray(chunk.vao);
            glDrawArrays(GL_TRIANGLES, 0, chunk.vertexCount);
            glBindVertexArray(0);
        }

        // ── Characters: local player, NPCs, remote players (same mesh & textures) ──
        shader.setUniform("isPlayer", 1.0f);

        List<TextRenderer.FloatingText> floatingTexts = new ArrayList<>();
        int[] viewport = new int[]{0, 0, screenWidth, screenHeight};
        org.joml.Vector3f dest = new org.joml.Vector3f();

        drawPlayerBody(player.position.x, player.position.y, player.position.z, player.facing);

        for (com.miniv.core.NPCEntity npc : com.miniv.core.NPCManager.getAll()) {
            drawPlayerBody(npc.position.x, npc.position.y, npc.position.z, npc.facing);
            projView.project(new org.joml.Vector3f(npc.position.x + 0.5f, npc.position.y + 1.2f, npc.position.z + 0.5f), viewport, dest);
            if (dest.z >= 0.0f && dest.z <= 1.0f) {
                floatingTexts.add(new TextRenderer.FloatingText(npc.name, (int) dest.x, screenHeight - (int) dest.y));
            }
        }

        for (com.miniv.core.SupabaseMultiplayer.RemotePlayer rp : com.miniv.core.SupabaseMultiplayer.remotePlayers.values()) {
            int facing = dirToFacing(rp.dir);
            drawPlayerBody((float) rp.renderX, (float) rp.renderY, (float) rp.renderZ, facing);
            projView.project(new org.joml.Vector3f((float) rp.renderX + 0.5f, (float) rp.renderY + 1.2f, (float) rp.renderZ + 0.5f), viewport, dest);
            if (dest.z >= 0.0f && dest.z <= 1.0f) {
                floatingTexts.add(new TextRenderer.FloatingText(rp.name, (int) dest.x, screenHeight - (int) dest.y));
            }
        }

        glBindVertexArray(0);
        shader.unbind();

        // Render UI overlay
        textRenderer.updateTexture(chat, floatingTexts, screenWidth, screenHeight);
        textRenderer.bind();
        shader2D.render();
    }

    private void buildChunkMesh(Chunk chunk, World world) {
        List<Float> vertices = new ArrayList<>();

        int H = Config.worldHeight;
        int S = World.CHUNK_SIZE;

        for (int lx = 0; lx < S; lx++) {
            for (int lz = 0; lz < S; lz++) {
                for (int ly = 0; ly < H; ly++) {
                    byte block = chunk.getBlockLocal(lx, ly, lz);
                    if (block == Voxel.AIR) continue;

                    int gx = chunk.cx * S + lx;
                    int gz = chunk.cz * S + lz;

                    // ── Underground block cull ──────────────────────────────────
                    // If every one of the 6 face-neighbours is a solid (non-air)
                    // block this block contributes zero visible faces. Skip it to
                    // avoid 6 getBlock() calls + all AO sampling for buried voxels.
                    if (world.getBlock(gx, ly + 1, gz) != Voxel.AIR &&
                        world.getBlock(gx, ly - 1, gz) != Voxel.AIR &&
                        world.getBlock(gx - 1, ly,  gz) != Voxel.AIR &&
                        world.getBlock(gx + 1, ly,  gz) != Voxel.AIR &&
                        world.getBlock(gx,     ly, gz + 1) != Voxel.AIR &&
                        world.getBlock(gx,     ly, gz - 1) != Voxel.AIR) {
                        continue;
                    }

                    int topTex = getTopTexture(block);
                    int sideTex = getSideTexture(block);
                    int botTex = getBottomTexture(block);

                    // Top Face (+y)
                    if (world.getBlock(gx, ly + 1, gz) == Voxel.AIR) {
                        float[] ao = getTopFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx, ly+1, lz, ao[0], lx, ly+1, lz+1, ao[1], lx+1, ly+1, lz+1, ao[2], lx+1, ly+1, lz, ao[3], topTex, false);
                    }
                    // Bottom Face (-y)
                    if (world.getBlock(gx, ly - 1, gz) == Voxel.AIR) {
                        float[] ao = getBottomFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx, ly, lz+1, ao[0], lx, ly, lz, ao[1], lx+1, ly, lz, ao[2], lx+1, ly, lz+1, ao[3], botTex, false);
                    }
                    // Left Face (-x)
                    if (world.getBlock(gx - 1, ly, gz) == Voxel.AIR) {
                        float[] ao = getLeftFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx, ly, lz, ao[0], lx, ly, lz+1, ao[1], lx, ly+1, lz+1, ao[2], lx, ly+1, lz, ao[3], sideTex, false);
                    }
                    // Right Face (+x)
                    if (world.getBlock(gx + 1, ly, gz) == Voxel.AIR) {
                        float[] ao = getRightFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx+1, ly, lz+1, ao[0], lx+1, ly, lz, ao[1], lx+1, ly+1, lz, ao[2], lx+1, ly+1, lz+1, ao[3], sideTex, false);
                    }
                    // Front Face (+z)
                    if (world.getBlock(gx, ly, gz + 1) == Voxel.AIR) {
                        float[] ao = getFrontFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx, ly, lz+1, ao[0], lx+1, ly, lz+1, ao[1], lx+1, ly+1, lz+1, ao[2], lx, ly+1, lz+1, ao[3], sideTex, false);
                    }
                    // Back Face (-z)
                    if (world.getBlock(gx, ly, gz - 1) == Voxel.AIR) {
                        float[] ao = getBackFaceAO(world, gx, ly, gz);
                        addQuad(vertices, lx+1, ly, lz, ao[0], lx, ly, lz, ao[1], lx, ly+1, lz, ao[2], lx+1, ly+1, lz, ao[3], sideTex, false);
                    }
                }
            }
        }

        float[] array = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) array[i] = vertices.get(i);
        chunk.vertexCount = array.length / 7;

        if (chunk.vao < 0) {
            chunk.vao = glGenVertexArrays();
            chunk.vbo = glGenBuffers();
        }

        glBindVertexArray(chunk.vao);
        glBindBuffer(GL_ARRAY_BUFFER, chunk.vbo);
        glBufferData(GL_ARRAY_BUFFER, array, GL_DYNAMIC_DRAW);
        setupVertexAttributes();
        glBindVertexArray(0);

        chunk.hasMesh = true;
    }

    private static int dirToFacing(String dir) {
        if ("back".equals(dir)) return 1;
        if ("left".equals(dir)) return 2;
        if ("right".equals(dir)) return 3;
        return 0;
    }

    private void buildAllPlayerMeshes() {
        for (int facing = 0; facing < 4; facing++) {
            playerMeshVaos[facing] = glGenVertexArrays();
            playerMeshVbos[facing] = glGenBuffers();
            uploadPlayerMesh(facing, playerMeshVaos[facing], playerMeshVbos[facing]);
        }
        playerMeshesReady = true;
    }

    private void drawPlayerBody(float x, float y, float z, int facing) {
        if (!playerMeshesReady) buildAllPlayerMeshes();
        int f = facing & 3;
        modelMatrix.identity().translate(x, y, z);
        shader.setUniform("model", modelMatrix);
        glBindVertexArray(playerMeshVaos[f]);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    private void uploadPlayerMesh(int facing, int vao, int vbo) {
        List<Float> vertices = new ArrayList<>();

        int topTex = Texture.getIndex("Player_top");
        int botTex = Texture.getIndex("Player_top");
        int texFront = Texture.getIndex("player");
        int texBack = Texture.getIndex("Player_back");
        int texSide = Texture.getIndex("Player_left_right");

        int faceXPos, faceZPos, faceXNeg, faceZNeg;
        boolean flipXPos = false, flipZPos = false, flipXNeg = false, flipZNeg = false;

        switch (facing) {
            case 0:
            default:
                faceZPos = texFront;
                faceXPos = texSide;
                flipXPos = true;
                faceZNeg = texBack;
                faceXNeg = texSide;
                break;
            case 1:
                faceZPos = texBack;
                faceXPos = texSide;
                faceZNeg = texFront;
                faceXNeg = texSide;
                flipXNeg = true;
                break;
            case 2:
                faceZPos = texSide;
                flipZPos = true;
                faceXPos = texBack;
                faceZNeg = texSide;
                faceXNeg = texFront;
                break;
            case 3:
                faceZPos = texSide;
                faceXPos = texFront;
                faceZNeg = texSide;
                flipZNeg = true;
                faceXNeg = texBack;
                break;
        }

        addQuad(vertices, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, topTex, false);
        addQuad(vertices, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, botTex, false);
        addQuad(vertices, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, faceXNeg, flipXNeg);
        addQuad(vertices, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, faceXPos, flipXPos);
        addQuad(vertices, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, faceZPos, flipZPos);
        addQuad(vertices, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, faceZNeg, flipZNeg);

        float[] array = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) array[i] = vertices.get(i);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, array, GL_STATIC_DRAW);
        setupVertexAttributes();
        glBindVertexArray(0);
    }

    private void setupVertexAttributes() {
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 7 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
    }

    private void addQuad(List<Float> list,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          int tex, boolean flipX) {
        addQuad(list, x0, y0, z0, 1.0f, x1, y1, z1, 1.0f, x2, y2, z2, 1.0f, x3, y3, z3, 1.0f, tex, flipX);
    }

    private void addQuad(List<Float> list,
                          float x0, float y0, float z0, float ao0,
                          float x1, float y1, float z1, float ao1,
                          float x2, float y2, float z2, float ao2,
                          float x3, float y3, float z3, float ao3,
                          int tex, boolean flipX) {
        float u0 = flipX ? 1 : 0;
        float u1 = flipX ? 0 : 1;
        float v0 = 1, v1 = 0;

        if (ao0 + ao2 < ao1 + ao3) {
            list.add(x1); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add((float) tex); list.add(ao1);
            list.add(x2); list.add(y2); list.add(z2); list.add(u1); list.add(v1); list.add((float) tex); list.add(ao2);
            list.add(x3); list.add(y3); list.add(z3); list.add(u0); list.add(v1); list.add((float) tex); list.add(ao3);

            list.add(x3); list.add(y3); list.add(z3); list.add(u0); list.add(v1); list.add((float) tex); list.add(ao3);
            list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add((float) tex); list.add(ao0);
            list.add(x1); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add((float) tex); list.add(ao1);
        } else {
            list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add((float) tex); list.add(ao0);
            list.add(x1); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add((float) tex); list.add(ao1);
            list.add(x2); list.add(y2); list.add(z2); list.add(u1); list.add(v1); list.add((float) tex); list.add(ao2);

            list.add(x2); list.add(y2); list.add(z2); list.add(u1); list.add(v1); list.add((float) tex); list.add(ao2);
            list.add(x3); list.add(y3); list.add(z3); list.add(u0); list.add(v1); list.add((float) tex); list.add(ao3);
            list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add((float) tex); list.add(ao0);
        }
    }

    private float calculateAO(World world, int s1x, int s1y, int s1z, int s2x, int s2y, int s2z, int cx, int cy, int cz) {
        boolean side1 = world.getBlock(s1x, s1y, s1z) != Voxel.AIR;
        boolean side2 = world.getBlock(s2x, s2y, s2z) != Voxel.AIR;
        boolean corner = world.getBlock(cx, cy, cz) != Voxel.AIR;
        
        int ao;
        if (side1 && side2) {
            ao = 0;
        } else {
            int count = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
            ao = 3 - count;
        }
        
        switch (ao) {
            case 0: return 0.3f;
            case 1: return 0.55f;
            case 2: return 0.8f;
            case 3: default: return 1.0f;
        }
    }

    private float[] getTopFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx-1, ly+1, gz, gx, ly+1, gz-1, gx-1, ly+1, gz-1);
        float ao1 = calculateAO(world, gx-1, ly+1, gz, gx, ly+1, gz+1, gx-1, ly+1, gz+1);
        float ao2 = calculateAO(world, gx+1, ly+1, gz, gx, ly+1, gz+1, gx+1, ly+1, gz+1);
        float ao3 = calculateAO(world, gx+1, ly+1, gz, gx, ly+1, gz-1, gx+1, ly+1, gz-1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private float[] getBottomFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx-1, ly-1, gz, gx, ly-1, gz+1, gx-1, ly-1, gz+1);
        float ao1 = calculateAO(world, gx-1, ly-1, gz, gx, ly-1, gz-1, gx-1, ly-1, gz-1);
        float ao2 = calculateAO(world, gx+1, ly-1, gz, gx, ly-1, gz-1, gx+1, ly-1, gz-1);
        float ao3 = calculateAO(world, gx+1, ly-1, gz, gx, ly-1, gz+1, gx+1, ly-1, gz+1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private float[] getLeftFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx-1, ly-1, gz, gx-1, ly, gz-1, gx-1, ly-1, gz-1);
        float ao1 = calculateAO(world, gx-1, ly-1, gz, gx-1, ly, gz+1, gx-1, ly-1, gz+1);
        float ao2 = calculateAO(world, gx-1, ly+1, gz, gx-1, ly, gz+1, gx-1, ly+1, gz+1);
        float ao3 = calculateAO(world, gx-1, ly+1, gz, gx-1, ly, gz-1, gx-1, ly+1, gz-1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private float[] getRightFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx+1, ly-1, gz, gx+1, ly, gz+1, gx+1, ly-1, gz+1);
        float ao1 = calculateAO(world, gx+1, ly-1, gz, gx+1, ly, gz-1, gx+1, ly-1, gz-1);
        float ao2 = calculateAO(world, gx+1, ly+1, gz, gx+1, ly, gz-1, gx+1, ly+1, gz-1);
        float ao3 = calculateAO(world, gx+1, ly+1, gz, gx+1, ly, gz+1, gx+1, ly+1, gz+1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private float[] getFrontFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx-1, ly, gz+1, gx, ly-1, gz+1, gx-1, ly-1, gz+1);
        float ao1 = calculateAO(world, gx+1, ly, gz+1, gx, ly-1, gz+1, gx+1, ly-1, gz+1);
        float ao2 = calculateAO(world, gx+1, ly, gz+1, gx, ly+1, gz+1, gx+1, ly+1, gz+1);
        float ao3 = calculateAO(world, gx-1, ly, gz+1, gx, ly+1, gz+1, gx-1, ly+1, gz+1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private float[] getBackFaceAO(World world, int gx, int ly, int gz) {
        float ao0 = calculateAO(world, gx+1, ly, gz-1, gx, ly-1, gz-1, gx+1, ly-1, gz-1);
        float ao1 = calculateAO(world, gx-1, ly, gz-1, gx, ly-1, gz-1, gx-1, ly-1, gz-1);
        float ao2 = calculateAO(world, gx-1, ly, gz-1, gx, ly+1, gz-1, gx-1, ly+1, gz-1);
        float ao3 = calculateAO(world, gx+1, ly, gz-1, gx, ly+1, gz-1, gx+1, ly+1, gz-1);
        return new float[]{ao0, ao1, ao2, ao3};
    }

    private int getTopTexture(byte block) {
        switch (block) {
            case Voxel.GRASS:  return Texture.getIndex("Grass");
            case Voxel.DIRT:   return Texture.getIndex("Dirt");
            case Voxel.STONE:  return Texture.getIndex("Stone");
            case Voxel.WOOD:   return Texture.getIndex("Wood");
            case Voxel.LEAVES: return Texture.getIndex("Leaves");
            case Voxel.SAND:   return Texture.getIndex("Sand");
            case Voxel.COBBLE: return Texture.getIndex("Stone");
            case Voxel.LOG:    return Texture.getIndex("Wood");
            case Voxel.GRAVEL: return Texture.getIndex("Dirt");
            case Voxel.QUIZ_BLOCK:       return Texture.getIndex("Quiz_block");
            case Voxel.INFO_BLOCK:       return Texture.getIndex("Info_block");
            case Voxel.TELEPORT_BLOCK:   return Texture.getIndex("Teleportal_block");
            case Voxel.ATTENDANCE_BLOCK: return Texture.getIndex("Attendance_block");
            case Voxel.NPC_SPAWNER:      return Texture.getIndex("Plank");
            default: return 0;
        }
    }

    private int getSideTexture(byte block) {
        switch (block) {
            case Voxel.GRASS:  return Texture.getIndex("Grass_side");
            case Voxel.DIRT:   return Texture.getIndex("Dirt");
            case Voxel.STONE:  return Texture.getIndex("Stone");
            case Voxel.WOOD:   return Texture.getIndex("Wood");
            case Voxel.LEAVES: return Texture.getIndex("Leaves");
            case Voxel.SAND:   return Texture.getIndex("Sand");
            case Voxel.COBBLE: return Texture.getIndex("Stone");
            case Voxel.LOG:    return Texture.getIndex("Grass_side");
            case Voxel.GRAVEL: return Texture.getIndex("Dirt");
            case Voxel.QUIZ_BLOCK:       return Texture.getIndex("Quiz_block");
            case Voxel.INFO_BLOCK:       return Texture.getIndex("Info_block");
            case Voxel.TELEPORT_BLOCK:   return Texture.getIndex("Teleportal_block");
            case Voxel.ATTENDANCE_BLOCK: return Texture.getIndex("Attendance_block");
            case Voxel.NPC_SPAWNER:      return Texture.getIndex("Plank");
            default: return 0;
        }
    }

    private int getBottomTexture(byte block) {
        switch (block) {
            case Voxel.GRASS:  return Texture.getIndex("Dirt");
            case Voxel.DIRT:   return Texture.getIndex("Dirt");
            case Voxel.STONE:  return Texture.getIndex("Stone");
            case Voxel.WOOD:   return Texture.getIndex("Wood");
            case Voxel.LEAVES: return Texture.getIndex("Leaves");
            case Voxel.SAND:   return Texture.getIndex("Sand");
            case Voxel.COBBLE: return Texture.getIndex("Stone");
            case Voxel.LOG:    return Texture.getIndex("Wood");
            case Voxel.GRAVEL: return Texture.getIndex("Dirt");
            case Voxel.QUIZ_BLOCK:       return Texture.getIndex("Quiz_block");
            case Voxel.INFO_BLOCK:       return Texture.getIndex("Info_block");
            case Voxel.TELEPORT_BLOCK:   return Texture.getIndex("Teleportal_block");
            case Voxel.ATTENDANCE_BLOCK: return Texture.getIndex("Attendance_block");
            case Voxel.NPC_SPAWNER:      return Texture.getIndex("Plank");
            default: return 0;
        }
    }

    public void cleanup() {
        shader.cleanup();
    }
}
