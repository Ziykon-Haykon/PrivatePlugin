package dev.portalgenesis.rrPrivates;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public final class RrPrivates extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this, this);

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {

        e.getPlayer().sendMessage("Molodec");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public Map<Vector, Private> privateMap = new HashMap<>();



    // chtoto delaet

}