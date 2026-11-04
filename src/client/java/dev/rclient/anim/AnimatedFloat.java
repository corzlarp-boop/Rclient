package dev.rclient.anim;

/**
 * A float that always glides toward its target. Two modes:
 *  - exponential smoothing (default): frame-rate independent, never snaps, critically damped feel.
 *  - spring: velocity + stiffness/damping for overshoot (toggle knobs, pop-ins).
 * Reading {@link #get()} advances the value once per frame (guarded by a frame stamp) so callers
 * do not need to tick it explicitly and there is zero per-frame allocation.
 */
public final class AnimatedFloat {
    private float value, target, velocity;
    private float speed;           // exponential rate (1/s) – higher = faster
    private boolean spring;
    private float stiffness = 260f, damping = 22f;
    private long stamp = Long.MIN_VALUE;
    private static long frameCounter = 0;

    public AnimatedFloat(float initial, float speed) {
        this.value = this.target = initial;
        this.speed = speed;
    }

    public static AnimatedFloat spring(float initial, float stiffness, float damping) {
        AnimatedFloat f = new AnimatedFloat(initial, 0);
        f.spring = true;
        f.stiffness = stiffness;
        f.damping = damping;
        return f;
    }

    /** Called by the client render hook once per frame so stamps differ between frames. */
    public static void nextFrame() { frameCounter++; }

    public void set(float target) { this.target = target; }
    public void snapTo(float v) { value = target = v; velocity = 0; }
    public float target() { return target; }

    public float get() {
        if (stamp != frameCounter) {
            stamp = frameCounter;
            step(Animator.dt());
        }
        return value;
    }

    public boolean isAnimating() { return Math.abs(value - target) > 0.0005f || Math.abs(velocity) > 0.001f; }

    private void step(float dt) {
        if (spring) {
            // semi-implicit Euler, sub-stepped for stability at low fps
            int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                float a = stiffness * (target - value) - damping * velocity;
                velocity += a * h;
                value += velocity * h;
            }
            if (Math.abs(target - value) < 0.0005f && Math.abs(velocity) < 0.001f) { value = target; velocity = 0; }
        } else {
            float k = 1f - (float) Math.exp(-speed * dt);
            value += (target - value) * k;
            if (Math.abs(target - value) < 0.0005f) value = target;
        }
    }
}
