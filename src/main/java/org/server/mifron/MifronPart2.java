package org.server.mifron;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart2 extends MifronPart1x3 {
   protected void loadShopPrices() {
      // Delegated to PricingService: parses shop-prices.yml into EnumMap caches.
      this.pricingService.loadShopPrices();
   }

   protected void applyEconomyPriceTable() {
      // Delegated to PricingService: economy-price-table.yml overrides shop prices.
      int applied = this.pricingService.applyEconomyPriceTable();
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
