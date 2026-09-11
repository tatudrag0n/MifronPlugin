package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvancementBonusRulesTest {
   @Test
   void framePercentsMatchConfigDefaults() {
      assertEquals(1, AdvancementBonusRules.percentForFrame("TASK", 1, 2, 5));
      assertEquals(2, AdvancementBonusRules.percentForFrame("GOAL", 1, 2, 5));
      assertEquals(5, AdvancementBonusRules.percentForFrame("CHALLENGE", 1, 2, 5));
   }

   @Test
   void completedAdvancementsAccumulate() {
      assertEquals(8, AdvancementBonusRules.totalPercent(List.of(1, 2, 5)));
      assertEquals(0, AdvancementBonusRules.totalPercent(List.of()));
   }

   @Test
   void incomeBonusAddsAdvancementAndReincarnationWithoutDoubleBase() {
      assertEquals(110, AdvancementBonusRules.incomeBonus(100, 5, 5));
      assertEquals(0, AdvancementBonusRules.incomeBonus(0, 10, 10));
   }

   @Test
   void specialUnlockOverridesFrameFallback() {
      assertEquals(5, AdvancementBonusRules.unlockOverride(Map.of("story/mine_diamond", 5), "minecraft:story/mine_diamond", 1));
      assertEquals(1, AdvancementBonusRules.unlockOverride(Map.of(), "minecraft:story/mine_stone", 1));
   }
}
