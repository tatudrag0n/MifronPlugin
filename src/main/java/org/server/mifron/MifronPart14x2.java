package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

abstract class MifronPart14x2 extends MifronPart14x1 {
   protected void applyConfiguredSpawnLocation(String primaryPath, String fallbackPath, String defaultWorld, double defaultX, double defaultY, double defaultZ) {
      String path = this.getConfig().contains(primaryPath + ".world") ? primaryPath : fallbackPath;
      String worldName = this.getConfig().getString(path + ".world", defaultWorld);
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world == null) return;
      Location spawn = new Location(
         world,
         this.getConfig().getDouble(path + ".x", defaultX),
         this.getConfig().getDouble(path + ".y", defaultY),
         this.getConfig().getDouble(path + ".z", defaultZ),
         (float) this.getConfig().getDouble(path + ".yaw", 0.0),
         (float) this.getConfig().getDouble(path + ".pitch", 0.0)
      );
      world.setSpawnLocation(spawn);
   }

   protected void migrateDefaultHubLocation() {
      if (!"world".equalsIgnoreCase(this.getConfig().getString("hub.world", "world"))) return;
      boolean oldDefault = Math.abs(this.getConfig().getDouble("hub.x") - 0.5) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("hub.y") - 64.0) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("hub.z") - 0.5) < 1.0E-4;
      boolean lowOriginSpawn = Math.abs(this.getConfig().getDouble("hub.x")) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("hub.z")) < 1.0E-4
         && (Math.abs(this.getConfig().getDouble("hub.y") - 60.0) < 1.0E-4 || Math.abs(this.getConfig().getDouble("hub.y") - 64.0) < 1.0E-4);
      boolean highOriginSpawn = Math.abs(this.getConfig().getDouble("hub.x")) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("hub.y") - 300.0) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("hub.z")) < 1.0E-4;
      boolean missing = !this.getConfig().contains("hub.x") || !this.getConfig().contains("hub.y") || !this.getConfig().contains("hub.z");
      if (!(oldDefault || lowOriginSpawn || highOriginSpawn || missing)) return;
      this.getConfig().set("hub.world", "world");
      this.getConfig().set("hub.x", 0.0);
      this.getConfig().set("hub.y", 0.0);
      this.getConfig().set("hub.z", 0.0);
      this.getConfig().set("hub.yaw", 0.0);
      this.getConfig().set("hub.pitch", 0.0);
      this.setIfMissing("world-rules.spawn.main.world", "world");
      this.setIfMissing("world-rules.spawn.main.x", 0.0);
      this.setIfMissing("world-rules.spawn.main.y", 0.0);
      this.setIfMissing("world-rules.spawn.main.z", 0.0);
      this.setIfMissing("world-rules.spawn.main.yaw", 0.0);
      this.setIfMissing("world-rules.spawn.main.pitch", 0.0);
      this.saveConfig();
   }
}
