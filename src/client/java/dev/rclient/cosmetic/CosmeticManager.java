package dev.rclient.cosmetic;

import dev.rclient.mesh.CosmeticContext;
import dev.rclient.mesh.MeshBuilder;
import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

/** Collects the enabled cosmetic modules and builds one combined mesh. */
public final class CosmeticManager {
    private CosmeticManager() {}

    private static final List<CosmeticModule> ALL = new ArrayList<>();
    private static boolean init;

    private static void init() {
        if (init) return;
        for (Module m : ModuleManager.all()) if (m instanceof CosmeticModule c) ALL.add(c);
        init = true;
    }

    public static boolean any() {
        init();
        for (CosmeticModule c : ALL) if (c.isEnabled()) return true;
        return false;
    }

    public static void build(MeshBuilder b, CosmeticContext ctx) {
        init();
        for (CosmeticModule c : ALL) if (c.isEnabled()) c.build(b, ctx);
    }
}
