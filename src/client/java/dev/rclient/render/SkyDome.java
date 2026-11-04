package dev.rclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rclient.anim.Animator;
import dev.rclient.visual.SkyModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.Random;

/**
 * Fog-free sky dome. Everything is plain vertex colours (position_color pipelines have no distance fog, which is
 * what hid the old textured dome): a gradient dome, additive star sprites and thin constellation lines.
 * Vertex colours are recomputed each frame (a couple thousand cheap evaluations); nothing is allocated per frame.
 */
public final class SkyDome {
    private SkyDome() {}

    // ------------------------------------------------------------------ grid
    private static int gAz, gEl;
    private static float[] dx, dy, dz;
    private static int[] col;

    private static void ensureGrid(int az, int el) {
        if (gAz == az && gEl == el) return;
        gAz = az; gEl = el;
        int n = (az + 1) * (el + 1);
        dx = new float[n]; dy = new float[n]; dz = new float[n]; col = new int[n];
        for (int j = 0; j <= el; j++) {
            double e = Math.PI / 2 - j * Math.PI / el;
            float ce = (float) Math.cos(e), se = (float) Math.sin(e);
            for (int i = 0; i <= az; i++) {
                double a = i * Math.PI * 2 / az;
                int k = j * (az + 1) + i;
                dx[k] = (float) Math.cos(a) * ce; dy[k] = se; dz[k] = (float) Math.sin(a) * ce;
            }
        }
    }

    // ------------------------------------------------------------------ stars
    private static final int MAX_STARS = 900;
    private static float[] sx, sy, sz, t1x, t1y, t1z, t2x, t2y, t2z, ssize, smag, sphase;
    private static int[] scol;
    private static int[][] groups;
    private static float[] groupPhase;
    private static boolean starsReady;

    private static void buildStars() {
        Random r = new Random(20261104L);
        float[][] tmp = new float[MAX_STARS][];
        for (int i = 0; i < MAX_STARS; i++) {
            float y = r.nextFloat() * 2f - 1f;
            float a = r.nextFloat() * 6.2831855f;
            float rr = (float) Math.sqrt(1f - y * y);
            float mag = (float) Math.pow(r.nextFloat(), 2.6);          // few bright, many faint
            tmp[i] = new float[]{rr * (float) Math.cos(a), y, rr * (float) Math.sin(a), mag, r.nextFloat() * 6.28f, r.nextFloat()};
        }
        Arrays.sort(tmp, (p, q) -> Float.compare(q[3], p[3]));          // brightest first
        sx = new float[MAX_STARS]; sy = new float[MAX_STARS]; sz = new float[MAX_STARS];
        t1x = new float[MAX_STARS]; t1y = new float[MAX_STARS]; t1z = new float[MAX_STARS];
        t2x = new float[MAX_STARS]; t2y = new float[MAX_STARS]; t2z = new float[MAX_STARS];
        ssize = new float[MAX_STARS]; smag = new float[MAX_STARS]; sphase = new float[MAX_STARS];
        scol = new int[MAX_STARS];
        for (int i = 0; i < MAX_STARS; i++) {
            float[] s = tmp[i];
            sx[i] = s[0]; sy[i] = s[1]; sz[i] = s[2]; smag[i] = s[3]; sphase[i] = s[4];
            ssize[i] = 0.0016f + 0.0042f * s[3];
            // tangent basis with t1 x t2 = -dir (so the winding faces the viewer inside the sphere)
            float ux = 0, uy = 1, uz = 0;
            if (Math.abs(s[1]) > 0.95f) { ux = 1; uy = 0; }
            float cx = uy * s[2] - uz * s[1], cy = uz * s[0] - ux * s[2], cz = ux * s[1] - uy * s[0];
            float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-6f;
            cx /= cl; cy /= cl; cz /= cl;
            t1x[i] = cx; t1y[i] = cy; t1z[i] = cz;
            t2x[i] = cy * s[2] - cz * s[1]; t2y[i] = cz * s[0] - cx * s[2]; t2z[i] = cx * s[1] - cy * s[0];
            float tc = s[5];       // colour temperature: blue-white .. white .. warm
            scol[i] = tc < 0.3f ? 0xFFBFD6FF : tc < 0.75f ? 0xFFFFFFFF : 0xFFFFE2B8;
        }
        // constellations: greedy nearest-neighbour chains over the brightest stars above the horizon
        int cand = 170;
        boolean[] used = new boolean[cand];
        int[][] g = new int[12][];
        int ng = 0;
        Random cr = new Random(77);
        for (int attempt = 0; attempt < 80 && ng < 11; attempt++) {
            int a = cr.nextInt(cand);
            if (used[a] || sy[a] < 0.12f) continue;
            int[] chain = new int[7]; int len = 0;
            chain[len++] = a; used[a] = true;
            int cur = a;
            while (len < 7) {
                int best = -1; float bd = 0.9f;                          // cos(angle) threshold ~ 0.45 rad
                for (int k = 0; k < cand; k++) {
                    if (used[k] || sy[k] < 0.05f) continue;
                    float d = sx[cur] * sx[k] + sy[cur] * sy[k] + sz[cur] * sz[k];
                    if (d > bd) { bd = d; best = k; }
                }
                if (best < 0) break;
                // prefer not-too-close neighbours: skip if almost identical direction
                if (bd > 0.9995f) { used[best] = true; continue; }
                chain[len++] = best; used[best] = true; cur = best;
            }
            if (len >= 3) g[ng++] = Arrays.copyOf(chain, len);
        }
        groups = Arrays.copyOf(g, ng);
        groupPhase = new float[ng];
        for (int i = 0; i < ng; i++) groupPhase[i] = cr.nextFloat() * 6.28f;
        starsReady = true;
    }

    // ------------------------------------------------------------------ noise
    private static float h3(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float vn3(float x, float y, float z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        float a = lerp(h3(x0, y0, z0), h3(x0 + 1, y0, z0), fx), b = lerp(h3(x0, y0 + 1, z0), h3(x0 + 1, y0 + 1, z0), fx);
        float c = lerp(h3(x0, y0, z0 + 1), h3(x0 + 1, y0, z0 + 1), fx), d = lerp(h3(x0, y0 + 1, z0 + 1), h3(x0 + 1, y0 + 1, z0 + 1), fx);
        return lerp(lerp(a, b, fy), lerp(c, d, fy), fz);
    }

    private static float fbm3(float x, float y, float z) {
        return vn3(x, y, z) * 0.55f + vn3(x * 2.1f, y * 2.1f, z * 2.1f) * 0.3f + vn3(x * 4.3f, y * 4.3f, z * 4.3f) * 0.15f;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float sm(float e0, float e1, float x) { float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }
    private static int c255(float v) { return v <= 0 ? 0 : v >= 1 ? 255 : (int) (v * 255f); }

    // ------------------------------------------------------------------ galaxy colour field
    private static int galaxy(float x, float y, float z, float c, float s, float t, float tr, float tg, float tb, float br) {
        float rx = x * c + z * s, rz = -x * s + z * c, ry = y;
        // base gradient: teal horizon glow -> violet -> deep indigo zenith, dark below the horizon
        float mr = 0.20f + 0.05f * (float) Math.sin(t * 0.13f), mg = 0.07f, mb = 0.38f + 0.06f * (float) Math.cos(t * 0.10f);
        float r, g, b;
        if (ry >= 0) {
            if (ry < 0.38f) { float k = sm(0f, 0.38f, ry); r = lerp(0.05f, mr, k); g = lerp(0.30f, mg, k); b = lerp(0.46f, mb, k); }
            else { float k = sm(0.38f, 1f, ry); r = lerp(mr, 0.015f, k); g = lerp(mg, 0.01f, k); b = lerp(mb, 0.07f, k); }
        } else { float k = sm(0f, 0.45f, -ry); r = lerp(0.05f, 0.01f, k); g = lerp(0.30f, 0.02f, k); b = lerp(0.46f, 0.05f, k); }

        // galactic band with dust lanes
        float dn = 0.35f * rx + 0.80f * ry + 0.49f * rz;
        float band = (float) Math.exp(-(dn * dn) / 0.05f);
        float dust = fbm3(rx * 3f + 1.7f, ry * 3f, rz * 3f + t * 0.01f);
        float bandAmt = band * (0.30f + 1.25f * dust);
        r += 0.85f * bandAmt * 0.55f + 0.5f * band * band * 0.35f;
        g += 0.55f * bandAmt * 0.55f + 0.8f * band * band * 0.35f;
        b += 1.0f * bandAmt * 0.55f + 1.0f * band * band * 0.35f;

        // nebula clouds (slow drift)
        float n1 = fbm3(rx * 1.8f + 10f, ry * 1.8f, rz * 1.8f - t * 0.006f);
        float n2 = fbm3(rx * 2.6f, ry * 2.6f + 5f, rz * 2.6f + t * 0.008f);
        float a1 = sm(0.46f, 0.82f, n1) * 0.55f, a2 = sm(0.50f, 0.86f, n2) * 0.5f;
        r += 0.10f * a1 + 0.80f * a2; g += 0.55f * a1 + 0.15f * a2; b += 0.95f * a1 + 0.55f * a2;

        // bright galactic core glow
        float core = (float) Math.exp(-(1f - (0.62f * rx + 0.35f * ry - 0.70f * rz)) * 9f);
        r += 1.0f * core * 0.35f; g += 0.85f * core * 0.35f; b += 0.7f * core * 0.35f;

        return 0xFF000000 | (c255(r * br * tr) << 16) | (c255(g * br * tg) << 8) | c255(b * br * tb);
    }

    // ------------------------------------------------------------------ image colour field (bilinear)
    private static int sample(int[] px, float u, float v, float tr, float tg, float tb, float br) {
        int w = SkyModule.BW, h = SkyModule.BH;
        u = u - (float) Math.floor(u);
        // mirror horizontally so the flat image has no seam when it wraps around the dome
        u = u <= 0.5f ? u * 2f : 2f - u * 2f;
        float fx = u * (w - 1), fy = Math.max(0f, Math.min(1f, v)) * (h - 1);
        int x0 = (int) fx, y0 = (int) fy, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        float ax = fx - x0, ay = fy - y0;
        float r = 0, g = 0, b = 0;
        for (int k = 0; k < 4; k++) {
            int xx = (k & 1) == 0 ? x0 : x1, yy = (k & 2) == 0 ? y0 : y1;
            float wgt = ((k & 1) == 0 ? 1 - ax : ax) * ((k & 2) == 0 ? 1 - ay : ay);
            int p = px[yy * w + xx];
            r += ((p >> 16) & 255) * wgt; g += ((p >> 8) & 255) * wgt; b += (p & 255) * wgt;
        }
        return 0xFF000000 | (c255(r / 255f * br * tr) << 16) | (c255(g / 255f * br * tg) << 8) | c255(b / 255f * br * tb);
    }

    // ------------------------------------------------------------------ draw
    public static void draw(PoseStack ms, MultiBufferSource consumers, SkyModule sky, float R) {
        int q = sky.qualityLevel();
        int az = q <= 1 ? 40 : q == 2 ? 56 : 72, el = az / 2;
        ensureGrid(az, el);
        if (!starsReady) buildStars();

        float t = Animator.time();
        float spinAng = t * sky.speed.f() * 0.02f;
        float c = (float) Math.cos(spinAng), s = (float) Math.sin(spinAng);
        float br = sky.brightness.f();
        int tint = sky.cycleTint.on()
            ? Colors.hsb((t * 0.02f * Math.max(0.1f, sky.speed.f())) % 1f, 0.35f, 1f, 255)
            : sky.tint.primary();
        float tr = Colors.red(tint) / 255f, tg = Colors.green(tint) / 255f, tb = Colors.blue(tint) / 255f;

        int[] px = sky.pixels();
        int n = (az + 1) * (el + 1);
        if (px == null) {
            for (int k = 0; k < n; k++) col[k] = galaxy(dx[k], dy[k], dz[k], c, s, t, tr, tg, tb, br);
        } else {
            float uOff = spinAng / 6.2831855f;
            for (int j = 0; j <= el; j++) {
                float v = j / (float) el;
                for (int i = 0; i <= az; i++) col[j * (az + 1) + i] = sample(px, i / (float) az + uOff, v, tr, tg, tb, br);
            }
        }

        Matrix4f mat = ms.last().pose();
        VertexConsumer vc = consumers.getBuffer(RenderTypes.debugQuads());
        int stride = az + 1;
        for (int j = 0; j < el; j++) {
            for (int i = 0; i < az; i++) {
                int a = j * stride + i, b = a + 1, d = a + stride, e = d + 1;
                // two windings: visible from inside whatever the pipeline's culling does
                vtx(vc, mat, a, R); vtx(vc, mat, b, R); vtx(vc, mat, e, R); vtx(vc, mat, d, R);
                vtx(vc, mat, d, R); vtx(vc, mat, e, R); vtx(vc, mat, b, R); vtx(vc, mat, a, R);
            }
        }
        if (consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch(RenderTypes.debugQuads());

        if (sky.stars.on() || sky.constellations.on()) drawStars(mat, consumers, sky, R * 0.985f, c, s, t, br);
    }

    private static void vtx(VertexConsumer vc, Matrix4f mat, int k, float R) {
        vc.addVertex(mat, dx[k] * R, dy[k] * R, dz[k] * R).setColor(col[k]);
    }

    private static float rx(float x, float z, float c, float s) { return x * c + z * s; }
    private static float rz(float x, float z, float c, float s) { return -x * s + z * c; }

    private static void drawStars(Matrix4f mat, MultiBufferSource consumers, SkyModule sky, float R, float c, float s, float t, float br) {
        VertexConsumer vc = consumers.getBuffer(RenderTypes.lightning());
        int q = sky.qualityLevel();
        int count = (int) Math.max(260, Math.min(MAX_STARS, MAX_STARS * sky.starDensity.f() * (q <= 1 ? 0.4f : q == 2 ? 0.7f : 1f) * 0.65f));
        boolean tw = sky.twinkle.on();
        boolean drawStars = sky.stars.on();

        for (int i = 0; i < count; i++) {
            if (sy[i] < -0.12f) continue;
            float mag = smag[i];
            boolean inGroup = false;
            float tFlicker = tw ? 0.72f + 0.28f * (float) Math.sin(t * (0.9f + mag * 1.6f) + sphase[i]) : 1f;
            float horizon = sm(-0.12f, 0.12f, sy[i]);
            float base = (0.38f + 0.62f * mag) * tFlicker * horizon * Math.min(1.3f, br);
            if (!drawStars) continue;
            float cx = rx(sx[i], sz[i], c, s) * R, cy = sy[i] * R, cz = rz(sx[i], sz[i], c, s) * R;
            float ax = rx(t1x[i], t1z[i], c, s), ay = t1y[i], az = rz(t1x[i], t1z[i], c, s);
            float bx = rx(t2x[i], t2z[i], c, s), by = t2y[i], bz = rz(t2x[i], t2z[i], c, s);
            int rgb = scol[i] & 0xFFFFFF;
            float h = ssize[i] * R;
            quad(vc, mat, cx, cy, cz, ax, ay, az, bx, by, bz, h * 3.4f, h * 3.4f, (rgb) | (c255(base * 0.16f) << 24));
            quad(vc, mat, cx, cy, cz, ax, ay, az, bx, by, bz, h, h, rgb | (c255(base) << 24));
            if (mag > 0.45f) {
                float L = h * (3.5f + 5f * mag), w = h * 0.22f;
                int sp = rgb | (c255(base * 0.75f) << 24);
                quad(vc, mat, cx, cy, cz, ax, ay, az, bx, by, bz, L, w, sp);
                quad(vc, mat, cx, cy, cz, ax, ay, az, bx, by, bz, w, L, sp);
            }
        }

        if (sky.constellations.on() && groups != null) {
            float lw = R * 0.0011f;
            for (int g = 0; g < groups.length; g++) {
                int[] ch = groups[g];
                // each constellation fades in and out slowly
                float fade = 0.5f + 0.5f * (float) Math.sin(t * 0.18f + groupPhase[g]);
                float a = (0.18f + 0.34f * fade) * Math.min(1.3f, br);
                int lc = 0xAFCBFF | (c255(a) << 24);
                for (int k = 0; k + 1 < ch.length; k++) {
                    int i0 = ch[k], i1 = ch[k + 1];
                    if (sy[i0] < 0f || sy[i1] < 0f) continue;
                    float x0 = rx(sx[i0], sz[i0], c, s) * R, y0 = sy[i0] * R, z0 = rz(sx[i0], sz[i0], c, s) * R;
                    float x1 = rx(sx[i1], sz[i1], c, s) * R, y1 = sy[i1] * R, z1 = rz(sx[i1], sz[i1], c, s) * R;
                    // perpendicular to the segment within the sphere surface
                    float ex = x1 - x0, ey = y1 - y0, ez = z1 - z0;
                    float mx = x0 + x1, my = y0 + y1, mz = z0 + z1;
                    float px = ey * mz - ez * my, py = ez * mx - ex * mz, pz = ex * my - ey * mx;
                    float pl = (float) Math.sqrt(px * px + py * py + pz * pz) + 1e-6f;
                    px = px / pl * lw; py = py / pl * lw; pz = pz / pl * lw;
                    vc.addVertex(mat, x0 - px, y0 - py, z0 - pz).setColor(lc);
                    vc.addVertex(mat, x0 + px, y0 + py, z0 + pz).setColor(lc);
                    vc.addVertex(mat, x1 + px, y1 + py, z1 + pz).setColor(lc);
                    vc.addVertex(mat, x1 - px, y1 - py, z1 - pz).setColor(lc);
                    vc.addVertex(mat, x1 - px, y1 - py, z1 - pz).setColor(lc);
                    vc.addVertex(mat, x1 + px, y1 + py, z1 + pz).setColor(lc);
                    vc.addVertex(mat, x0 + px, y0 + py, z0 + pz).setColor(lc);
                    vc.addVertex(mat, x0 - px, y0 - py, z0 - pz).setColor(lc);
                }
                // brighter nodes on the connected stars
                for (int k = 0; k < ch.length; k++) {
                    int i0 = ch[k];
                    if (sy[i0] < 0f) continue;
                    float cx = rx(sx[i0], sz[i0], c, s) * R, cy = sy[i0] * R, cz = rz(sx[i0], sz[i0], c, s) * R;
                    float ax = rx(t1x[i0], t1z[i0], c, s), ay = t1y[i0], az = rz(t1x[i0], t1z[i0], c, s);
                    float bx = rx(t2x[i0], t2z[i0], c, s), by = t2y[i0], bz = rz(t2x[i0], t2z[i0], c, s);
                    float h = (ssize[i0] + 0.0022f) * R;
                    quad(vc, mat, cx, cy, cz, ax, ay, az, bx, by, bz, h * 2.2f, h * 2.2f, 0xCFE0FF | (c255(a * 0.5f) << 24));
                }
            }
        }
        if (consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch(RenderTypes.lightning());
    }

    /** Camera-facing (sphere-tangent) rectangle emitted with both windings. */
    private static void quad(VertexConsumer vc, Matrix4f mat, float cx, float cy, float cz,
                             float ax, float ay, float az, float bx, float by, float bz, float ha, float hb, int argb) {
        float x0 = cx - ax * ha - bx * hb, y0 = cy - ay * ha - by * hb, z0 = cz - az * ha - bz * hb;
        float x1 = cx + ax * ha - bx * hb, y1 = cy + ay * ha - by * hb, z1 = cz + az * ha - bz * hb;
        float x2 = cx + ax * ha + bx * hb, y2 = cy + ay * ha + by * hb, z2 = cz + az * ha + bz * hb;
        float x3 = cx - ax * ha + bx * hb, y3 = cy - ay * ha + by * hb, z3 = cz - az * ha + bz * hb;
        vc.addVertex(mat, x0, y0, z0).setColor(argb); vc.addVertex(mat, x1, y1, z1).setColor(argb);
        vc.addVertex(mat, x2, y2, z2).setColor(argb); vc.addVertex(mat, x3, y3, z3).setColor(argb);
        vc.addVertex(mat, x3, y3, z3).setColor(argb); vc.addVertex(mat, x2, y2, z2).setColor(argb);
        vc.addVertex(mat, x1, y1, z1).setColor(argb); vc.addVertex(mat, x0, y0, z0).setColor(argb);
    }
}
