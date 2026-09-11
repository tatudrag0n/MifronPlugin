package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EconomyBoundsTest {
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
