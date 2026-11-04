package dev.rclient.mesh;

/**
 * Allocation-free quad soup in model space with a small affine transform stack.
 * Model space: origin at the player's feet centre, +Y up, the player faces +Z (so "behind" is -Z).
 * The same mesh is drawn in the world (WorldDraw) and in the main-menu preview (Preview3D).
 */
public final class MeshBuilder {
    public static final int EMISSIVE = 1;   // not lit, drawn bright (glow)
    public static final int ADDITIVE = 2;   // additive blending layer (glow, flames)

    public static final int MAX_QUADS = 6000;

    public final float[] v = new float[MAX_QUADS * 12];
    public final int[] color = new int[MAX_QUADS];
    public final byte[] flags = new byte[MAX_QUADS];
    public int count;

    // affine 3x4 row-major
    private final float[][] stack = new float[24][12];
    private int sp;

    public MeshBuilder() { identity(stack[0]); }

    public void clear() { count = 0; sp = 0; identity(stack[0]); }

    private static void identity(float[] m) {
        m[0] = 1; m[1] = 0; m[2] = 0; m[3] = 0;
        m[4] = 0; m[5] = 1; m[6] = 0; m[7] = 0;
        m[8] = 0; m[9] = 0; m[10] = 1; m[11] = 0;
    }

    public void push() { System.arraycopy(stack[sp], 0, stack[sp + 1], 0, 12); sp++; }
    public void pop() { if (sp > 0) sp--; }

    public void translate(float x, float y, float z) {
        float[] m = stack[sp];
        m[3] += m[0] * x + m[1] * y + m[2] * z;
        m[7] += m[4] * x + m[5] * y + m[6] * z;
        m[11] += m[8] * x + m[9] * y + m[10] * z;
    }

    public void scale(float x, float y, float z) {
        float[] m = stack[sp];
        m[0] *= x; m[4] *= x; m[8] *= x;
        m[1] *= y; m[5] *= y; m[9] *= y;
        m[2] *= z; m[6] *= z; m[10] *= z;
    }

    public void rotateX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = stack[sp];
        for (int r = 0; r < 3; r++) {
            float y = m[r * 4 + 1], z = m[r * 4 + 2];
            m[r * 4 + 1] = y * c + z * s;
            m[r * 4 + 2] = -y * s + z * c;
        }
    }

    public void rotateY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = stack[sp];
        for (int r = 0; r < 3; r++) {
            float x = m[r * 4], z = m[r * 4 + 2];
            m[r * 4] = x * c + z * s;
            m[r * 4 + 2] = -x * s + z * c;
        }
    }

    public void rotateZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = stack[sp];
        for (int r = 0; r < 3; r++) {
            float x = m[r * 4], y = m[r * 4 + 1];
            m[r * 4] = x * c + y * s;
            m[r * 4 + 1] = -x * s + y * c;
        }
    }

    // ------------------------------------------------------------------ primitives

    private int o;
    private void put(float x, float y, float z) {
        float[] m = stack[sp];
        v[o++] = m[0] * x + m[1] * y + m[2] * z + m[3];
        v[o++] = m[4] * x + m[5] * y + m[6] * z + m[7];
        v[o++] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3, int argb, int flag) {
        if (count >= MAX_QUADS) return;
        o = count * 12;
        put(x0, y0, z0); put(x1, y1, z1); put(x2, y2, z2); put(x3, y3, z3);
        color[count] = argb;
        flags[count] = (byte) flag;
        count++;
    }

    public void tri(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, int argb, int flag) {
        quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x2, y2, z2, argb, flag);
    }

    /** Axis-aligned box centred at (cx,cy,cz) with half extents (hx,hy,hz). */
    public void box(float cx, float cy, float cz, float hx, float hy, float hz, int argb, int flag) {
        float x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb, flag); // +z
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, argb, flag); // -z
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, argb, flag); // +x
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb, flag); // -x
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, argb, flag); // +y
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb, flag); // -y
    }

    /** Flat vertical or arbitrary strip between two points with a width (camera independent, two-sided). */
    public void ribbon(float ax, float ay, float az, float bx, float by, float bz, float wx, float wy, float wz, int argb, int flag) {
        quad(ax - wx, ay - wy, az - wz, ax + wx, ay + wy, az + wz, bx + wx, by + wy, bz + wz, bx - wx, by - wy, bz - wz, argb, flag);
    }
}
