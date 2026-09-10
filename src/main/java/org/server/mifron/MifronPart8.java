package org.server.mifron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

abstract class MifronPart8 extends MifronPart7x2 {
   protected boolean merchantTypeAllows(String merchantType, Material material, boolean selling) {
      if ("purple".equals(merchantType)) return true;
      String name = material.name();
      if ("red".equals(merchantType)) {
         return selling
            ? material.getMaxDurability() > 0 || name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.contains("BOW") || name.contains("ARROW") || name.equals("SHIELD") || name.equals("TRIDENT") || name.equals("MACE")
            : name.contains("STONE") || name.contains("ORE") || name.contains("INGOT") || name.contains("COAL") || name.contains("COPPER") || name.contains("IRON") || name.contains("GOLD") || name.contains("REDSTONE") || name.contains("LAPIS") || name.contains("QUARTZ");
      }
      if ("yellow".equals(merchantType)) return this.mifron().materialPrice(material) >= 100 && !name.endsWith("_SPAWN_EGG") && !name.equals("SPAWNER") && !name.equals("TRIAL_SPAWNER");
      return !name.endsWith("_SPAWN_EGG") && !name.equals("SPAWNER") && !name.equals("TRIAL_SPAWNER") && this.mifron().materialPrice(material) < 1000;
   }

   protected MerchantOffer randomWeightedMerchantOffer(Set<Material> used, List<MerchantOffer> sourcePool, Map<String, Integer> weights) {
      List<MerchantOffer> pool = sourcePool.stream().filter(o -> !used.contains(o.material())).toList();
      if (pool.isEmpty()) { used.clear(); pool = sourcePool; }
      int totalWeight = pool.stream().mapToInt(o -> Math.max(1, weights.getOrDefault(o.material().name(), 1))).sum();
      int selected = this.random.nextInt(Math.max(1, totalWeight));
      for (MerchantOffer offer : pool) {
         selected -= Math.max(1, weights.getOrDefault(offer.material().name(), 1));
         if (selected < 0) { used.add(offer.material()); return offer; }
      }
      MerchantOffer offer = pool.get(this.random.nextInt(pool.size()));
      used.add(offer.material());
      return offer;
   }

   protected List<MerchantOffer> merchantOffers(Map<String, Integer> weights, boolean selling) {
      List<MerchantOffer> offers = new ArrayList<>();
      for (Material material : Material.values()) {
         if (!this.mifron().isMerchantWeightedPoolItem(material, weights)) continue;
         int price = selling ? this.mifron().materialPrice(material) : this.mifron().materialBuyPrice(material);
         if (price > 0) offers.add(new MerchantOffer(material, 1, this.mifron().merchantRarity(material), price));
      }
      return offers;
   }

   protected int randomMerchantPrice(Material material, boolean selling) {
      int basePrice = selling ? this.mifron().materialPrice(material) : this.mifron().materialBuyPrice(material);
      return (int) Math.max(1L, Math.min(2000000000L, (long) Math.max(1, basePrice) * (95 + this.random.nextInt(11)) / 100L));
   }

   protected List<MerchantOffer> barrelShopOffers() {
      List<MerchantOffer> offers = new ArrayList<>();
      for (Material material : Material.values()) {
         BarrelShopConfig config = this.barrelShopConfigs.get(material.name());
         if (config != null && this.mifron().isBarrelShopPoolItem(material)) offers.add(new MerchantOffer(material, 1, config.tier(), this.mifron().materialPrice(material)));
      }
      return offers;
   }

   protected List<Material> randomShopMaterials(int count) {
      if (count <= 0) return List.of();
      List<Material> materials = new ArrayList<>();
      for (Material material : Material.values()) if (this.mifron().isRandomShopItem(material)) materials.add(material);
      if (materials.isEmpty()) return List.of();
      Collections.shuffle(materials, this.random);
      return materials.stream().limit(count).toList();
   }

   protected boolean isRandomShopItem(Material material) {
      return material != null && material.isItem() && this.randomShopPrice(material) > 0 && !MERCHANT_EXCLUDED_ITEMS.contains(material) && !material.name().startsWith("LEGACY_") && !material.name().endsWith("_SPAWN_EGG");
   }

   protected int randomShopPrice(Material material) {
      if (material == null) return 0;
      int configuredPrice = this.shopSalePrices.getOrDefault(material.name(), 0);
      if (configuredPrice > 0) return configuredPrice;
      int economyPrice = this.economyPriceTable.price(material);
      if (economyPrice > 0) return economyPrice;
      Integer exact = this.mifron().exactMaterialPrice(material);
      return exact == null ? 0 : Math.max(1, exact);
   }

   protected int barrelShopWeight(Material material) {
      BarrelShopConfig config = this.barrelShopConfigs.get(material.name());
      return config == null ? 0 : config.weight();
   }

   protected List<MerchantOffer> allMerchantOffers() {
      Map<Material, MerchantOffer> configured = new HashMap<>();
      this.getConfig().getMapList("merchant-items").stream().map(this.mifron()::readMerchantOffer).filter(Objects::nonNull).forEach(offer -> configured.put(offer.material(), offer));
      List<MerchantOffer> offers = new ArrayList<>();
      for (Material material : Material.values()) {
         if (!this.mifron().isMerchantPoolItem(material)) continue;
         MerchantOffer override = configured.get(material);
         offers.add(override != null ? override : new MerchantOffer(material, 1, this.mifron().merchantRarity(material), this.mifron().materialPrice(material)));
      }
      return offers;
   }

   protected void saveMerchantOffers(UUID merchantId, List<MerchantOffer> sellOffers, List<MerchantOffer> buyOffers) {
      this.data.set("merchants." + merchantId + ".sell", this.serializeMerchantOffers(sellOffers));
      this.data.set("merchants." + merchantId + ".buy", this.serializeMerchantOffers(buyOffers));
      this.queueDataSave();
   }

   protected List<String> serializeMerchantOffers(List<MerchantOffer> offers) {
      return offers.stream().map(o -> o.material().name() + ":" + o.rarity() + ":" + o.price()).toList();
   }

   protected List<MerchantOffer> readMerchantOffers(UUID merchantId, String key) {
      List<MerchantOffer> offers = new ArrayList<>();
      for (String raw : this.data.getStringList("merchants." + merchantId + "." + key)) {
         String[] parts = raw.split(":");
         if (parts.length < 3) continue;
         Material material = Material.matchMaterial(parts[0]);
         if (material == null || !this.mifron().isMerchantPoolItem(material)) continue;
         int normalizedPrice = this.mifron().parsePositiveInt(parts[2], "buy".equals(key) ? this.mifron().materialBuyPrice(material) : this.mifron().materialPrice(material));
         if (normalizedPrice > 0) offers.add(new MerchantOffer(material, 1, this.mifron().merchantRarity(material), normalizedPrice));
      }
      return offers;
   }
}
