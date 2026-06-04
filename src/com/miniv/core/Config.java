package com.miniv.core;

public class Config {
    public static float zoom = 3.0f;

    // Shade Settings - 0: Off, 1: Low, 2: Medium, 3: High, 4: Lock
    public static int shadeQuality = 0;

    /**
     * Night brightness 0–1. Lifts how dark night gets while keeping day/night cycle
     * (day stays bright, night stays dimmer — never fully equal).
     */
    public static float brightness = 0.25f;

    private static final float NIGHT_LIGHT = 0.2f;
    private static final float DAY_LIGHT = 1.0f;
    /** Darkest night can get at 100% brightness (still below full day). */
    private static final float MAX_NIGHT_FLOOR = 0.72f;

    public static float applyBrightness(float skyLight) {
        float nightFloor = NIGHT_LIGHT + brightness * (MAX_NIGHT_FLOOR - NIGHT_LIGHT);
        if (skyLight <= NIGHT_LIGHT) return nightFloor;
        if (skyLight >= DAY_LIGHT) return DAY_LIGHT;
        float t = (skyLight - NIGHT_LIGHT) / (DAY_LIGHT - NIGHT_LIGHT);
        return nightFloor + t * (DAY_LIGHT - nightFloor);
    }

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
        com.miniv.world.Voxel.QUIZ_BLOCK,
        com.miniv.world.Voxel.INFO_BLOCK,
        com.miniv.world.Voxel.TELEPORT_BLOCK,
        com.miniv.world.Voxel.ATTENDANCE_BLOCK,
        com.miniv.world.Voxel.NPC_SPAWNER
    };
    public static final String[] placeableNames = {
        "Wood", "Stone", "Cobble", "Dirt", "Gravel", "Sand", "Leaves", "Log", "Grass",
        "Quiz", "Info", "Teleport", "Attendance", "NPC Spawner"
    };
    
    public static final int HOTBAR_SIZE = 9;

    // Hotbar slots hold indexes into placeableBlocks (13 blocks total; use E inventory for slots 10+)
    public static int[] hotbarSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    
    public static byte getActiveBlock() { return placeableBlocks[hotbarSlots[activeBlockIndex]]; }
    
    // Day cycle speed (how many real seconds per game day)
    public static float dayLengthSeconds = 60.0f; // Fast cycle for testing
    
    // FPS Cap: -1 (VSync), 30, 60, 120, 0 (Unlimited)
    public static int fpsCapIndex = 0;
    public static final int[] fpsCaps = {-1, 30, 60, 120, 0};
    public static int getFpsCap() { return fpsCaps[fpsCapIndex]; }
    
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
