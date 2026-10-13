package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class MenuFanRulesTest {
   @Test
   void fanAnglesCenterAndSpread() {
      assertEquals(1, MenuFanFeature.fanAngles(1, 130.0).length);
      assertEquals(0.0, MenuFanFeature.fanAngles(1, 130.0)[0], 0.0001);
      double[] angles = MenuFanFeature.fanAngles(8, 140.0);
      assertEquals(8, angles.length);
      assertEquals(-70.0, angles[0], 0.0001);
      assertEquals(70.0, angles[7], 0.0001);
      assertEquals(0.0, angles[3] + angles[4], 0.0001);
      assertEquals(0, MenuFanFeature.fanAngles(0, 140.0).length);
   }

   @Test
   void rotateYKeepsHeightAndTurnsForward() {
      Vector forward = new Vector(0, 0, 1);
      Vector rotated = MenuFanFeature.rotateY(forward, 90.0);
      assertEquals(-1.0, rotated.getX(), 0.0001);
      assertEquals(0.0, rotated.getZ(), 0.0001);
      assertEquals(0.0, rotated.getY(), 0.0001);
      Vector up = new Vector(0, 1, 0);
      Vector unchanged = MenuFanFeature.rotateY(up, 123.0);
      assertEquals(1.0, unchanged.getY(), 0.0001);
   }
}
