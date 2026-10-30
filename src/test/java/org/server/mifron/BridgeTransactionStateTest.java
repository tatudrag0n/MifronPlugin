package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BridgeTransactionStateTest {
   private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

   private ConfigurationSection record() {
      ConfigurationSection record = new YamlConfiguration().createSection("transaction");
      record.set("uuid", PLAYER.toString());
      record.set("requested", 100);
      record.set("setMode", false);
      return record;
   }

   @Test
   void pendingWithoutResultMustNotBeAppliedAgain() {
      ConfigurationSection record = record();
      record.set("pending", true);
      assertEquals("transaction_still_processing", MinoruBridgeFeature.existingTransactionError(record, PLAYER, false, 100));
   }

   @Test
   void uncertainResultMustNotBeReplayedAsSuccess() {
      ConfigurationSection record = record();
      record.set("balance", 500);
      record.set("applied", 100);
      record.set("uncertain", true);
      assertEquals("transaction_state_uncertain", MinoruBridgeFeature.existingTransactionError(record, PLAYER, false, 100));
   }

   @Test
   void completedTransactionCanBeReplayed() {
      ConfigurationSection record = record();
      record.set("balance", 500);
      record.set("applied", 100);
      assertNull(MinoruBridgeFeature.existingTransactionError(record, PLAYER, false, 100));
   }

   @Test
   void reusedIdWithDifferentAmountIsRejected() {
      ConfigurationSection record = record();
      record.set("pending", true);
      assertEquals("transaction_id_reused", MinoruBridgeFeature.existingTransactionError(record, PLAYER, false, 101));
   }

   @Test
   void incompleteRecordIsQuarantined() {
      assertEquals("transaction_state_uncertain", MinoruBridgeFeature.existingTransactionError(record(), PLAYER, false, 100));
   }
}
