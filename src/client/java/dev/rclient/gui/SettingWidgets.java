package dev.rclient.gui;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.core.Theme;
import dev.rclient.gui.Regions.Kind;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.*;
import net.minecraft.client.gui.GuiGraphics;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Draws and registers the inline setting rows (toggle, slider, dropdown, colour picker, keybind). */
public final class SettingWidgets {
    private SettingWidgets() {}

    public static final int ROW_BOOL = 20, ROW_NUM = 28, ROW_MODE = 22, ROW_KEY = 22, ROW_COLOR = 22;
    public static final int OPT_H = 16, COLOR_OPEN_H = 106;

    private static final Map<Object, AnimatedFloat> ANIMS = new IdentityHashMap<>();
    private static final Map<ColorSetting, float[]> HSB = new IdentityHashMap<>();   // h,s,b (stop 1), h,s,b (stop 2), editSecond
    public static KeybindSetting listening;

    private static AnimatedFloat anim(Object key, float initial, float speed) {
        AnimatedFloat a = ANIMS.get(key);
        if (a == null) { a = new AnimatedFloat(initial, speed); ANIMS.put(key, a); }
        return a;
    }

    public static AnimatedFloat openAnim(Setting<?> s) { return anim(s, 0f, 14f); }
    public static boolean isOpen(Setting<?> s) { return openAnim(s).target() > 0.5f; }
    public static void toggleOpen(Setting<?> s) { openAnim(s).set(isOpen(s) ? 0f : 1f); }

    public static float[] hsb(ColorSetting c) {
        float[] h = HSB.get(c);
        if (h == null) {
            h = new float[7];
            java.awt.Color.RGBtoHSB(Colors.red(c.get()), Colors.green(c.get()), Colors.blue(c.get()), tmp);
            h[0] = tmp[0]; h[1] = tmp[1]; h[2] = tmp[2];
            java.awt.Color.RGBtoHSB(Colors.red(c.second), Colors.green(c.second), Colors.blue(c.second), tmp);
            h[3] = tmp[0]; h[4] = tmp[1]; h[5] = tmp[2];
            HSB.put(c, h);
        }
        return h;
    }
    private static final float[] tmp = new float[3];

    /** After a reset, recompute the cached HSB from the actual colours. */
    public static void invalidate(ColorSetting c) { HSB.remove(c); }

    public static void applyHsb(ColorSetting c) {
        float[] h = hsb(c);
        c.set(Colors.hsb(h[0], h[1], h[2], 255));
        c.second = Colors.hsb(h[3], h[4], h[5], 255);
    }

    /** Height of one setting row including its animated open area. */
    public static int height(Setting<?> s) {
        if (s instanceof BoolSetting) return ROW_BOOL;
        if (s instanceof NumberSetting) return ROW_NUM;
        if (s instanceof KeybindSetting) return ROW_KEY;
        if (s instanceof ModeSetting m) return ROW_MODE + Math.round(openAnim(s).get() * m.options.length * OPT_H);
        if (s instanceof ColorSetting) return ROW_COLOR + Math.round(openAnim(s).get() * COLOR_OPEN_H);
        return ROW_BOOL;
    }

    public static int totalHeight(List<? extends Setting<?>> list) {
        int h = 0;
        for (Setting<?> s : list) if (s.isVisible()) h += height(s);
        return h;
    }

    // ------------------------------------------------------------------ drawing

    /** Draw one row at (x, y) with width w. Returns row height. alpha 0..1 multiplies everything. */
    public static int draw(GuiGraphics ctx, Regions reg, Setting<?> s, int x, int y, int w, float alpha, boolean clipped, int mx, int my) {
        RFont label = RFont.regular(8f), small = RFont.regular(7.5f), semi = RFont.semi(7.5f);
        int tc = Colors.mulAlpha(Colors.TEXT_DIM, alpha);
        int hv = Colors.mulAlpha(Colors.TEXT, alpha);

        if (s instanceof BoolSetting b) {
            AnimatedFloat a = anim(b, b.on() ? 1f : 0f, 18f);
            a.set(b.on() ? 1f : 0f);
            label.draw(ctx, s.name, x, y + 4, tc);
            pill(ctx, x + w - 24, y + 4, 24, 12, a.get(), alpha);
            reg.add(Kind.S_BOOL, x, y, w, ROW_BOOL, s, 0, clipped);
            return ROW_BOOL;
        }

        if (s instanceof NumberSetting n) {
            AnimatedFloat a = anim(n, n.norm(), 20f);
            a.set(n.norm());
            label.draw(ctx, s.name, x, y + 2, tc);
            String v = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.2f", n.get());
            small.drawRight(ctx, v, x + w, y + 2, hv);
            int ty = y + 18;
            RenderUtil.roundRect(ctx, x, ty, w, 4, 2, Colors.mulAlpha(0xFF2A2A30, alpha));
            int fw = Math.max(4, (int) (w * a.get()));
            RenderUtil.roundRectH(ctx, x, ty, fw, 4, 2, Colors.mulAlpha(Theme.accent(), alpha), Colors.mulAlpha(Theme.accent2(), alpha));
            RenderUtil.circle(ctx, x + (int) (w * a.get()), ty + 2, 5, Colors.mulAlpha(Colors.WHITE, alpha));
            reg.add(Kind.S_NUM, x, y + 10, w, 18, s, 0, clipped);
            return ROW_NUM;
        }

        if (s instanceof ModeSetting m) {
            AnimatedFloat open = openAnim(m);
            label.draw(ctx, s.name, x, y + 4, tc);
            int cw = (int) semi.width(m.get()) + 22;
            RenderUtil.roundRect(ctx, x + w - cw, y + 1, cw, 17, 6, Colors.mulAlpha(0xFF24242A, alpha));
            semi.draw(ctx, m.get(), x + w - cw + 8, y + 5, hv);
            // chevron
            RenderUtil.rect(ctx, x + w - 12, y + 8, 5, 1, hv);
            reg.add(Kind.S_MODE, x, y, w, ROW_MODE, s, 0, clipped);
            float o = open.get();
            if (o > 0.02f) {
                int oy = y + ROW_MODE;
                int full = m.options.length * OPT_H;
                int vis = Math.round(full * o);
                RenderUtil.scissor(ctx, x, oy, w, vis);
                RenderUtil.roundRect(ctx, x, oy, w, full, 7, Colors.mulAlpha(0xFF18181C, alpha));
                for (int i = 0; i < m.options.length; i++) {
                    boolean cur = m.options[i].equals(m.get());
                    boolean hov = mx >= x && mx < x + w && my >= oy + i * OPT_H && my < oy + (i + 1) * OPT_H;
                    if (cur) RenderUtil.roundRect(ctx, x + 2, oy + i * OPT_H + 1, w - 4, OPT_H - 2, 5, Theme.tint(0.22f * alpha));
                    else if (hov) RenderUtil.roundRect(ctx, x + 2, oy + i * OPT_H + 1, w - 4, OPT_H - 2, 5, Colors.argb((int) (14 * alpha), 255, 255, 255));
                    small.draw(ctx, m.options[i], x + 9, oy + i * OPT_H + 4, cur ? Colors.mulAlpha(Colors.WHITE, alpha) : tc);
                    reg.add(Kind.S_MODE_OPT, x, oy + i * OPT_H, w, OPT_H, m, i, clipped);
                }
                RenderUtil.endScissor(ctx);
            }
            return height(m);
        }

        if (s instanceof KeybindSetting k) {
            label.draw(ctx, s.name, x, y + 4, tc);
            boolean l = listening == k;
            String t = l ? "Press a key…" : k.label();
            int cw = (int) semi.width(t) + 16;
            RenderUtil.roundRect(ctx, x + w - cw, y + 1, cw, 17, 6, l ? Theme.tint(0.35f * alpha) : Colors.mulAlpha(0xFF24242A, alpha));
            if (l) RenderUtil.border(ctx, x + w - cw, y + 1, cw, 17, 6, 1, Theme.accent((int) (200 * alpha)), Theme.accent((int) (90 * alpha)));
            semi.draw(ctx, t, x + w - cw + 8, y + 5, hv);
            reg.add(Kind.S_KEY, x, y, w, ROW_KEY, s, 0, clipped);
            return ROW_KEY;
        }

        if (s instanceof ColorSetting c) {
            AnimatedFloat open = openAnim(c);
            label.draw(ctx, s.name, x, y + 4, tc);
            // preview swatch (gradient aware)
            int sw = 34;
            if (c.gradient || c.rainbow) RenderUtil.roundRectH(ctx, x + w - sw, y + 2, sw, 14, 7, Colors.mulAlpha(c.at(0), alpha), Colors.mulAlpha(c.at(1), alpha));
            else RenderUtil.roundRect(ctx, x + w - sw, y + 2, sw, 14, 7, Colors.mulAlpha(c.get(), alpha));
            RenderUtil.border(ctx, x + w - sw, y + 2, sw, 14, 7, 1, Colors.argb((int) (60 * alpha), 255, 255, 255), Colors.argb((int) (20 * alpha), 255, 255, 255));
            reg.add(Kind.S_COLOR_HEAD, x, y, w, ROW_COLOR, s, 0, clipped);
            float o = open.get();
            if (o > 0.02f) {
                int oy = y + ROW_COLOR, full = COLOR_OPEN_H;
                RenderUtil.scissor(ctx, x - 2, oy, w + 4, Math.round(full * o));
                drawPicker(ctx, reg, c, x, oy + 2, w, alpha * o, clipped, small, semi);
                RenderUtil.endScissor(ctx);
            }
            return height(c);
        }
        return ROW_BOOL;
    }

    private static void drawPicker(GuiGraphics ctx, Regions reg, ColorSetting c, int x, int y, int w, float alpha, boolean clipped, RFont small, RFont semi) {
        float[] h = hsb(c);
        boolean second = h[6] > 0.5f;
        int o = second ? 3 : 0;
        int sqW = Math.min(w - 22, 150), sqH = 56;
        // saturation / brightness square: columns of vertical gradients
        int step = 2;
        for (int cx = 0; cx < sqW; cx += step) {
            float sat = cx / (float) sqW;
            ctx.fillGradient(x + cx, y, x + Math.min(cx + step, sqW), y + sqH,
                Colors.mulAlpha(Colors.hsb(h[o], sat, 1f, 255), alpha), Colors.mulAlpha(0xFF000000, alpha));
        }
        RenderUtil.border(ctx, x, y, sqW, sqH, 0, 1, Colors.argb((int) (50 * alpha), 255, 255, 255), Colors.argb((int) (50 * alpha), 255, 255, 255));
        RenderUtil.circle(ctx, x + (int) (h[o + 1] * sqW), y + (int) ((1f - h[o + 2]) * sqH), 4, Colors.mulAlpha(Colors.WHITE, alpha));
        RenderUtil.circle(ctx, x + (int) (h[o + 1] * sqW), y + (int) ((1f - h[o + 2]) * sqH), 3,
            Colors.mulAlpha(Colors.hsb(h[o], h[o + 1], h[o + 2], 255), alpha));
        reg.add(Kind.S_COLOR_SV, x, y, sqW, sqH, c, 0, clipped);
        // hue strip
        int hx = x + sqW + 8, hw = 10, segs = 28;
        for (int i = 0; i < segs; i++) {
            int y0 = y + i * sqH / segs, y1 = y + (i + 1) * sqH / segs;
            ctx.fillGradient(hx, y0, hx + hw, y1, Colors.mulAlpha(Colors.hsb(i / (float) segs, 1f, 1f, 255), alpha),
                Colors.mulAlpha(Colors.hsb((i + 1) / (float) segs, 1f, 1f, 255), alpha));
        }
        RenderUtil.circle(ctx, hx + hw / 2, y + (int) (h[o] * sqH), 4, Colors.mulAlpha(Colors.WHITE, alpha));
        reg.add(Kind.S_COLOR_HUE, hx - 2, y, hw + 4, sqH, c, 0, clipped);

        // chips: stop selector + gradient + rainbow
        int cy = y + sqH + 8;
        chip(ctx, reg, "Color 1", x, cy, 50, !second, alpha, Kind.S_COLOR_STOP, c, 0, clipped, semi);
        chip(ctx, reg, "Color 2", x + 54, cy, 50, second, alpha, Kind.S_COLOR_STOP, c, 1, clipped, semi);
        chip(ctx, reg, "Gradient", x + 108, cy, 54, c.gradient, alpha, Kind.S_COLOR_GRAD, c, 0, clipped, semi);
        chip(ctx, reg, "Rainbow", x + 166, cy, 54, c.rainbow, alpha, Kind.S_COLOR_RAINBOW, c, 0, clipped, semi);
    }

    private static void chip(GuiGraphics ctx, Regions reg, String t, int x, int y, int w, boolean on, float alpha,
                             Kind k, Object o, int extra, boolean clipped, RFont f) {
        RenderUtil.roundRect(ctx, x, y, w, 16, 6, on ? Theme.tint(0.35f * alpha) : Colors.mulAlpha(0xFF24242A, alpha));
        if (on) RenderUtil.border(ctx, x, y, w, 16, 6, 1, Theme.accent((int) (160 * alpha)), Theme.accent((int) (60 * alpha)));
        f.drawCentered(ctx, t, x + w / 2f, y + 4.5f, Colors.mulAlpha(on ? Colors.WHITE : Colors.TEXT_DIM, alpha));
        reg.add(k, x, y, w, 16, o, extra, clipped);
    }

    public static void pill(GuiGraphics ctx, int x, int y, int w, int h, float t, float alpha) {
        int off = Colors.mulAlpha(0xFF2A2A30, alpha);
        int on = Colors.mulAlpha(Theme.accent(), alpha);
        float tc = Math.max(0f, Math.min(1f, t));
        RenderUtil.roundRect(ctx, x, y, w, h, h / 2, Colors.lerp(off, on, tc));
        int r = h / 2 - 2;
        int kx = (int) (x + h / 2 + (w - h) * t);   // spring may overshoot slightly – that's the point
        RenderUtil.circle(ctx, kx, y + h / 2, r, Colors.mulAlpha(Colors.WHITE, alpha));
    }

    // ------------------------------------------------------------------ input

    public static void slide(NumberSetting n, double mx, int x, int w) {
        double t = Math.max(0, Math.min(1, (mx - x) / w));
        n.set(n.min + (n.max - n.min) * t);
    }

    public static void pickSv(ColorSetting c, double mx, double my, int x, int y, int w, int h) {
        float[] hs = hsb(c);
        int o = hs[6] > 0.5f ? 3 : 0;
        hs[o + 1] = (float) Math.max(0, Math.min(1, (mx - x) / w));
        hs[o + 2] = 1f - (float) Math.max(0, Math.min(1, (my - y) / h));
        applyHsb(c);
    }

    public static void pickHue(ColorSetting c, double my, int y, int h) {
        float[] hs = hsb(c);
        int o = hs[6] > 0.5f ? 3 : 0;
        hs[o] = (float) Math.max(0, Math.min(0.999, (my - y) / h));
        applyHsb(c);
    }
}
