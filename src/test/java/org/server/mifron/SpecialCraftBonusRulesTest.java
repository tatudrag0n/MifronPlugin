package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** 特殊クラフト20種の効果ルール（装備の軽減率・射程判定・回復量）のテスト。 */
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
        assertEquals(22, SpecialCraftCatalog.all().size());
    }

    @Test
    void kitRejectsUnknownIds() {
        // カタログ外のID（他Featureの特殊クラフト）は生成しない
        assertTrue(this.plugin.specialCraftKit.create("night_vision_scope") == null);
        assertTrue(this.plugin.specialCraftKit.create("does_not_exist") == null);
        assertTrue(this.plugin.specialCraftKit.idOf(new ItemStack(Material.STICK)) == null);
        assertFalse(this.plugin.specialCraftKit.is(new ItemStack(Material.STICK), "field_bandage"));
    }

    @Test
    void wearableDamageRules() {
        assertEquals(0.65, SpecialCraftWearableFeature.fireDamageMultiplier(), 1e-9);
        assertEquals(0.60, SpecialCraftWearableFeature.fallDamageMultiplier(), 1e-9);
        assertTrue(SpecialCraftWearableFeature.isFireDamage(EntityDamageEvent.DamageCause.FIRE));
        assertTrue(SpecialCraftWearableFeature.isFireDamage(EntityDamageEvent.DamageCause.FIRE_TICK));
        assertTrue(SpecialCraftWearableFeature.isFireDamage(EntityDamageEvent.DamageCause.LAVA));
        assertFalse(SpecialCraftWearableFeature.isFireDamage(EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        assertTrue(SpecialCraftWearableFeature.isFallDamage(EntityDamageEvent.DamageCause.FALL));
        assertFalse(SpecialCraftWearableFeature.isFallDamage(EntityDamageEvent.DamageCause.FIRE));

        // 何も着ていなければ軽減なし
        assertEquals(1.0,
            SpecialCraftWearableFeature.damageMultiplier(EntityDamageEvent.DamageCause.FIRE, false, false), 1e-9);
        // 外套のみ
        assertEquals(0.65,
            SpecialCraftWearableFeature.damageMultiplier(EntityDamageEvent.DamageCause.FIRE, true, false), 1e-9);
        // 命綱のみ
        assertEquals(0.60,
            SpecialCraftWearableFeature.damageMultiplier(EntityDamageEvent.DamageCause.FALL, false, true), 1e-9);
        // 関係ないダメージは軽減しない
        assertEquals(1.0,
            SpecialCraftWearableFeature.damageMultiplier(EntityDamageEvent.DamageCause.ENTITY_ATTACK, true, true), 1e-9);
    }

    @Test
    void consumableHealingAndRepairRules() {
        assertEquals(14.0, SpecialCraftConsumableFeature.healedHealth(10.0, 20.0), 1e-9);
        assertEquals(20.0, SpecialCraftConsumableFeature.healedHealth(18.0, 20.0), 1e-9);
        assertEquals(0, SpecialCraftConsumableFeature.repairedDamage(3));
        assertEquals(5, SpecialCraftConsumableFeature.repairedDamage(20));
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
        assertTrue(this.plugin.specialCraftKit.tryUse(player, "field_bandage", 25));
        int left = this.plugin.specialCraftKit.remaining(player, "field_bandage");
        assertTrue(left >= 24 && left <= 25, "残り秒数が想定外: " + left);
        assertFalse(this.plugin.specialCraftKit.tryUse(player, "field_bandage", 25));
        // 別IDは独立している
        assertTrue(this.plugin.specialCraftKit.tryUse(player, "repair_hammer", 60));
        this.plugin.specialCraftKit.clearCooldown(player, "field_bandage");
        assertEquals(0, this.plugin.specialCraftKit.remaining(player, "field_bandage"));
    }
}
