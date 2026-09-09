package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart10x1 extends MifronPart10 {
   protected MerchantSale removeItemsForMerchantSale(Player player, Material material, int amount, int basePrice) {
      int available = this.countItemsByType(player, material);
      if (available < amount) return null;
      int remaining = amount;
      int totalPrice = 0;
      ItemStack[] contents = player.getInventory().getContents();
      for (int i = 0; i < contents.length && remaining > 0; i++) {
         ItemStack item = contents[i];
         if (!this.isMerchantSellableStack(item, material)) continue;
         int removed = Math.min(item.getAmount(), remaining);
         totalPrice = this.safeAdd(totalPrice, this.safeMultiply(this.merchantSaleUnitPrice(basePrice, item), removed));
         item.setAmount(item.getAmount() - removed);
         remaining -= removed;
         if (item.getAmount() <= 0) contents[i] = null;
      }
      player.getInventory().setContents(contents);
      return new MerchantSale(amount, totalPrice);
   }

   protected int countItemsByType(Player player, Material material) {
      int available = 0;
      for (ItemStack item : player.getInventory().getContents()) if (this.isMerchantSellableStack(item, material)) available += item.getAmount();
      return available;
   }

   protected boolean isMerchantSellableStack(ItemStack item, Material material) {
      return item != null && item.getType() == material && this.utilityItemsFeature.getMifronItemId(item) == null && !this.isMerchantOffer(item);
   }

   protected int merchantSaleUnitPrice(int basePrice, ItemStack item) {
      if (item.getItemMeta() instanceof Damageable damageable && item.getType().getMaxDurability() > 0) {
         int maxDurability = item.getType().getMaxDurability();
         return Math.max(1, (int) ((long) Math.max(1, basePrice) * Math.max(1, maxDurability - damageable.getDamage()) / maxDurability));
      }
      return Math.max(1, basePrice);
   }

   protected int maxMerchantPurchaseQuantity(Player player, Material material, int price) {
      if (material.getMaxStackSize() <= 1 || price <= 0) return 0;
      return Math.max(0, Math.min(Math.min(this.getEmeralds(player.getUniqueId()) / price, this.inventorySpaceFor(player, material)), material.getMaxStackSize()));
   }

   protected int maxMerchantSaleQuantity(Player player, Material material) {
      return material.getMaxStackSize() <= 1 ? 0 : Math.min(this.countItemsByType(player, material), material.getMaxStackSize());
   }

   protected int inventorySpaceFor(Player player, Material material) {
      int space = 0;
      int maxStack = material.getMaxStackSize();
      for (ItemStack item : player.getInventory().getStorageContents()) {
         if (item == null || item.getType() == Material.AIR) space += maxStack;
         else if (item.getType() == material && item.getAmount() < maxStack) space += maxStack - item.getAmount();
      }
      return space;
   }

   protected void giveShopPurchasedItems(Player player, Material material, int amount) {
      this.recordAcquiredItem(player, material);
      int remaining = amount;
      int maxStack = material.getMaxStackSize();
      while (remaining > 0) {
         int stackAmount = Math.min(maxStack, remaining);
         for (ItemStack leftover : player.getInventory().addItem(this.createShopPurchasedItem(material, stackAmount)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }
         remaining -= stackAmount;
      }
   }

   protected String rarityLabel(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) { case "epic" -> "\u00a7d\u30a8\u30d4\u30c3\u30af"; case "rare" -> "\u00a79\u30ec\u30a2"; case "uncommon" -> "\u00a7a\u30a2\u30f3\u30b3\u30e2\u30f3"; default -> "\u00a7f\u30b3\u30e2\u30f3"; };
   }
   protected String rarityName(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) { case "epic" -> "\u30a8\u30d4\u30c3\u30af"; case "rare" -> "\u30ec\u30a2"; case "uncommon" -> "\u30a2\u30f3\u30b3\u30e2\u30f3"; default -> "\u30b3\u30e2\u30f3"; };
   }
   protected NamedTextColor rarityTextColor(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) { case "epic" -> NamedTextColor.LIGHT_PURPLE; case "rare" -> NamedTextColor.BLUE; case "uncommon" -> NamedTextColor.GREEN; default -> NamedTextColor.WHITE; };
   }
   protected String japaneseItemName(Material material) {
      return switch (material) {
         case COBBLESTONE -> "\u4e38\u77f3"; case IRON_INGOT -> "\u9244\u30a4\u30f3\u30b4\u30c3\u30c8"; case GOLD_INGOT -> "\u91d1\u30a4\u30f3\u30b4\u30c3\u30c8";
         case DIAMOND -> "\u30c0\u30a4\u30e4\u30e2\u30f3\u30c9"; case EMERALD -> "\u30a8\u30e1\u30e9\u30eb\u30c9"; default -> this.toReadableMaterialName(material);
      };
   }
   protected String toReadableMaterialName(Material material) {
      List<String> words = new ArrayList<>();
      for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
         if (!part.isEmpty()) words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
      }
      return String.join(" ", words);
   }
   protected void markMerchantTraded(String rawUuid) {
      if (rawUuid == null) return;
      try {
         UUID merchantId = UUID.fromString(rawUuid);
         for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(merchantId);
            if (entity != null && this.isMifronMerchant(entity)) {
               entity.getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, true);
               return;
            }
         }
      } catch (IllegalArgumentException ignored) {}
   }
   protected int parsePositiveInt(String value, int fallback) {
      if (value == null) return fallback;
      String normalized = value.replaceAll("[^0-9]", "");
      if (normalized.isEmpty() || normalized.length() > 10) return fallback;
      try {
         long parsed = Long.parseLong(normalized);
         return parsed > Integer.MAX_VALUE ? fallback : (int) Math.max(0L, parsed);
      } catch (NumberFormatException e) { return fallback; }
   }
   protected String sanitizeTextInput(String value, int maxLength) {
      if (value == null) return "";
      String sanitized = value.replaceAll("\\p{Cntrl}", "").trim();
      return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
   }
}
