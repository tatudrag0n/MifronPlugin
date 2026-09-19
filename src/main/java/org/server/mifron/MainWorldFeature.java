package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * "Semi-creative" rules for the main world. Players stay in Survival and may
 * place blocks, but may only break blocks they placed themselves. Ownership
 * (world, x, y, z, placer UUID) persists in data.yml across restarts.
 *
 * Placement is additionally refused inside the spawn protection radius and on
 * X,Z columns already claimed by another player.
 */
final class MainWorldFeature implements Listener {
   private final Mifron plugin;
   private final Map<BlockKey, String> owners = new HashMap<>();
   private final Map<String, ColumnState> columns = new HashMap<>();

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
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("main-world.enabled", true);
   }

   String worldName() {
      String name = this.plugin.getConfig().getString("main-world.name", "main");
      return name == null || name.isBlank() ? "main" : name;
   }

   int spawnProtectRadius() {
      return Math.max(0, this.plugin.getConfig().getInt("main-world.spawn-protect-radius", 30));
   }

   private boolean isMainWorld(World world) {
      return this.enabled() && world != null && this.worldName().equalsIgnoreCase(world.getName());
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
      Block block = event.getBlockPlaced();
      if (block == null || !this.isMainWorld(block.getWorld())) return;
      Player player = event.getPlayer();
      // Spawn protection: horizontal X,Z distance only.
      Location spawn = block.getWorld().getSpawnLocation();
      if (insideSpawnRadius(block.getX(), block.getZ(), spawn.getBlockX(), spawn.getBlockZ(), this.spawnProtectRadius())) {
         event.setCancelled(true);
         player.sendMessage("§cスポーン地点から" + this.spawnProtectRadius() + "ブロック以内には設置できません。");
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
      this.owners.put(new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()), uuid);
      ColumnState owned = this.columns.computeIfAbsent(columnKey, ignored -> new ColumnState());
      owned.owner = uuid;
      owned.count++;
      this.persist();
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      Block block = event.getBlock();
      if (!this.isMainWorld(block.getWorld())) return;
      Player player = event.getPlayer();
      String owner = this.owners.get(new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
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
      this.owners.remove(new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
      String columnKey = columnKey(block.getWorld().getName(), block.getX(), block.getZ());
      ColumnState column = this.columns.get(columnKey);
      if (column != null) {
         column.count--;
         if (column.count <= 0) this.columns.remove(columnKey);
      }
      this.persist();
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onEntityExplode(EntityExplodeEvent event) {
      if (event.blockList().isEmpty()) return;
      boolean changed = false;
      var iterator = event.blockList().iterator();
      while (iterator.hasNext()) {
         Block block = iterator.next();
         if (!this.isMainWorld(block.getWorld())) continue;
         BlockKey key = new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
         if (!this.owners.containsKey(key)) continue;
         // Owned blocks never explode away (and the record is kept so the
         // column claim survives); natural blocks follow vanilla rules.
         iterator.remove();
         changed = true;
      }
      if (changed) this.persist();
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

   static final class ColumnState {
      String owner = "";
      int count;
   }
}
