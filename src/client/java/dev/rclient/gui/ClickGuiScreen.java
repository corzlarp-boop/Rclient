package dev.rclient.gui;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.anim.Animator;
import dev.rclient.anim.Easing;
import dev.rclient.anim.Tween;
import dev.rclient.core.ClientSettings;
import dev.rclient.core.ConfigManager;
import dev.rclient.core.Theme;
import dev.rclient.gui.Regions.Kind;
import dev.rclient.gui.Regions.Region;
import dev.rclient.module.Category;
import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;
import dev.rclient.render.Colors;
import dev.rclient.render.RClientLogo;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The main RClient window. */
public final class ClickGuiScreen extends RScreen {

    private enum Tab {
        FAVORITES("Favorites", true), VISUALS("Visuals", true), COSMETICS("Cosmetics", true), HUD("HUD", true), MISC("Misc", true),
        EDITOR("Editor", false), CONFIGS("Configs", false), SETTINGS("Settings", false);
        final String label; final boolean feature;
        Tab(String l, boolean f) { label = l; feature = f; }
    }

    private static Tab lastTab = Tab.VISUALS;

    private final Screen parent;
    private Tab tab = lastTab;
    private final Regions reg = new Regions();

    // open/close
    private final Tween open = new Tween(0.32f, 0.16f, Easing.EXPO_OUT, Easing.CUBIC_IN_OUT);
    private boolean closing;
    private float openTime;

    // sidebar pill
    private final AnimatedFloat pillY = new AnimatedFloat(0f, 16f);
    private boolean pillInit;

    // search
    private String query = "";
    private boolean searchFocus;
    private final AnimatedFloat searchAnim = new AnimatedFloat(0f, 14f);
    private float caretTime;

    // scroll
    private float scroll, scrollVel, maxScroll;
    private final AnimatedFloat scrollbarAlpha = new AnimatedFloat(0f, 6f);
    private float lastScrollInput;

    // focus (Tab navigation)
    private int focusIdx = -1;

    // per-card animation
    private static final class CardAnim {
        final AnimatedFloat hover = new AnimatedFloat(0f, 16f);
        final AnimatedFloat press = new AnimatedFloat(0f, 30f);
        final AnimatedFloat star = AnimatedFloat.spring(0f, 300f, 18f);
    }
    private final Map<Module, CardAnim> cards = new IdentityHashMap<>();

    // drag state
    private Region drag;
    private int dragX, dragY, dragW, dragH;

    // configs tab
    private String cfgSelected = "";
    private String cfgField = "";
    private boolean cfgFieldFocus;
    private String cfgMsg = "";

    // layout (recomputed each frame)
    private int wx, wy, ww, wh;
    private static final int SIDE = 128, PAD = 12, FOOT = 24;
    private float curScale = 1f, curAlpha = 1f;

    private final List<Module> visible = new ArrayList<>();

    public ClickGuiScreen(Screen parent) {
        super("RClient");
        this.parent = parent;
    }

    @Override protected void init() {
        open.forward();
        closing = false;
        openTime = 0f;
    }

    @Override public void onClose() {
        if (closing) return;
        closing = true;
        open.backward();
        ConfigManager.autosave();
    }

    private void finishClose() {
        lastTab = tab;
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ helpers

    private int c(int argb) { return Colors.mulAlpha(argb, curAlpha); }
    private CardAnim anim(Module m) { return cards.computeIfAbsent(m, k -> new CardAnim()); }

    private List<Module> moduleList() {
        visible.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Module m : ModuleManager.all()) {
            if (!q.isEmpty()) {
                if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) visible.add(m);
                continue;
            }
            switch (tab) {
                case FAVORITES -> { if (m.favorite) visible.add(m); }
                case VISUALS -> { if (m.category == Category.VISUALS) visible.add(m); }
                case COSMETICS -> { if (m.category == Category.COSMETICS) visible.add(m); }
                case HUD -> { if (m.category == Category.HUD) visible.add(m); }
                case MISC -> { if (m.category == Category.MISC) visible.add(m); }
                default -> { }
            }
        }
        return visible;
    }

    // ------------------------------------------------------------------ frame

    @Override protected void frame(GuiGraphics ctx, int mx, int my, float partial) {
        float dt = Animator.dt();
        open.update(dt);
        openTime += dt;
        if (closing && open.done()) { finishClose(); return; }
        caretTime += dt;

        float e = open.eased();
        curScale = 0.94f + 0.06f * Math.max(0f, Math.min(1.2f, e));
        curAlpha = Math.max(0f, Math.min(1f, open.raw() * (closing ? 1f : 1.4f)));

        // background: vanilla blur (if enabled) + animated dim
        if (ClientSettings.BG_BLUR.on() && !ClientSettings.FPS_FRIENDLY.on()) vanillaBackground(ctx, mx, my, partial);
        RenderUtil.rectGradient(ctx, 0, 0, width, height, Colors.argb((int) (150 * curAlpha), 4, 4, 6), Colors.argb((int) (190 * curAlpha), 0, 0, 0));

        ww = Math.min(560, width - 28);
        wh = Math.min(360, height - 28);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;

        // mouse in window space (undo the open scale)
        float cx = width / 2f, cy = height / 2f;
        int lmx = (int) (cx + (mx - cx) / curScale), lmy = (int) (cy + (my - cy) / curScale);

        reg.clear();
        RenderUtil.pushScale(ctx, cx, cy, curScale);

        // window
        RenderUtil.shadow(ctx, wx, wy, ww, wh, 14, 22, Colors.argb((int) (150 * curAlpha), 0, 0, 0), 0, 6);
        RenderUtil.roundRect(ctx, wx, wy, ww, wh, 14, c(Colors.BG_TOP), c(Colors.BG_BOTTOM));
        RenderUtil.border(ctx, wx, wy, ww, wh, 14, 1, Colors.argb((int) (40 * curAlpha), 255, 255, 255), Colors.argb((int) (10 * curAlpha), 255, 255, 255));
        // accent glow along the top edge
        RenderUtil.roundRect(ctx, wx + 40, wy, ww - 80, 1, 0, Theme.accent((int) (60 * curAlpha)));

        drawSidebar(ctx, lmx, lmy);
        drawContent(ctx, lmx, lmy);
        drawFooter(ctx, lmx, lmy);

        RenderUtil.pop(ctx);
    }

    // ------------------------------------------------------------------ sidebar

    private int tabY(Tab t) {
        int y = wy + 56;
        for (Tab x : Tab.values()) {
            if (x == Tab.EDITOR) y += 22;      // space for the OTHERS header
            if (x == t) return y;
            y += 22;
        }
        return y;
    }

    private void drawSidebar(GuiGraphics ctx, int mx, int my) {
        RenderUtil.rect(ctx, wx + SIDE, wy + 14, 1, wh - 28, c(Colors.STROKE));

        // logo + name
        RClientLogo.draw(ctx, wx + 16, wy + 14, 22, c(0xFFFFFFFF));
        RFont.bold(11f).draw(ctx, "RClient", wx + 44, wy + 15, c(Colors.TEXT));
        RFont.regular(7f).draw(ctx, "v1.0.0", wx + 44, wy + 28, c(Colors.TEXT_FAINT));

        RFont head = RFont.semi(6.8f);
        head.draw(ctx, "FEATURES", wx + 16, wy + 46 - 2, c(Colors.TEXT_FAINT));
        head.draw(ctx, "OTHERS", wx + 16, tabY(Tab.EDITOR) - 18, c(Colors.TEXT_FAINT));

        // sliding pill
        int target = tabY(tab);
        if (!pillInit) { pillY.snapTo(target); pillInit = true; }
        pillY.set(target);
        int py = (int) pillY.get();
        RenderUtil.roundRect(ctx, wx + 8, py, SIDE - 16, 20, 8, Theme.tint(0.16f * curAlpha));
        RenderUtil.roundRect(ctx, wx + 8, py + 4, 2, 12, 1, c(Theme.accent()));

        RFont f = RFont.semi(8.6f);
        for (Tab t : Tab.values()) {
            int y = tabY(t);
            boolean hov = mx >= wx + 8 && mx < wx + SIDE - 8 && my >= y && my < y + 20;
            boolean act = t == tab;
            int col = act ? Colors.WHITE : hov ? Colors.TEXT : Colors.TEXT_DIM;
            f.draw(ctx, t.label, wx + 30, y + 5.5f, c(col));
            dot(ctx, wx + 19, y + 10, t, act ? Theme.accent() : col);
            reg.add(Kind.TAB, wx + 8, y, SIDE - 16, 20, t, 0, false);
        }

        // profile row
        int py2 = wy + wh - 44;
        RenderUtil.roundRect(ctx, wx + 8, py2, SIDE - 16, 34, 10, c(0xFF17171B));
        RenderUtil.circle(ctx, wx + 26, py2 + 17, 10, c(Theme.accent2()));
        String name = Minecraft.getInstance().getUser().getName();
        RFont.bold(9f).drawCentered(ctx, name.substring(0, 1).toUpperCase(), wx + 26, py2 + 11, c(Colors.WHITE));
        RenderUtil.circle(ctx, wx + 33, py2 + 25, 3, c(0xFF17171B));
        RenderUtil.circle(ctx, wx + 33, py2 + 25, 2, c(0xFF3DDC84));
        RFont.semi(8f).draw(ctx, RFont.semi(8f).truncate(name, 64), wx + 42, py2 + 8, c(Colors.TEXT));
        RFont.regular(6.8f).draw(ctx, "Online", wx + 42, py2 + 19, c(Colors.TEXT_FAINT));
    }

    /** Tiny tab icons drawn from primitives (no icon font needed). */
    private void dot(GuiGraphics ctx, int x, int y, Tab t, int col) {
        int cc = c(col);
        switch (t) {
            case FAVORITES -> RenderUtil.star(ctx, x, y, 5f, cc, 0f);
            case VISUALS -> { RenderUtil.roundRect(ctx, x - 5, y - 3, 10, 6, 3, cc); RenderUtil.circle(ctx, x, y, 2, c(0xFF111114)); }
            case COSMETICS -> { RenderUtil.roundRect(ctx, x - 4, y - 4, 8, 8, 2, cc); RenderUtil.roundRect(ctx, x - 1, y - 1, 2, 2, 0, c(0xFF111114)); }
            case HUD -> { RenderUtil.roundRect(ctx, x - 5, y - 4, 10, 8, 2, cc); RenderUtil.rect(ctx, x - 3, y - 2, 6, 1, c(0xFF111114)); }
            case MISC -> { RenderUtil.circle(ctx, x - 4, y, 1, cc); RenderUtil.circle(ctx, x, y, 1, cc); RenderUtil.circle(ctx, x + 4, y, 1, cc); }
            case EDITOR -> { RenderUtil.roundRect(ctx, x - 5, y - 5, 5, 5, 1, cc); RenderUtil.roundRect(ctx, x, y, 5, 5, 1, cc); }
            case CONFIGS -> { RenderUtil.roundRect(ctx, x - 4, y - 5, 8, 10, 2, cc); RenderUtil.rect(ctx, x - 2, y - 2, 4, 1, c(0xFF111114)); RenderUtil.rect(ctx, x - 2, y + 1, 4, 1, c(0xFF111114)); }
            case SETTINGS -> { RenderUtil.circle(ctx, x, y, 5, cc); RenderUtil.circle(ctx, x, y, 2, c(0xFF111114)); }
        }
    }

    // ------------------------------------------------------------------ content

    private int cx0, cy0, cw, ch;   // scrolling viewport

    private void drawContent(GuiGraphics ctx, int mx, int my) {
        int ax = wx + SIDE + PAD, aw = ww - SIDE - PAD * 2;

        // search bar (expands + glows on focus)
        searchAnim.set(searchFocus ? 1f : 0f);
        float sf = searchAnim.get();
        int sx = ax - (int) (3 * sf), sw = aw + (int) (6 * sf), sy = wy + 14, sh = 26;
        if (sf > 0.01f) RenderUtil.shadow(ctx, sx, sy, sw, sh, 10, 10, Theme.accent((int) (70 * sf * curAlpha)), 0, 0);
        RenderUtil.roundRect(ctx, sx, sy, sw, sh, 10, c(Colors.lerp(0xFF16161A, 0xFF1C1C21, sf)));
        RenderUtil.border(ctx, sx, sy, sw, sh, 10, 1, Colors.lerp(Colors.argb((int) (28 * curAlpha), 255, 255, 255), Theme.accent((int) (200 * curAlpha)), sf),
            Colors.lerp(Colors.argb((int) (10 * curAlpha), 255, 255, 255), Theme.accent((int) (70 * curAlpha)), sf));
        // magnifier
        RenderUtil.circle(ctx, sx + 14, sy + 12, 4, c(Colors.TEXT_FAINT));
        RenderUtil.circle(ctx, sx + 14, sy + 12, 2, c(0xFF16161A));
        RenderUtil.rect(ctx, sx + 17, sy + 16, 3, 1, c(Colors.TEXT_FAINT));
        RFont sf2 = RFont.regular(8.6f);
        if (query.isEmpty() && !searchFocus) sf2.draw(ctx, "Search modules…", sx + 26, sy + 8.5f, c(Colors.TEXT_FAINT));
        else {
            sf2.draw(ctx, query, sx + 26, sy + 8.5f, c(Colors.TEXT));
            if (searchFocus && ((int) (caretTime * 2.2f) & 1) == 0)
                RenderUtil.rect(ctx, sx + 26 + (int) sf2.width(query) + 1, sy + 7, 1, 12, c(Theme.accent()));
        }
        reg.add(Kind.SEARCH, sx, sy, sw, sh, null, 0, false);

        cx0 = ax; cy0 = wy + 50; cw = aw; ch = wh - 50 - FOOT - 6;
        reg.clipX = cx0 - 6; reg.clipY = cy0; reg.clipW = cw + 12; reg.clipH = ch;

        switch (tab) {
            case CONFIGS -> drawConfigs(ctx, mx, my);
            case SETTINGS -> drawGlobalSettings(ctx, mx, my);
            default -> drawModules(ctx, mx, my);
        }
    }

    // ---- scroll physics (inertia + rubber band) ----
    private void stepScroll(float contentH) {
        float dt = Animator.dt();
        maxScroll = Math.max(0f, contentH - ch);
        boolean smooth = ClientSettings.SMOOTH_SCROLL.on();
        scroll += scrollVel * dt;
        float over = scroll < 0 ? scroll : scroll > maxScroll ? scroll - maxScroll : 0f;
        if (over != 0f && openTime - lastScrollInput > 0.05f) {
            // spring back toward the bounds
            scrollVel += -over * 90f * dt * 6f;
            scrollVel *= (float) Math.exp(-9f * dt);
            if (Math.abs(over) < 0.2f && Math.abs(scrollVel) < 2f) { scroll = scroll < 0 ? 0 : maxScroll; scrollVel = 0; }
        } else {
            scrollVel *= (float) Math.exp(-(smooth ? 7.5f : 30f) * dt);
            if (Math.abs(scrollVel) < 0.5f) scrollVel = 0;
        }
        boolean active = Math.abs(scrollVel) > 1f || over != 0f || openTime - lastScrollInput < 0.9f;
        scrollbarAlpha.set(active && maxScroll > 0 ? 1f : 0f);
    }

    private void drawScrollbar(GuiGraphics ctx, float contentH) {
        float a = scrollbarAlpha.get();
        if (a < 0.02f || maxScroll <= 0) return;
        int trackH = ch - 8;
        float frac = ch / contentH;
        int th = Math.max(20, (int) (trackH * frac));
        float pos = Math.max(0f, Math.min(1f, scroll / maxScroll));
        int ty = cy0 + 4 + (int) ((trackH - th) * pos);
        RenderUtil.roundRect(ctx, cx0 + cw + 3, ty, 3, th, 1, Colors.argb((int) (90 * a * curAlpha), 255, 255, 255));
    }

    // ---- module grid ----
    private void drawModules(GuiGraphics ctx, int mx, int my) {
        List<Module> list = moduleList();
        RFont title = RFont.semi(7f);
        if (list.isEmpty()) {
            RFont f = RFont.regular(8.6f);
            String msg = !query.isEmpty() ? "No modules match “" + query + "”"
                : tab == Tab.FAVORITES ? "Star a module to pin it here" : "Nothing here yet";
            f.drawCentered(ctx, msg, cx0 + cw / 2f, cy0 + ch / 2f - 6, c(Colors.TEXT_FAINT));
            return;
        }

        int gap = 8, colW = (cw - gap) / 2, cardH = 42;

        // layout pass: rows of two; an expanded card spans the full row
        int y = 0, i = 0;
        float contentH;
        // two-pass: first compute total height for scroll bounds
        int total = 0;
        for (int k = 0; k < list.size(); ) {
            Module m = list.get(k);
            m.expandAnim.set(m.expanded ? 1f : 0f);
            float ex = m.expandAnim.get();
            if (ex > 0.01f) { total += cardH + Math.round(ex * (settingsHeight(m) + 10)) + gap; k++; }
            else { total += cardH + gap; k += (k + 1 < list.size() && list.get(k + 1).expandAnim.get() <= 0.01f && !list.get(k + 1).expanded) ? 2 : 1; }
        }
        contentH = total + 6;
        stepScroll(contentH);

        float scr = scroll;
        RenderUtil.scissor(ctx, cx0 - 6, cy0, cw + 12, ch);
        int idx = 0, rowY = (int) (cy0 - scr);
        for (int k = 0; k < list.size(); ) {
            Module m = list.get(k);
            float ex = m.expandAnim.get();
            if (ex > 0.01f || m.expanded) {
                int h = cardH + Math.round(ex * (settingsHeight(m) + 10));
                drawCard(ctx, m, cx0, rowY, cw, h, idx++, mx, my, true);
                rowY += h + gap; k++;
            } else {
                drawCard(ctx, m, cx0, rowY, colW, cardH, idx++, mx, my, false);
                if (k + 1 < list.size() && !list.get(k + 1).expanded && list.get(k + 1).expandAnim.get() <= 0.01f) {
                    drawCard(ctx, list.get(k + 1), cx0 + colW + gap, rowY, colW, cardH, idx++, mx, my, false);
                    k += 2;
                } else k++;
                rowY += cardH + gap;
            }
        }
        RenderUtil.endScissor(ctx);
        drawScrollbar(ctx, contentH);
        if (focusIdx >= list.size()) focusIdx = list.size() - 1;
    }

    private int settingsHeight(Module m) {
        int h = 0;
        for (Setting<?> s : m.settings()) if (s.isVisible()) h += SettingWidgets.height(s) + 2;
        return h;
    }

    private void drawCard(GuiGraphics ctx, Module m, int x, int y, int w, int h, int idx, int mx, int my, boolean wide) {
        if (y + h < cy0 - 20 || y > cy0 + ch + 20) { // off-screen: skip draw but keep animation state sane
            return;
        }
        CardAnim a = anim(m);
        // stagger-in
        float t = Math.max(0f, Math.min(1f, (openTime - idx * 0.035f) / 0.28f));
        float appear = Easing.CUBIC_OUT.apply(t);
        float alpha = curAlpha * appear;
        int slide = (int) ((1f - appear) * 10);

        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + 42 && my >= cy0 && my < cy0 + ch;
        a.hover.set(hovered ? 1f : 0f);
        boolean pressing = hovered && drag == null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), 0) == GLFW.GLFW_PRESS;
        a.press.set(pressing ? 1f : 0f);
        a.star.set(m.favorite ? 1f : 0f);

        float hv = a.hover.get(), pr = a.press.get();
        float lift = -1.5f * hv;
        float sc = 1f - 0.02f * pr;

        float tg = Math.max(0f, Math.min(1f, m.toggleAnim.get()));
        int base = Colors.lerp(Colors.CARD, Colors.CARD_HOVER, hv);
        base = Colors.lerp(base, Colors.lerp(0xFF1A1A1E, Theme.accent(), 0.14f), tg);

        int cy = y + slide + (int) lift;
        RenderUtil.pushScale(ctx, x + w / 2f, cy + 21, sc);
        if (hv > 0.02f || tg > 0.02f)
            RenderUtil.shadow(ctx, x, cy, w, h, 11, 10, Colors.argb((int) ((40 * hv + 28 * tg) * alpha), (Colors.red(Theme.accent()) * (int) (tg * 100)) / 100, (Colors.green(Theme.accent()) * (int) (tg * 100)) / 100, (Colors.blue(Theme.accent()) * (int) (tg * 100)) / 100), 0, 3);
        RenderUtil.roundRect(ctx, x, cy, w, h, 11, Colors.mulAlpha(base, alpha));
        int bt = Colors.lerp(Colors.argb(26, 255, 255, 255), Theme.accent(90), tg);
        RenderUtil.border(ctx, x, cy, w, h, 11, 1, Colors.mulAlpha(bt, alpha), Colors.mulAlpha(Colors.lerp(Colors.argb(8, 255, 255, 255), Theme.accent(30), tg), alpha));
        if (!searchFocus && focusIdx >= 0 && focusIdx < visible.size() && visible.get(focusIdx) == m)
            RenderUtil.border(ctx, x - 1, cy - 1, w + 2, h + 2, 12, 1, Colors.mulAlpha(Theme.accent(), alpha), Colors.mulAlpha(Theme.accent(), alpha));

        RFont nf = RFont.semi(9f), df = RFont.regular(7.4f);
        int textMax = w - 14 - 26 - 30 - 20;
        nf.draw(ctx, m.name, x + 12, cy + 8, Colors.mulAlpha(Colors.lerp(Colors.TEXT, Colors.WHITE, tg), alpha));
        df.draw(ctx, df.truncate(m.description, textMax + 24), x + 12, cy + 22, Colors.mulAlpha(Colors.TEXT_DIM, alpha));

        // toggle pill
        int px = x + w - 12 - 28, py = cy + 14;
        SettingWidgets.pill(ctx, px, py, 28, 14, m.toggleAnim.get(), alpha);
        // star
        int sx = px - 18, sy = cy + 21;
        float sp = a.star.get();
        int starCol = Colors.lerp(Colors.TEXT_FAINT, 0xFFFFC857, Math.max(0f, Math.min(1f, sp)));
        RenderUtil.star(ctx, sx, sy, 5.2f + 1.5f * Math.max(0f, sp - 1f), Colors.mulAlpha(starCol, alpha), 0f);
        // dots (settings)
        int dx = sx - 17;
        int dcol = Colors.mulAlpha(m.expanded ? Theme.accent() : Colors.TEXT_FAINT, alpha);
        RenderUtil.circle(ctx, dx - 4, sy, 1, dcol); RenderUtil.circle(ctx, dx, sy, 1, dcol); RenderUtil.circle(ctx, dx + 4, sy, 1, dcol);

        int rcy = cy;
        reg.add(Kind.CARD, x, rcy, w, 42, m, 0, true);
        reg.add(Kind.DOTS, dx - 8, rcy, 16, 42, m, 0, true);
        reg.add(Kind.STAR, sx - 9, rcy, 18, 42, m, 0, true);
        reg.add(Kind.TOGGLE, px - 2, rcy, 34, 42, m, 0, true);

        float ex = m.expandAnim.get();
        if (ex > 0.01f) {
            int sy0 = cy + 42 + 2, sw = w - 24;
            int full = h - 42;
            RenderUtil.scissor(ctx, x, cy + 42, w, full);
            RenderUtil.rect(ctx, x + 12, cy + 42, w - 24, 1, Colors.mulAlpha(Colors.STROKE, alpha * ex));
            int yy = sy0 + 6;
            for (Setting<?> s : m.settings()) {
                if (!s.isVisible()) continue;
                int rh = SettingWidgets.draw(ctx, reg, s, x + 12, yy, sw, alpha * ex, true, mx, my);
                yy += rh + 2;
            }
            RenderUtil.endScissor(ctx);
        }
        RenderUtil.pop(ctx);
    }

    // ---- global settings tab ----
    private void drawGlobalSettings(GuiGraphics ctx, int mx, int my) {
        List<Setting<?>> list = ClientSettings.ALL;
        int total = 12 + SettingWidgets.totalHeight(list) + list.size() * 6;
        stepScroll(total + 8);
        RenderUtil.scissor(ctx, cx0 - 6, cy0, cw + 12, ch);
        int y = (int) (cy0 - scroll);
        RenderUtil.roundRect(ctx, cx0, y, cw, total, 11, c(Colors.CARD));
        RenderUtil.border(ctx, cx0, y, cw, total, 11, c(Colors.STROKE));
        RFont.semi(7f).draw(ctx, "CLIENT SETTINGS", cx0 + 12, y + 8, c(Colors.TEXT_FAINT));
        int yy = y + 22;
        for (Setting<?> s : list) {
            if (!s.isVisible()) continue;
            yy += SettingWidgets.draw(ctx, reg, s, cx0 + 14, yy, cw - 28, curAlpha, true, mx, my) + 6;
        }
        RenderUtil.endScissor(ctx);
        drawScrollbar(ctx, total + 8);
    }

    // ---- configs tab ----
    private void drawConfigs(GuiGraphics ctx, int mx, int my) {
        List<String> names = ConfigManager.list();
        if (cfgSelected.isEmpty() && !names.isEmpty()) cfgSelected = ConfigManager.active();
        int rowH = 30;
        int total = 60 + names.size() * (rowH + 6) + 24;
        stepScroll(total);

        RenderUtil.scissor(ctx, cx0 - 6, cy0, cw + 12, ch);
        int y = (int) (cy0 - scroll);
        RFont h = RFont.semi(7f);
        h.draw(ctx, "CONFIG PROFILES  ·  " + ConfigManager.dir().toString().replace('\\', '/').replaceAll(".*/(\\.minecraft/)?", "…/"), cx0 + 2, y + 2, c(Colors.TEXT_FAINT));

        // name field + save
        int fy = y + 16;
        int fw = cw - 82;
        RenderUtil.roundRect(ctx, cx0, fy, fw, 24, 9, c(0xFF16161A));
        RenderUtil.border(ctx, cx0, fy, fw, 24, 9, 1,
            cfgFieldFocus ? Theme.accent((int) (200 * curAlpha)) : Colors.argb((int) (28 * curAlpha), 255, 255, 255),
            cfgFieldFocus ? Theme.accent((int) (70 * curAlpha)) : Colors.argb((int) (10 * curAlpha), 255, 255, 255));
        RFont f = RFont.regular(8.4f);
        if (cfgField.isEmpty() && !cfgFieldFocus) f.draw(ctx, "New profile name…", cx0 + 10, fy + 7.5f, c(Colors.TEXT_FAINT));
        else {
            f.draw(ctx, cfgField, cx0 + 10, fy + 7.5f, c(Colors.TEXT));
            if (cfgFieldFocus && ((int) (caretTime * 2.2f) & 1) == 0) RenderUtil.rect(ctx, cx0 + 11 + (int) f.width(cfgField), fy + 6, 1, 12, c(Theme.accent()));
        }
        reg.add(Kind.CFG_FIELD, cx0, fy, fw, 24, null, 0, true);
        button(ctx, cx0 + fw + 6, fy, 76, 24, "Save as", true, mx, my, Kind.CFG_SAVE, null);

        int ry = fy + 34;
        for (String n : names) {
            boolean sel = n.equals(cfgSelected), act = n.equals(ConfigManager.active());
            boolean hov = mx >= cx0 && mx < cx0 + cw && my >= ry && my < ry + rowH && my >= cy0 && my < cy0 + ch;
            RenderUtil.roundRect(ctx, cx0, ry, cw, rowH, 10, c(sel ? Colors.lerp(0xFF1A1A1E, Theme.accent(), 0.14f) : hov ? Colors.CARD_HOVER : Colors.CARD));
            RenderUtil.border(ctx, cx0, ry, cw, rowH, 10, 1, sel ? Theme.accent((int) (90 * curAlpha)) : Colors.argb((int) (22 * curAlpha), 255, 255, 255), Colors.argb((int) (8 * curAlpha), 255, 255, 255));
            RFont.semi(8.8f).draw(ctx, n, cx0 + 12, ry + 6, c(Colors.TEXT));
            RFont.regular(7f).draw(ctx, act ? "Active profile" : "Saved", cx0 + 12, ry + 18, c(act ? Theme.accent() : Colors.TEXT_FAINT));
            reg.add(Kind.CFG_ROW, cx0, ry, cw, rowH, n, 0, true);
            int bx = cx0 + cw - 8;
            bx -= 52; button(ctx, bx, ry + 5, 50, 20, "Delete", false, mx, my, Kind.CFG_DELETE, n);
            bx -= 56; button(ctx, bx, ry + 5, 54, 20, "Rename", false, mx, my, Kind.CFG_RENAME, n);
            bx -= 46; button(ctx, bx, ry + 5, 44, 20, "Load", false, mx, my, Kind.CFG_LOAD, n);
            ry += rowH + 6;
        }
        if (!cfgMsg.isEmpty()) RFont.regular(7.4f).draw(ctx, cfgMsg, cx0 + 2, ry + 4, c(Colors.TEXT_DIM));
        RenderUtil.endScissor(ctx);
        drawScrollbar(ctx, total);
    }

    private void button(GuiGraphics ctx, int x, int y, int w, int h, String label, boolean primary, int mx, int my, Kind kind, Object obj) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h && my >= cy0 && my < cy0 + ch;
        int bg = primary ? Colors.lerp(Theme.accent(), Colors.WHITE, hov ? 0.12f : 0f) : (hov ? 0xFF2C2C33 : 0xFF222228);
        RenderUtil.roundRect(ctx, x, y, w, h, 8, c(bg));
        RFont.semi(7.8f).drawCentered(ctx, label, x + w / 2f, y + (h - 9) / 2f, c(primary ? Colors.WHITE : Colors.TEXT));
        reg.add(kind, x, y, w, h, obj, 0, true);
    }

    // ------------------------------------------------------------------ footer

    private void drawFooter(GuiGraphics ctx, int mx, int my) {
        int fy = wy + wh - FOOT;
        int fx = wx + SIDE + PAD, fw = ww - SIDE - PAD * 2;
        RenderUtil.rect(ctx, fx, fy - 3, fw, 1, c(Colors.STROKE));
        RFont f = RFont.regular(6.8f);
        String hints = "Right-click reset  ·  Enter settings  ·  Space toggle  ·  Tab navigate";
        f.draw(ctx, hints, fx, fy + 5, c(Colors.TEXT_FAINT));
        // global toggles
        int x = fx + fw;
        x = footToggle(ctx, x, fy + 2, "Notifs", ClientSettings.SHOW_NOTIFS.on(), Kind.FOOT_NOTIF);
        x = footToggle(ctx, x - 8, fy + 2, "Blur", ClientSettings.BG_BLUR.on(), Kind.FOOT_BLUR);
        String prof = "Profile: " + ConfigManager.active();
        f.drawRight(ctx, prof, x - 10, fy + 5, c(Colors.TEXT_DIM));
    }

    private int footToggle(GuiGraphics ctx, int right, int y, String label, boolean on, Kind k) {
        RFont f = RFont.regular(6.8f);
        int pw = 20, lw = (int) f.width(label);
        int x = right - pw;
        SettingWidgets.pill(ctx, x, y + 1, pw, 10, on ? 1f : 0f, curAlpha);
        f.draw(ctx, label, x - lw - 5, y + 2.5f, c(Colors.TEXT_DIM));
        reg.add(k, x - lw - 6, y - 2, lw + pw + 8, 16, null, 0, false);
        return x - lw - 6;
    }

    // ------------------------------------------------------------------ input

    private int ux(double mx) { return (int) (width / 2f + (mx - width / 2f) / curScale); }
    private int uy(double my) { return (int) (height / 2f + (my - height / 2f) / curScale); }

    @Override protected boolean onMouseDown(double rmx, double rmy, int button) {
        if (closing) return true;
        int mx = ux(rmx), my = uy(rmy);
        Region r = reg.hit(mx, my);
        boolean wasSearch = searchFocus, wasField = cfgFieldFocus;
        searchFocus = false; cfgFieldFocus = false;
        SettingWidgets.listening = (r != null && r.kind == Kind.S_KEY) ? SettingWidgets.listening : null;

        if (r == null) {
            if (mx < wx || mx > wx + ww || my < wy || my > wy + wh) onClose();
            return true;
        }
        switch (r.kind) {
            case TAB -> {
                Tab t = (Tab) r.obj;
                if (t == Tab.EDITOR) { lastTab = tab; if (minecraft != null) minecraft.setScreen(new HudEditorScreen(this)); }
                else { tab = t; scroll = 0; scrollVel = 0; focusIdx = -1; openTime = Math.max(openTime, 0.35f); restagger(); }
            }
            case SEARCH -> searchFocus = true;
            case CFG_FIELD -> cfgFieldFocus = true;
            case CARD -> {
                Module m = (Module) r.obj;
                if (button == 0) m.toggle(); else m.expanded = !m.expanded;
            }
            case TOGGLE -> { if (button == 0) ((Module) r.obj).toggle(); else ((Module) r.obj).expanded = !((Module) r.obj).expanded; }
            case STAR -> { Module m = (Module) r.obj; m.favorite = !m.favorite; anim(m).star.set(m.favorite ? 1.6f : 0f); anim(m).star.set(m.favorite ? 1f : 0f); }
            case DOTS -> { Module m = (Module) r.obj; m.expanded = !m.expanded; }
            case S_BOOL -> {
                BoolSetting b = (BoolSetting) r.obj;
                if (button == 1) b.reset(); else b.toggle();
                afterGlobalChange();
            }
            case S_NUM -> {
                NumberSetting n = (NumberSetting) r.obj;
                if (button == 1) n.reset(); else { drag = r; dragX = r.x; dragW = r.w; SettingWidgets.slide(n, mx, r.x, r.w); }
                afterGlobalChange();
            }
            case S_MODE -> {
                ModeSetting m = (ModeSetting) r.obj;
                if (button == 1) { m.reset(); afterGlobalChange(); } else SettingWidgets.toggleOpen(m);
            }
            case S_MODE_OPT -> {
                ModeSetting m = (ModeSetting) r.obj;
                m.set(m.options[r.extra]);
                SettingWidgets.openAnim(m).set(0f);
                afterGlobalChange();
            }
            case S_KEY -> {
                KeybindSetting k = (KeybindSetting) r.obj;
                if (button == 1) { k.reset(); SettingWidgets.listening = null; } else SettingWidgets.listening = k;
            }
            case S_COLOR_HEAD -> {
                ColorSetting c = (ColorSetting) r.obj;
                if (button == 1) { c.reset(); SettingWidgets.invalidate(c); } else SettingWidgets.toggleOpen(c);
            }
            case S_COLOR_SV -> { drag = r; dragX = r.x; dragY = r.y; dragW = r.w; dragH = r.h; SettingWidgets.pickSv((ColorSetting) r.obj, mx, my, r.x, r.y, r.w, r.h); }
            case S_COLOR_HUE -> { drag = r; dragY = r.y; dragH = r.h; SettingWidgets.pickHue((ColorSetting) r.obj, my, r.y, r.h); }
            case S_COLOR_STOP -> SettingWidgets.hsb((ColorSetting) r.obj)[6] = r.extra;
            case S_COLOR_GRAD -> { ColorSetting c = (ColorSetting) r.obj; c.gradient = !c.gradient; }
            case S_COLOR_RAINBOW -> { ColorSetting c = (ColorSetting) r.obj; c.rainbow = !c.rainbow; }
            case CFG_ROW -> { cfgSelected = (String) r.obj; cfgField = cfgField.isEmpty() ? "" : cfgField; }
            case CFG_SAVE -> {
                String n = cfgField.trim().isEmpty() ? ConfigManager.active() : cfgField.trim();
                cfgMsg = ConfigManager.save(n) ? "Saved “" + n + "”" : "Could not save that name";
                cfgSelected = n; cfgField = "";
            }
            case CFG_LOAD -> { String n = (String) r.obj; cfgMsg = ConfigManager.load(n) ? "Loaded “" + n + "”" : "Could not load “" + n + "”"; cfgSelected = n; }
            case CFG_RENAME -> {
                String n = (String) r.obj;
                if (cfgField.trim().isEmpty()) cfgMsg = "Type the new name in the box first";
                else { cfgMsg = ConfigManager.rename(n, cfgField.trim()) ? "Renamed to “" + cfgField.trim() + "”" : "Rename failed (name taken?)"; cfgField = ""; }
            }
            case CFG_DELETE -> { String n = (String) r.obj; cfgMsg = ConfigManager.delete(n) ? "Deleted “" + n + "”" : "Could not delete"; }
            case FOOT_BLUR -> ClientSettings.BG_BLUR.toggle();
            case FOOT_NOTIF -> ClientSettings.SHOW_NOTIFS.toggle();
            default -> { }
        }
        return true;
    }

    private void restagger() { openTime = 0.0f; }

    private void afterGlobalChange() { if (tab == Tab.SETTINGS) ClientSettings.applyAll(); }

    @Override protected boolean onMouseDrag(double rmx, double rmy, int button, double dx, double dy) {
        if (drag == null) return false;
        int mx = ux(rmx), my = uy(rmy);
        switch (drag.kind) {
            case S_NUM -> { SettingWidgets.slide((NumberSetting) drag.obj, mx, dragX, dragW); afterGlobalChange(); }
            case S_COLOR_SV -> SettingWidgets.pickSv((ColorSetting) drag.obj, mx, my, dragX, dragY, dragW, dragH);
            case S_COLOR_HUE -> SettingWidgets.pickHue((ColorSetting) drag.obj, my, dragY, dragH);
            default -> { }
        }
        return true;
    }

    @Override protected boolean onMouseUp(double x, double y, int button) { boolean d = drag != null; drag = null; return d; }

    @Override protected boolean onScroll(double x, double y, double amount) {
        scrollVel -= (float) amount * 260f;
        // resist when overscrolled (rubber band)
        if (scroll < 0 || scroll > maxScroll) scrollVel *= 0.35f;
        lastScrollInput = openTime;
        return true;
    }

    @Override protected boolean onKey(int key, int scancode, int mods) {
        if (SettingWidgets.listening != null) {
            KeybindSetting k = SettingWidgets.listening;
            if (key == GLFW.GLFW_KEY_ESCAPE) { /* cancel */ }
            else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) k.set(-1);
            else k.set(key);
            SettingWidgets.listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocus || cfgFieldFocus) { searchFocus = false; cfgFieldFocus = false; return true; }
            if (!query.isEmpty()) { query = ""; return true; }
            onClose();
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) { query = query.substring(0, query.length() - 1); scroll = 0; }
            else if (key == GLFW.GLFW_KEY_ENTER) searchFocus = false;
            return true;
        }
        if (cfgFieldFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !cfgField.isEmpty()) cfgField = cfgField.substring(0, cfgField.length() - 1);
            else if (key == GLFW.GLFW_KEY_ENTER) { cfgMsg = ConfigManager.save(cfgField.trim().isEmpty() ? ConfigManager.active() : cfgField.trim()) ? "Saved" : "Could not save"; cfgField = ""; cfgFieldFocus = false; }
            return true;
        }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) { searchFocus = true; return true; }
        if (key == GLFW.GLFW_KEY_SLASH) { searchFocus = true; return true; }

        List<Module> list = visible;
        if (key == GLFW.GLFW_KEY_TAB) {
            if (list.isEmpty()) return true;
            int dir = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            focusIdx = (focusIdx + dir + list.size()) % list.size();
            return true;
        }
        if (focusIdx >= 0 && focusIdx < list.size()) {
            Module m = list.get(focusIdx);
            if (key == GLFW.GLFW_KEY_SPACE) { m.toggle(); return true; }
            if (key == GLFW.GLFW_KEY_ENTER) { m.expanded = !m.expanded; return true; }
        }
        return false;
    }

    @Override protected boolean onChar(int cp) {
        if (cp < 32 || cp == 127) return false;
        if (searchFocus) { if (query.length() < 32) { query += new String(Character.toChars(cp)); scroll = 0; } return true; }
        if (cfgFieldFocus) { if (cfgField.length() < 24) cfgField += new String(Character.toChars(cp)); return true; }
        return false;
    }
}
