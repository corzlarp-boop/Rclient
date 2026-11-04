package dev.rclient.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Tiny software renderer that draws the player's real skin on the real Minecraft player model layout
 * (head, body, arms, legs + the hat/jacket/sleeve/pants overlay layers, wide or slim arms) into a small dynamic
 * texture, which the menu then draws as one image. Textured triangles, z-buffer, back-face culling and simple
 * lighting; no allocation per frame and one texture upload (instead of hundreds of GUI polygons).
 * Deliberately shows ONLY the skin: no cosmetics are ever drawn here.
 */
public final class SkinPreview {
    public static final int W = 288, H = 432;
    private static final float UNIT = 12f;                 // pixels per model unit (1/16 block)

    private final int[] pix = new int[W * H];
    private final float[] zb = new float[W * H];
    private final int[] skinPx = new int[64 * 64];
    private int skinVersion = -1;
    private boolean slim;
    private TextureFactory.Tex tex;

    // pose / camera state for the current frame
    private float cyaw, syaw, cpit, spit;
    private final float[] sxv = new float[4], syv = new float[4], szv = new float[4];

    public Identifier id() { return tex == null ? null : tex.id; }

    public void draw(GuiGraphics ctx, int x, int y, int w, int h, int tint) {
        if (tex != null) RenderUtil.image(ctx, tex.id, x, y, w, h, W, H, tint);
    }

    public void destroy() { if (tex != null) { TextureFactory.destroy(tex); tex = null; } }

    private void syncSkin() {
        int v = SkinProvider.version();
        if (v == skinVersion) return;
        BufferedImage s = SkinProvider.skin();
        if (s == null) return;
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) skinPx[y * 64 + x] = s.getRGB(x, y);
        slim = SkinProvider.slim();
        skinVersion = v;
    }

    /**
     * @param yaw     body rotation (0 = facing the viewer)
     * @param walk    walk cycle phase (radians)
     * @param walkAmt 0 standing .. 1 full stride
     * @param headYaw/headPitch head look offsets (radians)
     * @param t       time for idle breathing
     */
    public void render(float yaw, float walk, float walkAmt, float headYaw, float headPitch, float t) {
        syncSkin();
        if (skinVersion < 0) return;
        if (tex == null) tex = TextureFactory.createSmooth("skin_preview", W, H);
        Arrays.fill(pix, 0);
        Arrays.fill(zb, -1e9f);

        cyaw = (float) Math.cos(yaw); syaw = (float) Math.sin(yaw);
        float pitch = 0.10f;
        cpit = (float) Math.cos(pitch); spit = (float) Math.sin(pitch);

        float breathe = (float) Math.sin(t * 1.9f) * (1f - walkAmt);
        float swing = (float) Math.sin(walk) * 0.95f * walkAmt;
        float bob = Math.abs((float) Math.cos(walk)) * 0.55f * walkAmt;
        float armIdle = breathe * 0.03f;

        float aw = slim ? 1.5f : 2f;                  // arm half width
        float ax = 4f + aw;

        // body (+ jacket)
        float by = bob;
        box(0, by, 0, 0, 0, 0, 0, 18, 0, 4, 6, 2, 16, 16, 8, 12, 4, 0, 0);
        box(0, by, 0, 0, 0, 0, 0, 18, 0, 4.25f, 6.25f, 2.25f, 16, 32, 8, 12, 4, 0, 0);

        // head (+ hat): pivot at the neck, looks toward the cursor
        float hx = -headPitch, hy = headYaw;
        headBox(0, 24 + by, 0, hx + breathe * 0.015f, hy, 4f, 0, 0);
        headBox(0, 24 + by, 0, hx + breathe * 0.015f, hy, 4.5f, 32, 0);

        // arms (character right = -X): swing opposite to legs
        limb(-ax, 22 + by, 0, -swing + armIdle, -0.04f - breathe * 0.01f, aw, 6f, 40, 16, 40, 32, aw * 2);
        limb(ax, 22 + by, 0, swing - armIdle, 0.04f + breathe * 0.01f, aw, 6f, 32, 48, 48, 48, aw * 2);
        // legs
        limb(-2, 12 + by * 0.0f, 0, swing, 0, 2f, 6f, 0, 16, 0, 32, 4);
        limb(2, 12 + by * 0.0f, 0, -swing, 0, 2f, 6f, 16, 48, 0, 48, 4);

        // upload
        var img = tex.image;
        for (int y = 0; y < H; y++) {
            int row = y * W;
            for (int x = 0; x < W; x++) img.setPixel(x, y, pix[row + x]);
        }
        tex.upload();
    }

    private void headBox(float px, float py, float pz, float rx, float ry, float half, int u, int v) {
        box(px, py, pz, rx, ry, 0, 0, 4, 0, half, half, half, u, v, 8, 8, 8, 0, 0);
    }

    /** arm/leg with base + overlay layer. hw = half width, hh = half height. */
    private void limb(float px, float py, float pz, float rx, float rz, float hw, float hh,
                      int u, int v, int ou, int ov, float uvw) {
        // arms pivot 2 units below their top (shoulder), legs pivot at their top
        float off = py > 20f ? -4f : -6f;
        box(px, py, pz, rx, 0, rz, 0, off, 0, hw, hh, 2, u, v, (int) uvw, 12, 4, 0, 0);
        box(px, py, pz, rx, 0, rz, 0, off, 0, hw + 0.25f, hh + 0.25f, 2.25f, ou, ov, (int) uvw, 12, 4, 0, 0);
    }

    // ------------------------------------------------------------------ box

    private static final float LX = -0.35f, LY = 0.62f, LZ = 0.70f;

    /**
     * One textured box. (px,py,pz) pivot in model units, (rx,ry,rz) rotation about the pivot, (cx,cy,cz) box centre
     * relative to the pivot, (hx,hy,hz) half extents, (u,v) skin offset, (uw,uh,ud) the unscaled UV box size.
     */
    private void box(float px, float py, float pz, float rx, float ry, float rz,
                     float cx, float cy, float cz, float hx, float hy, float hz,
                     int u, int v, int uw, int uh, int ud, int unusedA, int unusedB) {
        // faces: a (top-left), b (top-right), c (bottom-right), d (bottom-left) as seen from outside, in texture orientation
        float[][] f = {
            {-hx, hy, hz, hx, hy, hz, hx, -hy, hz, -hx, -hy, hz},          // front  +Z
            {hx, hy, -hz, -hx, hy, -hz, -hx, -hy, -hz, hx, -hy, -hz},      // back   -Z
            {-hx, hy, -hz, -hx, hy, hz, -hx, -hy, hz, -hx, -hy, -hz},      // right  -X
            {hx, hy, hz, hx, hy, -hz, hx, -hy, -hz, hx, -hy, hz},          // left   +X
            {-hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz},          // top    +Y
            {-hx, -hy, hz, hx, -hy, hz, hx, -hy, -hz, -hx, -hy, -hz},      // bottom -Y
        };
        float[][] n = {{0, 0, 1}, {0, 0, -1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        // skin rects (x, y, w, h) per face
        int[][] r = {
            {u + ud, v + ud, uw, uh},
            {u + 2 * ud + uw, v + ud, uw, uh},
            {u, v + ud, ud, uh},
            {u + ud + uw, v + ud, ud, uh},
            {u + ud, v, uw, ud},
            {u + ud + uw, v, uw, ud},
        };
        float crx = (float) Math.cos(rx), srx = (float) Math.sin(rx), cry = (float) Math.cos(ry), sry = (float) Math.sin(ry), crz = (float) Math.cos(rz), srz = (float) Math.sin(rz);
        for (int fi = 0; fi < 6; fi++) {
            for (int k = 0; k < 4; k++) {
                float x = f[fi][k * 3] + cx, y = f[fi][k * 3 + 1] + cy, z = f[fi][k * 3 + 2] + cz;
                // part rotation: X, then Y, then Z
                float y1 = y * crx - z * srx, z1 = y * srx + z * crx;
                float x2 = x * cry + z1 * sry, z2 = -x * sry + z1 * cry;
                float x3 = x2 * crz - y1 * srz, y3 = x2 * srz + y1 * crz;
                x3 += px; y3 += py; z2 += pz;
                // global yaw then pitch
                float x4 = x3 * cyaw + z2 * syaw, z4 = -x3 * syaw + z2 * cyaw;
                float y5 = (y3 - 16f) * cpit - z4 * spit, z5 = (y3 - 16f) * spit + z4 * cpit;
                sxv[k] = W * 0.5f + x4 * UNIT;
                syv[k] = H * 0.5f - y5 * UNIT + 8f;
                szv[k] = z5;
            }
            // cull back faces (screen-space winding, y down)
            float area = (sxv[1] - sxv[0]) * (syv[2] - syv[0]) - (syv[1] - syv[0]) * (sxv[2] - sxv[0]);
            if (area <= 0f) continue;
            // lighting from the rotated face normal
            float nx = n[fi][0], ny = n[fi][1], nz = n[fi][2];
            float ny1 = ny * crx - nz * srx, nz1 = ny * srx + nz * crx;
            float nx2 = nx * cry + nz1 * sry, nz2 = -nx * sry + nz1 * cry;
            float nx3 = nx2 * crz - ny1 * srz, ny3 = nx2 * srz + ny1 * crz;
            float nx4 = nx3 * cyaw + nz2 * syaw, nz4 = -nx3 * syaw + nz2 * cyaw;
            float ny5 = ny3 * cpit - nz4 * spit, nz5 = ny3 * spit + nz4 * cpit;
            float lit = 0.52f + 0.50f * Math.max(0f, nx4 * LX + ny5 * LY + nz5 * LZ) + 0.10f * (1f - Math.abs(nz5));
            int[] rc = r[fi];
            // two triangles with texture coords at the rect corners (a=(u0,v0) b=(u1,v0) c=(u1,v1) d=(u0,v1))
            tri(sxv[0], syv[0], szv[0], rc[0], rc[1], sxv[1], syv[1], szv[1], rc[0] + rc[2], rc[1], sxv[2], syv[2], szv[2], rc[0] + rc[2], rc[1] + rc[3], lit);
            tri(sxv[0], syv[0], szv[0], rc[0], rc[1], sxv[2], syv[2], szv[2], rc[0] + rc[2], rc[1] + rc[3], sxv[3], syv[3], szv[3], rc[0], rc[1] + rc[3], lit);
        }
    }

    private void tri(float x0, float y0, float z0, float u0, float v0,
                     float x1, float y1, float z1, float u1, float v1,
                     float x2, float y2, float z2, float u2, float v2, float lit) {
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(W - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(H - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        float den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(den) < 1e-6f) return;
        float inv = 1f / den;
        float uMin = Math.min(u0, Math.min(u1, u2)), uMax = Math.max(u0, Math.max(u1, u2)) - 0.01f;
        float vMin = Math.min(v0, Math.min(v1, v2)), vMax = Math.max(v0, Math.max(v1, v2)) - 0.01f;
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float l0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) * inv;
                float l1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) * inv;
                float l2 = 1f - l0 - l1;
                if (l0 < -0.0005f || l1 < -0.0005f || l2 < -0.0005f) continue;
                float z = l0 * z0 + l1 * z1 + l2 * z2;
                int idx = y * W + x;
                if (z <= zb[idx]) continue;
                float u = l0 * u0 + l1 * u1 + l2 * u2, v = l0 * v0 + l1 * v1 + l2 * v2;
                // keep the sample inside this face's rectangle so edge pixels never bleed into a neighbouring face
                if (u < uMin) u = uMin; else if (u > uMax) u = uMax;
                if (v < vMin) v = vMin; else if (v > vMax) v = vMax;
                int tx = (int) u, ty = (int) v;
                int c = skinPx[ty * 64 + tx];
                if ((c >>> 24) < 100) continue;                    // transparent overlay texel
                zb[idx] = z;
                int r = (int) (((c >> 16) & 255) * lit), g = (int) (((c >> 8) & 255) * lit), b = (int) ((c & 255) * lit);
                pix[idx] = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
            }
        }
    }
}
