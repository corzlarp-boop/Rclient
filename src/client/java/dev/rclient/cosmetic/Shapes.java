package dev.rclient.cosmetic;

import dev.rclient.mesh.MeshBuilder;

/** Reusable procedural shapes for cosmetics. Model space: feet origin, +Y up, player faces +Z. */
public final class Shapes {
    private Shapes() {}

    public static final float HEAD_Y = 1.62f;      // eye height / head centre
    public static final float HEAD_TOP = 2.0f;
    public static final float BACK_Z = -0.14f;     // just behind the torso
    public static final float BACK_Y = 1.38f;      // upper back

    /** Bat wing: arm bone with fingers and a membrane fan between them. flap in radians. */
    public static void batWing(MeshBuilder b, int side, float flap, int bone, int membrane, int glow) {
        b.push();
        b.translate(side * 0.1f, BACK_Y, BACK_Z);
        b.rotateY(side * (0.55f + flap * 0.5f));
        b.rotateZ(side * (-0.15f + flap * 0.2f));
        float len = 0.95f;
        // arm
        b.box(side * len / 2, 0.02f, 0, len / 2, 0.015f, 0.015f, bone, 0);
        // 4 fingers fanning out
        float[] ang = {0.95f, 0.45f, -0.05f, -0.55f};
        float tipX = side * len, tipY = 0.08f;
        float prevX = side * 0.05f, prevY = -0.05f;
        for (int i = 0; i < ang.length; i++) {
            float fl = 0.85f - i * 0.08f;
            float fx = tipX * 0.55f + side * (float) Math.cos(ang[i] * 0.9f) * fl * 0.75f;
            float fy = tipY + (float) Math.sin(ang[i]) * fl * 0.85f - 0.25f - i * 0.13f;
            b.ribbon(side * 0.55f, 0.04f, 0, fx, fy, 0, 0, 0.012f, 0, bone, 0);
            // membrane triangle between this finger and the previous one
            b.quad(side * 0.05f, -0.05f, 0, prevX, prevY, 0, fx, fy, 0, fx, fy, 0, membrane, 0);
            b.quad(side * 0.05f, -0.05f, 0, fx, fy, 0, prevX, prevY, 0, prevX, prevY, 0, membrane, 0);
            prevX = fx; prevY = fy;
        }
        b.quad(side * 0.05f, -0.05f, 0, side * 0.55f, 0.04f, 0, prevX, prevY, 0, side * 0.05f, -0.30f, 0, membrane, 0);
        // faint edge glow
        if (glow != 0) b.ribbon(side * 0.05f, -0.05f, 0, tipX, tipY, 0, 0, 0.01f, 0, glow, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
        b.pop();
    }

    /** Crystal wing: a fan of angular shards, brightest at the root. */
    public static void crystalWing(MeshBuilder b, int side, float flap, int c1, int c2, int glow) {
        b.push();
        b.translate(side * 0.08f, BACK_Y, BACK_Z);
        b.rotateY(side * (0.5f + flap * 0.35f));
        int shards = 6;
        for (int i = 0; i < shards; i++) {
            float t = i / (float) (shards - 1);
            float len = 0.45f + (1f - Math.abs(t - 0.35f)) * 0.75f;
            float a = 0.9f - t * 1.55f;
            b.push();
            b.rotateZ(side * a);
            int col = dev.rclient.render.Colors.lerp(c1, c2, t);
            float w = 0.07f - t * 0.02f;
            // a lopsided diamond shard
            b.quad(0, 0, 0, side * len * 0.3f, w, 0.01f, side * len, 0, 0, side * len * 0.3f, -w * 0.6f, -0.01f, col, 0);
            b.quad(0, 0, 0, side * len * 0.3f, -w * 0.6f, -0.01f, side * len, 0, 0, side * len * 0.3f, w, 0.01f, col, 0);
            if (glow != 0) b.ribbon(0, 0, 0, side * len, 0, 0, 0, 0.006f, 0, glow, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
            b.pop();
        }
        b.pop();
    }

    /**
     * Angel wing modelled on a classic feathered wing: four overlapping rows of tapered feathers (long flight
     * feathers at the back, short coverts along the leading edge) hanging from a curved arc, root at the shoulder
     * blade, tips sweeping down and outward. Each feather is two quads (shaded base + bright outer part) so the
     * layers read clearly. The wing is rooted behind the torso and angled back so it never clips the body.
     */
    public static void angelWing(MeshBuilder b, int side, float flap, float time) {
        b.push();
        b.translate(side * 0.11f, BACK_Y + 0.06f, BACK_Z - 0.04f);
        b.rotateY(side * (0.42f + flap * 0.30f));

        final int[] counts = {8, 9, 10, 11};
        final float[] lens = {1.04f, 0.78f, 0.54f, 0.31f};
        final float[] wids = {0.140f, 0.128f, 0.116f, 0.102f};

        // soft glow behind the wing
        b.quad(side * 0.02f, 0.30f, 0.02f, side * 0.50f, 0.34f, 0.02f, side * 0.46f, -0.95f, 0.02f, side * 0.06f, -0.70f, 0.02f,
               0x18FFFFFF, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);

        for (int r = 0; r < 4; r++) {
            int n = counts[r];
            float zOff = -r * 0.011f;
            for (int i = 0; i < n; i++) {
                float t = n == 1 ? 0.5f : i / (float) (n - 1);
                float hs = (float) Math.sin((r * 13 + i) * 12.9898) * 43758.547f;
                hs = hs - (float) Math.floor(hs);

                // pivot along the leading-edge arc, higher for the covert rows
                float px = 0.02f + 0.40f * t;
                float py = -0.02f + 0.30f * (float) Math.sin(t * 1.4f) + r * 0.065f;
                // fan: 0 = straight down, positive = outward; gentle shimmer + flap spread
                float phi = 0.03f + 0.30f * t + (float) Math.sin(time * 1.7f + t * 3.2f + r) * 0.018f * (0.5f + t) + flap * 0.14f * t;
                float len = lens[r] * (0.64f + 0.36f * (float) Math.sin(Math.PI * Math.min(1f, t * 0.85f + 0.12f))) * (0.95f + hs * 0.09f);
                float w = wids[r];

                float dxn = (float) Math.sin(phi), dyn = -(float) Math.cos(phi);
                float qx = -dyn, qy = dxn;                      // perpendicular in the wing plane
                float mx = px + dxn * len * 0.55f, my = py + dyn * len * 0.55f;
                float tx = px + dxn * len, ty = py + dyn * len;
                float w0 = w * 0.24f, w1 = w * 0.56f;
                float zb = zOff, zm = zOff - 0.006f, zt = zOff - 0.012f;

                float k = r / 3f;
                int shade = dev.rclient.render.Colors.lerp(0xFFB9BAC4, 0xFFEDEBEA, 0.30f + 0.30f * k + hs * 0.18f);
                int bright = dev.rclient.render.Colors.lerp(0xFFEEECEA, 0xFFFFFFFF, 0.45f + hs * 0.4f);
                int tipc = dev.rclient.render.Colors.lerp(0xFFF6F2EC, 0xFFFFFFFF, 0.5f + hs * 0.5f);

                b.quad(side * (px - qx * w0), py - qy * w0, zb, side * (px + qx * w0), py + qy * w0, zb,
                       side * (mx + qx * w1), my + qy * w1, zm, side * (mx - qx * w1), my - qy * w1, zm, shade, 0);
                b.quad(side * (mx - qx * w1), my - qy * w1, zm, side * (mx + qx * w1), my + qy * w1, zm,
                       side * tx, ty, zt, side * tx, ty, zt, r == 0 ? tipc : bright, 0);
                // centre shaft highlight on the long flight feathers
                if (r < 2) b.ribbon(side * px, py, zb - 0.002f, side * tx, ty, zt - 0.002f, 0, 0.0035f, 0, 0xFFFFFFFF, MeshBuilder.EMISSIVE);
            }
        }
        b.pop();
    }

    /** Two curved horns on the head. */
    public static void horns(MeshBuilder b, int color, int tip, float scale) {
        for (int side = -1; side <= 1; side += 2) {
            b.push();
            b.translate(side * 0.17f, HEAD_TOP - 0.08f, 0.02f);
            b.scale(scale, scale, scale);
            int segs = 6;
            float px = 0, py = 0, pz = 0, pw = 0.055f;
            for (int i = 1; i <= segs; i++) {
                float t = i / (float) segs;
                float nx = side * (t * 0.17f + t * t * 0.06f);
                float ny = t * 0.22f - t * t * 0.02f;
                float nz = -t * t * 0.07f;
                float w = pw * (1f - t * 0.85f);
                int col = dev.rclient.render.Colors.lerp(color, tip, t);
                b.quad(px - pw, py, pz, px + pw, py, pz, nx + w, ny, nz, nx - w, ny, nz, col, 0);
                b.quad(px, py, pz - pw, px, py, pz + pw, nx, ny, nz + w, nx, ny, nz - w, col, 0);
                px = nx; py = ny; pz = nz; pw = w;
            }
            b.pop();
        }
    }

    /** Flame hat: stacked flickering tongues on top of the head. Additive + emissive. */
    public static void flameHat(MeshBuilder b, float time, int inner, int outer, float scale) {
        b.push();
        b.translate(0, HEAD_TOP + 0.02f, 0);
        b.scale(scale, scale, scale);
        int tongues = 9;
        for (int i = 0; i < tongues; i++) {
            float ph = i * 2.399f;
            float rx = (float) Math.cos(ph) * (0.05f + (i % 3) * 0.05f);
            float rz = (float) Math.sin(ph) * (0.05f + (i % 3) * 0.05f);
            float flick = 0.55f + 0.45f * (float) Math.sin(time * (5f + i * 0.9f) + ph);
            float h = (0.22f + (i % 4) * 0.07f) * (0.7f + 0.5f * flick);
            float sway = (float) Math.sin(time * 3.1f + ph) * 0.045f;
            float w = 0.085f;
            for (int layer = 0; layer < 2; layer++) {
                float hh = layer == 0 ? h : h * 0.62f, ww = layer == 0 ? w : w * 0.55f;
                int col = layer == 0 ? outer : inner;
                b.quad(rx - ww, 0, rz, rx + ww, 0, rz, rx + sway, hh, rz, rx + sway, hh, rz, col, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
                b.quad(rx, 0, rz - ww, rx, 0, rz + ww, rx + sway, hh, rz + sway, rx + sway, hh, rz + sway, col, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
            }
        }
        b.pop();
    }

    /** Glowing ring floating above the head. */
    public static void halo(MeshBuilder b, float time, int color, float radius) {
        b.push();
        b.translate(0, HEAD_TOP + 0.28f + (float) Math.sin(time * 1.6f) * 0.015f, 0);
        b.rotateX(0.08f * (float) Math.sin(time * 0.9f));
        int seg = 20;
        float r0 = radius, r1 = radius + 0.045f;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (i * Math.PI * 2 / seg), a1 = (float) ((i + 1) * Math.PI * 2 / seg);
            b.quad((float) Math.cos(a0) * r0, 0, (float) Math.sin(a0) * r0, (float) Math.cos(a0) * r1, 0, (float) Math.sin(a0) * r1,
                   (float) Math.cos(a1) * r1, 0, (float) Math.sin(a1) * r1, (float) Math.cos(a1) * r0, 0, (float) Math.sin(a1) * r0,
                   color, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
        }
        b.pop();
    }

    /** Small spinning coin. */
    public static void coin(MeshBuilder b, float x, float y, float z, float spin, int face, int edge, float r) {
        b.push();
        b.translate(x, y, z);
        b.rotateY(spin);
        int seg = 10;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (i * Math.PI * 2 / seg), a1 = (float) ((i + 1) * Math.PI * 2 / seg);
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            b.tri(0, 0, 0.012f, x0, y0, 0.012f, x1, y1, 0.012f, face, 0);
            b.tri(0, 0, -0.012f, x1, y1, -0.012f, x0, y0, -0.012f, face, 0);
            b.quad(x0, y0, -0.012f, x1, y1, -0.012f, x1, y1, 0.012f, x0, y0, 0.012f, edge, 0);
        }
        b.pop();
    }

    /** Pet: a small glowing orb with two eyes and a soft trail. Position is absolute model space. */
    public static void pet(MeshBuilder b, float x, float y, float z, float time, float tilt, int body, int glow) {
        b.push();
        b.translate(x, y, z);
        b.rotateX(tilt);
        b.rotateZ((float) Math.sin(time * 2.3f) * 0.08f);
        float r = 0.14f + (float) Math.sin(time * 3.1f) * 0.012f;
        b.box(0, 0, 0, r, r * 0.9f, r, body, 0);
        b.box(0, 0, 0, r * 1.5f, r * 1.4f, r * 1.5f, glow, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
        b.box(-r * 0.4f, r * 0.15f, r + 0.002f, r * 0.17f, r * 0.2f, 0.004f, 0xFFFFFFFF, MeshBuilder.EMISSIVE);
        b.box(r * 0.4f, r * 0.15f, r + 0.002f, r * 0.17f, r * 0.2f, 0.004f, 0xFFFFFFFF, MeshBuilder.EMISSIVE);
        b.box(0, r + 0.07f, 0, 0.012f, 0.07f, 0.012f, body, 0);
        b.box(0, r + 0.15f, 0, 0.03f, 0.03f, 0.03f, glow, MeshBuilder.EMISSIVE);
        b.pop();
    }

    /** Cape: a hanging strip that swings back with speed. */
    public static void cape(MeshBuilder b, float swing, float time, int top, int bottom, float sneakDrop) {
        b.push();
        b.translate(0, 1.44f - sneakDrop, -0.15f);
        int segs = 6;
        float h = 1.0f / segs;
        float px = 0, py = 0, pz = 0;
        float w = 0.29f;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float back = swing * t * t * 0.65f + (float) Math.sin(time * 2.2f + t * 3f) * 0.02f * t;
            float nz = -back - t * 0.04f, ny = -t * 0.95f;
            int col = dev.rclient.render.Colors.lerp(top, bottom, t);
            b.quad(-w, py, pz, w, py, pz, w, ny, nz, -w, ny, nz, col, 0);
            b.quad(w, py, pz, -w, py, pz, -w, ny, nz, w, ny, nz, col, 0);
            py = ny; pz = nz;
        }
        b.pop();
    }

    /** Knife replacing a held sword: blade + guard + handle. Origin at the grip, blade along +Y. */
    public static void knife(MeshBuilder b, int blade, int edge, int handle, int accent) {
        b.box(0, -0.07f, 0, 0.016f, 0.07f, 0.02f, handle, 0);
        b.box(0, 0.0f, 0, 0.055f, 0.012f, 0.03f, accent, 0);
        // blade: kite shape
        b.quad(-0.03f, 0.012f, 0.006f, 0.03f, 0.012f, 0.006f, 0.018f, 0.2f, 0.004f, 0f, 0.31f, 0f, blade, 0);
        b.quad(0.03f, 0.012f, -0.006f, -0.03f, 0.012f, -0.006f, 0f, 0.31f, 0f, 0.018f, 0.2f, -0.004f, blade, 0);
        b.quad(-0.03f, 0.012f, 0.006f, 0f, 0.31f, 0f, -0.018f, 0.2f, 0.004f, -0.03f, 0.012f, 0.006f, edge, 0);
        b.quad(0f, 0.012f, 0.0075f, 0f, 0.31f, 0.001f, 0.003f, 0.31f, 0.001f, 0.003f, 0.012f, 0.0075f, 0xFFFFFFFF, MeshBuilder.EMISSIVE);
    }
}
