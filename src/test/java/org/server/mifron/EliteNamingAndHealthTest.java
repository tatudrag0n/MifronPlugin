package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * エリートの改名・体力強化を実体で検証する。
 *
 * <ul>
 *   <li>腐死王 / 砂漠の腐死王 / 海淵の腐死王 の固有名が付くこと</li>
 *   <li>異空の支配者 / 極炎の化身 に改名されること</li>
 *   <li>エリート化で体力が大きく増え、下限も保証されること</li>
 * </ul>
 */
class EliteNamingAndHealthTest {

   private ServerMock server;
   private Mifron plugin;
   private World world;

   @BeforeEach
   void setUp() {
      this.server = MockBukkit.mock();
      this.world = this.server.addSimpleWorld("world");
      this.plugin = MockBukkit.load(Mifron.class);
   }

   @AfterEach
   void tearDown() {
      MockBukkit.unmock();
   }

   private Mob spawn(EntityType type) {
      Location at = new Location(this.world, 8.5, 64.0, 8.5);
      return (Mob) this.world.spawnEntity(at, type);
   }

   @Test
   void zombieKingsGetTheirOwnNames() {
      assertEquals("腐死王", EliteMobFeature.eliteTitle(this.spawn(EntityType.ZOMBIE)));
      assertEquals("砂漠の腐死王", EliteMobFeature.eliteTitle(this.spawn(EntityType.HUSK)));
      assertEquals("海淵の腐死王", EliteMobFeature.eliteTitle(this.spawn(EntityType.DROWNED)));
   }

   @Test
   void voidRulerAndInfernoAvatarAreRenamed() {
      assertEquals("異空の支配者", EliteMobFeature.eliteTitle(this.spawn(EntityType.ENDERMAN)));
      assertEquals("極炎の化身", EliteMobFeature.eliteTitle(this.spawn(EntityType.BLAZE)));
   }

   @Test
   void eliteNamesAreDistinctWithinTheSet() {
      String zombie = EliteMobFeature.eliteTitle(this.spawn(EntityType.ZOMBIE));
      String husk = EliteMobFeature.eliteTitle(this.spawn(EntityType.HUSK));
      String drowned = EliteMobFeature.eliteTitle(this.spawn(EntityType.DROWNED));
      String skeleton = EliteMobFeature.eliteTitle(this.spawn(EntityType.SKELETON));
      assertTrue(!zombie.equals(husk) && !husk.equals(drowned) && !zombie.equals(drowned),
         "腐死王系3種の名前は互いに異なる");
      assertTrue(!zombie.equals(skeleton), "ゾンビ系とスケルトン系も別名");
   }

   @Test
   void eliteZombieGetsABoostedHealthPoolAndCustomName() {
      Zombie zombie = (Zombie) this.world.spawnEntity(
         new Location(this.world, 8.5, 64.0, 8.5), EntityType.ZOMBIE);
      double base = zombie.getMaxHealth();

      this.plugin.eliteMobFeature.makeElite(zombie);

      assertTrue(this.plugin.eliteMobFeature.isElite(zombie), "エリートとしてマークされる");
      assertTrue(zombie.getMaxHealth() >= base * 4.0,
         "体力が大きく増える (base=" + base + " -> " + zombie.getMaxHealth() + ")");
      assertTrue(zombie.getMaxHealth() >= 40.0, "弱いMOBでも下限40は保証される");
      assertNotNull(zombie.getCustomName());
      assertTrue(zombie.getCustomName().contains("腐死王"),
         "表示名に固有名が入る: " + zombie.getCustomName());
   }

   @Test
   void weakMobsGetTheHealthFloor() {
      Mob silverfish = this.spawn(EntityType.SILVERFISH);
      this.plugin.eliteMobFeature.makeElite(silverfish);
      assertTrue(silverfish.getMaxHealth() >= 40.0,
         "シルバーフィッシュでも下限40 (base was small)");
   }}

