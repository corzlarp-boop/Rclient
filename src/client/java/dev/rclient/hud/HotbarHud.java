package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.render.Colors;
import dev.rclient.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Restyled hotbar (visual only – selection still comes from the vanilla hotbar state). */
public final class HotbarHud extends HudElement {
    private final AnimatedFloat sel = new AnimatedFloat(0f, 22f);
    private boolean placed;

    public HotbarHud() { super("Custom Hotbar", "Rounded animated hotbar replacing the vanilla one", 0, 0); }

    @Override public int contentWidth() { return 9 * 20 + 12; }
    @Override public int contentHeight() { return 28; }
    @Override protected boolean drawsOwnBackground() { return false; }

    @Override protected void onEnable() { placed = false; }

    /** Called once so the widget starts bottom-centre. */
    public void ensurePlaced() {
        if (placed) return;
        Minecraft mc = Minecraft.getInstance();
        moveTo((mc.getWindow().getGuiScaledWidth() - width()) / 2, mc.getWindow().getGuiScaledHeight() - height() - 6);
        placed = true;
    }

    @Override protected void drawContent(GuiGraphics ctx, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int slot = mc.player.getInventory().getSelectedSlot();
        sel.set(slot);
        float sx = 6 + sel.get() * 20;
        RenderUtil.roundRect(ctx, (int) sx, 4, 20, 20, 6, Colors.withAlpha(accent(), 70));
        RenderUtil.border(ctx, (int) sx, 4, 20, 20, 6, 1, Colors.withAlpha(accent(), 230), Colors.withAlpha(accent(), 90));
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            int ix = 6 + i * 20 + 2, iy = 6;
            ctx.renderItem(st, ix, iy);
            ctx.renderItemDecorations(mc.font, st, ix, iy);
        }
    }
}
