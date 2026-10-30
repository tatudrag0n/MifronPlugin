package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradeServiceExecutionTest {

   @Test
   void allowsZeroAndOneWayMoneyTrades() {
      assertTrue(TradeService.canSettleExchange(100, 200, 0, 0));
      assertTrue(TradeService.canSettleExchange(100, 200, 75, 0));
      assertTrue(TradeService.canSettleExchange(100, 200, 0, 75));
   }

   @Test
   void allowsBilateralSettlementBelowBalanceLimit() {
      assertTrue(TradeService.canSettleExchange(
         1_999_999_900, 1_000, 1, 100
      ));
   }

   @Test
   void allowsBilateralSettlementExactlyAtBalanceLimit() {
      assertTrue(TradeService.canSettleExchange(
         1_999_999_900, 1_000, 1, 101
      ));
   }

   @Test
   void rejectsBilateralSettlementAboveBalanceLimit() {
      assertFalse(TradeService.canSettleExchange(
         1_999_999_900, 1_000, 1, 102
      ));
   }

   @Test
   void rejectsOverflowForEitherReceiver() {
      assertFalse(TradeService.canSettleExchange(
         2_000_000_000, 100, 0, 1
      ));
      assertFalse(TradeService.canSettleExchange(
         100, 2_000_000_000, 1, 0
      ));
   }

   @Test
   void rejectsOffersExceedingOwnBalance() {
      assertFalse(TradeService.canSettleExchange(10, 200, 11, 0));
      assertFalse(TradeService.canSettleExchange(100, 20, 0, 21));
   }

   @Test
   void rejectsNegativeInputs() {
      assertFalse(TradeService.canSettleExchange(-1, 100, 0, 0));
      assertFalse(TradeService.canSettleExchange(100, -1, 0, 0));
      assertFalse(TradeService.canSettleExchange(100, 100, -1, 0));
      assertFalse(TradeService.canSettleExchange(100, 100, 0, -1));
   }

   @Test
   void rejectsBalancesAlreadyAboveLimit() {
      assertFalse(TradeService.canSettleExchange(
         2_000_000_001, 100, 1, 0
      ));
      assertFalse(TradeService.canSettleExchange(
         100, 2_000_000_001, 0, 1
      ));
   }

   @Test
   void onlyReceiverCanAcceptTradeRequest() {
      UUID requester = UUID.randomUUID();
      UUID receiver = UUID.randomUUID();
      UUID unrelated = UUID.randomUUID();

      assertTrue(TradeService.canAcceptRequest(
         requester, receiver, receiver
      ));
      assertFalse(TradeService.canAcceptRequest(
         requester, receiver, requester
      ));
      assertFalse(TradeService.canAcceptRequest(
         requester, receiver, unrelated
      ));
      assertFalse(TradeService.canAcceptRequest(
         requester, receiver, null
      ));
   }
}
