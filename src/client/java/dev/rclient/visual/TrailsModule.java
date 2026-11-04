package dev.rclient.visual;

import dev.rclient.mesh.CosmeticContext;
import dev.rclient.mesh.MeshBuilder;
import dev.rclient.mesh.MeshProvider;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.setting.*;

/**
 * Glowing ribbon trails: N parallel smooth bands following the player's path with a bloom layer,
 * gradient colour and fade-out, plus an optional rotating geometric glyph on the ground.
 * Built into the world-aligned effect mesh (origin = player feet). Visual only.
 */
public final class TrailsModule extends Module implements MeshProvider {
    public final ColorSetting color = add(new ColorSetting("Color", 0xFFE5383B, 0xFF7A5CFF, true));
    public final NumberSetting bands = add(new NumberSetting("Bands", 3, 1, 6, 1));
    public final NumberSetting width = add(new NumberSetting("Width", 0.07, 0.02, 0.2, 0.005));
    public final NumberSetting length = add(new NumberSetting("Length", 1.1, 0.3, 3.0, 0.05)).desc("Seconds before the trail fades out");
    public final BoolSetting bloom = add(new BoolSetting("Bloom", true));
    public final BoolSetting glyph = add(new BoolSetting("Ground glyph", true));
    public final ModeSetting glyphShape = add(new ModeSetting("Glyph shape", "Hexagram", "Hexagram", "Triangle", "Square", "Pentagram")).visibleWhen(glyph::on);
    public final NumberSetting glyphSize = add(new NumberSetting("Glyph size", 0.9, 0.4, 2.0, 0.05)).visibleWhen(glyph::on);

    private static final int N = 96;
    private final double[] hx = new double[N], hy = new double[N], hz = new double[N];
    private final float[] ht = new float[N];
    private int head, size;
    private float lastSample;
    private boolean hadTime;

    public TrailsModule() {
        super("Trails", "Glowing ribbon trails behind you and a rotating ground glyph", Category.VISUALS);
        addQuality();
    }

    @Override protected void onDisable() { size = 0; head = 0; }

    private void record(CosmeticContext c) {
        float interval = 1f / (30f + 15f * qualityLevel());
        if (c.time < lastSample) lastSample = c.time;           // clock reset
        if (!hadTime || c.time - lastSample >= interval) {
            lastSample = c.time; hadTime = true;
            head = (head + 1) % N;
            hx[head] = c.px; hy[head] = c.py; hz[head] = c.pz; ht[head] = c.time;
            if (size < N) size++;
        } else if (size > 0) {                                   // keep the newest point glued to the player
            hx[head] = c.px; hy[head] = c.py; hz[head] = c.pz;
        }
    }

    @Override public void build(MeshBuilder b, CosmeticContext c) {
        record(c);
        int nb = bands.i();
        float life = length.f();
        int ADD = MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE;

        // ---- ribbons ----
        for (int band = 0; band < nb; band++) {
            float bt = nb == 1 ? 0.5f : band / (float) (nb - 1);
            float yOff = 0.25f + bt * 1.25f;
            float side = (bt - 0.5f) * 0.0f;
            double pxp = 0, pyp = 0, pzp = 0; float pw = 0, pa = 0; boolean havePrev = false;
            double pwx = 0, pwy = 0, pwz = 0;
            for (int s = 0; s < size; s++) {
                int i = Math.floorMod(head - s, N);
                float age = c.time - ht[i];
                if (age > life) break;
                float f = 1f - age / life;                         // 1 newest -> 0 oldest
                double x = hx[i] - c.px, y = hy[i] - c.py + yOff + (float) Math.sin(c.time * 2f + band + s * 0.15f) * 0.02f * (1f - f), z = hz[i] - c.pz;
                // camera-facing width vector
                double dx = x + c.px - c.camX, dy = y + c.py - c.camY, dz = z + c.pz - c.camZ;
                double tx, ty, tz;
                if (havePrev) { tx = x - pxp; ty = y - pyp; tz = z - pzp; } else { tx = 0; ty = 0; tz = 1; }
                double wx = ty * dz - tz * dy, wy = tz * dx - tx * dz, wz = tx * dy - ty * dx;
                double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (wl < 1e-6) { wx = 0; wy = 1; wz = 0; wl = 1; }
                float w = width.f() * (0.25f + 0.75f * f);
                wx = wx / wl * w; wy = wy / wl * w; wz = wz / wl * w;
                int col = Colors.mulAlpha(color.at(1f - f), 0.75f * f);
                if (havePrev) {
                    b.quad((float) (pxp - pwx), (float) (pyp - pwy), (float) (pzp - pwz), (float) (pxp + pwx), (float) (pyp + pwy), (float) (pzp + pwz),
                           (float) (x + wx), (float) (y + wy), (float) (z + wz), (float) (x - wx), (float) (y - wy), (float) (z - wz), col, ADD);
                    if (bloom.on() && qualityLevel() >= 2) {
                        int g = Colors.mulAlpha(color.at(1f - f), 0.22f * f);
                        b.quad((float) (pxp - pwx * 3.2), (float) (pyp - pwy * 3.2), (float) (pzp - pwz * 3.2), (float) (pxp + pwx * 3.2), (float) (pyp + pwy * 3.2), (float) (pzp + pwz * 3.2),
                               (float) (x + wx * 3.2), (float) (y + wy * 3.2), (float) (z + wz * 3.2), (float) (x - wx * 3.2), (float) (y - wy * 3.2), (float) (z - wz * 3.2), g, ADD);
                    }
                }
                pxp = x; pyp = y; pzp = z; pwx = wx; pwy = wy; pwz = wz; havePrev = true;
            }
        }

        // ---- ground glyph ----
        if (glyph.on()) {
            float r = glyphSize.f();
            float rot = c.time * 0.9f;
            int col = Colors.mulAlpha(color.at(0), 0.8f), col2 = Colors.mulAlpha(color.at(1), 0.7f);
            switch (glyphShape.get()) {
                case "Triangle" -> { poly(b, 3, r, rot, col, 0); poly(b, 3, r * 0.6f, -rot, col2, 0); }
                case "Square" -> { poly(b, 4, r, rot, col, 0); poly(b, 4, r * 0.7f, -rot * 1.3f, col2, 0); }
                case "Pentagram" -> star(b, 5, 2, r, rot, col);
                default -> { poly(b, 3, r, rot, col, 0); poly(b, 3, r, rot + (float) Math.PI, col2, 0); poly(b, 6, r * 1.12f, -rot * 0.5f, Colors.mulAlpha(col, 0.5f), 0); }
            }
            poly(b, 24, r * 1.25f, 0, Colors.mulAlpha(col, 0.35f), 0);
        }
    }

    private static void poly(MeshBuilder b, int n, float r, float rot, int col, float y) {
        int ADD = MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE;
        for (int i = 0; i < n; i++) {
            float a0 = rot + (float) (i * Math.PI * 2 / n), a1 = rot + (float) ((i + 1) * Math.PI * 2 / n);
            line(b, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, col, ADD);
        }
    }

    private static void star(MeshBuilder b, int pts, int skip, float r, float rot, int col) {
        int ADD = MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE;
        for (int i = 0; i < pts; i++) {
            float a0 = rot + (float) (i * Math.PI * 2 / pts), a1 = rot + (float) (((i + skip) % pts) * Math.PI * 2 / pts);
            line(b, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, col, ADD);
        }
        poly(b, 24, r * 1.1f, 0, Colors.mulAlpha(col, 0.4f), 0);
    }

    /** Flat line on the ground (xz plane) a hair above the surface. */
    private static void line(MeshBuilder b, float x0, float z0, float x1, float z1, int col, int flag) {
        float dx = x1 - x0, dz = z1 - z0;
        float l = (float) Math.sqrt(dx * dx + dz * dz) + 1e-5f;
        float nx = -dz / l * 0.018f, nz = dx / l * 0.018f;
        b.quad(x0 - nx, 0.03f, z0 - nz, x0 + nx, 0.03f, z0 + nz, x1 + nx, 0.03f, z1 + nz, x1 - nx, 0.03f, z1 - nz, col, flag);
        float gx = nx * 3.5f, gz = nz * 3.5f;
        b.quad(x0 - gx, 0.028f, z0 - gz, x0 + gx, 0.028f, z0 + gz, x1 + gx, 0.028f, z1 + gz, x1 - gx, 0.028f, z1 - gz, Colors.mulAlpha(col, 0.25f), flag);
    }
}
