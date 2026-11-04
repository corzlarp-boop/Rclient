package dev.rclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ModeSetting extends Setting<String> {
    public final String[] options;

    public ModeSetting(String name, String def, String... options) {
        super(name, def);
        this.options = options;
    }

    public boolean is(String s) { return value.equals(s); }
    public int index() {
        for (int i = 0; i < options.length; i++) if (options[i].equals(value)) return i;
        return 0;
    }
    public void cycle() { value = options[(index() + 1) % options.length]; }

    @Override public void set(String v) {
        for (String o : options) if (o.equals(v)) { value = v; return; }
    }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsString()); }
}
