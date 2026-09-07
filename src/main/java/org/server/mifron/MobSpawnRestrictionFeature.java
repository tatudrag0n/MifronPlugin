package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MobSpawnRestrictionFeature
 * ----------------------------
 * Cancels mob spawns in every world except the configured Survival world.
 * Admin/plugin-driven spawns (CUSTOM, COMMAND) are still allowed so existing
 * features (FFA kits, event spawns, etc.) keep working.
 *
 * Wiring (Mifron.java onEnable):
 *   MobSpawnRestrictionFeature.register(this, "world_survival");
 */
public final class MobSpawnRestrictionFeature implements Listener {

    private final String survivalWorldName;

    private MobSpawnRestrictionFeature(String survivalWorldName) {
        this.survivalWorldName = survivalWorldName;
    }

    public static void register(JavaPlugin plugin, String survivalWorldName) {
        Bukkit.getPluginManager().registerEvents(new MobSpawnRestrictionFeature(survivalWorldName), plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals(survivalWorldName)) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return;
        }
        event.setCancelled(true);
    }
}
