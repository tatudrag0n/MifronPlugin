package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradeRulesTest {
   @Test
   void offerSlotsBelongToCorrectSide() {
      assertTrue(TradeRules.isAOffer(0));
      assertTrue(TradeRules.isAOffer(30));
      assertFalse(TradeRules.isAOffer(5));
      assertTrue(TradeRules.isBOffer(5));
      assertTrue(TradeRules.isBOffer(35));
      assertFalse(TradeRules.isBOffer(0));
      assertFalse(TradeRules.isAOffer(47));
      assertFalse(TradeRules.isBOffer(47));
   }

   @Test
   void moneyOfferValidation() {
      assertFalse(TradeRules.isValidMoneyOffer(0, 1000000));
      assertFalse(TradeRules.isValidMoneyOffer(-5, 1000000));
      assertTrue(TradeRules.isValidMoneyOffer(1, 1000000));
      assertTrue(TradeRules.isValidMoneyOffer(1000000, 1000000));
      assertFalse(TradeRules.isValidMoneyOffer(1000001, 1000000));
   }

   @Test
   void sessionKeyIsOrderIndependent() {
      UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
      assertEquals(TradeRules.sessionKey(a, b), TradeRules.sessionKey(b, a));
   }

   @Test
   void sessionConfirmFlow() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      TradeSession session = new TradeSession(a, b);
      assertTrue(session.involves(a));
      assertTrue(session.involves(b));
      assertTrue(session.isA(a));
      assertFalse(session.isA(b));
      assertEquals(b, session.other(a));
      assertFalse(session.bothConfirmed());
      session.confirmedA = true;
      assertFalse(session.bothConfirmed());
      session.confirmedB = true;
      assertTrue(session.bothConfirmed());
      session.resetConfirms();
      assertFalse(session.bothConfirmed());
   }
}
