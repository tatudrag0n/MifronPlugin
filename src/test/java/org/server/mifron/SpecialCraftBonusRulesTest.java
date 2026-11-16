package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** 特殊クラフトの効果ルール（射程判定・クールダウン・アイテム生成）のテスト。 */
class SpecialCraftBonusRulesTest {

    private ServerMock server;
    private Mifron plugin;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
        this.plugin = MockBukkit.load(Mifron.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void kitCreatesEveryCatalogItemWithItsId() {
        for (SpecialCraftCatalog.Def def : SpecialCraftCatalog.all()) {
            ItemStack item = this.plugin.specialCraftKit.create(def.id());
            assertNotNull(item, "生成できない: " + def.id());
            assertEquals(def.material(), item.getType(), def.id());
            assertEquals(def.amount(), item.getAmount(), def.id());
            assertEquals(def.id(), this.plugin.specialCraftKit.idOf(item), def.id());
            assertTrue(this.plugin.specialCraftKit.is(item, def.id()), def.id());
            assertNotNull(item.getItemMeta());
            assertNotNull(item.getItemMeta().getDisplayName(), def.id());
            assertFalse(item.getItemMeta().getLore().isEmpty(), def.id());
        }
        assertEquals(5, SpecialCraftCatalog.all().size());
    }

    @Test
    void theWeaponsKeepTheirResourcePackModels() {
        ItemStack sniper = this.plugin.specialCraftKit.create("sniper_bow");
        assertNotNull(sniper);
        assertNotNull(sniper.getItemMeta().getItemModel(), "狙撃弓のモデルが無い");
        assertEquals("mifron:special/sniper_bow",
            sniper.getItemMeta().getItemModel().toString());
        ItemStack repeater = this.plugin.specialCraftKit.create("repeating_crossbow");
        assertNotNull(repeater);
        assertEquals("mifron:special/repeating_crossbow",
            repeater.getItemMeta().getItemModel().toString());
        // 専用テクスチャを持たないものはモデル無し
        ItemStack scythe = this.plugin.specialCraftKit.create("harvest_scythe");
        assertNotNull(scythe);
        assertEquals(null, scythe.getItemMeta().getItemModel());
    }

    @Test
    void kitRejectsUnknownIds() {
        assertTrue(this.plugin.specialCraftKit.create("field_bandage") == null,
            "削除したIDが生成できてしまう");
        assertTrue(this.plugin.specialCraftKit.create("does_not_exist") == null);
        assertTrue(this.plugin.specialCraftKit.idOf(new ItemStack(Material.STICK)) == null);
        assertFalse(this.plugin.specialCraftKit.is(new ItemStack(Material.STICK), "throw_net"));
    }

    @Test
    void grantableIdsMatchTheCatalogPlusTheThreeExtras() {
        // 管理者コマンドの一覧はカタログから自動生成される（特殊クラフト5 + 暗視鏡/作業台/かまど3）。
        assertEquals(8, SpecialCraftFeature.grantableIds().size());
        assertTrue(SpecialCraftFeature.grantableIds().containsAll(
            java.util.List.of("sniper_bow", "repeating_crossbow", "harvest_scythe",
                "light_drop", "throw_net", "night_vision_scope",
                "advanced_crafting_table", "steel_furnace")));
    }

    @Test
    void combatRangeRules() {
        World world = this.server.addSimpleWorld("craft-test");
        Location eye = new Location(world, 0.5, 64.0, 0.5);
        Vector look = new Vector(0, 0, 1);

        // 前方6ブロック・真っ直ぐ → 投網が届く
        assertTrue(SpecialCraftCombatFeature.inFront(eye, look, new Location(world, 0.5, 64.0, 6.5), 8.0));
        assertTrue(SpecialCraftCombatFeature.withinNetReach(eye, look,
            new Location(world, 0.5, 64.0, 6.5), 8.0, 4.0));

        // 横に5ブロックずれている → 前方でも投網の射程外（横4まで）
        assertFalse(SpecialCraftCombatFeature.withinNetReach(eye, look,
            new Location(world, 5.5, 64.0, 6.5), 8.0, 4.0));
        // 横3ブロックのずれは範囲内
        assertTrue(SpecialCraftCombatFeature.withinNetReach(eye, look,
            new Location(world, 3.5, 64.0, 6.5), 8.0, 4.0));

        // 後ろ → 常に範囲外
        assertFalse(SpecialCraftCombatFeature.inFront(eye, look, new Location(world, 0.5, 64.0, -6.5), 8.0));
        // 遠すぎる → 範囲外
        assertFalse(SpecialCraftCombatFeature.withinRadius(eye, new Location(world, 0.5, 64.0, 9.5), 8.0));
        assertTrue(SpecialCraftCombatFeature.withinRadius(eye, new Location(world, 0.5, 64.0, 7.5), 8.0));
        // ワールドが違う/不正
        assertFalse(SpecialCraftCombatFeature.withinRadius(eye, new Location(null, 0.5, 64.0, 0.5), 8.0));
        assertFalse(SpecialCraftCombatFeature.inFront(null, look, eye, 8.0));
    }

    @Test
    void cooldownGateBlocksRepeatedUse() {
        var player = this.server.addPlayer("CraftBot");
        assertTrue(this.plugin.specialCraftKit.tryUse(player, "throw_net", 15));
        int left = this.plugin.specialCraftKit.remaining(player, "throw_net");
        assertTrue(left >= 14 && left <= 15, "残り秒数が想定外: " + left);
        assertFalse(this.plugin.specialCraftKit.tryUse(player, "throw_net", 15));
        // 別IDは独立している
        assertTrue(this.plugin.specialCraftKit.tryUse(player, "harvest_scythe", 4));
        this.plugin.specialCraftKit.clearCooldown(player, "throw_net");
        assertEquals(0, this.plugin.specialCraftKit.remaining(player, "throw_net"));
    }
}
