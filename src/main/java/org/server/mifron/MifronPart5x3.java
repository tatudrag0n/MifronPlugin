package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart5x3 extends MifronPart5x2 {
   protected int clearAllShelfShopRegistrations() {
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) {
         this.data.set("shelf-shop-offers", null);
         this.saveData();
         return 0;
      }
      List<String> removablePaths = new ArrayList<>();
      for (String worldId : new ArrayList<>(shops.getKeys(false))) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) continue;
         World world = this.worldFromId(worldId);
         for (String coordinates : new ArrayList<>(worldShops.getKeys(false))) {
            Block block = world == null ? null : this.blockFromCoordinates(world, coordinates);
            boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
            if (!slotMachine && block != null && this.slotMachineManager != null) slotMachine = this.slotMachineManager.isMachine(block);
            if (slotMachine) continue;
            removablePaths.add(worldId + "." + coordinates);
            if (block != null) {
               try { this.clearShelfShopDisplay(block); }
               catch (Throwable e) { this.getLogger().warning("shelf display clear failed: " + worldId + " " + coordinates); }
            }
         }
      }
      for (String path : removablePaths) {
         this.data.set("shelf-shops." + path, null);
         this.data.set("shop-owners." + path, null);
         this.data.set("shelf-shop-offers." + path, null);
      }
      this.saveData();
      this.syncShelfShopDisplays();
      return removablePaths.size();
   }

   protected void handleShelfShopCommand(CommandSender sender, String[] args) {
      if (args.length >= 2 && ("configure".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
         if (sender instanceof Player player) this.configureShelfShop(player, args);
         else sender.sendMessage("\u00a7c\u30d7\u30ec\u30a4\u30e4\u30fc\u306e\u307f\u5b9f\u884c\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      if (!sender.hasPermission("mifron.shop.admin") && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (args.length >= 2 && List.of("clearall", "removeall", "disableall", "clear").contains(args[1].toLowerCase())) {
         int registered = this.clearAllShelfShopRegistrations();
         sender.sendMessage("\u00a7a\u68da\u30b7\u30e7\u30c3\u30d7\u3092\u5168\u89e3\u9664\u3057\u307e\u3057\u305f: " + registered + "\u4ef6");
         return;
      }
      if (args.length >= 2 && List.of("reorder", "renumber", "resetorder").contains(args[1].toLowerCase())) {
         this.renumberSequentialShelfShops();
         sender.sendMessage("\u00a7a\u9806\u756a\u914d\u7f6e\u306e\u68da\u756a\u53f7\u3092\u632f\u308a\u76f4\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      if (args.length >= 2 && ("reset".equalsIgnoreCase(args[1]) || "resetstock".equalsIgnoreCase(args[1]))) {
         this.data.set("shelf-shop-stock", null);
         this.data.set("shelf-shop-unlocked", null);
         this.saveData();
         this.syncShelfShopDisplays();
         sender.sendMessage("\u00a7a\u68da\u30b7\u30e7\u30c3\u30d7\u306e\u5171\u6709\u5728\u5eab\u30920\u306b\u30ea\u30bb\u30c3\u30c8\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      sender.sendMessage("\u00a7e/mf shelfshop clearall|reorder|resetstock");
   }

   protected Material materialForShelfSlot(List<Material> materials, int selectedSlot) {
      if (materials.isEmpty()) return null;
      return selectedSlot >= 0 && selectedSlot < materials.size() ? materials.get(selectedSlot) : materials.get(0);
   }

   protected void setShelfShopRandomOffers(Block block, List<Material> materials) {
      String path = this.shelfShopOfferPath(block);
      this.data.set(path + ".type", ShopWandType.SHELF.key());
      this.data.set(path + ".material", materials.get(0).name());
      this.data.set(path + ".materials", materials.stream().map(Enum::name).toList());
      this.data.set(path + ".created-at", System.currentTimeMillis());
      this.displayShelfShopOffers(block, materials);
      this.queueDataSave();
   }

   protected void clearShelfShopRandomOffer(Block block) {
      this.data.set(this.shelfShopOfferPath(block), null);
   }
}
