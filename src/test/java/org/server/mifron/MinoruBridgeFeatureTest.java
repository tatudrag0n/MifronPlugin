package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MinoruBridgeFeatureTest {
   private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
   private static final UUID OTHER_PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

   @Test
   void acceptsExactIdempotentReplay() {
      assertTrue(MinoruBridgeFeature.transactionMatches(PLAYER.toString(), false, 25, PLAYER, false, 25));
   }

   @Test
   void rejectsReplayForAnotherPlayer() {
      assertFalse(MinoruBridgeFeature.transactionMatches(PLAYER.toString(), false, 25, OTHER_PLAYER, false, 25));
   }

   @Test
   void rejectsReplayWithDifferentOperationOrValue() {
      assertFalse(MinoruBridgeFeature.transactionMatches(PLAYER.toString(), false, 25, PLAYER, true, 25));
      assertFalse(MinoruBridgeFeature.transactionMatches(PLAYER.toString(), false, 25, PLAYER, false, 30));
   }

   @Test
   void acceptsLegacyRecordWithoutUuid() {
      assertTrue(MinoruBridgeFeature.transactionMatches("", false, 25, PLAYER, false, 25));
   }
}
