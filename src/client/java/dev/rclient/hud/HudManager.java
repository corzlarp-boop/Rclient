package dev.rclient.hud;

import dev.rclient.anim.AnimatedFloat;
import dev.rclient.anim.Animator;
import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class HudManager {
    private HudManager() {}

    public static HotbarHud HOTBAR;
    public static StatBarHud HEALTH, HUNGER;

    private static final List<HudElement> ELEMENTS = new ArrayList<>();

    public static void init() {
        for (Module m : ModuleManager.all()) if (m instanceof HudElement h) ELEMENTS.add(h);

        // Replace vanilla layers only while our styled version is on – otherwise pass through untouched.
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR,
            orig -> (ctx, tick) -> { if (HOTBAR == null || !HOTBAR.isEnabled()) orig.render(ctx, tick); });
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR,
            orig -> (ctx, tick) -> { if (HEALTH == null || !HEALTH.isEnabled()) orig.render(ctx, tick); });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR,
            orig -> (ctx, tick) -> { if (HUNGER == null || !HUNGER.isEnabled()) orig.render(ctx, tick); });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("rclient", "hud"), (ctx, tick) -> {
            Animator.frame(System.nanoTime());
            dev.rclient.RClient.overlays(ctx, ctx.guiWidth(), ctx.guiHeight());
            render(ctx, tick.getGameTimeDeltaPartialTick(false), false);
            NotificationManager.render(ctx, ctx.guiWidth(), ctx.guiHeight());
        });
    }

    public static List<HudElement> elements() { return ELEMENTS; }

    /** Draws all enabled widgets (or all of them with outlines when the editor is open). */
    public static void render(GuiGraphics ctx, float partialTicks, boolean editor) {
        Minecraft mc = Minecraft.getInstance();
        if (!editor && mc.options.hideGui) return;
        if (HOTBAR != null) HOTBAR.ensurePlaced();
        if (HEALTH != null) HEALTH.ensurePlaced();
        if (HUNGER != null) HUNGER.ensurePlaced();
        for (HudElement e : ELEMENTS) {
            if (!editor && !e.isEnabled() && e.fade.get() < 0.01f) continue;
            e.render(ctx, partialTicks, editor);
        }
    }
}
