package org.server.mifron;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Single source of truth for item pricing, merchant weight pools and barrel
 * shop configuration. Loads shop-prices.yml into {@link EnumMap} caches so hot
 * paths (materialPrice / materialBuyPrice / merchant rolls) never touch YAML.
 *
 * <p>Owned by the main plugin class and injected into consumers through the
 * plugin getter. It depends only on the {@link Plugin} for resource/logger
 * access and on {@link EconomyPriceTable} for the economy overrides, so it does
 * not reach back into the MifronPart inheritance chain.
 */
final class PricingService {

   /** Barrel shop pool entry (replaces the former BarrelShopConfig on MifronBase). */
   record BarrelPoolEntry(String tier, int weight) {
      String tierKey() {
         String normalized = this.tier == null ? "" : this.tier.toLowerCase(Locale.ROOT);
         return normalized.contains("掘り出し") || normalized.contains("bargain") ? "bargain" : "junk";
      }
   }

   private static final Set<Material> EXCLUDED_ITEMS = Set.of(
      Material.AIR, Material.BARRIER, Material.BEDROCK, Material.COMMAND_BLOCK,
      Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
      Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.JIGSAW, Material.LIGHT,
      Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK, Material.SPAWNER, Material.DRAGON_EGG
   );

   private final Plugin plugin;
   private final EconomyPriceTable economyPriceTable;
   // EnumMap<Material, ...> gives O(1) lookups keyed by enum ordinal instead of string hashing.
   private final EnumMap<Material, Integer> salePrices = new EnumMap<>(Material.class);
   private final EnumMap<Material, Integer> buyPrices = new EnumMap<>(Material.class);
   private final EnumMap<Material, Integer> merchantBuyWeights = new EnumMap<>(Material.class);
   private final EnumMap<Material, Integer> merchantSellWeights = new EnumMap<>(Material.class);
   private final EnumMap<Material, BarrelPoolEntry> barrelPools = new EnumMap<>(Material.class);

   PricingService(Plugin plugin, EconomyPriceTable economyPriceTable) {
      this.plugin = plugin;
      this.economyPriceTable = economyPriceTable;
   }

   void loadShopPrices() {
      this.salePrices.clear();
      this.buyPrices.clear();
      this.merchantBuyWeights.clear();
      this.merchantSellWeights.clear();
      this.barrelPools.clear();
      try (InputStream input = this.plugin.getResource("shop-prices.yml")) {
         if (input == null) {
            this.plugin.getLogger().severe("Bundled shop-prices.yml is missing.");
            return;
         }
         YamlConfiguration prices = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
         ConfigurationSection section = prices.getConfigurationSection("prices");
         if (section == null) {
            this.plugin.getLogger().severe("shop-prices.yml has no prices section.");
            return;
         }
         for (String materialName : section.getKeys(false)) {
            List<Integer> values = section.getIntegerList(materialName);
            if (values.size() < 2) continue;
            Material material = Material.matchMaterial(materialName);
            if (material == null) continue;
            this.salePrices.put(material, Math.max(0, values.get(0)));
            this.buyPrices.put(material, Math.max(0, values.get(1)));
         }
         this.loadWeightedPool(prices.getConfigurationSection("merchant.buy"), this.merchantBuyWeights);
         this.loadWeightedPool(prices.getConfigurationSection("merchant.sell"), this.merchantSellWeights);
         this.loadBarrelPool(prices.getConfigurationSection("barrel"));
         this.applyAliases(prices.getConfigurationSection("aliases"));
         this.plugin.getLogger().info("Loaded " + this.salePrices.size() + " shop prices from shop-prices.yml.");
      } catch (Exception e) {
         this.plugin.getLogger().severe("Could not load shop-prices.yml: " + e.getMessage());
      }
   }

   private void loadWeightedPool(ConfigurationSection section, EnumMap<Material, Integer> target) {
      if (section == null) return;
      for (String materialName : section.getKeys(false)) {
         int weight = section.getInt(materialName + ".weight", 0);
         if (weight <= 0) continue;
         Material material = Material.matchMaterial(materialName);
         if (material != null) target.put(material, weight);
      }
   }

   private void loadBarrelPool(ConfigurationSection section) {
      if (section == null) return;
      for (String materialName : section.getKeys(false)) {
         int weight = section.getInt(materialName + ".weight", 0);
         if (weight <= 0) continue;
         String tier = section.getString(materialName + ".tier", "junk").toLowerCase(Locale.ROOT);
         Material material = Material.matchMaterial(materialName);
         if (material != null) this.barrelPools.put(material, new BarrelPoolEntry(tier, weight));
      }
   }

   private void applyAliases(ConfigurationSection aliases) {
      if (aliases == null) return;
      for (String targetName : aliases.getKeys(false)) {
         Material source = Material.matchMaterial(aliases.getString(targetName, ""));
         Material target = Material.matchMaterial(targetName);
         if (source == null || target == null) continue;
         Integer salePrice = this.salePrices.get(source);
         Integer buyPrice = this.buyPrices.get(source);
         if (salePrice == null || buyPrice == null) continue;
         this.salePrices.putIfAbsent(target, salePrice);
         this.buyPrices.putIfAbsent(target, buyPrice);
         this.copyWeightAlias(this.merchantBuyWeights, source, target);
         this.copyWeightAlias(this.merchantSellWeights, source, target);
         BarrelPoolEntry barrelConfig = this.barrelPools.get(source);
         if (barrelConfig != null) this.barrelPools.putIfAbsent(target, barrelConfig);
      }
   }

   private void copyWeightAlias(EnumMap<Material, Integer> weights, Material source, Material target) {
      Integer weight = weights.get(source);
      if (weight != null) weights.putIfAbsent(target, weight);
   }

   /**
    * Applies economy-price-table.yml over shop-prices.yml. The economy table
    * wins, matching the historical behaviour that also overrode merchant weight
    * pools and barrel shop configuration.
    */
   int applyEconomyPriceTable() {
      int applied = 0;
      for (EconomyPriceTable.Entry entry : this.economyPriceTable.entries()) {
         Material material = entry.material();
         if (entry.priceEm() > 0) this.salePrices.put(material, entry.priceEm());
         if (entry.sellEm() > 0) this.buyPrices.put(material, entry.sellEm());
         if (entry.merchantBuyPool() && entry.merchantBuyWeight() > 0) this.merchantBuyWeights.put(material, entry.merchantBuyWeight());
         if (entry.merchantSellPool() && entry.merchantSellWeight() > 0) this.merchantSellWeights.put(material, entry.merchantSellWeight());
         if (entry.barrelShopPool() && entry.barrelShopWeight() > 0) this.barrelPools.put(material, new BarrelPoolEntry(entry.barrelTierKey(), entry.barrelShopWeight()));
         applied++;
      }
      return applied;
   }

   // ---- Queries used by shop / merchant / barrel code paths ----

   int salePrice(Material material) {
      Integer price = material == null ? null : this.salePrices.get(material);
      return price == null ? 0 : price;
   }

   int buyPrice(Material material) {
      Integer price = material == null ? null : this.buyPrices.get(material);
      return price == null ? 0 : Math.min(2000000000, Math.max(1, price));
   }

   int merchantBuyWeight(Material material) {
      return this.merchantWeight(this.merchantBuyWeights, material);
   }

   int merchantSellWeight(Material material) {
      return this.merchantWeight(this.merchantSellWeights, material);
   }

   int merchantWeight(boolean selling, Material material) {
      return selling ? this.merchantSellWeight(material) : this.merchantBuyWeight(material);
   }

   private int merchantWeight(EnumMap<Material, Integer> weights, Material material) {
      Integer weight = material == null ? null : weights.get(material);
      return weight == null ? 0 : weight;
   }

   int barrelWeight(Material material) {
      BarrelPoolEntry entry = material == null ? null : this.barrelPools.get(material);
      return entry == null ? 0 : entry.weight();
   }

   String barrelTier(Material material) {
      BarrelPoolEntry entry = material == null ? null : this.barrelPools.get(material);
      return entry == null ? "junk" : entry.tier();
   }

   boolean isBarrelPoolItem(Material material) {
      return material != null && this.barrelPools.containsKey(material);
   }

   /** Materials that participate in the merchant roll pool for the given side. */
   List<Material> merchantWeightedMaterials(boolean selling) {
      EnumMap<Material, Integer> weights = selling ? this.merchantSellWeights : this.merchantBuyWeights;
      List<Material> result = new ArrayList<>();
      for (Map.Entry<Material, Integer> entry : weights.entrySet()) {
         Material material = entry.getKey();
         if (entry.getValue() > 0 && !EXCLUDED_ITEMS.contains(material) && material.isItem()) result.add(material);
      }
      return result;
   }

   boolean isExcluded(Material material) {
      return material == null || EXCLUDED_ITEMS.contains(material);
   }
}
