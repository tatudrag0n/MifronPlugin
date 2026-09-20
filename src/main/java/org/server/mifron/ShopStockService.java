package org.server.mifron;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-product stock for the OnlineShop with supply/demand price fluctuation.
 *
 * <ul>
 *   <li>Purchase decreases stock, sale increases it.</li>
 *   <li>Low stock raises both sale and buy prices; high stock lowers them.</li>
 *   <li>On the first day of each month every product is restocked by +5
 *   (idempotent per calendar month).</li>
 *   <li>All state persists in data.yml so restarts keep stock and prices.</li>
 * </ul>
 */
final class ShopStockService {
   static final int DEFAULT_STOCK = 10;
   static final int MONTHLY_RESTOCK = 5;
   static final int MIN_STOCK = 0;
   static final int MAX_STOCK = 9999;
   /** Price factor bounds so fluctuation can never produce absurd prices. */
   static final double MIN_FACTOR = 0.25;
   static final double MAX_FACTOR = 4.0;

   private final Mifron plugin;
   private final Map<String, Integer> stocks = new HashMap<>();

   ShopStockService(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.plugin.getConfig().addDefault("shop-stock.enabled", true);
      this.plugin.getConfig().addDefault("shop-stock.default-stock", DEFAULT_STOCK);
      this.plugin.getConfig().addDefault("shop-stock.monthly-restock", MONTHLY_RESTOCK);
      this.plugin.getConfig().options().copyDefaults(true);
      this.stocks.clear();
      var section = this.plugin.data().getConfigurationSection("shop-stock.stocks");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            this.stocks.put(key, Math.max(MIN_STOCK, Math.min(MAX_STOCK, section.getInt(key, DEFAULT_STOCK))));
         }
      }
      this.ensureMonthlyRestock();
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("shop-stock.enabled", true);
   }

   int defaultStock() {
      return Math.max(1, this.plugin.getConfig().getInt("shop-stock.default-stock", DEFAULT_STOCK));
   }

   /** Current stock for a product id, creating the entry at default if new. */
   synchronized int stockOf(String productId) {
      return this.stocks.computeIfAbsent(productId, ignored -> this.defaultStock());
   }

   /**
    * Supply/demand factor: {@code (base + 5) / (stock + 5)} clamped to
    * [MIN_FACTOR, MAX_FACTOR]. Empty shelves cost up to 4x, full shelves
    * discount down to 0.25x.
    */
   static double factorFor(int stock, int base) {
      int safeBase = Math.max(1, base);
      int safeStock = Math.max(MIN_STOCK, Math.min(MAX_STOCK, stock));
      double factor = (double) (safeBase + 5) / (double) (safeStock + 5);
      return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
   }

   /** Adjusted sale price for a base price and product id. Never below 1. */
   synchronized int salePriceNow(String productId, int basePrice) {
      if (!this.enabled() || basePrice <= 0) return Math.max(0, basePrice);
      double factor = factorFor(this.stockOf(productId), this.defaultStock());
      long adjusted = Math.round(basePrice * factor);
      return (int) Math.max(1L, Math.min(2_000_000_000L, adjusted));
   }

   /** Adjusted buy (sell-to-shop) price for a base buy price. Never below 0. */
   synchronized int buyPriceNow(String productId, int baseBuyPrice) {
      if (!this.enabled() || baseBuyPrice <= 0) return Math.max(0, baseBuyPrice);
      double factor = factorFor(this.stockOf(productId), this.defaultStock());
      long adjusted = Math.round(baseBuyPrice * factor);
      return (int) Math.max(0L, Math.min(2_000_000_000L, adjusted));
   }

   /** Purchase consumed one unit. */
   synchronized void onPurchase(String productId) {
      if (!this.enabled()) return;
      this.stocks.put(productId, Math.max(MIN_STOCK, this.stockOf(productId) - 1));
      this.persist();
   }

   /** Sale to the shop added units. */
   synchronized void onSale(String productId, int amount) {
      if (!this.enabled() || amount <= 0) return;
      this.stocks.put(productId, Math.min(MAX_STOCK, this.stockOf(productId) + amount));
      this.persist();
   }

   /**
    * Monthly restock: +5 to every known product, executed at most once per
    * calendar month (idempotency key in data.yml).
    */
   synchronized void ensureMonthlyRestock() {
      String month = YearMonth.now(ZoneId.of("Asia/Tokyo")).toString();
      String done = this.plugin.data().getString("shop-stock.last-restock-month", "");
      if (month.equals(done)) return;
      int amount = Math.max(0, this.plugin.getConfig().getInt("shop-stock.monthly-restock", MONTHLY_RESTOCK));
      if (amount > 0) {
         for (Map.Entry<String, Integer> entry : this.stocks.entrySet()) {
            entry.setValue(Math.min(MAX_STOCK, entry.getValue() + amount));
         }
      }
      this.plugin.data().set("shop-stock.last-restock-month", month);
      this.persist();
   }

   private void persist() {
      for (Map.Entry<String, Integer> entry : this.stocks.entrySet()) {
         this.plugin.data().set("shop-stock.stocks." + entry.getKey(), entry.getValue());
      }
      this.plugin.queueDataSave();
   }
}
