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
      int lost = this.mifron().getEmeralds(player.getUniqueId()) / 2;
      if (lost > 0) {
         this.mifron().withdrawEmeralds(player.getUniqueId(), lost);
         player.sendMessage("\u00a7c\u6b7b\u4ea1\u306b\u3088\u308a\u6240\u6301MP\u306e50%\u3092\u5931\u3044\u307e\u3057\u305f: -" + this.mifron().formatNumber(lost) + "MP");
      }
   }

   protected boolean tryShopPayment(Player player, Block block) {
      ShelfShopOffer offer = this.mifron().readShelfShopOffer(player, block);
      if (offer == null || offer.material() == null || offer.price() <= 0) return false;
      if (!ShelfShopTradeRules.walletMayBuy(offer.mode())) {
         this.mifron().showTemporaryActionBar(player, "\u3053\u306e\u68da\u306f\u8cb7\u53d6\u30b7\u30e7\u30c3\u30d7\u3067\u3059\u3002");
         return true;
      }
      int discountedPrice = this.mifron().applyShopDiscount(player, offer.price());
      int currentEmeralds = this.mifron().getEmeralds(player.getUniqueId());
      int stock = this.mifron().shelfShopStock(offer.material());
      if (stock < offer.amount()) { this.mifron().showTemporaryActionBar(player, "\u5728\u5eab\u5207\u308c\u3067\u3059\u3002"); return true; }
      if (currentEmeralds < discountedPrice) { this.mifron().showTemporaryActionBar(player, "MP\u304c\u4e0d\u8db3\u3057\u3066\u3044\u307e\u3059"); return true; }
      if (this.mifron().inventorySpaceFor(player, offer.material()) < offer.amount()) { this.mifron().showTemporaryActionBar(player, "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return true; }
      if (!this.mifron().withdrawEmeralds(player.getUniqueId(), discountedPrice, false)) return true;
      this.mifron().changeShelfShopStock(offer.material(), -offer.amount());
      this.mifron().giveShopPurchasedItems(player, offer.material(), offer.amount());
      this.mifron().markShopActivity(block);
      this.mifron().addPlayerStat(player.getUniqueId(), "total-trades", offer.amount(), false);
      this.queueDataSave();
      this.mifron().playPurchaseSound(player);
      return true;
   }

   protected boolean tryShopSell(Player player, Block block, ItemStack held) {
      if (player == null || block == null || held == null || held.getAmount() <= 0) return false;
      ShelfShopOffer offer = this.mifron().readShelfShopOffer(player, block);
      if (offer == null || offer.material() == null || held.getType() != offer.material()) return false;
      if (!ShelfShopTradeRules.itemMaySell(offer.mode())) { this.mifron().showTemporaryActionBar(player, "\u3053\u306e\u68da\u306f\u8ca9\u58f2\u30b7\u30e7\u30c3\u30d7\u3067\u3059\u3002"); return true; }
      Material material = offer.material();
      int price = Math.max(0, Math.min(this.mifron().materialBuyPrice(material), offer.price() - 1));
      if (price <= 0) { this.mifron().showTemporaryActionBar(player, "\u8cb7\u3044\u53d6\u308a\u5bfe\u8c61\u5916\u3067\u3059\u3002"); return true; }
      if (this.utilityItemsFeature.getMifronItemId(held) != null || this.isShopWand(held)) return true;
      this.mifron().depositEmeralds(player.getUniqueId(), price, false);
      held.setAmount(held.getAmount() - 1);
      this.mifron().changeShelfShopStock(material, 1);
      this.mifron().markShopActivity(block);
      this.mifron().addPlayerStat(player.getUniqueId(), "total-trades", 1, false);
      this.mifron().recordFarmingSubmission(player, material);
      this.queueDataSave();
      this.mifron().playPurchaseSound(player);
      return true;
   }
}
