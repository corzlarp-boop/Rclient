package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.core.Theme;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base for every HUD widget. It is a Module (so it shows up in the ClickGUI HUD tab and has a toggle),
 * and adds: position, scale, opacity, themeable gradient background and optional "frosted" blur look.
 * Subclasses only describe their content size and draw it at (0,0); the base handles transform,
 * background, fade in/out and screen clamping.
 */
public abstract class HudElement extends Module {
    // position / scale are saved but hidden from the settings list; the Editor tab edits them
    public final NumberSetting posX = new NumberSetting("X", 10, 0, 4000, 1);
    public final NumberSetting posY = new NumberSetting("Y", 10, 0, 4000, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.05);
    public final NumberSetting opacity = new NumberSetting("Opacity", 1.0, 0.1, 1.0, 0.05);
    public final BoolSetting background = new BoolSetting("Background", true);
    public final BoolSetting blur = new BoolSetting("Frosted blur", true);
    public final ColorSetting bgColor = new ColorSetting("Background color", 0xFF111114, 0xFF1B1B20, true);
    public final BoolSetting useAccent = new BoolSetting("Accent colour", true);
    public final NumberSetting rounding = new NumberSetting("Rounding", 8, 0, 16, 1);

    public final AnimatedFloat fade = new AnimatedFloat(0f, 10f);

    protected HudElement(String name, String description, int x, int y) {
        super(name, description, Category.HUD);
        add(scale);
        add(opacity);
        add(background);
        add(blur);
        add(bgColor).visibleWhen(background::on);
        add(useAccent);
        add(rounding);
        add(posX).visibleWhen(() -> false);
        add(posY).visibleWhen(() -> false);
        posX.set((double) x);
        posY.set((double) y);
    }

    // ---- subclass API ----
    /** Content size in unscaled pixels. */
    public abstract int contentWidth();
    public abstract int contentHeight();
    /** Draw content with (0,0) as the top-left of the widget. */
    protected abstract void drawContent(GuiGraphics ctx, float partialTicks);

    /** Optional: skip the background panel for widgets that draw their own look. */
    protected boolean drawsOwnBackground() { return false; }

    public int x() { return posX.i(); }
    public int y() { return posY.i(); }
    public float s() { return scale.f(); }
    public int width() { return Math.round(contentWidth() * s()); }
    public int height() { return Math.round(contentHeight() * s()); }

    public void moveTo(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        posX.set((double) Math.max(0, Math.min(x, sw - width())));
        posY.set((double) Math.max(0, Math.min(y, sh - height())));
    }

    /** Accent used for highlights inside the widget. */
    protected int accent() { return useAccent.on() ? Theme.accent() : 0xFFFFFFFF; }

    public final void render(GuiGraphics ctx, float partialTicks, boolean editor) {
        fade.set(isEnabled() || editor ? 1f : 0f);
        float f = fade.get();
        if (f < 0.01f) return;
        float sc = s();
        // slight scale-in while fading
        float pop = 0.96f + 0.04f * f;
        int w = contentWidth(), h = contentHeight();
        float cx = x() + w * sc / 2f, cy = y() + h * sc / 2f;

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(sc * pop, sc * pop);
        ctx.pose().translate(-w / 2f, -h / 2f);

        float a = f * opacity.f();
        if (background.on() && !drawsOwnBackground()) {
            int r = rounding.i();
            int top = Colors.mulAlpha(bgColor.get(), a * (blur.on() ? 0.70f : 0.92f));
            int bot = Colors.mulAlpha(bgColor.gradient ? bgColor.second : bgColor.get(), a * (blur.on() ? 0.78f : 0.95f));
            RenderUtil.shadow(ctx, 0, 0, w, h, r, 8, Colors.argb((int) (80 * a), 0, 0, 0), 0, 2);
            RenderUtil.roundRect(ctx, 0, 0, w, h, r, top, bot);
            if (blur.on()) {
                // frosted sheen: faint light gradient on top
                RenderUtil.roundRect(ctx, 0, 0, w, h / 2, r, r, 0, 0, Colors.argb((int) (14 * a), 255, 255, 255), 0x00FFFFFF);
            }
            RenderUtil.border(ctx, 0, 0, w, h, r, 1,
                Colors.argb((int) (44 * a), 255, 255, 255), Colors.argb((int) (10 * a), 255, 255, 255));
        }
        drawContent(ctx, partialTicks);
        ctx.pose().popMatrix();
    }

    /** Hit test in screen coordinates (editor). */
    public boolean hit(double mx, double my) {
        return mx >= x() && mx <= x() + width() && my >= y() && my <= y() + height();
    }
}
