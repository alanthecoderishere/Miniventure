package com.miniv.rendering;

import org.joml.Matrix4f;

/**
 * CPU-side frustum culler using the Gribb/Hartmann plane-extraction method.
 *
 * Call {@link #update(Matrix4f)} every frame with the combined projection×view
 * matrix before testing chunks, then call {@link #isBoxVisible} for each
 * chunk AABB. If it returns {@code false}, skip rendering that chunk entirely.
 *
 * The plane equation used is: ax + by + cz + d >= 0 means "inside".
 * We test the positive-vertex (the AABB corner most in the plane normal
 * direction) — if even that corner is outside, the whole box is outside.
 */
public class FrustumCuller {

    // 6 planes × 4 floats (a, b, c, d)
    private final float[] px = new float[6];
    private final float[] py = new float[6];
    private final float[] pz = new float[6];
    private final float[] pd = new float[6];

    /**
     * Extract the six frustum planes from a column-major projection×view matrix.
     * JOML stores matrices column-major, so index layout is:
     *   col0 = m[0..3], col1 = m[4..7], col2 = m[8..11], col3 = m[12..15]
     */
    public void update(Matrix4f m) {
        float[] f = new float[16];
        m.get(f); // fills column-major

        // Gribb-Hartmann extracts planes by adding/subtracting the rows of the matrix.
        // In column-major layout, row i is: f[i], f[i+4], f[i+8], f[i+12]
        
        // Left   = row3 + row0
        set(0,  f[3]+f[0],  f[7]+f[4],  f[11]+f[8],  f[15]+f[12]);
        // Right  = row3 - row0
        set(1,  f[3]-f[0],  f[7]-f[4],  f[11]-f[8],  f[15]-f[12]);
        // Bottom = row3 + row1
        set(2,  f[3]+f[1],  f[7]+f[5],  f[11]+f[9],  f[15]+f[13]);
        // Top    = row3 - row1
        set(3,  f[3]-f[1],  f[7]-f[5],  f[11]-f[9],  f[15]-f[13]);
        // Near   = row3 + row2
        set(4,  f[3]+f[2],  f[7]+f[6],  f[11]+f[10], f[15]+f[14]);
        // Far    = row3 - row2
        set(5,  f[3]-f[2],  f[7]-f[6],  f[11]-f[10], f[15]-f[14]);
    }

    private void set(int i, float a, float b, float c, float d) {
        // Normalise so distance comparisons are meaningful
        float len = (float) Math.sqrt(a*a + b*b + c*c);
        if (len == 0) { px[i]=py[i]=pz[i]=pd[i]=0; return; }
        px[i] = a/len; py[i] = b/len; pz[i] = c/len; pd[i] = d/len;
    }

    /**
     * Returns {@code true} if the axis-aligned box defined by its min/max corners
     * could be visible (intersects or is inside the frustum).
     * Returns {@code false} if the box is completely outside at least one plane —
     * safe to cull.
     */
    public boolean isBoxVisible(float minX, float minY, float minZ,
                                float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float a = px[i], b = py[i], c = pz[i], d = pd[i];
            // Positive vertex: corner most in the direction of the plane normal
            float pvx = a >= 0 ? maxX : minX;
            float pvy = b >= 0 ? maxY : minY;
            float pvz = c >= 0 ? maxZ : minZ;
            if (a * pvx + b * pvy + c * pvz + d < 0) {
                return false; // positive vertex is outside → whole box is outside
            }
        }
        return true;
    }
}
