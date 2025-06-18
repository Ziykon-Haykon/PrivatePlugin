package dev.portalgenesis.rrPrivates;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class RrPrivates extends JavaPlugin implements Listener {
    private static final LegacyComponentSerializer MESSAGE_PARSER = LegacyComponentSerializer.builder()
            .extractUrls()
            .character('&')
            .hexColors()
            .build();
    private final Map<Material, Integer> blockRadiusMap = new HashMap<>();
    private final Map<String, Private> playersInPrivates = new HashMap<>();
    private final Map<String, Private> privateMap = new HashMap<>();
    private int limit;
    private BukkitTask autoSaveTask;

    private void showEntryTitle(Player player, String ownerName) {
        Component msg = getMessage("on_first_enter", ownerName);
        player.showTitle(Title.title(
                msg,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPrivatesFromJson();
        loadConfigSettings();
        startAutoSaveTask();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private Component getMessage(String key, String arg) {
        String rawMsg = getConfig().getString("messages." + key);
        if (rawMsg == null) {
            return Component.text(key);
        }
        String result = rawMsg.replace("%arg%", arg);
        return MESSAGE_PARSER.deserialize(result);
    }

    private void sendMessage(Player to, String key, String arg) {
        to.sendMessage(getMessage(key, arg));
    }

    private void loadConfigSettings() {
        ConfigurationSection radiusSection = getConfig().getConfigurationSection("radius");
        if (radiusSection != null) {
            for (String key : radiusSection.getKeys(false)) {
                Material material = Material.getMaterial(key);
                if (material != null) {
                    int radius = radiusSection.getInt(key);
                    blockRadiusMap.put(material, radius);
                }
            }
        }
        limit = getConfig().getInt("limit", 0);
    }
    private void startAutoSaveTask() {
        int interval = Math.toIntExact(getConfig().getInt("autoSaveInterval", 20) * 20L);
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(this, this::savePrivatesToJson,
                interval, interval);
    }

    private void loadPrivatesFromJson() {
        File file = new File(getDataFolder(), "privates.json");
        if (!file.exists()) return;

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vector.class, new VectorSerializer())
                .create();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Private>>() {}.getType();
            privateMap.putAll(gson.fromJson(reader, type));
        } catch (IOException e) {
            getLogger().severe("Failed to load privates.json: " + e.getMessage());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Material type = e.getBlockPlaced().getType();

        if (!blockRadiusMap.containsKey(type)) return;

        String playerName = e.getPlayer().getName();

        long count = privateMap.values().stream()
                .filter(p -> p.player.equals(playerName))
                .count();

        if (limit > 0 && count >= limit) {
            sendMessage(e.getPlayer(), "on_limit", String.valueOf(limit));
            e.setCancelled(true);
            return;
        }

        int radius = blockRadiusMap.get(type);
        Location loc = e.getBlockPlaced().getLocation();
        String vectorKey = vectorToString(loc.toVector());

        BoundingBox box = BoundingBox.of(loc, radius, radius, radius);
        Private newPrivate = new Private(box, playerName);
        privateMap.put(vectorKey, newPrivate);

        sendMessage(e.getPlayer(), "on_create", String.valueOf(radius));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        String vectorKey = vectorToString(e.getBlock().getLocation().toVector());
        if (privateMap.containsKey(vectorKey)) {
            privateMap.remove(vectorKey);
            sendMessage(e.getPlayer(), "on_delete", "");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        String name = player.getName();
        Location to = e.getTo();

        Private currentPrivate = null;
        for (Private p : privateMap.values()) {
            if (p.box.contains(to.toVector())) {
                currentPrivate = p;
                break;
            }
        }

        Private previousPrivate = playersInPrivates.get(name);

        if (previousPrivate != null && currentPrivate == null) {
            onExitPrivate(previousPrivate, player);
            playersInPrivates.remove(name);
            return;
        }

        if (previousPrivate == null && currentPrivate != null) {
            onEnterPrivate(currentPrivate, player);
            return;
        }

        if (previousPrivate != null && !previousPrivate.equals(currentPrivate)) {
            onExitPrivate(previousPrivate, player);
            onEnterPrivate(currentPrivate, player);
        }
    }

    private void onExitPrivate(Private p, Player player) {
        sendMessage(player, "on_leave", p.player);
    }

    private void onEnterPrivate(Private p, Player player) {
        String playerName = player.getName();
        if (!p.wereBefore.contains(playerName)) {
            showEntryTitle(player, p.player);
            p.wereBefore.add(playerName);
        } else {
            sendMessage(player, "on_enter", p.player);
        }
        playersInPrivates.put(playerName, p);
    }

    @Override
    public void onDisable() {
        savePrivatesToJson();
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
    }

    private void savePrivatesToJson() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Vector.class, new VectorSerializer())
                .create();
        try (FileWriter writer = new FileWriter(new File(getDataFolder(), "privates.json"))) {
            gson.toJson(privateMap, writer);
        } catch (IOException e) {
            getLogger().severe("Failed to save privates.json: " + e.getMessage());
        }
    }

    private String vectorToString(Vector vec) {
        return vec.getX() + "," + vec.getY() + "," + vec.getZ();
    }

    private static class VectorSerializer implements JsonSerializer<Vector>, JsonDeserializer<Vector> {
        @Override
        public JsonElement serialize(Vector src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            array.add(src.getX());
            array.add(src.getY());
            array.add(src.getZ());
            return array;
        }

        @Override
        public Vector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonArray array = json.getAsJsonArray();
            return new Vector(
                    array.get(0).getAsDouble(),
                    array.get(1).getAsDouble(),
                    array.get(2).getAsDouble()
            );
        }
    }
}