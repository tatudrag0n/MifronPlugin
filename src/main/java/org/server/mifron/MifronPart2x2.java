package org.server.mifron;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart2x2 extends MifronPart2x1 {
   protected void startPlayerSession(Player player) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      section.set("session-minutes", 0);
      section.set("session-playtime-rewards", 0);
      section.set("total-play-count", this.mifron().safeAdd(section.getInt("total-play-count", 0), 1));
      this.queueDataSave();
   }

   protected boolean isFirstJoin(Player player) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      return !section.getBoolean("tutorial.completed", false) && section.getBoolean("tutorial.auto-pending", false);
   }

   protected void startTutorial(Player player, boolean manual) {
      if (player == null || !player.isOnline()) return;
      if (!this.activeTutorials.add(player.getUniqueId())) {
         if (manual) player.sendMessage("\u00a7e\u30c1\u30e5\u30fc\u30c8\u30ea\u30a2\u30eb\u306f\u3059\u3067\u306b\u9032\u884c\u4e2d\u3067\u3059\u3002");
         return;
      }
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      section.set("tutorial.started", true);
      section.set("tutorial.last-started-at", System.currentTimeMillis());
      this.queueDataSave();
      List<String> steps = List.of(
         "\u00a76Mifron\u3078\u3088\u3046\u3053\u305d\u3002\u3053\u3053\u306fsurvival\u30e1\u30a4\u30f3\u306e\u751f\u6d3b\u9bd6\u3067\u3059\u3002",
         "\u00a7e\u30e1\u30cb\u30e5\u30fc\u00a77: \u914d\u5e03\u30a2\u30a4\u30c6\u30e0\u53f3\u30af\u30ea\u30c3\u30af\u3067SHOP\u30fb\u30af\u30a8\u30b9\u30c8\u30fb\u79fb\u52d5\u306a\u3069\u3092\u958b\u304d\u307e\u3059\u3002",
         "\u00a7eMP\u00a77: \u30a8\u30e1\u30e9\u30eb\u30c9\u306fMP\u306b\u306a\u308a\u307e\u305b\u3093\u3002SHOP\u58f2\u5374\u3067MP\u7372\u5f97\u3002",
         "\u00a7bSHOP\u00a77: \u5de6\u8cfc\u5165\u30fb\u53f3\u58f2\u5374\u30fbShift+\u53f3\u3067\u4e00\u62ec\u58f2\u5374\u3002",
         "\u00a7c\u6b7b\u4ea1\u6642\u00a77: \u30a2\u30a4\u30c6\u30e0\u306f\u4fdd\u6301\u3055\u308c\u307e\u3059\u304cMP\u304c\u534a\u6e1b\u3057\u307e\u3059\u3002",
         "\u00a7a\u30eb\u30fc\u30eb\u00a77: TNT\u30fb\u6eb6\u5ca9\u306f\u4f7f\u7528\u4e0d\u53ef\u3001PvP\u306f\u65e2\u5b9aOFF\u3002",
         "\u00a7a\u571f\u5730\u4fdd\u8b77\u00a77: \u81ea\u5206\u306e\u30c1\u30e3\u30f3\u30af\u3067 /mf protect chunk \u3092\u5b9f\u884c\u3002",
         "\u00a76\u79fb\u52d5\u00a77: \u30c6\u30ec\u30dd\u30fc\u30bf\u30fc\u3067\u5404\u30ef\u30fc\u30eb\u30c9\u3078\u3002\u767b\u9332\u6e08\u307f\u30d5\u30ec\u30fc\u30e0\u306f\u6bb4\u308b\u3068\u79fb\u52d5\u3002",
         "\u00a7d\u30af\u30a8\u30b9\u30c8\u00a77: \u9054\u6210\u3067\u5831\u916c\u3002\u30b9\u30c6\u30fc\u30bf\u30b9\u3067\u9032\u6357\u78ba\u8a8d\u3002",
         "\u00a7c\u904a\u3073\u5834\u00a77: \u30df\u30cb\u30b2\u30fc\u30e0\u304b\u3089FFA\u30fb\u30a2\u30b9\u30ec\u30fb\u30b9\u30ed\u30c3\u30c8\u3078\u3002",
         "\u00a76\u6848\u5185\u00a77: \u518d\u8868\u793a\u306f\u30e1\u30cb\u30e5\u30fc\u306e\u672c\u30fb/tutorial\u3002"
      );
      player.sendMessage("\u00a76=== Mifron Tutorial ===");
      for (int i = 0; i < steps.size(); i++) {
         int index = i;
         Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) { this.activeTutorials.remove(player.getUniqueId()); return; }
            player.sendMessage(steps.get(index));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.0F + index * 0.08F);
            if (index == steps.size() - 1) {
               this.mifron().getPlayerSection(player.getUniqueId()).set("tutorial.completed", true);
               this.mifron().getPlayerSection(player.getUniqueId()).set("tutorial.auto-pending", false);
               this.mifron().getPlayerSection(player.getUniqueId()).set("tutorial.completed-at", System.currentTimeMillis());
               this.queueDataSave();
               this.activeTutorials.remove(player.getUniqueId());
            }
         }, 20L * i);
      }
   }

   protected void giveInitialItems(Player player) { this.utilityItemsFeature.giveInitialItems(player); }
   void giveInitialItemsAfterInventoryRestore(Player player) { this.utilityItemsFeature.giveInitialItems(player); }
   protected boolean hasMifronItem(Player player, String id) { return this.utilityItemsFeature.hasMifronItem(player, id); }
   protected ItemStack createShopWand() { return this.utilityItemsFeature.createShopWand(); }
   protected ItemStack createShopWand(ShopWandType type) { return this.utilityItemsFeature.createShopWand(type); }
   protected ItemStack createJumpPadWand(int verticalPower, int horizontalPower) { return this.utilityItemsFeature.createJumpPadWand(verticalPower, horizontalPower); }
   public boolean isMifronItem(ItemStack item, String id) { return this.utilityItemsFeature.isMifronItem(item, id); }
   boolean isShopWand(ItemStack item) { return this.utilityItemsFeature.isShopWand(item); }
   boolean isLegacyShopWand(ItemStack item) { return this.utilityItemsFeature.isLegacyShopWand(item); }
   protected ShopWandType shopWandType(ItemStack item) { return this.utilityItemsFeature.getShopWandType(item); }
   protected boolean isReincarnationStar(ItemStack item) {
      return item != null && item.hasItemMeta()
         && Boolean.TRUE.equals(MifronPdc.get(item.getItemMeta().getPersistentDataContainer(), this.reincarnationStarKey, PersistentDataType.BOOLEAN));
   }
   protected void consumeOne(ItemStack item) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
      item.setAmount(item.getAmount() - 1);
   }
}
