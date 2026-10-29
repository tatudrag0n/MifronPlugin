package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** Ice-slide physics for the roller skate relic. */
class RelicSkatesTest {
   private static double speed(Vector v) {
      return Math.hypot(v.getX(), v.getZ());
   }

   private static Vector drive(Vector start, boolean sprinting, Vector wish, int steps) {
      Vector v = start.clone();
      for (int i = 0; i < steps; i++) v = RelicFeature.iceStep(v, wish, sprinting, false);
      return v;
   }

   @Test
   void glideDecaysThenStopsWithoutInput() {
      Vector v = RelicFeature.iceStep(new Vector(0.5, 0, 0), null, false, false);
      assertTrue(speed(v) <= 0.5 * RelicFeature.SKATE_FRICTION + 1.0E-9, "friction must blunt momentum each tick");
      Vector stopped = drive(new Vector(0.5, 0, 0), false, null, 400);
      assertEquals(0.0, speed(stopped), 0.0001, "glide comes to a hard stop below the cutoff");
   }

   @Test
   void sprintDriveRampsUpToTheGlideCap() {
      Vector v = drive(new Vector(0, 0, 0), true, new Vector(0, 0, 1), 200);
      assertTrue(speed(v) <= RelicFeature.SKATE_MAX_SPEED + 1.0E-9, "never faster than the ice cap");
      assertTrue(speed(v) >= RelicFeature.SKATE_MAX_SPEED * 0.9, "sprint thrust reaches the ice cap");
   }

   @Test
   void momentumCurvesTowardWhereThePlayerLooks() {
      Vector v = new Vector(0.4, 0, 0); // gliding east
      Vector wish = new Vector(0, 0, -1); // player now looks north
      for (int i = 0; i < 30; i++) v = RelicFeature.iceStep(v, wish, false, false);
      assertTrue(v.getZ() < 0, "a 90-degree look must bend the glide, not teleport it");
      assertTrue(v.getX() > 0, "existing momentum is retained while curving");
   }

   @Test
   void sneakingBrakesFasterThanPureGlide() {
      Vector sneak = RelicFeature.iceStep(new Vector(0.4, 0, 0), null, false, true);
      Vector glide = RelicFeature.iceStep(new Vector(0.4, 0, 0), null, false, false);
      assertTrue(speed(sneak) < speed(glide));
   }

   @Test
   void standingStillStaysStill() {
      Vector v = RelicFeature.iceStep(new Vector(0, 0, 0), null, false, false);
      assertEquals(0.0, speed(v), 0.0001);
   }
}
