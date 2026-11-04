package dev.rclient.anim;

/** 2D point that glides toward a target (HUD drag settling, cursor followers, scroll offsets). */
public final class AnimatedVec2 {
    public final AnimatedFloat x, y;

    public AnimatedVec2(float x, float y, float speed) {
        this.x = new AnimatedFloat(x, speed);
        this.y = new AnimatedFloat(y, speed);
    }

    public void set(float tx, float ty) { x.set(tx); y.set(ty); }
    public void snapTo(float vx, float vy) { x.snapTo(vx); y.snapTo(vy); }
    public float x() { return x.get(); }
    public float y() { return y.get(); }
}
