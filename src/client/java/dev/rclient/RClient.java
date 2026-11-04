package dev.rclient;

import dev.rclient.anim.Animator;
import dev.rclient.core.ClientSettings;
import dev.rclient.core.ConfigManager;
import dev.rclient.gui.ClickGuiScreen;
import dev.rclient.gui.EmoteWheelScreen;
import dev.rclient.gui.RScreen;
import dev.rclient.hud.HudManager;
import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;
import dev.rclient.render.Colors;
import dev.rclient.render.RenderUtil;
import dev.rclient.render.WorldDraw;
import dev.rclient.visual.Effects;
import dev.rclient.visual.EmoteModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * RClient — client-side visuals and cosmetics ONLY.
 * Nothing here sends or alters packets, touches movement or combat, or gives a gameplay advantage.
 */
public final class RClient implements ClientModInitializer {
    public static final String ID = "rclient";
    public static final Logger LOG = LoggerFactory.getLogger("RClient");

    private static final boolean[] KEY_DOWN = new boolean[ModuleSlots.MAX];
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
    private static final Map<Screen, Boolean> PUSHED = new WeakHashMap<>();
    private static boolean guiKeyDown;

    @Override public void onInitializeClient() {
        ModuleManager.init();
        HudManager.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            if (!ConfigManager.load("default")) ConfigManager.save("default");
            ClientSettings.applyAll();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ConfigManager.autosave());

        ClientTickEvents.END_CLIENT_TICK.register(RClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> WorldDraw.render(ctx.matrices(), ctx.consumers()));

        // Local-only reactions to your own attacks. Always PASS: the attack itself is never touched.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) {
                var hf = ModuleManager.get(Effects.HitFx.class);
                var kf = ModuleManager.get(Effects.KillFx.class);
                var hs = ModuleManager.get(Effects.HitSounds.class);
                if (hf.isEnabled()) hf.onHit(entity);
                if (kf.isEnabled()) kf.onHit(entity);
                if (hs.isEnabled()) hs.onHit(entity);
            }
            return InteractionResult.PASS;
        });

        // Vanilla screen open animation (scale + fade), no mixin needed.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof RScreen) return;
            OPENED.put(screen, System.nanoTime());
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, delta) -> guiAnimBegin(s, ctx));
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> guiAnimEnd(s, ctx));
        });

        LOG.info("RClient initialised (visual-only).");
    }

    // ------------------------------------------------------------------ tick + keybinds

    private static void tick(Minecraft mc) {
        for (Module m : ModuleManager.all()) if (m.isEnabled()) m.onClientTick();

        // emote auto-stop when you start moving
        if (EmoteModule.active() && mc.player != null && Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z) > 0.06) EmoteModule.stop();

        if (mc.getWindow() == null) return;
        long win = mc.getWindow().handle();

        // open menu (only when no screen is open)
        boolean g = down(win, ClientSettings.OPEN_GUI.get());
        if (g && !guiKeyDown && mc.screen == null && mc.level != null) mc.setScreen(new ClickGuiScreen(null));
        guiKeyDown = g;

        // module keybinds (toggle on press; emotes open the wheel)
        if (mc.screen != null) { java.util.Arrays.fill(KEY_DOWN, false); return; }
        int i = 0;
        for (Module m : ModuleManager.all()) {
            if (i >= KEY_DOWN.length) break;
            boolean d = m.keybind.bound() && down(win, m.keybind.get());
            if (d && !KEY_DOWN[i]) {
                if (m instanceof EmoteModule em) { if (em.isEnabled() && mc.level != null) mc.setScreen(new EmoteWheelScreen(em)); }
                else m.toggle();
            }
            KEY_DOWN[i] = d;
            i++;
        }
    }

    private static boolean down(long win, int key) {
        if (key < 0) return false;
        if (key >= 1000) return GLFW.glfwGetMouseButton(win, key - 1000) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
    }

    // ------------------------------------------------------------------ HUD-layer overlays

    /** Called from the HUD layer (see HudManager) for screen-space visual modules. */
    public static void overlays(GuiGraphics ctx, int w, int h) {
        var kill = ModuleManager.get(Effects.KillFx.class);
        var hand = ModuleManager.get(Effects.HandEffects.class);
        var blur = ModuleManager.get(Effects.MotionBlur.class);
        if (blur.isEnabled()) blur.renderOverlay(ctx, w, h);
        if (hand.isEnabled()) hand.renderOverlay(ctx, w, h);
        kill.renderOverlay(ctx, w, h);
        if (EmoteModule.active()) EmoteModule.playTime += Animator.dt();
    }

    // ------------------------------------------------------------------ vanilla GUI animation

    private static void guiAnimBegin(Screen s, GuiGraphics ctx) {
        var m = ModuleManager.get(Effects.GuiAnimation.class);
        if (m == null || !m.isEnabled()) return;
        Long t0 = OPENED.get(s);
        if (t0 == null) return;
        float t = (System.nanoTime() - t0) / 1e9f / m.duration.f();
        if (t >= 1f) { PUSHED.put(s, false); return; }
        float e = 1f - (float) Math.pow(1f - Math.max(0f, t), 3);
        float sc = m.startScale.f() + (1f - m.startScale.f()) * e;
        float cx = ctx.guiWidth() / 2f, cy = ctx.guiHeight() / 2f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(sc, sc);
        ctx.pose().translate(-cx, -cy);
        PUSHED.put(s, true);
    }

    private static void guiAnimEnd(Screen s, GuiGraphics ctx) {
        if (!Boolean.TRUE.equals(PUSHED.get(s))) return;
        ctx.pose().popMatrix();
        PUSHED.put(s, false);
        var m = ModuleManager.get(Effects.GuiAnimation.class);
        Long t0 = OPENED.get(s);
        if (m != null && m.fade.on() && t0 != null) {
            float t = (System.nanoTime() - t0) / 1e9f / m.duration.f();
            float e = 1f - (float) Math.pow(1f - Math.max(0f, Math.min(1f, t)), 3);
            RenderUtil.rect(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight(), Colors.argb((int) (90 * (1f - e)), 0, 0, 0));
        }
    }

    /** Max number of modules whose keybinds are polled. */
    private static final class ModuleSlots { static final int MAX = 128; }
}
