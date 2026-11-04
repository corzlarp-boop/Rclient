package dev.rclient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 2D drawing primitives for RClient.
 *
 * Anti-aliased shapes come from signed-distance-field coverage evaluated ONCE on the CPU and baked
 * into small cached textures sized to the display's real pixel grid (radius * GUI scale), so edges are
 * smooth at every GUI scale and nothing is computed per frame. Everything is then drawn through
 * GuiGraphics, which batches quads. Per-corner radii, vertical/horizontal gradients, gradient borders,
 * soft drop shadows / glows and circles are supported. Coordinates are GUI pixels (ints).
 */
public final class RenderUtil {
    private RenderUtil() {}

    private static final int TL = 0, TR = 1, BR = 2, BL = 3;

    private static final Map<Long, TextureFactory.Tex> MASKS = new HashMap<>();
    private static double bakedScale = -1;

    // ------------------------------------------------------------------ scale / cache

    public static double scale() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    private static void checkScale() {
        double s = scale();
        if (s != bakedScale) {
            for (TextureFactory.Tex t : MASKS.values()) TextureFactory.destroy(t);
            MASKS.clear();
            bakedScale = s;
        }
    }

    private static int dev(int guiPx) { return Math.max(1, (int) Math.round(guiPx * bakedScale)); }

    // ------------------------------------------------------------------ low-level draw

    private static void blit(GuiGraphics c, Identifier id, int x, int y, int w, int h,
                             int u, int v, int rw, int rh, int texW, int texH, int color) {
        c.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (float) u, (float) v, w, h, rw, rh, texW, texH, color);
    }

    private static float cov(float d) { return Math.max(0f, Math.min(1f, 0.5f - d)); }

    // ------------------------------------------------------------------ mask baking

    /** kind 0 = filled quarter disc, 1 = ring (width bw), 2 = glow */
    private static TextureFactory.Tex corner(int corner, int rGui, int bwGui, int kind, int blurGui) {
        checkScale();
        long key = (long) corner | ((long) rGui << 2) | ((long) bwGui << 14) | ((long) kind << 24) | ((long) blurGui << 28);
        TextureFactory.Tex t = MASKS.get(key);
        if (t != null) return t;

        int n = dev(rGui);
        int size = kind == 2 ? dev(rGui + blurGui) : n;
        t = TextureFactory.create("m" + kind, size, size);
        float bw = dev(Math.max(bwGui, 1));
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int qx = (corner == TR || corner == BR) ? size - 1 - px : px;
                int qy = (corner == BL || corner == BR) ? size - 1 - py : py;
                float a;
                if (kind == 2) {
                    // glow: signed distance to a rounded box whose corner arc has radius n,
                    // corner arc centre sits at (size, size) with the box edge n further in.
                    // arc centre is the inner corner of the patch; the rect edge is `blur` px from the quad edge
                    float dx = size - (qx + 0.5f), dy = size - (qy + 0.5f);
                    float dist = (float) Math.hypot(dx, dy) - n;
                    float blur = size - n;
                    float g = 1f - smooth(-blur * 0.25f, blur, dist);
                    a = g * g;
                } else {
                    float dist = (float) Math.hypot(n - (qx + 0.5f), n - (qy + 0.5f)) - n;
                    a = cov(dist);
                    if (kind == 1) {
                        float inner = cov((float) Math.hypot(n - (qx + 0.5f), n - (qy + 0.5f)) - (n - bw));
                        a = Math.max(0f, a - inner);
                    }
                }
                int ia = (int) (a * 255f + 0.5f);
                t.image.setPixel(px, py, (ia << 24) | 0x00FFFFFF);
            }
        }
        t.upload();
        MASKS.put(key, t);
        return t;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int vcol(int top, int bottom, float t) { return Colors.lerp(top, bottom, t); }

    // ------------------------------------------------------------------ rects

    public static void rect(GuiGraphics c, int x, int y, int w, int h, int color) {
        if (Colors.alpha(color) == 0 || w <= 0 || h <= 0) return;
        c.fill(x, y, x + w, y + h, color);
    }

    public static void rectGradient(GuiGraphics c, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        c.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** Uniform radius, flat colour. */
    public static void roundRect(GuiGraphics c, int x, int y, int w, int h, int r, int color) {
        roundRect(c, x, y, w, h, r, r, r, r, color, color);
    }

    /** Uniform radius, vertical gradient. */
    public static void roundRect(GuiGraphics c, int x, int y, int w, int h, int r, int top, int bottom) {
        roundRect(c, x, y, w, h, r, r, r, r, top, bottom);
    }

    /** Per-corner radii (tl, tr, br, bl) with vertical gradient. */
    public static void roundRect(GuiGraphics c, int x, int y, int w, int h,
                                 int tl, int tr, int br, int bl, int top, int bottom) {
        if (w <= 0 || h <= 0 || (Colors.alpha(top) == 0 && Colors.alpha(bottom) == 0)) return;
        int maxR = Math.min(w, h) / 2;
        tl = Math.min(tl, maxR); tr = Math.min(tr, maxR); br = Math.min(br, maxR); bl = Math.min(bl, maxR);
        int topBand = Math.max(tl, tr), botBand = Math.max(bl, br);
        float fh = Math.max(1, h);

        // corners
        drawCorner(c, TL, x, y, tl, vcol(top, bottom, tl * 0.5f / fh), 0, 0);
        drawCorner(c, TR, x + w - tr, y, tr, vcol(top, bottom, tr * 0.5f / fh), 0, 0);
        drawCorner(c, BR, x + w - br, y + h - br, br, vcol(top, bottom, 1f - br * 0.5f / fh), 0, 0);
        drawCorner(c, BL, x, y + h - bl, bl, vcol(top, bottom, 1f - bl * 0.5f / fh), 0, 0);

        // top band
        band(c, x + tl, y, w - tl - tr, topBand, y, h, top, bottom);
        if (tl < topBand) band(c, x, y + tl, tl, topBand - tl, y, h, top, bottom);
        if (tr < topBand) band(c, x + w - tr, y + tr, tr, topBand - tr, y, h, top, bottom);
        // middle
        band(c, x, y + topBand, w, h - topBand - botBand, y, h, top, bottom);
        // bottom band
        band(c, x + bl, y + h - botBand, w - bl - br, botBand, y, h, top, bottom);
        if (bl < botBand) band(c, x, y + h - botBand, bl, botBand - bl, y, h, top, bottom);
        if (br < botBand) band(c, x + w - br, y + h - botBand, br, botBand - br, y, h, top, bottom);
    }

    private static void band(GuiGraphics c, int bx, int by, int bw, int bh, int ry, int rh, int top, int bottom) {
        if (bw <= 0 || bh <= 0) return;
        float t0 = (by - ry) / (float) rh, t1 = (by + bh - ry) / (float) rh;
        c.fillGradient(bx, by, bx + bw, by + bh, vcol(top, bottom, t0), vcol(top, bottom, t1));
    }

    private static void drawCorner(GuiGraphics c, int corner, int x, int y, int r, int color, int bw, int kind) {
        if (r <= 0) return;
        TextureFactory.Tex t = corner(corner, r, bw, kind, 0);
        int s = t.image.getWidth();
        blit(c, t.id, x, y, r, r, 0, 0, s, s, s, s, color);
    }

    /** Horizontal gradient, uniform radius. Used for accent bars / toggles / sliders. */
    public static void roundRectH(GuiGraphics c, int x, int y, int w, int h, int r, int left, int right) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        drawCorner(c, TL, x, y, r, left, 0, 0);
        drawCorner(c, BL, x, y + h - r, r, left, 0, 0);
        drawCorner(c, TR, x + w - r, y, r, right, 0, 0);
        drawCorner(c, BR, x + w - r, y + h - r, r, right, 0, 0);
        int inner = w - 2 * r;
        int slices = Math.max(1, Math.min(inner, 24));
        for (int i = 0; i < slices; i++) {
            int sx = x + r + inner * i / slices, ex = x + r + inner * (i + 1) / slices;
            int col = Colors.lerp(left, right, (i + 0.5f) / slices);
            c.fill(sx, y, ex, y + h, col);
        }
        if (r > 0) {
            c.fill(x, y + r, x + r, y + h - r, left);
            c.fill(x + w - r, y + r, x + w, y + h - r, right);
        }
    }

    // ------------------------------------------------------------------ borders

    /** 1..n px border with a vertical gradient. */
    public static void border(GuiGraphics c, int x, int y, int w, int h, int r, int bw, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        float fh = Math.max(1, h);
        if (r > 0) {
            drawCorner(c, TL, x, y, r, vcol(top, bottom, r * 0.5f / fh), bw, 1);
            drawCorner(c, TR, x + w - r, y, r, vcol(top, bottom, r * 0.5f / fh), bw, 1);
            drawCorner(c, BR, x + w - r, y + h - r, r, vcol(top, bottom, 1f - r * 0.5f / fh), bw, 1);
            drawCorner(c, BL, x, y + h - r, r, vcol(top, bottom, 1f - r * 0.5f / fh), bw, 1);
        }
        c.fill(x + r, y, x + w - r, y + bw, top);                       // top edge
        c.fill(x + r, y + h - bw, x + w - r, y + h, bottom);            // bottom edge
        c.fillGradient(x, y + r, x + bw, y + h - r, vcol(top, bottom, r / fh), vcol(top, bottom, 1f - r / fh));
        c.fillGradient(x + w - bw, y + r, x + w, y + h - r, vcol(top, bottom, r / fh), vcol(top, bottom, 1f - r / fh));
    }

    public static void border(GuiGraphics c, int x, int y, int w, int h, int r, int color) {
        border(c, x, y, w, h, r, 1, color, color);
    }

    // ------------------------------------------------------------------ shadow / glow

    /**
     * Soft drop shadow or coloured glow behind a rounded rect. blur is in GUI px. The texture is a
     * baked 9-slice so a large glow costs 9 quads regardless of size.
     */
    public static void shadow(GuiGraphics c, int x, int y, int w, int h, int r, int blur, int color, int offX, int offY) {
        if (blur <= 0 || Colors.alpha(color) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        int m = blur;                       // margin around the rect
        int cs = r + m;                     // corner patch size in GUI px
        int gx = x - m + offX, gy = y - m + offY, gw = w + 2 * m, gh = h + 2 * m;
        TextureFactory.Tex t;
        t = corner(TL, r, 0, 2, m); int s = t.image.getWidth();
        blit(c, t.id, gx, gy, cs, cs, 0, 0, s, s, s, s, color);
        t = corner(TR, r, 0, 2, m);
        blit(c, t.id, gx + gw - cs, gy, cs, cs, 0, 0, s, s, s, s, color);
        t = corner(BR, r, 0, 2, m);
        blit(c, t.id, gx + gw - cs, gy + gh - cs, cs, cs, 0, 0, s, s, s, s, color);
        t = corner(BL, r, 0, 2, m);
        blit(c, t.id, gx, gy + gh - cs, cs, cs, 0, 0, s, s, s, s, color);
        // edges + centre: stretch the innermost row/column of the corner masks
        int midW = gw - 2 * cs, midH = gh - 2 * cs;
        if (midW > 0) {
            t = corner(TL, r, 0, 2, m);
            blit(c, t.id, gx + cs, gy, midW, cs, s - 1, 0, 1, s, s, s, color);
            t = corner(BL, r, 0, 2, m);
            blit(c, t.id, gx + cs, gy + gh - cs, midW, cs, s - 1, 0, 1, s, s, s, color);
        }
        if (midH > 0) {
            t = corner(TL, r, 0, 2, m);
            blit(c, t.id, gx, gy + cs, cs, midH, 0, s - 1, s, 1, s, s, color);
            t = corner(TR, r, 0, 2, m);
            blit(c, t.id, gx + gw - cs, gy + cs, cs, midH, 0, s - 1, s, 1, s, s, color);
        }
        if (midW > 0 && midH > 0) {
            t = corner(TL, r, 0, 2, m);
            blit(c, t.id, gx + cs, gy + cs, midW, midH, s - 1, s - 1, 1, 1, s, s, color);
        }
    }

    // ------------------------------------------------------------------ circles

    public static void circle(GuiGraphics c, int cx, int cy, int radius, int color) {
        if (radius <= 0) return;
        checkScale();
        long key = 0x7000000000L | radius;
        TextureFactory.Tex t = MASKS.get(key);
        if (t == null) {
            int n = dev(radius * 2);
            t = TextureFactory.create("c", n, n);
            float rr = n / 2f;
            for (int py = 0; py < n; py++) for (int px = 0; px < n; px++) {
                float d = (float) Math.hypot(px + 0.5f - rr, py + 0.5f - rr) - rr;
                t.image.setPixel(px, py, ((int) (cov(d) * 255f + 0.5f) << 24) | 0xFFFFFF);
            }
            t.upload();
            MASKS.put(key, t);
        }
        int s = t.image.getWidth();
        blit(c, t.id, cx - radius, cy - radius, radius * 2, radius * 2, 0, 0, s, s, s, s, color);
    }

    // ------------------------------------------------------------------ frosted panels

    /**
     * Frosted-glass style panel: translucent fill + 1px gradient border + soft shadow. Real background
     * blur is only available while a Screen is open (vanilla blur); in the in-game HUD this uses a
     * layered translucent tint, which is what keeps it cheap at 144+ FPS.
     */
    public static void panel(GuiGraphics c, int x, int y, int w, int h, int r, int tint, float alpha, boolean shadow) {
        if (shadow) shadow(c, x, y, w, h, r, 8, Colors.argb((int) (90 * alpha), 0, 0, 0), 0, 2);
        int top = Colors.mulAlpha(Colors.lerp(tint, 0xFFFFFFFF, 0.06f), alpha * 0.78f);
        int bot = Colors.mulAlpha(tint, alpha * 0.82f);
        roundRect(c, x, y, w, h, r, top, bot);
        border(c, x, y, w, h, r, 1, Colors.argb((int) (46 * alpha), 255, 255, 255), Colors.argb((int) (12 * alpha), 255, 255, 255));
    }

    // ------------------------------------------------------------------ clip + transform helpers

    public static void scissor(GuiGraphics c, int x, int y, int w, int h) { c.enableScissor(x, y, x + w, y + h); }
    public static void endScissor(GuiGraphics c) { c.disableScissor(); }

    /** Draw something scaled around a centre point (open animations, press-shrink, hover lift). */
    public static void pushScale(GuiGraphics c, float cx, float cy, float s) {
        c.pose().pushMatrix();
        c.pose().translate(cx, cy);
        c.pose().scale(s, s);
        c.pose().translate(-cx, -cy);
    }

    public static void pushTranslate(GuiGraphics c, float dx, float dy) {
        c.pose().pushMatrix();
        c.pose().translate(dx, dy);
    }

    public static void pop(GuiGraphics c) { c.pose().popMatrix(); }

    /** Draw a bundled PNG (any size) into a GUI rect, tinted. */
    public static void image(GuiGraphics c, Identifier id, int x, int y, int w, int h, int texW, int texH, int tint) {
        blit(c, id, x, y, w, h, 0, 0, texW, texH, texW, texH, tint);
    }

    // ------------------------------------------------------------------ polygons

    private static final float[] XS = new float[64];

    /**
     * Filled convex-or-simple polygon via horizontal scanlines (even-odd). Edge pixels get fractional
     * coverage (alpha-scaled spans) so small shapes such as stars and rotating glyphs stay smooth.
     * Allocation-free. Points are GUI-space floats; rowStep > 1 trades accuracy for speed on large shapes.
     */
    public static void polygon(GuiGraphics c, float[] px, float[] py, int n, int color, int rowStep) {
        if (n < 3 || Colors.alpha(color) == 0) return;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) { minY = Math.min(minY, py[i]); maxY = Math.max(maxY, py[i]); }
        int y0 = (int) Math.floor(minY), y1 = (int) Math.ceil(maxY);
        int step = Math.max(1, rowStep);
        for (int y = y0; y < y1; y += step) {
            float sy = y + 0.5f * step;
            int k = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float ay = py[j], by = py[i];
                if ((ay <= sy) != (by <= sy)) {
                    float t = (sy - ay) / (by - ay);
                    if (k < XS.length) XS[k++] = px[j] + (px[i] - px[j]) * t;
                }
            }
            // insertion sort (k is tiny)
            for (int a = 1; a < k; a++) { float v = XS[a]; int b = a - 1; while (b >= 0 && XS[b] > v) { XS[b + 1] = XS[b]; b--; } XS[b + 1] = v; }
            for (int a = 0; a + 1 < k; a += 2) {
                float xa = XS[a], xb = XS[a + 1];
                int ia = (int) Math.floor(xa), ib = (int) Math.ceil(xb);
                if (ib <= ia) continue;
                if (ib - ia <= 2) { c.fill(ia, y, ib, y + step, Colors.mulAlpha(color, Math.min(1f, xb - xa))); continue; }
                float fa = 1f - (xa - ia), fb = 1f - (ib - xb);
                if (fa < 0.98f) c.fill(ia, y, ia + 1, y + step, Colors.mulAlpha(color, fa)); else ia--;
                if (fb < 0.98f) c.fill(ib - 1, y, ib, y + step, Colors.mulAlpha(color, fb)); else ib++;
                c.fill(ia + 1, y, ib - 1, y + step, color);
            }
        }
    }

    private static final float[] STAR_X = new float[10], STAR_Y = new float[10];

    /** 5-point star centred on (cx, cy). */
    public static void star(GuiGraphics c, float cx, float cy, float outer, int color, float rotation) {
        float inner = outer * 0.42f;
        for (int i = 0; i < 10; i++) {
            float r = (i & 1) == 0 ? outer : inner;
            double a = rotation - Math.PI / 2 + i * Math.PI / 5;
            STAR_X[i] = cx + (float) Math.cos(a) * r;
            STAR_Y[i] = cy + (float) Math.sin(a) * r;
        }
        polygon(c, STAR_X, STAR_Y, 10, color, 1);
    }

    public static void clearCache() {
        for (TextureFactory.Tex t : MASKS.values()) TextureFactory.destroy(t);
        MASKS.clear();
        bakedScale = -1;
    }
}
