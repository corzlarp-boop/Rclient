package dev.rclient.module;

import dev.rclient.cosmetic.CosmeticModule;
import dev.rclient.hud.*;
import dev.rclient.visual.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private ModuleManager() {}

    private static final List<Module> MODULES = new ArrayList<>();

    public static void init() {
        if (!MODULES.isEmpty()) return;

        // ---- Visuals ----
        reg(new SkyModule());
        reg(new TrailsModule());
        reg(new EmoteModule());
        reg(new Effects.HitFx());
        reg(new Effects.JumpFx());
        reg(new Effects.KillFx());
        reg(new Effects.PearlParticles());
        reg(new Effects.HandEffects());
        reg(new Effects.LineGlyphs());
        reg(new Effects.MotionBlur());

        // ---- Cosmetics ----
        reg(new CosmeticModule.Wings());
        reg(new CosmeticModule.Horns());
        reg(new CosmeticModule.FlameHat());
        reg(new CosmeticModule.Halo());
        reg(new CosmeticModule.Orbit());
        reg(new CosmeticModule.Pet());
        reg(new CosmeticModule.Cape());
        reg(new CosmeticModule.Knife());
        reg(new Effects.PlayerModel());

        // ---- HUD ----
        reg(new InfoBarHud());
        reg(new KeystrokesHud());
        reg(new KeybindsHud());
        HudManager.HOTBAR = reg(new HotbarHud());
        HudManager.HEALTH = reg(new StatBarHud(StatBarHud.Kind.HEALTH));
        HudManager.HUNGER = reg(new StatBarHud(StatBarHud.Kind.HUNGER));

        // ---- Misc ----
        reg(new Effects.HitSounds());
        reg(new Effects.GuiAnimation());
        reg(new Effects.ItemPhysics());
        reg(new Effects.NoBlockRotation());

        // sensible first-run defaults (silent: no toast spam on startup)
        get(InfoBarHud.class).setEnabledSilently(true);
        get(KeystrokesHud.class).setEnabledSilently(true);
        get(Effects.GuiAnimation.class).setEnabledSilently(true);
        get(CosmeticModule.Wings.class).setEnabledSilently(true);
        get(CosmeticModule.FlameHat.class).setEnabledSilently(true);
        get(CosmeticModule.Horns.class).setEnabledSilently(true);
    }

    private static <T extends Module> T reg(T m) { MODULES.add(m); return m; }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> c) {
        for (Module m : MODULES) if (m.getClass() == c) return (T) m;
        return null;
    }
}
