package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * "Semi-creative" rules for the main world plus a block-edit approval flow.
 *
 * <ul>
 *   <li>Players stay in Survival, can fly, take nothing from the creative
 *   inventory (armor stands excepted), never consume items, and cannot use
 *   entity-spawning items.</li>
 *   <li>Placed blocks start as <em>pending</em> (temporary, red mist particles
 *   on the edges, uneditable), survive restarts via data.yml, and become
 *   formally saved (<em>approved</em>, fixed) through
 *   {@code /mf main approve}.</li>
 * </ul>
 *
 * <p>All handlers are main-world scoped; survival/FFA worlds are untouched.
 */
final class MainWorldFeature implements Listener {
   private final Mifron plugin;
   private final Map<BlockKey, String> owners = new HashMap<>();
   private final Map<String, ColumnState> columns = new HashMap<>();
   private final Map<BlockKey, PendingRecord> pending = new HashMap<>();
   private BukkitTask particleTask;

   MainWorldFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.plugin.getConfig().addDefault("main-world.enabled", true);
      this.plugin.getConfig().addDefault("main-world.name", "main");
      this.plugin.getConfig().addDefault("main-world.spawn-protect-radius", 30);
      this.plugin.getConfig().options().copyDefaults(true);
      this.owners.clear();
      this.columns.clear();
      for (String raw : this.plugin.data().getStringList("main-block-ownership")) {
         String[] parts = raw.split(";", -1);
         if (parts.length != 5) continue;
         try {
            BlockKey key = new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            String owner = parts[4];
            if (owner.isBlank()) continue;
            this.owners.put(key, owner);
            ColumnState column = this.columns.computeIfAbsent(key.columnKey(), ignored -> new ColumnState());
            column.owner = owner;
            column.count++;
         } catch (NumberFormatException ignored) {
         }
      }
      this.pending.clear();
      for (String raw : this.plugin.data().getStringList("main-pending-blocks")) {
         String[] parts = raw.split(";", -1);
         if (parts.length != 7) continue;
         try {
            BlockKey key = new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            String owner = parts[4];
            Material material = Material.matchMaterial(parts[5]);
            long placedAt = Long.parseLong(parts[6]);
            if (owner.isBlank() || material == null) continue;
            this.pending.put(key, new PendingRecord(owner, material, placedAt));
         } catch (NumberFormatException ignored) {
         }
      }
   }

   /** Starts (or restarts) the red-mist particle task for pending blocks. */
   void start() {
      if (this.particleTask != null) {
         this.particleTask.cancel();
         this.particleTask = null;
      }
      this.particleTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickPendingParticles, 20L, 20L);
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("main-world.enabled", true);
   }

   String worldName() {
      String name = this.plugin.getConfig().getString("main-world.name", "main");
      return name == null || name.isBlank() ? "main" : name;
   }

   int noEditRadius() {
      return Math.max(0, this.plugin.getConfig().getInt("main-world.spawn-protect-radius", 32));
   }

   /** Fixed no-edit zone centered at 0,0 (horizontal X,Z only). */
   boolean insideNoEditZone(int x, int z) {
      return insideSpawnRadius(x, z, 0, 0, this.noEditRadius());
   }

   boolean isMainWorld(World world) {
      return this.enabled() && world != null && this.worldName().equalsIgnoreCase(world.getName());
   }

   // ------------------------------------------------------------------
   // Semi-creative: flight, creative inventory, consumption, spawn items.
   // ------------------------------------------------------------------

   /** Entity-spawning items banned in main (armor stands are allowed). */
   static boolean isEntitySpawnItem(Material material) {
      if (material == null) return false;
      if (material == Material.ARMOR_STAND) return false;
      String name = material.name();
      return name.endsWith("_SPAWN_EGG")
         || material == Material.SNOWBALL
         || material == Material.EGG
         || material == Material.END_CRYSTAL
         || material == Material.FIRE_CHARGE;
   }

   private void applyFlight(Player player) {
      if (player == null) return;
      if (this.isMainWorld(player.getWorld())) {
         GameMode mode = player.getGameMode();
         if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setAllowFlight(true);
         }
         return;
      }
      // Leaving main: restore vanilla flight rules so other worlds (FFA etc.)
      // are unaffected. Creative/spectator keep their flight.
      GameMode mode = player.getGameMode();
      if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isOp()) {
         player.setFlying(false);
         player.setAllowFlight(false);
      }
   }

   @EventHandler
   public void onJoinFlight(PlayerJoinEvent event) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.applyFlight(event.getPlayer()), 10L);
   }

   @EventHandler
   public void onWorldChangeFlight(PlayerChangedWorldEvent event) {
      this.applyFlight(event.getPlayer());
   }

   @EventHandler
   public void onRespawnFlight(PlayerRespawnEvent event) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.applyFlight(event.getPlayer()), 10L);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onCreativeInventory(InventoryCreativeEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (!this.isMainWorld(player.getWorld())) return;
      ItemStack cursor = event.getCursor();
      ItemStack current = event.getCurrentItem();
      boolean allowed = (cursor != null && cursor.getType() == Material.ARMOR_STAND)
         || (current != null && current.getType() == Material.ARMOR_STAND);
      if (!allowed) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onConsumeNoUse(PlayerItemConsumeEvent event) {
      if (!this.isMainWorld(event.getPlayer().getWorld())) return;
      // Eating/drinking in main never consumes: refund one item next tick.
      ItemStack consumed = event.getItem().clone();
      consumed.setAmount(1);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         Player player = event.getPlayer();
         if (!player.isOnline()) return;
         player.getInventory().addItem(consumed);
      }, 1L);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onBannedInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (!this.isMainWorld(player.getWorld())) return;
      ItemStack item = event.getItem();
      if (item == null && event.getHand() == EquipmentSlot.OFF_HAND) {
         item = player.getInventory().getItemInOffHand();
      } else if (item == null) {
         item = player.getInventory().getItemInMainHand();
      }
      if (item != null && isEntitySpawnItem(item.getType())) {
         event.setCancelled(true);
         player.sendMessage("§cmainワールドではそのアイテムは使用できません。");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBannedProjectile(ProjectileLaunchEvent event) {
      if (!(event.getEntity().getShooter() instanceof Player player)) return;
      if (!this.isMainWorld(player.getWorld())) return;
      switch (event.getEntityType()) {
         case SNOWBALL, EGG, END_CRYSTAL, FIREBALL -> {
            event.setCancelled(true);
            player.sendMessage("§cmainワールドではそのアイテムは使用できません。");
         }
         default -> {}
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onBannedCreatureSpawn(CreatureSpawnEvent event) {
      if (!this.isMainWorld(event.getLocation().getWorld())) return;
      if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
         || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) {
         if (event.getEntityType() == org.bukkit.entity.EntityType.ARMOR_STAND) return;
         event.setCancelled(true);
      }
   }

   // ------------------------------------------------------------------
   // Approval flow: place -> pending (red mist) -> approve -> fixed.
   // ------------------------------------------------------------------

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
      Block block = event.getBlockPlaced();
      if (block == null || !this.isMainWorld(block.getWorld())) return;
      Player player = event.getPlayer();
      if (isEntitySpawnItem(event.getItemInHand().getType())) {
         event.setCancelled(true);
         return;
      }
      // No-edit zone around 0,0: neither placing nor breaking allowed.
      if (this.insideNoEditZone(block.getX(), block.getZ())) {
         event.setCancelled(true);
         player.sendMessage("§c0,0から" + this.noEditRadius() + "ブロック以内には設置できません。");
         return;
      }
      // X,Z column conflict: another player's column is off limits.
      String columnKey = columnKey(block.getWorld().getName(), block.getX(), block.getZ());
      ColumnState column = this.columns.get(columnKey);
      String uuid = player.getUniqueId().toString();
      if (column != null && !column.owner.equals(uuid)) {
         event.setCancelled(true);
         player.sendMessage("§c他のプレイヤーの設置列（X,Z）と重なるため設置できません。");
         return;
      }
      BlockKey key = new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
      Material placedType = block.getType();
      this.pending.put(key, new PendingRecord(uuid, placedType, System.currentTimeMillis()));
      ColumnState owned = this.columns.computeIfAbsent(columnKey, ignored -> new ColumnState());
      owned.owner = uuid;
      owned.count++;
      this.persistPending();
      player.sendMessage("§e設置を承認待ちとして保存しました。承認されるまで編集できません。");
      // Semi-creative: placing never consumes the item; refund one next tick.
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (!player.isOnline()) return;
         player.getInventory().addItem(new ItemStack(placedType, 1));
         player.updateInventory();
      }, 1L);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
      // Semi-creative: digging in main breaks instantly like creative mode.
      // Approval guards still apply at break time, so this never bypasses them.
      if (!(event.getPlayer() instanceof Player player)) return;
      if (!this.isMainWorld(player.getWorld())) return;
      GameMode mode = player.getGameMode();
      if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
         event.setInstaBreak(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Block block = event.getBlock();
      if (!this.isMainWorld(block.getWorld())) return;
      Player player = event.getPlayer();
      BlockKey key = new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
      if (this.insideNoEditZone(block.getX(), block.getZ())) {
         event.setCancelled(true);
         player.sendMessage("§c0,0から" + this.noEditRadius() + "ブロック以内は編集できません。");
         return;
      }
      if (player.hasPermission("mifron.admin")) {
         // Admin moderation breaks drop any approval record so no stale
         // red mist or phantom ownership survives the removed block.
         if (this.pending.remove(key) != null) this.persistPending();
         if (this.owners.remove(key) != null) this.persist();
         return;
      }
      if (this.pending.containsKey(key)) {
         event.setCancelled(true);
         player.sendMessage("§c承認待ちのブロックは編集できません。");
         return;
      }
      String owner = this.owners.get(key);
      if (owner == null) {
         event.setCancelled(true);
         player.sendMessage("§c自然ブロックは破壊できません。自分が設置したブロックのみ破壊できます。");
         return;
      }
      if (!owner.equals(player.getUniqueId().toString())) {
         event.setCancelled(true);
         player.sendMessage("§c他のプレイヤーが設置したブロックは破壊できません。");
         return;
      }
      // Approved blocks are formally saved and permanently fixed.
      event.setCancelled(true);
      player.sendMessage("§c承認済みのブロックは編集できません。");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onEntityExplode(EntityExplodeEvent event) {
      if (event.blockList().isEmpty()) return;
      var iterator = event.blockList().iterator();
      while (iterator.hasNext()) {
         Block block = iterator.next();
         if (!this.isMainWorld(block.getWorld())) continue;
         BlockKey key = new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
         // Pending and approved blocks never explode away.
         if (!this.owners.containsKey(key) && !this.pending.containsKey(key)) continue;
         iterator.remove();
      }
   }

   /** Approves pending blocks. Returns the approved count. */
   int approvePending(String ownerUuidOrNull) {
      List<BlockKey> targets = new ArrayList<>();
      for (Map.Entry<BlockKey, PendingRecord> entry : this.pending.entrySet()) {
         if (ownerUuidOrNull == null || entry.getValue().owner().equals(ownerUuidOrNull)) {
            targets.add(entry.getKey());
         }
      }
      for (BlockKey key : targets) {
         PendingRecord record = this.pending.remove(key);
         this.owners.put(key, record.owner());
      }
      if (!targets.isEmpty()) {
         this.persist();
         this.persistPending();
      }
      return targets.size();
   }

   int pendingCount() {
      return this.pending.size();
   }

   int pendingCount(String ownerUuid) {
      int count = 0;
      for (PendingRecord record : this.pending.values()) {
         if (record.owner().equals(ownerUuid)) count++;
      }
      return count;
   }

   /**
    * A pending record is released once its block is gone or replaced: air
    * (broken/decayed/popped by other means) or a different material ends the
    * approval wait and its red mist.
    */
   static boolean isPendingReleased(Material current, Material placed) {
      // NOTE: no Material#isAir() here — registry-backed calls crash unit tests.
      return current == null || current == Material.AIR
         || current == Material.CAVE_AIR || current == Material.VOID_AIR
         || current != placed;
   }

   private void tickPendingParticles() {
      if (this.pending.isEmpty()) return;
      Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.0f);
      List<BlockKey> stale = new ArrayList<>();
      int shown = 0;
      for (Map.Entry<BlockKey, PendingRecord> entry : this.pending.entrySet()) {
         if (shown++ >= 200) break;
         BlockKey key = entry.getKey();
         World world = Bukkit.getWorld(key.world);
         if (world == null) continue;
         // Release records whose block vanished without a break event.
         if (world.isChunkLoaded(key.x >> 4, key.z >> 4)) {
            Material current = world.getBlockAt(key.x, key.y, key.z).getType();
            if (isPendingReleased(current, entry.getValue().material())) {
               stale.add(key);
               continue;
            }
         }
         // Red mist along the block edges, visible to nearby viewers only.
         double x = key.x + 0.5;
         double y = key.y + 0.5;
         double z = key.z + 0.5;
         for (Player viewer : world.getPlayers()) {
            Location view = viewer.getLocation();
            double dx = view.getX() - x;
            double dy = view.getY() - y;
            double dz = view.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > 48.0 * 48.0) continue;
            for (double edge : new double[] {-0.5, 0.5}) {
               viewer.spawnParticle(Particle.DUST, x + edge, y + edge, z + edge, 2, 0.05, 0.05, 0.05, 0.0, red);
               viewer.spawnParticle(Particle.DUST, x - edge, y + edge, z - edge, 2, 0.05, 0.05, 0.05, 0.0, red);
               viewer.spawnParticle(Particle.DUST, x + edge, y - edge, z - edge, 2, 0.05, 0.05, 0.05, 0.0, red);
               viewer.spawnParticle(Particle.DUST, x - edge, y - edge, z + edge, 2, 0.05, 0.05, 0.05, 0.0, red);
            }
         }
      }
      if (!stale.isEmpty()) {
         for (BlockKey key : stale) this.pending.remove(key);
         this.persistPending();
      }
   }

   private void persist() {
      List<String> rows = new ArrayList<>(this.owners.size());
      for (Map.Entry<BlockKey, String> entry : this.owners.entrySet()) {
         BlockKey key = entry.getKey();
         rows.add(key.world + ";" + key.x + ";" + key.y + ";" + key.z + ";" + entry.getValue());
      }
      this.plugin.data().set("main-block-ownership", rows);
      this.plugin.queueDataSave();
   }

   private void persistPending() {
      List<String> rows = new ArrayList<>(this.pending.size());
      for (Map.Entry<BlockKey, PendingRecord> entry : this.pending.entrySet()) {
         BlockKey key = entry.getKey();
         PendingRecord record = entry.getValue();
         rows.add(key.world + ";" + key.x + ";" + key.y + ";" + key.z + ";" + record.owner()
            + ";" + record.material().name() + ";" + record.placedAt());
      }
      this.plugin.data().set("main-pending-blocks", rows);
      this.plugin.queueDataSave();
   }

   /** Horizontal X,Z distance check; Y is ignored. Uses long math (no overflow). */
   static boolean insideSpawnRadius(int x, int z, int spawnX, int spawnZ, int radius) {
      if (radius <= 0) return false;
      long dx = (long) x - spawnX;
      long dz = (long) z - spawnZ;
      return dx * dx + dz * dz <= (long) radius * radius;
   }

   static String columnKey(String world, int x, int z) {
      return world + ";" + x + ";" + z;
   }

   record BlockKey(String world, int x, int y, int z) {
      String columnKey() {
         return MainWorldFeature.columnKey(this.world, this.x, this.z);
      }
   }

   record PendingRecord(String owner, Material material, long placedAt) {}

   static final class ColumnState {
      String owner = "";
      int count;
   }
}
