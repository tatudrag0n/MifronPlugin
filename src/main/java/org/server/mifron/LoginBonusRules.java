package org.server.mifron;

import java.util.Map;

/** Pure login-bonus math: streak milestones and return-bonus gap checks. */
final class LoginBonusRules {
   private LoginBonusRules() {
   }

   /** Milestone MP the streak just reached (0 when not a milestone). */
   static int milestoneBonus(Map<Integer, Integer> milestones, int streak) {
      Integer value = milestones.get(streak);
      return value == null ? 0 : Math.max(0, value);
   }

   /** Smallest configured milestone day strictly greater than streak, or -1. */
   static int nextMilestone(Map<Integer, Integer> milestones, int streak) {
      int next = -1;
      for (int day : milestones.keySet()) {
         if (day > streak && (next < 0 || day < next)) next = day;
      }
      return next;
   }

   /** True when a player absent for {@code gapDays} qualifies for the return bonus. */
   static boolean qualifiesForReturnBonus(long gapDays, int thresholdDays) {
      return gapDays >= Math.max(1, thresholdDays);
   }
}
