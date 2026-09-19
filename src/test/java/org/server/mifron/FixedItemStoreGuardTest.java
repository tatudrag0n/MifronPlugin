package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

/**
 * Fixed items move freely inside the player's own inventory but must never
 * cross into an external top container (shift-click out, cursor placement,
 * NUMBER_KEY hotbar swaps, OFFHAND swaps).
 */
class FixedItemStoreGuardTest {
   @Test
   void shiftClickOfFixedItemOutOfPlayerInventoryIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SHIFT_LEFT, true, false, false, false, true, false));
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SHIFT_RIGHT, true, false, false, false, true, false));
   }

   @Test
   void rearrangingInsideOwnInventoryIsAllowed() {
      // Picking up a fixed item with an empty cursor.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, true, false, false, false, true, false));
      // Placing a cursor-held fixed item onto an own-inventory slot.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.RIGHT, false, true, false, false, true, false));
      // Swapping a fixed hotbar slot with another own-inventory slot.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.NUMBER_KEY, false, false, true, false, true, false));
      // Off-hand swap inside the own inventory.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SWAP_OFFHAND, false, false, false, true, true, false));
      // Double-click collect stays in the player's possession.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.DOUBLE_CLICK, true, true, false, false, true, false));
   }

   @Test
   void cursorPlacedFixedItemIntoTopContainerIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, true, false, false, false, false));
   }

   @Test
   void numberKeySwapOfFixedHotbarItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.NUMBER_KEY, false, false, true, false, false, false));
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.NUMBER_KEY, false, false, false, false, false, false));
   }

   @Test
   void offhandSwapOfFixedItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SWAP_OFFHAND, false, false, false, true, false, false));
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.SWAP_OFFHAND, false, false, false, false, false, false));
   }

   @Test
   void ordinaryPlayIsNotBlocked() {
      // Rearranging non-fixed items inside the own inventory.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, false, false, false, true, false));
      // Moving a normal item into the top container.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, false, false, false, false, false));
      // Taking an item back out of the top container.
      assertFalse(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, false, false, false, false, false));
   }

   @Test
   void cursorDropOutsideWithFixedItemIsCancelled() {
      assertTrue(UtilityItemsFeature.shouldCancelFixedItemStore(
         ClickType.LEFT, false, true, false, false, false, true));
   }
}
