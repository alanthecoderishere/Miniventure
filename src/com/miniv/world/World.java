package com.miniv.world;

import com.miniv.core.SupabaseMultiplayer;

public class World {
    public static final int CHUNK_SIZE = 16;
    private java.util.concurrent.ConcurrentHashMap<Long, Chunk> chunks = new java.util.concurrent.ConcurrentHashMap<>();

    private long getChunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }
    
    public java.util.Collection<Chunk> getActiveChunks() {
        return chunks.values();
    }

    public java.util.concurrent.ConcurrentLinkedQueue<Chunk> prunedChunks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void updateChunks(float playerX, float playerZ) {
        int pcx = (int) Math.floor(playerX / CHUNK_SIZE);
        int pcz = (int) Math.floor(playerZ / CHUNK_SIZE);
        
        int radius = 4; // Low Default
        if (com.miniv.core.Config.renderDistance == 1) radius = 6;
        if (com.miniv.core.Config.renderDistance == 2) radius = 10;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int cx = pcx + x;
                int cz = pcz + z;
                long key = getChunkKey(cx, cz);
                if (!chunks.containsKey(key)) {
                    Chunk chunk = new Chunk(cx, cz);
                    generateChunkData(chunk);
                    chunks.put(key, chunk);
                }
            }
        }
        
        // Prune far chunks to save memory
        int pruneRadius = radius + 2;
        chunks.entrySet().removeIf(entry -> {
            Chunk c = entry.getValue();
            boolean far = Math.abs(c.cx - pcx) > pruneRadius || Math.abs(c.cz - pcz) > pruneRadius;
            if (far) {
                prunedChunks.add(c);
            }
            return far;
        });
    }

    private void generateChunkData(Chunk chunk) {
        float offX = chunk.cx * CHUNK_SIZE;
        float offZ = chunk.cz * CHUNK_SIZE;

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                float gx = offX + x;
                float gz = offZ + z;

                // 3-octave noise for nicer hills; scale 0.05 → gentle rolling terrain
                float raw = PerlinNoise.octaveNoise(gx * 0.05f, gz * 0.05f, 3, 0.5f);
                int h = (int)(raw * (com.miniv.core.Config.worldHeight * 0.25f) + 4);
                h = Math.max(1, Math.min(com.miniv.core.Config.worldHeight - 1, h));

                for (int y = 0; y <= h; y++) {
                    String blockKey = (int) gx + "," + y + "," + (int) gz;
                    Byte placed = SupabaseMultiplayer.sessionBlocks.get(blockKey);
                    if (placed != null) {
                        chunk.setBlockLocal(x, y, z, placed);
                        continue;
                    }
                    if (y == h) {
                        chunk.setBlockLocal(x, y, z, Voxel.GRASS);
                    } else if (y >= h - 3) {
                        chunk.setBlockLocal(x, y, z, Voxel.DIRT);
                    } else {
                        chunk.setBlockLocal(x, y, z, Voxel.STONE);
                    }
                }
            }
        }
        applySessionBlocksToChunk(chunk);
    }

    /** Apply placed/broken blocks at any height (including above terrain). */
    private void applySessionBlocksToChunk(Chunk chunk) {
        int baseX = chunk.cx * CHUNK_SIZE;
        int baseZ = chunk.cz * CHUNK_SIZE;
        for (var e : SupabaseMultiplayer.sessionBlocks.entrySet()) {
            String[] p = e.getKey().split(",");
            if (p.length != 3) continue;
            try {
                int gx = Integer.parseInt(p[0]);
                int gy = Integer.parseInt(p[1]);
                int gz = Integer.parseInt(p[2]);
                if (gx < baseX || gx >= baseX + CHUNK_SIZE || gz < baseZ || gz >= baseZ + CHUNK_SIZE)
                    continue;
                int lx = gx - baseX;
                int lz = gz - baseZ;
                chunk.setBlockLocal(lx, gy, lz, e.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public byte getBlock(int gx, int gy, int gz) {
        if (gy < 0 || gy >= com.miniv.core.Config.worldHeight) return Voxel.AIR;
        int cx = (int) Math.floor((float) gx / CHUNK_SIZE);
        int cz = (int) Math.floor((float) gz / CHUNK_SIZE);
        long key = getChunkKey(cx, cz);
        
        Chunk chunk = chunks.get(key);
        if (chunk == null) return Voxel.AIR;
        
        int lx = gx - (cx * CHUNK_SIZE);
        int lz = gz - (cz * CHUNK_SIZE);
        return chunk.getBlockLocal(lx, gy, lz);
    }

    public void setBlock(int gx, int gy, int gz, byte block) {
        if (gy < 0 || gy >= com.miniv.core.Config.worldHeight) return;
        
        SupabaseMultiplayer.recordBlock(gx, gy, gz, block);

        int cx = (int) Math.floor((float) gx / CHUNK_SIZE);
        int cz = (int) Math.floor((float) gz / CHUNK_SIZE);
        Chunk chunk = chunks.get(getChunkKey(cx, cz));
        
        if (chunk != null) {
            int lx = gx - (cx * CHUNK_SIZE);
            int lz = gz - (cz * CHUNK_SIZE);
            chunk.setBlockLocal(lx, gy, lz, block);
            
            // Mark neighboring chunks dirty if on border
            if (lx == 0) markDirty(cx - 1, cz);
            if (lx == CHUNK_SIZE - 1) markDirty(cx + 1, cz);
            if (lz == 0) markDirty(cx, cz - 1);
            if (lz == CHUNK_SIZE - 1) markDirty(cx, cz + 1);
        }
    }

    private void markDirty(int cx, int cz) {
        Chunk chunk = chunks.get(getChunkKey(cx, cz));
        if (chunk != null) chunk.isDirty = true;
    }
}
