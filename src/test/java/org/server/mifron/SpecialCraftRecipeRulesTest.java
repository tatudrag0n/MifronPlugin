package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** 高度な作業台の特殊クラフト（カタログとレシピ照合）の純粋ルールテスト。 */
class SpecialCraftRecipeRulesTest {

    private static Material[] grid(Material... cells) {
        Material[] grid = new Material[9];
        for (int i = 0; i < cells.length && i < 9; i++) {
            grid[i] = cells[i];
        }
        return grid;
    }

    @Test
    void catalogKeepsOnlyTheFiveChosenCrafts() {
        assertEquals(5, SpecialCraftCatalog.all().size());
        Set<String> ids = new HashSet<>();
        for (SpecialCraftCatalog.Def def : SpecialCraftCatalog.all()) {
            assertNotNull(def.id(), "id は必須");
            assertTrue(ids.add(def.id()), "id が重複している: " + def.id());
            assertNotNull(def.material(), "完成品の素材が無い: " + def.id());
            assertFalse(def.lore().isEmpty(), "説明文が無い: " + def.id());
            assertNotNull(def.recipe(), "レシピが無い: " + def.id());
        }
        // バニラやレリックで代替できるものは残さない。
        for (String removed : new String[] {"fireproof_cloak", "safety_harness", "artisan_apron",
            "deep_diver_helm", "wind_feather", "portable_crafting_table", "portable_ender_chest",
            "campfire_kit", "folding_boat", "scaffold_pack", "field_bandage", "antidote_honey",
            "repair_hammer", "blow_dart", "smoke_bomb", "spider_lens", "frost_vial"}) {
            assertNull(SpecialCraftCatalog.byId(removed), "削除したはずのIDが残っている: " + removed);
        }
    }

    @Test
    void shapedRecipeMatchesAnywhereInTheTable() {
        SpecialCraftCatalog.Recipe scythe = SpecialCraftCatalog.byId("harvest_scythe").recipe();
        // 左上に置いた場合（鉄 鉄 小麦 / 空 空 棒）
        assertTrue(SpecialCraftRecipeRules.matches(scythe,
            grid(Material.IRON_INGOT, Material.IRON_INGOT, Material.WHEAT,
                null, null, Material.STICK)));
        // 1行下にずらした場合
        assertTrue(SpecialCraftRecipeRules.matches(scythe,
            grid(null, null, null,
                Material.IRON_INGOT, Material.IRON_INGOT, Material.WHEAT,
                null, null, Material.STICK)));
        // 別物を混ぜると成立しない
        assertFalse(SpecialCraftRecipeRules.matches(scythe,
            grid(Material.IRON_INGOT, Material.IRON_INGOT, Material.WHEAT,
                Material.DIRT, null, Material.STICK)));
        // 素材が足りない
        assertFalse(SpecialCraftRecipeRules.matches(scythe,
            grid(Material.IRON_INGOT, Material.IRON_INGOT,
                null, Material.STICK)));
    }

    @Test
    void shapelessRecipeIgnoresOrder() {
        SpecialCraftCatalog.Recipe light = SpecialCraftCatalog.byId("light_drop").recipe();
        assertTrue(SpecialCraftRecipeRules.matches(light,
            grid(Material.GLOWSTONE_DUST, Material.GLASS_BOTTLE)));
        assertTrue(SpecialCraftRecipeRules.matches(light,
            grid(null, Material.GLASS_BOTTLE, null,
                Material.GLOWSTONE_DUST, null, null)));
        // 余分な材料があると成立しない
        assertFalse(SpecialCraftRecipeRules.matches(light,
            grid(Material.GLOWSTONE_DUST, Material.GLASS_BOTTLE, Material.STICK)));
        // 足りない
        assertFalse(SpecialCraftRecipeRules.matches(light, grid(Material.GLOWSTONE_DUST)));
    }

    @Test
    void duplicateIngredientsNeedTheSameCount() {
        SpecialCraftCatalog.Recipe net = SpecialCraftCatalog.byId("throw_net").recipe();
        assertTrue(SpecialCraftRecipeRules.matches(net,
            grid(Material.STRING, Material.STRING, Material.IRON_BARS)));
        assertFalse(SpecialCraftRecipeRules.matches(net,
            grid(Material.STRING, Material.IRON_BARS, Material.IRON_BARS)));
    }

    @Test
    void everyRecipeMatchesItsOwnIngredients() {
        for (SpecialCraftCatalog.Def def : SpecialCraftCatalog.all()) {
            Material[] grid;
            if (def.recipe().shapeless()) {
                grid = new Material[9];
                int slot = 0;
                for (Material material : def.recipe().ingredients()) {
                    if (material != null) {
                        grid[slot] = material;
                        slot++;
                    }
                }
            } else {
                grid = def.recipe().pattern().clone();
            }
            assertEquals(def.id(), SpecialCraftRecipeRules.findMatch(SpecialCraftCatalog.all(), grid),
                "自分のレシピで一致しない（または別レシピと衝突）: " + def.id());
        }
    }

    @Test
    void emptyTableMatchesNothing() {
        assertNull(SpecialCraftRecipeRules.findMatch(SpecialCraftCatalog.all(), new Material[9]));
        assertNull(SpecialCraftRecipeRules.findMatch(SpecialCraftCatalog.all(),
            grid(Material.DIRT)));
    }

    @Test
    void unknownIdReturnsNull() {
        assertNull(SpecialCraftCatalog.byId("does_not_exist"));
        assertNull(SpecialCraftCatalog.byId(null));
    }

    @Test
    void shapeNormalizationTrimsEmptyRowsAndColumns() {
        SpecialCraftRecipeRules.Shape shape = SpecialCraftRecipeRules.shape(
            grid(null, null, null,
                null, Material.STICK, null));
        assertEquals(1, shape.rows());
        assertEquals(1, shape.cols());
        assertEquals(Material.STICK, shape.cells()[0]);

        SpecialCraftRecipeRules.Shape empty = SpecialCraftRecipeRules.shape(new Material[9]);
        assertTrue(empty.isEmpty());
    }
}
