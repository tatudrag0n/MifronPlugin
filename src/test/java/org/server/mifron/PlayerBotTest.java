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
 * Covers tutorial stage progression end to end.
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
   void guideOpenAdvancesQuestStage() {
      World tutorial = server.getWorld("tutorial");
      assertNotNull(tutorial);
      UUID uuid = bot.getUniqueId();
      plugin.getPlayerSection(uuid).set("tutorial.stage", 5);
      plugin.getPlayerSection(uuid).set("tutorial.completed", false);
      bot.teleport(tutorial.getSpawnLocation());
      plugin.tutorialFeature.onGuideOpen(bot);
      assertEquals(6, plugin.getPlayerSection(uuid).getInt("tutorial.stage", 0));
      assertTrue(plugin.getEmeralds(uuid) > 0, "stage reward must be deposited");
   }

   @Test
   void relicUseAdvancesItemStage() {
      World tutorial = server.getWorld("tutorial");
      assertNotNull(tutorial);
      UUID uuid = bot.getUniqueId();
      plugin.getPlayerSection(uuid).set("tutorial.stage", 4);
      plugin.getPlayerSection(uuid).set("tutorial.completed", false);
      bot.teleport(tutorial.getSpawnLocation());
      plugin.tutorialFeature.onSpecialUse(bot);
      assertEquals(5, plugin.getPlayerSection(uuid).getInt("tutorial.stage", 0));
      assertTrue(plugin.getEmeralds(uuid) > 0, "stage reward must be deposited");
   }

   @Test
   void guideBookIsGivenOnlyOnce() {
      PlayerGuide.give(plugin, bot);
      long first = java.util.Arrays.stream(bot.getInventory().getContents())
         .filter(item -> item != null && item.getType() == Material.WRITTEN_BOOK)
         .count();
      assertEquals(1, first, "one guide book must be delivered");

      org.bukkit.inventory.ItemStack book = java.util.Arrays.stream(bot.getInventory().getContents())
         .filter(item -> item != null && item.getType() == Material.WRITTEN_BOOK)
         .findFirst().orElseThrow();
      org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
      assertEquals(PlayerGuide.TITLE, meta.getTitle());
      assertTrue(meta.getPageCount() >= 10, "the guide must cover the main systems");
      String pages = String.join("", meta.getPages());
      assertTrue(pages.contains("提案"), "the guide must explain proposals");
      assertTrue(pages.contains("ログボ"), "the guide must explain login/vote/referral");
      org.junit.jupiter.api.Assertions.assertFalse(pages.contains("Build"),
         "the guide must not advertise the disabled Build world");
      org.junit.jupiter.api.Assertions.assertFalse(pages.contains("structure submit"),
         "the guide must not advertise building submissions while Build is disabled");
      PlayerGuide.give(plugin, bot);
      long second = java.util.Arrays.stream(bot.getInventory().getContents())
         .filter(item -> item != null && item.getType() == Material.WRITTEN_BOOK)
         .count();
      assertEquals(1, second, "the guide must not be duplicated");
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
