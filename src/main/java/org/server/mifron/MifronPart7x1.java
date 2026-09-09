package org.server.mifron;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart7x1 extends MifronPart7 {
   protected MerchantOffer randomWeightedBarrelOffer(List<MerchantOffer> candidates) {
      int totalWeight = candidates.stream().mapToInt(o -> Math.max(1, this.barrelShopWeight(o.material()))).sum();
      int selected = this.random.nextInt(Math.max(1, totalWeight));
      for (MerchantOffer offer : candidates) {
         selected -= Math.max(1, this.barrelShopWeight(offer.material()));
         if (selected < 0) return offer;
      }
      return candidates.get(this.random.nextInt(candidates.size()));
   }

   protected ItemStack createBarrelOfferItem(MerchantOffer offer) {
      ItemStack item = new ItemStack(offer.material());
      boolean equipment = item.getItemMeta() instanceof Damageable && offer.material().getMaxDurability() > 0;
      ItemMeta meta = item.getItemMeta();
      boolean damaged = equipment && this.random.nextInt(10) != 0;
      if (damaged && meta instanceof Damageable damageable) {
         int damagePercent = 20 + this.random.nextInt(61);
         damageable.setDamage(Math.min(offer.material().getMaxDurability() - 1, Math.max(1, offer.material().getMaxDurability() * damagePercent / 100)));
      }
      int price = this.barrelOfferPrice(offer);
      if (damaged) price = this.discountedPrice(price, 10 + this.random.nextInt(61));
      meta.lore(List.of(
         Component.text("\u67a0: ", NamedTextColor.GRAY).append(Component.text(this.barrelTierName(offer.rarity()), this.barrelTierColor(offer.rarity()))),
         Component.text("\u4fa1\u683c: " + this.formatNumber(price) + "MP", NamedTextColor.GOLD),
         Component.text("\u30af\u30ea\u30c3\u30af\u3067\u8cfc\u5165", NamedTextColor.GRAY)
      ));
      meta.getPersistentDataContainer().set(this.barrelOfferPriceKey, PersistentDataType.INTEGER, price);
      meta.getPersistentDataContainer().set(this.barrelOfferRarityKey, PersistentDataType.STRING, offer.rarity());
      item.setItemMeta(meta);
      return item;
   }

   protected int barrelOfferPrice(MerchantOffer offer) {
      return (int) Math.max(1L, Math.min(2000000000L, (long) Math.max(1, offer.price()) * (75 + this.random.nextInt(51)) / 100L));
   }
   protected String barrelTierName(String tier) { return "bargain".equals(tier) ? "\u6398\u308a\u51fa\u3057\u7269" : "\u30b8\u30e3\u30f3\u30af"; }
   protected NamedTextColor barrelTierColor(String tier) { return "bargain".equals(tier) ? NamedTextColor.AQUA : NamedTextColor.GRAY; }
   protected int discountedPrice(int price, int discountPercent) { return Math.max(1, (int) ((long) Math.max(1, price) * (100 - discountPercent) / 100L)); }

   protected void buyBarrelOffer(Player player, ItemStack displayed, int selectedSlot, Inventory inventory) {
      if (displayed == null || displayed.getType() == Material.AIR || !displayed.hasItemMeta()) return;
      Integer basePrice = displayed.getItemMeta().getPersistentDataContainer().get(this.barrelOfferPriceKey, PersistentDataType.INTEGER);
      if (basePrice == null || basePrice <= 0) return;
      int price = this.applyShopDiscount(player, basePrice);
      if (this.getEmeralds(player.getUniqueId()) < price) { this.showTemporaryActionBar(player, "MP\u304c\u4e0d\u8db3\u3057\u3066\u3044\u307e\u3059\uff1a" + this.formatNumber(price) + "MP"); return; }
      ItemStack purchased = displayed.clone();
      purchased.setAmount(1);
      ItemMeta meta = purchased.getItemMeta();
      meta.lore(null);
      meta.getPersistentDataContainer().remove(this.barrelOfferPriceKey);
      meta.getPersistentDataContainer().remove(this.barrelOfferRarityKey);
      purchased.setItemMeta(meta);
      if (this.inventorySpaceFor(player, purchased.getType()) < 1 || !this.withdrawEmeralds(player.getUniqueId(), price)) {
         this.showTemporaryActionBar(player, this.inventorySpaceFor(player, purchased.getType()) < 1 ? "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002" : "MP\u304c\u4e0d\u8db3\u3057\u3066\u3044\u307e\u3059\u3002");
         return;
      }
      if (!player.getInventory().addItem(purchased).isEmpty()) {
         this.depositEmeralds(player.getUniqueId(), price);
         this.showTemporaryActionBar(player, "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (inventory.getHolder() instanceof Barrel barrel) this.markShopActivity(barrel.getBlock());
      this.addPlayerStat(player.getUniqueId(), "total-trades", 1);
      this.playPurchaseSound(player);
      this.sendItemMessage(player, NamedTextColor.GREEN, "\u8cfc\u5165\u3057\u307e\u3057\u305f: ", purchased.getType(), " (" + this.formatNumber(price) + "MP)");
      List<MerchantOffer> pool = this.barrelShopOffers();
      if (!pool.isEmpty() && selectedSlot >= 0 && selectedSlot < inventory.getSize()) {
         Set<Material> used = new HashSet<>();
         for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == selectedSlot) continue;
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) used.add(item.getType());
         }
         int bargainSlots = Math.max(0, this.getConfig().getInt("barrel-shop.bargain-slots", 3));
         inventory.setItem(selectedSlot, this.createBarrelOfferItem(this.randomBarrelOffer(pool, used, selectedSlot < bargainSlots)));
      }
   }

   protected void normalizeMerchants() {
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
               if (!"survival".equalsIgnoreCase(world.getName())) entity.remove();
               else { villager.setAI(true); villager.setInvulnerable(false); }
            }
         }
      }
   }

   protected ItemStack createShopPurchasedItem(Material material, int amount) { return new ItemStack(material, amount); }
   protected int applyShopDiscount(Player player, int price) {
      int discount = Math.max(0, Math.min(95, this.getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0)));
      return (int) Math.max(1L, Math.min(2000000000L, Math.max(1, price) * (100L - discount) / 100L));
   }
}
