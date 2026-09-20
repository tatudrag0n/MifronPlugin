package org.server.mifron;

import java.util.Locale;
import org.bukkit.Material;

final class OnlineShopRules {
   private OnlineShopRules() {}

   static int price(Mifron plugin, Material material) {
      int configured = plugin.pricingService.salePrice(material);
      int raw = configured > 0 ? configured : Math.max(1, plugin.getConfig().getInt("online-shop.fallback-price", 10));
      return Math.max(raw, minimumPrice(material));
   }

   static int minimumPrice(Material material) {
      String name = material.name();
      // Deep slate emerald ore is the most valuable shop good by design.
      if (name.contains("DEEPSLATE_EMERALD_ORE")) return 60000;
      if (name.contains("MACE") || name.contains("HEAVY_CORE")) return 25000;
      if (name.contains("OMINOUS_TRIAL_KEY")) return 3000;
      if (name.contains("TRIAL_KEY")) return 1000;
      if (name.contains("OMINOUS_BOTTLE")) return 800;
      if (name.contains("BREEZE_ROD")) return 400;
      if (name.contains("CREAKING_HEART")) return 2000;
      if (name.contains("FLOW_ARMOR_TRIM") || name.contains("BOLT_ARMOR_TRIM")) return 10000;
      if (name.contains("ELYTRA") || name.equals("NETHER_STAR") || name.contains("BEACON") || name.contains("DRAGON_EGG")) return 20000;
      if (name.contains("TOTEM")) return 15000;
      if (name.contains("NETHERITE")) return 8000;
      if (name.contains("TURTLE_HELMET")) return 4000;
      if (name.contains("SHULKER") || name.contains("TRIDENT")) return 4000;
      if (name.contains("ENCHANTED_GOLDEN_APPLE")) return 3500;
      // Diamond gear is never cheaper than the diamonds it is made of
      // (diamond = 2500 MP): sword 2, pickaxe/axe 3, shovel/hoe 1,
      // helmet 5, chestplate 8, leggings 7, boots 4.
      if (name.contains("DIAMOND")) {
         if (name.contains("CHESTPLATE")) return 19000;
         if (name.contains("LEGGINGS")) return 17000;
         if (name.contains("HELMET")) return 12000;
         if (name.contains("BOOTS")) return 9500;
         if (name.contains("PICKAXE") || name.contains("AXE")) return 7000;
         if (name.contains("SWORD")) return 5000;
         if (name.contains("SHOVEL") || name.contains("HOE")) return 2500;
      }
      return 0;
   }

   static long cooldownDeadline(long now, long seconds) {
      if (seconds <= 0L) return now;
      try {
         return Math.addExact(now, Math.multiplyExact(seconds, 1000L));
      } catch (ArithmeticException e) {
         return Long.MAX_VALUE;
      }
   }

   static long remainingSeconds(long now, long until) {
      if (until <= now) return 0L;
      long remaining;
      try {
         remaining = Math.subtractExact(until, now);
      } catch (ArithmeticException e) {
         remaining = Long.MAX_VALUE;
      }
      return remaining / 1000L + (remaining % 1000L == 0L ? 0L : 1L);
   }

   static int cooldownTicks(long seconds) {
      if (seconds <= 0L) return 0;
      return seconds > Integer.MAX_VALUE / 20L ? Integer.MAX_VALUE : (int) (seconds * 20L);
   }

   static long cooldownSeconds(String rarity) {
      return switch (rarity == null ? "COMMON" : rarity.toUpperCase()) {
         case "LEGENDARY" -> 1800L;
         case "EPIC" -> 600L;
         case "RARE" -> 180L;
         case "UNCOMMON" -> 45L;
         default -> 8L;
      };
   }

   static final long QUICK_CONSUMABLE_CAP_DEFAULT_SECONDS = 10L;

   /**
    * Consumables (food, arrows, thrown projectiles, rockets, ...) must stay
    * usable right after purchase, so their shop cooldown is capped low. The
    * {@code edible} flag covers drinkables/eatables; the name list covers
    * non-edible throwables/shootables identified purely by name so this stays
    * unit-testable without the Bukkit API.
    */
   static boolean isQuickConsumable(String materialName, boolean edible) {
      if (edible) return true;
      if (materialName == null) return false;
      return switch (materialName) {
         case "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW",
            "POTION", "SPLASH_POTION", "LINGERING_POTION",
            "MILK_BUCKET", "HONEY_BOTTLE",
            "ENDER_PEARL", "ENDER_EYE",
            "SNOWBALL", "EGG", "EXPERIENCE_BOTTLE",
            "FIREWORK_ROCKET", "WIND_CHARGE" -> true;
         default -> false;
      };
   }

   static long applyConsumableCap(long seconds, long capSeconds) {
      long cap = Math.max(0L, capSeconds);
      if (seconds <= 0L) return seconds;
      return Math.min(seconds, cap);
   }

   /**
    * Enchanted-book price doubles per level (Lv1:X, Lv2:2X, Lv3:4X, ...);
    * treasure enchantments cost double. Pure helper for tests and catalog.
    */
   static int enchantedBookPrice(int level, boolean treasure) {
      int safeLevel = Math.max(1, Math.min(10, level));
      long price = 120L << (safeLevel - 1);
      if (treasure) price *= 2L;
      return (int) Math.max(1L, Math.min(2_000_000_000L, price));
   }

   /** Potion price by container, with a bonus for strong/extended effects. */
   static int potionPrice(String containerMaterial, boolean strongOrLong) {
      int base = switch (containerMaterial) {
         case "SPLASH_POTION" -> 80;
         case "LINGERING_POTION" -> 110;
         case "TIPPED_ARROW" -> 40;
         default -> 60;
      };
      return strongOrLong ? base + 40 : base;
   }

   /** SHARP -> Sharp, STRONG_HEALING -> Strong Healing. Pure string helper. */
   static String prettyVariantName(String raw) {
      if (raw == null || raw.isBlank()) return "";
      String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
      StringBuilder out = new StringBuilder();
      for (String part : parts) {
         if (part.isEmpty()) continue;
         if (out.length() > 0) out.append(' ');
         out.append(Character.toUpperCase(part.charAt(0)));
         if (part.length() > 1) out.append(part.substring(1));
      }
      return out.toString();
   }

   static String romanLevel(int level) {
      return switch (Math.max(1, Math.min(10, level))) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         case 5 -> "V";
         case 6 -> "VI";
         case 7 -> "VII";
         case 8 -> "VIII";
         case 9 -> "IX";
         default -> "X";
      };
   }
}
