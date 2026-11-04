package dev.rclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.rclient.render.Colors;

/**
 * Colour with optional two-stop gradient. {@link #get()} is the primary colour; {@link #second} the end
 * colour used when {@link #gradient} is true. {@link #at(float)} samples along the gradient (and cycles
 * through the hue wheel when rainbow is on).
 */
public final class ColorSetting extends Setting<Integer> {
    public int second;
    public boolean gradient;
    public boolean rainbow;
    private final int defSecond;
    private final boolean defGradient;

    public ColorSetting(String name, int c1) { this(name, c1, c1, false); }

    public ColorSetting(String name, int c1, int c2, boolean gradient) {
        super(name, c1);
        this.second = c2; this.defSecond = c2;
        this.gradient = gradient; this.defGradient = gradient;
    }

    /** t in 0..1 along the gradient. */
    public int at(float t) {
        if (rainbow) return Colors.hsb((t + (System.nanoTime() / 4_000_000_000f)) % 1f, 0.65f, 1f, 255);
        return gradient ? Colors.lerp(value, second, t) : value;
    }

    public int primary() { return rainbow ? at(0) : value; }

    @Override public void reset() { super.reset(); second = defSecond; gradient = defGradient; rainbow = false; }
    @Override public boolean isDefault() { return value.equals(def) && second == defSecond && gradient == defGradient && !rainbow; }

    @Override public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("c1", value); o.addProperty("c2", second);
        o.addProperty("gradient", gradient); o.addProperty("rainbow", rainbow);
        return o;
    }

    @Override public void load(JsonElement e) {
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        if (o.has("c1")) value = o.get("c1").getAsInt();
        if (o.has("c2")) second = o.get("c2").getAsInt();
        if (o.has("gradient")) gradient = o.get("gradient").getAsBoolean();
        if (o.has("rainbow")) rainbow = o.get("rainbow").getAsBoolean();
    }
}
