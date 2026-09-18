package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression test for the slot free-spin faucet: a finished spin (win or
 * loss) must never refund the wager, while a genuinely interrupted spin
 * (quit, machine break, animation failure) must refund it exactly once.
 */
class SlotRefundDecisionTest {
   @Test
   void settledSessionNeverRefunds() {
      // Normal completion clears pending fields before running the payout
      // callback, which re-enters finishSession: settled=true blocks refund.
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(true, false, false));
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(true, true, false));
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(true, false, true));
   }

   @Test
   void interruptedSessionRefunds() {
      // Quit before any result was determined: nothing pending, no outcome.
      assertTrue(SlotMachineManager.shouldRefundOnInterrupt(false, false, false));
   }

   @Test
   void midResultSessionDoesNotDoubleRefund() {
      // Columns are stopping (completion armed or result visible): the spin
      // is resolving, so no interruption refund on top of the outcome.
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(false, true, false));
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(false, false, true));
      assertFalse(SlotMachineManager.shouldRefundOnInterrupt(false, true, true));
   }
}
