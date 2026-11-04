package dev.rclient.cosmetic;

import dev.rclient.mesh.CosmeticContext;
import dev.rclient.mesh.MeshBuilder;
import dev.rclient.mesh.MeshProvider;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.setting.*;

/** Base for modules that contribute geometry to the player model. Visual only. */
public abstract class CosmeticModule extends Module implements MeshProvider {
    protected CosmeticModule(String name, String desc) { super(name, desc, Category.COSMETICS); }

    // ------------------------------------------------------------------ concrete cosmetics

    public static final class Wings extends CosmeticModule {
        public final ModeSetting style = add(new ModeSetting("Style", "Angel", "Angel", "Bat", "Crystal"));
        public final ColorSetting color = add(new ColorSetting("Color", 0xFF2B2B33, 0xFF6A1F2E, true)).visibleWhen(() -> !style.is("Angel"));
        public final ColorSetting glow = add(new ColorSetting("Glow", 0xFFE5383B)).visibleWhen(() -> !style.is("Angel"));
        public final NumberSetting flapSpeed = add(new NumberSetting("Flap speed", 1.2, 0.2, 4.0, 0.1));
        public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 1.6, 0.05));
        public Wings() { super("Wings", "Bat, crystal or angel wings that flap while you move"); }

        @Override public void build(MeshBuilder b, CosmeticContext c) {
            float idle = (float) Math.sin(c.time * flapSpeed.f() * 1.6f) * 0.12f;
            float move = Math.min(1f, c.speed * 6f);
            float air = c.airborne ? 0.55f * (float) Math.sin(c.time * flapSpeed.f() * 9f) : 0f;
            float flap = idle + move * 0.18f * (float) Math.sin(c.time * flapSpeed.f() * 7f) + air;
            b.push();
            b.translate(0, -c.sneakDrop(), 0);
            b.scale(size.f(), size.f(), size.f());
            int c1 = color.at(0), c2 = color.at(1), g = Colors.mulAlpha(glow.primary(), 0.8f);
            for (int side = -1; side <= 1; side += 2) {
                switch (style.get()) {
                    case "Crystal" -> Shapes.crystalWing(b, side, flap, Colors.mulAlpha(c1, 0.9f), Colors.mulAlpha(c2, 0.8f), g);
                    case "Angel" -> Shapes.angelWing(b, side, flap, c.time);
                    default -> Shapes.batWing(b, side, flap, Colors.scale(c1, 1.2f), Colors.mulAlpha(c2, 0.92f), g);
                }
            }
            b.pop();
        }
    }

    public static final class Horns extends CosmeticModule {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFF2A2A30, 0xFFE5383B, true));
        public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 1.8, 0.05));
        public Horns() { super("Horns", "Curved horns on your head"); }
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            b.push();
            b.translate(0, -c.sneakDrop() * 0.6f, 0);
            Shapes.horns(b, color.at(0), color.at(1), size.f());
            b.pop();
        }
    }

    public static final class FlameHat extends CosmeticModule {
        public final ColorSetting inner = add(new ColorSetting("Core", 0xFFFFD27A));
        public final ColorSetting outer = add(new ColorSetting("Outer", 0xFFFF5A1F));
        public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.5, 2.0, 0.05));
        public FlameHat() { super("Flame Hat", "An animated flame burning on top of your head"); }
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            b.push();
            b.translate(0, -c.sneakDrop() * 0.6f, 0);
            Shapes.flameHat(b, c.time, Colors.mulAlpha(inner.primary(), 0.9f), Colors.mulAlpha(outer.primary(), 0.7f), size.f());
            b.pop();
        }
    }

    public static final class Halo extends CosmeticModule {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFE08A));
        public final NumberSetting radius = add(new NumberSetting("Radius", 0.22, 0.12, 0.4, 0.01));
        public Halo() { super("Halo", "A glowing ring floating above your head"); }
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            b.push();
            b.translate(0, -c.sneakDrop() * 0.6f, 0);
            Shapes.halo(b, c.time, Colors.mulAlpha(color.primary(), 0.85f), radius.f());
            b.pop();
        }
    }

    public static final class Orbit extends CosmeticModule {
        public final ModeSetting kind = add(new ModeSetting("Item", "Coins", "Coins", "Gems", "Orbs"));
        public final NumberSetting count = add(new NumberSetting("Count", 5, 1, 10, 1));
        public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.2, 3.0, 0.1));
        public final NumberSetting radius = add(new NumberSetting("Radius", 0.75, 0.4, 1.4, 0.05));
        public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFC857, 0xFFFF8A3D, true));
        public Orbit() { super("Orbiting Items", "Coins, gems or orbs circling around you"); }
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            int n = count.i();
            float r = radius.f();
            for (int i = 0; i < n; i++) {
                float a = c.time * speed.f() + i * (float) (Math.PI * 2 / n);
                float y = 1.0f + (float) Math.sin(c.time * 1.3f + i) * 0.18f - c.sneakDrop();
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                float t = n == 1 ? 0f : i / (float) (n - 1);
                int face = color.at(t);
                switch (kind.get()) {
                    case "Gems" -> {
                        b.push(); b.translate(x, y, z); b.rotateY(c.time * 2f + i);
                        float s = 0.07f;
                        b.quad(0, s * 1.4f, 0, s, 0, 0, 0, 0, s, 0, 0, s, face, 0);
                        b.quad(0, s * 1.4f, 0, 0, 0, s, -s, 0, 0, -s, 0, 0, face, 0);
                        b.quad(0, s * 1.4f, 0, -s, 0, 0, 0, 0, -s, 0, 0, -s, face, 0);
                        b.quad(0, s * 1.4f, 0, 0, 0, -s, s, 0, 0, s, 0, 0, face, 0);
                        b.quad(0, -s * 1.4f, 0, 0, 0, s, s, 0, 0, s, 0, 0, Colors.scale(face, 0.7f), 0);
                        b.quad(0, -s * 1.4f, 0, -s, 0, 0, 0, 0, s, 0, 0, s, Colors.scale(face, 0.7f), 0);
                        b.quad(0, -s * 1.4f, 0, 0, 0, -s, -s, 0, 0, -s, 0, 0, Colors.scale(face, 0.7f), 0);
                        b.quad(0, -s * 1.4f, 0, s, 0, 0, 0, 0, -s, 0, 0, -s, Colors.scale(face, 0.7f), 0);
                        b.pop();
                    }
                    case "Orbs" -> {
                        b.box(x, y, z, 0.05f, 0.05f, 0.05f, face, MeshBuilder.EMISSIVE);
                        b.box(x, y, z, 0.1f, 0.1f, 0.1f, Colors.mulAlpha(face, 0.35f), MeshBuilder.EMISSIVE | MeshBuilder.ADDITIVE);
                    }
                    default -> Shapes.coin(b, x, y, z, c.time * 3f + i, face, Colors.scale(face, 0.65f), 0.09f);
                }
            }
        }
    }

    public static final class Pet extends CosmeticModule {
        public final ColorSetting color = add(new ColorSetting("Body", 0xFF22222A));
        public final ColorSetting glow = add(new ColorSetting("Glow", 0xFFE5383B));
        public final NumberSetting follow = add(new NumberSetting("Follow smoothing", 6.0, 2.0, 14.0, 0.5));
        // follower state in model space
        private float fx = 0.7f, fy = 1.8f, fz = 0.2f, tilt;
        private float lastTime;
        public Pet() { super("Pet", "A floating companion that follows you and reacts to your movement"); }

        @Override public void build(MeshBuilder b, CosmeticContext c) {
            float dt = Math.max(0.001f, Math.min(0.05f, c.time - lastTime));
            if (c.time < lastTime) dt = 0.016f;
            lastTime = c.time;
            float tx = 0.75f + (float) Math.sin(c.time * 0.7f) * 0.08f;
            float ty = 1.85f + (float) Math.sin(c.time * 1.9f) * 0.07f + (c.airborne ? 0.12f : 0f);
            float tz = -0.1f - Math.min(0.6f, c.speed * 4f);   // lags behind when you run
            float k = 1f - (float) Math.exp(-follow.f() * dt);
            fx += (tx - fx) * k; fy += (ty - fy) * k; fz += (tz - fz) * k;
            float targetTilt = Math.min(0.5f, c.speed * 3f) + (c.airborne ? -0.15f : 0f);
            tilt += (targetTilt - tilt) * k;
            Shapes.pet(b, fx, fy, fz, c.time, tilt, color.get(), Colors.mulAlpha(glow.primary(), 0.35f));
        }
    }

    public static final class Cape extends CosmeticModule {
        public final ColorSetting color = add(new ColorSetting("Color", 0xFF16161B, 0xFF7A1C28, true));
        private float swing;
        public Cape() { super("Cape", "A flowing cape that swings with your movement"); }
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            float target = Math.min(1f, c.speed * 5f) + (c.airborne ? 0.25f : 0f);
            swing += (target - swing) * 0.15f;
            Shapes.cape(b, swing, c.time, color.at(0), color.at(1), c.sneakDrop());
        }
    }

    public static final class Knife extends CosmeticModule {
        public final ModeSetting style = add(new ModeSetting("Style", "Tanto", "Tanto", "Karambit", "Dagger"));
        public final ColorSetting blade = add(new ColorSetting("Blade", 0xFFD7DAE0));
        public final ColorSetting accent = add(new ColorSetting("Accent", 0xFFE5383B));
        public final NumberSetting size = add(new NumberSetting("Size", 1.0, 0.6, 2.0, 0.05));
        public Knife() { super("Knife Models", "Draws a 3D knife in place of held swords (visual only)"); }
        /** Preview only (menu): a knife floating beside the player. In-game it is drawn in the hand via WorldDraw. */
        @Override public void build(MeshBuilder b, CosmeticContext c) {
            if (!c.inMenu) return;
            b.push();
            b.translate(-0.55f, 1.0f + (float) Math.sin(c.time * 1.4f) * 0.03f, 0.1f);
            b.rotateZ(-0.35f); b.rotateY(c.time * 0.8f);
            b.scale(size.f() * 1.4f, size.f() * 1.4f, size.f() * 1.4f);
            buildKnife(b);
            b.pop();
        }
        public void buildKnife(MeshBuilder b) {
            Shapes.knife(b, blade.primary(), Colors.scale(blade.primary(), 0.8f), 0xFF1D1D22, accent.primary());
        }
    }
}
