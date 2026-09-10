package org.server.mifron;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

abstract class MifronPart2 extends MifronPart1x3 {
   protected void loadShopPrices() {
      this.shopSalePrices.clear();
      this.shopBuyPrices.clear();
      this.merchantBuyWeights.clear();
      this.merchantSellWeights.clear();
      this.barrelShopConfigs.clear();
      try (InputStream input = this.getResource("shop-prices.yml")) {
         if (input == null) { this.getLogger().severe("Bundled shop-prices.yml is missing."); return; }
         YamlConfiguration prices = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
         ConfigurationSection section = prices.getConfigurationSection("prices");
         if (section == null) { this.getLogger().severe("shop-prices.yml has no prices section."); return; }
         for (String materialName : section.getKeys(false)) {
            List<Integer> values = section.getIntegerList(materialName);
            if (values.size() < 2) continue;
            String key = materialName.toUpperCase(Locale.ROOT);
            this.shopSalePrices.put(key, Math.max(0, values.get(0)));
            this.shopBuyPrices.put(key, Math.max(0, values.get(1)));
         }
         this.loadWeightedPool(prices.getConfigurationSection("merchant.buy"), this.merchantBuyWeights);
         this.loadWeightedPool(prices.getConfigurationSection("merchant.sell"), this.merchantSellWeights);
         this.loadBarrelShopPool(prices.getConfigurationSection("barrel"));
         ConfigurationSection aliases = prices.getConfigurationSection("aliases");
         if (aliases != null) {
            for (String targetName : aliases.getKeys(false)) {
               String sourceName = aliases.getString(targetName, "").toUpperCase(Locale.ROOT);
               Integer salePrice = this.shopSalePrices.get(sourceName);
               Integer buyPrice = this.shopBuyPrices.get(sourceName);
               if (salePrice == null || buyPrice == null) continue;
               String targetKey = targetName.toUpperCase(Locale.ROOT);
               this.shopSalePrices.putIfAbsent(targetKey, salePrice);
               this.shopBuyPrices.putIfAbsent(targetKey, buyPrice);
               this.copyWeightAlias(this.merchantBuyWeights, sourceName, targetKey);
               this.copyWeightAlias(this.merchantSellWeights, sourceName, targetKey);
               BarrelShopConfig barrelConfig = this.barrelShopConfigs.get(sourceName);
               if (barrelConfig != null) this.barrelShopConfigs.putIfAbsent(targetKey, barrelConfig);
            }
         }
         this.getLogger().info("Loaded " + this.shopSalePrices.size() + " shop prices from shop-prices.yml.");
      } catch (IOException e) {
         this.getLogger().severe("Could not load shop-prices.yml: " + e.getMessage());
      }
   }

   protected void loadWeightedPool(ConfigurationSection section, Map<String, Integer> target) {
      if (section == null) return;
      for (String materialName : section.getKeys(false)) {
         int weight = section.getInt(materialName + ".weight", 0);
         if (weight > 0) target.put(materialName.toUpperCase(Locale.ROOT), weight);
      }
   }

   protected void loadBarrelShopPool(ConfigurationSection section) {
      if (section == null) return;
      for (String materialName : section.getKeys(false)) {
         int weight = section.getInt(materialName + ".weight", 0);
         if (weight <= 0) continue;
         String tier = section.getString(materialName + ".tier", "junk").toLowerCase(Locale.ROOT);
         this.barrelShopConfigs.put(materialName.toUpperCase(Locale.ROOT), new BarrelShopConfig(tier, weight));
      }
   }

   protected void copyWeightAlias(Map<String, Integer> weights, String sourceName, String targetName) {
      Integer weight = weights.get(sourceName);
      if (weight != null) weights.putIfAbsent(targetName, weight);
   }

   protected void applyEconomyPriceTable() {
      int applied = 0;
      for (EconomyPriceTable.Entry entry : this.economyPriceTable.entries()) {
         String key = entry.material().name();
         if (entry.priceEm() > 0) this.shopSalePrices.put(key, entry.priceEm());
         if (entry.sellEm() > 0) this.shopBuyPrices.put(key, entry.sellEm());
         if (entry.merchantBuyPool() && entry.merchantBuyWeight() > 0) this.merchantBuyWeights.put(key, entry.merchantBuyWeight());
         if (entry.merchantSellPool() && entry.merchantSellWeight() > 0) this.merchantSellWeights.put(key, entry.merchantSellWeight());
         if (entry.barrelShopPool() && entry.barrelShopWeight() > 0) this.barrelShopConfigs.put(key, new BarrelShopConfig(entry.barrelTierKey(), entry.barrelShopWeight()));
         applied++;
      }
      if (applied > 0) this.getLogger().info("Applied " + applied + " economy price table entries.");
   }

   void queueDataSave() {
      if (this.pendingDataSaveTask != null && !this.pendingDataSaveTask.isCancelled()) return;
      this.pendingDataSaveTask = Bukkit.getScheduler().runTaskLater(this, () -> {
         this.pendingDataSaveTask = null;
         this.mifron().saveData();
      }, 20L);
   }

   void trackAnalytics(Player player, String eventName, String dedupeKey) {
      if (player != null) this.minoruBridgeFeature.sendAnalyticsEvent(player, eventName, player.getUniqueId() + ":active", dedupeKey);
   }

   void trackAnalyticsWithMetadata(Player player, String eventName, String dedupeKey, String metadataKey, String metadataValue) {
      if (player != null) this.minoruBridgeFeature.sendAnalyticsEvent(player, eventName, player.getUniqueId() + ":active", dedupeKey, metadataKey, metadataValue);
   }

   void trackFfaDamageAnalytics(Player player, String kit, double damage, String dedupeKey) {
      if (player != null) this.minoruBridgeFeature.sendFfaDamageAnalyticsEvent(player, player.getUniqueId() + ":active", dedupeKey, kit, damage);
   }

   protected void trackEconomyAnalytics(UUID uuid, String eventName, int amount, int balanceAfter, String reason) {
      if (uuid == null || amount <= 0) return;
      Player player = Bukkit.getPlayer(uuid);
      String sessionId = uuid + (player == null ? ":offline" : ":active");
      this.minoruBridgeFeature.sendEconomyAnalyticsEvent(uuid, eventName, sessionId, "economy:" + eventName + ":" + uuid + ":" + UUID.randomUUID(), amount, balanceAfter, reason);
   }

   protected void flushPendingFirstMpEvent(Player player) {
      if (player == null) return;
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("analytics.first-mp-earned-pending", false) || section.getBoolean("analytics.first-mp-earned-recorded", false)) return;
      section.set("analytics.first-mp-earned-pending", false);
      section.set("analytics.first-mp-earned-recorded", true);
      this.mifron().trackAnalytics(player, "first_mp_earned", "first-mp-earned:" + player.getUniqueId());
   }
}
