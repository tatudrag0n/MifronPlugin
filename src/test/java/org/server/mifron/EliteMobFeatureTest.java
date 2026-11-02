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
}
