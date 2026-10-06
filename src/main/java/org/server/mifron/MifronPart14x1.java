package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart14x1 extends MifronPart14 {
   public Location readLocation(String path) {
      String worldName = this.getConfig().getString(path + ".world");
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world == null) return null;
      Location configured = new Location(world,
         this.getConfig().getDouble(path + ".x"),
         this.getConfig().getDouble(path + ".y"),
         this.getConfig().getDouble(path + ".z"),
         (float) this.getConfig().getDouble(path + ".yaw"),
         (float) this.getConfig().getDouble(path + ".pitch"));
      return this.toSafeLocation(configured);
   }

   /**
    * Lifts a configured location onto the first free standing spot at or above
    * it. Config entries such as warning-servers.* point at (0,0,0), which is
    * solid rock: teleporting there suffocates the player. Players are never
    * placed inside a block or a liquid.
    */
   protected Location toSafeLocation(Location location) {
      if (location == null || location.getWorld() == null) return location;
      World world = location.getWorld();
      if (world.getBlockAt(location).getType().isAir() && this.isStandable(world, location)) return location;

      int x = location.getBlockX();
      int z = location.getBlockZ();
      int top = world.getHighestBlockYAt(x, z);
      int ceiling = world.getMaxHeight() - 2;
      int start = Math.max(location.getBlockY(), Math.min(top, ceiling));

      for (int y = start; y <= ceiling; y++) {
         if (!this.isStandable(world, new Location(world, x, y, z))) continue;
         Location safe = new Location(world, x + 0.5D, y, z + 0.5D, location.getYaw(), location.getPitch());
         return safe;
      }
      // Nothing standable nearby: keep the configured X/Z but use the terrain top.
      return new Location(world, x + 0.5D, top, z + 0.5D, location.getYaw(), location.getPitch());
   }

   private boolean isStandable(World world, Location location) {
      Location feet = new Location(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
      Location head = feet.clone().add(0.5D, 1.0D, 0.5D);
      Location ground = feet.clone().add(0.5D, -1.0D, 0.5D);
      return this.isFree(world, feet) && this.isFree(world, head) && this.isSolid(world, ground);
   }

   private boolean isFree(World world, Location location) {
      return world.getBlockAt(location).getType().isAir();
   }

   private boolean isSolid(World world, Location location) {
      return world.getBlockAt(location).getType().isSolid();
   }

   protected void applyWorldSpawnLocations() {
      ConfigurationSection spawns = this.getConfig().getConfigurationSection("world-rules.spawn");
      if (spawns == null) return;
      for (String key : spawns.getKeys(false)) {
         String path = "world-rules.spawn." + key;
         String worldName = this.getConfig().getString(path + ".world");
         World world = worldName == null ? null : Bukkit.getWorld(worldName);
         if (world == null) continue;
         world.setSpawnLocation(new Location(world,
            this.getConfig().getDouble(path + ".x", 0.0),
            this.getConfig().getDouble(path + ".y", 0.0),
            this.getConfig().getDouble(path + ".z", 0.0),
            (float) this.getConfig().getDouble(path + ".yaw", 0.0),
            (float) this.getConfig().getDouble(path + ".pitch", 0.0)));
      }
   }

   protected void normalizeSpawnLocationsToOrigin() {
      if (!"survival".equalsIgnoreCase(this.getConfig().getString("hub.world"))) this.setLocationCoordinatesToOrigin("hub");
      this.normalizeLocationSection("world-rules.spawn");
      this.normalizeLocationSection("warning-servers");
      this.saveConfig();
   }

   protected void normalizeLocationSection(String sectionPath) {
      ConfigurationSection section = this.getConfig().getConfigurationSection(sectionPath);
      if (section == null) return;
      for (String key : section.getKeys(false)) {
         String path = sectionPath + "." + key;
         if (!"survival".equalsIgnoreCase(this.getConfig().getString(path + ".world"))) this.setLocationCoordinatesToOrigin(path);
      }
   }

   protected void setLocationCoordinatesToOrigin(String path) {
      if (!this.getConfig().contains(path + ".world")) return;
      this.getConfig().set(path + ".x", 0.0);
      this.getConfig().set(path + ".y", 0.0);
      this.getConfig().set(path + ".z", 0.0);
      this.getConfig().set(path + ".yaw", 0.0);
      this.getConfig().set(path + ".pitch", 0.0);
   }

   protected void configureSurvivalSpawnLocation() {
      this.mifron().setIfMissing("world-rules.spawn.survival.world", "survival");
      this.mifron().setIfMissing("world-rules.spawn.survival.x", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.survival.y", 79.0);
      this.mifron().setIfMissing("world-rules.spawn.survival.z", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.survival.yaw", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.survival.pitch", 0.0);
      this.saveConfig();
   }
}
