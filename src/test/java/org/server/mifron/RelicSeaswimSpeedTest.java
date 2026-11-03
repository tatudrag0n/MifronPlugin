package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Air-swimming must use roughly water sprint-swim speed, not air walking speed. */
class RelicSeaswimSpeedTest {
   private ServerMock server;
   private Mifron plugin;
   private PlayerMock player;

   @BeforeEach
   void setUp() {
      server = MockBukkit.mock();
      plugin = MockBukkit.load(Mifron.class);
      player = server.addPlayer("SwimBot");
   }

   @AfterEach
   void tearDown() {
      MockBukkit.unmock();
   }

   @Test
   void modifierScalesMovementToFortyPercent() {
      AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
      assertNotNull(speed);
      double base = speed.getValue();

      plugin.relicFeature.applySeaswimSpeed(player);
      assertEquals(base * RelicFeature.SEASWIM_SPEED_MULTIPLIER, speed.getValue(), 1.0e-9);

      long matching = speed.getModifiers().stream()
         .filter(modifier -> "seaswim_speed".equals(modifier.getKey().getKey()))
         .count();
      assertEquals(1, matching, "the modifier must not stack");
   }

   @Test
   void jetpackAndBunnyHopAreNotSlowedByTheAirSwimModifier() {
      // Regression: holding the Sea-Sky Orb while flying with the jetpack (or
      // during a bunny-hop arc) used to stack the x0.4 swim modifier on top of
      // the ability, making movement feel much slower than intended.
      assertTrue(RelicFeature.shouldAirSwim(true, false, false, false, false));
      assertFalse(RelicFeature.shouldAirSwim(true, false, false, true, false));
      assertFalse(RelicFeature.shouldAirSwim(true, false, false, false, true));
      assertFalse(RelicFeature.shouldAirSwim(true, true, false, false, false));
      assertFalse(RelicFeature.shouldAirSwim(true, false, true, false, false));
      assertFalse(RelicFeature.shouldAirSwim(false, false, false, false, false));
   }

   @Test
   void removingTheRelicRestoresNormalSpeed() {
      AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
      assertNotNull(speed);
      double base = speed.getValue();

      plugin.relicFeature.applySeaswimSpeed(player);
      plugin.relicFeature.removeSeaswimSpeed(player);
      assertEquals(base, speed.getValue(), 1.0e-9);

      for (AttributeModifier modifier : speed.getModifiers()) {
         assertTrue(!"seaswim_speed".equals(modifier.getKey().getKey()), "modifier must be removed");
      }
   }
}
