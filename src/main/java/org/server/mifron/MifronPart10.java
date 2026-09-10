package org.server.mifron;

import java.util.ArrayList;
import java.util.Iterator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart10 extends MifronPart9x2 {
   protected void buyMerchantOfferNow(Player player, ItemStack clicked, boolean bulk) {
      ItemMeta meta = clicked.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      Integer price = container.get(this.merchantOfferPriceKey, PersistentDataType.INTEGER);
      Integer amount = container.get(this.merchantOfferAmountKey, PersistentDataType.INTEGER);
      String materialName = container.get(this.merchantOfferMaterialKey, PersistentDataType.STRING);
      String action = container.get(this.merchantOfferActionKey, PersistentDataType.STRING);
      if (price == null || amount == null || materialName == null || action == null) { player.sendMessage("\u00a7c\u5546\u4eba\u306e\u5546\u54c1\u30c7\u30fc\u30bf\u304c\u4e0d\u6b63\u3067\u3059\u3002"); return; }
      Material material = Material.matchMaterial(materialName);
      if (material == null || !material.isItem()) { player.sendMessage("\u00a7c\u5546\u4eba\u306e\u5546\u54c1\u304c\u4e0d\u6b63\u3067\u3059\u3002"); return; }
      price = Math.min(2000000000, Math.max(1, price));
      if ("buy".equals(action)) {
         int quantity = bulk ? this.mifron().maxMerchantSaleQuantity(player, material) : 1;
         if (bulk && quantity <= 0) { player.sendMessage("\u00a7c\u3053\u306e\u30a2\u30a4\u30c6\u30e0\u306f\u4e00\u62ec\u53d6\u5f15\u3067\u304d\u307e\u305b\u3093\u3002"); return; }
         MerchantSale sale = this.mifron().removeItemsForMerchantSale(player, material, Math.max(1, quantity), price);
         if (sale == null) { this.mifron().sendItemMessage(player, NamedTextColor.RED, "", material, "\u30921\u500b\u6301\u3063\u3066\u3044\u307e\u305b\u3093\u3002"); return; }
         this.mifron().depositEmeralds(player.getUniqueId(), sale.totalPrice());
         this.mifron().addPlayerStat(player.getUniqueId(), "total-trades", sale.quantity());
         this.mifron().recordFarmingSubmission(player, material);
         this.mifron().markMerchantTraded(container.get(this.merchantOfferMerchantKey, PersistentDataType.STRING));
         this.mifron().playPurchaseSound(player);
         this.mifron().sendItemMessage(player, NamedTextColor.GREEN, "\u58f2\u5374\u3057\u307e\u3057\u305f: ", material, " x" + sale.quantity() + " (+" + this.mifron().formatNumber(sale.totalPrice()) + "MP)");
         return;
      }
      int quantity = bulk ? this.mifron().maxMerchantPurchaseQuantity(player, material, price) : 1;
      if (bulk && material.getMaxStackSize() <= 1) { player.sendMessage("\u00a7c\u3053\u306e\u30a2\u30a4\u30c6\u30e0\u306f\u4e00\u62ec\u8cfc\u5165\u3067\u304d\u307e\u305b\u3093\u3002"); return; }
      if (quantity <= 0) { player.sendMessage(this.mifron().getEmeralds(player.getUniqueId()) < price ? "\u00a7cMP\u304c\u8db3\u308a\u307e\u305b\u3093\u3002" : "\u00a7c\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      int total = this.mifron().safeMultiply(price, quantity);
      if (!this.mifron().withdrawEmeralds(player.getUniqueId(), total)) { player.sendMessage("\u00a7cMP\u304c\u8db3\u308a\u307e\u305b\u3093\u3002"); return; }
      this.mifron().giveShopPurchasedItems(player, material, quantity);
      this.mifron().addPlayerStat(player.getUniqueId(), "total-trades", quantity);
      this.mifron().markMerchantTraded(container.get(this.merchantOfferMerchantKey, PersistentDataType.STRING));
      this.mifron().playPurchaseSound(player);
      this.mifron().sendItemMessage(player, NamedTextColor.GREEN, "\u8cfc\u5165\u3057\u307e\u3057\u305f: ", material, " x" + quantity + " (" + this.mifron().formatNumber(total) + "MP)");
   }

   protected void sendItemMessage(Player player, NamedTextColor color, String prefix, Material material, String suffix) {
      player.sendMessage(Component.text(prefix, color).append(Component.translatable(material.translationKey()).color(color)).append(Component.text(suffix, color)));
   }

   protected void tryReincarnate(Player player, ItemStack star) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("all-advancements-rewarded", false)) { player.sendMessage("\u00a7c\u5168\u9032\u6357\u9054\u6210\u5f8c\u306b\u8ee2\u751f\u3067\u304d\u307e\u3059\u3002"); return; }
      int next = this.mifron().safeAdd(section.getInt("reincarnations", 0), 1);
      int requiredEmeralds = this.mifron().safeMultiply(10000, next);
      int requiredLevel = Math.min(1000, this.mifron().safeAdd(20, this.mifron().safeMultiply(10, next)));
      int currentEmeralds = this.mifron().getEmeralds(player.getUniqueId());
      if (currentEmeralds < requiredEmeralds || player.getLevel() < requiredLevel) {
         player.sendMessage("\u00a7c\u8ee2\u751f\u6761\u4ef6\u3092\u6e80\u305f\u3057\u3066\u3044\u307e\u305b\u3093\u3002");
         return;
      }
      int bonus = Math.max(0, currentEmeralds / 1000 + player.getLevel());
      if (star != null) this.consumeOne(star);
      section.set("emeralds", 0);
      section.set("advancement-bonus-percent", 0);
      section.set("income-bonus-percent", null);
      section.set("reincarnations", next);
      section.set("reincarnation-bonus-percent", this.mifron().safeAdd(this.mifron().getReincarnationBonus(player.getUniqueId()), bonus));
      player.setLevel(0);
      player.setExp(0.0F);
      this.mifron().saveData();
      this.recordQuestProgress(player, "reincarnations", next);
      this.playReincarnationSound(player);
      player.sendMessage("\u00a7d\u8ee2\u751f\u3057\u307e\u3057\u305f: " + next + "\u56de\u76ee");
   }

   protected void resetAdvancements(Player player) {
      Iterator<Advancement> iterator = Bukkit.advancementIterator();
      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (!this.mifron().shouldTrackAdvancement(advancement)) continue;
         AdvancementProgress progress = player.getAdvancementProgress(advancement);
         for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) progress.revokeCriteria(criterion);
      }
   }

   protected void applyPendingAdvancementReset(Player player) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("pending-advancement-reset", false)) return;
      this.resetAdvancements(player);
      section.set("pending-advancement-reset", null);
      this.mifron().saveData();
   }

   protected void playPurchaseSound(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 1.1F); }
   protected void playUiClickSound(Player player) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.25F); }
   protected void playTeleportSound(Player player) { player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.0F); }
   protected void playReincarnationSound(Player player) {
      Location location = player.getLocation();
      player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
      player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.8F);
   }
}
