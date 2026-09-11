package org.server.mifron;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scoreboard.DisplaySlot;

final class WorldPolicyFeature implements Listener {
   private static final Set<String> BLOCKED_PRIVATE_COMMANDS = Set.of(
      "tell", "msg", "w", "whisper", "pm", "dm", "m", "t", "r", "reply"
   );
   private static final Set<String> RESTRICTED_WORLDEDIT = Set.of(
      "paste", "stack", "move", "deform", "smooth", "naturalize", "sphere", "hsphere", "cyl", "hcyl", "drain", "regen", "replace"
   );
   private final Mifron plugin;
   private final Map<UUID, PermissionAttachment> creativePerms = new HashMap<>();

   WorldPolicyFeature(Mifron plugin) { this.plugin = plugin; }

   void apply() {
      this.applyMainWorldBorder();
      this.applySurvivalSpawn();
   }

   void applyMainWorldBorder() { this.plugin.applyMainWorldBorder(); }

   void applySurvivalSpawn() {
      World survival = Bukkit.getWorld(this.plugin.getConfig().getString("world-rules.spawn.survival.world", "survival"));
      if (survival == null) return;
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
         || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) return;
      World world = event.getLocation().getWorld();
      if (world == null) return;
      List<String> disabled = this.plugin.getConfig().getStringList("mob-spawn.natural-spawn-disabled-worlds");
      String name = world.getName();
      boolean blocked = disabled.stream().anyMatch(entry -> entry.equalsIgnoreCase(name));
      if (!blocked && !"survival".equalsIgnoreCase(name) && !name.toLowerCase(Locale.ROOT).startsWith("survival")) blocked = true;
      if (blocked) event.setCancelled(true);
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onRespawn(PlayerRespawnEvent event) {
      World world = event.getRespawnLocation().getWorld();
      if (world == null || !"survival".equalsIgnoreCase(world.getName())) return;
      event.setRespawnLocation(new org.bukkit.Location(
         world,
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.x", 0.0D),
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.y", 101.0D),
         this.plugin.getConfig().getDouble("world-rules.spawn.survival.z", 0.0D)
      ));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      if (this.denyHubEdit(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      if (this.denyHubEdit(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteractProtected(PlayerInteractEvent event) {
      if (event.getClickedBlock() == null) return;
      if (this.denyHubEdit(event.getPlayer(), event.getClickedBlock().getLocation())) event.setCancelled(true);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      if (this.isBlockedPrivateCommand(event.getMessage())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "\u500b\u4eba\u30c1\u30e3\u30c3\u30c8\u30b3\u30de\u30f3\u30c9\u306f\u7121\u52b9\u3067\u3059\u3002Discord\u306eVC\u3092\u4f7f\u3063\u3066\u304f\u3060\u3055\u3044\u3002");
         return;
      }
      Player player = event.getPlayer();
      if (!this.isCreativeWorld(player.getWorld()) || player.isOp() || player.hasPermission("mifron.admin")) return;
      String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
      if (raw.startsWith("//")) raw = raw.substring(2);
      else if (raw.startsWith("/worldedit ")) raw = raw.substring("/worldedit ".length()).trim();
      else return;
      String name = raw.split("\\s+", 2)[0];
      List<String> extra = this.plugin.getConfig().getStringList("creative-world.restricted-worldedit-commands");
      boolean blocked = RESTRICTED_WORLDEDIT.contains(name) || extra.stream().anyMatch(value -> value.equalsIgnoreCase(name));
      if (blocked) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "Creative\u306e\u8ca0\u8377\u5bfe\u7b56\u306b\u3088\u308a\u3001\u3053\u306eWorldEdit\u64cd\u4f5c\u306f\u7ba1\u7406\u8005\u306e\u307f\u5b9f\u884c\u3067\u304d\u307e\u3059\u3002");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onCreativeAdvancement(PlayerAdvancementDoneEvent event) {
      Player player = event.getPlayer();
      if (!this.isCreativeWorld(player.getWorld())) return;
      Advancement advancement = event.getAdvancement();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         var progress = player.getAdvancementProgress(advancement);
         for (String criteria : progress.getAwardedCriteria()) progress.revokeCriteria(criteria);
      });
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onServerCommand(ServerCommandEvent event) {
      if (this.plugin.getConfig().getBoolean("ban-rollback.enabled", true) && this.isBanCommand(event.getCommand())) {
         String target = this.banTarget(event.getCommand());
         if (target != null) this.scheduleRollback(target);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.hideAthleticBoard(event.getPlayer());
      this.detachCreative(event.getPlayer());
   }

   @EventHandler
   public void onChangedWorld(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      String from = event.getFrom() == null ? "" : event.getFrom().getName();
      String to = player.getWorld() == null ? "" : player.getWorld().getName();
      if ("athletic".equalsIgnoreCase(from) && !"athletic".equalsIgnoreCase(to)) this.hideAthleticBoard(player);
      if (this.isCreativeWorld(player.getWorld())) this.attachCreative(player);
      else this.detachCreative(player);
   }

   private void attachCreative(Player player) {
      if (player == null || this.creativePerms.containsKey(player.getUniqueId())) return;
      PermissionAttachment attachment = player.addAttachment(this.plugin);
      attachment.setPermission("worldedit.*", true);
      attachment.setPermission("minecraft.command.teleport", true);
      attachment.setPermission("minecraft.command.tp", true);
      attachment.setPermission("bukkit.command.teleport", true);
      attachment.setPermission("minecraft.command.gamemode", true);
      attachment.setPermission("bukkit.command.gamemode", true);
      this.creativePerms.put(player.getUniqueId(), attachment);
   }

   private void detachCreative(Player player) {
      if (player == null) return;
      PermissionAttachment attachment = this.creativePerms.remove(player.getUniqueId());
      if (attachment != null) player.removeAttachment(attachment);
   }

   private boolean denyHubEdit(Player player, Location location) {
      if (player == null || location == null || location.getWorld() == null) return false;
      if (player.isOp() || player.hasPermission("mifron.admin") || player.hasPermission("mifron.protect.bypass")) return false;
      if (!this.isHubProtectedWorld(location.getWorld())) return false;
      if (!this.inSpawnRadius(location)) return false;
      player.sendMessage(ChatColor.RED + "\u3053\u306e\u30ef\u30fc\u30eb\u30c9\u306e\u30b9\u30dd\u30fc\u30f3\u5468\u8fba\u306f\u4fdd\u8b77\u3055\u308c\u3066\u3044\u307e\u3059\u3002");
      return true;
   }

   private boolean isHubProtectedWorld(World world) {
      String name = world.getName().toLowerCase(Locale.ROOT);
      if (name.equals("survival") || name.startsWith("survival_")) return false;
      if (name.equals("creative") || name.equals("build")) return false;
      return true;
   }

   private boolean inSpawnRadius(Location location) {
      Chunk chunk = location.getChunk();
      Location spawn = location.getWorld().getSpawnLocation();
      int radius = Math.max(4, this.plugin.getConfig().getInt("hub-protection-radius-chunks", 4));
      return Math.abs(chunk.getX() - spawn.getChunk().getX()) <= radius && Math.abs(chunk.getZ() - spawn.getChunk().getZ()) <= radius;
   }

   private boolean isCreativeWorld(World world) {
      if (world == null) return false;
      String name = world.getName();
      return "Creative".equalsIgnoreCase(name) || "build".equalsIgnoreCase(name);
   }

   private void hideAthleticBoard(Player player) {
      if (player == null || Bukkit.getScoreboardManager() == null) return;
      var board = player.getScoreboard();
      if (board != null && board.getObjective("athletic") != null) board.clearSlot(DisplaySlot.SIDEBAR);
      player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
   }

   boolean isBlockedPrivateCommand(String raw) {
      if (raw == null) return false;
      String command = raw.trim();
      if (command.startsWith("/")) command = command.substring(1);
      int space = command.indexOf(' ');
      String name = (space < 0 ? command : command.substring(0, space)).toLowerCase(Locale.ROOT);
      int colon = name.indexOf(':');
      if (colon >= 0) name = name.substring(colon + 1);
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
            this.plugin.getLogger().warning("CoreProtect missing, skip BAN rollback: " + playerName);
            return;
         }
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + playerName + " t:" + time + " #container");
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + playerName + " t:" + time);
         this.plugin.getLogger().info("BAN rollback queued for " + playerName);
      }, 20L);
   }
}
