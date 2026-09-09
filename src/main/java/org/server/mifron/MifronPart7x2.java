package org.server.mifron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart7x2 extends MifronPart7x1 {
   protected boolean spawnMerchant(Location location) {
      if (location == null || location.getWorld() == null) return false;
      if (!"survival".equalsIgnoreCase(location.getWorld().getName()) || this.isCentralPlazaLocation(location)) return false;
      WanderingTrader trader = (WanderingTrader) location.getWorld().spawnEntity(location, EntityType.WANDERING_TRADER);
      String merchantType = this.randomMerchantType();
      trader.customName(Component.text(this.merchantTypeColor(merchantType) + this.merchantTypeName(merchantType) + "\u5546\u4eba"));
      trader.setCustomNameVisible(true);
      trader.setDespawnDelay(Integer.MAX_VALUE);
      trader.setCanDrinkMilk(false);
      trader.setCanDrinkPotion(false);
      trader.setAI(true);
      trader.setInvulnerable(false);
      PersistentDataContainer container = trader.getPersistentDataContainer();
      container.set(this.merchantKey, PersistentDataType.BOOLEAN, true);
      container.set(this.merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
      container.set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
      container.set(this.merchantTypeKey, PersistentDataType.STRING, merchantType);
      this.rerollMerchant(trader);
      return true;
   }

   protected void rerollMerchant(AbstractVillager villager) {
      String merchantType = villager.getPersistentDataContainer().get(this.merchantTypeKey, PersistentDataType.STRING);
      if (merchantType == null || merchantType.isBlank()) {
         merchantType = this.randomMerchantType();
         villager.getPersistentDataContainer().set(this.merchantTypeKey, PersistentDataType.STRING, merchantType);
      }
      villager.customName(Component.text(this.merchantTypeColor(merchantType) + this.merchantTypeName(merchantType) + "\u5546\u4eba"));
      villager.setRecipes(Collections.emptyList());
      List<MerchantOffer> sellOffers = this.randomMerchantOffers(8, this.merchantSellWeights, true, merchantType);
      List<MerchantOffer> buyOffers = this.randomMerchantOffers(18, this.merchantBuyWeights, false, merchantType);
      this.saveMerchantOffers(villager.getUniqueId(), sellOffers, buyOffers);
   }

   protected String randomMerchantType() {
      int roll = this.random.nextInt(100);
      if (roll < 33) return "red";
      if (roll < 83) return "blue";
      return roll < 98 ? "yellow" : "purple";
   }

   protected String merchantTypeName(String type) {
      return switch (type) {
         case "red" -> "\u8d64";
         case "yellow" -> "\u9ec4";
         case "purple" -> "\u7d2b";
         default -> "\u9752";
      };
   }

   protected String merchantTypeColor(String type) {
      return switch (type) {
         case "red" -> "\u00a7c";
         case "yellow" -> "\u00a7e";
         case "purple" -> "\u00a7d";
         default -> "\u00a7b";
      };
   }

   protected List<MerchantOffer> randomMerchantOffers(int count, Map<String, Integer> weights, boolean selling, String merchantType) {
      List<MerchantOffer> pool = this.merchantOffers(weights, selling).stream()
         .filter(offerx -> this.merchantTypeAllows(merchantType, offerx.material(), selling)).toList();
      if (pool.isEmpty()) {
         pool = (selling ? this.allMerchantOffers() : this.allMerchantOffers().stream().filter(o -> this.materialBuyPrice(o.material()) > 0).toList())
            .stream().filter(offerx -> this.merchantTypeAllows(merchantType, offerx.material(), selling)).toList();
      }
      if (pool.isEmpty()) pool = this.merchantOffers(weights, selling);
      List<MerchantOffer> offers = new ArrayList<>();
      Set<Material> used = new HashSet<>();
      if ("purple".equals(merchantType)) {
         List<MerchantOffer> epicPool = pool.stream().filter(o -> this.isMerchantRarityAtLeast(o.rarity(), "epic")).toList();
         if (!epicPool.isEmpty()) {
            MerchantOffer epic = this.randomWeightedMerchantOffer(used, epicPool, weights);
            offers.add(new MerchantOffer(epic.material(), epic.amount(), epic.rarity(), this.randomMerchantPrice(epic.material(), selling)));
         }
         pool = pool.stream().filter(o -> this.isMerchantRarityAtLeast(o.rarity(), "rare")).toList();
         if (pool.isEmpty()) {
            pool = this.merchantOffers(weights, selling).stream().filter(o -> this.isMerchantRarityAtLeast(o.rarity(), "rare")).toList();
         }
      }
      for (int i = 0; i < count; i++) {
         MerchantOffer offer = this.randomWeightedMerchantOffer(used, pool, weights);
         offers.add(new MerchantOffer(offer.material(), offer.amount(), offer.rarity(), this.randomMerchantPrice(offer.material(), selling)));
      }
      return offers.stream().limit(count).toList();
   }
}
