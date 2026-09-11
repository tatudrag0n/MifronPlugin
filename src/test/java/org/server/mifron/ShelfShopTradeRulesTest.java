package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShelfShopTradeRulesTest {
   @Test
   void buyModeNeverChargesWallet() {
      assertFalse(ShelfShopTradeRules.walletMayBuy("buy"));
      assertFalse(ShelfShopTradeRules.walletMayBuy("BUY"));
      assertTrue(ShelfShopTradeRules.walletMayBuy("sell"));
      assertTrue(ShelfShopTradeRules.walletMayBuy("both"));
      assertTrue(ShelfShopTradeRules.walletMayBuy(null));
   }

   @Test
   void sellModeNeverConsumesPlayerItems() {
      assertFalse(ShelfShopTradeRules.itemMaySell("sell"));
      assertFalse(ShelfShopTradeRules.itemMaySell("SELL"));
      assertTrue(ShelfShopTradeRules.itemMaySell("buy"));
      assertTrue(ShelfShopTradeRules.itemMaySell("both"));
   }
}
