package org.server.mifron;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

final class ChunkProtectionFeature implements Listener {
   private static final String PROTECTION_FILE = "chunk-protection.yml";
   private final Mifron plugin;
   private final Map<UUID, String> lastChunkWarning = new ConcurrentHashMap<>();
   private org.bukkit.scheduler.BukkitTask scanTask;

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
         this.recordBaseBlock(event.getPlayer(), event.getBlock().getChunk(), event.getBlock().getType(), -1);
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

         this.recordBaseBlock(event.getPlayer(), event.getBlockPlaced().getChunk(), event.getBlockPlaced().getType(), 1);
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

   @EventHandler
   public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
      this.lastChunkWarning.remove(event.getPlayer().getUniqueId());
   }

   void handleRegenCommand(CommandSender sender, String[] args) {
      if (this.plugin.hasPermission(sender, "mifron.admin.regen.force") && sender instanceof Player player) {
         if (args.length >= 2 && ("allow".equalsIgnoreCase(args[1]) || "deny".equalsIgnoreCase(args[1]))) {
            if (!"survival".equalsIgnoreCase(player.getWorld().getName())) {
               sender.sendMessage(ChatColor.RED + "自然再生成の許可リストを設定できるのはSurvivalワールド内だけです。");
               return;
            }
            String key = this.chunkKey(player.getLocation().getChunk());
            java.util.List<String> excluded = new java.util.ArrayList<>(this.plugin.getConfig().getStringList("regen.excluded-chunks"));
            boolean allow = "allow".equalsIgnoreCase(args[1]);
            if (allow && excluded.remove(key)) {
               this.plugin.getConfig().set("regen.excluded-chunks", excluded);
               this.plugin.saveConfig();
            } else if (!allow && !excluded.contains(key)) {
               excluded.add(key);
               this.plugin.getConfig().set("regen.excluded-chunks", excluded);
               this.plugin.saveConfig();
            }
            sender.sendMessage((allow ? ChatColor.GREEN + "自然再生成を許可しました: " : ChatColor.YELLOW + "自然再生成の許可を解除しました: ") + key);
            return;
         }
         if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            java.util.List<String> excluded = this.plugin.getConfig().getStringList("regen.excluded-chunks");
            sender.sendMessage(ChatColor.GREEN + "自然再生成の除外チャンク: " + (excluded.isEmpty() ? "なし" : String.join(" / ", excluded)));
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
                  if (!chunk.isLoaded()) {
                     // Skip unloaded chunks to avoid a synchronous load spike;
                     // unloaded chunks contain no player builds to preserve.
                     skipped++;
                     continue;
                  }
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

   // ---- Chunk protection / Discord warnings ----

   private boolean chunkProtectionEnabled() {
      return this.plugin.getConfig().getBoolean("chunk-protection.enabled", true);
   }

   private int minBaseBlocks() {
      return Math.max(1, this.plugin.getConfig().getInt("chunk-protection.min-base-blocks", 8));
   }

   private int warnDaysBefore() {
      return Math.max(0, this.plugin.getConfig().getInt("chunk-protection.warn-days-before", 7));
   }

   private int rejectProtectDays() {
      return Math.max(0, this.plugin.getConfig().getInt("chunk-protection.reject-protect-days", 90));
   }

   private int approvedProtectDays() {
      return Math.max(0, this.plugin.getConfig().getInt("chunk-protection.approved-protect-days", 0));
   }

   private int scanIntervalMinutes() {
      return Math.max(1, this.plugin.getConfig().getInt("chunk-protection.scan-interval-minutes", 60));
   }

   private long regenIntervalMillis() {
      long days = Math.max(1L, Math.min(365L, this.plugin.getConfig().getLong("regen.interval-days", 30L)));
      return days * 86400000L;
   }

   private boolean isBaseBlock(Material material) {
      return material != null
         && (this.isWarningPlacement(material) || material == Material.BEACON || material == Material.RESPAWN_ANCHOR || material == Material.LODESTONE);
   }

   private void recordBaseBlock(Player player, Chunk chunk, Material material, int delta) {
      if (player == null || chunk == null || chunk.getWorld() == null) return;
      if (!"survival".equalsIgnoreCase(chunk.getWorld().getName()) || !this.isBaseBlock(material)) return;
      String path = this.baseBlockPath(chunk) + "." + player.getUniqueId();
      int next = this.plugin.data().getInt(path, 0) + delta;
      if (next <= 0) this.plugin.data().set(path, null);
      else this.plugin.data().set(path, next);
      this.plugin.queueDataSave();
   }

   private String baseBlockPath(Chunk chunk) {
      return "chunk-blocks." + chunk.getWorld().getName() + "." + chunk.getX() + "_" + chunk.getZ();
   }

   private String protectionPath(Chunk chunk) {
      return "chunk-protect." + chunk.getWorld().getName() + "." + chunk.getX() + "_" + chunk.getZ();
   }

   private boolean isChunkProtected(Chunk chunk) {
      return chunk != null && this.isChunkProtected(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
   }

   private boolean isChunkProtected(String worldName, int x, int z) {
      String path = "chunk-protect." + worldName + "." + x + "_" + z;
      if (!this.plugin.data().contains(path + ".until")) return false;
      long until = this.plugin.data().getLong(path + ".until", 0L);
      return until < 0L || until > System.currentTimeMillis();
   }

   private void protectChunk(Chunk chunk, String mode, long until) {
      String path = this.protectionPath(chunk);
      this.plugin.data().set(path + ".mode", mode);
      this.plugin.data().set(path + ".until", until);
      this.plugin.data().set(path + ".protected-at", System.currentTimeMillis());
      this.plugin.saveData();
   }

   private void unprotectChunk(Chunk chunk) {
      this.plugin.data().set(this.protectionPath(chunk), null);
      this.plugin.saveData();
   }

   void shutdown() {
      if (this.scanTask != null) {
         this.scanTask.cancel();
         this.scanTask = null;
      }
   }

   void scheduleScan() {
      if (this.scanTask != null) {
         this.scanTask.cancel();
      }
      if (!this.chunkProtectionEnabled()) return;
      long interval = this.scanIntervalMinutes() * 60L * 20L;
      this.scanTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::runProtectionScan, interval, interval);
   }

   void runProtectionScan() {
      if (!this.chunkProtectionEnabled()) return;
      World world = Bukkit.getWorld(this.plugin.getConfig().getString("survival-dimensions.overworld", "survival"));
      if (world == null) return;
      long nextRunAt = this.plugin.data().getLong("regen.next-run-at", 0L);
      if (nextRunAt <= 0L) return;
      long warnAt = nextRunAt - this.warnDaysBefore() * 86400000L;
      if (System.currentTimeMillis() < warnAt) return;

      ConfigurationSection chunks = this.plugin.data().getConfigurationSection("chunk-blocks." + world.getName());
      if (chunks == null) return;

      YamlConfiguration file = this.loadProtectionFile();
      boolean changed = false;
      for (String key : chunks.getKeys(false)) {
         ConfigurationSection owners = chunks.getConfigurationSection(key);
         if (owners == null) continue;
         int total = 0;
         Map<String, Integer> counts = new LinkedHashMap<>();
         for (String owner : owners.getKeys(false)) {
            int count = owners.getInt(owner, 0);
            if (count <= 0) continue;
            total += count;
            counts.put(owner, count);
         }
         if (total < this.minBaseBlocks()) continue;
         String[] coordinates = key.split("_", 2);
         if (coordinates.length != 2) continue;
         int x;
         int z;
         try {
            x = Integer.parseInt(coordinates[0]);
            z = Integer.parseInt(coordinates[1]);
         } catch (NumberFormatException ignored) {
            continue;
         }
         if (this.isChunkProtected(world.getName(), x, z)) continue;
         String warnedPath = "chunk-warn." + world.getName() + "." + key + ".warned-at";
         if (this.plugin.data().getLong(warnedPath, 0L) >= warnAt) continue;

         String id = "cp_" + System.currentTimeMillis() + "_" + key.replace('-', 'n');
         file.set("warnings." + id + ".world", world.getName());
         file.set("warnings." + id + ".x", x);
         file.set("warnings." + id + ".z", z);
         file.set("warnings." + id + ".base-blocks", total);
         file.set("warnings." + id + ".regen-at", nextRunAt);
         file.set("warnings." + id + ".status", "pending");
         file.set("warnings." + id + ".owners", new ArrayList<>(counts.keySet()));
         this.plugin.data().set(warnedPath, System.currentTimeMillis());
         changed = true;
      }
      if (changed) {
         this.saveProtectionFile(file);
         this.plugin.saveData();
      }
   }

   void handleProtectCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Player only.");
         return;
      }
      if (args.length < 2 || !"chunk".equalsIgnoreCase(args[1])) {
         player.sendMessage(ChatColor.YELLOW + "/mf protect chunk");
         return;
      }
      Chunk chunk = player.getLocation().getChunk();
      if (!"survival".equalsIgnoreCase(chunk.getWorld().getName())) {
         player.sendMessage(ChatColor.RED + "\u3053\u306e\u30b3\u30de\u30f3\u30c9\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u7528\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      if (this.isChunkProtected(chunk)) {
         player.sendMessage(ChatColor.YELLOW + "\u3053\u306e\u30c1\u30e3\u30f3\u30af\u306f\u3059\u3067\u306b\u4fdd\u8b77\u3055\u308c\u3066\u3044\u307e\u3059\u3002");
         return;
      }
      YamlConfiguration file = this.loadProtectionFile();
      String id = "cp_" + System.currentTimeMillis() + "_" + chunk.getX() + "n" + chunk.getZ();
      String base = "proposals." + id;
      file.set(base + ".world", chunk.getWorld().getName());
      file.set(base + ".x", chunk.getX());
      file.set(base + ".z", chunk.getZ());
      file.set(base + ".requester", player.getUniqueId().toString());
      file.set(base + ".requester-name", player.getName());
      file.set(base + ".status", "pending");
      file.set(base + ".created-at", System.currentTimeMillis());
      this.saveProtectionFile(file);
      player.sendMessage(ChatColor.GREEN + "\u30c1\u30e3\u30f3\u30af\u4fdd\u8b77\u306e\u63d0\u6848\u3092Discord\u3078\u9001\u4fe1\u3057\u307e\u3057\u305f\u3002\u7ba1\u7406\u8005\u306e\u627f\u8a8d\u3092\u304a\u5f85\u3061\u304f\u3060\u3055\u3044\u3002");
   }

   void handleChunkProtectCommand(CommandSender sender, String[] args) {
      if (!this.plugin.hasPermission(sender, "mifron.admin")) {
         sender.sendMessage(ChatColor.RED + "\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (args.length < 4 || !"apply".equalsIgnoreCase(args[1])) {
         sender.sendMessage(ChatColor.YELLOW + "/mf chunkprotect apply <id> <decision>");
         return;
      }
      String id = args[2];
      String decision = args[3].toLowerCase(java.util.Locale.ROOT);
      YamlConfiguration file = this.loadProtectionFile();
      String kind = null;
      ConfigurationSection entry = file.getConfigurationSection("warnings." + id);
      if (entry != null) kind = "warnings";
      if (entry == null) {
         entry = file.getConfigurationSection("proposals." + id);
         if (entry != null) kind = "proposals";
      }
      if (entry == null) {
         sender.sendMessage(ChatColor.RED + "\u5bfe\u8c61\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + id);
         return;
      }
      World world = Bukkit.getWorld(entry.getString("world", "survival"));
      if (world == null) {
         sender.sendMessage(ChatColor.RED + "\u30ef\u30fc\u30eb\u30c9\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      Chunk chunk = world.getChunkAt(entry.getInt("x", 0), entry.getInt("z", 0));
      boolean protect;
      long until;
      if ("reject".equals(decision)) {
         protect = true;
         until = this.durationUntil(this.rejectProtectDays());
      } else if ("approve".equals(decision)) {
         protect = true;
         until = this.durationUntil(this.approvedProtectDays());
      } else {
         protect = false;
         until = 0L;
      }
      if (protect) {
         this.protectChunk(chunk, decision, until);
      } else {
         this.unprotectChunk(chunk);
         this.plugin.data().set("chunk-warn." + world.getName() + "." + chunk.getX() + "_" + chunk.getZ() + ".warned-at", null);
      }
      file.set(kind + "." + id + ".status", protect ? decision : "denied");
      file.set(kind + "." + id + ".decision", decision);
      file.set(kind + "." + id + ".decided-at", System.currentTimeMillis());
      this.saveProtectionFile(file);
      sender.sendMessage(ChatColor.GREEN + "\u30c1\u30e3\u30f3\u30af\u4fdd\u8b77\u306e\u6c7a\u5b9a\u3092\u53cd\u6620\u3057\u307e\u3057\u305f: " + id + " / " + decision);
   }

   private long durationUntil(int days) {
      return days <= 0 ? -1L : System.currentTimeMillis() + days * 86400000L;
   }

   private File protectionFile() {
      return new File(this.plugin.getDataFolder(), PROTECTION_FILE);
   }

   private YamlConfiguration loadProtectionFile() {
      File file = this.protectionFile();
      return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
   }

   private void saveProtectionFile(YamlConfiguration config) {
      try {
         if (!this.plugin.getDataFolder().exists()) this.plugin.getDataFolder().mkdirs();
         config.save(this.protectionFile());
      } catch (IOException error) {
         this.plugin.getLogger().warning("Could not save " + PROTECTION_FILE + ": " + error.getMessage());
      }
   }

   void handleChunkCommand(Player player) {
      this.sendChunkInfo(player, player.getLocation().getChunk(), false);
   }

   void runMonthlyMaintenance() {
      World world = Bukkit.getWorld(this.plugin.getConfig().getString("survival-dimensions.overworld", "survival"));
      if (world == null) {
         this.plugin.getLogger().warning("Monthly regeneration skipped: Survival world is not loaded.");
         return;
      }
      int limit = Math.max(1, this.plugin.getConfig().getInt("regen.monthly-max-chunks", 256));
      int regenerated = 0;
      for (Chunk chunk : world.getLoadedChunks()) {
         if (regenerated >= limit) break;
         if (this.isChunkProtected(chunk)) continue;
         if (this.isChunkRegenerationAllowed(chunk) && this.regenerateChunkNow(Bukkit.getConsoleSender(), chunk)) {
            regenerated++;
         }
      }
      this.plugin.data().set("regen.next-run-at", System.currentTimeMillis() + this.regenIntervalMillis());
      this.plugin.saveData();
      this.plugin.getLogger().info("Monthly Survival regeneration completed: " + regenerated + " chunk(s).");
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
      return chunk != null && chunk.isLoaded()
         && (this.isCentralProtectedChunk(chunk) || this.isConfiguredProtectedChunk(chunk));
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
      return !this.plugin.getConfig().getStringList("regen.excluded-chunks").contains(this.chunkKey(chunk));
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
      if (this.isChunkRegenerationSafe(chunk) || this.isChunkProtected(chunk)) {
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
      try {
         java.lang.reflect.Method method = chunk.getWorld().getClass().getMethod("regenerateChunk", int.class, int.class);
         Object result = method.invoke(chunk.getWorld(), chunk.getX(), chunk.getZ());
         return !(result instanceof Boolean) || (Boolean)result;
      } catch (ReflectiveOperationException | RuntimeException error) {
         if (sender != Bukkit.getConsoleSender()) {
            sender.sendMessage(ChatColor.RED + "このPaper APIではチャンク再生成を実行できません。");
          }
          this.plugin.getLogger().warning("Chunk regeneration is unavailable for " + this.chunkKey(chunk) + ": " + error.getMessage());
          return false;
      }
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
