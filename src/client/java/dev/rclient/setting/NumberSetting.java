package dev.rclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
    public final double min, max, step;

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min; this.max = max; this.step = step;
    }

    public float f() { return value.floatValue(); }
    public int i() { return (int) Math.round(value); }

    @Override public void set(Double v) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round(c / step) * step;
        value = c;
    }

    /** 0..1 position within the range. */
    public float norm() { return (float) ((value - min) / (max - min)); }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsDouble()); }
}
