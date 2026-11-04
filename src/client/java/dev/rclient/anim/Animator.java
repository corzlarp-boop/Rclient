package dev.rclient.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * Global delta-time clock. Call {@link #frame(long)} once per rendered frame (we do it from the
 * client render hook). Every Animated* value reads {@link #dt()} so nothing depends on frame rate.
 * A global speed multiplier is exposed for the Settings tab.
 */
public final class Animator {
    private static long lastNanos = -1;
    private static float dt = 1f / 60f;
    private static float time = 0f;
    private static float speed = 1f;

    private static final List<Animated> ACTIVE = new ArrayList<>();

    private Animator() {}

    /**
     * Advance the clock. Safe to call from several places per frame (HUD layer, screens): calls closer
     * than 0.5 ms to the previous one are treated as the same frame and ignored, so dt is never zeroed.
     */
    public static void frame(long nowNanos) {
        if (lastNanos >= 0 && nowNanos - lastNanos < 500_000L) return;
        if (lastNanos < 0) lastNanos = nowNanos;
        float raw = (nowNanos - lastNanos) / 1_000_000_000f;
        lastNanos = nowNanos;
        // clamp so alt-tab / lag spikes never teleport animations
        dt = Math.min(Math.max(raw, 0f), 0.05f) * speed;
        time += dt;
        AnimatedFloat.nextFrame();
    }

    /** Scaled delta time in seconds for the current frame. */
    public static float dt() { return dt; }
    /** Scaled running time in seconds (for idle loops, drifting shapes, etc). */
    public static float time() { return time; }

    public static void setSpeed(float s) { speed = Math.max(0.1f, Math.min(s, 3f)); }
    public static float getSpeed() { return speed; }

    /** Anything that can be advanced manually. */
    public interface Animated { void update(float dt); }

    public static void register(Animated a) { ACTIVE.add(a); }
    public static void unregister(Animated a) { ACTIVE.remove(a); }
}
