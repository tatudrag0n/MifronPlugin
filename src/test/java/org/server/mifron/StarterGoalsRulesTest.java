package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StarterGoalsRulesTest {
   @Test
   void stateFollowsCompleteAndClaimed() {
      assertEquals(StarterGoalsRules.GoalState.LOCKED, StarterGoalsRules.state(false, false));
      assertEquals(StarterGoalsRules.GoalState.CLAIMABLE, StarterGoalsRules.state(true, false));
      assertEquals(StarterGoalsRules.GoalState.CLAIMED, StarterGoalsRules.state(true, true));
      assertEquals(StarterGoalsRules.GoalState.CLAIMED, StarterGoalsRules.state(false, true));
   }

   @Test
   void miningCompleteAtTarget() {
      assertFalse(StarterGoalsRules.miningComplete(0));
      assertFalse(StarterGoalsRules.miningComplete(StarterGoalsRules.MINE_TARGET - 1));
      assertTrue(StarterGoalsRules.miningComplete(StarterGoalsRules.MINE_TARGET));
      assertTrue(StarterGoalsRules.miningComplete(1000));
   }

   @Test
   void goalIdsAreUnique() {
      Set<String> ids = new HashSet<>();
      for (StarterGoalsFeature.Goal goal : StarterGoalsFeature.GOALS) {
         assertTrue(ids.add(goal.id()), "duplicate goal id: " + goal.id());
      }
      assertEquals(6, StarterGoalsFeature.GOALS.size());
   }
}
