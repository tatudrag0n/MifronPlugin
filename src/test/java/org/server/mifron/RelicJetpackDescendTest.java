package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Jetpack landing-assist clamp (Back/S key float-down). */
class RelicJetpackDescendTest {
   @Test
   void descendClampsFastFallsOnly() {
      assertEquals(-RelicFeature.JETPACK_DESCEND_SPEED, RelicFeature.descendY(-2.0), 1.0E-9);
      assertEquals(-RelicFeature.JETPACK_DESCEND_SPEED, RelicFeature.descendY(-0.13), 1.0E-9);
      assertEquals(-0.05, RelicFeature.descendY(-0.05), 1.0E-9, "gentle sinks are kept as-is");
      assertEquals(0.3, RelicFeature.descendY(0.3), 1.0E-9, "rising is untouched");
      assertEquals(0.0, RelicFeature.descendY(0.0), 1.0E-9);
   }

   @Test
   void descendNeverExceedsTheFloatCap() {
      double y = -3.0;
      for (int i = 0; i < 50; i++) {
         y = RelicFeature.descendY(y - 0.08); // gravity tick + clamp per event
         assertTrue(y >= -RelicFeature.JETPACK_DESCEND_SPEED - 1.0E-9);
      }
   }
}
