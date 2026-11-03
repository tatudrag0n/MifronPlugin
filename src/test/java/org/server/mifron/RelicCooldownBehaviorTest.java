package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Cooldown UI and the bunny-hop ground gate. */
class RelicCooldownBehaviorTest {
   private ServerMock server;
   private Mifron plugin;
   private PlayerMock player;

   @BeforeEach
   void setUp() {
      server = MockBukkit.mock();
      plugin = MockBukkit.load(Mifron.class);
      player = server.addPlayer("RelicBot");
   }

   @AfterEach
   void tearDown() {
      MockBukkit.unmock();
   }

   @Test
   void cooldownIsShownOnTheItemInsteadOfChat() {
      while (player.nextMessage() != null) { /* drain the join announcement */ }
      Map<UUID, Long> cooldowns = new HashMap<>();
      ItemStack relic = new ItemStack(Material.ECHO_SHARD);

      assertTrue(plugin.relicFeature.rollingCooldown(player, cooldowns, 8000L, relic));
      assertEquals(160, player.getCooldown(Material.ECHO_SHARD), "8s must become a 160-tick item sweep");
      assertNull(player.nextMessage(), "cooldown must not send a chat message");

      assertFalse(plugin.relicFeature.rollingCooldown(player, cooldowns, 8000L, relic));
      assertNull(player.nextMessage(), "blocked uses must stay silent");
   }

   @Test
   void bunnyHopStartsAnItemCooldownAndBlocksReuseInTheAir() {
      ItemStack dash = plugin.relicFeature.createRelicItem(RelicFeature.RelicType.DASHFOOT);

      plugin.relicFeature.tryDash(player, dash);
      assertEquals(RelicFeature.DASH_COOLDOWN_TICKS, player.getCooldown(Material.RABBIT_FOOT));

      plugin.relicFeature.tryDash(player, dash);
      assertEquals(RelicFeature.DASH_COOLDOWN_TICKS, player.getCooldown(Material.RABBIT_FOOT),
         "using the relic again before landing must be ignored");
   }
}
