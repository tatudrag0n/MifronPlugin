package org.server.mifron;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class InventoryGroupTransitionTest {
   private ServerMock server;
   private JavaPlugin plugin;
   private InventoryGroupFeature feature;
   private PlayerMock player;
   private World normal;
   private World survival;

   @BeforeEach
   void setUp() {
      server = MockBukkit.mock();
      plugin = MockBukkit.createMockPlugin();
      normal = server.addSimpleWorld("hub");
      survival = server.addSimpleWorld("survival");
      player = server.addPlayer("InventoryBot");
      player.teleport(normal.getSpawnLocation());
      feature = new InventoryGroupFeature(plugin);
      server.getPluginManager().registerEvents(feature, plugin);
   }

   @AfterEach
   void tearDown() { MockBukkit.unmock(); }

   private void join() {
      feature.onJoin(new PlayerJoinEvent(player, Component.empty()));
   }

   private Path snapshot(String group) {
      return plugin.getDataFolder().toPath().resolve("inventory-groups")
         .resolve(player.getUniqueId() + "-" + group + ".dat");
   }

   private int count(Material material) {
      int result = 0;
      for (ItemStack stack : player.getInventory().getStorageContents()) {
         if (stack != null && stack.getType() == material) result += stack.getAmount();
      }
      return result;
   }

   @Test
   void worldChangeDuringJoinWaitIsNotRecoveryFailure() {
      player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
      join();
      player.teleport(survival.getSpawnLocation());
      server.getScheduler().performTicks(2);
      assertTrue(player.isOnline());
      assertEquals(0, count(Material.DIAMOND));
      player.teleport(normal.getSpawnLocation());
      assertEquals(3, count(Material.DIAMOND));
   }

   @Test
   void rapidRoundTripRestoresBothDomainsWithoutWaitingForTicks() {
      join();
      server.getScheduler().performOneTick();
      player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
      player.teleport(survival.getSpawnLocation());
      player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 5));
      player.teleport(normal.getSpawnLocation());
      assertEquals(3, count(Material.DIAMOND));
      assertEquals(0, count(Material.IRON_INGOT));
      player.teleport(survival.getSpawnLocation());
      assertEquals(0, count(Material.DIAMOND));
      assertEquals(5, count(Material.IRON_INGOT));
   }

   @Test
   void quitImmediatelyAfterTransitionSavesDestinationInventory() {
      join();
      server.getScheduler().performOneTick();
      player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
      player.teleport(survival.getSpawnLocation());
      player.getInventory().setItem(0, new ItemStack(Material.IRON_INGOT, 5));
      feature.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
      player.getInventory().clear();
      join();
      server.getScheduler().performOneTick();
      assertEquals(0, count(Material.DIAMOND));
      assertEquals(5, count(Material.IRON_INGOT));
   }

   @Test
   void corruptSnapshotIsNotOverwrittenOnQuit() throws Exception {
      Path file = snapshot("normal");
      Files.createDirectories(file.getParent());
      byte[] broken = {0, 0, 0, 99};
      Files.write(file, broken);
      join();
      server.getScheduler().performOneTick();
      feature.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
      assertArrayEquals(broken, Files.readAllBytes(file));
   }

   @Test
   void saveFailureDoesNotClearSourceInventory() throws Exception {
      join();
      server.getScheduler().performOneTick();
      player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
      Path temp = snapshot("normal").resolveSibling(snapshot("normal").getFileName() + ".tmp");
      Files.createDirectories(temp);
      Files.writeString(temp.resolve("blocker"), "force write failure");
      player.teleport(survival.getSpawnLocation());
      assertEquals(3, count(Material.DIAMOND));
      assertFalse(Files.exists(snapshot("survival")));
   }

   @Test
   void rejectsNonFiniteStateBeforeApplyingSnapshot() {
      assertTrue(InventoryGroupFeature.isSnapshotValuesValid(0, 1, 0.5F, 20, 5));
      assertFalse(InventoryGroupFeature.isSnapshotValuesValid(0, 1, Float.NaN, 20, 5));
      assertFalse(InventoryGroupFeature.isSnapshotValuesValid(0, 1, 0.5F, 20, Float.POSITIVE_INFINITY));
   }
}
