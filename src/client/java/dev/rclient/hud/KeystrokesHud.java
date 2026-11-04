package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** W/A/S/D, mouse buttons with CPS, optional space bar. MouseButtonEvent counting polls GLFW once per frame. */
public final class KeystrokesHud extends HudElement {
    private static final int K = 22, G = 3;

    private final BoolSetting showMouse = add(new BoolSetting("Mouse buttons + CPS", true));
    private final BoolSetting showSpace = add(new BoolSetting("Space bar", true));

    private final AnimatedFloat[] press = new AnimatedFloat[7];   // W A S D LMB RMB SPACE
    private final long[][] clicks = new long[2][64];
    private final int[] clickHead = new int[2];
    private final boolean[] lastDown = new boolean[2];

    public KeystrokesHud() {
        super("Keystrokes", "Shows WASD, mouse buttons and CPS", 10, 120);
        for (int i = 0; i < press.length; i++) press[i] = new AnimatedFloat(0f, 22f);
    }

    @Override public int contentWidth() { return K * 3 + G * 2 + 12; }
    @Override public int contentHeight() {
        int h = K * 2 + G + 12;
        if (showMouse.on()) h += G + K;
        if (showSpace.on()) h += G + 14;
        return h;
    }

    private int cps(int b) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (long t : clicks[b]) if (t != 0 && now - t < 1000) n++;
        return n;
    }

    private void pollClicks() {
        long window = Minecraft.getInstance().getWindow().handle();
        for (int b = 0; b < 2; b++) {
            boolean down = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
            if (down && !lastDown[b]) {
                clicks[b][clickHead[b]] = System.currentTimeMillis();
                clickHead[b] = (clickHead[b] + 1) & 63;
            }
            lastDown[b] = down;
        }
    }

    @Override protected void drawContent(GuiGraphics ctx, float pt) {
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        pollClicks();
        boolean[] down = {
            o.keyUp.isDown(), o.keyLeft.isDown(), o.keyDown.isDown(), o.keyRight.isDown(),
            lastDown[0], lastDown[1], o.keyJump.isDown()
        };
        for (int i = 0; i < 7; i++) press[i].set(down[i] ? 1f : 0f);

        int ox = 6, oy = 6;
        key(ctx, ox + K + G, oy, K, K, "W", 0);
        key(ctx, ox, oy + K + G, K, K, "A", 1);
        key(ctx, ox + K + G, oy + K + G, K, K, "S", 2);
        key(ctx, ox + 2 * (K + G), oy + K + G, K, K, "D", 3);
        int y = oy + 2 * (K + G);
        if (showMouse.on()) {
            int bw = (K * 3 + G * 2 - G) / 2;
            key(ctx, ox, y, bw, K, "LMB", 4);
            key(ctx, ox + bw + G, y, bw, K, "RMB", 5);
            RFont small = RFont.regular(6.5f);
            small.drawCentered(ctx, cps(0) + " CPS", ox + bw / 2f, y + K - 8.5f, Colors.mulAlpha(Colors.TEXT_DIM, 0.9f));
            small.drawCentered(ctx, cps(1) + " CPS", ox + bw + G + bw / 2f, y + K - 8.5f, Colors.mulAlpha(Colors.TEXT_DIM, 0.9f));
            y += K + G;
        }
        if (showSpace.on()) {
            int w = K * 3 + G * 2;
            float p = press[6].get();
            RenderUtil.roundRect(ctx, ox, y, w, 14, 5, Colors.lerp(0x22FFFFFF, Colors.withAlpha(accent(), 150), p));
            RenderUtil.roundRect(ctx, ox + w / 2 - 10, y + 6, 20, 2, 1, Colors.lerp(0x66FFFFFF, 0xFFFFFFFF, p));
        }
    }

    private void key(GuiGraphics ctx, int x, int y, int w, int h, String label, int idx) {
        float p = press[idx].get();
        int base = 0x22FFFFFF;
        int on = Colors.withAlpha(accent(), 160);
        RenderUtil.roundRect(ctx, x, y, w, h, 6, Colors.lerp(base, on, p));
        RFont f = RFont.semi(label.length() > 1 ? 7.5f : 9f);
        float ty = idx >= 4 ? y + 3.5f : y + (h - f.height()) / 2f;
        f.drawCentered(ctx, label, x + w / 2f, ty, Colors.lerp(Colors.TEXT_DIM, Colors.WHITE, p));
    }
}
