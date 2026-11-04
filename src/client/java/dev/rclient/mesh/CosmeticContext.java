package dev.rclient.mesh;

/** Per-frame inputs for cosmetic meshes. Reused (mutable) to avoid allocation. */
public final class CosmeticContext {
    public float time;          // seconds, scaled by animation speed
    public float speed;         // horizontal speed (blocks / tick)
    public float verticalVel;   // blocks / tick
    public float headPitch;     // degrees
    public float headYawRel;    // head yaw relative to body, degrees
    public boolean sneaking;
    public boolean sprinting;
    public boolean airborne;
    public boolean inMenu;      // preview in the main menu
    public float partial;
    // player feet position (world, interpolated) and camera position (world) — for world-aligned effects
    public double px, py, pz, camX, camY, camZ;

    public float sneakDrop() { return sneaking ? 0.12f : 0f; }
}
