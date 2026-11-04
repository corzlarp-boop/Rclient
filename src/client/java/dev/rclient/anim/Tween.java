package dev.rclient.anim;

/**
 * Fixed-duration, eased 0→1 progress with reverse support. Use for open/close transitions and
 * staggered entrances where a timeline (not a chase) is wanted. Direction can flip mid-flight and
 * continues from the current progress, so it never snaps.
 */
public final class Tween {
    private float progress;
    private boolean forward;
    private final float durIn, durOut;
    private final Easing easeIn, easeOut;

    public Tween(float durIn, float durOut, Easing easeIn, Easing easeOut) {
        this.durIn = durIn; this.durOut = durOut; this.easeIn = easeIn; this.easeOut = easeOut;
    }

    public void forward() { forward = true; }
    public void backward() { forward = false; }
    public void setForward(boolean f) { forward = f; }
    public void reset() { progress = 0; }

    public void update(float dt) {
        if (forward) progress = Math.min(1f, progress + dt / durIn);
        else progress = Math.max(0f, progress - dt / durOut);
    }

    /** raw linear progress 0..1 */
    public float raw() { return progress; }
    /** eased progress */
    public float eased() { return (forward ? easeIn : easeOut).apply(progress); }
    public boolean done() { return forward ? progress >= 1f : progress <= 0f; }
}
