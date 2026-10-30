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
   void rejectsExchangeThatWouldOverflowAfterBilateralSettlement() {
      assertFalse(TradeService.canSettleExchange(1_999_999_900, 1_000, 1, 100));
   }

   @Test
   void onlyReceiverCanAcceptTradeRequest() {
      UUID requester = UUID.randomUUID();
      UUID receiver = UUID.randomUUID();
      assertTrue(TradeService.canAcceptRequest(requester, receiver, receiver));
      assertFalse(TradeService.canAcceptRequest(requester, receiver, requester));
   }
}
