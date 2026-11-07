package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Regression checks for the relic balance pass and new special items. */
class RelicUpdateRulesTest {
   @Test
   void antiGravityMaceLaunchesAndKeepsClimbing() {
      assertTrue(RelicFeature.MACE_LAUNCH_VELOCITY > 0.0);
      assertTrue(RelicFeature.MACE_LAUNCH_MILLIS > 0L);
      assertEquals(RelicFeature.MACE_CLIMB_ACCEL, RelicFeature.maceClimbY(0.0), 1.0e-9);
      assertTrue(RelicFeature.maceClimbY(0.0) > 0.0);
   }

   @Test
   void maceKnockbackIsStronglyUpward() {
      assertTrue(RelicFeature.maceKnockbackY(0.0) >= 0.9);
      assertTrue(RelicFeature.maceKnockbackY(2.0) > 2.0);
      assertTrue(RelicFeature.maceKnockbackY(3.5) > RelicFeature.maceKnockbackY(1.0));
      assertEquals(3.2, RelicFeature.maceKnockbackY(100.0), 1.0e-9);
   }

   @Test
   void ancientSlashArmorUsesFiveBlockFiveDamageNoCooldown() {
      assertEquals(5.0, RelicFeature.ANCIENT_SLASH_DAMAGE, 1.0e-9);
      assertEquals(5.0, RelicFeature.ANCIENT_SLASH_RADIUS, 1.0e-9);
      assertEquals(0L, RelicFeature.ANCIENT_SLASH_COOLDOWN_MILLIS);
   }

   @Test
   void gaeBolgDashIsFastEnoughToReachItsTarget() {
      assertTrue(RelicFeature.GAYBOLG_DASH_SPEED > 2.1);
      assertTrue(RelicFeature.GAYBOLG_DASH_TICKS > 0);
      assertTrue(RelicFeature.GAYBOLG_DASH_ARRIVE_DISTANCE > 0.0);
      assertEquals(50.0, RelicFeature.GAYBOLG_RANGE, 1.0e-9);
      assertEquals(5000L, RelicFeature.GAYBOLG_COOLDOWN_MILLIS);
   }

   @Test
   void excaliburHasShortCooldownAndLongForwardRange() {
      assertEquals(15000L, RelicFeature.EXCALIBUR_COOLDOWN_MILLIS);
      assertEquals(64.0, RelicFeature.EXCALIBUR_SLASH_RANGE, 1.0e-9);
      assertEquals(0.0, RelicFeature.EXCALIBUR_SLASH_CONE_DOT, 1.0e-9);
      assertTrue(RelicFeature.EXCALIBUR_COUNTER_DURATION_MILLIS > 0L);
      assertTrue(RelicFeature.EXCALIBUR_COUNTER_DAMAGE >= 100000.0);
   }

   @Test
   void repeatingCrossbowLoadsOneArrowEveryPointThreeSeconds() {
      assertEquals(50, RelicFeature.REPEATING_MAX_AMMO);
      assertEquals(6, RelicFeature.REPEATING_RELOAD_INTERVAL_TICKS);
      assertTrue(RelicFeature.REPEATING_AUTO_INTERVAL_TICKS < RelicFeature.REPEATING_RELOAD_INTERVAL_TICKS);
   }

   @Test
   void sniperZoomScalesSmoothlyWithDrawProgress() {
      assertEquals(20, RelicFeature.SNIPER_DRAW_TICKS);
      assertEquals(1.0, RelicFeature.SNIPER_DRAW_TIME_MULTIPLIER, 1.0e-9);
      assertEquals(1.0, RelicFeature.sniperZoomFactor(0L), 1.0e-9);
      assertEquals(RelicFeature.SNIPER_FULL_DRAW_FOV,
         RelicFeature.sniperZoomFactor(RelicFeature.SNIPER_DRAW_MILLIS), 1.0e-9);

      double previous = 1.0;
      for (long elapsed = 0; elapsed <= RelicFeature.SNIPER_DRAW_MILLIS; elapsed += 100L) {
         double current = RelicFeature.sniperZoomFactor(elapsed);
         assertTrue(current <= previous + 1.0e-9, "zoom must increase monotonically");
         previous = current;
      }
      assertTrue(RelicFeature.sniperMovementSpeedModifier(RelicFeature.SNIPER_FULL_DRAW_FOV) < 0.0);
      assertEquals(0.0, RelicFeature.sniperMovementSpeedModifier(1.0), 1.0e-9);
   }

   @Test
   void relicsUseTheFixedThousandDurabilityPool() {
      assertEquals(1000, RelicFeature.RELIC_MAX_DURABILITY);
   }

   @Test
   void armorPenaltyScalesPerPieceAndRespectsTheCap() {
      assertEquals(0.0, RelicFeature.armorMovementPenalty(0, 0.02, 0.08), 1.0e-9);
      assertEquals(-0.04, RelicFeature.armorMovementPenalty(2, 0.02, 0.08), 1.0e-9);
      assertEquals(-0.08, RelicFeature.armorMovementPenalty(4, 0.02, 0.08), 1.0e-9);
      assertEquals(-0.08, RelicFeature.armorMovementPenalty(10, 0.02, 0.08), 1.0e-9);
   }

   @Test
   void relicCatalogReflectsTheNewAndRemovedItems() {
      assertTrue(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("endless_grimoire")));
      assertTrue(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("infinite_bundle")));
      assertTrue(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("repeating_crossbow")));
      assertFalse(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("sixshot_crossbow")));
      assertTrue(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("ancient_slash_armor")));
      assertFalse(Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals("infinite_blaze")));
      assertTrue(RelicFeature.RelicType.LIGHTNING.displayName.contains("呼雷の銅杖"));
   }
}
