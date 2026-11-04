package dev.rclient.gui;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.anim.Animator;
import dev.rclient.core.ClientSettings;
import dev.rclient.core.Theme;
import dev.rclient.render.Colors;
import dev.rclient.render.RClientLogo;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.render.SkinPreview;
import dev.rclient.render.SkinProvider;
import dev.rclient.render.Tex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.lwjgl.glfw.GLFW;

/**
 * RClient main menu. Performance-minded: the backdrop is one baked texture (MenuBackground), the player is one
 * software-rendered texture (SkinPreview) with the player's REAL skin and no cosmetics, and everything else is a
 * handful of cached rounded rects and text. The character walks in from the background the first time the menu
 * opens in a session, then idles, looks at the cursor and can be dragged to rotate.
 */
public final class MainMenuScreen extends RScreen {
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Settings", "Client", "Quit"};
    private static boolean enteredOnce;                      // walk-in only the first time per session

    private final AnimatedFloat[] hover = new AnimatedFloat[LABELS.length];
    private final AnimatedFloat intro = new AnimatedFloat(0f, 3.2f);
    private final AnimatedFloat dropdown = new AnimatedFloat(0f, 14f);
    private final AnimatedFloat headYawA = new AnimatedFloat(0f, 7f), headPitchA = new AnimatedFloat(0f, 7f);
    private final int[] bx = new int[LABELS.length], by = new int[LABELS.length], bw = new int[LABELS.length];
    private int bh = 18;

    // preview
    private final SkinPreview preview = new SkinPreview();
    private final MenuBackground backdrop = new MenuBackground();
    private float yaw = 0f, yawVel = 0f, idleSince;
    private boolean dragging;
    private int dragZoneX, dragZoneY, dragZoneW, dragZoneH;
    private int acctX, acctY, acctW;

    // walk-in
    private static final float WALK_TIME = 3.4f;
    private float walkP;                                     // 0 far .. 1 arrived
    private float walkPhase;

    // twinkling stars
    private static final int STARS = 56;
    private final float[] stX = new float[STARS], stY = new float[STARS], stP = new float[STARS], stS = new float[STARS];

    public MainMenuScreen() {
        super("RClient");
        for (int i = 0; i < hover.length; i++) hover[i] = new AnimatedFloat(0f, 14f);
        java.util.Random r = new java.util.Random(11);
        for (int i = 0; i < STARS; i++) { stX[i] = r.nextFloat(); stY[i] = r.nextFloat() * 0.85f; stP[i] = r.nextFloat() * 6.28f; stS[i] = 0.5f + r.nextFloat(); }
        walkP = enteredOnce ? 1f : 0f;
    }

    @Override protected void init() {
        intro.set(1f);
        SkinProvider.start();                                // real skin: disk cache now, network refresh in the background
    }

    @Override public void removed() {
        preview.destroy();
        backdrop.destroy();
    }

    // ------------------------------------------------------------------ frame

    @Override protected void frame(GuiGraphics ctx, int mx, int my, float partial) {
        float t = Animator.time();
        float dt = Math.min(0.05f, Animator.dt());
        float in = intro.get();

        // walk-in progress
        if (walkP < 1f) {
            walkP = Math.min(1f, walkP + dt / WALK_TIME);
            if (walkP >= 1f) enteredOnce = true;
        }
        boolean perf = ClientSettings.FPS_FRIENDLY.on();
        float px = perf ? 0f : (mx / (float) width - 0.5f) * -10f, py = perf ? 0f : (my / (float) height - 0.5f) * -6f;
        backdrop.draw(ctx, width, height, t, in, px, py, ClientSettings.MENU_SHAPES.on(), 0.78f, 0.55f);

        if (ClientSettings.MENU_SHAPES.on()) stars(ctx, t, in);
        RenderUtil.rectGradient(ctx, 0, height - 70, width, 70, 0x00000000, Colors.argb((int) (140 * in), 0, 0, 0));

        center(ctx, mx, my, t, in);
        player(ctx, mx, my, t, dt, in);

        RFont.regular(6.8f).draw(ctx, "RClient 1.0.0 · client-side visuals only", 8, height - 12, Colors.mulAlpha(Colors.TEXT_FAINT, in));
        String right = "Minecraft 1.21.11 · Fabric";
        RFont.regular(6.8f).draw(ctx, right, width - 8 - RFont.regular(6.8f).width(right), height - 12, Colors.mulAlpha(Colors.TEXT_FAINT, in));
    }

    // ------------------------------------------------------------------ background bits

    private void stars(GuiGraphics ctx, float t, float in) {
        for (int i = 0; i < STARS; i++) {
            float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * (0.6f + stS[i]) + stP[i]));
            int a = (int) (150 * tw * in * stS[i] * 0.7f);
            if (a <= 2) continue;
            int x = (int) (stX[i] * width), y = (int) (stY[i] * height);
            RenderUtil.rect(ctx, x, y, 1, 1, Colors.argb(a, 220, 230, 255));
            if (stS[i] > 1.2f) {
                RenderUtil.rect(ctx, x - 1, y, 3, 1, Colors.argb(a / 3, 200, 215, 255));
                RenderUtil.rect(ctx, x, y - 1, 1, 3, Colors.argb(a / 3, 200, 215, 255));
            }
        }
    }

    // ------------------------------------------------------------------ centre column

    private void center(GuiGraphics ctx, int mx, int my, float t, float in) {
        int cx = (int) (width * 0.40f);
        int top = height / 2 - 78;
        int lift = (int) ((1f - in) * 14);

        // glass card behind the column
        int cw = 232, cTop = top - 26 + lift, ch = 262;
        RenderUtil.panel(ctx, cx - cw / 2, cTop, cw, ch, 16, 0xFF0B0B0F, in * 0.82f, true);
        RenderUtil.border(ctx, cx - cw / 2, cTop, cw, ch, 16, 1, Colors.argb((int) (34 * in), 255, 255, 255), Colors.argb((int) (8 * in), 255, 255, 255));
        // accent line along the top edge that slowly breathes
        int lw = (int) (cw * (0.30f + 0.12f * (float) Math.sin(t * 0.9f)));
        RenderUtil.rectGradient(ctx, cx - lw / 2, cTop, lw, 1, Theme.accent((int) (200 * in)), Theme.accent(0));

        RClientLogo.draw(ctx, cx - 30, top + lift, 60, Colors.mulAlpha(0xFFFFFFFF, in));
        RenderUtil.shadow(ctx, cx - 18, top + 14 + lift, 36, 30, 14, 22, Theme.accent((int) (36 * in)), 0, 0);
        RFont.bold(17f).drawCentered(ctx, "RClient", cx, top + 66 + lift, Colors.mulAlpha(Colors.WHITE, in));

        RFont f = RFont.semi(9.5f);
        int y = top + 100;
        for (int i = 0; i < LABELS.length; i++) {
            float appear = Math.max(0f, Math.min(1f, in * 1.6f - i * 0.12f));
            int w = (int) f.width(LABELS[i]);
            bw[i] = 196; bx[i] = cx - bw[i] / 2; by[i] = y - 4;
            boolean hov = mx >= bx[i] && mx < bx[i] + bw[i] && my >= by[i] && my < by[i] + bh + 2 && dropdown.target() < 0.5f;
            hover[i].set(hov ? 1f : 0f);
            float h = hover[i].get();
            int rowY = y + (int) ((1f - appear) * 6);
            if (h > 0.01f) {
                RenderUtil.roundRect(ctx, bx[i], by[i], bw[i], bh + 2, 8, Colors.argb((int) (22 * h * appear), 255, 255, 255), Colors.argb((int) (8 * h * appear), 255, 255, 255));
                RenderUtil.roundRect(ctx, bx[i] + 5, by[i] + 4, 2, (int) (10 * h), 1, Colors.mulAlpha(Theme.accent(), appear));
            }
            int col = Colors.lerp(0xFF8A8A94, 0xFFFFFFFF, h);
            f.draw(ctx, LABELS[i], bx[i] + 16 + h * 3f, rowY, Colors.mulAlpha(col, appear));
            if (h > 0.02f) RFont.semi(9.5f).draw(ctx, "›", bx[i] + bw[i] - 16 - (1f - h) * 5f, rowY, Colors.mulAlpha(Theme.accent(), appear * h));
            y += 24;
        }
    }

    // ------------------------------------------------------------------ player preview

    private void player(GuiGraphics ctx, int mx, int my, float t, float dt, float in) {
        float e = 1f - (1f - walkP) * (1f - walkP);                       // ease-out: decelerates into place
        float depth = 0.40f + 0.60f * e;                                   // far -> near scale
        float walkAmt = Math.max(0f, Math.min(1f, (1f - walkP) * 3f));    // stride fades as it arrives
        walkPhase += dt * (4f + 5.5f * walkAmt);

        float baseScale = Math.min(height * 0.165f, 62f);
        int cx = (int) (width * 0.78f - (1f - e) * width * 0.035f);
        int floor = (int) (height / 2 + 96 - (1f - e) * height * 0.10f);

        // account dropdown above the preview (stays at the final position)
        int fcx = (int) (width * 0.78f), ffloor = height / 2 + 96;
        String name = minecraft.getUser().getName();
        RFont nf = RFont.semi(8.6f);
        acctW = (int) nf.width(name) + 34;
        acctX = fcx - acctW / 2; acctY = ffloor - (int) (baseScale * 2.55f) - 24;
        boolean over = mx >= acctX && mx < acctX + acctW && my >= acctY && my < acctY + 20;
        RenderUtil.roundRect(ctx, acctX, acctY, acctW, 20, 10, Colors.mulAlpha(over ? 0xFF1E1E23 : 0xFF151518, in));
        RenderUtil.border(ctx, acctX, acctY, acctW, 20, 10, 1, Colors.argb((int) (36 * in), 255, 255, 255), Colors.argb((int) (10 * in), 255, 255, 255));
        RenderUtil.circle(ctx, acctX + 11, acctY + 10, 3, Colors.mulAlpha(0xFF3DDC84, in));
        nf.draw(ctx, name, acctX + 19, acctY + 5.5f, Colors.mulAlpha(Colors.TEXT, in));
        RenderUtil.rect(ctx, acctX + acctW - 12, acctY + 9, 5, 1, Colors.mulAlpha(Colors.TEXT_DIM, in));
        dropdown.set(dropdown.target());

        // pedestal: light column + ground glow (scale with distance)
        float charIn = Math.min(1f, in * Math.min(1f, walkP * 6f + 0.35f));
        int beamW = (int) (120 * depth);
        RenderUtil.rectGradient(ctx, cx - beamW / 2, floor - (int) (baseScale * 2.6f), beamW, (int) (baseScale * 2.6f), Theme.accent(0), Theme.accent((int) (26 * charIn * e)));
        RenderUtil.shadow(ctx, (int) (cx - 34 * depth), floor - 4, (int) (68 * depth), 8, 4, 22, Theme.accent((int) (90 * charIn)), 0, 0);
        RenderUtil.shadow(ctx, (int) (cx - 20 * depth), floor - 2, (int) (40 * depth), 4, 2, 12, Colors.argb((int) (100 * charIn), 0, 0, 0), 0, 0);

        // rotation: drag, inertia, then spring back to face the viewer
        float idle = Animator.time() - idleSince;
        if (!dragging) {
            yaw += yawVel * dt; yawVel *= (float) Math.exp(-3.5f * dt);
            if (Math.abs(yawVel) < 0.15f && idle > 0.8f) {
                float wrapped = (float) Math.atan2(Math.sin(yaw), Math.cos(yaw));
                yaw = wrapped + (0f - wrapped) * (1f - (float) Math.exp(-2.2f * dt));
            }
        } else yawVel *= (float) Math.exp(-12f * dt);

        // head follows the cursor once settled
        float dispH = baseScale * 2.25f * depth;
        float headY = floor - dispH * 0.80f;
        float settle = walkP >= 1f ? 1f : 0f;
        headYawA.set(Math.max(-0.55f, Math.min(0.55f, (mx - cx) / (float) width * 1.8f)) * settle);
        headPitchA.set(Math.max(-0.35f, Math.min(0.35f, (my - headY) / (float) height * 0.9f)) * settle);

        preview.render(yaw, walkPhase, walkAmt, headYawA.get(), headPitchA.get(), t);
        int dispW = (int) (dispH * SkinPreview.W / SkinPreview.H);
        int dh = (int) dispH;
        int ix = cx - dispW / 2, iy = floor - (int) (dispH * (416f / SkinPreview.H));
        preview.draw(ctx, ix, iy, dispW, dh, Colors.argb((int) (255 * charIn), 255, 255, 255));

        dragZoneX = cx - 90; dragZoneY = floor - (int) (baseScale * 2.6f); dragZoneW = 180; dragZoneH = (int) (baseScale * 2.7f);

        // dropdown list
        float d = dropdown.get();
        if (d > 0.02f) {
            int h = 40;
            RenderUtil.scissor(ctx, acctX - 4, acctY + 22, acctW + 8, (int) (h * d) + 8);
            RenderUtil.panel(ctx, acctX, acctY + 24, acctW, h, 9, 0xFF111114, in, true);
            RFont.regular(7.4f).draw(ctx, "Signed in as", acctX + 10, acctY + 31, Colors.mulAlpha(Colors.TEXT_FAINT, in));
            RFont.semi(8f).draw(ctx, name, acctX + 10, acctY + 42, Colors.mulAlpha(Colors.TEXT, in));
            RenderUtil.endScissor(ctx);
        }
    }

    // ------------------------------------------------------------------ input

    @Override protected boolean onMouseDown(double mx, double my, int button) {
        if (button != 0) return false;
        if (mx >= acctX && mx < acctX + acctW && my >= acctY && my < acctY + 20) {
            dropdown.set(dropdown.target() > 0.5f ? 0f : 1f);
            return true;
        }
        if (dropdown.target() > 0.5f) { dropdown.set(0f); return true; }
        for (int i = 0; i < LABELS.length; i++) {
            if (mx >= bx[i] && mx < bx[i] + bw[i] && my >= by[i] && my < by[i] + bh + 2) {
                switch (i) {
                    case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
                    case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
                    case 2 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
                    case 3 -> minecraft.setScreen(new ClickGuiScreen(this));
                    default -> minecraft.stop();
                }
                return true;
            }
        }
        if (mx >= dragZoneX && mx < dragZoneX + dragZoneW && my >= dragZoneY && my < dragZoneY + dragZoneH) { dragging = true; return true; }
        return false;
    }

    @Override protected boolean onMouseDrag(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        float d = (float) dx * 0.012f;
        yaw += d;
        yawVel = d / Math.max(0.004f, Animator.dt());
        return true;
    }

    @Override protected boolean onMouseUp(double x, double y, int button) {
        boolean was = dragging; dragging = false;
        yawVel = Math.max(-6f, Math.min(6f, yawVel));
        idleSince = Animator.time();
        return was;
    }

    @Override protected boolean onKey(int key, int scancode, int mods) {
        if (key == ClientSettings.OPEN_GUI.get()) { minecraft.setScreen(new ClickGuiScreen(this)); return true; }
        return key == GLFW.GLFW_KEY_ESCAPE;      // there is nowhere to go back to
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
