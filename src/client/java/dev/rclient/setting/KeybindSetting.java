package dev.rclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

/** GLFW key code; -1 = unbound. Mouse buttons are stored as 1000 + button. */
public final class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, int def) { super(name, def); }

    public boolean bound() { return value >= 0; }

    public String label() {
        if (value < 0) return "None";
        if (value >= 1000) return "Mouse " + (value - 1000 + 1);
        String n = GLFW.glfwGetKeyName(value, 0);
        if (n != null) return n.toUpperCase();
        return switch (value) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            default -> (value >= GLFW.GLFW_KEY_F1 && value <= GLFW.GLFW_KEY_F25)
                ? "F" + (value - GLFW.GLFW_KEY_F1 + 1) : "Key " + value;
        };
    }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { if (e.isJsonPrimitive()) value = e.getAsInt(); }
}
