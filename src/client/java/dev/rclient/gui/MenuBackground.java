package dev.rclient.gui;

import dev.rclient.core.Theme;
import dev.rclient.render.Colors;
import dev.rclient.render.TextureFactory;
import net.minecraft.client.gui.GuiGraphics;
import dev.rclient.render.RenderUtil;

/**
 * The animated main-menu backdrop baked into ONE small texture (aurora curtains on both edges, slow glow blobs and a
 * nebula behind the character), refreshed at ~30 Hz and drawn as a single smooth quad. This replaces ~1500 GUI fills
 * per frame the old curtains used, which was the main menu's biggest cost.
 */
final class MenuBackground {
    private static final int W = 192, H = 108;
    private static final float[] SIN = new float[1024];
    static { for (int i = 0; i < 1024; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2 / 1024); }
    private static float sin(float x) { return SIN[((int) (x * 162.97466f)) & 1023]; }

    private TextureFactory.Tex tex;
    private float lastBake = -1f;

    void destroy() { if (tex != null) { TextureFactory.destroy(tex); tex = null; } }

    void draw(GuiGraphics ctx, int sw, int sh, float t, float alpha, float parX, float parY, boolean animated, float charX, float charY) {
        if (tex == null) tex = TextureFactory.createSmooth("menu_bg", W, H);
        float step = animated ? 1f / 30f : 1e9f;
        if (lastBake < 0f || t - lastBake >= step || t < lastBake) { bake(t, charX, charY); lastBake = t; }
        int pad = 14;
        RenderUtil.image(ctx, tex.id, (int) (-pad + parX), (int) (-pad + parY), sw + pad * 2, sh + pad * 2, W, H, Colors.argb((int) (255 * alpha), 255, 255, 255));
    }

    private void bake(float t, float charX, float charY) {
        int ac = Theme.accent();
        float ar = Colors.red(ac) / 255f, ag = Colors.green(ac) / 255f, ab = Colors.blue(ac) / 255f;
        // curtain colour: accent pulled toward cool blue so it reads as light, not as a flat tint
        float cr = ar * 0.55f + 0.10f, cg = ag * 0.55f + 0.18f, cb = ab * 0.55f + 0.32f;
        float b1x = 0.30f + 0.10f * sin(t * 0.21f), b1y = 0.35f + 0.12f * sin(t * 0.17f + 1f);
        float b2x = 0.62f + 0.08f * sin(t * 0.13f + 2f), b2y = 0.72f + 0.10f * sin(t * 0.19f + 4f);
        var img = tex.image;
        for (int y = 0; y < H; y++) {
            float fy = y / (float) H;
            for (int x = 0; x < W; x++) {
                float fx = x / (float) W;
                float r = 0.020f + 0.012f * fy, g = 0.020f + 0.012f * fy, b = 0.035f + 0.020f * fy;

                // aurora curtains hugging both edges
                float edge = Math.min(fx, 1f - fx) / 0.30f;
                if (edge < 1f) {
                    float fall = (1f - edge); fall *= fall;
                    float w1 = 0.5f + 0.5f * sin(fy * 14f + t * 0.55f + fx * 9f);
                    float w2 = 0.5f + 0.5f * sin(fy * 5.5f - t * 0.35f + fx * 4f + 1.3f);
                    float w3 = 0.5f + 0.5f * sin(fy * 22f + t * 0.8f - fx * 12f);
                    float k = fall * (0.35f * w1 + 0.45f * w2 * w2 + 0.20f * w3 * w2) * 0.55f;
                    r += cr * k; g += cg * k; b += cb * k;
                }
                // slow glow blobs
                float dx = fx - b1x, dy = (fy - b1y) * 0.6f;
                float k1 = 1f / (1f + 60f * (dx * dx + dy * dy)); k1 *= k1 * 0.10f;
                dx = fx - b2x; dy = (fy - b2y) * 0.6f;
                float k2 = 1f / (1f + 70f * (dx * dx + dy * dy)); k2 *= k2 * 0.08f;
                r += ar * k1 + 0.4f * ar * k2; g += ag * k1 + 0.4f * ag * k2; b += ab * k1 + 0.9f * ab * k2;
                // nebula behind the character
                dx = (fx - charX) * 1.6f; dy = (fy - charY) * 1.1f;
                float kc = 1f / (1f + 38f * (dx * dx + dy * dy)); kc *= kc * (0.30f + 0.04f * sin(t * 0.7f));
                r += ar * kc; g += ag * kc * 0.9f; b += ab * kc * 1.1f + 0.05f * kc;
                // vignette
                float vx = fx - 0.5f, vy = fy - 0.5f;
                float vig = 1f - 0.55f * (vx * vx + vy * vy) * 2f;
                r *= vig; g *= vig; b *= vig;
                img.setPixel(x, y, 0xFF000000 | (c(r) << 16) | (c(g) << 8) | c(b));
            }
        }
        tex.upload();
    }

    private static int c(float v) { return v <= 0f ? 0 : v >= 1f ? 255 : (int) (v * 255f); }
}
