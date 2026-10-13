package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LoginBonusRulesTest {
   private static final Map<Integer, Integer> MILESTONES = Map.of(7, 2000, 30, 10000, 100, 30000);

   @Test
   void milestoneBonusPaysOnlyExactHits() {
      assertEquals(2000, LoginBonusRules.milestoneBonus(MILESTONES, 7));
      assertEquals(0, LoginBonusRules.milestoneBonus(MILESTONES, 8));
      assertEquals(0, LoginBonusRules.milestoneBonus(Map.of(), 7));
   }

   @Test
   void nextMilestoneFindsSmallestGreaterDay() {
      assertEquals(7, LoginBonusRules.nextMilestone(MILESTONES, 1));
      assertEquals(30, LoginBonusRules.nextMilestone(MILESTONES, 7));
      assertEquals(100, LoginBonusRules.nextMilestone(MILESTONES, 31));
      assertEquals(-1, LoginBonusRules.nextMilestone(MILESTONES, 100));
      assertEquals(-1, LoginBonusRules.nextMilestone(Map.of(), 5));
   }

   @Test
   void returnBonusNeedsFullThreshold() {
      assertTrue(LoginBonusRules.qualifiesForReturnBonus(14, 14));
      assertFalse(LoginBonusRules.qualifiesForReturnBonus(13, 14));
      assertFalse(LoginBonusRules.qualifiesForReturnBonus(0, 0));
      assertTrue(LoginBonusRules.qualifiesForReturnBonus(1, 0));
      assertFalse(LoginBonusRules.qualifiesForReturnBonus(-5, 14));
   }
}
