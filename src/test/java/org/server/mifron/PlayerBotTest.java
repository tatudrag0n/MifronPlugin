package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.ZombieMock;

/**
 * PlayerBot: simulated players exercising real plugin flows headlessly.
 * Covers tutorial stage progression (move + kill detection) end to end.
 */
class PlayerBotTest {
   private ServerMock server;
   private Mifron plugin;
   private PlayerMock bot;

   @BeforeEach
   void setUp() {
      server = MockBukkit.mock();
      plugin = MockBukkit.load(Mifron.class);
      bot = server.addPlayer("TestBot");
      assertNotNull(bot);
   }

   @AfterEach
   void tearDown() {
      MockBukkit.unmock();
   }

   @Test
   void pluginBootsAndTutorialWorldExists() {
      assertTrue(plugin.isEnabled());
      World tutorial = server.getWorld("tutorial");
      assertNotNull(tutorial, "ensureWorld must create the tutorial world on enable");
   }

   @Test
   void checkpointMoveAdvancesStage() {
      World tutorial = server.getWorld("tutorial");
      assertNotNull(tutorial);
      UUID uuid = bot.getUniqueId();
      plugin.getPlayerSection(uuid).set("tutorial.stage", 5);
      plugin.getPlayerSection(uuid).set("tutorial.completed", false);
      Location checkpoint = new Location(tutorial, 60.5, 65.0, 0.5);
      bot.teleport(new Location(tutorial, 50.0, 65.0, 0.5));
      bot.simulatePlayerMove(checkpoint);
      assertEquals(6, plugin.getPlayerSection(uuid).getInt("tutorial.stage", 0));
      assertTrue(plugin.getEmeralds(uuid) > 0, "stage reward must be deposited");
   }

   @Test
   void threeKillsAdvanceHuntStage() {
      World tutorial = server.getWorld("tutorial");
      assertNotNull(tutorial);
      UUID uuid = bot.getUniqueId();
      plugin.getPlayerSection(uuid).set("tutorial.stage", 2);
      plugin.getPlayerSection(uuid).set("tutorial.completed", false);
      plugin.getPlayerSection(uuid).set("tutorial.kills", 0);
      Location spot = new Location(tutorial, 0.0, 65.0, -30.0);
      bot.teleport(spot);
      for (int i = 0; i < 3; i++) {
         ZombieMock zombie = new ZombieMock(server, UUID.randomUUID());
         zombie.teleport(spot);
         zombie.setKiller((Player) bot);
         server.getPluginManager().callEvent(
            new EntityDeathEvent(zombie,
               org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).build(),
               List.of(new ItemStack(Material.ROTTEN_FLESH)), 5));
      }
      assertEquals(3, plugin.getPlayerSection(uuid).getInt("tutorial.stage", 0));
   }
}
