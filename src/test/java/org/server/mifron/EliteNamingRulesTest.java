package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/**
 * エリートの固有の名前と第二形態の対象制限は、サーバー不要の純粋関数なので直接検証する。
 */
class EliteNamingRulesTest {
   @Test
   void zombieFamilyUsesTheUndeadKingTitles() {
      assertEquals("腐死王", EliteMobFeature.eliteTitle(EntityType.ZOMBIE));
      assertEquals("砂漠の腐死王", EliteMobFeature.eliteTitle(EntityType.HUSK));
      assertEquals("海淵の腐死王", EliteMobFeature.eliteTitle(EntityType.DROWNED));
   }

   @Test
   void namedElitesUseTheirOwnTitle() {
      assertEquals("異空の支配者", EliteMobFeature.eliteTitle(EntityType.ENDERMAN));
      assertEquals("極炎の化身", EliteMobFeature.eliteTitle(EntityType.BLAZE));
   }

   @Test
   void unnamedElitesFallBackToTheVanillaName() {
      assertEquals("Zombie Villager", EliteMobFeature.eliteTitle(EntityType.ZOMBIE_VILLAGER));
      assertEquals("Wither Skeleton", EliteMobFeature.eliteTitle(EntityType.WITHER_SKELETON));
      assertEquals("Skeleton", EliteMobFeature.eliteTitle(EntityType.SKELETON));
      assertEquals("", EliteMobFeature.eliteTitle(null));
   }

   @Test
   void onlyTheFiveBossTypesReachTheSecondPhase() {
      assertTrue(EliteMobFeature.supportsPhase2(EntityType.ELDER_GUARDIAN));
      assertTrue(EliteMobFeature.supportsPhase2(EntityType.PIGLIN_BRUTE));
      assertTrue(EliteMobFeature.supportsPhase2(EntityType.WITHER));
      assertTrue(EliteMobFeature.supportsPhase2(EntityType.ENDER_DRAGON));
      assertTrue(EliteMobFeature.supportsPhase2(EntityType.WARDEN));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.ZOMBIE));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.SKELETON));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.BLAZE));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.ENDERMAN));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.RAVAGER));
      assertFalse(EliteMobFeature.supportsPhase2(EntityType.PIGLIN));
      assertFalse(EliteMobFeature.supportsPhase2(null));
   }

   @Test
   void undeadKingsAreTheZombieFamilyOnly() {
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.ZOMBIE));
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.HUSK));
      assertTrue(EliteMobFeature.isUndeadKing(EntityType.DROWNED));
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.SKELETON));
      assertFalse(EliteMobFeature.isUndeadKing(EntityType.PLAYER));
      assertFalse(EliteMobFeature.isUndeadKing(null));
   }

   @Test
   void everyEliteCandidateStillHasANonEmptyTitle() {
      for (EntityType type : EntityType.values()) {
         if (!EliteMobFeature.isEliteCandidate(type)) continue;
         assertFalse(EliteMobFeature.eliteTitle(type).isEmpty(), type.name());
      }
   }

}
