package dev.rclient.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** Typed, serialisable setting. Right-click in the GUI calls {@link #reset()}. */
public abstract class Setting<T> {
    public final String name;
    public String description = "";
    protected T value;
    protected final T def;
    private BooleanSupplier visible = () -> true;

    protected Setting(String name, T def) {
        this.name = name;
        this.def = def;
        this.value = def;
    }

    public T get() { return value; }
    public void set(T v) { this.value = v; }
    public void reset() { this.value = def; }
    public boolean isDefault() { return value.equals(def); }

    @SuppressWarnings("unchecked") public <S extends Setting<T>> S desc(String d) { this.description = d; return (S) this; }
    @SuppressWarnings("unchecked") public <S extends Setting<T>> S visibleWhen(BooleanSupplier s) { this.visible = s; return (S) this; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);
}
