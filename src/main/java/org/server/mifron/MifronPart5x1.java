package org.server.mifron;

import org.bukkit.Material;

abstract class MifronPart5x1 extends MifronPart5 {
   protected int shelfShopCategoryRank(Material material) {
      String name = material.name();
      if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
         || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.equals("BOW") || name.equals("CROSSBOW")
         || name.equals("TRIDENT") || name.equals("MACE") || name.equals("SHIELD") || name.equals("FISHING_ROD")
         || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL") || name.equals("BRUSH")) return 8;
      if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
         || name.equals("ELYTRA") || name.contains("HORSE_ARMOR") || name.equals("TURTLE_HELMET")) return 9;
      if (name.contains("SMITHING_TEMPLATE") || name.contains("MUSIC_DISC") || name.contains("POTTERY_SHERD")
         || name.contains("HEAD") || name.contains("SKULL") || name.equals("TOTEM_OF_UNDYING") || name.equals("NETHER_STAR")
         || name.equals("HEART_OF_THE_SEA") || name.equals("CONDUIT") || name.equals("BEACON") || name.equals("DRAGON_EGG")
         || name.equals("ENCHANTED_GOLDEN_APPLE")) return 11;
      if (material.isEdible() || name.contains("SEEDS") || name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO")
         || name.equals("BEETROOT") || name.contains("MELON") || name.contains("PUMPKIN") || name.contains("COCOA")
         || name.contains("BERRIES") || name.contains("HONEY") || name.equals("SUGAR_CANE") || name.equals("EGG")) return 6;
      if (name.equals("ROTTEN_FLESH") || name.equals("BONE") || name.equals("STRING") || name.contains("SPIDER_EYE")
         || name.equals("GUNPOWDER") || name.contains("BLAZE") || name.contains("GHAST") || name.equals("ENDER_PEARL")
         || name.equals("MAGMA_CREAM") || name.equals("SLIME_BALL") || name.equals("PHANTOM_MEMBRANE")
         || name.equals("SHULKER_SHELL") || name.contains("POTION") || name.contains("FERMENTED") || name.equals("RABBIT_FOOT")
         || name.equals("DRAGON_BREATH") || name.equals("GLISTERING_MELON_SLICE") || name.equals("NETHER_WART")) return 7;
      if (name.contains("PISTON") || name.equals("OBSERVER") || name.equals("COMPARATOR") || name.equals("REPEATER")
         || name.contains("HOPPER") || name.contains("DISPENSER") || name.contains("DROPPER") || name.equals("CRAFTER")
         || name.contains("RAIL") || name.equals("LEVER") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")
         || name.contains("TRIPWIRE") || name.equals("DAYLIGHT_DETECTOR") || name.equals("TARGET") || name.equals("NOTE_BLOCK")
         || name.equals("REDSTONE_TORCH") || name.equals("REDSTONE_LAMP") || name.equals("TNT")) return 5;
      if (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT") || name.endsWith("_NUGGET")
         || name.equals("COAL") || name.equals("CHARCOAL") || name.equals("DIAMOND") || name.equals("EMERALD")
         || name.equals("REDSTONE") || name.equals("LAPIS_LAZULI") || name.equals("QUARTZ") || name.startsWith("AMETHYST_")
         || name.startsWith("NETHERITE_") || name.equals("ANCIENT_DEBRIS")) return 4;
      if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX") || name.contains("BUNDLE")
         || name.contains("MINECART") || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.equals("SADDLE")
         || name.equals("LEAD") || name.equals("COMPASS") || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK")
         || name.equals("NAME_TAG") || name.contains("LANTERN") || name.endsWith("TORCH") || name.equals("LADDER")
         || name.equals("SCAFFOLDING") || name.equals("CRAFTING_TABLE") || name.contains("FURNACE") || name.equals("ANVIL")) return 10;
      if (material.isBlock()) {
         if (this.isShelfShopWoodFamily(name)) return 1;
         if (name.contains("WOOL") || name.contains("CARPET") || name.contains("CONCRETE") || name.contains("TERRACOTTA")
            || name.contains("GLASS") || name.contains("BANNER") || name.contains("CANDLE") || name.contains("CORAL")
            || name.contains("FLOWER")) return 2;
         if (name.equals("STONE") || name.equals("COBBLESTONE") || name.equals("DEEPSLATE") || name.equals("DIRT")
            || name.equals("GRASS_BLOCK") || name.equals("SAND") || name.equals("GRAVEL") || name.equals("NETHERRACK")
            || name.equals("END_STONE") || name.equals("BASALT") || name.equals("BLACKSTONE")) return 0;
         return 3;
      }
      return 12;
   }
}
