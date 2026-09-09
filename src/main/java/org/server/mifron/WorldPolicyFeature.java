package org.server.mifron;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;

final class WorldPolicyFeature implements Listener {
   private static final Set<String> BLOCKED_PRIVATE_COMMANDS = Set.of(
      "tell", "msg", "w", "whisper", "pm", "dm", "m", "t", "r", "reply"
   );

   private final Mifron plugin;

   WorldPolicyFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   void apply() {
      this.applyMainWorldBorder();
      this.applySurvivalSpawn();
   }

   void applyMainWorldBorder() {
      this.plugin.applyMainWorldBorder();
   }

   void applySurvivalSpawn() {
      World survival = Bukkit.getWorld(this.plugin.getConfig().getString("world-rules.spawn.survival.world", "survival"));
      if (survival == null) {
         return;
      }
      survival.setSpawnLocation(
         this.plugin.getConfig().getInt("world-rules.spawn.survival.x", 0),
         this.plugin.getConfig().getInt("world-rules.spawn.survival.y", 101),
         this.plugin.getConfig().getInt("world-rules.spawn.survival.z", 0)
      );
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSpawn(CreatureSpawnEvent event) {
      if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
         || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
         || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
         return;
      }
      World world = event.getLocation().getWorld();
      if (world == null) {
         return;
      }
      List<String> disabled = this.plugin.getConfig().getStringList("mob-spawn.natural-spawn-disabled-worlds");
      String name = world.getName();
      boolean blocked = disabled.stream().anyMatch(entry -> entry.equalsIgnoreCase(name));
      if (!blocked && !"survival".equalsIgnoreCase(name) && !name.toLowerCase(Locale.ROOT).startsWith("survival")) {
         blocked = true;
      }
      if (blocked) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onRespawn(PlayerRespawnEvent event) {
      World world = event.getRespawnLocation().getWorld();
      if (world == null || !"survival".equalsIgnoreCase(world.getName())) {
         return;
      }
      event.setRespawnLocation(new org.bukkit.Location(
         world,
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.x", 0.0D),
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.y", 101.0D),
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.z", 0.0D)
      ));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      if (this.isBlockedPrivateCommand(event.getMessage())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "個人チャットコマンドは無効です。DiscordのVCを使ってください。");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onServerCommand(ServerCommandEvent event) {
      if (this.plugin.getConfig().getBoolean("ban-rollback.enabled", true)
         && this.isBanCommand(event.getCommand())) {
         String target = this.banTarget(event.getCommand());
         if (target != null) {
            this.scheduleRollback(target);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
   }

   boolean isBlockedPrivateCommand(String raw) {
      if (raw == null) {
         return false;
      }
      String command = raw.trim();
      if (command.startsWith("/")) {
         command = command.substring(1);
      }
      int space = command.indexOf(' ');
      String name = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
      int colon = name.indexOf(':');
      if (colon >= 0) {
         name = name.substring(colon + 1);
      }
      return BLOCKED_PRIVATE_COMMANDS.contains(name);
   }

   private boolean isBanCommand(String raw) {
      String command = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
      return command.startsWith("ban ") || command.startsWith("minecraft:ban ") || command.startsWith("ban-ip ");
   }

   private String banTarget(String raw) {
      String[] parts = raw.trim().split("\\s+");
      return parts.length >= 2 ? parts[1] : null;
   }

   private void scheduleRollback(String playerName) {
      String time = this.plugin.getConfig().getString("ban-rollback.time", "3650d");
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (Bukkit.getPluginManager().getPlugin("CoreProtect") == null) {
            this.plugin.getLogger().warning("CoreProtect が入っていないため BAN ロールバックをスキップしました: " + playerName);
            return;
         }
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + playerName + " t:" + time + " #container");
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + playerName + " t:" + time);
         this.plugin.getLogger().info("BAN rollback queued for " + playerName);
      }, 20L);
   }
}
