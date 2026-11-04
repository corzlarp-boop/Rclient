package dev.rclient.anim;

/** ARGB colour that glides per-channel toward a target. */
public final class AnimatedColor {
    private final AnimatedFloat a, r, g, b;

    public AnimatedColor(int argb, float speed) {
        a = new AnimatedFloat(argb >>> 24, speed);
        r = new AnimatedFloat((argb >> 16) & 255, speed);
        g = new AnimatedFloat((argb >> 8) & 255, speed);
        b = new AnimatedFloat(argb & 255, speed);
    }

    public void set(int argb) {
        a.set(argb >>> 24);
        r.set((argb >> 16) & 255);
        g.set((argb >> 8) & 255);
        b.set(argb & 255);
    }

    public void snapTo(int argb) {
        a.snapTo(argb >>> 24);
        r.snapTo((argb >> 16) & 255);
        g.snapTo((argb >> 8) & 255);
        b.snapTo(argb & 255);
    }

    public int get() {
        int ia = clamp(a.get()), ir = clamp(r.get()), ig = clamp(g.get()), ib = clamp(b.get());
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static int clamp(float v) { return v < 0 ? 0 : Math.min(255, (int) (v + 0.5f)); }
}
