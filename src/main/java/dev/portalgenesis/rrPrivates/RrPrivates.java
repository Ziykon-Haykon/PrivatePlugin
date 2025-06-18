package dev.portalgenesis.rrPrivates;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.slf4j.Logger;

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
    public Map<Vector, Private> privateMap = new HashMap<>();
    private int limit;
    private Logger logger;

    private void showEntryTitle(Player player, String ownerName) {
        var msg = getMessage("on_first_enter", ownerName);
        player.showTitle(Title.title(
                msg,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
    }

    @Override
    public void onEnable() {
        logger = getSLF4JLogger();
        saveDefaultConfig();
        loadPrivatesFromJson();
        loadConfigSettings();
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private Component getMessage(String key, String arg) {
        var rawMsg = getConfig().getString("messages." + key);
        if (rawMsg == null) {
            return Component.text(key);
        }
        var result = rawMsg.replace("%arg%", arg);
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

    public void loadPrivatesFromJson() {
        File file = new File(getDataFolder(), "privates.json");
        if (!file.exists()) return;

        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<Vector, Private>>() {
            }.getType();
            privateMap = gson.fromJson(reader, type);
        } catch (IOException e) {
            logger.error("Failed to load privates.json", e);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Material type = e.getBlockPlaced().getType();

        // Проверка: есть ли такой блок в настройках
        if (!blockRadiusMap.containsKey(type)) return;

        String playerName = e.getPlayer().getName();

        // Проверка лимита
        long count = 0L;
        for (Private p : privateMap.values()) {
            if (p.player.equals(playerName)) {
                count++;
            }
        }

        if (limit > 0 && count >= limit) {
            sendMessage(e.getPlayer(), "on_limit", String.valueOf(limit));
            e.setCancelled(true);
            return;
        }

        // Радиус из config
        int radius = blockRadiusMap.get(type);
        Location loc = e.getBlockPlaced().getLocation();

        BoundingBox box = BoundingBox.of(loc, radius, radius, radius);

        Private newPrivate = new Private(box, playerName);
        privateMap.put(loc.toVector(), newPrivate);

        sendMessage(e.getPlayer(), "on_create", String.valueOf(radius));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Vector vec = e.getBlock().getLocation().toVector();
        if (privateMap.containsKey(vec)) {
            privateMap.remove(vec);
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

        if (previousPrivate != null && previousPrivate != currentPrivate) {
            onExitPrivate(previousPrivate, player);
            onEnterPrivate(currentPrivate, player);
        }
    }

    private void onExitPrivate(Private p, Player player) {
        sendMessage(player, "on_leave", p.player);
    }

    private void onEnterPrivate(Private p, Player player) {
        var playerName = player.getName();
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
    }

    public void savePrivatesToJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(new File(getDataFolder(), "privates.json"))) {
            gson.toJson(privateMap, writer);
        } catch (IOException e) {
            logger.error("Failed to save privates.json", e);
        }
    }

}