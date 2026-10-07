package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobRulesTest {
   @Test
   void bonusAddsPercentSaturated() {
      assertEquals(120, JobRules.applyBonus(100, 20.0));
      assertEquals(100, JobRules.applyBonus(100, 0.0));
      assertEquals(0, JobRules.applyBonus(0, 20.0));
      assertEquals(JobRules.MAX_MP, JobRules.applyBonus(JobRules.MAX_MP, 50.0));
   }

   @Test
   void discountReducesCostFlooredAtZero() {
      assertEquals(80, JobRules.applyDiscount(100, 20.0));
      assertEquals(0, JobRules.applyDiscount(100, 100.0));
      assertEquals(100, JobRules.applyDiscount(100, 0.0));
      assertEquals(0, JobRules.applyDiscount(0, 20.0));
   }

   @Test
   void cooldownRemainingCountsDown() {
      assertEquals(0, JobRules.cooldownRemainingMillis(0, 1000L, 3_600_000L));
      assertEquals(0, JobRules.cooldownRemainingMillis(1000L, 2000L, 0L));
      assertEquals(500L, JobRules.cooldownRemainingMillis(1000L, 1500L, 1000L));
      assertEquals(0, JobRules.cooldownRemainingMillis(1000L, 3000L, 1000L));
   }

   @Test
   void jobKeysRoundTrip() {
      assertEquals(JobType.HUNTER, JobType.fromKey("hunter"));
      assertEquals(JobType.NONE, JobType.fromKey("none"));
      assertEquals(null, JobType.fromKey("slot_normal"));
      assertEquals(JobAction.HUNT, JobType.HUNTER.incomeAction());
      assertEquals(null, JobType.NONE.incomeAction());
      assertEquals(JobAction.FISH, JobAction.fromKey("fish"));
   }
}
