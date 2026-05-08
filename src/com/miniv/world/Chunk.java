package com.miniv.world;

import com.miniv.core.Config;

public class Chunk {
    public final int cx, cz;
    private byte[][][] blocks;
    
    public int vao = -1;
    public int vbo = -1;
    public int vertexCount = 0;
    public boolean isDirty = true;
    public boolean hasMesh = false; // to clean up later

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
        blocks = new byte[World.CHUNK_SIZE][Config.worldHeight][World.CHUNK_SIZE];
    }

    public void setBlockLocal(int lx, int ly, int lz, byte type) {
        if (lx < 0 || lx >= World.CHUNK_SIZE || lz < 0 || lz >= World.CHUNK_SIZE || ly < 0 || ly >= Config.worldHeight) return;
        blocks[lx][ly][lz] = type;
        isDirty = true;
    }

    public byte getBlockLocal(int lx, int ly, int lz) {
        if (lx < 0 || lx >= World.CHUNK_SIZE || lz < 0 || lz >= World.CHUNK_SIZE || ly < 0 || ly >= Config.worldHeight) return Voxel.AIR;
        return blocks[lx][ly][lz];
    }
}
