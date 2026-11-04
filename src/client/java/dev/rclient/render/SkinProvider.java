package dev.rclient.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rclient.RClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Finds the signed-in player's real skin for the main-menu preview: UUID -> Mojang session server -> textures
 * property -> skin PNG (+ slim/wide model). The PNG is cached on disk (.minecraft/rclient/skins) so later launches
 * show it instantly; the network fetch runs on a background thread and never blocks rendering. If anything fails
 * (offline, cracked account, rate limit) the vanilla Steve/Alex skin is used, and as a last resort a generated one.
 * Read-only network access to Mojang's public skin endpoint; nothing about the game or other players is touched.
 */
public final class SkinProvider {
    private SkinProvider() {}

    private static volatile BufferedImage skin;      // always 64x64 ARGB once start() ran
    private static volatile boolean slim;
    private static volatile int version;
    private static boolean started;

    public static BufferedImage skin() { return skin; }
    public static boolean slim() { return slim; }
    /** Increments whenever the skin changes (so renderers re-read it). */
    public static int version() { return version; }

    public static synchronized void start() {
        if (started) return;
        started = true;
        UUID uuid = null;
        try { uuid = Minecraft.getInstance().getUser().getProfileId(); } catch (Throwable ignored) { }
        final UUID id = uuid;

        // 1. instant: disk cache, else vanilla default
        boolean fromCache = id != null && loadCache(id);
        if (!fromCache) {
            boolean alex = id != null && (id.hashCode() & 1) == 1;
            BufferedImage d = vanilla(alex);
            if (d == null) d = generated(alex);
            set(d, alex);
        }
        // 2. background refresh
        if (id != null) {
            Thread t = new Thread(() -> fetch(id), "RClient-skin");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void set(BufferedImage img, boolean isSlim) {
        slim = isSlim;
        skin = normalize(img);
        version++;
    }

    // ------------------------------------------------------------------ network

    private static void fetch(UUID id) {
        try {
            Path dir = dir();
            Path meta = dir.resolve(id + ".txt");
            if (Files.exists(meta)) {                       // fresh enough? skip the request
                try {
                    String[] m = Files.readString(meta).trim().split(",");
                    long ts = Long.parseLong(m[1]);
                    if (System.currentTimeMillis() - ts < 6L * 3600_000L) return;
                } catch (Exception ignored) { }
            }
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
            String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + id.toString().replace("-", "");
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return;
            JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
            String value = null;
            for (var e : root.getAsJsonArray("properties")) {
                JsonObject o = e.getAsJsonObject();
                if ("textures".equals(o.get("name").getAsString())) { value = o.get("value").getAsString(); break; }
            }
            if (value == null) return;
            JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("textures");
            if (tex == null || !tex.has("SKIN")) return;
            JsonObject sk = tex.getAsJsonObject("SKIN");
            String png = sk.get("url").getAsString();
            boolean isSlim = sk.has("metadata") && sk.getAsJsonObject("metadata").has("model")
                && "slim".equals(sk.getAsJsonObject("metadata").get("model").getAsString());
            if (!png.startsWith("http://textures.minecraft.net/") && !png.startsWith("https://textures.minecraft.net/")) return;
            HttpResponse<byte[]> pr = http.send(HttpRequest.newBuilder(URI.create(png)).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (pr.statusCode() != 200 || pr.body().length > 200_000) return;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pr.body()));
            if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) return;
            Files.createDirectories(dir);
            Files.write(dir.resolve(id + ".png"), pr.body());
            Files.writeString(meta, (isSlim ? "slim" : "wide") + "," + System.currentTimeMillis());
            // apply on the render thread's next read (fields are volatile)
            set(img, isSlim);
        } catch (Throwable t) {
            RClient.LOG.debug("RClient skin fetch failed: {}", t.toString());
        }
    }

    private static Path dir() { return FabricLoader.getInstance().getGameDir().resolve("rclient").resolve("skins"); }

    private static boolean loadCache(UUID id) {
        try {
            Path png = dir().resolve(id + ".png"), meta = dir().resolve(id + ".txt");
            if (!Files.exists(png)) return false;
            BufferedImage img = ImageIO.read(png.toFile());
            if (img == null) return false;
            boolean isSlim = Files.exists(meta) && Files.readString(meta).startsWith("slim");
            set(img, isSlim);
            return true;
        } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------ fallbacks

    private static BufferedImage vanilla(boolean alex) {
        String[] paths = alex
            ? new String[]{"/assets/minecraft/textures/entity/player/slim/alex.png", "/assets/minecraft/textures/entity/alex.png"}
            : new String[]{"/assets/minecraft/textures/entity/player/wide/steve.png", "/assets/minecraft/textures/entity/steve.png"};
        for (String p : paths) {
            try (InputStream in = SkinProvider.class.getResourceAsStream(p)) {
                if (in == null) continue;
                BufferedImage img = ImageIO.read(in);
                if (img != null) return img;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    /** Plain generated skin (head, body, limbs, face) so the preview never breaks. */
    private static BufferedImage generated(boolean alex) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color skinC = new Color(0xC49A7C), shirt = new Color(alex ? 0x6AAE5A : 0x2F8FA6), pants = new Color(0x35397E), hair = new Color(0x3A2A1D);
        g.setColor(skinC); g.fillRect(0, 0, 32, 16);
        g.setColor(hair); g.fillRect(8, 0, 16, 8); g.fillRect(0, 8, 32, 3); g.fillRect(16, 8, 8, 3);
        g.setColor(Color.WHITE); g.fillRect(10, 12, 2, 1); g.fillRect(14, 12, 2, 1);
        g.setColor(new Color(0x3A4BB3)); g.fillRect(11, 12, 1, 1); g.fillRect(14, 12, 1, 1);
        g.setColor(shirt); g.fillRect(16, 16, 24, 16); g.fillRect(40, 16, 16, 16); g.fillRect(32, 48, 16, 16);
        g.setColor(skinC); g.fillRect(44, 28, 8, 4); g.fillRect(36, 60, 8, 4);
        g.setColor(pants); g.fillRect(0, 16, 16, 16); g.fillRect(16, 48, 16, 16);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ helpers

    /** 64x64 ARGB; legacy 64x32 skins get their left limbs mirrored from the right ones. */
    private static BufferedImage normalize(BufferedImage in) {
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Math.min(64, in.getHeight()); y++) for (int x = 0; x < 64; x++) out.setRGB(x, y, in.getRGB(x, y));
        if (in.getHeight() == 32) {
            mirrorBox(out, 0, 16, 16, 48, 4, 12, 4);     // right leg -> left leg
            mirrorBox(out, 40, 16, 32, 48, 4, 12, 4);    // right arm -> left arm
            // legacy skins have no alpha in the base layer: opaque everywhere the base body is
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                int p = out.getRGB(x, y);
                boolean hat = y < 16 && x >= 32;
                if (!hat && (p >>> 24) != 0) out.setRGB(x, y, p | 0xFF000000);
            }
        }
        return out;
    }

    private static void mirrorBox(BufferedImage img, int su, int sv, int du, int dv, int w, int h, int d) {
        face(img, su + d, sv, du + d, dv, w, d);                         // top
        face(img, su + d + w, sv, du + d + w, dv, w, d);                 // bottom
        face(img, su + d, sv + d, du + d, dv + d, w, h);                 // front
        face(img, su + 2 * d + w, sv + d, du + 2 * d + w, dv + d, w, h); // back
        face(img, su, sv + d, du + d + w, dv + d, d, h);                 // right -> left side
        face(img, su + d + w, sv + d, du, dv + d, d, h);                 // left -> right side
    }

    private static void face(BufferedImage img, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(dx + (w - 1 - x), dy + y, img.getRGB(sx + x, sy + y));
    }
}
