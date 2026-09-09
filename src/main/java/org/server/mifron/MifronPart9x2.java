package org.server.mifron;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart9x2 extends MifronPart9x1 {
   protected ItemStack createMerchantOfferIcon(AbstractVillager villager, MerchantOffer offer, String action) {
      ItemStack item = new ItemStack(offer.material(), 1);
      ItemMeta meta = item.getItemMeta();
      boolean selling = "sell".equals(action);
      meta.displayName(Component.translatable(offer.material().translationKey()).color(this.rarityTextColor(offer.rarity())));
      meta.lore(List.of(
         Component.text("\u00a77\u30ec\u30a2\u5ea6: " + this.rarityLabel(offer.rarity())),
         Component.text("\u00a77" + (selling ? "\u4fa1\u683c: " : "\u8cb7\u53d6\u984d: ") + this.formatNumber(offer.price()) + "MP"),
         Component.text("\u00a77" + (selling ? "\u30af\u30ea\u30c3\u30af\u3067MP\u6b8b\u9ad8\u304b\u3089\u8cfc\u5165" : "\u30af\u30ea\u30c3\u30af\u30671\u500b\u58f2\u5374")),
         Component.text("\u00a77Shift+\u30af\u30ea\u30c3\u30af: \u307e\u3068\u3081\u3066\u53d6\u5f15")
      ));
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.merchantOfferKey, PersistentDataType.BOOLEAN, true);
      container.set(this.merchantOfferPriceKey, PersistentDataType.INTEGER, offer.price());
      container.set(this.merchantOfferMaterialKey, PersistentDataType.STRING, offer.material().name());
      container.set(this.merchantOfferAmountKey, PersistentDataType.INTEGER, 1);
      container.set(this.merchantOfferMerchantKey, PersistentDataType.STRING, villager.getUniqueId().toString());
      container.set(this.merchantOfferActionKey, PersistentDataType.STRING, action);
      container.set(this.merchantOfferRarityKey, PersistentDataType.STRING, offer.rarity());
      item.setItemMeta(meta);
      return item;
   }
   protected boolean isMerchantOffer(ItemStack item) {
      return item != null && item.hasItemMeta()
         && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(this.merchantOfferKey, PersistentDataType.BOOLEAN));
   }
   protected void buyMerchantOffer(Player player, ItemStack clicked, boolean bulk) {
      long now = System.currentTimeMillis();
      long last = this.lastMerchantTransaction.getOrDefault(player.getUniqueId(), 0L);
      if (now - last < 150L) return;
      if (!this.merchantTransactions.add(player.getUniqueId())) return;
      this.lastMerchantTransaction.put(player.getUniqueId(), now);
      try { this.buyMerchantOfferNow(player, clicked, bulk); }
      finally { Bukkit.getScheduler().runTaskLater(this, () -> this.merchantTransactions.remove(player.getUniqueId()), 3L); }
   }
}
