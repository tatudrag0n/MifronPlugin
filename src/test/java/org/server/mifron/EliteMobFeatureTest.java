package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class EliteMobFeatureTest {
   @Test
   void onlyEliteWardensGuaranteeTheResonanceCrystal() {
      assertTrue(EliteMobFeature.guaranteesResonanceDrop(EntityType.WARDEN));
      assertFalse(EliteMobFeature.guaranteesResonanceDrop(EntityType.ZOMBIE));
      assertFalse(EliteMobFeature.guaranteesResonanceDrop(EntityType.WITHER_SKELETON));
      assertFalse(EliteMobFeature.guaranteesResonanceDrop(EntityType.ENDER_DRAGON));
      assertFalse(EliteMobFeature.guaranteesResonanceDrop(null));
   }

   @Test
   void everyHostileMobTypeCanBecomeElite() {
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.ZOMBIE));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.SKELETON));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.CREEPER));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.ENDERMAN));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.WARDEN));
      // Non-Monster hostiles must be included too.
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.SLIME));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.MAGMA_CUBE));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.GHAST));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.SHULKER));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.PHANTOM));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.ENDER_DRAGON));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.WITHER));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.EVOKER));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.RAVAGER));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.GUARDIAN));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.SILVERFISH));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.BREEZE));
      assertTrue(EliteMobFeature.isEliteCandidate(EntityType.CREAKING));
   }

   @Test
   void passiveMobsAndNonMobsAreRejected() {
      assertFalse(EliteMobFeature.isEliteCandidate(EntityType.COW));
      assertFalse(EliteMobFeature.isEliteCandidate(EntityType.SHEEP));
      assertFalse(EliteMobFeature.isEliteCandidate(EntityType.VILLAGER));
      assertFalse(EliteMobFeature.isEliteCandidate(EntityType.ARMOR_STAND));
      assertFalse(EliteMobFeature.isEliteCandidate(EntityType.PLAYER));
      assertFalse(EliteMobFeature.isEliteCandidate(null));
   }
}
