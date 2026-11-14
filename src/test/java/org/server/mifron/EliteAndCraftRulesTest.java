package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * エリート改修と特殊クラフトの仕様を固定するテスト。
 *
 * <p>仕様（依頼）:
 * <ul>
 *   <li>狙撃弓は最大2倍まで引き絞れる</li>
 *   <li>第二形態は エルダーガーディアン / ピグリンブルート / ウィザー / エンダードラゴン / ウォーデン のみ</li>
 *   <li>腐死王系（ゾンビ・ハスク・ドラウンド）だけが Smite なしで復活する</li>
 *   <li>狙撃弓・連射式弩弓はレリックではなく特殊クラフト</li>
 * </ul>
 */
class EliteAndCraftRulesTest {

   // ------------------------------------------------------------------
   // 狙撃弓の引き絞り
   // ------------------------------------------------------------------

   @Test
   void sniperRatioMatchesVanillaAtFullDraw() {
      // 20tick = バニラのフルチャージ = 1.0倍
      assertEquals(1.0, SpecialCraftFeature.sniperRatio(20), 1.0e-9);
   }

   @Test
   void sniperRatioReachesTwiceAtDoubleDraw() {
      assertEquals(2.0, SpecialCraftFeature.sniperRatio(40), 1.0e-9);
   }

   @Test
   void sniperRatioIsCappedAtTwo() {
      assertEquals(2.0, SpecialCraftFeature.sniperRatio(200), 1.0e-9,
         "2倍を超えて伸びてはいけない");
   }

   @Test
   void sniperRatioHasAFloorForTinyDraws() {
      assertEquals(0.1, SpecialCraftFeature.sniperRatio(0), 1.0e-9);
      assertEquals(0.1, SpecialCraftFeature.sniperRatio(1), 1.0e-9);
   }

   @Test
   void sniperRatioIsMonotonic() {
      double previous = -1.0;
      for (int ticks = 0; ticks <= 60; ticks++) {
         double value = SpecialCraftFeature.sniperRatio(ticks);
         assertTrue(value >= previous, "倍率は tick に対して単調増加であるべき: " + ticks);
         previous = value;
      }
   }

   // ------------------------------------------------------------------
   // 第二形態の制限
   // ------------------------------------------------------------------

   @Test
   void phase2IsLimitedToTheFiveHeadlineBosses() {
      for (EntityType type : new EntityType[] {
         EntityType.ELDER_GUARDIAN, EntityType.PIGLIN_BRUTE, EntityType.WITHER,
         EntityType.ENDER_DRAGON, EntityType.WARDEN
      }) {
         assertTrue(EliteMobFeature.supportsPhase2(type), type + " は第二形態を持つ");
      }
   }

   @Test
   void phase2IsRemovedFromEverythingElse() {
      for (EntityType type : new EntityType[] {
         EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON,
         EntityType.CREEPER, EntityType.ENDERMAN, EntityType.BLAZE, EntityType.SPIDER,
         EntityType.GHAST, EntityType.RAVAGER, EntityType.WITCH, EntityType.PILLAGER,
         EntityType.GUARDIAN, EntityType.SHULKER, EntityType.PHANTOM
      }) {
         assertFalse(EliteMobFeature.supportsPhase2(type), type + " は第二形態を持たない");
      }
      assertFalse(EliteMobFeature.supportsPhase2(null));
   }

   // ------------------------------------------------------------------
   // 腐死王系の復活条件
   // ------------------------------------------------------------------

   @Test
   void undeadKingsAreTheThreeZombieVariants() {
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.ZOMBIE));
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.HUSK));
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.DROWNED));
      assertEquals(3, countUndeadKings());
   }

   @Test
   void otherUndeadDoNotRevive() {
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.SKELETON));
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.WITHER_SKELETON));
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.ZOMBIE_VILLAGER));
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.PHANTOM));
      assertFalse(EliteMobFeature.isUndeadKing(null));
   }

   private static int countUndeadKings() {
      return (int) Arrays.stream(EntityType.values())
         .filter(EliteMobFeature::isUndeadKing)
         .count();
   }

   // ------------------------------------------------------------------
   // レリックと特殊クラフトの住み分け
   // ------------------------------------------------------------------

   @Test
   void sniperBowAndRepeatingCrossbowAreNotRelics() {
      assertFalse(hasRelic("sniper_bow"), "狙撃弓は特殊クラフトへ移行した");
      assertFalse(hasRelic("repeating_crossbow"), "連射式弩弓は特殊クラフトへ移行した");
   }

   @Test
   void artemisIsARelic() {
      assertTrue(hasRelic("artemis"));
   }

   @Test
   void specialCraftIdsAreStable() {
      assertEquals("sniper_bow", SpecialCraftFeature.SNIPER_BOW_ID);
      assertEquals("repeating_crossbow", SpecialCraftFeature.REPEATING_CROSSBOW_ID);
   }

   private static boolean hasRelic(String id) {
      return Arrays.stream(RelicFeature.RelicType.values())
         .anyMatch(type -> type.id.equals(id));
   }
}
