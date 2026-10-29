package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class TutorialAnchorsTest {
   private static Location at(double x, double z) {
      return new Location(null, x, 0.0, z);
   }

   @Test
   void yawMatchesMinecraftCardinals() {
      // 0 = south (+Z), 90 = west (-X), 180 = north (-Z), 270 = east (+X).
      assertEquals(0.0F, TutorialFeature.yawTowards(at(0, 0), at(0, 10)), 0.01);
      assertEquals(180.0F, TutorialFeature.yawTowards(at(0, 0), at(0, -10)), 0.01);
      assertEquals(90.0F, TutorialFeature.yawTowards(at(0, 0), at(-10, 0)), 0.01);
      assertEquals(270.0F, TutorialFeature.yawTowards(at(0, 0), at(10, 0)), 0.01);
   }

   @Test
   void yawDeltaTakesTheShortWay() {
      assertEquals(90.0F, TutorialFeature.yawDelta(270.0F, 0.0F), 0.01);
      assertEquals(-90.0F, TutorialFeature.yawDelta(0.0F, 270.0F), 0.01);
      assertEquals(1.0F, TutorialFeature.yawDelta(179.0F, 180.0F), 0.01);
      assertEquals(0.0F, TutorialFeature.yawDelta(0.0F, 0.0F), 0.01);
   }

   @Test
   void anchorFacesArrivalSpawn() {
      // Live anchors: spawn z=20.5, kill anchor z=10.5, elite anchor z=-7.5 —
      // both anchors sit north of the spawn, so a mob at the anchor must face
      // south (+Z) toward the arriving player.
      assertEquals(0.0F, TutorialFeature.yawTowards(at(0.5, 10.5), at(0.5, 20.5)), 0.01);
      assertEquals(0.0F, TutorialFeature.yawTowards(at(0.5, -7.5), at(0.5, 20.5)), 0.01);
   }
}
