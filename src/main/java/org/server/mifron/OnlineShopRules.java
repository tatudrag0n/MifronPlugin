package org.server.mifron;

import org.bukkit.Material;

final class OnlineShopRules {
   private OnlineShopRules() {}

   static int price(Mifron plugin, Material material) {
      int configured = plugin.shopSalePrices.getOrDefault(material.name(), 0);
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
