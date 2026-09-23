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
      return Math.max(0, this.plugin.getConfig().getInt("main-world.spawn-protect-radius", 30));
   }

   /** No-edit circle around the world spawn (horizontal X,Z only). */
   boolean insideNoEditZone(World world, int x, int z) {
      if (world == null) return false;
      Location spawn = world.getSpawnLocation();
      return insideSpawnRadius(x, z, spawn.getBlockX(), spawn.getBlockZ(), this.noEditRadius());
   }

   boolean isMainWorld(World world) {
      return this.enabled() && world != null && this.worldName().equalsIgnoreCase(world.getName());
   }

   // ------------------------------------------------------------------
   // Semi-creative: flight, creative inventory, consumption, spawn items.
   // ------------------------------------------------------------------

   /**
    * Blocks that must never be taken from the creative inventory in main
    * (griefing, unbreakable or technical blocks).
    */
   static boolean isCreativeDeniedBlock(Material material) {
      if (material == null) return true;
      String name = material.name();
      if (name.startsWith("INFESTED_")) return true;
      return name.contains("COMMAND") || name.startsWith("LEGACY_")
         || switch (name) {
            case "TNT", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "BEDROCK", "BARRIER",
               "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW",
               "END_PORTAL_FRAME", "DRAGON_EGG", "REINFORCED_DEEPSLATE",
               "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "MOVING_PISTON",
               "DEBUG_STICK", "KNOWLEDGE_BOOK" -> true;
            default -> false;
         };
   }

   /**
    * Items obtainable from the creative inventory in main: armor stands plus
    * ordinary blocks (minus the denylist above). Spawn eggs, buckets,
    * projectiles and other entity/item goods stay unavailable.
    */
   static boolean isCreativeTakeAllowed(Material material, java.util.Set<String> extraAllowed) {
      if (material == null) return false;
      try {
         if (material.isAir()) return false;
      } catch (Throwable registryMissing) {
         if (material.name().endsWith("_AIR")) return false;
      }
      if (material == Material.ARMOR_STAND) return true;
      if (extraAllowed != null && extraAllowed.contains(material.name())) return true;
      try {
         return material.isBlock() && !isCreativeDeniedBlock(material);
      } catch (Throwable registryMissing) {
         return false;
      }
   }

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

   /**
    * Semi-creative flight: survival/adventure players can fly in main.
    * Leaving main restores vanilla rules so other worlds are unaffected.
    */
   private void applyFlight(Player player) {
      if (player == null) return;
      if (this.isMainWorld(player.getWorld())) {
         GameMode mode = player.getGameMode();
         if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setAllowFlight(true);
         }
         return;
      }
      GameMode mode = player.getGameMode();
      if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isOp()
         && !player.hasPermission("mifron.admin")) {
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

   java.util.Set<String> creativeExtraAllowed() {
      java.util.Set<String> extra = new java.util.HashSet<>();
      for (String raw : this.plugin.getConfig().getStringList("main-world.creative-allow-extra")) {
         if (raw != null && !raw.isBlank()) extra.add(raw.trim().toUpperCase(java.util.Locale.ROOT));
      }
      return extra;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onCreativeInventory(InventoryCreativeEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (!this.isMainWorld(player.getWorld())) return;
      // Pickup, place and hotbar-swap shapes: the wanted item is always in
      // either the cursor or the clicked slot (getCurrentItem).
      java.util.Set<String> extra = this.creativeExtraAllowed();
      boolean allowed = false;
      for (ItemStack item : new ItemStack[]{event.getCursor(), event.getCurrentItem()}) {
         if (item != null && isCreativeTakeAllowed(item.getType(), extra)) {
            allowed = true;
            break;
         }
      }
      if (!allowed) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onConsumeNoUse(PlayerItemConsumeEvent event) {
      if (!this.isMainWorld(event.getPlayer().getWorld())) return;
      // Using items in main never consumes them: refund one next tick.
      ItemStack consumed = event.getItem().clone();
      consumed.setAmount(1);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         Player player = event.getPlayer();
         if (!player.isOnline()) return;
         player.getInventory().addItem(consumed);
      }, 1L);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onNoFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player)) return;
      if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
      if (!this.isMainWorld(event.getEntity().getWorld())) return;
      event.setCancelled(true);
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
      // Bedrock can never be placed.
      if (block.getType() == Material.BEDROCK) {
         event.setCancelled(true);
         player.sendMessage("§c岩盤は設置できません。");
         return;
      }
      // No-edit circle around spawn.
      if (this.insideNoEditZone(block.getWorld(), block.getX(), block.getZ())) {
         event.setCancelled(true);
         player.sendMessage("§cスポーンから" + this.noEditRadius() + "ブロック以内には設置できません。");
         return;
      }
      if (player.hasPermission("mifron.admin")) return;
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
      // Bedrock can never be broken, by anyone.
      if (block.getType() == Material.BEDROCK) {
         event.setCancelled(true);
         return;
      }
      if (this.insideNoEditZone(block.getWorld(), block.getX(), block.getZ())) {
         event.setCancelled(true);
         player.sendMessage("§cスポーンから" + this.noEditRadius() + "ブロック以内は編集できません。");
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

   // ------------------------------------------------------------------
   // Schematic submissions: players file schematics + coordinates, admins
   // approve (auto-paste) or reject. Data persists in data.yml.
   // ------------------------------------------------------------------

   private int nextSubmissionId() {
      int next = Math.max(1, this.plugin.data().getInt("main-submission-next-id", 1));
      this.plugin.data().set("main-submission-next-id", next + 1);
      this.plugin.queueDataSave();
      return next;
   }

   /** Validates and records a submission. Returns id (>0) or a negative error code. */
   int submitBuild(java.util.UUID player, String fileName, double x, double y, double z) {
      if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return -1;
      java.io.File dir = new java.io.File("plugins/WorldEdit/schematics");
      java.io.File file = new java.io.File(dir, fileName);
      if (!file.isFile()) return -2;
      if (y < -64.0 || y > 320.0) return -3;
      int id = this.nextSubmissionId();
      String base = "main-submissions." + id + ".";
      this.plugin.data().set(base + "player", player.toString());
      this.plugin.data().set(base + "file", fileName);
      this.plugin.data().set(base + "x", x);
      this.plugin.data().set(base + "y", y);
      this.plugin.data().set(base + "z", z);
      this.plugin.data().set(base + "status", "pending");
      this.plugin.data().set(base + "created-at", System.currentTimeMillis());
      this.plugin.queueDataSave();
      return id;
   }

   java.util.List<String> submissionLines(boolean adminOnly, String viewerUuid) {
      java.util.List<String> lines = new java.util.ArrayList<>();
      var section = this.plugin.data().getConfigurationSection("main-submissions");
      if (section == null) return lines;
      for (String id : section.getKeys(false)) {
         String base = "main-submissions." + id + ".";
         String owner = this.plugin.data().getString(base + "player", "");
         if (!adminOnly && !owner.equals(viewerUuid)) continue;
         lines.add("#" + id + " " + this.plugin.data().getString(base + "file", "?")
            + " (" + this.plugin.data().getDouble(base + "x") + ", " + this.plugin.data().getDouble(base + "y")
            + ", " + this.plugin.data().getDouble(base + "z") + ") [" + this.plugin.data().getString(base + "status", "?") + "]");
      }
      return lines;
   }

   /** Approves a submission: pastes via WorldEdit (reflection, no dep) and marks approved. */
   String approveSubmission(int id) {
      String base = "main-submissions." + id + ".";
      if (!this.plugin.data().contains(base + "file")) return "申請 #" + id + " が見つかりません。";
      if (!"pending".equals(this.plugin.data().getString(base + "status", ""))) {
         return "申請 #" + id + " は承認待ちではありません。";
      }
      String fileName = this.plugin.data().getString(base + "file", "");
      double x = this.plugin.data().getDouble(base + "x");
      double y = this.plugin.data().getDouble(base + "y");
      double z = this.plugin.data().getDouble(base + "z");
      World world = Bukkit.getWorld(this.worldName());
      if (world == null) return "mainワールドが見つかりません。";
      String pasted = this.pasteSchematic(world, new java.io.File("plugins/WorldEdit/schematics", fileName), x, y, z);
      if (pasted == null) {
         return "自動設置に失敗しました。手動で //schem load " + fileName + " → //paste -o " + (int) x + "," + (int) y + "," + (int) z + " を実行してください。";
      }
      this.plugin.data().set(base + "status", "approved");
      this.plugin.queueDataSave();
      return "申請 #" + id + " を承認し設置しました（" + pasted + "ブロック）。";
   }

   String rejectSubmission(int id) {
      String base = "main-submissions." + id + ".";
      if (!this.plugin.data().contains(base + "file")) return "申請 #" + id + " が見つかりません。";
      this.plugin.data().set(base + "status", "rejected");
      this.plugin.queueDataSave();
      return "申請 #" + id + " を却下しました。";
   }

   /**
    * Pastes a schematic with WorldEdit through reflection only (no compile
    * dependency). Returns the affected-block description, or null when WorldEdit
    * is missing/incompatible — the caller then falls back to manual //paste.
    */
   private String pasteSchematic(World world, java.io.File file, double x, double y, double z) {
      try {
         Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
         Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);
         Class<?> formatsClass;
         try {
            formatsClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
         } catch (ClassNotFoundException first) {
            formatsClass = Class.forName("com.sk89q.worldedit.extension.factory.ClipboardFormats");
         }
         Object format = formatsClass.getMethod("findByFile", java.io.File.class).invoke(null, file);
         if (format == null) return null;
         Class<?> formatClass = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat");
         Object reader = formatClass.getMethod("getReader", java.io.InputStream.class)
            .invoke(format, new java.io.FileInputStream(file));
         Object clipboard;
         try {
            clipboard = reader.getClass().getMethod("read").invoke(reader);
         } finally {
            try { reader.getClass().getMethod("close").invoke(reader); } catch (Throwable ignored) {
            }
         }
         Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
         Object bWorld = bukkitAdapter.getMethod("adapt", World.class).invoke(null, world);
         Object editSessionBuilder = worldEditClass.getMethod("newEditSessionBuilder").invoke(worldEdit);
         Object builder = editSessionBuilder.getClass().getMethod("world", Class.forName("com.sk89q.worldedit.world.World")).invoke(editSessionBuilder, bWorld);
         Object session = builder.getClass().getMethod("build").invoke(builder);
         try {
            Class<?> holderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            Object holder = holderClass.getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard")).newInstance(clipboard);
            Object pasteBuilder = holderClass.getMethod("createPaste", Class.forName("com.sk89q.worldedit.EditSession")).invoke(holder, session);
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object to = vectorClass.getMethod("at", double.class, double.class, double.class).invoke(null, x, y, z);
            Object operation = pasteBuilder.getClass().getMethod("to", vectorClass).invoke(pasteBuilder, to);
            Object built = operation.getClass().getMethod("build").invoke(operation);
            Class.forName("com.sk89q.worldedit.function.operation.Operations").getMethod("complete", Class.forName("com.sk89q.worldedit.function.operation.Operation")).invoke(null, built);
            Object affected = built.getClass().getMethod("getAffected").invoke(built);
            try {
               session.getClass().getMethod("close").invoke(session);
            } catch (Throwable ignored) {
            }
            return String.valueOf(affected);
         } catch (Throwable operationFailed) {
            try {
               session.getClass().getMethod("cancel").invoke(session);
            } catch (Throwable ignored) {
            }
            try {
               session.getClass().getMethod("close").invoke(session);
            } catch (Throwable ignored) {
            }
            return null;
         }
      } catch (Throwable unavailable) {
         return null;
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
