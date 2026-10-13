package org.server.mifron;

/** Pure job-level math: XP curves, level lookup and level bonuses. */
final class JobLevelRules {
   private JobLevelRules() {
   }

   /** Cumulative XP required to reach {@code level} (level 1 = 0 XP). */
   static long xpForLevel(int level, int baseXp, double growthPercent, int maxLevel) {
      int cap = Math.max(2, maxLevel);
      int target = Math.max(1, Math.min(level, cap));
      double need = Math.max(1, baseXp);
      double growth = 1.0 + Math.max(0.0, growthPercent) / 100.0;
      long total = 0L;
      for (int current = 2; current <= target; current++) {
         total += (long) Math.ceil(need);
         need *= growth;
      }
      return total;
   }

   /** Level for a lifetime XP amount, capped at maxLevel. */
   static int levelForXp(long xp, int baseXp, double growthPercent, int maxLevel) {
      int cap = Math.max(2, maxLevel);
      double need = Math.max(1, baseXp);
      double growth = 1.0 + Math.max(0.0, growthPercent) / 100.0;
      long total = 0L;
      for (int level = 2; level <= cap; level++) {
         total += (long) Math.ceil(need);
         if (xp < total) return level - 1;
         need *= growth;
      }
      return cap;
   }

   /** XP gathered inside the current level. */
   static int xpIntoLevel(long xp, int level, int baseXp, double growthPercent, int maxLevel) {
      long floor = xpForLevel(level, baseXp, growthPercent, maxLevel);
      return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, xp - floor));
   }

   /** XP required to advance from {@code level} (0 at max level). */
   static int xpNeededForNext(int level, int baseXp, double growthPercent, int maxLevel) {
      int cap = Math.max(2, maxLevel);
      if (level >= cap) return 0;
      long current = xpForLevel(level, baseXp, growthPercent, maxLevel);
      long next = xpForLevel(level + 1, baseXp, growthPercent, maxLevel);
      return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, next - current));
   }

   /** Income bonus percent for a level, saturated at {@code cap}. */
   static double bonusPercent(int level, double perLevel, double cap) {
      double bonus = Math.max(0, level - 1) * Math.max(0.0, perLevel);
      return Math.min(Math.max(0.0, cap), bonus);
   }
}
