package com.miniv.rendering;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class Shader2D {
    private int programId;
    private int vao, vbo;

    public Shader2D() {
        programId = glCreateProgram();

        String vertexSource = "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoords;\n" +
            "out vec2 TexCoords;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "    TexCoords = aTexCoords;\n" +
            "}\n";

        String fragmentSource = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "in vec2 TexCoords;\n" +
            "uniform sampler2D tex;\n" +
            "void main() {\n" +
            "    vec4 col = texture(tex, vec2(TexCoords.x, TexCoords.y));\n" +
            "    if(col.a < 0.05) discard;\n" +
            "    FragColor = col;\n" +
            "}\n";

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertexSource); glCompileShader(vs);
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragmentSource); glCompileShader(fs);
        
        glAttachShader(programId, vs); glAttachShader(programId, fs);
        glLinkProgram(programId);

        glDeleteShader(vs); glDeleteShader(fs);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        
        float[] vertices = {
            -1.0f,  1.0f,  0.0f, 0.0f,
            -1.0f, -1.0f,  0.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 1.0f,
             1.0f,  1.0f,  1.0f, 0.0f,
            -1.0f,  1.0f,  0.0f, 0.0f,
        };
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    public void render() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        
        glUseProgram(programId);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glUseProgram(0);
        
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }
}
