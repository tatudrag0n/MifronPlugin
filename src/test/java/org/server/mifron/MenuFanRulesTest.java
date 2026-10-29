package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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

   @Test
   void hoveredIndexPicksClosestWithinAngle() {
      List<Vector> directions = List.of(new Vector(0, 0, 1), new Vector(1, 0, 0), new Vector(0, 0, -1));
      assertEquals(0, MenuFanFeature.hoveredIndex(directions, new Vector(0, 0, 1), 10.0));
      assertEquals(1, MenuFanFeature.hoveredIndex(directions, new Vector(1, 0, 0), 10.0));
      assertEquals(2, MenuFanFeature.hoveredIndex(directions, new Vector(0, 0, -1), 10.0));
      // Slightly off-axis but still inside the angle window.
      assertEquals(0, MenuFanFeature.hoveredIndex(directions, new Vector(0.1, 0, 1), 10.0));
      // Outside every window (looking up) returns -1.
      assertEquals(-1, MenuFanFeature.hoveredIndex(directions, new Vector(0, 1, 0), 10.0));
      assertEquals(-1, MenuFanFeature.hoveredIndex(List.of(), new Vector(0, 0, 1), 10.0));
      assertEquals(-1, MenuFanFeature.hoveredIndex(directions, new Vector(0, 0, 0), 10.0));
   }

   @Test
   void clickFollowsHighlightWhenHovered() {
      // The crosshair highlight decides the target: clicking a neighboring
      // hitbox opens the entry the player sees highlighted, not the hitbox.
      assertEquals(3, MenuFanFeature.selectedIndex(3, 1));
      assertEquals(3, MenuFanFeature.selectedIndex(3, 4));
      // With nothing highlighted the physically clicked entry opens.
      assertEquals(1, MenuFanFeature.selectedIndex(-1, 1));
   }
}
