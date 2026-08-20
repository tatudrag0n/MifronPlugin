package org.server.mifron;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.permissions.PermissionAttachment;

/** Owns the per-player build worlds and the temporary WorldEdit permission boundary. */
final class BuildWorldManager implements Listener {
   private static final String WORLD_PREFIX = "mifron_build_";
   private final Mifron plugin;
   private final Map<UUID, PermissionAttachment> worldEditAttachments = new ConcurrentHashMap<>();

   BuildWorldManager(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.plugin.getConfig().addDefault("build-world.enabled", true);
      this.plugin.getConfig().addDefault("build-world.world-prefix", WORLD_PREFIX);
      this.plugin.getConfig().addDefault("build-world.spawn-y", 64);
      this.plugin.getConfig().addDefault("build-world.border-size", 256);
      this.plugin.getConfig().addDefault("build-world.platform-radius", 16);
      this.plugin.getConfig().options().copyDefaults(true);
      this.plugin.saveConfig();
   }

   void shutdown() {
      for (Map.Entry<UUID, PermissionAttachment> entry : this.worldEditAttachments.entrySet()) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player != null) {
            player.removeAttachment(entry.getValue());
         }
      }
      this.worldEditAttachments.clear();
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("§cプレイヤーのみ実行できます。");
         return true;
      }
      if (!this.plugin.getConfig().getBoolean("build-world.enabled", true)) {
         player.sendMessage("§cBuildワールドは現在無効です。");
         return true;
      }

      String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "enter";
      if (action.equals("enter") || action.equals("open")) {
         this.enter(player, player.getUniqueId());
      } else if (action.equals("exit") || action.equals("leave")) {
         this.exit(player);
      } else if (action.equals("visit") && player.hasPermission("mifron.admin")) {
         if (args.length < 3) {
            player.sendMessage("§e/mf build visit <player>");
         } else {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
               player.sendMessage("§cオンラインのプレイヤーが見つかりません。");
            } else {
               this.enter(player, target.getUniqueId());
            }
         }
      } else if (action.equals("visit")) {
         player.sendMessage("§c他プレイヤーのBuildワールドへ入る権限がありません。");
      } else {
         player.sendMessage("§e/mf build enter|exit");
      }
      return true;
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 2) {
         return List.of("enter", "exit", "leave", "open", "visit");
      }
      return args.length == 3 && "visit".equalsIgnoreCase(args[1]) ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
   }

   boolean isBuildWorld(World world) {
      return world != null && world.getName().toLowerCase(java.util.Locale.ROOT).startsWith(this.worldPrefix());
   }

   boolean isOwner(Player player, World world) {
      if (player == null || !this.isBuildWorld(world)) {
         return false;
      }
      return world.getName().equalsIgnoreCase(this.worldName(player.getUniqueId()));
   }

   private void enter(Player player, UUID ownerId) {
      World world = this.ensureWorld(ownerId);
      if (world == null) {
         player.sendMessage("§cBuildワールドを作成できませんでした。管理者に連絡してください。");
         return;
      }
      this.attachWorldEdit(player);
      player.setGameMode(GameMode.CREATIVE);
      player.teleport(this.spawn(world));
      player.sendMessage("§a専用Buildワールドへ移動しました。");
      player.sendMessage("§7WorldEditが使用できます。完成した建築は /mf structure submit <名前> で提出できます。");
      if (!this.isOwner(player, world)) {
         player.sendMessage("§e運営確認用のBuildワールドを表示しています。編集は所有者本人のみ可能です。");
      }
   }

   private void exit(Player player) {
      this.detachWorldEdit(player);
      player.setGameMode(GameMode.SURVIVAL);
      World target = Bukkit.getWorld(this.plugin.getConfig().getString("servers.main.world", "world"));
      if (target == null) {
         target = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
      }
      if (target != null) {
         player.teleport(target.getSpawnLocation());
      }
      player.sendMessage("§aBuildワールドから退出しました。");
   }

   private World ensureWorld(UUID ownerId) {
      String worldName = this.worldName(ownerId);
      World existing = Bukkit.getWorld(worldName);
      if (existing != null) {
         return existing;
      }
      WorldCreator creator = new WorldCreator(worldName).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false);
      World world = Bukkit.createWorld(creator);
      if (world == null) {
         return null;
      }
      world.setGameRule(GameRules.KEEP_INVENTORY, true);
      world.setGameRule(GameRules.SPAWN_MOBS, false);
      world.setGameRule(GameRules.ADVANCE_TIME, false);
      world.setGameRule(GameRules.PVP, false);
      world.setTime(6000L);
      world.getWorldBorder().setCenter(0.0, 0.0);
      world.getWorldBorder().setSize(Math.max(32.0, this.plugin.getConfig().getDouble("build-world.border-size", 256.0)));
      Location spawn = this.spawn(world);
      this.createPlatform(world, spawn);
      world.setSpawnLocation(spawn);
      this.plugin.data().set("build-worlds." + ownerId + ".world", worldName);
      this.plugin.data().set("build-worlds." + ownerId + ".created-at", System.currentTimeMillis());
      this.plugin.saveData();
      return world;
   }

   private Location spawn(World world) {
      return new Location(world, 0.5, this.plugin.getConfig().getDouble("build-world.spawn-y", 64.0), 0.5);
   }

   private void createPlatform(World world, Location center) {
      int radius = Math.max(4, Math.min(64, this.plugin.getConfig().getInt("build-world.platform-radius", 16)));
      int y = center.getBlockY() - 1;
      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            world.getBlockAt(x, y, z).setType(org.bukkit.Material.GRASS_BLOCK, false);
         }
      }
   }

   private void attachWorldEdit(Player player) {
      if (player == null || this.worldEditAttachments.containsKey(player.getUniqueId())) {
         return;
      }
      PermissionAttachment attachment = player.addAttachment(this.plugin);
      attachment.setPermission("worldedit.*", true);
      this.worldEditAttachments.put(player.getUniqueId(), attachment);
   }

   private void detachWorldEdit(Player player) {
      if (player == null) {
         return;
      }
      PermissionAttachment attachment = this.worldEditAttachments.remove(player.getUniqueId());
      if (attachment != null) {
         player.removeAttachment(attachment);
      }
   }

   private String worldPrefix() {
      String configured = this.plugin.getConfig().getString("build-world.world-prefix", WORLD_PREFIX);
      return configured == null || configured.isBlank() ? WORLD_PREFIX : configured;
   }

   private String worldName(UUID ownerId) {
      return this.worldPrefix() + ownerId.toString().replace("-", "");
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onTeleport(PlayerTeleportEvent event) {
      World destination = event.getTo() == null ? null : event.getTo().getWorld();
      if (this.isBuildWorld(destination) && !event.getPlayer().hasPermission("mifron.admin") && !this.isOwner(event.getPlayer(), destination)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§c他プレイヤーのBuildワールドには入れません。");
      }
   }

   @EventHandler
   public void onChangedWorld(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      if (this.isBuildWorld(player.getWorld())) {
         if (player.hasPermission("mifron.admin") || this.isOwner(player, player.getWorld())) {
            this.attachWorldEdit(player);
         } else {
            this.exit(player);
         }
      } else {
         this.detachWorldEdit(player);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      if (this.isBuildWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("mifron.admin") && !this.isOwner(event.getPlayer(), event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      if (this.isBuildWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission("mifron.admin") && !this.isOwner(event.getPlayer(), event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.isBuildWorld(event.getPlayer().getWorld())) {
         if (event.getPlayer().hasPermission("mifron.admin") || this.isOwner(event.getPlayer(), event.getPlayer().getWorld())) {
            this.attachWorldEdit(event.getPlayer());
         } else {
            this.exit(event.getPlayer());
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.detachWorldEdit(event.getPlayer());
   }
}
