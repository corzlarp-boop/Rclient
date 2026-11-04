package dev.rclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rclient.RClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

import java.lang.reflect.*;

/**
 * Connects WorldDraw to Fabric's world/level render event WITHOUT a compile-time dependency on its exact
 * class names (they changed between Fabric API versions). It looks the event up by reflection, and reads the
 * PoseStack / buffer source out of the event context by return type. If nothing is found, in-world effects are
 * simply skipped (menus, GUI and HUD are unaffected) and the reason is written to the log.
 */
public final class WorldHook {
    private static final String P = "net.fabricmc.fabric.api.client.rendering.v1.";
    private static final String[] CLASSES = {
        P + "level.LevelRenderEvents", P + "world.WorldRenderEvents", P + "WorldRenderEvents"
    };
    private static final String[] FIELDS = { "AFTER_ENTITIES", "AFTER_TRANSLUCENT_TERRAIN", "AFTER_TRANSLUCENT", "AFTER_TRANSLUCENT_FEATURES", "END_MAIN", "LAST" };
    private static boolean warned;

    private WorldHook() {}

    public static void install() {
        for (String cn : CLASSES) {
            Class<?> c;
            try { c = Class.forName(cn); } catch (Throwable t) { continue; }
            for (String fn : FIELDS) {
                try {
                    Field f = c.getField(fn);
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    Object event = f.get(null);
                    Class<?> iface = listenerType(f);
                    if (event == null || iface == null) continue;
                    Object listener = Proxy.newProxyInstance(WorldHook.class.getClassLoader(), new Class<?>[]{iface},
                        (proxy, m, args) -> {
                            if (m.getDeclaringClass() == Object.class) return m.getName().equals("hashCode") ? 0 : m.getName().equals("equals") ? proxy == args[0] : "RClientWorldListener";
                            if (args != null && args.length > 0) dispatch(args[0]);
                            return null;
                        });
                    Method reg = null;
                    for (Method m : event.getClass().getMethods()) if (m.getName().equals("register") && m.getParameterCount() == 1) { reg = m; break; }
                    if (reg == null) continue;
                    reg.setAccessible(true);
                    reg.invoke(event, listener);
                    RClient.LOG.info("RClient world hook: {}.{}", cn, fn);
                    return;
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable t) {
                    RClient.LOG.warn("RClient world hook {}.{} failed: {}", cn, fn, t.toString());
                }
            }
        }
        RClient.LOG.warn("RClient: no Fabric world render event found - in-world effects (sky, cosmetics, trails, particles) disabled.");
    }

    /** Event<T> -> T */
    private static Class<?> listenerType(Field f) {
        Type g = f.getGenericType();
        if (g instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof Class<?> k) return k;
            if (a instanceof ParameterizedType p2 && p2.getRawType() instanceof Class<?> k2) return k2;
        }
        return null;
    }

    private static void dispatch(Object ctx) {
        try {
            PoseStack ms = null;
            MultiBufferSource mb = null;
            for (Method m : ctx.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> r = m.getReturnType();
                if (ms == null && r == PoseStack.class) { m.setAccessible(true); ms = (PoseStack) m.invoke(ctx); }
                else if (mb == null && MultiBufferSource.class.isAssignableFrom(r)) { m.setAccessible(true); mb = (MultiBufferSource) m.invoke(ctx); }
            }
            Minecraft mc = Minecraft.getInstance();
            if (mb == null) mb = mc.renderBuffers().bufferSource();
            if (ms == null) {
                if (!warned) { warned = true; RClient.LOG.warn("RClient: world render context has no PoseStack - in-world effects skipped."); }
                return;
            }
            WorldDraw.render(ms, mb);
        } catch (Throwable t) {
            if (!warned) { warned = true; RClient.LOG.warn("RClient world draw failed: {}", t.toString()); }
        }
    }
}
