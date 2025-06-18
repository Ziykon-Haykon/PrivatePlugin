package dev.portalgenesis.rrPrivates;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.eclipse.aether.spi.checksums.TrustedChecksumsSource;
import org.eclipse.aether.util.FileUtils;

import java.io.*;
import java.lang.module.Configuration;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;

public final class RrPrivates extends JavaPlugin implements Listener {


    private final Map<Material, Integer> blockRadiusMap = new HashMap<>();

    private final Set<String> titleShownPlayers = new HashSet<>();

    private int limit;

    private void showEntryTitle(Player player, String ownerName) {
        player.showTitle(Title.title(
                Component.text("Вы вошли на территорию " + ownerName),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
    }


    @Override
    public void onEnable() {

        saveDefaultConfig();
        loadPrivatesFromJson();
        loadConfigSettings();

        ConfigurationSection radiusSection = getConfig().getConfigurationSection("radius");
        this.getServer().getPluginManager().registerEvents(this, this);

        for(String key : radiusSection.getKeys(false)) {

            Material material =  Material.getMaterial(key);
            int radius = radiusSection.getInt(key);
            blockRadiusMap.put(material, radius);
        }
        this.limit = getConfig().getInt("limit");


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
            Type type = new TypeToken<Map<Vector, Private>>() {}.getType();
            privateMap = gson.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<Vector, Private> privateMap = new HashMap<>();

    public Map<String, Private> inPrivatePlayer = new HashMap<>();

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Material type = e.getBlockPlaced().getType();

        // Проверка: есть ли такой блок в настройках
        if (!blockRadiusMap.containsKey(type)) return;

        String playerName = e.getPlayer().getName();

        // Проверка лимита
        long count = privateMap.values().stream()
                .filter(p -> p.player.equals(playerName))
                .count();

        if (limit > 0 && count >= limit) {
            e.getPlayer().sendMessage("Превышен лимит приватов (" + limit + ")");
            e.setCancelled(true);
            return;
        }

        // Радиус из config
        int radius = blockRadiusMap.get(type);
        Location loc = e.getBlockPlaced().getLocation();

        BoundingBox box = BoundingBox.of(loc, radius, radius, radius);

        Private newPrivate = new Private(box, playerName);
        privateMap.put(loc.toVector(), newPrivate);

        e.getPlayer().sendMessage("Приват создан вокруг " + type.name() + " радиусом " + radius);

        if (limit > 0 && count >= limit) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("Превышен лимит приватов!");
            return;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Vector vec = e.getBlock().getLocation().toVector();
        if (privateMap.containsKey(vec)) {
            privateMap.remove(vec);
            e.getPlayer().sendMessage("Приват удалён");
        }
    }

    @EventHandler
    public void playerMove(PlayerMoveEvent e) {
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

        Private previousPrivate = inPrivatePlayer.get(name);

// Вышел из привата в обычную зону
        if (previousPrivate != null && currentPrivate == null) {
            player.sendMessage("Вы вышли с территории игрока " + previousPrivate.player);
            inPrivatePlayer.remove(name);
            titleShownPlayers.remove(name); // чтобы снова показать title при следующем входе
            return;
        }

// Вошёл впервые в приват
        if (previousPrivate == null && currentPrivate != null) {
            if (!titleShownPlayers.contains(name)) {
                showEntryTitle(player, currentPrivate.player);
                titleShownPlayers.add(name);
            } else {
                player.sendMessage("Вы вошли на территорию игрока " + currentPrivate.player);
            }
            inPrivatePlayer.put(name, currentPrivate);
            return;
        }

// Перешёл из одного привата в другой
        if (previousPrivate != null && currentPrivate != null && previousPrivate != currentPrivate) {
            player.sendMessage("Вы покинули территорию игрока " + previousPrivate.player);

            if (!titleShownPlayers.contains(name)) {
                showEntryTitle(player, currentPrivate.player);
                titleShownPlayers.add(name);
            } else {
                player.sendMessage("Вы вошли на территорию игрока " + currentPrivate.player);
            }

            inPrivatePlayer.put(name, currentPrivate);
        }
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
            e.printStackTrace();
        }
    }

}