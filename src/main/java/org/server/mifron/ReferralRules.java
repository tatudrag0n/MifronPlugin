package org.server.mifron;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/** Pure referral rules: code TTL, eligibility window and weekly caps. */
final class ReferralRules {
   private ReferralRules() {
   }

   /** True while a code issued at {@code createdAtMillis} may still be used. */
   static boolean codeValid(long createdAtMillis, long nowMillis, long ttlMillis) {
      if (createdAtMillis <= 0L || ttlMillis <= 0L) return false;
      return nowMillis - createdAtMillis <= ttlMillis;
   }

   /** True when the invitee joined recently enough to be referrable. */
   static boolean eligible(long firstSeenMillis, long nowMillis, int eligibleDays) {
      if (firstSeenMillis <= 0L) return false;
      long window = Math.max(1, eligibleDays) * 86_400_000L;
      return nowMillis - firstSeenMillis <= window;
   }

   /** True while the inviter has not hit this week's reward cap. */
   static boolean canRewardInviter(int rewardedThisWeek, int weeklyCap) {
      return rewardedThisWeek < Math.max(1, weeklyCap);
   }

   /** ISO week identifier such as "2026-W42" for weekly cap accounting. */
   static String weekKey(LocalDate date) {
      if (date == null) return "";
      return date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
   }
}
