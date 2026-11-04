package dev.rclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rclient.module.Module;
import dev.rclient.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** JSON config profiles in .minecraft/rclient/configs/<name>.json */
public final class ConfigManager {
    private ConfigManager() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String active = "default";

    public static Path dir() {
        Path p = FabricLoader.getInstance().getGameDir().resolve("rclient").resolve("configs");
        try { Files.createDirectories(p); } catch (IOException ignored) { }
        return p;
    }

    private static Path file(String name) { return dir().resolve(sanitize(name) + ".json"); }

    private static String sanitize(String n) { return n.replaceAll("[^a-zA-Z0-9 _\\-]", "").trim(); }

    public static String active() { return active; }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 5));
            });
        } catch (IOException ignored) { }
        return out;
    }

    public static boolean save(String name) {
        name = sanitize(name);
        if (name.isEmpty()) return false;
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("global", ClientSettings.save());
        JsonObject mods = new JsonObject();
        for (Module m : ModuleManager.all()) mods.add(m.name, m.save());
        root.add("modules", mods);
        try (Writer w = Files.newBufferedWriter(file(name), StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
            active = name;
            return true;
        } catch (IOException e) { return false; }
    }

    public static boolean load(String name) {
        Path p = file(name);
        if (!Files.exists(p)) return false;
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("global")) ClientSettings.load(root.getAsJsonObject("global"));
            if (root.has("modules")) {
                JsonObject mods = root.getAsJsonObject("modules");
                for (Module m : ModuleManager.all()) if (mods.has(m.name)) m.load(mods.getAsJsonObject(m.name));
            }
            active = sanitize(name);
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean delete(String name) {
        try { return Files.deleteIfExists(file(name)); } catch (IOException e) { return false; }
    }

    public static boolean rename(String from, String to) {
        to = sanitize(to);
        if (to.isEmpty() || Files.exists(file(to))) return false;
        try {
            Files.move(file(from), file(to));
            if (active.equals(from)) active = to;
            return true;
        } catch (IOException e) { return false; }
    }

    /** Autosave current state into the active profile. */
    public static void autosave() { save(active); }
}
