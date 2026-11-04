package dev.rclient.visual;

import dev.rclient.fx.Particles;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.render.Tex;
import dev.rclient.setting.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Replaces the visible sky with an animated dome (see render/SkyDome). The dome is built from vertex colours
 * (no textures, so Minecraft's distance fog cannot wash it out). "Galaxy Stars" is generated live: gradient,
 * nebula clouds, a galactic band, glowing stars and faint constellation lines. The other styles are the bundled
 * images / baked procedural skies sampled into the same dome. Visual only.
 */
public final class SkyModule extends Module {
    public static final String[] STYLES = {
        "Galaxy Stars", "Nebula Teal", "Nebula Green", "Galaxy", "Clouds", "Plasma", "Voronoi", "Aurora", "Sunset"
    };
    public static final int BW = 160, BH = 80;

    public final ModeSetting style = add(new ModeSetting("Style", "Galaxy Stars", STYLES));
    public final ColorSetting tint = add(new ColorSetting("Tint", 0xFFFFFFFF, 0xFF7AB8FF, false));
    public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.0, 4.0, 0.05));
    public final NumberSetting brightness = add(new NumberSetting("Brightness", 1.0, 0.3, 1.6, 0.05));
    public final BoolSetting cycleTint = add(new BoolSetting("Drift tint", false));
    public final BoolSetting stars = add(new BoolSetting("Stars", true));
    public final NumberSetting starDensity = add(new NumberSetting("Star density", 1.0, 0.3, 1.6, 0.05));
    public final BoolSetting twinkle = add(new BoolSetting("Twinkle", true));
    public final BoolSetting constellations = add(new BoolSetting("Constellations", true));
    public final ModeSetting streaks = add(new ModeSetting("Streaks", "Off", "Off", "Rain", "Embers"));
    public final NumberSetting streakAmount = add(new NumberSetting("Streak amount", 1.0, 0.2, 3.0, 0.1)).desc("Scaled by quality");

    private int[] baked;
    private String bakedFor = "";

    public SkyModule() {
        super("Skies", "Galaxy sky with stars and constellations, plus nebula, plasma, aurora and sunset skies", Category.VISUALS);
        addQuality();
    }

    public boolean isGalaxy() { return style.is("Galaxy Stars"); }

    /** BW x BH ARGB pixels of the current image/procedural style (null for the live galaxy). */
    public int[] pixels() {
        String s = style.get();
        if (s.equals("Galaxy Stars")) return null;
        if (baked == null || !bakedFor.equals(s)) {
            String img = switch (s) {
                case "Nebula Teal" -> "sky_nebula_teal";
                case "Nebula Green" -> "sky_nebula_green";
                case "Galaxy" -> "sky_galaxy";
                case "Clouds" -> "sky_clouds";
                default -> null;
            };
            int[] px = img != null ? loadImage(img) : null;
            baked = px != null ? px : SkyGen.bake(s, BW, BH);
            bakedFor = s;
        }
        return baked;
    }

    private static int[] loadImage(String name) {
        try (InputStream in = SkyModule.class.getResourceAsStream("/assets/rclient/textures/sky/" + name + ".png")) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int[] out = new int[BW * BH];
            for (int y = 0; y < BH; y++) {
                int y0 = y * h / BH, y1 = Math.max(y0 + 1, (y + 1) * h / BH);
                for (int x = 0; x < BW; x++) {
                    int x0 = x * w / BW, x1 = Math.max(x0 + 1, (x + 1) * w / BW);
                    long r = 0, g = 0, b = 0; int n = 0;
                    for (int yy = y0; yy < y1; yy += 2) for (int xx = x0; xx < x1; xx += 2) {
                        int p = img.getRGB(xx, yy);
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
                    }
                    n = Math.max(1, n);
                    out[y * BW + x] = 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Rain / ember streaks falling through view (client tick). */
    @Override public void onClientTick() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null || streaks.is("Off")) return;
        var rnd = java.util.concurrent.ThreadLocalRandom.current();
        int n = Particles.count(Math.round(6 * streakAmount.f()), qualityLevel());
        boolean rain = streaks.is("Rain");
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * 14;
            var q = Particles.spawn();
            q.x = p.getX() + Math.cos(a) * r; q.z = p.getZ() + Math.sin(a) * r;
            q.y = p.getY() + (rain ? 6 + rnd.nextDouble() * 8 : -1 + rnd.nextDouble() * 3);
            q.vx = (float) (rnd.nextDouble() - 0.5) * (rain ? 0.4f : 0.6f);
            q.vz = (float) (rnd.nextDouble() - 0.5) * (rain ? 0.4f : 0.6f);
            q.vy = rain ? -16f : 1.2f + (float) rnd.nextDouble() * 1.6f;
            q.drag = 1f; q.life = 0; q.maxLife = rain ? 0.7f : 3.2f;
            q.size = rain ? 0.05f : 0.09f; q.sizeEnd = rain ? 0.05f : 0.02f;
            q.color = rain ? Colors.mulAlpha(tint.primary() == 0xFFFFFFFF ? 0xFFBFE3FF : tint.primary(), 0.8f)
                           : Colors.argb(230, 255, 150 + rnd.nextInt(60), 50);
            q.color2 = q.color;
            q.tex = Tex.STAR_SOFT; q.additive = true;
        }
    }

    @Override protected void onDisable() { baked = null; bakedFor = ""; }
}
