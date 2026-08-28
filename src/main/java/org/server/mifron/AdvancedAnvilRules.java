package org.server.mifron;

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
}
