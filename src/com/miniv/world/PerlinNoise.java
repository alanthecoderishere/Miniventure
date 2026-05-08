package com.miniv.world;

public class PerlinNoise {
    private static long seed = 0;

    /** Call this whenever Config.worldSeed changes to re-key the noise generator. */
    public static void setSeed(String seedStr) {
        seed = seedStr.hashCode();
        // Spread the seed bits more evenly
        seed ^= (seed >>> 33);
        seed *= 0xff51afd7ed558ccdL;
        seed ^= (seed >>> 33);
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= (seed >>> 33);
    }

    /** Layered octave noise for richer terrain (3 octaves). */
    public static float octaveNoise(float x, float y, int octaves, float persistence) {
        float total = 0;
        float frequency = 1;
        float amplitude = 1;
        float maxValue  = 0;
        for (int i = 0; i < octaves; i++) {
            total    += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude  *= persistence;
            frequency  *= 2;
        }
        return total / maxValue;
    }

    public static float noise(float x, float y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;

        float v1 = rawNoise(ix,     iy);
        float v2 = rawNoise(ix + 1, iy);
        float v3 = rawNoise(ix,     iy + 1);
        float v4 = rawNoise(ix + 1, iy + 1);

        float i1 = interpolate(v1, v2, fx);
        float i2 = interpolate(v3, v4, fx);
        return interpolate(i1, i2, fy);
    }

    private static float interpolate(float a, float b, float f) {
        float f2 = (1 - (float) Math.cos(f * Math.PI)) / 2.0f;
        return a * (1 - f2) + b * f2;
    }

    private static float rawNoise(int x, int y) {
        long n = x + y * 57L + seed;
        n = (n ^ (n << 13)) ^ n;
        long tmp = (n * (n * n * 15731L + 789221L) + 1376312589L) & 0x7fffffffL;
        // Returns 0.0 .. 1.0
        return (float) tmp / 2147483647.0f;
    }
}
