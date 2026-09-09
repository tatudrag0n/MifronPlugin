package org.server.mifron;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart4x1 extends MifronPart4 {
   protected void handleShopWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block block = event.getClickedBlock();
      ItemStack wand = event.getItem();
      long now = System.currentTimeMillis();
      if (this.shopWandActionUntil.getOrDefault(player.getUniqueId(), 0L) > now) { event.setCancelled(true); return; }
      this.shopWandActionUntil.put(player.getUniqueId(), now + SHOP_WAND_ACTION_COOLDOWN_MILLIS);
      ShopWandType type = this.shopWandType(wand);
      if (type == null) { player.sendMessage("\u00a7c\u3053\u306e\u30ef\u30f3\u30c9\u306e\u7a2e\u985e\u3092\u5224\u5225\u3067\u304d\u307e\u305b\u3093\u3002"); event.setCancelled(true); return; }
      if (type.isSlotWand()) { this.handleSlotWandClick(event, player, block, type); return; }
      if (type == ShopWandType.FRAME) { player.sendMessage("\u00a7c\u984d\u7e01\u30b7\u30e7\u30c3\u30d7\u306f\u672a\u5b9f\u88c5\u3067\u3059\u3002"); event.setCancelled(true); return; }
      if (block == null || !this.isValidShopWandTarget(block, type)) { player.sendMessage("\u00a7c" + this.shopWandTargetMessage(type)); event.setCancelled(true); return; }
      if (event.getAction().isLeftClick() && !this.canManageShop(player, block)) { player.sendMessage("\u00a7c\u4f5c\u6210\u8005\u307e\u305f\u306f\u7ba1\u7406\u8005\u306e\u307f\u89e3\u9664\u3067\u304d\u307e\u3059\u3002"); event.setCancelled(true); return; }
      if (block.getType() == Material.BARREL) { this.handleBarrelShopWandClick(player, block, event, type); return; }
      if (event.getAction().isRightClick()) {
         if (player.isSneaking()) {
            ItemStack specified = player.getInventory().getItemInOffHand();
            Material material = specified == null ? Material.AIR : specified.getType();
            if (material == Material.AIR || !this.isRandomShopItem(material) || this.utilityItemsFeature.getMifronItemId(specified) != null) {
               player.sendMessage("\u00a7e\u30aa\u30d5\u30cf\u30f3\u30c9\u306b\u8ca9\u58f2\u3057\u305f\u3044\u901a\u5e38\u30a2\u30a4\u30c6\u30e0\u3092\u6301\u3063\u3066Shift+\u53f3\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
               event.setCancelled(true); return;
            }
            int selectedSlot = this.selectedShelfSlot(player, block);
            this.assignCustomShelfShopSlot(block, selectedSlot, material);
            this.setShopOwner(block, player.getUniqueId());
            player.sendMessage("\u00a7a\u6307\u5b9a\u914d\u7f6e: \u67a0" + (selectedSlot + 1) + " \u2192 " + this.japaneseItemName(material));
         } else {
            this.configureSequentialShelfShop(block);
            this.setShopOwner(block, player.getUniqueId());
            player.sendMessage("\u00a7a\u9806\u756a\u914d\u7f6e\u306b\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002");
         }
         event.setCancelled(true);
         return;
      }
      if (event.getAction().isLeftClick()) {
         if (player.isSneaking() && "custom".equals(this.shelfShopMode(block))) {
            int selectedSlot = this.selectedShelfSlot(player, block);
            this.clearCustomShelfShopSlot(block, selectedSlot);
            player.sendMessage("\u00a7a\u67a0" + (selectedSlot + 1) + "\u3092\u7a7a\u6b04\u306b\u3057\u307e\u3057\u305f\u3002");
         } else if (this.setShelfShop(block, false)) player.sendMessage("\u00a7a\u68da\u306e\u30b7\u30e7\u30c3\u30d7\u5316\u3092\u89e3\u9664\u3057\u307e\u3057\u305f\u3002");
         else player.sendMessage("\u00a7e\u3053\u306e\u68da\u306f\u30b7\u30e7\u30c3\u30d7\u5316\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002");
         event.setCancelled(true);
      }
   }

   protected void handleSlotWandClick(PlayerInteractEvent event, Player player, Block block, ShopWandType type) {
      event.setCancelled(true);
      if (block == null || !this.isShelf(block.getType())) { player.sendMessage("\u00a7c\u68da\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
      if (!event.getAction().isRightClick()) return;
      SlotMachineManager.Difficulty difficulty = type.getSlotDifficulty();
      if (difficulty == null || this.slotMachineManager == null) { player.sendMessage("\u00a7c\u7121\u52b9\u306a\u30b9\u30ed\u30c3\u30c8\u30ef\u30f3\u30c9\u3067\u3059\u3002"); return; }
      this.setShelfShop(block, false);
      this.data.set(this.shelfShopPath(block), null);
      this.data.set(this.shelfShopOfferPath(block), null);
      this.clearShopOwner(block);
      this.queueDataSave();
      if (!this.slotMachineManager.registerMachine(block, difficulty)) { player.sendMessage("\u00a7c\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u306e\u521d\u671f\u5316\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002"); return; }
      player.sendMessage("\u00a7b\u68da\u3092\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u5316\u3057\u307e\u3057\u305f\uff01");
   }

   protected boolean isValidShopWandTarget(Block block, ShopWandType type) {
      if (type == ShopWandType.SHELF) return this.isShelf(block.getType());
      return type == ShopWandType.BARREL ? block.getType() == Material.BARREL : this.isShelf(block.getType()) || block.getType() == Material.BARREL;
   }

   protected String shopWandTargetMessage(ShopWandType type) {
      if (type == ShopWandType.SHELF) return "\u68da\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002";
      return type == ShopWandType.BARREL ? "\u6a3d\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002" : "\u68da\u307e\u305f\u306f\u6a3d\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002";
   }

   protected void handleBarrelShopWandClick(Player player, Block block, PlayerInteractEvent event, ShopWandType type) {
      if (event.getAction().isRightClick()) {
         if (!(block.getState() instanceof Barrel barrel)) { player.sendMessage("\u00a7c\u6a3d\u3092\u8aad\u307f\u8fbc\u3081\u307e\u305b\u3093\u3067\u3057\u305f\u3002"); event.setCancelled(true); return; }
         this.populateBarrelShop(barrel);
         if (type == ShopWandType.BARREL) this.setBarrelShopMeta(block); else this.clearBarrelShopMeta(block);
         this.setBarrelShop(block, true);
         this.setShopOwner(block, player.getUniqueId());
         player.sendMessage("\u00a7a\u6a3d\u3092\u30b7\u30e7\u30c3\u30d7\u5316\u3057\u3001\u5546\u54c1\u3092\u751f\u6210\u3057\u307e\u3057\u305f\u3002");
         event.setCancelled(true);
         return;
      }
      if (event.getAction().isLeftClick()) {
         if (this.setBarrelShop(block, false)) {
            if (block.getState() instanceof Barrel barrel) barrel.getInventory().clear();
            this.clearBarrelShopMeta(block);
            player.sendMessage("\u00a7a\u6a3d\u306e\u30b7\u30e7\u30c3\u30d7\u5316\u3092\u89e3\u9664\u3057\u307e\u3057\u305f\u3002");
         } else player.sendMessage("\u00a7e\u3053\u306e\u6a3d\u306f\u30b7\u30e7\u30c3\u30d7\u5316\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002");
         event.setCancelled(true);
      }
   }
}
