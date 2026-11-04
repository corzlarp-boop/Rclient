package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Supplier;

/** Gradient value bar used for the custom health and hunger styling. Purely cosmetic read-only display. */
public class StatBarHud extends HudElement {
    public enum Kind { HEALTH, HUNGER }

    private final Kind kind;
    private final AnimatedFloat fill = new AnimatedFloat(1f, 10f);
    private final AnimatedFloat ghost = new AnimatedFloat(1f, 3.5f);   // delayed "damage trail"
    private boolean placed;

    public StatBarHud(Kind kind) {
        super(kind == Kind.HEALTH ? "Custom Health" : "Custom Hunger",
              kind == Kind.HEALTH ? "Styled health bar replacing the vanilla hearts" : "Styled hunger bar replacing the vanilla food icons",
              0, 0);
        this.kind = kind;
    }

    @Override public int contentWidth() { return 96; }
    @Override public int contentHeight() { return 16; }
    @Override protected void onEnable() { placed = false; }

    public void ensurePlaced() {
        if (placed) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2;
        moveTo(kind == Kind.HEALTH ? cx - 96 - 4 : cx + 4, sh - 28 - 16 - 8);
        placed = true;
    }

    @Override protected void drawContent(GuiGraphics ctx, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float cur, max;
        if (kind == Kind.HEALTH) { cur = mc.player.getHealth() + mc.player.getAbsorptionAmount(); max = mc.player.getMaxHealth(); }
        else { cur = mc.player.getFoodData().getFoodLevel(); max = 20f; }
        float p = Math.max(0f, Math.min(1f, cur / max));
        fill.set(p);
        ghost.set(p);
        float g = Math.max(ghost.get(), fill.get());

        int c1 = kind == Kind.HEALTH ? 0xFFFF5964 : 0xFFF5A524;
        int c2 = kind == Kind.HEALTH ? 0xFFB3202B : 0xFFB06F0A;
        int bx = 4, by = 3, bw = contentWidth() - 8, bh = 10;
        RenderUtil.roundRect(ctx, bx, by, bw, bh, 5, 0x40000000);
        if (g > fill.get() + 0.002f)
            RenderUtil.roundRect(ctx, bx, by, Math.max(bh, (int) (bw * g)), bh, 5, 0x55FFFFFF);
        RenderUtil.roundRectH(ctx, bx, by, Math.max(bh, (int) (bw * fill.get())), bh, 5, c1, c2);
        RFont f = RFont.semi(7f);
        String s = (kind == Kind.HEALTH ? String.format("%.0f", cur) : String.valueOf((int) cur));
        f.drawCentered(ctx, s, bx + bw / 2f, by + 1.4f, Colors.WHITE);
    }
}
