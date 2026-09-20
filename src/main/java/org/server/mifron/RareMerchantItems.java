package org.server.mifron;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Special high-value items sold exclusively through the dedicated rare
 * merchant. These materials are excluded from the normal OnlineShop catalog
 * so the two product tables never mix.
 */
final class RareMerchantItems {
   private RareMerchantItems() {}

   /** Materials that only the rare merchant may buy/sell. */
   static final Set<String> SPECIAL_NAMES = Set.of(
      "SPAWNER",
      "DRAGON_EGG",
      "HEART_OF_THE_SEA",
      "TRIAL_KEY",
      "OMINOUS_TRIAL_KEY",
      "MACE",
      "HEAVY_CORE",
      "ELYTRA",
      "ENCHANTED_GOLDEN_APPLE",
      "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE",
      "VEX_ARMOR_TRIM_SMITHING_TEMPLATE",
      "WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE",
      "SNIFFER_EGG",
      "TOTEM_OF_UNDYING",
      "MUSIC_DISC_PIGSTEP",
      "MUSIC_DISC_RELIC",
      "MUSIC_DISC_OTHERSIDE",
      "MUSIC_DISC_CREATOR",
      "MUSIC_DISC_CREATOR_MUSIC_BOX",
      "MUSIC_DISC_PRECIPICE"
   );

   /** Heads sold only by the rare merchant (player/mob/decorative). */
   static boolean isSpecialHead(Material material) {
      if (material == null) return false;
      String name = material.name();
      return name.endsWith("_HEAD") || name.endsWith("_SKULL")
         || material == Material.PLAYER_HEAD;
   }

   static boolean isSpecial(Material material) {
      if (material == null) return false;
      if (SPECIAL_NAMES.contains(material.name())) return true;
      if (isSpecialHead(material)) return true;
      // Spawn eggs are merchant-only (all current and future variants).
      return material.name().endsWith("_SPAWN_EGG");
   }

   /**
    * Very high fixed prices for the rare merchant (MP). Sell price is what the
    * player pays; buy price is what the merchant pays the player.
    */
   static final Map<String, int[]> PRICES = Map.ofEntries(
      Map.entry("SPAWNER", new int[] {500000, 100000}),
      Map.entry("DRAGON_EGG", new int[] {400000, 80000}),
      Map.entry("ELYTRA", new int[] {300000, 60000}),
      Map.entry("MACE", new int[] {250000, 50000}),
      Map.entry("HEAVY_CORE", new int[] {180000, 36000}),
      Map.entry("TOTEM_OF_UNDYING", new int[] {150000, 30000}),
      Map.entry("SNIFFER_EGG", new int[] {120000, 24000}),
      Map.entry("ENCHANTED_GOLDEN_APPLE", new int[] {100000, 20000}),
      Map.entry("HEART_OF_THE_SEA", new int[] {80000, 16000}),
      Map.entry("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", new int[] {80000, 16000}),
      Map.entry("VEX_ARMOR_TRIM_SMITHING_TEMPLATE", new int[] {60000, 12000}),
      Map.entry("WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE", new int[] {60000, 12000}),
      Map.entry("OMINOUS_TRIAL_KEY", new int[] {45000, 9000}),
      Map.entry("TRIAL_KEY", new int[] {15000, 3000}),
      Map.entry("MUSIC_DISC_PIGSTEP", new int[] {60000, 12000}),
      Map.entry("MUSIC_DISC_RELIC", new int[] {60000, 12000}),
      Map.entry("MUSIC_DISC_OTHERSIDE", new int[] {60000, 12000}),
      Map.entry("MUSIC_DISC_CREATOR", new int[] {60000, 12000}),
      Map.entry("MUSIC_DISC_CREATOR_MUSIC_BOX", new int[] {60000, 12000}),
      Map.entry("MUSIC_DISC_PRECIPICE", new int[] {60000, 12000})
   );

   /** Fallback price for special items without an explicit entry. */
   static int[] priceFor(Material material) {
      int[] fixed = PRICES.get(material.name());
      if (fixed != null) return fixed;
      if (isSpecialHead(material)) return new int[] {30000, 6000};
      if (material.name().endsWith("_SPAWN_EGG")) return new int[] {120000, 24000};
      return new int[] {50000, 10000};
   }
}
