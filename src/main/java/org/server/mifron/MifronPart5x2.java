package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart5x2 extends MifronPart5x1 {
   protected String shelfShopFamilyKey(Material material) {
      String name = material.name();
      for (String family : List.of("WOOL", "CARPET", "CONCRETE_POWDER", "CONCRETE", "TERRACOTTA", "GLASS_PANE", "GLASS", "BANNER", "BED", "CANDLE")) {
         if (name.endsWith("_" + family) || name.equals(family)) return family + ":" + name;
      }
      List<String> woods = List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED");
      for (String wood : woods) {
         if (name.startsWith(wood + "_")) return "WOOD:" + String.format("%02d", woods.indexOf(wood)) + ":" + name;
      }
      List<String> tools = List.of("SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "SPEAR");
      for (int i = 0; i < tools.size(); i++) {
         if (name.endsWith("_" + tools.get(i))) return "TOOL:" + String.format("%02d:%02d", i, this.shelfShopEquipmentTier(name)) + ":" + name;
      }
      List<String> armor = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "HORSE_ARMOR");
      for (int i = 0; i < armor.size(); i++) {
         if (name.endsWith("_" + armor.get(i))) return "ARMOR:" + String.format("%02d:%02d", i, this.shelfShopEquipmentTier(name)) + ":" + name;
      }
      List<String> resources = List.of("COAL", "IRON", "COPPER", "GOLD", "REDSTONE", "LAPIS", "DIAMOND", "EMERALD", "QUARTZ", "AMETHYST", "NETHERITE");
      for (int i = 0; i < resources.size(); i++) {
         if (name.contains(resources.get(i))) return "RESOURCE:" + String.format("%02d", i) + ":" + name;
      }
      return name;
   }

   protected boolean isShelfShopWoodFamily(String name) {
      return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
         || name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING") || name.contains("BAMBOO")
         || name.startsWith("OAK_") || name.startsWith("SPRUCE_") || name.startsWith("BIRCH_") || name.startsWith("JUNGLE_")
         || name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_") || name.startsWith("MANGROVE_")
         || name.startsWith("CHERRY_") || name.startsWith("PALE_OAK_") || name.startsWith("CRIMSON_") || name.startsWith("WARPED_");
   }

   protected int shelfShopEquipmentTier(String name) {
      List<String> tiers = List.of("WOODEN_", "STONE_", "COPPER_", "IRON_", "GOLDEN_", "DIAMOND_", "NETHERITE_");
      for (int i = 0; i < tiers.size(); i++) {
         if (name.startsWith(tiers.get(i))) return i;
      }
      return tiers.size();
   }

   protected void renumberSequentialShelfShops() {
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) return;
      List<String> paths = new ArrayList<>();
      for (String worldId : shops.getKeys(false)) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) continue;
         for (String coordinates : worldShops.getKeys(false)) {
            boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
            String mode = worldShops.getString(coordinates + ".mode", "sequential");
            boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
            if (enabled && !"custom".equalsIgnoreCase(mode) && !slotMachine) paths.add(worldId + "." + coordinates);
         }
      }
      paths.sort((a, b) -> {
         int order = Integer.compare(this.data.getInt("shelf-shops." + a + ".order", Integer.MAX_VALUE), this.data.getInt("shelf-shops." + b + ".order", Integer.MAX_VALUE));
         return order != 0 ? order : a.compareTo(b);
      });
      for (int i = 0; i < paths.size(); i++) this.data.set("shelf-shops." + paths.get(i) + ".order", i + 1);
      this.queueDataSave();
      this.mifron().syncShelfShopDisplays();
   }

   protected int shelfShopCatalogNumber(Material material) {
      if (material == null) return 0;
      if (!this.shelfShopCatalogReady) this.rebuildShelfShopCatalog();
      return this.shelfShopCatalogNumbers.getOrDefault(material, 0);
   }

   protected String shelfShopStockPath(Material material) { return "shelf-shop-stock." + material.name(); }
   protected int shelfShopStock(Material material) { return material == null ? 0 : Math.max(0, this.data.getInt(this.shelfShopStockPath(material), 0)); }
   protected void changeShelfShopStock(Material material, int delta) {
      if (material == null || delta == 0) return;
      int next = (int) Math.max(0L, Math.min(2000000000L, (long) this.mifron().shelfShopStock(material) + delta));
      this.data.set(this.shelfShopStockPath(material), next);
   }
   protected void recordAcquiredItem(Player player, Material material) {}
   protected void recordInventoryAcquisitions(Player player) {}
   void recordShelfShopAcquisition(Player player, Material material) {}
}
