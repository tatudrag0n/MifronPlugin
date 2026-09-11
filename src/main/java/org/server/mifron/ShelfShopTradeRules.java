package org.server.mifron;

final class ShelfShopTradeRules {
   private ShelfShopTradeRules() {}

   static boolean walletMayBuy(String mode) {
      return mode == null || mode.isBlank() || !"buy".equalsIgnoreCase(mode);
   }

   static boolean itemMaySell(String mode) {
      return mode == null || mode.isBlank() || !"sell".equalsIgnoreCase(mode);
   }
}
