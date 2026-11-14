package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  // ---- エリート第2弾: 復活と固有行動 --------------------------------

  @Test
  void undeadKingRevivesWhenWeaponHasNoSmite() {
    assertTrue(EliteMobFeature.shouldReviveUndeadKing(EntityType.ZOMBIE, false, 0, 3));
    assertTrue(EliteMobFeature.shouldReviveUndeadKing(EntityType.HUSK, false, 1, 3));
    assertTrue(EliteMobFeature.shouldReviveUndeadKing(EntityType.DROWNED, false, 2, 3));
  }

  @Test
  void undeadKingStaysDeadWhenKilledWithSmite() {
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.ZOMBIE, true, 0, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.HUSK, true, 0, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.DROWNED, true, 0, 3));
  }

  @Test
  void undeadKingRevivalStopsAtTheCap() {
    assertTrue(EliteMobFeature.shouldReviveUndeadKing(EntityType.ZOMBIE, false, 2, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.ZOMBIE, false, 3, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.ZOMBIE, false, 99, 3));
  }

  @Test
  void nonUndeadKingNeverRevives() {
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.ENDERMAN, false, 0, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.BLAZE, false, 0, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(EntityType.SKELETON, false, 0, 3));
    assertFalse(EliteMobFeature.shouldReviveUndeadKing(null, false, 0, 3));
  }

  @Test
  void undeadKingRevivalLimitDefaultsToThree() {
    assertEquals(3, EliteMobFeature.undeadKingRevivalLimit());
  }

  @Test
  void signatureAbilityTypesAreMapped() {
    assertTrue(EliteMobFeature.isVoidSovereign(EntityType.ENDERMAN));
    assertTrue(EliteMobFeature.isInfernoAvatar(EntityType.BLAZE));
    assertFalse(EliteMobFeature.isVoidSovereign(EntityType.BLAZE));
    assertFalse(EliteMobFeature.isInfernoAvatar(EntityType.ENDERMAN));
    assertFalse(EliteMobFeature.isVoidSovereign(null));
  }

  @Test
  void infernoAvatarFiresTenEmbers() {
    assertEquals(10, EliteMobFeature.blazeBurstShots());
    assertEquals(10, EliteMobFeature.BLAZE_BURST_SHOTS);
  }

  @Test
  void signatureAbilityCooldownsAreSane() {
    assertTrue(EliteMobFeature.VOID_LIFT_COOLDOWN_MS >= 5_000L);
    assertTrue(EliteMobFeature.VOID_TNT_COOLDOWN_MS >= 5_000L);
    assertTrue(EliteMobFeature.INFERNO_VOLLEY_COOLDOWN_MS >= 5_000L);
    assertTrue(EliteMobFeature.INFERNO_BLAST_COOLDOWN_MS >= 5_000L);
    assertTrue(EliteMobFeature.INFERNO_GROUND_COOLDOWN_MS >= 5_000L);
    assertEquals(3L, EliteMobFeature.BLAZE_BURST_INTERVAL_TICKS);
    assertEquals(40L, EliteMobFeature.UNDEAD_REVIVAL_DELAY_TICKS);
  }
}
