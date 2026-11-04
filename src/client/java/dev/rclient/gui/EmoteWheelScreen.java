package dev.rclient.gui;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.core.Theme;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.visual.EmoteModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Radial emote wheel. Opened while the key is held; releasing it plays the highlighted emote,
 * Esc cancels. The slice under the cursor gets an accent outline; the centre shows its name.
 */
public final class EmoteWheelScreen extends RScreen {
    private final EmoteModule mod;
    private final int slots;
    private final AnimatedFloat open = new AnimatedFloat(0f, 16f);
    private final AnimatedFloat[] hot = new AnimatedFloat[8];
    private int hovered = -1;
    private boolean cancelled;

    public EmoteWheelScreen(EmoteModule mod) {
        super("Emotes");
        this.mod = mod;
        this.slots = mod.slots.i();
        for (int i = 0; i < hot.length; i++) hot[i] = new AnimatedFloat(0f, 18f);
    }

    @Override protected void init() { open.set(1f); }

    @Override protected void frame(GuiGraphics ctx, int mx, int my, float partial) {
        float o = open.get();
        RenderUtil.rect(ctx, 0, 0, width, height, Colors.argb((int) (110 * o), 0, 0, 0));
        int cx = width / 2, cy = height / 2;
        float R = 92 * (0.85f + 0.15f * o), r0 = 38;

        // hover from cursor angle
        double dx = mx - cx, dy = my - cy;
        double dist = Math.hypot(dx, dy);
        hovered = -1;
        if (dist > r0 * 0.6 && dist < R + 40) {
            double a = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / slots;
            a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
            hovered = (int) (a / (Math.PI * 2 / slots)) % slots;
        }

        for (int i = 0; i < slots; i++) {
            hot[i].set(i == hovered ? 1f : 0f);
            float h = hot[i].get();
            double mid = -Math.PI / 2 + i * Math.PI * 2 / slots;
            double span = Math.PI * 2 / slots * 0.94;
            slice(ctx, cx, cy, r0, R + 6 * h, mid - span / 2, mid + span / 2, h, o);
            float ix = cx + (float) Math.cos(mid) * (r0 + R) / 2f, iy = cy + (float) Math.sin(mid) * (r0 + R) / 2f;
            icon(ctx, mod.slotEmote(i).icon(), ix, iy - 5, Colors.mulAlpha(Colors.lerp(Colors.TEXT_DIM, Colors.WHITE, h), o));
            RFont.semi(7.5f).drawCentered(ctx, mod.slotEmote(i).name(), ix, iy + 9, Colors.mulAlpha(Colors.lerp(Colors.TEXT_DIM, Colors.WHITE, h), o));
        }
        RenderUtil.circle(ctx, cx, cy, (int) (r0 - 4), Colors.mulAlpha(0xFF111114, 0.92f * o));
        String name = hovered >= 0 ? mod.slotEmote(hovered).name() : "Choose";
        RFont.bold(9.5f).drawCentered(ctx, name, cx, cy - 6, Colors.mulAlpha(Colors.WHITE, o));
        RFont.regular(7f).drawCentered(ctx, "Release to play · Esc to cancel", cx, cy + R + 22, Colors.mulAlpha(Colors.TEXT_DIM, o));

        // release detection (client key held?)
        if (mod.keybind.bound() && mod.keybind.get() < 1000) {
            long win = Minecraft.getInstance().getWindow().handle();
            if (GLFW.glfwGetKey(win, mod.keybind.get()) == GLFW.GLFW_RELEASE) {
                if (!cancelled && hovered >= 0) EmoteModule.play(mod.slotEmote(hovered).name());
                onClose();
            }
        }
    }

    /** Annular sector drawn with scanline-free thin triangles (polygon fill). */
    private static final float[] PX = new float[4], PY = new float[4];
    private void slice(GuiGraphics ctx, int cx, int cy, float r0, float r1, double a0, double a1, float h, float o) {
        int steps = 10;
        int fill = Colors.lerp(Colors.argb((int) (200 * o), 20, 20, 24), Theme.accent((int) (90 * o) + 40), h);
        int edge = Colors.mulAlpha(Colors.lerp(0x30FFFFFF, Theme.accent(), h), o);
        for (int s = 0; s < steps; s++) {
            double b0 = a0 + (a1 - a0) * s / steps, b1 = a0 + (a1 - a0) * (s + 1) / steps;
            PX[0] = cx + (float) Math.cos(b0) * r0; PY[0] = cy + (float) Math.sin(b0) * r0;
            PX[1] = cx + (float) Math.cos(b0) * r1; PY[1] = cy + (float) Math.sin(b0) * r1;
            PX[2] = cx + (float) Math.cos(b1) * r1; PY[2] = cy + (float) Math.sin(b1) * r1;
            PX[3] = cx + (float) Math.cos(b1) * r0; PY[3] = cy + (float) Math.sin(b1) * r0;
            RenderUtil.polygon(ctx, PX, PY, 4, fill, 1);
            // outer rim
            RenderUtil.circle(ctx, Math.round(PX[1]), Math.round(PY[1]), 1, edge);
            if (h > 0.05f) RenderUtil.circle(ctx, Math.round(PX[0]), Math.round(PY[0]), 1, edge);
        }
    }

    /** Small primitive icons so no icon font is needed. */
    private void icon(GuiGraphics ctx, String id, float x, float y, int col) {
        int ix = Math.round(x), iy = Math.round(y);
        switch (id) {
            case "wave" -> { RenderUtil.roundRect(ctx, ix - 2, iy - 5, 4, 10, 2, col); RenderUtil.roundRect(ctx, ix - 6, iy - 3, 3, 6, 1, col); RenderUtil.roundRect(ctx, ix + 3, iy - 3, 3, 6, 1, col); }
            case "floss" -> { RenderUtil.roundRect(ctx, ix - 7, iy - 1, 14, 3, 1, col); RenderUtil.circle(ctx, ix - 7, iy, 3, col); RenderUtil.circle(ctx, ix + 7, iy + 1, 3, col); }
            case "bounce" -> { RenderUtil.circle(ctx, ix, iy, 5, col); RenderUtil.rect(ctx, ix - 6, iy + 7, 12, 1, col); }
            case "spin" -> { RenderUtil.circle(ctx, ix, iy, 6, col); RenderUtil.circle(ctx, ix, iy, 3, 0xFF111114); RenderUtil.rect(ctx, ix, iy - 7, 5, 2, col); }
            case "dab" -> { RenderUtil.circle(ctx, ix - 2, iy + 2, 3, col); RenderUtil.roundRect(ctx, ix, iy - 7, 3, 10, 1, col); RenderUtil.roundRect(ctx, ix - 7, iy - 3, 8, 3, 1, col); }
            case "clap" -> { RenderUtil.roundRect(ctx, ix - 6, iy - 4, 5, 8, 2, col); RenderUtil.roundRect(ctx, ix + 1, iy - 4, 5, 8, 2, col); }
            case "flex" -> { RenderUtil.circle(ctx, ix - 3, iy, 5, col); RenderUtil.roundRect(ctx, ix - 1, iy - 7, 7, 4, 2, col); }
            default -> { RenderUtil.roundRect(ctx, ix - 6, iy - 1, 12, 3, 1, col); RenderUtil.circle(ctx, ix + 5, iy - 4, 3, col); }
        }
    }

    @Override protected boolean onKey(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { cancelled = true; onClose(); return true; }
        return false;
    }

    @Override protected boolean onMouseDown(double x, double y, int button) {
        if (button == 0 && hovered >= 0) { EmoteModule.play(mod.slotEmote(hovered).name()); onClose(); return true; }
        return false;
    }
}
