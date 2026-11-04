package dev.rclient.hud;

import dev.rclient.render.Colors;
import dev.rclient.render.RFont;
import dev.rclient.render.RenderUtil;
import dev.rclient.render.RClientLogo;
import dev.rclient.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Compact bar: logo | server | FPS | ping | clock. */
public final class InfoBarHud extends HudElement {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final BoolSetting showServer = add(new BoolSetting("Server address", true));
    private final BoolSetting showFps = add(new BoolSetting("FPS", true));
    private final BoolSetting showPing = add(new BoolSetting("Ping", true));
    private final BoolSetting showClock = add(new BoolSetting("Clock", true));

    private String cachedTime = "";
    private long lastTimeUpdate;
    private int width = 200;

    public InfoBarHud() { super("Info Bar", "Logo, server, FPS, ping and clock", 10, 10); }

    @Override public int contentWidth() { return width; }
    @Override public int contentHeight() { return 20; }

    private String server(Minecraft mc) {
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return mc.isLocalServer() ? "Singleplayer" : "Offline";
    }

    private int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return e == null ? 0 : e.getLatency();
    }

    @Override protected void drawContent(GuiGraphics ctx, float pt) {
        Minecraft mc = Minecraft.getInstance();
        RFont f = RFont.semi(7.8f), dim = RFont.regular(7.8f);
        long now = System.currentTimeMillis();
        if (now - lastTimeUpdate > 1000) { cachedTime = LocalTime.now().format(FMT); lastTimeUpdate = now; }

        int x = 7;
        RClientLogo.draw(ctx, x, 4, 12, accent());
        x += 18;
        f.draw(ctx, "RClient", x, 6, Colors.TEXT);
        x += (int) f.width("RClient") + 7;

        if (showServer.on()) x = seg(ctx, x, dim, f, server(mc), null);
        if (showFps.on()) x = seg(ctx, x, dim, f, String.valueOf(mc.getFps()), "FPS");
        if (showPing.on()) x = seg(ctx, x, dim, f, String.valueOf(ping(mc)), "ms");
        if (showClock.on()) x = seg(ctx, x, dim, f, cachedTime, null);
        width = x + 1;
    }

    private int seg(GuiGraphics ctx, int x, RFont dim, RFont f, String value, String unit) {
        RenderUtil.rect(ctx, x, 5, 1, 10, 0x22FFFFFF);
        x += 7;
        f.draw(ctx, value, x, 6, Colors.TEXT);
        x += (int) f.width(value);
        if (unit != null) { dim.draw(ctx, " " + unit, x, 6, Colors.TEXT_DIM); x += (int) dim.width(" " + unit); }
        return x + 7;
    }
}
