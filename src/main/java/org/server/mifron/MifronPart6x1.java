package org.server.mifron;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart6x1 extends MifronPart6 {
   protected String shopLastActivityPath(Block block) {
      return "shop-last-activity." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   protected void initializeShopActivity(Block block) {
      if (block != null && this.data.getLong(this.shopLastActivityPath(block), 0L) <= 0L) {
         this.data.set(this.shopLastActivityPath(block), System.currentTimeMillis());
      }
   }

   protected void configureShelfShop(Player player, String[] args) {
      if (args.length < 4) {
         player.sendMessage("\u00a7e/mf shelfshop configure <sell|buy|both> <price>");
         return;
      }
      String mode = args[2].toLowerCase(Locale.ROOT);
      if (!mode.equals("sell") && !mode.equals("buy") && !mode.equals("both")) {
         player.sendMessage("\u00a7c\u7a2e\u5225\u306f sell / buy / both \u306e\u3044\u305a\u308c\u304b\u3067\u3059\u3002");
         return;
      }
      int price;
      try { price = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) { price = 0; }
      if (price <= 0 || price > MAX_EMERALDS) { player.sendMessage("\u00a7c\u4fa1\u683c\u306f1\uff5e2000000000\u3067\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
      Block block = player.getTargetBlockExact(5);
      ItemStack held = player.getInventory().getItemInOffHand();
      Material material = held == null ? Material.AIR : held.getType();
      if (block == null || !this.isShelf(block.getType()) || material == Material.AIR || !this.isRandomShopItem(material) || this.utilityItemsFeature.getMifronItemId(held) != null) {
         player.sendMessage("\u00a7c\u68da\u3092\u898b\u306a\u304c\u3089\u3001\u30aa\u30d5\u30cf\u30f3\u30c9\u306b\u901a\u5e38\u30a2\u30a4\u30c6\u30e0\u3092\u6301\u3063\u3066\u5b9f\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
         return;
      }
      if (this.isShelfShop(block) && !this.canManageShop(player, block)) {
         player.sendMessage("\u00a7c\u4f5c\u6210\u8005\u307e\u305f\u306f\u7ba1\u7406\u8005\u306e\u307f\u8a2d\u5b9a\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      int selectedSlot = this.selectedShelfSlot(player, block);
      this.assignCustomShelfShopSlot(block, selectedSlot, material);
      String path = this.shelfShopPath(block);
      this.data.set(path + ".custom-prices." + selectedSlot, price);
      this.data.set(path + ".custom-modes." + selectedSlot, mode);
      this.setShopOwner(block, player.getUniqueId());
      this.markShopActivity(block);
      this.displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.queueDataSave();
      player.sendMessage("\u00a7a\u68da\u30b7\u30e7\u30c3\u30d7\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f: " + this.japaneseItemName(material) + " / " + mode + " / " + this.formatNumber(price) + "MP");
   }

   protected void markShopActivity(Block block) {
      if (block != null) this.data.set(this.shopLastActivityPath(block), System.currentTimeMillis());
   }

   protected Block shopBlock(String worldId, String coordinates) {
      try {
         World world = Bukkit.getWorld(UUID.fromString(worldId));
         String[] parts = coordinates.split("_", -1);
         if (world == null || parts.length != 3) return null;
         return world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (IllegalArgumentException ignored) { return null; }
   }

   protected void cleanupInactiveShops() {
      long cutoff = System.currentTimeMillis() - Math.max(1L, this.getConfig().getLong("shops.inactivity-days", 30L)) * 86400000L;
      int removed = 0;
      ConfigurationSection shelves = this.data.getConfigurationSection("shelf-shops");
      if (shelves != null) {
         for (String worldId : new ArrayList<>(shelves.getKeys(false))) {
            ConfigurationSection entries = shelves.getConfigurationSection(worldId);
            if (entries == null) continue;
            for (String coordinates : new ArrayList<>(entries.getKeys(false))) {
               Block block = this.shopBlock(worldId, coordinates);
               if (block == null) continue;
               String activityPath = this.shopLastActivityPath(block);
               long last = this.data.getLong(activityPath, 0L);
               if (last <= 0L) this.data.set(activityPath, System.currentTimeMillis());
               else if (last < cutoff) {
                  this.clearShelfShopDisplay(block);
                  this.data.set("shelf-shops." + worldId + "." + coordinates, null);
                  this.data.set("shop-owners." + worldId + "." + coordinates, null);
                  this.data.set("shelf-shop-offers." + worldId + "." + coordinates, null);
                  this.data.set(activityPath, null);
                  removed++;
               }
            }
         }
      }
      if (removed > 0) {
         this.renumberSequentialShelfShops();
         this.syncShelfShopDisplays();
         this.saveData();
         this.getLogger().info("Removed inactive shops: " + removed);
      } else this.queueDataSave();
   }

   boolean isBarrelShop(Block block) {
      return block != null && block.getType() == Material.BARREL && this.data.getBoolean(this.barrelShopPath(block), false);
   }
   boolean isShopBlock(Block block) { return this.isShelfShop(block) || this.isBarrelShop(block); }
   boolean isAuctionFrame(Entity entity) { return this.auctionFeature.isAuctionFrame(entity); }
   boolean isAuctionInteractionItem(ItemStack item) { return this.auctionFeature.isAuctionInteractionItem(item); }
   void recordQuestProgress(Player player, String progressKey, int amount) { this.questService.addProgress(player, progressKey, amount); }
   void recordSpecialQuestProgress(UUID uuid, String progressKey, int amount) { this.questService.addSpecialProgress(uuid, progressKey, amount); }
   void completeSpecialQuestProgress(UUID uuid, String progressKey) { this.questService.completeSpecialProgress(uuid, progressKey); }
   protected void recordFarmingSubmission(Player player, Material material) {
      if (material != null && Set.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.NETHER_WART, Material.MELON_SLICE, Material.PUMPKIN, Material.SUGAR_CANE, Material.BAMBOO, Material.CACTUS, Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.COCOA_BEANS).contains(material)) {
         this.recordQuestProgress(player, "farming_submission", 1);
      }
   }
}
