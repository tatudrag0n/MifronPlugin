package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart4x2 extends MifronPart4x1 {
   protected void tickShelfShopActionBars() {
      long now = System.currentTimeMillis();
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.mifron().recordInventoryAcquisitions(player);
         String temporaryMessage = this.temporaryActionBarMessages.get(player.getUniqueId());
         long temporaryUntil = this.temporaryActionBarUntil.getOrDefault(player.getUniqueId(), 0L);
         if (temporaryMessage != null && temporaryUntil > now) {
            player.sendActionBar(Component.text(temporaryMessage, NamedTextColor.RED));
            continue;
         }
         this.temporaryActionBarMessages.remove(player.getUniqueId());
         this.temporaryActionBarUntil.remove(player.getUniqueId());
         ShelfShopOffer offer = this.mifron().readShelfShopOffer(player, player.getTargetBlockExact(5));
         if (offer == null) continue;
         int catalogNumber = this.mifron().shelfShopCatalogNumber(offer.material());
         int sellPrice = offer.price();
         int buyPrice = Math.max(0, Math.min(this.mifron().materialBuyPrice(offer.material()), sellPrice - 1));
         String modeLabel = "sell".equalsIgnoreCase(offer.mode()) ? "\u8ca9\u58f2" : "buy".equalsIgnoreCase(offer.mode()) ? "\u8cb7\u53d6" : "\u8ca9\u58f2/\u8cb7\u53d6";
         int stock = this.mifron().shelfShopStock(offer.material());
         Component prefix = Component.text(String.format("No.%03d ", Math.max(0, catalogNumber)), NamedTextColor.GRAY);
         player.sendActionBar(prefix.append(Component.translatable(offer.material().translationKey())
            .color(this.mifron().rarityTextColor(this.mifron().merchantRarity(offer.material())))
            .append(Component.text("  " + modeLabel + " / \u8ca9\u58f2:" + this.mifron().formatNumber(sellPrice) + "MP / \u8cb7\u53d6:" + this.mifron().formatNumber(buyPrice) + "MP / \u5728\u5eab:" + this.mifron().formatNumber(stock), stock > 0 ? NamedTextColor.GOLD : NamedTextColor.RED))));
      }
   }

   protected void showTemporaryActionBar(Player player, String message) {
      this.temporaryActionBarMessages.put(player.getUniqueId(), message);
      this.temporaryActionBarUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
      player.sendActionBar(Component.text(message, NamedTextColor.RED));
   }

   protected ShelfShopOffer readShelfShopOffer(Player player, Block block) {
      if (block == null || !this.mifron().isShelf(block.getType()) || !this.mifron().isShelfShop(block)) return null;
      List<Material> configuredMaterials = this.shelfShopRandomOffers(block);
      if (configuredMaterials.isEmpty()) return null;
      int selectedSlot = this.mifron().selectedShelfSlot(player, block);
      Material configuredMaterial = this.mifron().materialForShelfSlot(configuredMaterials, selectedSlot);
      if (configuredMaterial == null || configuredMaterial == Material.AIR || !this.mifron().isPricedShopItem(configuredMaterial)) return null;
      int price = this.data.getInt(this.mifron().shelfShopPath(block) + ".custom-prices." + selectedSlot, this.mifron().materialPrice(configuredMaterial));
      String mode = this.data.getString(this.mifron().shelfShopPath(block) + ".custom-modes." + selectedSlot, "both");
      if (!mode.equalsIgnoreCase("sell") && !mode.equalsIgnoreCase("buy") && !mode.equalsIgnoreCase("both")) mode = "both";
      return price <= 0 ? null : new ShelfShopOffer(configuredMaterial, 1, price, mode);
   }

   protected List<Material> shelfShopRandomOffers(Block block) {
      if (block == null || !this.mifron().isShelfShop(block)) return List.of();
      if ("custom".equals(this.mifron().shelfShopMode(block))) return this.customShelfShopMaterials(block);
      int order = this.data.getInt(this.mifron().shelfShopPath(block) + ".order", 0);
      if (order <= 0) return List.of();
      List<Material> catalog = this.mifron().shelfShopCatalogMaterials();
      int start = (order - 1) * SHELF_SHOP_OFFER_SLOTS;
      return start >= catalog.size() ? List.of() : catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS));
   }

   protected String shelfShopMode(Block block) {
      if (block == null) return "sequential";
      String mode = this.data.getString(this.mifron().shelfShopPath(block) + ".mode", "sequential");
      return "custom".equalsIgnoreCase(mode) ? "custom" : "sequential";
   }

   protected void configureSequentialShelfShop(Block block) {
      if (block == null) return;
      this.slotMachineManager.unregisterMachine(block);
      String path = this.mifron().shelfShopPath(block);
      int order = "sequential".equals(this.mifron().shelfShopMode(block)) ? this.data.getInt(path + ".order", 0) : 0;
      if (order <= 0) order = this.nextSequentialShelfOrder();
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "sequential");
      this.data.set(path + ".order", order);
      this.data.set(path + ".custom", null);
      this.mifron().clearShelfShopRandomOffer(block);
      this.mifron().initializeShopActivity(block);
      this.mifron().displayShelfShopOffers(block, this.shelfShopRandomOffers(block));
      this.queueDataSave();
   }

   protected int nextSequentialShelfOrder() {
      int nextOrder = 1;
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) return nextOrder;
      for (String worldId : shops.getKeys(false)) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) continue;
         for (String coordinates : worldShops.getKeys(false)) {
            String mode = worldShops.getString(coordinates + ".mode", "sequential");
            if (!"custom".equalsIgnoreCase(mode) && worldShops.getBoolean(coordinates + ".enabled", worldShops.getBoolean(coordinates, false))) {
               nextOrder = Math.max(nextOrder, worldShops.getInt(coordinates + ".order", 0) + 1);
            }
         }
      }
      return nextOrder;
   }

   protected List<Material> customShelfShopMaterials(Block block) {
      List<Material> materials = new ArrayList<>();
      String base = this.mifron().shelfShopPath(block) + ".custom";
      for (int slot = 0; slot < SHELF_SHOP_OFFER_SLOTS; slot++) {
         String raw = this.data.getString(base + "." + slot, "");
         Material material = raw.isBlank() ? Material.AIR : Material.matchMaterial(raw);
         materials.add(material == null ? Material.AIR : material);
      }
      return materials;
   }
}
