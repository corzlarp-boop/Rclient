package dev.rclient.render;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Single place that creates dynamic textures. If a Minecraft update changes the
 * DynamicTexture constructor, this is the only file to touch.
 */
public final class TextureFactory {
    private TextureFactory() {}

    public static final class Tex {
        public final Identifier id;
        public final DynamicTexture texture;
        public final NativeImage image;
        Tex(Identifier id, DynamicTexture texture, NativeImage image) {
            this.id = id; this.texture = texture; this.image = image;
        }
        public void upload() { texture.upload(); }
    }

    private static int counter = 0;

    public static Tex create(String name, int w, int h) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, true);
        Identifier id = Identifier.fromNamespaceAndPath("rclient", "dyn/" + name + "_" + (counter++));
        DynamicTexture t = new DynamicTexture(() -> id.toString(), img);
        Minecraft.getInstance().getTextureManager().register(id, t);
        return new Tex(id, t, img);
    }

    /** Same as create() but asks for bilinear filtering (smooth scaling). Best-effort via reflection: ignored if absent. */
    public static Tex createSmooth(String name, int w, int h) {
        Tex t = create(name, w, h);
        try {
            t.texture.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(t.texture, true, false);
        } catch (Throwable ignored) { }
        return t;
    }

    public static void destroy(Tex t) {
        Minecraft.getInstance().getTextureManager().release(t.id);
    }
}
