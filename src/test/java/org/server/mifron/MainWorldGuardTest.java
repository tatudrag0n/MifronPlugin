package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Spawn-radius and column-key rules for the main semi-creative world.
 * Horizontal X,Z distance only; Y is never part of any decision.
 */
class MainWorldGuardTest {
   @Test
   void spawnRadiusUsesHorizontalDistanceOnly() {
      assertTrue(MainWorldFeature.insideSpawnRadius(0, 0, 0, 0, 30));
      assertTrue(MainWorldFeature.insideSpawnRadius(30, 0, 0, 0, 30));
      assertTrue(MainWorldFeature.insideSpawnRadius(21, 21, 0, 0, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(31, 0, 0, 0, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(22, 22, 0, 0, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(0, 0, 100, -100, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(0, 0, 0, 0, 0));
   }

   @Test
   void radiusBoundaryIsConsistent() {
      // 30-block radius is strict: exactly 30 is inside, 31 is outside.
      assertTrue(MainWorldFeature.insideSpawnRadius(18, 24, 0, 0, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(19, 24, 0, 0, 30));
   }

   @Test
   void columnKeyIgnoresY() {
      assertEquals("main;10;-7", MainWorldFeature.columnKey("main", 10, -7));
      assertEquals(MainWorldFeature.columnKey("main", 10, -7), MainWorldFeature.columnKey("main", 10, -7));
   }

   @Test
   void noOverflowOnExtremeCoordinates() {
      assertFalse(MainWorldFeature.insideSpawnRadius(30000000, 0, 0, 0, 30));
      assertFalse(MainWorldFeature.insideSpawnRadius(-30000000, -30000000, 0, 0, 30));
   }
}
