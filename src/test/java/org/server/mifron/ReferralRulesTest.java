package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReferralRulesTest {
   @Test
   void codeTtlWindow() {
      long now = 10_000_000L;
      assertTrue(ReferralRules.codeValid(now - 1000L, now, 5000L));
      assertTrue(ReferralRules.codeValid(now, now, 5000L));
      assertFalse(ReferralRules.codeValid(now - 6000L, now, 5000L));
      assertFalse(ReferralRules.codeValid(0L, now, 5000L));
      assertFalse(ReferralRules.codeValid(now, now, 0L));
   }

   @Test
   void eligibilityUsesJoinWindow() {
      long day = 86_400_000L;
      long now = 100 * day;
      assertTrue(ReferralRules.eligible(now - day, now, 7));
      assertTrue(ReferralRules.eligible(now - 7 * day, now, 7));
      assertFalse(ReferralRules.eligible(now - 8 * day, now, 7));
      assertFalse(ReferralRules.eligible(0L, now, 7));
   }

   @Test
   void weeklyCapBlocksAfterLimit() {
      assertTrue(ReferralRules.canRewardInviter(0, 5));
      assertTrue(ReferralRules.canRewardInviter(4, 5));
      assertFalse(ReferralRules.canRewardInviter(5, 5));
      assertFalse(ReferralRules.canRewardInviter(99, 5));
   }

   @Test
   void weekKeyIsStableIsoWeek() {
      assertEquals("2026-W1", ReferralRules.weekKey(LocalDate.of(2026, 1, 1)));
      assertEquals("2025-W1", ReferralRules.weekKey(LocalDate.of(2024, 12, 30)));
      assertEquals("", ReferralRules.weekKey(null));
   }
}
