package dev.rclient.visual;

import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.setting.*;
import org.lwjgl.glfw.GLFW;

/**
 * Emotes: hold the key to open the radial wheel, release to play. Emotes are client-side pose
 * animations on your own player model (no packets). The wheel UI is {@link dev.rclient.gui.EmoteWheelScreen}.
 */
public final class EmoteModule extends Module {
    public record Emote(String name, String icon) {}

    /** Slots shown on the wheel. Names are swappable in the settings via the slot dropdowns. */
    public static final Emote[] LIBRARY = {
        new Emote("Wave", "wave"), new Emote("Floss", "floss"), new Emote("Bounce", "bounce"), new Emote("Spin", "spin"),
        new Emote("Dab", "dab"), new Emote("Clap", "clap"), new Emote("Flex", "flex"), new Emote("Bow", "bow"),
    };

    public final NumberSetting slots = add(new NumberSetting("Wheel slots", 6, 4, 8, 1));
    public final ModeSetting[] slot = new ModeSetting[8];

    // active playback
    public static String playing = "";
    public static float playTime;

    public EmoteModule() {
        super("Emotes", "Hold the key for a radial wheel of client-side emotes", Category.VISUALS);
        keybind.set(GLFW.GLFW_KEY_B);
        String[] names = new String[LIBRARY.length];
        for (int i = 0; i < names.length; i++) names[i] = LIBRARY[i].name();
        for (int i = 0; i < 8; i++) slot[i] = add(new ModeSetting("Slot " + (i + 1), names[i], names));
    }

    public Emote slotEmote(int i) {
        String n = slot[i].get();
        for (Emote e : LIBRARY) if (e.name().equals(n)) return e;
        return LIBRARY[0];
    }

    public static void play(String name) { playing = name; playTime = 0f; }
    public static void stop() { playing = ""; }
    public static boolean active() { return !playing.isEmpty(); }

    /** Pose offsets in radians, evaluated per frame. out = {head x, head y, rArm x, rArm z, lArm x, lArm z, rLeg x, lLeg x, bodyY, yOffset} */
    public static void pose(float t, float[] out) {
        java.util.Arrays.fill(out, 0f);
        switch (playing) {
            case "Wave" -> { out[2] = -2.6f; out[3] = 0.3f + 0.45f * (float) Math.sin(t * 9f); out[1] = 0.2f * (float) Math.sin(t * 3f); }
            case "Floss" -> {
                float s = (float) Math.sin(t * 10f);
                out[8] = s * 0.5f; out[3] = 0.5f + s * 0.5f; out[5] = -0.5f + s * 0.5f; out[2] = 0.3f; out[4] = 0.3f;
                out[6] = s * 0.3f; out[7] = -s * 0.3f;
            }
            case "Bounce" -> { float s = Math.abs((float) Math.sin(t * 7f)); out[9] = s * 0.18f; out[2] = -0.5f * s; out[4] = -0.5f * s; out[6] = s * 0.4f; out[7] = s * 0.4f; }
            case "Spin" -> { out[8] = t * 7f; out[2] = -1.2f; out[4] = -1.2f; out[3] = 0.9f; out[5] = -0.9f; }
            case "Dab" -> { out[0] = 0.5f; out[1] = -0.6f; out[2] = -2.9f; out[3] = 0.1f; out[4] = -1.6f; out[5] = -0.9f; }
            case "Clap" -> { float s = (float) Math.sin(t * 12f) * 0.25f; out[2] = -1.4f; out[4] = -1.4f; out[3] = 0.55f - s; out[5] = -0.55f + s; }
            case "Flex" -> { out[2] = -1.3f; out[4] = -1.3f; out[3] = 1.3f + 0.1f * (float) Math.sin(t * 6f); out[5] = -1.3f - 0.1f * (float) Math.sin(t * 6f); }
            case "Bow" -> { float k = Math.min(1f, t * 3f) * (t < 1.5f ? 1f : Math.max(0f, 1f - (t - 1.5f) * 3f)); out[0] = 0.9f * k; out[8] = 0f; out[2] = 0.3f * k; out[4] = 0.3f * k; }
            default -> { }
        }
    }
}
