package dev.rclient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.Resource;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Smooth TTF text renderer.
 *  - Reads assets/rclient/font/Inter-Regular.ttf (+ Inter-SemiBold.ttf / Inter-Bold.ttf if present);
 *    falls back to the JVM's "SansSerif" so the client still runs without the font files.
 *  - Glyphs are rasterised by Java2D with fractional metrics at the real device pixel size
 *    (px * GUI scale) into a lazily-filled atlas.
 *  - Each glyph is baked at 4 sub-pixel phases, so pen positions are quantised to 1/4 device px
 *    (true subpixel positioning instead of snapping to whole pixels).
 *  - Component is drawn in device-pixel space (matrix scaled by 1/guiScale) so quads are 1:1 with the screen.
 */
public final class RFont {
    public static final int PHASES = 4;
    private static final int ATLAS = 1024;

    private static Font BASE_REGULAR, BASE_SEMI, BASE_BOLD;
    private static final Map<String, RFont> CACHE = new HashMap<>();

    public enum Weight { REGULAR, SEMI, BOLD }

    private final Font awt;
    private final float guiSize;
    private final float devSize;
    private final double scale;
    private TextureFactory.Tex atlas;
    private int penX = 1, penY = 1, rowH = 0;
    private boolean dirty;
    private final Map<Long, Glyph> glyphs = new HashMap<>();
    private final float[] ascii = new float[256];
    private final boolean[] asciiSet = new boolean[256];
    private final Map<Character, Float> advances = new HashMap<>();
    private final FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
    public final float ascent, lineHeightGui;

    private static final class Glyph { int u, v, w, h; float offX, offY; }

    // ---------------------------------------------------------------- factory

    public static RFont get(float guiSize, Weight w) {
        double sc = RenderUtil.scale();
        String key = guiSize + ":" + w + ":" + sc;
        RFont f = CACHE.get(key);
        if (f == null) {
            // drop stale-scale entries
            if (!CACHE.isEmpty() && !CACHE.keySet().iterator().next().endsWith(":" + sc)) clear();
            f = new RFont(guiSize, w, sc);
            CACHE.put(key, f);
        }
        return f;
    }

    public static RFont regular(float size) { return get(size, Weight.REGULAR); }
    public static RFont semi(float size) { return get(size, Weight.SEMI); }
    public static RFont bold(float size) { return get(size, Weight.BOLD); }

    public static void clear() {
        for (RFont f : CACHE.values()) if (f.atlas != null) TextureFactory.destroy(f.atlas);
        CACHE.clear();
    }

    private static Font load(String path, int style) {
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager()
                .getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("rclient", path));
            if (r.isPresent()) {
                try (InputStream in = r.get().open()) {
                    return Font.createFont(Font.TRUETYPE_FONT, in);
                }
            }
        } catch (java.io.IOException | FontFormatException ignored) { }
        return new Font("SansSerif", style, 12);
    }

    private static Font base(Weight w) {
        if (BASE_REGULAR == null) {
            BASE_REGULAR = load("font/Inter-Regular.ttf", Font.PLAIN);
            Font semi = load("font/Inter-SemiBold.ttf", Font.BOLD);
            BASE_SEMI = semi;
            Font bold = load("font/Inter-Bold.ttf", Font.BOLD);
            BASE_BOLD = bold;
        }
        return switch (w) { case REGULAR -> BASE_REGULAR; case SEMI -> BASE_SEMI; case BOLD -> BASE_BOLD; };
    }

    private RFont(float guiSize, Weight w, double scale) {
        this.guiSize = guiSize;
        this.scale = scale;
        this.devSize = (float) (guiSize * scale);
        this.awt = base(w).deriveFont(devSize);
        java.awt.FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(awt);
        this.ascent = fm.getAscent();
        this.lineHeightGui = (float) ((fm.getAscent() + fm.getDescent()) / scale);
        this.atlas = TextureFactory.create("font", ATLAS, ATLAS);
    }

    // ---------------------------------------------------------------- metrics

    private float advance(char ch) {
        if (ch < 256 && asciiSet[ch]) return ascii[ch];
        float a = (float) awt.createGlyphVector(frc, String.valueOf(ch)).getGlyphMetrics(0).getAdvanceX();
        if (ch < 256) { ascii[ch] = a; asciiSet[ch] = true; } else advances.put(ch, a);
        return a;
    }

    private float advanceOf(char ch) {
        if (ch < 256) return advance(ch);
        Float a = advances.get(ch);
        return a != null ? a : advance(ch);
    }

    /** Width in GUI px. */
    public float width(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += advanceOf(s.charAt(i));
        return (float) (w / scale);
    }

    public float height() { return lineHeightGui; }

    public String truncate(String s, float maxWidth) {
        if (width(s) <= maxWidth) return s;
        String ell = "…";
        float limit = maxWidth - width(ell);
        float w = 0;
        int i = 0;
        for (; i < s.length(); i++) {
            float a = (float) (advanceOf(s.charAt(i)) / scale);
            if (w + a > limit) break;
            w += a;
        }
        return s.substring(0, i) + ell;
    }

    // ---------------------------------------------------------------- glyph baking

    private Glyph glyph(char ch, int phase) {
        long key = ((long) ch << 3) | phase;
        Glyph g = glyphs.get(key);
        if (g != null) return g;
        g = new Glyph();
        glyphs.put(key, g);

        java.awt.FontMetrics probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(awt);
        int pad = 2;
        int gw = (int) Math.ceil(advance(ch)) + pad * 2 + 2;
        int gh = probe.getAscent() + probe.getDescent() + pad * 2;
        BufferedImage bi = new BufferedImage(gw, gh, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setFont(awt);
        g2.setColor(java.awt.Color.WHITE);
        g2.drawString(String.valueOf(ch), pad + phase / (float) PHASES, pad + probe.getAscent());
        g2.dispose();

        if (penX + gw + 1 >= ATLAS) { penX = 1; penY += rowH + 1; rowH = 0; }
        if (penY + gh + 1 >= ATLAS) { // atlas full: reset (rare) – glyphs re-bake on demand
            glyphs.clear(); penX = 1; penY = 1; rowH = 0;
            for (int y = 0; y < ATLAS; y++) for (int x = 0; x < ATLAS; x++) atlas.image.setPixel(x, y, 0);
            glyphs.put(key, g);
        }
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) {
            int argb = bi.getRGB(x, y);
            int a = argb >>> 24;
            atlas.image.setPixel(penX + x, penY + y, (a << 24) | 0x00FFFFFF);
        }
        g.u = penX; g.v = penY; g.w = gw; g.h = gh;
        g.offX = -pad; g.offY = -pad - probe.getAscent();
        penX += gw + 1;
        rowH = Math.max(rowH, gh);
        dirty = true;
        return g;
    }

    // ---------------------------------------------------------------- drawing

    /** Draw left-aligned; (x, y) is the top-left of the line in GUI px (floats allowed). */
    public void draw(GuiGraphics ctx, String text, float x, float y, int color) {
        if (text.isEmpty() || Colors.alpha(color) == 0) return;
        float penDev = (float) (x * scale);
        float baseDev = (float) (y * scale) + ascent;
        // pre-bake so a single upload covers the whole string
        for (int i = 0; i < text.length(); i++) {
            float frac = penDev - (float) Math.floor(penDev);
            glyph(text.charAt(i), Math.min(PHASES - 1, (int) (frac * PHASES)));
            penDev += advanceOf(text.charAt(i));
        }
        if (dirty) { atlas.upload(); dirty = false; }

        penDev = (float) (x * scale);
        ctx.pose().pushMatrix();
        ctx.pose().scale((float) (1.0 / scale), (float) (1.0 / scale));
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            float floor = (float) Math.floor(penDev);
            int phase = Math.min(PHASES - 1, (int) ((penDev - floor) * PHASES));
            Glyph g = glyph(ch, phase);
            if (ch != ' ') {
                ctx.blit(RenderPipelines.GUI_TEXTURED, atlas.id,
                    (int) floor + (int) g.offX, (int) Math.round(baseDev) + (int) g.offY,
                    (float) g.u, (float) g.v, g.w, g.h, g.w, g.h, ATLAS, ATLAS, color);
            }
            penDev += advanceOf(ch);
        }
        ctx.pose().popMatrix();
    }

    public void drawCentered(GuiGraphics ctx, String text, float cx, float y, int color) {
        draw(ctx, text, cx - width(text) / 2f, y, color);
    }

    public void drawRight(GuiGraphics ctx, String text, float right, float y, int color) {
        draw(ctx, text, right - width(text), y, color);
    }

    /** Draw with a soft shadow for readability on busy backgrounds (HUD text). */
    public void drawShadow(GuiGraphics ctx, String text, float x, float y, int color) {
        draw(ctx, text, x + 0.75f, y + 0.75f, Colors.argb(Colors.alpha(color) * 0.55f > 0 ? (int) (Colors.alpha(color) * 0.55f) : 0, 0, 0, 0));
        draw(ctx, text, x, y, color);
    }
}
