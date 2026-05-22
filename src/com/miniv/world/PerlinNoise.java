package com.miniv.world;

/**
 * Classic 2D Perlin gradient noise with seeded permutation table.
 *
 * Output of noise() is in roughly [-1, 1].
 * Output of octaveNoise() is normalised to [0, 1] via the theoretical max
 * amplitude of a gradient noise sum, so terrain height mapping is stable
 * regardless of octave count or persistence.
 */
public class PerlinNoise {

    // The seeded permutation table (size 512 = two copies of 256).
    private static final int[] P = new int[512];

    /** Must be called once before world generation whenever the seed changes. */
    public static void setSeed(String seedStr) {
        // Derive a 64-bit seed from the string
        long s = seedStr.hashCode();
        s ^= (s >>> 33);
        s *= 0xff51afd7ed558ccdL;
        s ^= (s >>> 33);
        s *= 0xc4ceb9fe1a85ec53L;
        s ^= (s >>> 33);

        // Build a 0-255 permutation using a seeded LCG shuffle (Fisher-Yates)
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;

        for (int i = 255; i > 0; i--) {
            s = s * 6364136223846793005L + 1442695040888963407L; // LCG step
            int j = (int) ((s >>> 33) & 0x7fffffffL) % (i + 1);
            int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
        }

        // Duplicate for wrap-around indexing without modulo
        for (int i = 0; i < 512; i++) P[i] = perm[i & 255];
    }

    // ─── public API ───────────────────────────────────────────────────────────

    /**
     * Multi-octave (fBm) wrapper.  Returns [0, 1].
     *
     * @param octaves     number of octave layers (3–6 recommended)
     * @param persistence amplitude scale per octave (0.5 = classic lacunarity)
     */
    public static float octaveNoise(float x, float y, int octaves, float persistence) {
        float total     = 0f;
        float frequency = 1f;
        float amplitude = 1f;
        float maxValue  = 0f;   // theoretical max for normalisation

        for (int i = 0; i < octaves; i++) {
            total     += noise(x * frequency, y * frequency) * amplitude;
            maxValue  += amplitude;
            amplitude *= persistence;
            frequency *= 2f;
        }

        // Map from [-maxValue, maxValue] → [0, 1]
        return (total / maxValue) * 0.5f + 0.5f;
    }

    /** Raw 2-D Perlin gradient noise.  Returns approximately [-1, 1]. */
    public static float noise(float x, float y) {
        // Integer cell coords
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;

        // Fractional offsets inside cell
        float fx = x - (float) Math.floor(x);
        float fy = y - (float) Math.floor(y);

        // Smooth (quintic) fade curves — eliminates banding from cosine
        float u = fade(fx);
        float v = fade(fy);

        // Permutation hash for four corners
        int aa = P[P[xi    ] + yi    ];
        int ab = P[P[xi    ] + yi + 1];
        int ba = P[P[xi + 1] + yi    ];
        int bb = P[P[xi + 1] + yi + 1];

        // Gradient dot products, then bilinear interpolation
        float x1 = lerp(grad(aa, fx,     fy    ),
                        grad(ba, fx - 1, fy    ), u);
        float x2 = lerp(grad(ab, fx,     fy - 1),
                        grad(bb, fx - 1, fy - 1), u);
        return lerp(x1, x2, v);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Ken Perlin's quintic ease curve: 6t⁵ − 15t⁴ + 10t³ */
    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    /**
     * Gradient: maps hash to one of 8 direction vectors and dots with (x, y).
     * Using 4-bit hash (h & 3) gives 4 directions; 8 directions (h & 7) gives
     * better isotropy.
     */
    private static float grad(int hash, float x, float y) {
        switch (hash & 7) {
            case 0: return  x + y;
            case 1: return -x + y;
            case 2: return  x - y;
            case 3: return -x - y;
            case 4: return  x;
            case 5: return -x;
            case 6: return  y;
            case 7: return -y;
            default: return 0; // unreachable
        }
    }
}
