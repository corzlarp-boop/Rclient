package dev.rclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.rclient.anim.AnimatedFloat;
import dev.rclient.setting.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every RClient feature. Modules are CLIENT-SIDE VISUALS ONLY: they must never touch
 * packets, movement, combat or anything that gives a gameplay advantage. Hooks default to no-ops.
 */
public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;

    private boolean enabled;
    public boolean favorite;

    private final List<Setting<?>> settings = new ArrayList<>();
    public final KeybindSetting keybind = new KeybindSetting("Keybind", -1);

    // GUI animation state kept on the module so the cards survive screen rebuilds.
    public final AnimatedFloat toggleAnim = AnimatedFloat.spring(0f, 320f, 24f);
    public final AnimatedFloat expandAnim = new AnimatedFloat(0f, 14f);
    public boolean expanded;

    /** Quality / on-off knob every effect exposes (0 = off, 1 = low, 2 = medium, 3 = high). */
    public final ModeSetting quality = new ModeSetting("Quality", "High", "Low", "Medium", "High");

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        settings.add(keybind);
    }

    // ---- state ----
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        toggleAnim.set(on ? 1f : 0f);
        if (on) onEnable(); else onDisable();
        dev.rclient.hud.NotificationManager.toggled(this);
    }

    public void toggle() { setEnabled(!enabled); }

    /** Silent enable used when loading configs. */
    public void setEnabledSilently(boolean on) {
        if (on == enabled) { toggleAnim.set(on ? 1f : 0f); return; }
        enabled = on;
        toggleAnim.set(on ? 1f : 0f);
        if (on) onEnable(); else onDisable();
    }

    // ---- hooks (all client-side visual only) ----
    protected void onEnable() {}
    protected void onDisable() {}
    public void onClientTick() {}

    // ---- settings ----
    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }
    protected void addQuality() { settings.add(quality); }
    public List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

    /** 0 = off, 1 = low, 2 = medium, 3 = high. Effects scale particle counts / segment counts with it. */
    public int qualityLevel() {
        return switch (quality.get()) { case "Low" -> 1; case "Medium" -> 2; default -> 3; };
    }

    // ---- serialisation ----
    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("favorite", favorite);
        JsonObject s = new JsonObject();
        for (Setting<?> st : settings) s.add(st.name, st.save());
        o.add("settings", s);
        return o;
    }

    public void load(JsonObject o) {
        if (o.has("favorite")) favorite = o.get("favorite").getAsBoolean();
        if (o.has("settings")) {
            JsonObject s = o.getAsJsonObject("settings");
            for (Setting<?> st : settings) {
                JsonElement e = s.get(st.name);
                if (e != null) st.load(e);
            }
        }
        if (o.has("enabled")) setEnabledSilently(o.get("enabled").getAsBoolean());
    }

    public void resetSettings() { for (Setting<?> s : settings) s.reset(); }
}
