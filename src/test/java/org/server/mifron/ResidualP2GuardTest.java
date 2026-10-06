package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for residual P2 guards: shop cooldown overlay gating.
 */
class ResidualP2GuardTest {
   @Test
   void overlayOnlyWhenShopCooldownActive() {
      assertFalse(OnlineShopFeature.shouldOverlayCooldown(0L));
      assertFalse(OnlineShopFeature.shouldOverlayCooldown(-3L));
      assertTrue(OnlineShopFeature.shouldOverlayCooldown(1L));
   }
}
