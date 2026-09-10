package org.server.mifron;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart13 extends MifronPart12x2 {
   protected int advancementDifficulty(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String path = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
      List<String> ordered = List.of("root", "mine_stone", "upgrade_tools", "smelt_iron", "obtain_armor", "iron_tools", "mine_diamond", "enchant_item", "enter_the_nether", "enter_the_end", "kill_dragon", "elytra", "netherite_armor");
      int explicit = ordered.indexOf(path);
      if (explicit >= 0) return explicit;
      int score = key.split("/").length * 10 + key.length();
      if (key.contains("root")) score -= 100;
      if (key.contains("netherite") || key.contains("elytra")) score += 80;
      if (key.contains("all") || key.contains("complete")) score += 60;
      return score;
   }

   String formatNumber(int value) { return String.format(Locale.US, "%,d", value); }
   protected String formatDateTime(long millis) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis)); }
   protected String safePlayerName(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName(); }
   protected boolean isSafeConfigKey(String key) { return key != null && SAFE_CONFIG_KEY_PATTERN.matcher(key).matches(); }
   protected void sendInvalidConfigKeyMessage(CommandSender sender, String label) { sender.sendMessage("\u00a7c" + label + " \u306f\u82f1\u6570\u5b57\u3001\u30cf\u30a4\u30d5\u30f3\u3001\u30a2\u30f3\u30c0\u30fc\u30b9\u30b3\u30a2\u306e\u307f\u30671-32\u6587\u5b57\u306b\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); }

   protected OfflinePlayer resolveKnownPlayer(CommandSender sender, String name) {
      if (name == null || name.isBlank() || name.length() > 16) { sender.sendMessage("\u00a7c\u30d7\u30ec\u30a4\u30e4\u30fc\u540d\u304c\u4e0d\u6b63\u3067\u3059\u3002"); return null; }
      OfflinePlayer player = Bukkit.getOfflinePlayer(name);
      if (!player.isOnline() && !player.hasPlayedBefore()) { sender.sendMessage("\u00a7c\u30d7\u30ec\u30a4\u30e4\u30fc\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + name); return null; }
      return player;
   }

   void openTeleportUi(Player player) {
      Inventory inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.DROPPER, Component.text("\u00a75Mifron Teleporter"));
      inventory.setItem(0, this.actionItem(Material.GRASS_BLOCK, "\u00a7a\u4e2d\u592e\u5e83\u5834", List.of(), "teleport", "hub"));
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers != null) {
         int slot = 1;
         for (String key : servers.getKeys(false)) {
            if (!this.isSafeConfigKey(key) || slot >= 9) continue;
            inventory.setItem(slot++, this.actionItem(this.serverIconMaterial("servers." + key), "\u00a7d" + key, List.of(), "teleport", "servers." + key));
         }
      }
      this.fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   void openServerPortalTargetUi(Player player, String portalKey) {
      Inventory inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.DROPPER, Component.text("\u00a75Mifron Teleporter"));
      inventory.setItem(0, this.named(Material.ENDER_EYE, "\u00a7d\u30dd\u30fc\u30bf\u30eb\u79fb\u52d5\u5148\u8a2d\u5b9a", List.of()));
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers != null) {
         int slot = 1;
         for (String key : servers.getKeys(false)) {
            if (!this.isSafeConfigKey(key) || slot >= 9) continue;
            inventory.setItem(slot++, this.actionItem(this.serverIconMaterial("servers." + key), "\u00a7d" + key, List.of(), "server_portal_bind", portalKey + "|servers." + key));
         }
      }
      this.fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected void fillEmptyGuiSlots(Inventory inventory) {
      ItemStack filler = this.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack item = inventory.getItem(slot);
         if (item == null || item.getType() == Material.AIR) inventory.setItem(slot, filler.clone());
      }
   }
}
