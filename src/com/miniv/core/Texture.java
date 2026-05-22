package com.miniv.core;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class Texture {
    private static int textureId;
    private static Map<String, Integer> textureIndices = new HashMap<>();
    
    private static final String[] TEXTURE_FILES = {
        "Dirt.png", "Grass.png", "Grass_side.png",
        "Sand.png", "Stone.png", "Wood.png",
        "Leaves.png", "Plank.png", "Water.png",
        "player.png", "Player_back.png",
        "Player_left_right.png", "Player_top.png",
        "Quiz_block.png", "Info_block.png",
        "Teleportal_block.png", "Attendance_block.png"
    };

    public static void init() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);

        // Textures are 4x4
        int width = 4;
        int height = 4;
        int layerCount = TEXTURE_FILES.length;

        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, layerCount, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        stbi_set_flip_vertically_on_load(false);

        for (int i = 0; i < TEXTURE_FILES.length; i++) {
            String file = TEXTURE_FILES[i];
            textureIndices.put(file.replace(".png", ""), i); // e.g., "Grass" -> 1

            try (MemoryStack stack = stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);
                
                String path = "assets/textures/" + file;
                ByteBuffer image = stbi_load(path, w, h, comp, 4);

                if (image != null) {
                    glPixelStorei(GL_UNPACK_ROW_LENGTH, w.get(0));
                    glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, Math.min(w.get(0), width), Math.min(h.get(0), height), 1, GL_RGBA, GL_UNSIGNED_BYTE, image);
                    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                    stbi_image_free(image);
                } else {
                    System.err.println("Failed to load texture: " + path);
                }
            }
        }
    }

    public static void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
    }

    public static int getIndex(String name) {
        return textureIndices.getOrDefault(name, 0);
    }
}
