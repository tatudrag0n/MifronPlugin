package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JobLevelRulesTest {
   private static final int BASE = 100;
   private static final double GROWTH = 0.0;
   private static final int MAX = 50;

   @Test
   void levelOneAtZeroXp() {
      assertEquals(1, JobLevelRules.levelForXp(0, BASE, GROWTH, MAX));
      assertEquals(1, JobLevelRules.levelForXp(BASE - 1, BASE, GROWTH, MAX));
   }

   @Test
   void flatGrowthLevelBoundaries() {
      assertEquals(2, JobLevelRules.levelForXp(BASE, BASE, GROWTH, MAX));
      assertEquals(2, JobLevelRules.levelForXp(2 * BASE - 1, BASE, GROWTH, MAX));
      assertEquals(3, JobLevelRules.levelForXp(2 * BASE, BASE, GROWTH, MAX));
      assertEquals(0, JobLevelRules.xpForLevel(1, BASE, GROWTH, MAX));
      assertEquals(BASE, JobLevelRules.xpForLevel(2, BASE, GROWTH, MAX));
      assertEquals(2 * BASE, JobLevelRules.xpForLevel(3, BASE, GROWTH, MAX));
   }

   @Test
   void growthIncreasesCosts() {
      long level2 = JobLevelRules.xpForLevel(2, BASE, 15.0, MAX);
      long level3 = JobLevelRules.xpForLevel(3, BASE, 15.0, MAX);
      assertEquals(BASE, level2);
      assertEquals(BASE + 115, level3);
      assertEquals(2, JobLevelRules.levelForXp(BASE + 114, BASE, 15.0, MAX));
      assertEquals(3, JobLevelRules.levelForXp(BASE + 115, BASE, 15.0, MAX));
   }

   @Test
   void maxLevelIsCapped() {
      assertEquals(MAX, JobLevelRules.levelForXp(Long.MAX_VALUE, BASE, GROWTH, MAX));
      assertEquals(0, JobLevelRules.xpNeededForNext(MAX, BASE, GROWTH, MAX));
   }

   @Test
   void progressAndNextNeeded() {
      assertEquals(40, JobLevelRules.xpIntoLevel(BASE + 40, 2, BASE, GROWTH, MAX));
      assertEquals(BASE - 40, JobLevelRules.xpNeededForNext(2, BASE, GROWTH, MAX) - 40);
      assertEquals(BASE, JobLevelRules.xpNeededForNext(2, BASE, GROWTH, MAX));
      assertEquals(BASE, JobLevelRules.xpNeededForNext(1, BASE, GROWTH, MAX));
   }

   @Test
   void bonusPercentScalesAndCaps() {
      assertEquals(0.0, JobLevelRules.bonusPercent(1, 0.5, 25.0));
      assertEquals(4.5, JobLevelRules.bonusPercent(10, 0.5, 25.0));
      assertEquals(25.0, JobLevelRules.bonusPercent(100, 0.5, 25.0));
      assertEquals(0.0, JobLevelRules.bonusPercent(10, 0.5, 0.0));
   }
}
