package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OnlineShopRulesTest {
   @Test
   void normalCooldownTimingIsUnchanged() {
      assertEquals(9000L, OnlineShopRules.cooldownDeadline(1000L, 8L));
      assertEquals(8L, OnlineShopRules.remainingSeconds(1000L, 9000L));
      assertEquals(1L, OnlineShopRules.remainingSeconds(8999L, 9000L));
      assertEquals(0L, OnlineShopRules.remainingSeconds(9000L, 9000L));
      assertEquals(160, OnlineShopRules.cooldownTicks(8L));
   }

   @Test
   void largeConfiguredDurationDoesNotExpireImmediately() {
      assertEquals(Long.MAX_VALUE, OnlineShopRules.cooldownDeadline(1000L, Long.MAX_VALUE));
      assertEquals(Long.MAX_VALUE, OnlineShopRules.cooldownDeadline(Long.MAX_VALUE - 500L, 1L));
   }

   @Test
   void distantDeadlineDoesNotOverflowRounding() {
      assertEquals(9223372036854776L, OnlineShopRules.remainingSeconds(0L, Long.MAX_VALUE));
      assertEquals(0L, OnlineShopRules.remainingSeconds(1000L, Long.MIN_VALUE));
   }

   @Test
   void overlayTicksSaturateWithoutOverflow() {
      assertEquals(Integer.MAX_VALUE, OnlineShopRules.cooldownTicks(Long.MAX_VALUE));
      assertEquals(0, OnlineShopRules.cooldownTicks(0L));
   }

   @Test
   void quickConsumablesCoverFoodArrowsAndThrowables() {
      // Edible flag covers bread/potions without name matching.
      assertEquals(true, OnlineShopRules.isQuickConsumable("BREAD", true));
      assertEquals(true, OnlineShopRules.isQuickConsumable("POTION", false));
      for (String name : new String[] {
         "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW",
         "SPLASH_POTION", "LINGERING_POTION", "MILK_BUCKET", "HONEY_BOTTLE",
         "ENDER_PEARL", "ENDER_EYE", "SNOWBALL", "EGG", "EXPERIENCE_BOTTLE",
         "FIREWORK_ROCKET", "WIND_CHARGE"}) {
         assertEquals(true, OnlineShopRules.isQuickConsumable(name, false), name);
      }
      assertEquals(false, OnlineShopRules.isQuickConsumable("DIAMOND_SWORD", false));
      assertEquals(false, OnlineShopRules.isQuickConsumable("IRON_DOOR", false));
      assertEquals(false, OnlineShopRules.isQuickConsumable(null, false));
   }

   @Test
   void consumableCapShortensLongCooldownsButKeepsZero() {
      assertEquals(10L, OnlineShopRules.applyConsumableCap(300L, 10L));
      assertEquals(10L, OnlineShopRules.applyConsumableCap(120L, 10L));
      assertEquals(8L, OnlineShopRules.applyConsumableCap(8L, 10L));
      assertEquals(0L, OnlineShopRules.applyConsumableCap(0L, 10L));
   }

   @Test
   void enchantedBookPriceDoublesPerLevel() {
      // Lv1:X -> Lv2:2X -> Lv3:4X with X=120; treasure costs double.
      assertEquals(120, OnlineShopRules.enchantedBookPrice(1, false));
      assertEquals(240, OnlineShopRules.enchantedBookPrice(2, false));
      assertEquals(480, OnlineShopRules.enchantedBookPrice(3, false));
      assertEquals(960, OnlineShopRules.enchantedBookPrice(4, false));
      assertEquals(1920, OnlineShopRules.enchantedBookPrice(5, false));
      assertEquals(240, OnlineShopRules.enchantedBookPrice(1, true));
      assertEquals(960, OnlineShopRules.enchantedBookPrice(3, true));
   }

   @Test
   void diamondGearCostsAtLeastItsDiamonds() {
      int diamond = 2500;
      assertTrue(OnlineShopRules.minimumPrice(org.bukkit.Material.DIAMOND_SWORD) >= 2 * diamond);
      assertTrue(OnlineShopRules.minimumPrice(org.bukkit.Material.DIAMOND_CHESTPLATE) >= 8 * diamond - 1000);
      assertTrue(OnlineShopRules.minimumPrice(org.bukkit.Material.DIAMOND_HELMET) >= 5 * diamond - 1000);
      assertTrue(OnlineShopRules.minimumPrice(org.bukkit.Material.TURTLE_HELMET) >= 4000);
      assertTrue(OnlineShopRules.minimumPrice(org.bukkit.Material.TOTEM_OF_UNDYING) >= 15000);
   }

   @Test
   void deepEmeraldOreStaysTop() {
      int top = OnlineShopRules.minimumPrice(org.bukkit.Material.DEEPSLATE_EMERALD_ORE);
      assertTrue(top >= 60000);
      assertTrue(top > OnlineShopRules.minimumPrice(org.bukkit.Material.NETHERITE_INGOT));
      assertTrue(top > OnlineShopRules.minimumPrice(org.bukkit.Material.DIAMOND_CHESTPLATE));
   }

   @Test
   void stockFactorRisesWhenLowFallsWhenHigh() {
      assertEquals(1.0, ShopStockService.factorFor(10, 10), 1e-9);
      assertTrue(ShopStockService.factorFor(0, 10) > 1.0);
      assertTrue(ShopStockService.factorFor(100, 10) < 1.0);
      assertTrue(ShopStockService.factorFor(0, 10) <= ShopStockService.MAX_FACTOR);
      assertTrue(ShopStockService.factorFor(9999, 10) >= ShopStockService.MIN_FACTOR);
   }

   @Test
   void revolverPerBulletIsOneSixth() {
      assertEquals(12L, FfaManager.revolverPerBulletTicks(75L));
      assertEquals(10L, FfaManager.revolverPerBulletTicks(65L));
      assertEquals(1L, FfaManager.revolverPerBulletTicks(1L));
   }

   @Test
   void mainWorldBansEntitySpawnItemsButAllowsArmorStand() {
      assertTrue(MainWorldFeature.isEntitySpawnItem(org.bukkit.Material.ZOMBIE_SPAWN_EGG));
      assertTrue(MainWorldFeature.isEntitySpawnItem(org.bukkit.Material.SNOWBALL));
      assertTrue(MainWorldFeature.isEntitySpawnItem(org.bukkit.Material.EGG));
      assertFalse(MainWorldFeature.isEntitySpawnItem(org.bukkit.Material.ARMOR_STAND));
      assertFalse(MainWorldFeature.isEntitySpawnItem(org.bukkit.Material.DIAMOND_SWORD));
   }

   @Test
   void shopHasFiveLeftColumnGenres() {
      assertEquals(5, OnlineShopFeature.Category.values().length);
      assertEquals(1, OnlineShopFeature.productSlot(0));
      assertEquals(5, OnlineShopFeature.productSlot(4));
      assertEquals(10, OnlineShopFeature.productSlot(5));
      assertEquals(41, OnlineShopFeature.productSlot(24));
   }

   @Test
   void specialItemsStayOutOfNormalShop() {
      assertTrue(RareMerchantItems.isSpecial(org.bukkit.Material.SPAWNER));
      assertTrue(RareMerchantItems.isSpecial(org.bukkit.Material.TOTEM_OF_UNDYING));
      assertTrue(RareMerchantItems.isSpecial(org.bukkit.Material.ELYTRA));
      assertTrue(RareMerchantItems.isSpecial(org.bukkit.Material.ZOMBIE_SPAWN_EGG));
      assertTrue(RareMerchantItems.isSpecial(org.bukkit.Material.PLAYER_HEAD));
      assertFalse(RareMerchantItems.isSpecial(org.bukkit.Material.DIAMOND_SWORD));
   }

   @Test
   void potionPriceVariesByContainerAndStrength() {
      assertEquals(60, OnlineShopRules.potionPrice("POTION", false));
      assertEquals(100, OnlineShopRules.potionPrice("POTION", true));
      assertEquals(80, OnlineShopRules.potionPrice("SPLASH_POTION", false));
      assertEquals(110, OnlineShopRules.potionPrice("LINGERING_POTION", false));
      assertEquals(40, OnlineShopRules.potionPrice("TIPPED_ARROW", false));
      assertEquals(80, OnlineShopRules.potionPrice("TIPPED_ARROW", true));
   }

   @Test
   void trialLootHasSaneMinimumPrices() {
      assertEquals(25000, OnlineShopRules.minimumPrice(org.bukkit.Material.MACE));
      assertEquals(25000, OnlineShopRules.minimumPrice(org.bukkit.Material.HEAVY_CORE));
      assertEquals(3000, OnlineShopRules.minimumPrice(org.bukkit.Material.OMINOUS_TRIAL_KEY));
      assertEquals(1000, OnlineShopRules.minimumPrice(org.bukkit.Material.TRIAL_KEY));
      assertEquals(800, OnlineShopRules.minimumPrice(org.bukkit.Material.OMINOUS_BOTTLE));
      assertEquals(400, OnlineShopRules.minimumPrice(org.bukkit.Material.BREEZE_ROD));
      assertEquals(2000, OnlineShopRules.minimumPrice(org.bukkit.Material.CREAKING_HEART));
      assertEquals(10000, OnlineShopRules.minimumPrice(org.bukkit.Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE));
   }

   @Test
   void variantNamesAreHumanReadable() {
      assertEquals("Sharpness", OnlineShopRules.prettyVariantName("sharpness"));
      assertEquals("Strong Healing", OnlineShopRules.prettyVariantName("STRONG_HEALING"));
      assertEquals("V", OnlineShopRules.romanLevel(5));
      assertEquals("I", OnlineShopRules.romanLevel(1));
      assertEquals("X", OnlineShopRules.romanLevel(30));
   }
}
