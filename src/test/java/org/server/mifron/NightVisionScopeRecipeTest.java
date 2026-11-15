package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** 暗視スコープ（特殊クラフト品）のレシピ判定のテスト。 */
class NightVisionScopeRecipeTest {

    private static int[] slots(int spyglassSlot, int potionSlot) {
        int[] kinds = new int[9];
        kinds[spyglassSlot] = SpecialCraftFeature.SLOT_SPYGLASS;
        kinds[potionSlot] = SpecialCraftFeature.SLOT_NIGHT_VISION_POTION;
        return kinds;
    }

    @Test
    void spyglassAndNightVisionPotionMatches() {
        assertTrue(SpecialCraftFeature.matchesNightVisionScope(slots(0, 8)));
        assertTrue(SpecialCraftFeature.matchesNightVisionScope(slots(4, 1)));
        assertTrue(SpecialCraftFeature.matchesNightVisionScope(slots(3, 5)));
    }

    @Test
    void missingIngredientDoesNotMatch() {
        int[] onlySpyglass = new int[9];
        onlySpyglass[4] = SpecialCraftFeature.SLOT_SPYGLASS;
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(onlySpyglass));
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(new int[9]));
    }

    @Test
    void extraIngredientDoesNotMatch() {
        int[] extraSpyglass = slots(0, 1);
        extraSpyglass[4] = SpecialCraftFeature.SLOT_SPYGLASS;
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(extraSpyglass));

        int[] unknownIngredient = slots(0, 1);
        unknownIngredient[4] = -1;
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(unknownIngredient));
    }

    @Test
    void wrongMatrixSizeDoesNotMatch() {
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(null));
        assertFalse(SpecialCraftFeature.matchesNightVisionScope(new int[4]));
    }
}
