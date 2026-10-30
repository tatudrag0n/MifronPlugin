package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventoryGroupFeatureTest {
   @Test
   void snapshotShapeValidationRejectsCorruptLengths() {
      assertTrue(InventoryGroupFeature.isSnapshotShapeValid(41, 4, 41));
      assertFalse(InventoryGroupFeature.isSnapshotShapeValid(40, 4, 41));
      assertFalse(InventoryGroupFeature.isSnapshotShapeValid(41, 3, 41));
   }
}
