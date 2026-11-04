package dev.rclient.render;

import net.minecraft.resources.Identifier;

/** Bundled textures (assets/rclient/textures/…). Particle PNGs are white/tintable unless noted. */
public final class Tex {
    private Tex() {}

    private static Identifier t(String p) { return Identifier.fromNamespaceAndPath("rclient", "textures/" + p + ".png"); }

    public static final Identifier LOGO        = t("gui/logo");
    public static final Identifier SMOKE       = t("particle/smoke_cloud");   // dark, normal blend
    public static final Identifier BURST       = t("particle/burst_rays");    // white, additive
    public static final Identifier FIRE        = t("particle/fire_embers");   // coloured, additive
    public static final Identifier GLOW_BLUE   = t("particle/glow_blue");     // coloured, additive
    public static final Identifier GLOW        = t("particle/glow_soft");     // white, additive

    /** Kenney effect cell: row 0-4, col 0-15. */
    public static Identifier kenney(int row, int col) { return t(String.format("particle/kenney/k_%d_%02d", row, col)); }

    // handy Kenney picks (see the sheet): sparkles, rings, flames, slashes
    public static final Identifier SPARK      = kenney(1, 4);
    public static final Identifier STAR4      = kenney(1, 5);
    public static final Identifier STAR_SOFT  = kenney(1, 3);
    public static final Identifier RING       = kenney(2, 9);
    public static final Identifier RING_THICK = kenney(2, 10);
    public static final Identifier FLAME      = kenney(4, 2);
    public static final Identifier SLASH      = kenney(4, 9);
    public static final Identifier SHOCK      = kenney(3, 10);
    public static final Identifier DOT        = kenney(2, 12);
}
