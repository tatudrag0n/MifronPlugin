package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EconomyBoundsTest {
   @Test
   void transfersRequireFullRecipientCapacity() {
      assertTrue(EconomyManager.canTransferExact(10, EconomyManager.MAX_EMERALDS - 10, 10));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canTransferExact(10, EconomyManager.MAX_EMERALDS - 9, 10));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canTransferExact(10, EconomyManager.MAX_EMERALDS, 1));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canTransferExact(9, 0, 10));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canTransferExact(10, 0, 0));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canTransferExact(10, 0, -1));
   }

   @Test
   void exactCreditsRejectAmountsBeyondCapacity() {
      assertTrue(EconomyManager.canCreditExact(0, EconomyManager.MAX_EMERALDS));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canCreditExact(1, EconomyManager.MAX_EMERALDS));
      org.junit.jupiter.api.Assertions.assertFalse(EconomyManager.canCreditExact(0, Long.MAX_VALUE));
   }

   @Test
   void incomeBonusNeverGoesNegative() {
      assertEquals(0, AdvancementBonusRules.incomeBonus(-10, 50, 50));
      assertTrue(AdvancementBonusRules.incomeBonus(10, 0, 0) >= 0);
   }

   @Test
   void incomeBonusIsCapped() {
      assertEquals(2_000_000_000, AdvancementBonusRules.incomeBonus(2_000_000_000, 100, 100));
   }
}
