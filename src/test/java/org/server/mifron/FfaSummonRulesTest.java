package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FfaSummonRulesTest {
   @Test
   void enforcesOwnerAndGlobalCaps() {
      assertTrue(FfaManager.canSpawnSummon(0, 0, 3, 20));
      assertFalse(FfaManager.canSpawnSummon(3, 3, 3, 20));
      assertFalse(FfaManager.canSpawnSummon(1, 20, 3, 20));
   }

   @Test
   void invalidConfigurationStillHasAValidMinimumCap() {
      assertTrue(FfaManager.canSpawnSummon(0, 0, 0, 0));
      assertFalse(FfaManager.canSpawnSummon(1, 0, 0, 20));
      assertFalse(FfaManager.canSpawnSummon(0, 1, 3, 0));
   }
}
