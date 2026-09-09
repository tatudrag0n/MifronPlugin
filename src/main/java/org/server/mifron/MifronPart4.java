package org.server.mifron;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart4 extends MifronPart3x2 {
   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (this.ffaManager.isPlaying(player)) return;
      int lost = this.getEmeralds(player.getUniqueId()) / 2;
      if (lost > 0) {
         this.withdrawEmeralds(player.getUniqueId(), lost);
         player.sendMessage("\u00a7c\u6b7b\u4ea1\u306b\u3088\u308a\u6240\u6301MP\u306e50%\u3092\u5931\u3044\u307e\u3057\u305f: -" + this.formatNumber(lost) + "MP");
      }
   }

   protected boolean tryShopPayment(Player player, Block block) {
      ShelfShopOffer offer = this.readShelfShopOffer(player, block);
      if (offer == null || offer.material() == null || offer.price() <= 0) return false;
      int discountedPrice = this.applyShopDiscount(player, offer.price());
      int currentEmeralds = this.getEmeralds(player.getUniqueId());
      int stock = this.shelfShopStock(offer.material());
      if (stock < offer.amount()) {
         this.showTemporaryActionBar(player, "\u5728\u5eab\u5207\u308c\u3067\u3059\u3002\u30d7\u30ec\u30a4\u30e4\u30fc\u304c\u58f2\u5374\u3059\u308b\u3068\u518d\u5165\u8377\u3057\u307e\u3059\u3002");
         return true;
      }
      if (currentEmeralds < discountedPrice) {
         this.showTemporaryActionBar(player, "MP\u304c\u4e0d\u8db3\u3057\u3066\u3044\u307e\u3059\uff1a" + this.formatNumber(discountedPrice - currentEmeralds) + "MP");
         return true;
      }
      if (this.inventorySpaceFor(player, offer.material()) < offer.amount()) {
         this.showTemporaryActionBar(player, "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return true;
      }
      if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice, false)) {
         this.showTemporaryActionBar(player, "MP\u304c\u4e0d\u8db3\u3057\u3066\u3044\u307e\u3059\uff1a" + this.formatNumber(discountedPrice) + "MP");
         return true;
      }
      if ("buy".equalsIgnoreCase(offer.mode())) {
         this.showTemporaryActionBar(player, "\u3053\u306e\u68da\u306f\u8cb7\u53d6\u30b7\u30e7\u30c3\u30d7\u3067\u3059\u3002");
         return true;
      }
      this.changeShelfShopStock(offer.material(), -offer.amount());
      this.giveShopPurchasedItems(player, offer.material(), offer.amount());
      this.markShopActivity(block);
      this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount(), false);
      this.queueDataSave();
      this.playPurchaseSound(player);
      this.sendItemMessage(player, NamedTextColor.GREEN, "\u8cfc\u5165\u3057\u307e\u3057\u305f: ", offer.material(),
         " x" + offer.amount() + " (" + this.formatNumber(discountedPrice) + "MP / \u5728\u5eab " + this.formatNumber(this.shelfShopStock(offer.material())) + ")");
      return true;
   }

   protected boolean tryShopSell(Player player, Block block, ItemStack held) {
      if (player == null || block == null || held == null || held.getAmount() <= 0) return false;
      ShelfShopOffer offer = this.readShelfShopOffer(player, block);
      if (offer == null || offer.material() == null || held.getType() != offer.material()) return false;
      if ("sell".equalsIgnoreCase(offer.mode())) {
         this.showTemporaryActionBar(player, "\u3053\u306e\u68da\u306f\u8ca9\u58f2\u30b7\u30e7\u30c3\u30d7\u3067\u3059\u3002");
         return true;
      }
      Material material = offer.material();
      int price = Math.max(0, Math.min(this.materialBuyPrice(material), offer.price() - 1));
      if (price <= 0) {
         this.showTemporaryActionBar(player, "\u3053\u306e\u30a2\u30a4\u30c6\u30e0\u306f\u8cb7\u3044\u53d6\u308a\u5bfe\u8c61\u5916\u3067\u3059\u3002");
         return true;
      }
      if (this.utilityItemsFeature.getMifronItemId(held) != null || this.isShopWand(held)) return true;
      held.setAmount(held.getAmount() - 1);
      this.changeShelfShopStock(material, 1);
      this.markShopActivity(block);
      this.depositEmeralds(player.getUniqueId(), price, false);
      this.addPlayerStat(player.getUniqueId(), "total-trades", 1, false);
      this.recordFarmingSubmission(player, material);
      this.queueDataSave();
      this.playPurchaseSound(player);
      this.sendItemMessage(player, NamedTextColor.GREEN, "\u8cb7\u3044\u53d6\u308a\u307e\u3057\u305f: ", material,
         " (" + this.formatNumber(price) + "MP / \u5728\u5eab " + this.formatNumber(this.shelfShopStock(material)) + ")");
      return true;
   }
}
