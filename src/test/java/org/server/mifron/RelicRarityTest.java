package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The transform chance must follow both material scarcity and relic power. */
class RelicRarityTest {
   @Test
   void chanceIsTheProductOfMaterialRarityAndPower() {
      for (RelicFeature.RelicType type : RelicFeature.RelicType.values()) {
         double expected = type.materialRarity.baseChance * type.power.multiplier;
         assertEquals(expected, type.chance, 1.0e-12, type.id);
         assertTrue(type.chance > 0.0, type.id + " must be obtainable");
         assertTrue(type.chance <= 0.005, type.id + " must stay a rare drop");
      }
   }

   @Test
   void rarerBaseMaterialGivesABetterChanceAtTheSamePower() {
      for (RelicFeature.RelicPower power : RelicFeature.RelicPower.values()) {
         List<RelicFeature.RelicType> types = Arrays.stream(RelicFeature.RelicType.values())
            .filter(type -> type.power == power)
            .sorted(Comparator.comparingInt(type -> type.materialRarity.ordinal()))
            .toList();
         for (int i = 1; i < types.size(); i++) {
            RelicFeature.RelicType previous = types.get(i - 1);
            RelicFeature.RelicType current = types.get(i);
            if (previous.materialRarity == current.materialRarity) continue;
            assertTrue(current.chance > previous.chance,
               current.id + " (" + current.materialRarity + ") should beat " + previous.id);
         }
      }
   }

   @Test
   void strongerRelicIsRarerAtTheSameMaterialRarity() {
      for (RelicFeature.MaterialRarity rarity : RelicFeature.MaterialRarity.values()) {
         RelicFeature.RelicType minor = find(rarity, RelicFeature.RelicPower.MINOR);
         RelicFeature.RelicType medium = find(rarity, RelicFeature.RelicPower.MEDIUM);
         RelicFeature.RelicType major = find(rarity, RelicFeature.RelicPower.MAJOR);
         if (minor != null && medium != null) assertTrue(minor.chance > medium.chance, rarity + " minor > medium");
         if (medium != null && major != null) assertTrue(medium.chance > major.chance, rarity + " medium > major");
         if (minor != null && major != null) assertTrue(minor.chance > major.chance, rarity + " minor > major");
      }
   }

   @Test
   void initialBalanceMatchesTheIntendedExamples() {
      // Calibration point requested by the owner.
      assertEquals(0.0020, RelicFeature.RelicType.RESONANCE_CRYSTAL.chance, 1.0e-12,
         "Resonance Crystal must be 0.20%");
      // The netherite sword used to be practically impossible (0.001%).
      assertTrue(RelicFeature.RelicType.DECAYED_SWORD.chance > 0.002,
         "netherite-sword relic needs a reasonable chance");
      // Flight, any-enchant and grapple relics must stay chase items.
      assertTrue(RelicFeature.RelicType.JETPACK.chance < 0.0005);
      assertTrue(RelicFeature.RelicType.HOOKSHOT.chance < 0.0003);
      // Common-mob sources should not be the easiest route to strong relics.
      assertTrue(RelicFeature.RelicType.DASHFOOT.chance < 0.001);
   }

   private static RelicFeature.RelicType find(RelicFeature.MaterialRarity rarity, RelicFeature.RelicPower power) {
      return Arrays.stream(RelicFeature.RelicType.values())
         .filter(type -> type.materialRarity == rarity && type.power == power)
         .findFirst().orElse(null);
   }
}
