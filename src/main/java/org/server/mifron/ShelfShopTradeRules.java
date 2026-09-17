package org.server.mifron;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class ShelfShopTradeRules {
   private ShelfShopTradeRules() {}

   static int inventorySpaceFor(Inventory inventory, ItemStack product) {
      if (product == null || product.isEmpty()) return 0;
      int space = 0;
      int limit = Math.max(0, Math.min(inventory.getMaxStackSize(), product.getMaxStackSize()));
      for (ItemStack item : inventory.getStorageContents()) {
         if (item == null || item.isEmpty()) space += limit;
         else if (product.isSimilar(item)) space += Math.max(0, Math.min(limit, item.getMaxStackSize()) - item.getAmount());
      }
      return space;
   }

   static boolean walletMayBuy(String mode) {
      return mode == null || mode.isBlank() || !"buy".equalsIgnoreCase(mode);
   }

   static boolean itemMaySell(String mode) {
      return mode == null || mode.isBlank() || !"sell".equalsIgnoreCase(mode);
   }
}
