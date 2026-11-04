package dev.rclient.gui;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.core.ConfigManager;
import dev.rclient.core.Theme;
import dev.rclient.hud.HudElement;
import dev.rclient.hud.HudManager;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.BoolSetting;
import dev.rclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Drag-and-drop HUD editor. Drag widgets to move them (edges snap to the screen centre lines, the
 * screen edges and other widgets), drag the corner handle to scale, click a widget to select it and
 * edit blur / gradient / opacity in the side panel.
 */
public final class HudEditorScreen extends RScreen {
    private final net.minecraft.client.gui.screens.Screen parent;
    private HudElement selected, dragging;
    private boolean resizing;
    private double grabX, grabY;
    private int startX, startY; private float startScale; private double startMx;
    private final AnimatedFloat open = new AnimatedFloat(0f, 12f);
    private final AnimatedFloat snapX = new AnimatedFloat(0f, 30f), snapY = new AnimatedFloat(0f, 30f);
    private boolean showSnapX, showSnapY;

    public HudEditorScreen(net.minecraft.client.gui.screens.Screen parent) {
        super("HUD Editor");
        this.parent = parent;
    }

    @Override protected void init() { open.set(1f); }

    @Override public void onClose() {
        ConfigManager.autosave();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override protected void frame(GuiGraphics ctx, int mx, int my, float partial) {
        float o = open.get();
        // dim + subtle grid so the layout is easy to judge
        RenderUtil.rect(ctx, 0, 0, width, height, Colors.argb((int) (120 * o), 0, 0, 0));
        int grid = 20;
        for (int x = 0; x < width; x += grid) RenderUtil.rect(ctx, x, 0, 1, height, Colors.argb((int) (10 * o), 255, 255, 255));
        for (int y = 0; y < height; y += grid) RenderUtil.rect(ctx, 0, y, width, 1, Colors.argb((int) (10 * o), 255, 255, 255));

        HudManager.render(ctx, partial, true);

        for (HudElement e : HudManager.elements()) {
            boolean sel = e == selected, hov = e.hit(mx, my);
            int a = sel ? 230 : hov ? 150 : 60;
            RenderUtil.border(ctx, e.x() - 2, e.y() - 2, e.width() + 4, e.height() + 4, 9, 1,
                Colors.withAlpha(sel ? Theme.accent() : 0xFFFFFFFF, a), Colors.withAlpha(sel ? Theme.accent2() : 0xFFFFFFFF, a / 2));
            if (!e.isEnabled()) RFont.regular(6.5f).draw(ctx, "OFF", e.x() + e.width() - 16, e.y() - 11, Colors.TEXT_FAINT);
            if (sel) {
                RenderUtil.circle(ctx, e.x() + e.width() + 2, e.y() + e.height() + 2, 4, Theme.accent());
            }
        }

        // snap guides
        if (dragging != null) {
            if (showSnapX) RenderUtil.rect(ctx, (int) snapX.get(), 0, 1, height, Theme.accent(150));
            if (showSnapY) RenderUtil.rect(ctx, 0, (int) snapY.get(), width, 1, Theme.accent(150));
        }

        RFont h = RFont.semi(9f);
        h.drawCentered(ctx, "HUD Editor", width / 2f, 10, Colors.mulAlpha(Colors.TEXT, o));
        RFont.regular(7.5f).drawCentered(ctx, "Drag to move · corner to scale · click to select · Esc to save & exit",
            width / 2f, 23, Colors.mulAlpha(Colors.TEXT_DIM, o));

        if (selected != null) panel(ctx, mx, my);
    }

    // ---- side panel for the selected widget ----
    private static final int PW = 150;
    private int panelX() { return width - PW - 12; }

    private void panel(GuiGraphics ctx, int mx, int my) {
        HudElement e = selected;
        int x = panelX(), y = 40, h = 138;
        RenderUtil.panel(ctx, x, y, PW, h, 10, 0xFF111114, 1f, true);
        RFont.semi(8.5f).draw(ctx, e.name, x + 10, y + 8, Colors.TEXT);
        row(ctx, x, y + 26, "Enabled", e.isEnabled());
        row(ctx, x, y + 42, "Frosted blur", e.blur.on());
        row(ctx, x, y + 58, "Background", e.background.on());
        row(ctx, x, y + 74, "Gradient", e.bgColor.gradient);
        slider(ctx, x, y + 92, "Scale", e.scale);
        slider(ctx, x, y + 114, "Opacity", e.opacity);
    }

    private void row(GuiGraphics ctx, int x, int y, String label, boolean on) {
        RFont.regular(7.5f).draw(ctx, label, x + 10, y + 2, Colors.TEXT_DIM);
        int tx = x + PW - 10 - 22;
        RenderUtil.roundRect(ctx, tx, y, 22, 11, 5, on ? Theme.accent() : 0xFF2A2A30);
        RenderUtil.circle(ctx, on ? tx + 17 : tx + 5, y + 5, 4, Colors.WHITE);
    }

    private void slider(GuiGraphics ctx, int x, int y, String label, NumberSetting s) {
        RFont f = RFont.regular(7.5f);
        f.draw(ctx, label, x + 10, y, Colors.TEXT_DIM);
        f.drawRight(ctx, String.format("%.2f", s.get()), x + PW - 10, y, Colors.TEXT);
        int tw = PW - 20;
        RenderUtil.roundRect(ctx, x + 10, y + 11, tw, 3, 1, 0xFF2A2A30);
        RenderUtil.roundRectH(ctx, x + 10, y + 11, Math.max(3, (int) (tw * s.norm())), 3, 1, Theme.accent(), Theme.accent2());
        RenderUtil.circle(ctx, x + 10 + (int) (tw * s.norm()), y + 12, 4, Colors.WHITE);
    }

    private boolean panelClick(double mx, double my) {
        if (selected == null) return false;
        int x = panelX(), y = 40;
        if (mx < x || mx > x + PW || my < y || my > y + 138) return false;
        int ry = (int) my - y;
        if (ry >= 26 && ry < 42) selected.toggle();
        else if (ry >= 42 && ry < 58) selected.blur.toggle();
        else if (ry >= 58 && ry < 74) selected.background.toggle();
        else if (ry >= 74 && ry < 90) selected.bgColor.gradient = !selected.bgColor.gradient;
        else if (ry >= 90 && ry < 112) setSlider(selected.scale, mx, x);
        else if (ry >= 112) setSlider(selected.opacity, mx, x);
        return true;
    }

    private void setSlider(NumberSetting s, double mx, int px) {
        double t = (mx - (px + 10)) / (PW - 20);
        t = Math.max(0, Math.min(1, t));
        s.set(s.min + (s.max - s.min) * t);
    }

    @Override protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) return false;
        if (panelClick(mx, my)) return true;
        // iterate topmost first
        var list = HudManager.elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudElement e = list.get(i);
            if (!e.hit(mx, my) && !(Math.abs(mx - (e.x() + e.width() + 2)) < 6 && Math.abs(my - (e.y() + e.height() + 2)) < 6 && e == selected)) continue;
            selected = e;
            boolean handle = Math.abs(mx - (e.x() + e.width() + 2)) < 6 && Math.abs(my - (e.y() + e.height() + 2)) < 6;
            dragging = e;
            resizing = handle;
            grabX = mx - e.x(); grabY = my - e.y();
            startScale = e.scale.f(); startMx = mx; startX = e.x(); startY = e.y();
            return true;
        }
        selected = null;
        return true;
    }

    @Override protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        if (dragging == null) {
            if (selected != null && my >= 40 && mx >= panelX()) {
                int ry = (int) my - 40;
                if (ry >= 90 && ry < 112) { setSlider(selected.scale, mx, panelX()); return true; }
                if (ry >= 112 && ry < 138) { setSlider(selected.opacity, mx, panelX()); return true; }
            }
            return false;
        }
        HudElement e = dragging;
        if (resizing) {
            double ds = (mx - startMx) / Math.max(20, e.contentWidth());
            e.scale.set(startScale + ds);
            return true;
        }
        int nx = (int) (mx - grabX), ny = (int) (my - grabY);
        showSnapX = showSnapY = false;
        int t = 5;
        // screen edges + centre
        int[] xs = {0, (width - e.width()) / 2, width - e.width()};
        int[] ys = {0, (height - e.height()) / 2, height - e.height()};
        for (int sx : xs) if (Math.abs(nx - sx) < t) { nx = sx; showSnapX = true; snapX.snapTo(sx == 0 ? 0 : sx + e.width() / 2f * (sx == xs[1] ? 1 : 0) + (sx == xs[2] ? e.width() : 0)); }
        for (int sy : ys) if (Math.abs(ny - sy) < t) { ny = sy; showSnapY = true; snapY.snapTo(sy == 0 ? 0 : sy + e.height() / 2f * (sy == ys[1] ? 1 : 0) + (sy == ys[2] ? e.height() : 0)); }
        // other widgets
        for (HudElement o : HudManager.elements()) {
            if (o == e || !o.isEnabled()) continue;
            if (Math.abs(nx - (o.x() + o.width())) < t) nx = o.x() + o.width();
            else if (Math.abs(nx + e.width() - o.x()) < t) nx = o.x() - e.width();
            else if (Math.abs(nx - o.x()) < t) nx = o.x();
            if (Math.abs(ny - (o.y() + o.height())) < t) ny = o.y() + o.height();
            else if (Math.abs(ny + e.height() - o.y()) < t) ny = o.y() - e.height();
            else if (Math.abs(ny - o.y()) < t) ny = o.y();
        }
        e.moveTo(nx, ny);
        return true;
    }

    @Override protected boolean onMouseUp(double mx, double my, int button) {
        boolean was = dragging != null;
        dragging = null; resizing = false; showSnapX = showSnapY = false;
        return was;
    }

    @Override protected boolean onKey(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return false;
    }
}
