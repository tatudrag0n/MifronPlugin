package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AdvancedAnvilRulesTest {
   @Test
   void equalLevelsUpgradeThroughEightAndTwenty() {
      assertEquals(6, AdvancedAnvilRules.combineLevel(5, 5, 20));
      assertEquals(7, AdvancedAnvilRules.combineLevel(6, 6, 20));
      assertEquals(8, AdvancedAnvilRules.combineLevel(7, 7, 20));
      assertEquals(20, AdvancedAnvilRules.combineLevel(19, 19, 20));
      assertEquals(20, AdvancedAnvilRules.combineLevel(20, 20, 20));
   }

   @Test
   void everyConfiguredLevelCombinesToTheNextLevel() {
      for (int level = 1; level < 20; level++) {
         assertEquals(level + 1, AdvancedAnvilRules.combineLevel(level, level, 20));
      }
   }

   @Test
   void unequalLevelsKeepTheHigherAndPlainItemsTakeIncomingLevel() {
      assertEquals(5, AdvancedAnvilRules.combineLevel(0, 5, 20));
      assertEquals(7, AdvancedAnvilRules.combineLevel(7, 5, 20));
      assertEquals(7, AdvancedAnvilRules.combineLevel(5, 7, 20));
      assertEquals(1, AdvancedAnvilRules.combineLevel(1, 1, 20, 1));
      assertEquals(3, AdvancedAnvilRules.combineLevel(2, 2, 20, 3));
   }

   @Test
   void normalizedMergeSupportsBothItemAndBookInputShapes() {
      Map<String, Integer> item = Map.of("sharpness", 7, "unbreaking", 3);
      Map<String, Integer> otherItem = Map.of("sharpness", 7, "mending", 1);
      Map<String, Integer> enchantedBook = Map.of("sharpness", 7);

      assertEquals(8, AdvancedAnvilRules.mergeEnchantments(item, otherItem, 20).get("sharpness"));
      assertEquals(8, AdvancedAnvilRules.mergeEnchantments(item, enchantedBook, 20).get("sharpness"));
      assertEquals(8, AdvancedAnvilRules.mergeEnchantments(enchantedBook, enchantedBook, 20).get("sharpness"));
      assertEquals(3, AdvancedAnvilRules.mergeEnchantments(item, otherItem, 20).get("unbreaking"));
      assertEquals(1, AdvancedAnvilRules.mergeEnchantments(item, otherItem, 20).get("mending"));
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
      assertEquals(750, AdvancedAnvilRules.mpCost(25, 10, 3));
      assertEquals(2000000000, AdvancedAnvilRules.mpCost(Integer.MAX_VALUE, Integer.MAX_VALUE));
   }
}
