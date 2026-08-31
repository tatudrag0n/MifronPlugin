package org.server.mifron;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

final class ChunkProtectionFeature implements Listener {
   private final Mifron plugin;
   private final Map<UUID, String> lastChunkWarning = new ConcurrentHashMap<>();

   ChunkProtectionFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.plugin.isShopBlock(event.getBlock())) {
         event.setCancelled(true);
         event.setDropItems(false);
         event.setExpToDrop(0);
         event.getPlayer().sendMessage(ChatColor.YELLOW + "ショップ化されたブロックはショップワンドで解除してください。");
      } else if (!this.plugin.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
         event.setCancelled(true);
         event.setDropItems(false);
         event.setExpToDrop(0);
         event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
      } else {
         this.plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-broken", 1);
      }
   }

   @EventHandler
   public void onBlockPlace(BlockPlaceEvent event) {
      Location location = event.getBlockPlaced().getLocation();
      if (!this.plugin.canBuild(event.getPlayer(), location)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
      } else if (this.isSurvivalHazardBlocked(location, event.getBlockPlaced().getType())
         && !this.isProtectionAdmin(event.getPlayer())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + this.survivalHazardMessage(event.getBlockPlaced().getType()));
      } else {
         if (this.isWarningPlacement(event.getBlockPlaced().getType()) && this.isChunkRegenerationAllowed(event.getBlockPlaced().getChunk())) {
            this.sendChunkWarning(event.getPlayer(), event.getBlockPlaced().getChunk());
         }

         this.plugin.addPlayerStat(event.getPlayer().getUniqueId(), "total-blocks-placed", 1);
      }
   }

   @EventHandler
   public void onEntityExplode(EntityExplodeEvent event) {
      event.blockList().removeIf(this.plugin::isShopBlock);
   }

   @EventHandler
   public void onBlockExplode(BlockExplodeEvent event) {
      event.blockList().removeIf(this.plugin::isShopBlock);
   }

   void handleRegenCommand(CommandSender sender, String[] args) {
      if (this.plugin.hasPermission(sender, "mifron.admin.regen.force") && sender instanceof Player player) {
         if (args.length >= 2 && ("allow".equalsIgnoreCase(args[1]) || "deny".equalsIgnoreCase(args[1]))) {
            if (!"survival".equalsIgnoreCase(player.getWorld().getName())) {
               sender.sendMessage(ChatColor.RED + "自然再生成の許可リストを設定できるのはSurvivalワールド内だけです。");
               return;
            }
            String key = this.chunkKey(player.getLocation().getChunk());
            java.util.List<String> allowed = new java.util.ArrayList<>(this.plugin.getConfig().getStringList("regen.allowed-chunks"));
            boolean allow = "allow".equalsIgnoreCase(args[1]);
            if (allow && !allowed.contains(key)) {
               allowed.add(key);
               this.plugin.getConfig().set("regen.allowed-chunks", allowed);
               this.plugin.saveConfig();
            } else if (!allow && allowed.remove(key)) {
               this.plugin.getConfig().set("regen.allowed-chunks", allowed);
               this.plugin.saveConfig();
            }
            sender.sendMessage((allow ? ChatColor.GREEN + "自然再生成を許可しました: " : ChatColor.YELLOW + "自然再生成の許可を解除しました: ") + key);
            return;
         }
         if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            java.util.List<String> allowed = this.plugin.getConfig().getStringList("regen.allowed-chunks");
            sender.sendMessage(ChatColor.GREEN + "自然再生成の許可チャンク: " + (allowed.isEmpty() ? "なし" : String.join(" / ", allowed)));
            return;
         }
         int radiusArgIndex = args.length >= 2 && "force".equalsIgnoreCase(args[1]) ? 2 : 1;
         if (args.length > radiusArgIndex && !args[radiusArgIndex].matches("\\d+")) {
            sender.sendMessage(ChatColor.RED + "/mifron regen [radius]");
            sender.sendMessage(ChatColor.GRAY + "例: /mifron regen 0, /mifron regen 2");
         } else {
            int radius = args.length > radiusArgIndex ? Math.min(8, this.parsePositiveInt(args[radiusArgIndex], 0)) : 0;
            Chunk center = player.getLocation().getChunk();
            int count = 0;
            int skipped = 0;

            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
               for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                  Chunk chunk = center.getWorld().getChunkAt(x, z);
                  if (this.regenerateChunkNow(sender, chunk)) {
                     count++;
                  } else {
                     skipped++;
                  }
               }
            }

            this.plugin.saveData();
            sender.sendMessage(ChatColor.GREEN + "強制自然再生を実行しました: " + count + "チャンク / 保護・対象外・失敗: " + skipped + "チャンク");
         }
      }
   }

   void handleChunkCommand(Player player) {
      this.sendChunkInfo(player, player.getLocation().getChunk(), false);
   }

   private void sendChunkInfo(Player player, Chunk chunk, boolean admin) {
      String key = this.chunkKey(chunk);
      player.sendMessage(ChatColor.GREEN + "チャンク情報: " + key);
      player.sendMessage(ChatColor.GRAY + "自然再生成許可: " + (this.isChunkRegenerationAllowed(chunk) ? "はい" : "いいえ"));
      long scheduledAt = this.plugin.data().getLong("regen." + key + ".regenScheduledAt", 0L);
      if (scheduledAt > 0L) {
         player.sendMessage(ChatColor.RED + "自然再生予定: " + this.formatDateTime(scheduledAt));
      } else {
         player.sendMessage(ChatColor.GRAY + "自然再生予定: なし");
      }

      player.sendMessage(ChatColor.GRAY + "再生成回数: " + this.plugin.data().getInt("regen." + key + ".regenCount", 0));
      if (!admin && this.isChunkRegenerationAllowed(chunk)) {
         player.sendMessage(ChatColor.YELLOW + "このチャンクは自然再生成の許可リストに入っています。");
      }
   }

   private boolean isProtected(Player player, Location location) {
      return location != null && !this.isProtectionAdmin(player) && this.isBuildProtectedChunk(location.getChunk());
   }

   boolean isProtectedLocation(Location location) {
      return location != null && this.isBuildProtectedChunk(location.getChunk());
   }

   boolean canBuild(Player player, Location location) {
      return !this.isProtected(player, location);
   }

   boolean isTrusted(Player player, Location location) {
      return player != null && location != null && !this.isProtected(player, location);
   }

   boolean isSpawnProtected(Location location) {
      return location != null && this.isCentralProtectedChunk(location.getChunk());
   }

   private boolean isBuildProtectedChunk(Chunk chunk) {
      return this.isCentralProtectedChunk(chunk) || this.isConfiguredProtectedChunk(chunk);
   }

   private String chunkKey(Chunk chunk) {
      return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
   }

   private boolean isChunkRegenerationSafe(Chunk chunk) {
      return !this.isChunkRegenerationAllowed(chunk);
   }

   private boolean isChunkRegenerationAllowed(Chunk chunk) {
      if (chunk == null || chunk.getWorld() == null || !"survival".equalsIgnoreCase(chunk.getWorld().getName())) {
         return false;
      }
      return this.plugin.getConfig().getStringList("regen.allowed-chunks").contains(this.chunkKey(chunk));
   }

   boolean isSurvivalHazardBlocked(Location location, Material material) {
      if (location == null || location.getWorld() == null || !"survival".equalsIgnoreCase(location.getWorld().getName())) {
         return false;
      }
      if (material == Material.TNT) {
         return this.plugin.getConfig().getBoolean("survival-safety.disable-tnt", true);
      }
      if (material == Material.LAVA || material == Material.LAVA_BUCKET) {
         return this.plugin.getConfig().getBoolean("survival-safety.disable-lava", true);
      }
      return false;
   }

   private boolean isProtectionAdmin(Player player) {
      return player != null && this.plugin.getConfig().getBoolean("survival-safety.allow-admin-bypass", true)
         && (player.hasPermission("mifron.admin") || player.hasPermission("mifron.protect.bypass"));
   }

   private String survivalHazardMessage(Material material) {
      return material == Material.TNT ? "SurvivalではTNTを使用できません。" : "Survivalでは溶岩を使用できません。";
   }

   private boolean isCentralProtectedChunk(Chunk chunk) {
      Location hub = this.getCentralProtectionLocation(chunk.getWorld());
      if (hub != null && hub.getWorld().equals(chunk.getWorld())) {
         int radius = Math.max(4, this.plugin.getConfig().getInt("hub-protection-radius-chunks", 4));
         return Math.abs(chunk.getX() - hub.getChunk().getX()) <= radius && Math.abs(chunk.getZ() - hub.getChunk().getZ()) <= radius;
      } else {
         return false;
      }
   }

   private Location getCentralProtectionLocation(World world) {
      Location hub = this.plugin.readLocation("hub");
      if (hub != null) {
         return hub;
      } else {
         return world == null ? null : world.getSpawnLocation();
      }
   }

   private boolean isConfiguredProtectedChunk(Chunk chunk) {
      String key = this.chunkKey(chunk);
      return this.plugin.getConfig().getStringList("regen.nation-chunks").contains(key)
         || this.plugin.getConfig().getStringList("regen.public-facility-chunks").contains(key)
         || this.plugin.getConfig().getStringList("regen.staff-excluded-chunks").contains(key);
   }

   private boolean isRegenScheduled(Chunk chunk) {
      return this.plugin.data().getLong("regen." + this.chunkKey(chunk) + ".regenScheduledAt", 0L) > 0L;
   }

   private void clearRegenSchedule(Chunk chunk) {
      String path = "regen." + this.chunkKey(chunk);
      this.plugin.data().set(path + ".warned", false);
      this.plugin.data().set(path + ".warnedAt", null);
      this.plugin.data().set(path + ".regenScheduledAt", null);
   }

   private void sendChunkWarning(Player player, Chunk chunk) {
      String key = this.chunkKey(chunk);
      String warningKey = key + ":place";
      if (!warningKey.equals(this.lastChunkWarning.get(player.getUniqueId()))) {
         this.lastChunkWarning.put(player.getUniqueId(), warningKey);
         if (this.isRegenScheduled(chunk)) {
            player.sendMessage(ChatColor.RED + this.plugin.getConfig().getString("messages.scheduledRegen", "このチャンクは自然再生成の許可リストに入っているため、次回メンテナンスの対象です。"));
         } else {
            player.sendMessage(ChatColor.YELLOW + this.plugin.getConfig().getString("messages.unprotectedChunk", "このチャンクは自然再生成の許可リストに入っています。"));
         }

      }
   }

   private boolean isWarningPlacement(Material material) {
      String name = material.name();
      return name.contains("CHEST")
         || name.endsWith("_BED")
         || name.endsWith("CRAFTING_TABLE")
         || name.endsWith("FURNACE")
         || name.endsWith("ANVIL")
         || name.endsWith("BARREL")
         || name.endsWith("SHULKER_BOX")
         || name.endsWith("HOPPER")
         || name.endsWith("FARMLAND");
   }

   private boolean regenerateChunkNow(CommandSender sender, Chunk chunk) {
      if (this.isChunkRegenerationSafe(chunk)) {
         return false;
      }

      long now = System.currentTimeMillis();
      String key = this.chunkKey(chunk);
      int regenCount = this.plugin.data().getInt("regen." + key + ".regenCount", 0) + 1;
      boolean regenerated = this.regenerateChunkUsingSupportedApi(sender, chunk);
      if (!regenerated) {
         return false;
      }

      this.plugin.data().set("regen." + key + ".worldName", chunk.getWorld().getName());
      this.plugin.data().set("regen." + key + ".chunkX", chunk.getX());
      this.plugin.data().set("regen." + key + ".chunkZ", chunk.getZ());
      this.plugin.data().set("regen." + key + ".protected", false);
      this.plugin.data().set("regen." + key + ".regenCount", regenCount);
      this.plugin.data().set("regen." + key + ".lastRegen", now);
      this.plugin.data().set("regen." + key + ".warned", false);
      this.plugin.data().set("regen." + key + ".warnedAt", null);
      this.plugin.data().set("regen." + key + ".regenScheduledAt", null);
      this.plugin.getLogger().info("Force regenerated chunk " + key + " by " + sender.getName());
      return true;
   }

   private boolean regenerateChunkUsingSupportedApi(CommandSender sender, Chunk chunk) {
      sender.sendMessage(ChatColor.RED + "このPaper APIではチャンク再生成はサポートされていません。");
      this.plugin
         .getLogger()
         .warning("Chunk regeneration is disabled because World#regenerateChunk is deprecated for removal and unsupported in this API: " + this.chunkKey(chunk));
      return false;
   }

   private int parsePositiveInt(String value, int fallback) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed >= 0 ? parsed : fallback;
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private String formatDateTime(long millis) {
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
   }
}
