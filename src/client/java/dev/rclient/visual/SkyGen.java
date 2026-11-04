package dev.rclient.visual;


/**
 * CPU-baked procedural sky textures (equirectangular 512x256). Generated once when a style is picked.
 * The sky is animated by rotating the dome and drifting a colour tint, not by re-baking each frame.
 */
public final class SkyGen {
    private SkyGen() {}

    public static final int W = 512, H = 256;

    // ---- noise helpers ----
    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Tileable value noise across x with period px. */
    private static float vnoise(float x, float y, int px, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = hash(Math.floorMod(x0, px), y0, seed), b = hash(Math.floorMod(x0 + 1, px), y0, seed);
        float c = hash(Math.floorMod(x0, px), y0 + 1, seed), d = hash(Math.floorMod(x0 + 1, px), y0 + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float fbm(float x, float y, int period, int seed) {
        float v = 0, amp = 0.5f; int p = period;
        for (int o = 0; o < 5; o++) { v += vnoise(x, y, p, seed + o) * amp; x *= 2; y *= 2; p *= 2; amp *= 0.5f; }
        return v;
    }

    private static int pack(float r, float g, float b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

    /** Bakes a procedural style into ARGB pixels (equirectangular, tileable horizontally). */
    public static int[] bake(String style, int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            float v = y / (float) h;           // 0 = zenith, 1 = nadir
            for (int x = 0; x < w; x++) {
                float u = x / (float) w;
                out[y * w + x] = switch (style) {
                    case "Plasma" -> plasma(u, v);
                    case "Voronoi" -> voronoi(u, v);
                    case "Aurora" -> aurora(u, v);
                    case "Sunset" -> sunset(u, v);
                    default -> 0xFF000000;
                };
            }
        }
        return out;
    }

    private static int plasma(float u, float v) {
        float a = fbm(u * 6, v * 3, 6, 11), b = fbm(u * 6 + a * 4, v * 3 + a * 2, 6, 29);
        float s = (float) (Math.sin((a + b) * 7.0) * 0.5 + 0.5);
        float r = 30 + 200 * s * b, g = 10 + 90 * (1 - s) * a, bl = 80 + 175 * (0.4f + 0.6f * s);
        float vig = 0.55f + 0.45f * (float) Math.sin(v * Math.PI);
        return pack(r * vig, g * vig, bl * vig);
    }

    private static int voronoi(float u, float v) {
        int cells = 14;
        float px = u * cells * 2, py = v * cells;
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py);
        float d1 = 9, d2 = 9;
        for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            int gx = cx + i, gy = cy + j;
            int wx = Math.floorMod(gx, cells * 2);
            float fx = gx + hash(wx, gy, 5), fy = gy + hash(wx, gy, 91);
            float d = (float) Math.hypot(px - fx, py - fy);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
        }
        float edge = d2 - d1;
        float line = (float) Math.exp(-edge * 11.0);          // glowing cell edges
        float fill = 0.05f + 0.06f * (1f - d1);
        float r = 8 + 40 * line + 12 * fill, g = 14 + 130 * line, b = 30 + 255 * line + 30 * fill;
        return pack(r, g, b);
    }

    private static int aurora(float u, float v) {
        // night gradient
        float r = 4 + 10 * v, g = 8 + 18 * v, b = 22 + 34 * v;
        // a few ribbons across the upper half
        for (int k = 0; k < 3; k++) {
            float cy = 0.28f + k * 0.12f + (fbm(u * 3 + k * 7, 0.5f, 3, 40 + k) - 0.5f) * 0.22f;
            float band = (float) Math.exp(-Math.pow((v - cy) / (0.045f + k * 0.01f), 2));
            float flow = 0.5f + 0.5f * (float) Math.sin(u * Math.PI * 2 * (3 + k) + fbm(u * 8, v * 8, 8, 70) * 6);
            float k1 = band * (0.45f + 0.55f * flow);
            r += k1 * (40 + k * 70); g += k1 * (190 - k * 50); b += k1 * (120 + k * 70);
        }
        // faint stars
        float st = hash((int) (u * W * 1.0f), (int) (v * H), 3);
        if (st > 0.9965f) { float s = (st - 0.9965f) / 0.0035f * 200; r += s; g += s; b += s; }
        return pack(r, g, b);
    }

    private static int sunset(float u, float v) {
        // v: 0 zenith .. 0.5 horizon .. 1 nadir. Mirror below the horizon so reflections feel natural.
        float h = v < 0.5f ? v / 0.5f : (1f - v) / 0.5f;     // 0 at zenith, 1 at horizon
        float sunU = 0.25f, sunV = 0.47f;
        float dx = Math.min(Math.abs(u - sunU), 1f - Math.abs(u - sunU)) * 2f, dy = (v - sunV) * 1.2f;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float r = 22 + (255 - 22) * (float) Math.pow(h, 1.8), g = 18 + (120 - 18) * (float) Math.pow(h, 2.4), b = 70 + (95 - 70) * h;
        float sun = (float) Math.exp(-d * 11.0), halo = (float) Math.exp(-d * 3.2);
        r += 255 * sun + 120 * halo; g += 190 * sun + 50 * halo; b += 120 * sun + 15 * halo;
        float clouds = fbm(u * 8, v * 6, 8, 123);
        float cm = Math.max(0, clouds - 0.52f) * 2.5f * (1f - Math.abs(h - 0.55f));
        r += cm * 70; g += cm * 20; b -= cm * 25;
        return pack(r, g, b);
    }
}
