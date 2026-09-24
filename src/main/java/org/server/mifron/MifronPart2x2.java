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
         "\u00a76Mifron\u3078\u3088\u3046\u3053\u305d\u3002\u57fa\u672c\u64cd\u4f5c\u3092\u9806\u306b\u6848\u5185\u3057\u307e\u3059\u3002",
         "\u00a7e\u30e1\u30cb\u30e5\u30fc\u00a77: \u914d\u5e03\u30a2\u30a4\u30c6\u30e0\u53f3\u30af\u30ea\u30c3\u30af\u3067SHOP\u30fb\u30a6\u30a9\u30ec\u30c3\u30c8\u30fb\u30b9\u30c6\u30fc\u30bf\u30b9\u3092\u958b\u304d\u307e\u3059\u3002",
         "\u00a7e\u30a6\u30a9\u30ec\u30c3\u30c8\u00a77: \u30ab\u30fc\u30bd\u30eb\u3092\u5408\u308f\u305b\u308b\u3068\u6240\u6301MP\u8868\u793a\u3001\u5de6\u30af\u30ea\u30c3\u30af\u3067\u6b8b\u9ad8\u78ba\u8a8d\u3002",
         "\u00a7eMP\u306e\u6ce8\u610f\u00a77: \u901a\u5e38\u306e\u30a8\u30e1\u30e9\u30eb\u30c9\u306fMP\u306b\u306a\u308a\u307e\u305b\u3093\u3002\u58f2\u5374\u3067MP\u7372\u5f97\u3002",
         "\u00a7bSHOP\u00a77: \u5de6\u30af\u30ea\u30c3\u30af\u8cfc\u5165\u3001\u53f3\u30af\u30ea\u30c3\u30af\u58f2\u5374\u3001Shift+\u53f3\u4e00\u62ec\u58f2\u5374\u3002\u5728\u5eab\u3067\u4fa1\u683c\u5909\u52d5\u3002",
         "\u00a76\u5546\u4eba\u00a77: \u53e4\u3044\u5e02\u5834\u306b\u51fa\u73fe\u3002\u7a00\u5c11\u5546\u4eba\u306f\u7279\u6b8a\u54c1\u5c02\u9580\u3002",
         "\u00a76\u30c6\u30ec\u30dd\u30fc\u30bf\u30fc\u00a77: survival\u30fbmain\u30fbFFA\u3078\u79fb\u52d5\u3002\u521d\u56de\u306f\u4fdd\u8b77\u4ed8\u304d\u3002",
         "\u00a7d\u30b9\u30c6\u30fc\u30bf\u30b9\u30fb\u30af\u30a8\u30b9\u30c8\u00a77: \u9032\u6357\u3067MFL\u4e0a\u6607\u3001\u79f0\u53f7\u89e3\u653e\u3002\u6761\u4ef6\u30d2\u30f3\u30c8\u4ed8\u304d\u3002",
         "\u00a7dFFA\u00a77: \u30ed\u30d3\u30fc\u304b\u3089\u53c2\u6218\u3002\u30ea\u30dc\u30eb\u30d0\u30fc\u306f1\u767a\u305a\u3064\u88c5\u5857\u3001\u5c04\u6483\u3067\u4e2d\u65ad\u3002",
         "\u00a7amain\u4e16\u754c\u00a77: \u534a\u30af\u30ea\u30a8\u30a4\u30c6\u30a3\u30d6\u3002\u98db\u884c\u53ef\u3001\u6d88\u8cbb\u306a\u3057\u3001\u8a2d\u7f6e\u306f\u627f\u8a8d\u5236\u3002",
         "\u00a7a\u627f\u8a8d\u5236\u00a77: \u8a2d\u7f6e\u5f8c\u306f\u8d64\u30d1\u30fc\u30c6\u30a3\u30af\u30eb\u3001\u627f\u8a8d\u5f8c\u56fa\u5b9a\u3002",
         "\u00a7b\u6697\u8996\u00a77: \u30e1\u30cb\u30e5\u30fc\u304b\u308910,000MP\u3067\u89e3\u653e\u3001\u4ee5\u964dON/OFF\u5207\u66ff\u3002",
         "\u00a7a\u5efa\u7bc9\u306e\u6ce8\u610f\u00a77: Survival\u3067\u306fTNT\u3068\u6eb6\u5ca9\u3092\u4f7f\u3048\u307e\u305b\u3093\u3002",
         "\u00a7d\u9ad8\u5ea6\u306a\u91d1\u5e8a\u00a77: \u901a\u5e38\u306e\u91d1\u5e8a\u3067\u5408\u6210\u3059\u308b\u3068\u3001\u540cLv\u306e\u30a8\u30f3\u30c1\u30e3\u306f\u30ec\u30d9\u30eb\u30a2\u30c3\u30d7\u3001\u4e0a\u9650\u8d85\u3048\u306fMP\u8ab2\u91d1\u3067\u4fdd\u6301\u3002\u7d50\u679c\u306b\u5fc5\u8981XP/MP\u8868\u793a\u3002",
         "\u00a7c\u30df\u30cb\u30b2\u30fc\u30e0\u00a77: \u30e1\u30cb\u30e5\u30fc\u306e\u30df\u30cb\u30b2\u30fc\u30e0\u304b\u3089FFA\u30fb\u30a2\u30b9\u30ec\u30fb\u30b9\u30ed\u30c3\u30c8\u3092\u9078\u629e\u3002\u3053\u3053\u304c\u904a\u3073\u5834\u306e\u5165\u53e3\u3002",
         "\u00a7d\u56f3\u9451\u00a77: \u30b9\u30c6\u30fc\u30bf\u30b9\u306e\u56f3\u9451\u30bf\u30d6\u3067\u30ec\u30a2\u7269\u53ce\u96c6\u72b6\u6cc1\u3001\u8a0e\u4f10\u30bf\u30d6\u3067\u30a8\u30ea\u30fc\u30c8\u72b6\u6cc1\u3092\u78ba\u8a8d\u3002",
         "\u00a76\u305d\u306e\u4ed6\u00a77: \u8a73\u3057\u304f\u306fDiscord\u306e/tutorial\u30b3\u30de\u30f3\u30c9\u3067\u3002"
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
   protected void consumeOne(ItemStack item) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) return;
      item.setAmount(item.getAmount() - 1);
   }
}
