package com.theflex5710.oresim.utils;

import autismclient.AutismClientAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// Per-world seed storage, persisted to .minecraft/config/autism/oresim-addon-seeds.json.
public final class SeedStore {
    private static final File FILE = new File(AutismClientAddon.FOLDER, "oresim-addon-seeds.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Long>>() { }.getType();

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static Map<String, Long> seeds = new LinkedHashMap<>();

    private SeedStore() {
    }

    public static void load() {
        try {
            if (!FILE.isFile()) return;
            try (FileReader reader = new FileReader(FILE)) {
                Map<String, Long> loaded = GSON.fromJson(reader, TYPE);
                seeds = loaded == null ? new LinkedHashMap<>() : loaded;
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[OreSim Addon] Failed to read stored seeds", t);
            seeds = new LinkedHashMap<>();
        }
    }

    private static synchronized void save() {
        try {
            File tmp = new File(FILE.getParentFile(), FILE.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(seeds, writer);
            }
            File target = new File(FILE.getParentFile(), FILE.getName());
            java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            AutismClientAddon.LOG.warn("[OreSim Addon] Failed to save seeds", t);
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static String scopeKey(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (mc.hasSingleplayerServer()) {
            IntegratedServer integrated = mc.getSingleplayerServer();
            if (integrated != null && integrated.getWorldData() != null) {
                String levelName = integrated.getWorldData().getLevelName();
                if (levelName != null && !levelName.isBlank()) return levelName.trim();
            }
        }
        return "unknown";
    }

    public static Long current(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null
            && mc.getSingleplayerServer().overworld() != null) {
            return mc.getSingleplayerServer().overworld().getSeed();
        }
        String scope = scopeKey(mc);
        if (scope == null) return null;
        synchronized (SeedStore.class) {
            return seeds.get(scope);
        }
    }

    public static boolean hasSeed(Minecraft mc) {
        return current(mc) != null;
    }

    public static java.util.List<java.util.Map.Entry<String, Long>> stored() {
        synchronized (SeedStore.class) {
            return new java.util.ArrayList<>(seeds.entrySet());
        }
    }

    public static boolean set(Minecraft mc, long seed) {
        if (mc == null || mc.hasSingleplayerServer()) return false;
        String scope = scopeKey(mc);
        if (scope == null) return false;
        synchronized (SeedStore.class) {
            seeds.put(scope, seed);
            save();
        }
        notifyListeners();
        return true;
    }

    public static boolean clear(Minecraft mc) {
        if (mc == null || mc.hasSingleplayerServer()) return false;
        String scope = scopeKey(mc);
        if (scope == null) return false;
        boolean removed;
        synchronized (SeedStore.class) {
            removed = seeds.remove(scope) != null;
            if (removed) save();
        }
        if (removed) notifyListeners();
        return removed;
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
