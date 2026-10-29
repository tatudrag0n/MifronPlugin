package org.server.mifron;

import java.util.HashMap;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class ShopTradeService {
   private final Mifron plugin;
   private final ShopBlockStore store;
   private final NamespacedKey itemKey;
   private final NamespacedKey specialKey;

   ShopTradeService(Mifron plugin, ShopBlockStore store) {
      this.plugin = plugin;
      this.store = store;
      this.itemKey = new NamespacedKey(plugin, "item");
      this.specialKey = new NamespacedKey(plugin, "special_item_id");
   }

   boolean isWallet(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return false;
      String id = item.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING);
      // The distributed menu item pays for shelf-shop purchases like the old
      // emerald_bundle wallet.
      return ShelfShopTradeRules.isWalletItem(id);
   }

   boolean isSpecial(ItemStack item) {
      return item != null && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(this.specialKey, PersistentDataType.STRING);
   }

   void tradeShelf(Player player, Block block, String shopType, ItemStack hand, int slot) {
      ItemStack displayed = this.store.displayedItem(block, slot);
      if (displayed == null) {
         player.sendMessage(ChatColor.YELLOW + "\u3053\u306e\u67a0\u306b\u306f\u5546\u54c1\u304c\u4e26\u3093\u3067\u3044\u307e\u305b\u3093\u3002");
         return;
      }
      if (this.isSpecial(displayed)) {
         player.sendMessage(ChatColor.RED + "\u7279\u6b8a\u30a2\u30a4\u30c6\u30e0\u306f\u58f2\u8cb7\u3067\u304d\u307e\u305b\u3093\u3002");
         return;
      }
      this.trade(player, block, shopType, displayed, hand, this.store.slotPrice(block, slot));
   }

   private void trade(Player player, Block block, String shopType, ItemStack displayed, ItemStack hand, int price) {
      boolean buyShop = "BUY_SHELF".equals(shopType);
      if (buyShop) {
         if (hand == null || hand.getType() != displayed.getType() || this.isSpecial(hand) || this.isWallet(hand)) {
            player.sendMessage(ChatColor.GOLD + "\u8cb7\u53d6: " + displayed.getType().name() + " = " + price + " MP");
            return;
         }
         if (price <= 0) {
            player.sendMessage(ChatColor.RED + "\u8cb7\u53d6\u4fa1\u683c\u304c\u8a2d\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002");
            return;
         }
         // The sale price must fit the capped balance BEFORE the held item is
         // consumed; otherwise a capped wallet silently swallows the item.
         // depositExact pays exactly `price` (the fit check passed), so the
         // withdraw below is a precise rollback, not a lossy refund.
         if (!this.plugin.economyManager.depositExact(player.getUniqueId(), price, true)) {
            player.sendMessage(ChatColor.RED + "MP上限に達しているため売却できません。MPを消費してから再度お試しください。");
            return;
         }
         hand.setAmount(hand.getAmount() - 1);
         this.store.touch(block);
          player.sendMessage(ChatColor.GOLD + displayed.getType().name() + " \u3092 " + price + " MP \u3067\u58f2\u5374\u3057\u307e\u3057\u305f\u3002");
          this.plugin.starterGoalsFeature.mark(player, "sell");
          return;
      }
      if (!this.isWallet(hand)) {
         player.sendMessage(ChatColor.YELLOW + "\u8ca9\u58f2: " + displayed.getType().name() + " = " + price + " MP");
         player.sendMessage(ChatColor.GRAY + "\u300c\u30e1\u30cb\u30e5\u30fc\u300d\u3092\u6301\u3063\u3066\u53f3\u30af\u30ea\u30c3\u30af\u3067\u8cfc\u5165\u3057\u307e\u3059\u3002");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MP\u304c\u8db3\u308a\u307e\u305b\u3093\u3002\u5fc5\u8981: " + price);
         return;
      }
      ItemStack product = displayed.clone();
      product.setAmount(1);
      HashMap leftover = player.getInventory().addItem(product);
      if (!leftover.isEmpty()) {
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      this.store.touch(block);
      player.sendMessage(ChatColor.GREEN + displayed.getType().name() + " \u3092 " + price + " MP \u3067\u8cfc\u5165\u3057\u307e\u3057\u305f\u3002");
   }
}
