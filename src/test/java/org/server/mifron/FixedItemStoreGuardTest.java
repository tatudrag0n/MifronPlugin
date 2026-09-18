package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/**
 * Regression test for fixed-item external-storage bypasses: besides the
 * classic shift-click, cursor placement, NUMBER_KEY hotbar swaps, and OFFHAND
 * swaps into the top container must all be cancelled.
 */
class FixedItemStoreGuardTest {
   @Test
   void shiftClickOfFixedItemOutOfPlayerInventoryIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SHIFT_LEFT, true, false, false, false, true, false, false));
   }

   @Test
   void cursorPlacedFixedItemIntoTopContainerIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, true, false, false, false, false, false));
   }

   @Test
   void numberKeySwapOfFixedHotbarItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.NUMBER_KEY, false, false, true, false, false, false, false));
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.NUMBER_KEY, false, false, false, false, false, false, false));
   }

   @Test
   void offhandSwapOfFixedItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SWAP_OFFHAND, false, false, false, true, false, false, false));
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SWAP_OFFHAND, false, false, false, false, false, false, false));
   }

   @Test
   void ordinaryPlayIsNotBlocked() {
      // Rearranging non-fixed items inside the own inventory.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, false, false, false, true, false, false));
      // Moving a normal item into the top container.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, false, false, false, false, false, false));
      // Double-click collect with a matching stack stays in player possession.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.DOUBLE_CLICK, true, true, false, false, true, false, true));
   }

   @Test
   void cursorDropOutsideWithFixedItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, true, false, false, false, true, false));
   }
}
