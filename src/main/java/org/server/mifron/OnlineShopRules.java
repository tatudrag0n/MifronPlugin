package org.server.mifron;

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
      if (name.contains("MACE") || name.contains("HEAVY_CORE")) return 25000;
      if (name.contains("ELYTRA") || name.equals("NETHER_STAR") || name.contains("BEACON") || name.contains("DRAGON_EGG")) return 20000;
      if (name.contains("NETHERITE")) return 8000;
      if (name.contains("TOTEM") || name.contains("SHULKER") || name.contains("TRIDENT")) return 4000;
      if (name.contains("ENCHANTED_GOLDEN_APPLE")) return 3500;
      if (name.contains("DIAMOND") && (name.contains("SWORD") || name.contains("CHESTPLATE") || name.contains("PICKAXE") || name.contains("HELMET") || name.contains("LEGGINGS") || name.contains("BOOTS"))) return 800;
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
}
