package dev.rclient.hud;

import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Lists ONLY the RClient (visual) modules that have a keybind assigned. */
public final class KeybindsHud extends HudElement {
    private final BoolSetting onlyEnabled = add(new BoolSetting("Only enabled modules", false));
    private final List<Module> cache = new ArrayList<>();

    public KeybindsHud() { super("Keybinds", "Shows your visual-module keybinds", 10, 220); }

    private List<Module> rows() {
        cache.clear();
        for (Module m : ModuleManager.all()) {
            if (m instanceof HudElement && m != this) continue;
            if (m == this) continue;
            if (!m.keybind.bound()) continue;
            if (onlyEnabled.on() && !m.isEnabled()) continue;
            cache.add(m);
        }
        return cache;
    }

    @Override public int contentWidth() { return 110; }
    @Override public int contentHeight() { return 22 + Math.max(1, rows().size()) * 13; }

    @Override protected void drawContent(GuiGraphics ctx, float pt) {
        RFont title = RFont.semi(8f), row = RFont.regular(7.5f), key = RFont.semi(7f);
        title.draw(ctx, "KEYBINDS", 8, 6, Colors.TEXT_FAINT);
        int y = 19;
        List<Module> list = rows();
        if (list.isEmpty()) row.draw(ctx, "No keybinds set", 8, y, Colors.TEXT_FAINT);
        for (Module m : list) {
            int col = m.isEnabled() ? Colors.TEXT : Colors.TEXT_DIM;
            row.draw(ctx, row.truncate(m.name, 64), 8, y, col);
            String k = m.keybind.label();
            int kw = (int) key.width(k) + 8;
            RenderUtil.roundRect(ctx, 110 - 8 - kw, y - 1, kw, 11, 4,
                m.isEnabled() ? dev.rclient.render.Colors.withAlpha(accent(), 130) : 0x22FFFFFF);
            key.drawCentered(ctx, k, 110 - 8 - kw / 2f, y + 0.5f, Colors.TEXT);
            y += 13;
        }
    }
}
