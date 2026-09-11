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
   Location readLocation(String path) {
      if (path != null && path.startsWith("servers.")) {
         String key = path.substring("servers.".length()).split("\\.", 2)[0];
         if (this.getConfig().getStringList("deleted-servers").contains(key)) return null;
      }
      String worldName = this.getConfig().getString(path + ".world");
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world == null) return null;
      return new Location(world,
         this.getConfig().getDouble(path + ".x"),
         this.getConfig().getDouble(path + ".y"),
         this.getConfig().getDouble(path + ".z"),
         (float) this.getConfig().getDouble(path + ".yaw"),
         (float) this.getConfig().getDouble(path + ".pitch"));
   }

   protected void handleServerOrderCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) { sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (args.length < 3) { sender.sendMessage("\u00a7e/mf serverorder <server-id> <position>"); return; }
      String key = args[1];
      if (!this.getConfig().isConfigurationSection("servers." + key)) { sender.sendMessage("\u00a7c\u30b5\u30fc\u30d0\u30fc\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + key); return; }
      int requested;
      try { requested = Integer.parseInt(args[2]); }
      catch (NumberFormatException e) { sender.sendMessage("\u00a7cposition \u306f1\u4ee5\u4e0a\u306e\u6574\u6570\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers == null || servers.getKeys(false).isEmpty()) { sender.sendMessage("\u00a7c\u767b\u9332\u6e08\u307f\u30b5\u30fc\u30d0\u30fc\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      List<String> keys = new ArrayList<>(servers.getKeys(false));
      Map<String, Integer> originalIndex = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) originalIndex.put(keys.get(i), i);
      keys.sort((a, b) -> {
         int order = Integer.compare(this.getConfig().getInt("servers." + a + ".order", originalIndex.get(a) + 1), this.getConfig().getInt("servers." + b + ".order", originalIndex.get(b) + 1));
         return order != 0 ? order : Integer.compare(originalIndex.get(a), originalIndex.get(b));
      });
      keys.remove(key);
      int position = Math.max(1, Math.min(requested, keys.size() + 1));
      keys.add(position - 1, key);
      for (int i = 0; i < keys.size(); i++) this.getConfig().set("servers." + keys.get(i) + ".order", i + 1);
      this.saveConfig();
      sender.sendMessage("\u00a7a\u30c6\u30ec\u30dd\u30fc\u30c8\u5148\u306e\u8868\u793a\u9806\u3092\u5909\u66f4\u3057\u307e\u3057\u305f: " + key + " \u2192 " + position);
   }

   protected void handleServerIconCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) { sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (args.length < 3) { sender.sendMessage("\u00a7e/mf servericon <server-id> <material>"); return; }
      String key = args[1];
      if (!this.getConfig().isConfigurationSection("servers." + key)) { sender.sendMessage("\u00a7c\u30b5\u30fc\u30d0\u30fc\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + key); return; }
      Material icon = Material.matchMaterial(args[2]);
      if (icon == null || !icon.isItem() || icon == Material.AIR) { sender.sendMessage("\u00a7c\u6709\u52b9\u306a\u30a2\u30a4\u30c6\u30e0\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
      this.getConfig().set("servers." + key + ".icon", icon.name().toLowerCase(Locale.ROOT));
      this.saveConfig();
      sender.sendMessage("\u00a7a\u30a2\u30a4\u30b3\u30f3\u3092\u5909\u66f4\u3057\u307e\u3057\u305f: " + key + " \u2192 " + icon.name().toLowerCase(Locale.ROOT));
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
      this.setLocationCoordinatesToOrigin("hub");
      this.normalizeLocationSection("world-rules.spawn");
      this.normalizeLocationSection("servers");
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

   protected void applyFixedSpawnLocation(String worldName, double x, double y, double z) {
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world != null) world.setSpawnLocation(new Location(world, x, y, z, 0.0F, 0.0F));
   }

   protected void configureSurvivalSpawnLocation() {
      this.getConfig().set("world-rules.spawn.survival.world", "survival");
      this.getConfig().set("world-rules.spawn.survival.x", 0.0);
      this.getConfig().set("world-rules.spawn.survival.y", 101.0);
      this.getConfig().set("world-rules.spawn.survival.z", 0.0);
      this.getConfig().set("world-rules.spawn.survival.yaw", 0.0);
      this.getConfig().set("world-rules.spawn.survival.pitch", 0.0);
      this.getConfig().set("servers.survival.world", "survival");
      this.getConfig().set("servers.survival.x", 0.0);
      this.getConfig().set("servers.survival.y", 101.0);
      this.getConfig().set("servers.survival.z", 0.0);
      this.getConfig().set("servers.survival.yaw", 0.0);
      this.getConfig().set("servers.survival.pitch", 0.0);
      this.mifron().setIfMissing("servers.survival.icon", "grass_block");
      this.applyFixedSpawnLocation("survival", 0.0, 101.0, 0.0);
      this.saveConfig();
   }
}
