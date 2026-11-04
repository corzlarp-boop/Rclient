package dev.rclient.render;

/** ARGB helpers. No allocation. */
public final class Colors {
    private Colors() {}

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int rgb(int rgb) { return 0xFF000000 | rgb; }

    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return (c >> 16) & 255; }
    public static int green(int c) { return (c >> 8) & 255; }
    public static int blue(int c) { return c & 255; }

    /** Replace alpha channel with a 0..1 factor multiplied into the existing alpha. */
    public static int mulAlpha(int c, float f) {
        int a = (int) (alpha(c) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int withAlpha(int c, int a) { return (clamp(a) << 24) | (c & 0xFFFFFF); }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
            (int) (alpha(a) + (alpha(b) - alpha(a)) * t),
            (int) (red(a) + (red(b) - red(a)) * t),
            (int) (green(a) + (green(b) - green(a)) * t),
            (int) (blue(a) + (blue(b) - blue(a)) * t));
    }

    /** Brighten (f>1) or darken (f<1) RGB keeping alpha. */
    public static int scale(int c, float f) {
        return argb(alpha(c), (int) (red(c) * f), (int) (green(c) * f), (int) (blue(c) * f));
    }

    public static int hsb(float h, float s, float v, int a) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, v);
        return (clamp(a) << 24) | (rgb & 0xFFFFFF);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    // ---- palette ----
    public static final int BG_TOP      = 0xFF0E0E10;
    public static final int BG_BOTTOM   = 0xFF151518;
    public static final int CARD        = 0xFF1A1A1E;
    public static final int CARD_HOVER  = 0xFF212126;
    public static final int STROKE      = 0x14FFFFFF;
    public static final int TEXT        = 0xFFF2F2F5;
    public static final int TEXT_DIM    = 0xFF8C8C96;
    public static final int TEXT_FAINT  = 0xFF5A5A63;
    public static final int WHITE       = 0xFFFFFFFF;
}
