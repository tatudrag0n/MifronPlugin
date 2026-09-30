package org.server.mifron;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
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
      if (type == null) { player.sendMessage("\u00a7c\u30ef\u30f3\u30c9\u7a2e\u985e\u3092\u5224\u5225\u3067\u304d\u307e\u305b\u3093\u3002"); event.setCancelled(true); return; }
      if (type.isSlotWand()) { this.handleSlotWandClick(event, player, block, type); return; }
      if (type == ShopWandType.FRAME) { player.sendMessage("\u00a7c\u984d\u7e01\u30b7\u30e7\u30c3\u30d7\u306f\u672a\u5b9f\u88c5\u3067\u3059\u3002"); event.setCancelled(true); return; }
      if (block == null || !this.isValidShopWandTarget(block, type)) { player.sendMessage("\u00a7c" + this.shopWandTargetMessage(type)); event.setCancelled(true); return; }
      if (event.getAction().isLeftClick() && !this.mifron().canManageShop(player, block)) { player.sendMessage("\u00a7c\u4f5c\u6210\u8005\u307e\u305f\u306f\u7ba1\u7406\u8005\u306e\u307f\u89e3\u9664\u3067\u304d\u307e\u3059\u3002"); event.setCancelled(true); return; }
      if (block.getType() == Material.BARREL) { this.handleBarrelShopWandClick(player, block, event, type); return; }
      if (event.getAction().isRightClick()) {
         if (player.isSneaking()) {
            ItemStack specified = player.getInventory().getItemInOffHand();
            Material material = specified == null ? Material.AIR : specified.getType();
            if (material == Material.AIR || !this.mifron().isRandomShopItem(material) || this.utilityItemsFeature.getMifronItemId(specified) != null) {
               player.sendMessage("\u00a7e\u30aa\u30d5\u30cf\u30f3\u30c9\u306b\u901a\u5e38\u30a2\u30a4\u30c6\u30e0\u3092\u6301\u3063\u3066Shift+\u53f3\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
               event.setCancelled(true); return;
            }
            int selectedSlot = this.mifron().selectedShelfSlot(player, block);
            this.mifron().assignCustomShelfShopSlot(block, selectedSlot, material);
            this.mifron().setShopOwner(block, player.getUniqueId());
            player.sendMessage("\u00a7a\u6307\u5b9a\u914d\u7f6e: \u67a0" + (selectedSlot + 1));
         } else {
            this.mifron().configureSequentialShelfShop(block);
            this.mifron().setShopOwner(block, player.getUniqueId());
            player.sendMessage("\u00a7a\u9806\u756a\u914d\u7f6e\u306b\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002");
         }
         event.setCancelled(true);
         return;
      }
      if (event.getAction().isLeftClick()) {
         if (player.isSneaking() && "custom".equals(this.mifron().shelfShopMode(block))) {
            int selectedSlot = this.mifron().selectedShelfSlot(player, block);
            this.mifron().clearCustomShelfShopSlot(block, selectedSlot);
            player.sendMessage("\u00a7a\u67a0" + (selectedSlot + 1) + "\u3092\u7a7a\u6b04\u306b\u3057\u307e\u3057\u305f\u3002");
         } else if (this.mifron().setShelfShop(block, false)) player.sendMessage("\u00a7a\u68da\u306e\u30b7\u30e7\u30c3\u30d7\u5316\u3092\u89e3\u9664\u3057\u307e\u3057\u305f\u3002");
         else player.sendMessage("\u00a7e\u3053\u306e\u68da\u306f\u30b7\u30e7\u30c3\u30d7\u5316\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002");
         event.setCancelled(true);
      }
   }

   protected void handleSlotWandClick(PlayerInteractEvent event, Player player, Block block, ShopWandType type) {
      event.setCancelled(true);
      if (block == null || !this.mifron().isShelf(block.getType())) { player.sendMessage("\u00a7c\u68da\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
      if (!event.getAction().isRightClick()) return;
      String difficulty = type.getSlotDifficultyName();
      if (difficulty == null) { player.sendMessage("\u00a7c\u7121\u52b9\u306a\u30b9\u30ed\u30c3\u30c8\u30ef\u30f3\u30c9\u3067\u3059\u3002"); return; }
      this.mifron().setShelfShop(block, false);
      this.data.set(this.mifron().shelfShopPath(block), null);
      this.data.set(this.mifron().shelfShopOfferPath(block), null);
      this.mifron().clearShopOwner(block);
      this.queueDataSave();
      if (!this.minigameBridge.registerMachineOrRecord(block, difficulty)) {
         player.sendMessage("\u00a7c\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u767b\u9332\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      player.sendMessage("\u00a7b\u68da\u3092\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u5316\u3057\u307e\u3057\u305f\uff01");
   }

   protected boolean handleSlotCreateCommand(CommandSender sender, String[] args) {
      if (args.length < 5) {
         sender.sendMessage("\u00a7e/mf slotcreate <easy|normal|hard|expert> <x> <y> <z> [world]");
         return true;
      }
      String difficulty = args[1].toUpperCase(java.util.Locale.ROOT);
      if (this.minigameBridge.describeSlotDifficulty(difficulty) == null) {
         sender.sendMessage("\u00a7c\u96e3\u6613\u5ea6\u306feasy/normal/hard/expert\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
         return true;
      }
      int x;
      int y;
      int z;
      try {
         x = Integer.parseInt(args[2]);
         y = Integer.parseInt(args[3]);
         z = Integer.parseInt(args[4]);
      } catch (NumberFormatException e) {
         sender.sendMessage("\u00a7c\u5ea7\u6a19\u306f\u6574\u6570\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
         return true;
      }
      World world;
      if (args.length >= 6) {
         world = Bukkit.getWorld(args[5]);
         if (world == null) {
            sender.sendMessage("\u00a7c\u30ef\u30fc\u30eb\u30c9\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + args[5]);
            return true;
         }
      } else if (sender instanceof Player player) {
         world = player.getWorld();
      } else {
         sender.sendMessage("\u00a7e/mf slotcreate <easy|normal|hard|expert> <x> <y> <z> [world]");
         return true;
      }
      if (y < world.getMinHeight() || y > world.getMaxHeight()) {
         sender.sendMessage("\u00a7cY\u5ea7\u6a19\u304c\u7bc4\u56f2\u5916\u3067\u3059\u3002");
         return true;
      }
      if (!world.getChunkAt(x >> 4, z >> 4).load(true)) {
         sender.sendMessage("\u00a7c\u30c1\u30e3\u30f3\u30af\u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002");
         return true;
      }
      Block block = world.getBlockAt(x, y, z);
      if (!this.mifron().isShelf(block.getType()) && block.getType() != Material.CHISELED_BOOKSHELF) {
         sender.sendMessage("\u00a7c\u6307\u5b9a\u4f4d\u7f6e\u306b\u68da\u30d6\u30ed\u30c3\u30af\u304c\u3042\u308a\u307e\u305b\u3093: " + block.getType().name());
         return true;
      }
      this.mifron().setShelfShop(block, false);
      this.data.set(this.mifron().shelfShopPath(block), null);
      this.data.set(this.mifron().shelfShopOfferPath(block), null);
      this.mifron().clearShopOwner(block);
      this.queueDataSave();
      if (!this.minigameBridge.registerMachine(block, difficulty)) {
         sender.sendMessage("\u00a7c\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u767b\u9332\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002");
         return true;
      }
      sender.sendMessage("\u00a7b\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u767b\u9332: " + world.getName() + " " + x + "," + y + "," + z + " (" + difficulty + ")");
      return true;
   }

   protected boolean handleSlotRemoveCommand(CommandSender sender, String[] args) {
      if (args.length < 4) {
         sender.sendMessage("\u00a7e/mf slotremove <x> <y> <z> [world]");
         return true;
      }
      int x;
      int y;
      int z;
      try {
         x = Integer.parseInt(args[1]);
         y = Integer.parseInt(args[2]);
         z = Integer.parseInt(args[3]);
      } catch (NumberFormatException e) {
         sender.sendMessage("\u00a7c\u5ea7\u6a19\u306f\u6574\u6570\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
         return true;
      }
      World world;
      if (args.length >= 5) {
         world = Bukkit.getWorld(args[4]);
         if (world == null) {
            sender.sendMessage("\u00a7c\u30ef\u30fc\u30eb\u30c9\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + args[4]);
            return true;
         }
      } else if (sender instanceof Player player) {
         world = player.getWorld();
      } else {
         sender.sendMessage("\u00a7e/mf slotremove <x> <y> <z> [world]");
         return true;
      }
      if (!world.getChunkAt(x >> 4, z >> 4).load(true)) {
         sender.sendMessage("\u00a7c\u30c1\u30e3\u30f3\u30af\u3092\u30ed\u30fc\u30c9\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002");
         return true;
      }
      Block block = world.getBlockAt(x, y, z);
      if (!this.minigameBridge.isSlotMachine(block)) {
         sender.sendMessage("\u00a7c\u6307\u5b9a\u4f4d\u7f6e\u306b\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return true;
      }
      this.minigameBridge.unregisterMachine(block);
      sender.sendMessage("\u00a7b\u30b9\u30ed\u30c3\u30c8\u30de\u30b7\u30f3\u767b\u9332\u3092\u89e3\u9664: " + world.getName() + " " + x + "," + y + "," + z);
      return true;
   }

   protected boolean isValidShopWandTarget(Block block, ShopWandType type) {
      if (type == ShopWandType.SHELF) return this.mifron().isShelf(block.getType());
      return type == ShopWandType.BARREL ? block.getType() == Material.BARREL : this.mifron().isShelf(block.getType()) || block.getType() == Material.BARREL;
   }

   protected String shopWandTargetMessage(ShopWandType type) {
      if (type == ShopWandType.SHELF) return "\u68da\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002";
      return type == ShopWandType.BARREL ? "\u6a3d\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002" : "\u68da\u307e\u305f\u306f\u6a3d\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002";
   }

   protected void handleBarrelShopWandClick(Player player, Block block, PlayerInteractEvent event, ShopWandType type) {
      if (event.getAction().isRightClick()) {
         if (!(block.getState() instanceof Barrel barrel)) { player.sendMessage("\u00a7c\u6a3d\u3092\u8aad\u307f\u8fbc\u3081\u307e\u305b\u3093\u3067\u3057\u305f\u3002"); event.setCancelled(true); return; }
         this.mifron().populateBarrelShop(barrel);
         if (type == ShopWandType.BARREL) this.mifron().setBarrelShopMeta(block); else this.mifron().clearBarrelShopMeta(block);
         this.mifron().setBarrelShop(block, true);
         this.mifron().setShopOwner(block, player.getUniqueId());
         player.sendMessage("\u00a7a\u6a3d\u3092\u30b7\u30e7\u30c3\u30d7\u5316\u3057\u307e\u3057\u305f\u3002");
         event.setCancelled(true);
         return;
      }
      if (event.getAction().isLeftClick()) {
         if (this.mifron().setBarrelShop(block, false)) {
            if (block.getState() instanceof Barrel barrel) barrel.getInventory().clear();
            this.mifron().clearBarrelShopMeta(block);
            player.sendMessage("\u00a7a\u6a3d\u306e\u30b7\u30e7\u30c3\u30d7\u5316\u3092\u89e3\u9664\u3057\u307e\u3057\u305f\u3002");
         } else player.sendMessage("\u00a7e\u3053\u306e\u6a3d\u306f\u30b7\u30e7\u30c3\u30d7\u5316\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002");
         event.setCancelled(true);
      }
   }
}
