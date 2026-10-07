package org.server.mifron;

/** Pure job math: bonus application, discounts, and change-cooldown checks. */
final class JobRules {
   private JobRules() {
   }

   static final int MAX_MP = 2_000_000_000;

   /** Applies a +percent% bonus to a base amount, saturated at MAX_MP. */
   static int applyBonus(int base, double percent) {
      if (base <= 0) return 0;
      if (percent <= 0.0) return base;
      long result = Math.round(base * (1.0 + Math.max(0.0, Math.min(1000.0, percent)) / 100.0));
      return (int) Math.min(MAX_MP, Math.max(0L, result));
   }

   /** Applies a -percent% discount to a cost, floored at 0. */
   static int applyDiscount(int cost, double percent) {
      if (cost <= 0) return 0;
      if (percent <= 0.0) return cost;
      double clamped = Math.max(0.0, Math.min(100.0, percent));
      return (int) Math.max(0L, Math.round(cost * (1.0 - clamped / 100.0)));
   }

   /** Milliseconds remaining on a job-change cooldown, or 0 if changeable. */
   static long cooldownRemainingMillis(long changedAt, long now, long cooldownMillis) {
      if (cooldownMillis <= 0 || changedAt <= 0) return 0;
      long remaining = (changedAt + cooldownMillis) - now;
      return Math.max(0L, remaining);
   }
}
