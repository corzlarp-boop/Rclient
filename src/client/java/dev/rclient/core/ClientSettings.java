package dev.rclient.core;

import com.google.gson.JsonObject;
import dev.rclient.anim.Animator;
import dev.rclient.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Global (non-module) settings shown in the Settings tab and stored in every config profile. */
public final class ClientSettings {
    private ClientSettings() {}

    public static final NumberSetting ANIM_SPEED = new NumberSetting("Animation speed", 1.0, 0.25, 2.0, 0.05);
    public static final ModeSetting THEME = new ModeSetting("Theme preset", "Crimson", Theme.presetNames());
    public static final BoolSetting BG_BLUR = new BoolSetting("Menu background blur", true);
    public static final BoolSetting SMOOTH_SCROLL = new BoolSetting("Smooth scrolling", true);
    public static final BoolSetting REDUCE_PARTICLES = new BoolSetting("Reduce particles", false);
    public static final ModeSetting GLOBAL_QUALITY = new ModeSetting("Global quality cap", "High", "Low", "Medium", "High");
    public static final BoolSetting FPS_FRIENDLY = new BoolSetting("Performance mode (disable glow/blur extras)", false);
    public static final BoolSetting SHOW_NOTIFS = new BoolSetting("Toggle notifications", true);
    public static final BoolSetting MENU_SHAPES = new BoolSetting("Animated menu background", true);

    public static final KeybindSetting OPEN_GUI = new KeybindSetting("Open menu key", org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);

    public static final List<Setting<?>> ALL = new ArrayList<>(List.of(
        OPEN_GUI, THEME, ANIM_SPEED, SMOOTH_SCROLL, BG_BLUR, MENU_SHAPES, SHOW_NOTIFS,
        GLOBAL_QUALITY, REDUCE_PARTICLES, FPS_FRIENDLY));

    /** Global particle multiplier from the quality cap + reduce-particles switch. */
    public static float particleScale() {
        float q = switch (GLOBAL_QUALITY.get()) { case "Low" -> 0.35f; case "Medium" -> 0.65f; default -> 1f; };
        return REDUCE_PARTICLES.on() ? q * 0.5f : q;
    }

    /** Clamp a module quality level (1..3) to the global cap. */
    public static int capQuality(int level) {
        int cap = switch (GLOBAL_QUALITY.get()) { case "Low" -> 1; case "Medium" -> 2; default -> 3; };
        return Math.min(level, cap);
    }

    /** Re-apply side effects (theme + animation speed) after a change or a config load. */
    public static void applyAll() {
        Animator.setSpeed(ANIM_SPEED.f());
        Theme.apply(THEME.get());
    }

    public static JsonObject save() {
        JsonObject o = new JsonObject();
        for (Setting<?> s : ALL) o.add(s.name, s.save());
        return o;
    }

    public static void load(JsonObject o) {
        for (Setting<?> s : ALL) if (o.has(s.name)) s.load(o.get(s.name));
        applyAll();
    }
}
