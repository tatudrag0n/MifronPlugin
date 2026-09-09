package org.server.mifron;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

abstract class MifronPart6x2 extends MifronPart6x1 {
   boolean setBarrelShop(Block block, boolean enabled) {
      String path = this.barrelShopPath(block);
      boolean existed = this.data.getBoolean(path, false);
      this.data.set(path, enabled ? true : null);
      if (enabled) this.initializeShopActivity(block);
      if (!enabled) {
         this.clearShopOwner(block);
         this.clearBarrelShopMeta(block);
         this.data.set(this.shopLastActivityPath(block), null);
      }
      this.queueDataSave();
      return existed;
   }

   protected String barrelShopPath(Block block) {
      return "barrel-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   protected void populateBarrelShop(Barrel barrel) {
      this.populateBarrelShop(barrel, this.barrelShopOffers());
   }

   protected void populateBarrelShop(Barrel barrel, List<MerchantOffer> pool) {
      Set<Material> used = new HashSet<>();
      Inventory inventory = barrel.getInventory();
      inventory.clear();
      if (pool.isEmpty()) {
         this.getLogger().warning("Barrel shop pool is empty. No offers were generated.");
         return;
      }
      int offerSlots = Math.min(inventory.getSize(), Math.max(1, this.getConfig().getInt("barrel-shop.offer-slots", 27)));
      int bargainSlots = Math.max(0, Math.min(offerSlots, this.getConfig().getInt("barrel-shop.bargain-slots", 3)));
      for (int slot = 0; slot < offerSlots; slot++) {
         MerchantOffer offer = this.randomBarrelOffer(pool, used, slot < bargainSlots);
         inventory.setItem(slot, this.createBarrelOfferItem(offer));
      }
   }

   protected void setBarrelShopMeta(Block block) {
      String path = this.barrelShopMetaPath(block);
      this.data.set(path + ".type", ShopWandType.BARREL.key());
      this.data.set(path + ".created-at", System.currentTimeMillis());
      this.queueDataSave();
   }

   protected void clearBarrelShopMeta(Block block) {
      this.data.set(this.barrelShopMetaPath(block), null);
   }

   protected String barrelShopMetaPath(Block block) {
      return "barrel-shop-meta." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }
}
