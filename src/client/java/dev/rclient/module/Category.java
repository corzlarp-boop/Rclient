package dev.rclient.module;

public enum Category {
    VISUALS("Visuals"),
    COSMETICS("Cosmetics"),
    HUD("HUD"),
    MISC("Misc");

    public final String label;
    Category(String label) { this.label = label; }
}
