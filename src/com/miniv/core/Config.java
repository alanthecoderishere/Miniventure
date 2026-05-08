package com.miniv.core;

public class Config {
    // Zoom Settings — default zoomed out enough to see the infinite terrain
    public static float zoom = 18.0f;

    // Shade Settings - 0: Off, 1: Low, 2: Medium, 3: High, 4: Lock
    public static int shadeQuality = 0;

    // Active block the player places with RMB
    public static int activeBlockIndex = 0;
    public static final byte[] placeableBlocks = {
        com.miniv.world.Voxel.WOOD,
        com.miniv.world.Voxel.STONE,
        com.miniv.world.Voxel.COBBLE,
        com.miniv.world.Voxel.DIRT,
        com.miniv.world.Voxel.GRAVEL,
        com.miniv.world.Voxel.SAND,
        com.miniv.world.Voxel.LEAVES,
        com.miniv.world.Voxel.LOG,
        com.miniv.world.Voxel.GRASS,
    };
    public static final String[] placeableNames = {
        "Wood", "Stone", "Cobble", "Dirt", "Gravel", "Sand", "Leaves", "Log", "Grass"
    };
    public static byte getActiveBlock() { return placeableBlocks[activeBlockIndex]; }
    
    // Day cycle speed (how many real seconds per game day)
    public static float dayLengthSeconds = 60.0f; // Fast cycle for testing
    
    // World Seed
    public static String worldSeed = "123456789";

    // World Parameters
    public static int worldHeight = 64; // Default; adjustable in Settings
    public static final int MIN_WORLD_HEIGHT = 32;
    public static final int MAX_WORLD_HEIGHT = 120;
    public static int renderDistance = 0; // 0: Low (8x8), 1: Medium (12x12), 2: High (20x20)
    public static boolean progressiveLoading = true; // true: load sequentially, false: block until finished

    // Max Zoom calculation based on Render Distance
    public static float getMaxZoom() {
        if (renderDistance == 0) return 40.0f;
        if (renderDistance == 1) return 60.0f;
        return 100.0f; 
    }
}
