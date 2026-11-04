package dev.rclient.visual;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.anim.Animator;
import dev.rclient.core.Theme;
import dev.rclient.fx.Particles;
import dev.rclient.mesh.CosmeticContext;
import dev.rclient.mesh.MeshBuilder;
import dev.rclient.mesh.MeshProvider;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.render.RenderUtil;
import dev.rclient.render.Tex;
import dev.rclient.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * All the small effect modules. Everything here is client-side visual/audio-local only: no packets are
 * sent or changed, no movement or combat is touched, and event callbacks always pass the original action through.
 */
public final class Effects {
    private Effects() {}

    private static Minecraft mc() { return Minecraft.getInstance(); }

    // ======================================================================= HitFX
    public static final class HitFx extends Module {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFFF5964, 0xFFFFC857, true));
        public final NumberSetting count = add(new NumberSetting("Particles", 14, 2, 40, 1));
        public final BoolSetting ring = add(new BoolSetting("Expanding ring", true));
        public final BoolSetting flash = add(new BoolSetting("Impact flash", true));
        public HitFx() { super("HitFX", "Particles and an expanding ring where you hit", Category.VISUALS); addQuality(); }

        public void onHit(Entity e) {
            double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.55, z = e.getZ();
            int n = Particles.count(count.i(), qualityLevel());
            for (int i = 0; i < n; i++) {
                float t = n <= 1 ? 0 : i / (float) (n - 1);
                Particles.burst(x, y, z, 1, 3.4f, 0.65f, 0.2f, color.at(t), Colors.mulAlpha(color.at(1f - t), 0.6f), Tex.SPARK, true);
            }
            Particles.motes(x, y, z, Math.max(2, n / 3), 0.25f, 0.6f, 0.8f, 0.22f, color.at(0), color.at(1));
            if (ring.on()) Particles.ring(x, e.getY() + 0.05, z, 1.1f, 0.45f, Colors.mulAlpha(color.at(0), 0.85f), Tex.RING);
            if (flash.on()) Particles.glow(x, y, z, 0.9f, 0.18f, Colors.mulAlpha(color.at(1), 0.9f), Tex.BURST);
        }
    }

    // ======================================================================= JumpFX
    public static final class JumpFx extends Module {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFF7AB8FF, 0xFFB78CFF, true));
        public final BoolSetting jump = add(new BoolSetting("On jump", true));
        public final BoolSetting land = add(new BoolSetting("On landing", true));
        private boolean wasGround = true;
        private float airTime;

        public JumpFx() { super("JumpFX", "Glowing rings and sparks when you jump and land", Category.VISUALS); addQuality(); }

        @Override public void onClientTick() {
            Player p = mc().player;
            if (p == null) return;
            boolean g = p.onGround();
            if (wasGround && !g && p.getDeltaMovement().y > 0.2 && jump.on()) {
                Particles.ring(p.getX(), p.getY() + 0.05, p.getZ(), 1.0f, 0.4f, Colors.mulAlpha(color.at(0), 0.8f), Tex.RING);
                spark(p, 0.6f);
            }
            if (!g) airTime += 0.05f;
            if (!wasGround && g && land.on()) {
                float s = Math.min(2.2f, 0.9f + airTime * 1.2f);
                if (airTime > 0.25f) {
                    Particles.ring(p.getX(), p.getY() + 0.05, p.getZ(), 1.6f * s, 0.5f, Colors.mulAlpha(color.at(1), 0.9f), Tex.RING_THICK);
                    spark(p, s);
                }
            }
            if (g) airTime = 0f;
            wasGround = g;
        }

        private void spark(Player p, float s) {
            int n = Particles.count((int) (8 * s), qualityLevel());
            for (int i = 0; i < n; i++) Particles.burst(p.getX(), p.getY() + 0.1, p.getZ(), 1, 2.2f * s, 0.6f, 0.16f, color.at(i / (float) n), color.at(1f - i / (float) n), Tex.STAR_SOFT, true);
            Particles.motes(p.getX(), p.getY() + 0.1, p.getZ(), Math.max(2, n / 2), 0.35f * s, 0.9f, 0.9f, 0.2f, color.at(0), color.at(1));
        }
    }

    // ======================================================================= KillFX
    public static final class KillFx extends Module {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFD27A, 0xFFFF4D5A, true));
        public final BoolSetting finisher = add(new BoolSetting("Cinematic finisher", true));
        public final NumberSetting intensity = add(new NumberSetting("Intensity", 1.0, 0.3, 2.0, 0.1));
        public final AnimatedFloat flash = new AnimatedFloat(0f, 5f);
        private Entity target; private long hitAt;

        public KillFx() { super("KillFX", "Burst of light and a cinematic flourish when a target dies", Category.VISUALS); addQuality(); }

        public void onHit(Entity e) { target = e; hitAt = System.currentTimeMillis(); }

        @Override public void onClientTick() {
            if (target == null) return;
            boolean dead = target.isRemoved() || !target.isAlive() || (target instanceof LivingEntity le && le.getHealth() <= 0f);
            if (System.currentTimeMillis() - hitAt > 4000) { target = null; return; }
            if (!dead) return;
            double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.5, z = target.getZ();
            float k = intensity.f();
            int n = Particles.count((int) (36 * k), qualityLevel());
            for (int i = 0; i < n; i++) Particles.burst(x, y, z, 1, 5f * k, 1.0f, 0.24f, color.at(i / (float) n), Colors.mulAlpha(color.at(1), 0.5f), Tex.SPARK, true);
            Particles.motes(x, y, z, Math.max(4, n / 2), 0.5f * k, 1.2f, 1.2f, 0.26f, color.at(0), color.at(1));
            Particles.ring(x, target.getY() + 0.05, z, 2.4f * k, 0.7f, Colors.mulAlpha(color.at(0), 0.9f), Tex.RING_THICK);
            Particles.ring(x, y, z, 1.6f * k, 0.55f, Colors.mulAlpha(color.at(1), 0.8f), Tex.RING);
            Particles.glow(x, y, z, 2.2f * k, 0.5f, Colors.mulAlpha(color.at(0), 0.9f), Tex.BURST);
            if (finisher.on()) { flash.snapTo(1f); flash.set(0f); }
            target = null;
        }

        /** Cinematic flourish: radial rays + vignette, screen-space. Called from the HUD layer. */
        public void renderOverlay(GuiGraphics ctx, int w, int h) {
            if (!isEnabled() || !finisher.on()) return;
            float f = flash.get();
            if (f < 0.01f) return;
            int a = (int) (200 * f * Math.min(1f, intensity.f()));
            int size = (int) (Math.max(w, h) * (1.0f + (1f - f) * 0.35f));
            ctx.pose().pushMatrix();
            ctx.pose().translate(w / 2f, h / 2f);
            ctx.pose().rotate((1f - f) * 0.5f);
            ctx.pose().translate(-w / 2f, -h / 2f);
            RenderUtil.image(ctx, Tex.BURST, (w - size) / 2, (h - size) / 2, size, size, 1832, 1744, Colors.argb((int) (a * 0.8f), Colors.red(color.at(0)), Colors.green(color.at(0)), Colors.blue(color.at(0))));
            ctx.pose().popMatrix();
            // bars (letterbox) slide in for the cinematic feel
            int bar = (int) (h * 0.07f * f);
            RenderUtil.rect(ctx, 0, 0, w, bar, Colors.argb((int) (230 * f), 0, 0, 0));
            RenderUtil.rect(ctx, 0, h - bar, w, bar, Colors.argb((int) (230 * f), 0, 0, 0));
            RenderUtil.rectGradient(ctx, 0, 0, w, h, Colors.argb((int) (60 * f), 0, 0, 0), Colors.argb((int) (110 * f), 0, 0, 0));
        }
    }

    // ======================================================================= Pearl particles
    public static final class PearlParticles extends Module {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFB78CFF, 0xFF4DD2FF, true));
        public final NumberSetting size = add(new NumberSetting("Size", 0.28, 0.1, 0.8, 0.02));
        private int t;
        public PearlParticles() { super("Pearl Particles", "Glowing trail behind ender pearls", Category.VISUALS); addQuality(); }

        @Override public void onClientTick() {
            if (mc().level == null) return;
            t++;
            for (Entity e : mc().level.entitiesForRendering()) {
                if (e.getType() != EntityType.ENDER_PEARL) continue;
                int n = Particles.count(2, qualityLevel());
                for (int i = 0; i < n; i++) {
                    float k = (t % 20) / 20f;
                    Particles.glow(e.getX(), e.getY() + 0.1, e.getZ(), size.f(), 0.7f, Colors.mulAlpha(color.at((k + i * 0.5f) % 1f), 0.85f), Tex.GLOW);
                }
                Particles.motes(e.getX(), e.getY() + 0.1, e.getZ(), 1, 0.12f, 0.15f, 0.9f, size.f() * 0.55f, color.at((t % 20) / 20f), color.at(1f - (t % 20) / 20f));
                if ((t & 1) == 0) Particles.glow(e.getX(), e.getY() + 0.1, e.getZ(), size.f() * 0.5f, 0.5f, 0xFFFFFFFF, Tex.STAR_SOFT);
            }
        }
    }

    // ======================================================================= Line glyphs
    public static final class LineGlyphs extends Module implements MeshProvider {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFE5383B, 0xFFFFC857, true));
        public final NumberSetting count = add(new NumberSetting("Lines", 10, 2, 28, 1));
        public final NumberSetting radius = add(new NumberSetting("Radius", 1.6, 0.8, 3.5, 0.1));
        public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1));
        public LineGlyphs() { super("Line Glyphs", "Animated angular lines drifting around you", Category.VISUALS); addQuality(); }

        /** World-aligned mesh around the player's feet. */
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            int n = Math.max(2, Math.round(count.i() * (qualityLevel() / 3f)));
            for (int i = 0; i < n; i++) {
                float seed = i * 12.9898f;
                float ang = c.time * speed.f() * (0.25f + (i % 5) * 0.06f) * ((i & 1) == 0 ? 1 : -1) + seed;
                float r = radius.f() * (0.65f + 0.35f * (float) Math.sin(seed * 3.1f));
                float y = 0.2f + (0.5f + 0.5f * (float) Math.sin(c.time * 0.5f * speed.f() + seed)) * 1.9f;
                int col = Colors.mulAlpha(color.at(i / (float) n), 0.55f);
                // 3-point angular polyline
                float len = 0.35f + (i % 3) * 0.15f;
                float x0 = (float) Math.cos(ang) * r, z0 = (float) Math.sin(ang) * r;
                float a1 = ang + len, a2 = ang + len * 1.9f;
                float x1 = (float) Math.cos(a1) * (r + 0.12f), z1 = (float) Math.sin(a1) * (r + 0.12f), y1 = y + 0.18f * (float) Math.sin(seed);
                float x2 = (float) Math.cos(a2) * r, z2 = (float) Math.sin(a2) * r, y2 = y - 0.1f;
                b.ribbon(x0, y, z0, x1, y1, z1, 0, 0.012f, 0, col, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
                b.ribbon(x1, y1, z1, x2, y2, z2, 0, 0.012f, 0, col, MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
            }
        }
    }

    // ======================================================================= Hand effects (screen-space)
    public static final class HandEffects extends Module {
        public final ModeSetting mode = add(new ModeSetting("Mode", "Flames", "Flames", "Gradient glow", "Both"));
        public final ColorSetting color = add(new ColorSetting("Glow color", 0xFFE5383B, 0xFFFFC857, true));
        public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05));
        public final NumberSetting opacity = add(new NumberSetting("Opacity", 0.8, 0.1, 1.0, 0.05));
        public final NumberSetting amount = add(new NumberSetting("Flame amount", 1.0, 0.3, 2.0, 0.1));
        public final BoolSetting follow = add(new BoolSetting("Follow mouse", true)).desc("Flames stream against the direction you turn the camera");
        public final AnimatedFloat windX = new AnimatedFloat(0f, 7f), windY = new AnimatedFloat(0f, 7f);

        private static final int N = 110;
        private static final class Pool {
            final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
            final float[] life = new float[N], max = new float[N], size = new float[N], seed = new float[N];
            final byte[] tex = new byte[N];
            final boolean[] alive = new boolean[N];
            float accum;
        }
        private final Pool[] pools = { new Pool(), new Pool() };
        private float lastYaw, lastPitch;
        private boolean init;
        private final java.util.Random rng = new java.util.Random();

        public HandEffects() { super("Hand Effects", "Animated flames under your first-person hands that stream with your mouse movement", Category.VISUALS); addQuality(); }

        private float rs() { return rng.nextFloat() * 2f - 1f; }

        /** Drawn in screen space under the held-item area (the hand model itself is drawn by the game before the HUD). */
        public void renderOverlay(GuiGraphics ctx, int w, int h) {
            var p = mc().player;
            if (p == null || !mc().options.getCameraType().isFirstPerson()) { init = false; return; }
            float dt = Math.min(0.05f, Math.max(0.001f, Animator.dt()));
            float t = Animator.time();
            float a = opacity.f();
            float s = h * 0.30f * size.f();          // base flame height in GUI pixels

            // camera movement -> smoothed "wind" so flames stream opposite to where you look
            float yaw = p.getYRot(), pitch = p.getXRot();
            if (!init) { lastYaw = yaw; lastPitch = pitch; init = true; }
            float dyw = net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw), dpt = pitch - lastPitch;
            lastYaw = yaw; lastPitch = pitch;
            float f = follow.on() ? 1f : 0f;
            windX.set(Math.max(-45f, Math.min(45f, -dyw * 1.15f)) * f);
            windY.set(Math.max(-35f, Math.min(35f, -dpt * 1.15f)) * f);
            float wx = windX.get(), wy = windY.get();

            for (int hand = 0; hand < 2; hand++) {
                if (hand == 1 && p.getOffhandItem().isEmpty()) { pools[1].accum = 0; continue; }
                // anchor sits BELOW the hand so the flames rise from underneath it; it leans a little with the wind
                float ax = (hand == 0 ? w * 0.80f : w * 0.20f) + wx * s * 0.020f;
                float ay = h + s * 0.02f + wy * s * 0.010f;

                if (!mode.is("Flames")) {
                    float pulse = 0.85f + 0.15f * (float) Math.sin(t * 3.2f + hand);
                    RenderUtil.shadow(ctx, (int) (ax - s * 0.55f), (int) (ay - s * 0.50f), (int) (s * 1.1f), (int) (s * 0.5f), 16, 26, Colors.mulAlpha(color.at(0), a * 0.45f * pulse), 0, 0);
                    RenderUtil.roundRect(ctx, (int) (ax - s * 0.30f), (int) (ay - s * 0.16f), (int) (s * 0.6f), (int) (s * 0.16f), 10, Colors.mulAlpha(color.at(0), a * 0.40f), Colors.mulAlpha(color.at(1), a * 0.10f));
                }
                if (!mode.is("Gradient glow")) flames(ctx, pools[hand], dt, t, ax, ay, s, wx, wy, a);
            }
        }

        private void flames(GuiGraphics ctx, Pool pl, float dt, float t, float ax, float ay, float s, float wx, float wy, float a) {
            // emit
            float rate = 62f * amount.f() * (qualityLevel() / 3f * 0.6f + 0.4f);
            pl.accum += rate * dt;
            int guard = 6;
            while (pl.accum >= 1f && guard-- > 0) {
                pl.accum -= 1f;
                for (int i = 0; i < N; i++) if (!pl.alive[i]) {
                    pl.alive[i] = true;
                    pl.x[i] = ax + rs() * s * 0.20f; pl.y[i] = ay + rs() * s * 0.02f;
                    pl.vx[i] = rs() * s * 0.10f; pl.vy[i] = -s * (0.85f + rng.nextFloat() * 0.9f);
                    pl.life[i] = 0f; pl.max[i] = 0.55f + rng.nextFloat() * 0.6f;
                    pl.size[i] = s * (0.30f + rng.nextFloat() * 0.26f);
                    pl.seed[i] = rng.nextFloat() * 6.28f;
                    pl.tex[i] = (byte) rng.nextInt(4);
                    break;
                }
            }
            pl.accum = Math.min(pl.accum, 2f);

            int hot = Colors.lerp(0xFFFFFFFF, color.at(1), 0.45f), mid = color.at(1), cool = color.at(0), dark = 0xFF2A0A0C;
            float drag = (float) Math.exp(-3.0f * dt);
            for (int i = 0; i < N; i++) {
                if (!pl.alive[i]) continue;
                pl.life[i] += dt;
                float k = pl.life[i] / pl.max[i];
                if (k >= 1f) { pl.alive[i] = false; continue; }
                // sway + wind stream (older flame = more affected, so the tongue bends)
                pl.vx[i] += (float) Math.sin(t * 5.2f + pl.seed[i] * 3f + pl.life[i] * 7f) * s * 0.9f * dt;
                pl.vx[i] += wx * s * 0.55f * dt * (0.25f + k);
                pl.vy[i] += wy * s * 0.55f * dt * (0.25f + k);
                pl.vx[i] *= drag; pl.vy[i] = pl.vy[i] * drag - s * 0.25f * dt;
                pl.x[i] += pl.vx[i] * dt; pl.y[i] += pl.vy[i] * dt;

                int c = k < 0.22f ? Colors.lerp(hot, mid, k / 0.22f) : k < 0.6f ? Colors.lerp(mid, cool, (k - 0.22f) / 0.38f) : Colors.lerp(cool, dark, (k - 0.6f) / 0.4f);
                float al = Math.min(1f, k * 9f) * (float) Math.pow(1f - k, 1.15) * a * 0.95f;
                if (al <= 0.01f) continue;
                float sz = pl.size[i] * (1f - 0.5f * k) * (0.92f + 0.08f * (float) Math.sin(t * 14f + pl.seed[i]));
                float ang = (float) Math.atan2(pl.vx[i], -pl.vy[i] + 1e-3f) * 0.55f;
                ctx.pose().pushMatrix();
                ctx.pose().translate(pl.x[i], pl.y[i]);
                ctx.pose().rotate(ang);
                int tint = Colors.mulAlpha(c, al);
                // soft glow puff under every third tongue
                if ((i % 3) == 0) {
                    int g = (int) (sz * 1.5f);
                    RenderUtil.image(ctx, Tex.GLOW, -g / 2, -g * 3 / 4, g, g, 1600, 1600, Colors.mulAlpha(color.at(0), al * 0.28f));
                }
                int ti = (int) sz;
                RenderUtil.image(ctx, Particles.FLAMES[pl.tex[i]], -ti * 7 / 20, -ti, ti * 7 / 10, ti, 240, 240, tint);
                ctx.pose().popMatrix();
            }
        }
    }

    // ======================================================================= Motion blur (screen-space approximation)
    public static final class MotionBlur extends Module {
        public final NumberSetting strength = add(new NumberSetting("Strength", 0.5, 0.0, 1.0, 0.05));
        private float lastYaw, lastPitch; private boolean init;
        private final AnimatedFloat vx = new AnimatedFloat(0f, 12f), vy = new AnimatedFloat(0f, 12f);
        public MotionBlur() { super("Motion Blur", "Soft directional streaks when your camera moves quickly", Category.VISUALS); }

        public void renderOverlay(GuiGraphics ctx, int w, int h) {
            var p = mc().player;
            if (p == null) return;
            if (!init) { lastYaw = p.getYRot(); lastPitch = p.getXRot(); init = true; }
            float dt = Math.max(0.001f, Animator.dt());
            float dy = net.minecraft.util.Mth.wrapDegrees(p.getYRot() - lastYaw) / dt;
            float dp = (p.getXRot() - lastPitch) / dt;
            lastYaw = p.getYRot(); lastPitch = p.getXRot();
            vx.set(Math.min(1f, Math.abs(dy) / 900f) * Math.signum(dy));
            vy.set(Math.min(1f, Math.abs(dp) / 700f) * Math.signum(dp));
            float sx = Math.abs(vx.get()) * strength.f(), sy = Math.abs(vy.get()) * strength.f();
            if (sx > 0.01f) {
                int a = (int) (110 * sx), band = (int) (w * 0.22f);
                for (int i = 0; i < 6; i++) {
                    int bw = band * (6 - i) / 6;
                    int al = a / 6;
                    RenderUtil.rect(ctx, 0, 0, bw, h, Colors.argb(al, 255, 255, 255));
                    RenderUtil.rect(ctx, w - bw, 0, bw, h, Colors.argb(al, 255, 255, 255));
                }
            }
            if (sy > 0.01f) {
                int a = (int) (110 * sy), band = (int) (h * 0.22f);
                for (int i = 0; i < 6; i++) {
                    int bh = band * (6 - i) / 6;
                    RenderUtil.rect(ctx, 0, 0, w, bh, Colors.argb(a / 6, 255, 255, 255));
                    RenderUtil.rect(ctx, 0, h - bh, w, bh, Colors.argb(a / 6, 255, 255, 255));
                }
            }
        }
    }

    // ======================================================================= Hit sounds (local playback only)
    public static final class HitSounds extends Module {
        public final ModeSetting sound = add(new ModeSetting("Sound", "Chime", "Chime", "Pling", "Bell", "Pop", "MouseButtonEvent"));
        public final NumberSetting volume = add(new NumberSetting("Volume", 0.6, 0.0, 1.0, 0.05));
        public final NumberSetting pitch = add(new NumberSetting("Pitch", 1.2, 0.5, 2.0, 0.05));
        public HitSounds() { super("Hit Sounds", "Plays a sound locally when you hit something", Category.MISC); }

        public void onHit(Entity e) {
            SoundEvent ev = switch (sound.get()) {
                case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
                case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
                case "Pop" -> SoundEvents.ITEM_PICKUP;
                case "MouseButtonEvent" -> SoundEvents.UI_BUTTON_CLICK.value();
                default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            };
            mc().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch.f(), volume.f()));
        }
    }

    // ======================================================================= GUI animation
    public static final class GuiAnimation extends Module {
        public final NumberSetting duration = add(new NumberSetting("Duration", 0.22, 0.08, 0.6, 0.02));
        public final NumberSetting startScale = add(new NumberSetting("Start scale", 0.94, 0.8, 1.0, 0.01));
        public final BoolSetting fade = add(new BoolSetting("Fade in", true));
        public GuiAnimation() { super("GUI Animation", "Animates vanilla menus, chat and inventory opening", Category.MISC); }
    }

    // ======================================================================= Not yet hooked (settings + GUI only)
    public static final class ItemPhysics extends Module {
        public final BoolSetting water = add(new BoolSetting("Float in water", true));
        public ItemPhysics() { super("Item Physics", "Dropped items rest on the ground (render hook pending)", Category.MISC); }
    }

    public static final class NoBlockRotation extends Module {
        public NoBlockRotation() { super("No Block Rotation", "Keep randomized block textures facing one way (render hook pending)", Category.MISC); }
    }

    public static final class PlayerModel extends Module {
        public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 1.5, 0.05));
        public PlayerModel() { super("Player Model", "Optional replacement model for yourself (render hook pending)", Category.COSMETICS); }
    }
}
