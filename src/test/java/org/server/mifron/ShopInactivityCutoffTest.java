package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression test: a huge shops.inactivity-days config value must not
 * overflow days * 86400000L and mass-mark every shop inactive at once.
 */
class ShopInactivityCutoffTest {
   @Test
   void standardValuesAreExact() {
      assertEquals(86400000L, ShopBlockStore.inactivityCutoffMillis(1L));
      assertEquals(30L * 86400000L, ShopBlockStore.inactivityCutoffMillis(30L));
   }

   @Test
   void hugeValuesAreClampedWithoutOverflow() {
      long cutoff = ShopBlockStore.inactivityCutoffMillis(Long.MAX_VALUE);
      assertTrue(cutoff > 0L);
      assertEquals(365000L * 86400000L, cutoff);
   }

   @Test
   void nonPositiveValuesFallBackToOneDay() {
      assertEquals(86400000L, ShopBlockStore.inactivityCutoffMillis(0L));
      assertEquals(86400000L, ShopBlockStore.inactivityCutoffMillis(-5L));
   }
}
