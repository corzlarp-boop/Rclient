package dev.rclient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the RClient logo crisply at any size. The 1689px source is area-averaged (box filter) down to
 * the exact device-pixel size once and cached, so it never aliases like a nearest-sampled giant texture.
 * Tint multiplies the silver logo: WHITE keeps the natural look, an accent colour recolours it.
 */
public final class RClientLogo {
    private RClientLogo() {}

    public static final Identifier SOURCE = Identifier.fromNamespaceAndPath("rclient", "textures/gui/logo.png");

    private static int[] src;       // premultiplied-free ARGB
    private static int srcW, srcH;
    private static final Map<Integer, TextureFactory.Tex> CACHE = new HashMap<>();
    private static double cachedScale = -1;

    private static void loadSource() {
        if (src != null) return;
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(SOURCE);
            if (r.isEmpty()) { src = new int[0]; return; }
            try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                srcW = img.getWidth(); srcH = img.getHeight();
                src = new int[srcW * srcH];
                for (int y = 0; y < srcH; y++) for (int x = 0; x < srcW; x++) src[y * srcW + x] = img.getPixel(x, y);
            }
        } catch (Exception e) { src = new int[0]; }
    }

    private static TextureFactory.Tex scaled(int px) {
        double sc = RenderUtil.scale();
        if (sc != cachedScale) { for (var t : CACHE.values()) TextureFactory.destroy(t); CACHE.clear(); cachedScale = sc; }
        TextureFactory.Tex t = CACHE.get(px);
        if (t != null) return t;
        loadSource();
        t = TextureFactory.create("logo", px, px);
        if (src.length > 0) {
            for (int y = 0; y < px; y++) {
                int sy0 = y * srcH / px, sy1 = Math.max(sy0 + 1, (y + 1) * srcH / px);
                for (int x = 0; x < px; x++) {
                    int sx0 = x * srcW / px, sx1 = Math.max(sx0 + 1, (x + 1) * srcW / px);
                    long a = 0, r = 0, g = 0, b = 0; int n = 0;
                    for (int sy = sy0; sy < sy1; sy++) for (int sx = sx0; sx < sx1; sx++) {
                        int c = src[sy * srcW + sx];
                        int ca = c >>> 24;
                        a += ca; r += ((c >> 16) & 255) * ca; g += ((c >> 8) & 255) * ca; b += (c & 255) * ca; n++;
                    }
                    int oa = (int) (a / n);
                    int orr = a == 0 ? 0 : (int) (r / a), og = a == 0 ? 0 : (int) (g / a), ob = a == 0 ? 0 : (int) (b / a);
                    t.image.setPixel(x, y, (oa << 24) | (orr << 16) | (og << 8) | ob);
                }
            }
        }
        t.upload();
        CACHE.put(px, t);
        return t;
    }

    /** Draw into a size×size GUI square. */
    public static void draw(GuiGraphics ctx, int x, int y, int size, int tint) {
        int px = Math.max(8, (int) Math.round(size * RenderUtil.scale()));
        TextureFactory.Tex t = scaled(px);
        ctx.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, 0f, 0f, size, size, px, px, px, px, tint);
    }

    public static void draw(GuiGraphics ctx, int x, int y, int size) { draw(ctx, x, y, size, 0xFFFFFFFF); }
}
