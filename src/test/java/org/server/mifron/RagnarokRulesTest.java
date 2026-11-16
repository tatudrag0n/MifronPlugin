package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ラグナロクの秒数丸め・残り時間表示・ボスバー進捗はサーバー不要の純粋関数なので直接検証する。
 */
class RagnarokRulesTest {
   @Test
   void durationIsClampedIntoConfiguredRange() {
      assertEquals(300, RagnarokRules.clampDuration(300, 10, 3600));
      assertEquals(10, RagnarokRules.clampDuration(1, 10, 3600));
      assertEquals(3600, RagnarokRules.clampDuration(99999, 10, 3600));
      assertEquals(1, RagnarokRules.clampDuration(0, 0, 0));
   }

   @Test
   void invertedBoundsDoNotEscape() {
      assertEquals(10, RagnarokRules.clampDuration(5, 10, 3));
   }

   @Test
   void activityFollowsUntilTimestamp() {
      assertTrue(RagnarokRules.isActive(1000L, 2000L));
      assertFalse(RagnarokRules.isActive(2000L, 2000L));
      assertFalse(RagnarokRules.isActive(3000L, 2000L));
      assertFalse(RagnarokRules.isActive(1000L, 0L));
   }

   @Test
   void remainingNeverGoesNegative() {
      assertEquals(0L, RagnarokRules.remainingMillis(5000L, 1000L));
      assertEquals(1000L, RagnarokRules.remainingMillis(5000L, 6000L));
   }

   @Test
   void remainingIsFormattedAsMinutesAndSeconds() {
      assertEquals("05:00", RagnarokRules.formatRemaining(300000L));
      assertEquals("00:09", RagnarokRules.formatRemaining(9400L));
      assertEquals("00:00", RagnarokRules.formatRemaining(-1L));
      assertEquals("60:00", RagnarokRules.formatRemaining(3600000L));
   }

   @Test
   void progressStaysWithinZeroAndOne() {
      assertEquals(1.0D, RagnarokRules.progress(1000L, 1000L), 1.0e-9);
      assertEquals(0.5D, RagnarokRules.progress(500L, 1000L), 1.0e-9);
      assertEquals(0.0D, RagnarokRules.progress(-100L, 1000L), 1.0e-9);
      assertEquals(0.0D, RagnarokRules.progress(100L, 0L), 1.0e-9);
      assertEquals(1.0D, RagnarokRules.progress(5000L, 1000L), 1.0e-9);
   }
}
