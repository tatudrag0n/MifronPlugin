package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for residual P2 guards: same-tick FFA double-lethal
 * suppression and shop cooldown overlay gating.
 */
class ResidualP2GuardTest {
   @Test
   void duplicateLethalOnlyMatchesSameTick() {
      assertFalse(FfaListener.isDuplicateLethal(null, 100L));
      assertFalse(FfaListener.isDuplicateLethal(99L, 100L));
      assertTrue(FfaListener.isDuplicateLethal(100L, 100L));
   }

   @Test
   void overlayOnlyWhenShopCooldownActive() {
      assertFalse(OnlineShopFeature.shouldOverlayCooldown(0L));
      assertFalse(OnlineShopFeature.shouldOverlayCooldown(-3L));
      assertTrue(OnlineShopFeature.shouldOverlayCooldown(1L));
   }
}
