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
         "\u00a76Mifron\u3078\u3088\u3046\u3053\u305d\u3002",
         "\u00a7e\u30a6\u30a9\u30ec\u30c3\u30c8\u00a77: \u5de6\u30af\u30ea\u30c3\u30af\u3067MP\u3092\u78ba\u8a8d\u3057\u307e\u3059\u3002",
         "\u00a7eMP\u306e\u6ce8\u610f\u00a77: \u901a\u5e38\u306e\u30a8\u30e1\u30e9\u30eb\u30c9\u306fMP\u306b\u306a\u308a\u307e\u305b\u3093\u3002",
         "\u00a76\u30c6\u30ec\u30dd\u30fc\u30bf\u30fc\u00a77: \u79fb\u52d5\u5148\u3092\u8868\u793a\u3057\u307e\u3059\u3002",
         "\u00a7d\u30b9\u30c6\u30fc\u30bf\u30b9\u30fb\u30af\u30a8\u30b9\u30c8\u00a77: \u914d\u5e03\u30a2\u30a4\u30c6\u30e0\u304b\u3089\u78ba\u8a8d\u3067\u304d\u307e\u3059\u3002",
         "\u00a7a\u5efa\u7bc9\u306e\u6ce8\u610f\u00a77: Survival\u3067\u306fTNT\u3068\u6eb6\u5ca9\u3092\u4f7f\u3048\u307e\u305b\u3093\u3002"
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
   boolean isMifronItem(ItemStack item, String id) { return this.utilityItemsFeature.isMifronItem(item, id); }
   boolean isShopWand(ItemStack item) { return this.utilityItemsFeature.isShopWand(item); }
   boolean isLegacyShopWand(ItemStack item) { return this.utilityItemsFeature.isLegacyShopWand(item); }
   protected ShopWandType shopWandType(ItemStack item) { return this.utilityItemsFeature.getShopWandType(item); }
   protected boolean isReincarnationStar(ItemStack item) {
      return item != null && item.hasItemMeta()
         && Boolean.TRUE.equals(MifronPdc.get(item.getItemMeta().getPersistentDataContainer(), this.reincarnationStarKey, PersistentDataType.BOOLEAN));
   }
   protected void consumeOne(ItemStack item) { item.setAmount(item.getAmount() - 1); }
}
