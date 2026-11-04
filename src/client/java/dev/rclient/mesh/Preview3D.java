package dev.rclient.mesh;

import dev.rclient.render.Colors;
import dev.rclient.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;

/**
 * Small software 3D renderer for GUI previews: rotates a MeshBuilder around Y, projects it, depth-sorts
 * the quads (painter's algorithm) and fills each with simple Lambert shading. Emissive quads skip
 * shading. Allocation-free after construction.
 */
public final class Preview3D {
    private final long[] keys = new long[MeshBuilder.MAX_QUADS];
    private final float[] sx = new float[MeshBuilder.MAX_QUADS * 4];
    private final float[] sy = new float[MeshBuilder.MAX_QUADS * 4];
    private final float[] qx = new float[4], qy = new float[4];

    private static final float LX = -0.45f, LY = 0.65f, LZ = 0.62f;

    /**
     * @param cx,cy  screen position of the model origin (feet centre)
     * @param scale  pixels per block
     * @param yaw    rotation around Y in radians
     * @param rowStep rasterisation row step (1 = best, 2 = faster)
     */
    public void render(GuiGraphics ctx, MeshBuilder m, float cx, float cy, float scale, float yaw, float pitch, int rowStep, float alpha) {
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        int n = m.count;
        for (int i = 0; i < n; i++) {
            float depth = 0;
            for (int k = 0; k < 4; k++) {
                float x = m.v[i * 12 + k * 3], y = m.v[i * 12 + k * 3 + 1], z = m.v[i * 12 + k * 3 + 2];
                // yaw around Y then slight pitch around X
                float x1 = x * cyaw + z * syaw, z1 = -x * syaw + z * cyaw;
                float y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;
                float persp = 1f / (1f + z2 * 0.045f);
                sx[i * 4 + k] = cx + x1 * scale * persp;
                sy[i * 4 + k] = cy - y2 * scale * persp;
                depth += z2;
            }
            // larger z = farther? we rotate so +z faces away after yaw=PI; sort far -> near
            int dq = (int) ((depth * 0.25f + 64f) * 4096f);
            keys[i] = ((long) dq << 20) | i;
        }
        Arrays.sort(keys, 0, n);
        // painter's: draw descending depth (far first). keys sorted ascending → iterate from large z to small
        for (int j = n - 1; j >= 0; j--) {
            int i = (int) (keys[j] & 0xFFFFF);
            int base = m.color[i];
            if (Colors.alpha(base) == 0) continue;
            int flag = m.flags[i];
            int col = base;
            if ((flag & MeshBuilder.EMISSIVE) == 0) {
                // normal from first three verts (rotated)
                float ax = m.v[i * 12 + 3] - m.v[i * 12], ay = m.v[i * 12 + 4] - m.v[i * 12 + 1], az = m.v[i * 12 + 5] - m.v[i * 12 + 2];
                float bx = m.v[i * 12 + 6] - m.v[i * 12], by = m.v[i * 12 + 7] - m.v[i * 12 + 1], bz = m.v[i * 12 + 8] - m.v[i * 12 + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl > 1e-6f) { nx /= nl; ny /= nl; nz /= nl; }
                float rx = nx * cyaw + nz * syaw, rz = -nx * syaw + nz * cyaw;
                float ry = ny * cp - rz * sp, rz2 = ny * sp + rz * cp;
                float l = Math.abs(rx * LX + ry * LY + rz2 * LZ);
                col = Colors.scale(base, 0.42f + 0.62f * l);
            }
            col = Colors.mulAlpha(col, alpha);
            for (int k = 0; k < 4; k++) { qx[k] = sx[i * 4 + k]; qy[k] = sy[i * 4 + k]; }
            RenderUtil.polygon(ctx, qx, qy, 4, col, rowStep);
        }
    }
}
