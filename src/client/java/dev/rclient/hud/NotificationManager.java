package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.anim.Animator;
import dev.rclient.core.ClientSettings;
import dev.rclient.core.Theme;
import dev.rclient.module.Module;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Slide-in toast notifications for module toggles. Newest at the bottom, stacking upwards. */
public final class NotificationManager {
    private NotificationManager() {}

    private static final class Toast {
        final String title, sub;
        final boolean on;
        float life = 2.2f;
        final AnimatedFloat in = new AnimatedFloat(0f, 12f);
        final AnimatedFloat slot = new AnimatedFloat(0f, 12f);
        Toast(String title, String sub, boolean on) { this.title = title; this.sub = sub; this.on = on; }
    }

    private static final List<Toast> TOASTS = new ArrayList<>();

    public static void toggled(Module m) {
        if (!ClientSettings.SHOW_NOTIFS.on()) return;
        if (m.category == dev.rclient.module.Category.HUD && !(m instanceof dev.rclient.hud.HudElement)) return;
        push(m.name, m.isEnabled() ? "Enabled" : "Disabled", m.isEnabled());
    }

    public static void push(String title, String sub, boolean on) {
        if (TOASTS.size() >= 5) TOASTS.remove(0);
        Toast t = new Toast(title, sub, on);
        t.in.set(1f);
        TOASTS.add(t);
    }

    public static void render(GuiGraphics ctx, int screenW, int screenH) {
        if (TOASTS.isEmpty()) return;
        RFont title = RFont.semi(8.5f), sub = RFont.regular(7.5f);
        float dt = Animator.dt();
        int w = 118, h = 28, gap = 5;
        int baseX = screenW - 12, baseY = screenH - 40;

        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            Toast t = TOASTS.get(i);
            t.life -= dt;
            if (t.life <= 0f) t.in.set(0f);
            if (t.life <= 0f && t.in.get() < 0.02f) { TOASTS.remove(i); continue; }
        }
        int n = TOASTS.size();
        for (int i = 0; i < n; i++) {
            Toast t = TOASTS.get(i);
            t.slot.set(n - 1 - i);
            float a = t.in.get();
            float slideX = (1f - a) * 30f;
            int x = (int) (baseX - w + slideX);
            int y = (int) (baseY - t.slot.get() * (h + gap));
            RenderUtil.panel(ctx, x, y, w, h, 8, 0xFF111114, Math.min(1f, a), true);
            int dot = t.on ? 0xFF3DDC84 : Theme.accent();
            RenderUtil.circle(ctx, x + 12, y + h / 2, 3, Colors.mulAlpha(dot, a));
            title.draw(ctx, t.title, x + 22, y + 5, Colors.mulAlpha(Colors.TEXT, a));
            sub.draw(ctx, t.sub, x + 22, y + 15.5f, Colors.mulAlpha(Colors.TEXT_DIM, a));
            // life bar
            float p = Math.max(0f, Math.min(1f, t.life / 2.2f));
            RenderUtil.roundRect(ctx, x + 8, y + h - 3, (int) ((w - 16) * p), 1, 0, Colors.mulAlpha(dot, a * 0.7f));
        }
    }
}
