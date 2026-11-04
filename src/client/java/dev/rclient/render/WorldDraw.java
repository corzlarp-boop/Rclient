package dev.rclient.render;

import dev.rclient.anim.Animator;
import dev.rclient.cosmetic.CosmeticManager;
import dev.rclient.fx.Particles;
import dev.rclient.mesh.CosmeticContext;
import dev.rclient.mesh.MeshBuilder;
import dev.rclient.module.ModuleManager;
import dev.rclient.visual.Effects;
import dev.rclient.visual.SkyModule;
import dev.rclient.visual.TrailsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * In-world rendering for everything RClient draws: sky dome, cosmetics, trails/glyphs and particles.
 *
 * !! This is the file most likely to need adjusting after a Minecraft/Fabric update: it uses the
 * WorldRenderEvents callback and RenderType factories (debugQuads, lightning, eyes, entityTranslucent*).
 * Everything else in the client is independent of those APIs. !!
 */
public final class WorldDraw {
    private WorldDraw() {}

    private static final MeshBuilder BODY = new MeshBuilder();
    private static final MeshBuilder FX = new MeshBuilder();
    private static final CosmeticContext CTX = new CosmeticContext();

    private static SkyModule SKY;
    private static TrailsModule TRAILS;
    private static Effects.LineGlyphs GLYPHS;
    private static Effects.KillFx KILL;
    private static boolean init;
    private static long lastNs;

    private static void init() {
        if (init) return;
        SKY = ModuleManager.get(SkyModule.class);
        TRAILS = ModuleManager.get(TrailsModule.class);
        GLYPHS = ModuleManager.get(Effects.LineGlyphs.class);
        KILL = ModuleManager.get(Effects.KillFx.class);
        init = true;
    }

    private static final float LX = -0.45f, LY = 0.65f, LZ = 0.62f;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    // ------------------------------------------------------------------ entry

    public static void render(PoseStack ms, MultiBufferSource consumers) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        init();

        if (ms == null || consumers == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        Particles.tick(dt);

        double cx = cam.position().x, cy = cam.position().y, cz = cam.position().z;
        double px = Mth.lerp(pt, p.xOld, p.getX());
        double py = Mth.lerp(pt, p.yOld, p.getY());
        double pz = Mth.lerp(pt, p.zOld, p.getZ());

        fillContext(p, pt, px, py, pz, cx, cy, cz);

        // sky first (depth-tested: only shows where nothing closer was drawn)
        if (SKY != null && SKY.isEnabled()) drawSky(ms, consumers, cam, mc);

        // cosmetics – hidden in first person
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        BODY.clear();
        if (!firstPerson && CosmeticManager.any()) {
            CosmeticManager.build(BODY, CTX);
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            drawMesh(ms, consumers, BODY, px - cx, py - cy, pz - cz, (float) Math.toRadians(-yaw));
        }

        // world-aligned effects (trails, glyphs)
        FX.clear();
        if (TRAILS != null && TRAILS.isEnabled()) TRAILS.build(FX, CTX);
        if (GLYPHS != null && GLYPHS.isEnabled()) GLYPHS.build(FX, CTX);
        if (FX.count > 0) drawMesh(ms, consumers, FX, px - cx, py - cy, pz - cz, 0f);

        drawParticles(ms, consumers, cam, cx, cy, cz);
    }

    private static void fillContext(LocalPlayer p, float pt, double px, double py, double pz, double cx, double cy, double cz) {
        CosmeticContext c = CTX;
        c.time = Animator.time();
        c.partial = pt;
        c.speed = (float) Math.hypot(p.getX() - p.xOld, p.getZ() - p.zOld);
        c.verticalVel = (float) p.getDeltaMovement().y;
        c.sneaking = p.isShiftKeyDown();
        c.sprinting = p.isSprinting();
        c.airborne = !p.onGround();
        c.inMenu = false;
        c.px = px; c.py = py; c.pz = pz;
        c.camX = cx; c.camY = cy; c.camZ = cz;
    }

    // ------------------------------------------------------------------ mesh

    private static void drawMesh(PoseStack ms, MultiBufferSource consumers, MeshBuilder m, double ox, double oy, double oz, float yawRad) {
        float cy = (float) Math.cos(yawRad), sy = (float) Math.sin(yawRad);
        Matrix4f mat = ms.last().pose();
        VertexConsumer solid = consumers.getBuffer(RenderTypes.debugQuads());
        for (int pass = 0; pass < 2; pass++) {
            boolean additive = pass == 1;
            VertexConsumer vc = additive ? consumers.getBuffer(RenderTypes.lightning()) : solid;
            for (int i = 0; i < m.count; i++) {
                int f = m.flags[i];
                if (((f & MeshBuilder.ADDITIVE) != 0) != additive) continue;
                int col = m.color[i];
                if (Colors.alpha(col) == 0) continue;
                if ((f & MeshBuilder.EMISSIVE) == 0) col = shade(m, i, col, cy, sy);
                int b = i * 12;
                for (int k = 0; k < 4; k++) {
                    float x = m.v[b + k * 3], y = m.v[b + k * 3 + 1], z = m.v[b + k * 3 + 2];
                    float rx = x * cy + z * sy, rz = -x * sy + z * cy;
                    vc.addVertex(mat, (float) (ox + rx), (float) (oy + y), (float) (oz + rz)).setColor(col);
                }
                // second winding so ribbons/membranes are visible from both sides
                for (int k = 3; k >= 0; k--) {
                    float x = m.v[b + k * 3], y = m.v[b + k * 3 + 1], z = m.v[b + k * 3 + 2];
                    float rx = x * cy + z * sy, rz = -x * sy + z * cy;
                    vc.addVertex(mat, (float) (ox + rx), (float) (oy + y), (float) (oz + rz)).setColor(col);
                }
            }
            if (consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch(additive ? RenderTypes.lightning() : RenderTypes.debugQuads());
        }
    }

    private static int shade(MeshBuilder m, int i, int base, float cy, float sy) {
        int b = i * 12;
        float ax = m.v[b + 3] - m.v[b], ay = m.v[b + 4] - m.v[b + 1], az = m.v[b + 5] - m.v[b + 2];
        float bx = m.v[b + 6] - m.v[b], by = m.v[b + 7] - m.v[b + 1], bz = m.v[b + 8] - m.v[b + 2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6f) return base;
        nx /= nl; ny /= nl; nz /= nl;
        float rx = nx * cy + nz * sy, rz = -nx * sy + nz * cy;
        float l = Math.abs(rx * LX + ny * LY + rz * LZ);
        return Colors.scale(base, 0.45f + 0.6f * l);
    }

    // ------------------------------------------------------------------ sky

    private static void drawSky(PoseStack ms, MultiBufferSource consumers, Camera cam, Minecraft mc) {
        float R = Math.max(120f, Math.min(420f, mc.options.getEffectiveRenderDistance() * 16f * 2.4f));
        SkyDome.draw(ms, consumers, SKY, R);
    }

    // ------------------------------------------------------------------ particles

    private static final Identifier[] TEX = new Identifier[16];
    private static final boolean[] TADD = new boolean[16];

    private static void drawParticles(PoseStack ms, MultiBufferSource consumers, Camera cam, double cx, double cy, double cz) {
        int n = 0;
        for (Particles.P p : Particles.POOL) {
            if (!p.alive || p.tex == null) continue;
            boolean seen = false;
            for (int i = 0; i < n; i++) if (TEX[i] == p.tex && TADD[i] == p.additive) { seen = true; break; }
            if (!seen && n < TEX.length) { TEX[n] = p.tex; TADD[n] = p.additive; n++; }
        }
        if (n == 0) return;

        float yaw = (float) Math.toRadians(mc().player.getViewYRot(1f)), pitch = (float) Math.toRadians(mc().player.getViewXRot(1f));
        float fx = -Mth.sin(yaw) * Mth.cos(pitch), fy = -Mth.sin(pitch), fz = Mth.cos(yaw) * Mth.cos(pitch);
        float rx = -Mth.cos(yaw), ry = 0, rz = -Mth.sin(yaw);
        float ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        Matrix4f mat = ms.last().pose();

        for (int t = 0; t < n; t++) {
            var layer = TADD[t] ? RenderTypes.eyes(TEX[t]) : RenderTypes.entityTranslucent(TEX[t]);
            VertexConsumer vc = consumers.getBuffer(layer);
            for (Particles.P p : Particles.POOL) {
                if (!p.alive || p.tex != TEX[t] || p.additive != TADD[t]) continue;
                float k = p.life / p.maxLife;
                float u = 1f - k;
                float a = k < 0.1f ? k * 10f * (1f - k) : u * (0.4f + 0.6f * u);       // quick fade-in, soft fade-out
                if (p.twinkle > 0f) a *= 1f - p.twinkle * (0.5f + 0.5f * Mth.sin(p.life * 18f + p.seed * 30f));
                int col = Colors.mulAlpha(p.color2 != 0 ? Colors.lerp(p.color, p.color2, k) : p.color, a);
                float s = p.size + (p.sizeEnd - p.size) * k;
                if (p.ring) s = p.sizeEnd * (float) (1 - Math.pow(1 - k, 3)) + 0.1f;
                if (s <= 0.001f) continue;
                float x = (float) (p.x - cx), y = (float) (p.y - cy), z = (float) (p.z - cz);
                float ax, ay, az, bx, by, bz;
                if (p.ring) { ax = s; ay = 0; az = 0; bx = 0; by = 0; bz = s; y += 0.02f; }
                else {
                    float cr = Mth.cos(p.rot), sr = Mth.sin(p.rot);
                    ax = (rx * cr + ux * sr) * s; ay = (ry * cr + uy * sr) * s; az = (rz * cr + uz * sr) * s;
                    bx = (-rx * sr + ux * cr) * s; by = (-ry * sr + uy * cr) * s; bz = (-rz * sr + uz * cr) * s;
                }
                pv(vc, mat, x - ax - bx, y - ay - by, z - az - bz, 0, 1, col);
                pv(vc, mat, x + ax - bx, y + ay - by, z + az - bz, 1, 1, col);
                pv(vc, mat, x + ax + bx, y + ay + by, z + az + bz, 1, 0, col);
                pv(vc, mat, x - ax + bx, y - ay + by, z - az + bz, 0, 0, col);
            }
            if (consumers instanceof MultiBufferSource.BufferSource imm) imm.endBatch(layer);
        }
    }

    private static void pv(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float u, float v, int col) {
        vc.addVertex(mat, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
