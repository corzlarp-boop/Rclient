package dev.rclient.core;

import dev.rclient.anim.AnimatedColor;
import dev.rclient.render.Colors;

/** Accent colour + presets. The accent glides when changed, so the whole UI re-tints smoothly. */
public final class Theme {
    private Theme() {}

    public record Preset(String name, int accent, int accent2) {}

    public static final Preset[] PRESETS = {
        new Preset("Crimson", 0xFFE5383B, 0xFF9B1D20),
        new Preset("Ocean",   0xFF3A86FF, 0xFF1B4FBF),
        new Preset("Violet",  0xFF8B5CF6, 0xFF5B34C9),
        new Preset("Emerald", 0xFF2EC27E, 0xFF178A57),
        new Preset("Amber",   0xFFF5A524, 0xFFC27C0E),
        new Preset("Mono",    0xFFE4E4E7, 0xFF9A9AA2),
    };

    private static final AnimatedColor ACCENT = new AnimatedColor(PRESETS[0].accent, 9f);
    private static final AnimatedColor ACCENT2 = new AnimatedColor(PRESETS[0].accent2, 9f);
    private static String current = PRESETS[0].name;

    public static void apply(String name) {
        for (Preset p : PRESETS) if (p.name.equals(name)) { setAccent(p.accent, p.accent2); current = name; return; }
    }

    public static void setAccent(int a, int b) { ACCENT.set(a); ACCENT2.set(b); }
    public static void snap(int a, int b) { ACCENT.snapTo(a); ACCENT2.snapTo(b); }
    public static String current() { return current; }

    public static int accent() { return ACCENT.get(); }
    public static int accent2() { return ACCENT2.get(); }
    public static int accent(int alpha) { return Colors.withAlpha(ACCENT.get(), alpha); }
    /** Accent tint for enabled card backgrounds etc. */
    public static int tint(float alpha) { return Colors.mulAlpha(ACCENT.get(), alpha); }

    public static String[] presetNames() {
        String[] n = new String[PRESETS.length];
        for (int i = 0; i < n.length; i++) n[i] = PRESETS[i].name;
        return n;
    }
}
