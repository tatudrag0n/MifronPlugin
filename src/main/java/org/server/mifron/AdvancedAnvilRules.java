package org.server.mifron;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure rules used by the Survival advanced-anvil feature. */
final class AdvancedAnvilRules {
   private AdvancedAnvilRules() {
   }

   static int combineLevel(int existing, int incoming, int maximum) {
      int max = Math.max(1, Math.min(255, maximum));
      int left = Math.max(0, Math.min(max, existing));
      int right = Math.max(0, Math.min(max, incoming));
      if (left == 0) return right;
      if (right == 0) return left;
      return Math.max(1, Math.min(max, left == right ? left + 1 : Math.max(left, right)));
   }

   static int combineLevel(int existing, int incoming, int maximum, int enchantmentMaximum) {
      return combineLevel(existing, incoming, Math.min(maximum, enchantmentMaximum));
   }

   /**
    * Merges the normalized enchantment maps from either an item or a book.
    * Keeping this operation independent from Bukkit metadata makes the two
    * supported input shapes (item+item and item+book/book+book) use exactly
    * the same level rules and prevents direct/stored book enchantments from
    * being counted twice.
    */
   static <K> Map<K, Integer> mergeEnchantments(Map<K, Integer> left, Map<K, Integer> right, int maximum) {
      Map<K, Integer> merged = new LinkedHashMap<>();
      if (left != null) {
         left.forEach((key, level) -> {
            if (key != null && level != null) {
               merged.put(key, Math.max(0, level));
            }
         });
      }
      if (right != null) {
         right.forEach((key, level) -> {
            if (key == null || level == null) {
               return;
            }
            int current = merged.getOrDefault(key, 0);
            merged.put(key, combineLevel(current, level, maximum));
         });
      }
      return merged;
   }

   static String toRoman(int level) {
      int remaining = Math.max(1, level);
      int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
      String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
      StringBuilder roman = new StringBuilder();
      for (int index = 0; index < values.length; index++) {
         while (remaining >= values[index]) {
            roman.append(numerals[index]);
            remaining -= values[index];
         }
      }
      return roman.toString();
   }

   static int mpCost(int xpCost, int perLevel) {
      long result = (long)Math.max(0, xpCost) * Math.max(0, perLevel);
      return (int)Math.min(2000000000L, result);
   }

   static int mpCost(int xpCost, int perLevel, int multiplier) {
      long result = (long)Math.max(0, xpCost) * Math.max(0, perLevel) * Math.max(0, multiplier);
      return (int)Math.min(2000000000L, result);
   }
}
