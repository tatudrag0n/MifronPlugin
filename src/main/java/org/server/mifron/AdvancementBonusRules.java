package org.server.mifron;

import java.util.Locale;
import java.util.Map;

final class AdvancementBonusRules {
   private AdvancementBonusRules() {}

   static int percentForFrame(String frame, int taskPercent, int goalPercent, int challengePercent) {
      String key = frame == null ? "TASK" : frame.toUpperCase(Locale.ROOT);
      return switch (key) {
         case "CHALLENGE" -> Math.max(0, challengePercent);
         case "GOAL" -> Math.max(0, goalPercent);
         default -> Math.max(0, taskPercent);
      };
   }

   static int totalPercent(Iterable<Integer> percents) {
      long total = 0L;
      if (percents == null) return 0;
      for (Integer percent : percents) {
         if (percent != null && percent > 0) total += percent;
      }
      return (int) Math.min(2_000_000_000L, total);
   }

   static int incomeBonus(int base, int reincarnationPercent, int advancementPercent) {
      if (base <= 0) return 0;
      long bonus = Math.max(0, reincarnationPercent) + Math.max(0, advancementPercent);
      long reward = base + base * bonus / 100L;
      return (int) Math.min(2_000_000_000L, reward);
   }

   static int unlockOverride(Map<String, Integer> unlockPercents, String advancementKey, int fallback) {
      if (advancementKey == null || advancementKey.isBlank()) return Math.max(0, fallback);
      String shortKey = advancementKey.contains(":") ? advancementKey.substring(advancementKey.indexOf(':') + 1) : advancementKey;
      if (unlockPercents != null && unlockPercents.containsKey(shortKey)) {
         return Math.max(0, unlockPercents.get(shortKey));
      }
      return Math.max(0, fallback);
   }
}
