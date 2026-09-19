package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OnlineShopRulesTest {
   @Test
   void normalCooldownTimingIsUnchanged() {
      assertEquals(9000L, OnlineShopRules.cooldownDeadline(1000L, 8L));
      assertEquals(8L, OnlineShopRules.remainingSeconds(1000L, 9000L));
      assertEquals(1L, OnlineShopRules.remainingSeconds(8999L, 9000L));
      assertEquals(0L, OnlineShopRules.remainingSeconds(9000L, 9000L));
      assertEquals(160, OnlineShopRules.cooldownTicks(8L));
   }

   @Test
   void largeConfiguredDurationDoesNotExpireImmediately() {
      assertEquals(Long.MAX_VALUE, OnlineShopRules.cooldownDeadline(1000L, Long.MAX_VALUE));
      assertEquals(Long.MAX_VALUE, OnlineShopRules.cooldownDeadline(Long.MAX_VALUE - 500L, 1L));
   }

   @Test
   void distantDeadlineDoesNotOverflowRounding() {
      assertEquals(9223372036854776L, OnlineShopRules.remainingSeconds(0L, Long.MAX_VALUE));
      assertEquals(0L, OnlineShopRules.remainingSeconds(1000L, Long.MIN_VALUE));
   }

   @Test
   void overlayTicksSaturateWithoutOverflow() {
      assertEquals(Integer.MAX_VALUE, OnlineShopRules.cooldownTicks(Long.MAX_VALUE));
      assertEquals(0, OnlineShopRules.cooldownTicks(0L));
   }

   @Test
   void quickConsumablesCoverFoodArrowsAndThrowables() {
      // Edible flag covers bread/potions without name matching.
      assertEquals(true, OnlineShopRules.isQuickConsumable("BREAD", true));
      assertEquals(true, OnlineShopRules.isQuickConsumable("POTION", false));
      for (String name : new String[] {
         "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW",
         "SPLASH_POTION", "LINGERING_POTION", "MILK_BUCKET", "HONEY_BOTTLE",
         "ENDER_PEARL", "ENDER_EYE", "SNOWBALL", "EGG", "EXPERIENCE_BOTTLE",
         "FIREWORK_ROCKET", "WIND_CHARGE"}) {
         assertEquals(true, OnlineShopRules.isQuickConsumable(name, false), name);
      }
      assertEquals(false, OnlineShopRules.isQuickConsumable("DIAMOND_SWORD", false));
      assertEquals(false, OnlineShopRules.isQuickConsumable("IRON_DOOR", false));
      assertEquals(false, OnlineShopRules.isQuickConsumable(null, false));
   }

   @Test
   void consumableCapShortensLongCooldownsButKeepsZero() {
      assertEquals(10L, OnlineShopRules.applyConsumableCap(300L, 10L));
      assertEquals(10L, OnlineShopRules.applyConsumableCap(120L, 10L));
      assertEquals(8L, OnlineShopRules.applyConsumableCap(8L, 10L));
      assertEquals(0L, OnlineShopRules.applyConsumableCap(0L, 10L));
   }
}
