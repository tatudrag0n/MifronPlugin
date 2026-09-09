package org.server.mifron;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

abstract class MifronPart8x1 extends MifronPart8 {
   protected MerchantOffer readMerchantOffer(Map<?, ?> raw) {
      Object materialValue = raw.get("material");
      if (materialValue == null) return null;
      Material material = Material.matchMaterial(materialValue.toString());
      if (material == null || !this.isMerchantPoolItem(material)) return null;
      int configuredPrice = raw.containsKey("price") ? this.parsePositiveInt(String.valueOf(raw.get("price")), -1) : -1;
      return new MerchantOffer(material, 1, this.merchantRarity(material), Math.max(this.materialPrice(material), configuredPrice));
   }

   protected boolean isMerchantPoolItem(Material material) {
      String name = material.name();
      return this.isPricedShopItem(material) && !name.startsWith("LEGACY_") && !name.startsWith("INFESTED_") && !name.endsWith("_COMMAND_BLOCK")
         && !Set.of("AIR", "BARRIER", "BEDROCK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "LIGHT", "DEBUG_STICK", "KNOWLEDGE_BOOK").contains(name);
   }

   protected boolean isMerchantWeightedPoolItem(Material material, Map<String, Integer> weights) {
      return this.isMerchantPoolItem(material) && weights.getOrDefault(material.name(), 0) > 0;
   }

   protected boolean isBarrelShopPoolItem(Material material) {
      String name = material.name();
      return this.isPricedShopItem(material) && this.barrelShopConfigs.containsKey(name) && !name.endsWith("_SPAWN_EGG") && !MERCHANT_EXCLUDED_ITEMS.contains(material);
   }

   protected boolean isPricedShopItem(Material material) {
      return material != null && material.isItem() && this.shopSalePrices.getOrDefault(material.name(), 0) > 0;
   }

   protected String merchantRarity(Material material) {
      int price = this.materialPrice(material);
      String name = material.name();
      if (price >= 1000 || name.contains("NETHERITE") || name.equals("ELYTRA") || name.equals("ENCHANTED_GOLDEN_APPLE") || name.endsWith("_TEMPLATE") || name.endsWith("_HEAD") || name.endsWith("_SKULL")) return "epic";
      if (price >= 100 || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("GOLDEN") || name.contains("TOTEM") || name.contains("HEART_OF_THE_SEA") || name.contains("TRIDENT") || name.endsWith("_SPEAR") || name.contains("SHULKER_BOX")) return "rare";
      if (price < 10 && !name.contains("IRON") && !name.contains("GOLD") && !name.contains("COPPER") && !name.contains("REDSTONE") && !name.contains("LAPIS") && !name.contains("QUARTZ") && !name.contains("AMETHYST") && !name.contains("ENDER") && !name.contains("BLAZE")) return "common";
      return "uncommon";
   }

   protected boolean isMerchantRarityAtLeast(String rarity, String minimum) {
      return this.merchantRarityRank(rarity) >= this.merchantRarityRank(minimum);
   }

   protected int merchantRarityRank(String rarity) {
      return switch ((rarity == null ? "" : rarity).toLowerCase(Locale.ROOT)) {
         case "epic" -> 3;
         case "rare" -> 2;
         case "uncommon" -> 1;
         default -> 0;
      };
   }

   protected int materialPrice(Material material) {
      String name = material.name();
      Integer configuredPrice = this.shopSalePrices.get(name);
      if (configuredPrice != null && configuredPrice > 0) return configuredPrice;
      Integer exact = this.exactMaterialPrice(material);
      if (exact != null) return exact;
      if (name.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) return 1000;
      if (name.endsWith("_SMITHING_TEMPLATE")) return 150;
      int baseFromStorage = this.storageMaterialPrice(name);
      if (baseFromStorage > 0) return baseFromStorage;
      int equipment = this.equipmentPrice(name);
      if (equipment > 0) return equipment;
      if (name.endsWith("_ORE")) return Math.max(8, this.priceByContainedResource(name));
      if (name.startsWith("RAW_") && !name.endsWith("_BLOCK")) return switch (name) { case "RAW_IRON" -> 8; case "RAW_GOLD" -> 20; case "RAW_COPPER" -> 3; default -> 4; };
      if (name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) return 2;
      if (name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING")) return 1;
      if (name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_FENCE") || name.endsWith("_WALL")) return 2;
      if (name.endsWith("_SHULKER_BOX")) return 150;
      if (name.contains("POTION") || name.contains("ENCHANTED_BOOK") || name.contains("MUSIC_DISC")) return 100;
      if (name.contains("SCULK") || name.contains("ECHO_SHARD")) return 50;
      if (name.contains("NETHER") || name.contains("END_")) return 8;
      if (name.contains("SUSPICIOUS") || name.contains("TRIAL") || name.contains("OMINOUS")) return 50;
      return material.isBlock() ? 1 : 3;
   }

   protected int materialBuyPrice(Material material) {
      int configuredBuyPrice = this.shopBuyPrices.getOrDefault(material.name(), 0);
      return configuredBuyPrice <= 0 ? 0 : Math.max(1, Math.min(2000000000, configuredBuyPrice));
   }
}
