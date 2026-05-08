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

    private Matrix4f modelMatrix;

    private int playerVao, playerVbo;

    public void init() {
        shader = new Shader3D();
        shader2D = new Shader2D();
        textRenderer = new TextRenderer();
        textRenderer.init(800, 600);

        modelMatrix = new Matrix4f();

        playerVao = glGenVertexArrays();
        playerVbo = glGenBuffers();
    }

    /** Called when a single block breaks/places — marks the relevant chunk dirty */
    public void rebuildChunk() {
        // No longer pre-builds; chunks are rebuilt lazily via isDirty flag
    }

    public void render(Camera camera, World world, Player player, ChatSystem chat, int screenWidth, int screenHeight) {
        shader.bind();
        Texture.bind();

        shader.setUniform("projection", camera.getProjectionMatrix());
        shader.setUniform("view", camera.getViewMatrix());

        // Render all loaded chunks
        for (Chunk chunk : world.getActiveChunks()) {
            if (chunk.isDirty) {
                buildChunkMesh(chunk, world);
                chunk.isDirty = false;
            }
            if (chunk.vao < 0 || chunk.vertexCount == 0) continue;

            modelMatrix.identity().translate(chunk.cx * World.CHUNK_SIZE, 0, chunk.cz * World.CHUNK_SIZE);
            shader.setUniform("model", modelMatrix);

            glBindVertexArray(chunk.vao);
            glDrawArrays(GL_TRIANGLES, 0, chunk.vertexCount);
            glBindVertexArray(0);
        }

        // Render Player
        buildPlayerMesh(player);
        modelMatrix.identity().translate(player.position.x, player.position.y, player.position.z);
        shader.setUniform("model", modelMatrix);
        glBindVertexArray(playerVao);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        shader.unbind();

        // Render UI overlay
        textRenderer.updateTexture(chat, screenWidth, screenHeight);
        textRenderer.bind();
        shader2D.render();
    }

    private void buildChunkMesh(Chunk chunk, World world) {
        List<Float> vertices = new ArrayList<>();

        int H = Config.worldHeight;
        int S = World.CHUNK_SIZE;

        for (int lx = 0; lx < S; lx++) {
            for (int ly = 0; ly < H; ly++) {
                for (int lz = 0; lz < S; lz++) {
                    byte block = chunk.getBlockLocal(lx, ly, lz);
                    if (block == Voxel.AIR) continue;

                    int gx = chunk.cx * S + lx;
                    int gz = chunk.cz * S + lz;

                    int topTex = getTopTexture(block);
                    int sideTex = getSideTexture(block);
                    int botTex = getBottomTexture(block);

                    // Top Face (+y)
                    if (world.getBlock(gx, ly + 1, gz) == Voxel.AIR)
                        addQuad(vertices, lx, ly+1, lz, lx, ly+1, lz+1, lx+1, ly+1, lz+1, lx+1, ly+1, lz, topTex, false);
                    // Bottom Face (-y)
                    if (world.getBlock(gx, ly - 1, gz) == Voxel.AIR)
                        addQuad(vertices, lx, ly, lz+1, lx, ly, lz, lx+1, ly, lz, lx+1, ly, lz+1, botTex, false);
                    // Left Face (-x)
                    if (world.getBlock(gx - 1, ly, gz) == Voxel.AIR)
                        addQuad(vertices, lx, ly, lz, lx, ly, lz+1, lx, ly+1, lz+1, lx, ly+1, lz, sideTex, false);
                    // Right Face (+x)
                    if (world.getBlock(gx + 1, ly, gz) == Voxel.AIR)
                        addQuad(vertices, lx+1, ly, lz+1, lx+1, ly, lz, lx+1, ly+1, lz, lx+1, ly+1, lz+1, sideTex, false);
                    // Front Face (+z)
                    if (world.getBlock(gx, ly, gz + 1) == Voxel.AIR)
                        addQuad(vertices, lx, ly, lz+1, lx+1, ly, lz+1, lx+1, ly+1, lz+1, lx, ly+1, lz+1, sideTex, false);
                    // Back Face (-z)
                    if (world.getBlock(gx, ly, gz - 1) == Voxel.AIR)
                        addQuad(vertices, lx+1, ly, lz, lx, ly, lz, lx, ly+1, lz, lx+1, ly+1, lz, sideTex, false);
                }
            }
        }

        float[] array = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) array[i] = vertices.get(i);
        chunk.vertexCount = array.length / 6;

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

    private void buildPlayerMesh(Player player) {
        List<Float> vertices = new ArrayList<>();

        int topTex = Texture.getIndex("Player_top");
        int botTex  = Texture.getIndex("Player_top");
        int texFront = Texture.getIndex("player");
        int texBack  = Texture.getIndex("Player_back");
        int texSide  = Texture.getIndex("Player_left_right");

        int faceXPos, faceZPos, faceXNeg, faceZNeg;
        boolean flipXPos = false, flipZPos = false, flipXNeg = false, flipZNeg = false;

        switch (player.facing) {
            case 0: default:
                faceZPos = texFront; faceXPos = texSide; flipXPos = true;
                faceZNeg = texBack;  faceXNeg = texSide;
                break;
            case 1:
                faceZPos = texBack;  faceXPos = texSide;
                faceZNeg = texFront; faceXNeg = texSide; flipXNeg = true;
                break;
            case 2:
                faceZPos = texSide; flipZPos = true; faceXPos = texBack;
                faceZNeg = texSide;                  faceXNeg = texFront;
                break;
            case 3:
                faceZPos = texSide;                  faceXPos = texFront;
                faceZNeg = texSide; flipZNeg = true; faceXNeg = texBack;
                break;
        }

        addQuad(vertices, 0,1,0, 0,1,1, 1,1,1, 1,1,0, topTex, false);
        addQuad(vertices, 0,0,1, 0,0,0, 1,0,0, 1,0,1, botTex, false);
        addQuad(vertices, 0,0,0, 0,0,1, 0,1,1, 0,1,0, faceXNeg, flipXNeg);
        addQuad(vertices, 1,0,1, 1,0,0, 1,1,0, 1,1,1, faceXPos, flipXPos);
        addQuad(vertices, 0,0,1, 1,0,1, 1,1,1, 0,1,1, faceZPos, flipZPos);
        addQuad(vertices, 1,0,0, 0,0,0, 0,1,0, 1,1,0, faceZNeg, flipZNeg);

        float[] array = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) array[i] = vertices.get(i);

        glBindVertexArray(playerVao);
        glBindBuffer(GL_ARRAY_BUFFER, playerVbo);
        glBufferData(GL_ARRAY_BUFFER, array, GL_DYNAMIC_DRAW);
        setupVertexAttributes();
        glBindVertexArray(0);
    }

    private void setupVertexAttributes() {
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
    }

    private void addQuad(List<Float> list,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          int tex, boolean flipX) {
        float u0 = flipX ? 1 : 0;
        float u1 = flipX ? 0 : 1;
        float v0 = 1, v1 = 0;
        list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add((float) tex);
        list.add(x1); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add((float) tex);
        list.add(x2); list.add(y2); list.add(z2); list.add(u1); list.add(v1); list.add((float) tex);
        list.add(x2); list.add(y2); list.add(z2); list.add(u1); list.add(v1); list.add((float) tex);
        list.add(x3); list.add(y3); list.add(z3); list.add(u0); list.add(v1); list.add((float) tex);
        list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add((float) tex);
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
            case Voxel.LOG:    return Texture.getIndex("Grass_side"); // bark on sides
            case Voxel.GRAVEL: return Texture.getIndex("Dirt");
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
            default: return 0;
        }
    }

    public void cleanup() {
        shader.cleanup();
    }
}
