package com.miniv.rendering;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;

public class Shader3D {
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    public Shader3D() {
        programId = glCreateProgram();

        String vertexSource = "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec3 aTexCoords;\n" +
            "out vec3 TexCoords;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 model;\n" +
            "void main() {\n" +
            "    gl_Position = projection * view * model * vec4(aPos, 1.0);\n" +
            "    TexCoords = aTexCoords;\n" +
            "}\n";

        String fragmentSource = "#version 330 core\n" +
            "out vec4 FragColor;\n" +
            "in vec3 TexCoords;\n" +
            "uniform sampler2DArray textureArray;\n" +
            "void main() {\n" +
            "    FragColor = texture(textureArray, TexCoords);\n" +
            "    if(FragColor.a < 0.1) discard;\n" +
            "}\n";

        vertexShaderId = createShader(vertexSource, GL_VERTEX_SHADER);
        fragmentShaderId = createShader(fragmentSource, GL_FRAGMENT_SHADER);

        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Error linking shader code: " + glGetProgramInfoLog(programId, 1024));
        }

        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    private int createShader(String shaderCode, int shaderType) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new RuntimeException("Error creating shader.");
        }
        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
             throw new RuntimeException("Error compiling Shader code: " + glGetShaderInfoLog(shaderId, 1024));
        }
        return shaderId;
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }

    public void setUniform(String uniformName, Matrix4f value) {
        int location = glGetUniformLocation(programId, uniformName);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            value.get(buffer);
            glUniformMatrix4fv(location, false, buffer);
        }
    }
}
