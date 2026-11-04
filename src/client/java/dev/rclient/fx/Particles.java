package dev.rclient.fx;

import dev.rclient.core.ClientSettings;
import dev.rclient.render.Tex;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixed-size pool of world-space billboard particles (no per-frame allocation).
 * All particles are visual only. Each particle can carry a colour gradient (color -> color2), turbulence
 * (curl-like wandering), a swirl (velocity rotating around the vertical axis), twinkle and a random sprite
 * from a themed set, so bursts spread in every direction and no two particles move alike.
 */
public final class Particles {
    private Particles() {}

    public static final int MAX = 1500;

    public static final class P {
        public boolean alive;
        public double x, y, z;
        public float vx, vy, vz, drag, gravity;
        public float life, maxLife;
        public float size, sizeEnd;
        public int color;           // ARGB at birth
        public int color2;          // ARGB at death (0 = keep color and just fade)
        public float rot, rotSpeed;
        public float turb;          // wandering acceleration (blocks/s^2)
        public float swirl;         // rad/s the horizontal velocity rotates
        public float twinkle;       // 0..1 brightness flicker depth
        public float seed;
        public Identifier tex;
        public boolean additive;
        public boolean ring;        // ring: expands on the horizontal plane
    }

    public static final P[] POOL = new P[MAX];
    private static int cursor;
    static { for (int i = 0; i < MAX; i++) POOL[i] = new P(); }

    // ---- themed sprite sets (see the Kenney sheet) ----
    public static final Identifier[] SPARKLES = {
        Tex.kenney(1, 5), Tex.kenney(1, 2), Tex.kenney(1, 3), Tex.kenney(1, 9), Tex.kenney(1, 4)
    };
    public static final Identifier[] GLOWS = { Tex.kenney(2, 12), Tex.kenney(1, 3), Tex.kenney(1, 0) };
    public static final Identifier[] DEBRIS = { Tex.kenney(2, 13), Tex.kenney(2, 14), Tex.kenney(2, 12) };
    public static final Identifier[] FLAMES = { Tex.kenney(4, 2), Tex.kenney(4, 3), Tex.kenney(4, 4), Tex.kenney(4, 5) };
    public static final Identifier[] WISPS = { Tex.kenney(3, 6), Tex.kenney(3, 7), Tex.kenney(1, 3) };

    public static Identifier pick(Identifier[] set) { return set[ThreadLocalRandom.current().nextInt(set.length)]; }

    public static P spawn() {
        P p = null;
        for (int n = 0; n < MAX; n++) {
            cursor = (cursor + 1) % MAX;
            if (!POOL[cursor].alive) { p = POOL[cursor]; break; }
        }
        if (p == null) p = POOL[cursor];     // pool full: recycle
        p.alive = true; p.ring = false; p.drag = 0.9f; p.gravity = 0; p.rot = 0; p.rotSpeed = 0;
        p.color2 = 0; p.turb = 0; p.swirl = 0; p.twinkle = 0; p.seed = rnd();
        return p;
    }

    public static void tick(float dt) {
        float dragK = Math.min(1f, dt * 60f);
        for (P p : POOL) {
            if (!p.alive) continue;
            p.life += dt;
            if (p.life >= p.maxLife) { p.alive = false; continue; }
            float d = 1f - (1f - p.drag) * dragK;
            p.vx *= d; p.vy *= d; p.vz *= d;
            p.vy -= p.gravity * dt;
            if (p.turb > 0f) {
                float ph = p.life * 4f + p.seed * 20f;
                p.vx += (float) Math.sin(ph * 1.3f + (float) p.y * 2f) * p.turb * dt;
                p.vy += (float) Math.cos(ph * 0.9f + (float) p.x * 2f) * p.turb * dt * 0.6f;
                p.vz += (float) Math.sin(ph * 1.1f + (float) p.z * 2f + 1.7f) * p.turb * dt;
            }
            if (p.swirl != 0f) {
                float a = p.swirl * dt, c = (float) Math.cos(a), s = (float) Math.sin(a);
                float nx = p.vx * c - p.vz * s, nz = p.vx * s + p.vz * c;
                p.vx = nx; p.vz = nz;
            }
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            p.rot += p.rotSpeed * dt;
        }
    }

    /** Scaled count honoring quality + the global particle cap. */
    public static int count(int base, int qualityLevel) {
        float q = qualityLevel == 1 ? 0.35f : qualityLevel == 2 ? 0.7f : 1f;
        return Math.max(1, Math.round(base * q * ClientSettings.particleScale()));
    }

    // ------------------------------------------------------------------ emitters

    private static float rnd() { return ThreadLocalRandom.current().nextFloat(); }
    private static float rs() { return rnd() * 2f - 1f; }

    private static boolean isSparkle(Identifier t) {
        return t == Tex.SPARK || t == Tex.STAR_SOFT || t == Tex.STAR4;
    }

    /** Radial burst: sprites vary, speeds vary, some arc down, some drift and swirl. */
    public static void burst(double x, double y, double z, int n, float speed, float life, float size, int color, Identifier tex, boolean additive) {
        burst(x, y, z, n, speed, life, size, color, 0, tex, additive);
    }

    public static void burst(double x, double y, double z, int n, float speed, float life, float size, int color, int color2, Identifier tex, boolean additive) {
        for (int i = 0; i < n; i++) {
            P p = spawn();
            p.x = x + rs() * 0.05; p.y = y + rs() * 0.05; p.z = z + rs() * 0.05;
            float vx = rs(), vy = rs() * 0.9f + 0.15f, vz = rs();
            float l = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4f;
            float kind = rnd();                                   // 0..1 picks the personality of this particle
            float s = speed * (0.25f + rnd() * 0.85f) * (kind < 0.2f ? 1.45f : 1f);
            p.vx = vx / l * s; p.vy = vy / l * s; p.vz = vz / l * s;
            p.life = 0; p.maxLife = life * (0.5f + rnd() * 0.9f) * (kind > 0.8f ? 1.6f : 1f);
            p.size = size * (0.45f + rnd() * 1.1f) * (kind < 0.2f ? 0.65f : 1f); p.sizeEnd = p.size * (kind > 0.8f ? 0.9f : 0f);
            p.color = color; p.color2 = color2; p.additive = additive;
            p.drag = 0.90f + rnd() * 0.07f;
            p.rotSpeed = rs() * 4f;
            p.rot = rnd() * 6.28f;
            p.gravity = kind < 0.4f ? 1.2f + rnd() * 2.5f : (kind > 0.8f ? -0.35f : 0.2f);
            p.turb = kind > 0.55f ? 0.9f + rnd() * 1.6f : 0.15f;
            p.swirl = kind > 0.7f ? rs() * 3.2f : 0f;
            p.twinkle = 0.25f + rnd() * 0.5f;
            p.tex = isSparkle(tex) ? pick(kind > 0.85f ? GLOWS : SPARKLES) : tex;
        }
    }

    /** Soft motes that wander and swirl upward (auras, pearl trails, jump sparkles). */
    public static void motes(double x, double y, double z, int n, float spread, float rise, float life, float size, int color, int color2) {
        for (int i = 0; i < n; i++) {
            P p = spawn();
            p.x = x + rs() * spread; p.y = y + rs() * spread * 0.5f; p.z = z + rs() * spread;
            p.vx = rs() * 0.25f; p.vy = rise * (0.5f + rnd()); p.vz = rs() * 0.25f;
            p.life = 0; p.maxLife = life * (0.6f + rnd() * 0.8f);
            p.size = size * (0.5f + rnd() * 0.9f); p.sizeEnd = 0f;
            p.color = color; p.color2 = color2; p.additive = true;
            p.drag = 0.97f; p.turb = 1.4f + rnd(); p.swirl = rs() * 2.4f; p.twinkle = 0.5f; p.rotSpeed = rs() * 2f;
            p.tex = pick(rnd() < 0.55f ? GLOWS : SPARKLES);
        }
    }

    public static void ring(double x, double y, double z, float radius, float life, int color, Identifier tex) {
        P p = spawn();
        p.x = x; p.y = y; p.z = z; p.vx = p.vy = p.vz = 0;
        p.life = 0; p.maxLife = life;
        p.size = 0.2f; p.sizeEnd = radius;
        p.color = color; p.tex = tex; p.additive = true; p.ring = true; p.drag = 1f;
    }

    public static void glow(double x, double y, double z, float size, float life, int color, Identifier tex) {
        P p = spawn();
        p.x = x; p.y = y; p.z = z;
        p.vx = rs() * 0.15f; p.vy = rs() * 0.15f + 0.05f; p.vz = rs() * 0.15f;
        p.life = 0; p.maxLife = life;
        p.size = size * (0.85f + rnd() * 0.3f); p.sizeEnd = 0f;
        p.color = color; p.tex = tex; p.additive = true; p.drag = 0.95f;
        p.turb = 0.5f; p.swirl = rs(); p.twinkle = 0.2f;
    }
}
