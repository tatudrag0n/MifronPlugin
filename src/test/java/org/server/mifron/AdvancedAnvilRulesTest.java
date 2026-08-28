package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdvancedAnvilRulesTest {
   @Test
   void equalLevelsUpgradeThroughEightAndTwenty() {
      assertEquals(6, AdvancedAnvilRules.combineLevel(5, 5, 20));
      assertEquals(8, AdvancedAnvilRules.combineLevel(7, 7, 20));
      assertEquals(20, AdvancedAnvilRules.combineLevel(19, 19, 20));
      assertEquals(20, AdvancedAnvilRules.combineLevel(20, 20, 20));
   }

   @Test
   void unequalLevelsKeepTheHigherAndPlainItemsTakeIncomingLevel() {
      assertEquals(5, AdvancedAnvilRules.combineLevel(0, 5, 20));
      assertEquals(7, AdvancedAnvilRules.combineLevel(7, 5, 20));
      assertEquals(7, AdvancedAnvilRules.combineLevel(5, 7, 20));
   }

   @Test
   void romanNumeralsCoverTheConfiguredDisplayRange() {
      String[] expected = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
      for (int index = 0; index < expected.length; index++) {
         assertEquals(expected[index], AdvancedAnvilRules.toRoman(index + 1));
      }
   }

   @Test
   void mpCostIsIndependentFromXpDisplayAndBounded() {
      assertEquals(0, AdvancedAnvilRules.mpCost(0, 1));
      assertEquals(250, AdvancedAnvilRules.mpCost(25, 10));
      assertEquals(2000000000, AdvancedAnvilRules.mpCost(Integer.MAX_VALUE, Integer.MAX_VALUE));
   }
}
