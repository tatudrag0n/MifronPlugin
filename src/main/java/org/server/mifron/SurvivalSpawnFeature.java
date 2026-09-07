package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SurvivalSpawnFeature
 * ----------------------
 * Forces every respawn inside the Survival world to the fixed coordinate
 * (0, 101, 0), regardless of bed/respawn-anchor location.
 *
 * Wiring (Mifron.java onEnable):
 *   SurvivalSpawnFeature.register(this, "world_survival"); // pass real world name
 */
public final class SurvivalSpawnFeature implements Listener {

    private final String survivalWorldName;

    private SurvivalSpawnFeature(String survivalWorldName) {
        this.survivalWorldName = survivalWorldName;
    }

    public static void register(JavaPlugin plugin, String survivalWorldName) {
        Bukkit.getPluginManager().registerEvents(new SurvivalSpawnFeature(survivalWorldName), plugin);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(survivalWorldName)) return;
        Location fixed = new Location(event.getPlayer().getWorld(), 0.5, 101, 0.5);
        event.setRespawnLocation(fixed);
    }
}
