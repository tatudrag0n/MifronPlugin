package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;

abstract class MifronPart5 extends MifronPart4x2 {
   protected void assignCustomShelfShopSlot(Block block, int selectedSlot, Material material) {
      if (block == null || material == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) return;
      this.slotMachineManager.unregisterMachine(block);
      String path = this.mifron().shelfShopPath(block);
      if (!"custom".equals(this.mifron().shelfShopMode(block))) this.data.set(path + ".custom", null);
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "custom");
      this.data.set(path + ".order", null);
      this.data.set(path + ".custom." + selectedSlot, material.name());
      this.data.set(path + ".custom-prices." + selectedSlot, null);
      this.data.set(path + ".custom-modes." + selectedSlot, null);
      this.mifron().clearShelfShopRandomOffer(block);
      this.mifron().displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.queueDataSave();
   }
   protected void clearCustomShelfShopSlot(Block block, int selectedSlot) {
      if (block == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) return;
      this.data.set(this.mifron().shelfShopPath(block) + ".custom." + selectedSlot, null);
      this.data.set(this.mifron().shelfShopPath(block) + ".custom-prices." + selectedSlot, null);
      this.data.set(this.mifron().shelfShopPath(block) + ".custom-modes." + selectedSlot, null);
      this.mifron().displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.queueDataSave();
   }
   protected List<Material> shelfShopCatalogMaterials() {
      if (!this.shelfShopCatalogReady) this.rebuildShelfShopCatalog();
      return this.shelfShopCatalog;
   }
   protected void rebuildShelfShopCatalog() {
      List<Material> materials = new ArrayList<>();
      for (Material material : Material.values()) {
         if (this.mifron().isRandomShopItem(material)) materials.add(material);
      }
      materials.sort((a, b) -> {
         int category = Integer.compare(this.mifron().shelfShopCategoryRank(a), this.mifron().shelfShopCategoryRank(b));
         if (category != 0) return category;
         int family = this.mifron().shelfShopFamilyKey(a).compareTo(this.mifron().shelfShopFamilyKey(b));
         return family != 0 ? family : a.name().compareTo(b.name());
      });
      Map<Material, Integer> catalogNumbers = new HashMap<>();
      for (int index = 0; index < materials.size(); index++) catalogNumbers.put(materials.get(index), index + 1);
      this.shelfShopCatalog = List.copyOf(materials);
      this.shelfShopCatalogNumbers = Map.copyOf(catalogNumbers);
      this.shelfShopCatalogReady = true;
      this.getLogger().info("Cached " + materials.size() + " shelf shop catalog entries.");
   }
}
