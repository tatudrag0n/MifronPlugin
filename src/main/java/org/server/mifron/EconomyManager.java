package org.server.mifron;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Owns the player balance primitives (deposit / withdraw / transfer / refund)
 * and the per-player lock stripe used to serialise them. Extracted from the
 * MifronPart15x1/x2 chain so the money rules live in one testable place.
 *
 * <p>The plugin keeps thin delegating methods with the historical names so the
 * dozens of existing call sites stay source-compatible. All mutating operations
 * are serialised per UUID; transfers lock both accounts in UUID order to avoid
 * deadlocks.
 */
final class EconomyManager {

   static final int MAX_EMERALDS = 2_000_000_000;

   private final Mifron plugin;
   private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

   EconomyManager(Mifron plugin) {
      this.plugin = plugin;
   }

   Object lock(UUID uuid) {
      return this.locks.computeIfAbsent(uuid, key -> new Object());
   }

   void withLocks(UUID first, UUID second, Runnable action) {
      if (first.equals(second)) {
         synchronized (this.lock(first)) { action.run(); }
         return;
      }
      UUID a = first.compareTo(second) < 0 ? first : second;
      UUID b = first.compareTo(second) < 0 ? second : first;
      synchronized (this.lock(a)) {
         synchronized (this.lock(b)) { action.run(); }
      }
   }

   int balance(UUID uuid) {
      if (uuid == null) return 0;
      synchronized (this.lock(uuid)) {
         return clamp(this.plugin.getPlayerSection(uuid).getInt("emeralds", 0));
      }
   }

   void deposit(UUID uuid, int amount, boolean persist) {
      if (uuid == null || amount <= 0) return;
      synchronized (this.lock(uuid)) {
         ConfigurationSection section = this.plugin.getPlayerSection(uuid);
         int added = Math.min(amount, MAX_EMERALDS);
         int before = clamp(section.getInt("emeralds", 0));
         int after = safeAdd(before, added);
         int credited = Math.max(0, after - before);
         section.set("emeralds", after);
         this.plugin.trackEconomyAnalytics(uuid, "mp_earned", credited, after, "unclassified");
         section.set("total-earned-emeralds", safeAdd(section.getInt("total-earned-emeralds", 0), credited));
         if (credited > 0 && !section.getBoolean("analytics.first-mp-earned-recorded", false)) {
            section.set("analytics.first-mp-earned-pending", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) this.plugin.flushPendingFirstMpEvent(player);
         }
         if (credited > 0 && !section.getBoolean("analytics.first-reward-recorded", false)) {
            section.set("analytics.first-reward-recorded", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) this.plugin.trackAnalytics(player, "first_reward", "first-reward:" + uuid);
         }
      }
      if (persist) this.plugin.queueDataSave();
   }

   boolean withdraw(UUID uuid, int amount, boolean persist) {
      if (uuid == null || amount <= 0) return false;
      boolean ok;
      synchronized (this.lock(uuid)) {
         ConfigurationSection section = this.plugin.getPlayerSection(uuid);
         int current = clamp(section.getInt("emeralds", 0));
         if (current < amount) return false;
         section.set("emeralds", current - amount);
         this.plugin.trackEconomyAnalytics(uuid, "mp_spent", amount, current - amount, "unclassified");
         ok = true;
      }
      if (persist) this.plugin.queueDataSave();
      return ok;
   }

   void refund(UUID uuid, int amount) {
      if (uuid == null || amount <= 0) return;
      synchronized (this.lock(uuid)) {
         ConfigurationSection section = this.plugin.getPlayerSection(uuid);
         int before = clamp(section.getInt("emeralds", 0));
         int after = safeAdd(before, amount);
         section.set("emeralds", after);
         this.plugin.trackEconomyAnalytics(uuid, "mp_earned", Math.max(0, after - before), after, "refund");
      }
      this.plugin.queueDataSave();
   }

   boolean transfer(UUID from, UUID to, int amount) {
      if (from == null || to == null || amount <= 0 || from.equals(to)) return false;
      boolean[] ok = {false};
      this.withLocks(from, to, () -> {
         if (!this.withdraw(from, amount, false)) return;
         this.deposit(to, amount, false);
         this.plugin.queueDataSave();
         ok[0] = true;
      });
      if (ok[0]) {
         Player online = Bukkit.getPlayer(to);
         if (online != null) this.plugin.recordQuestProgress(online, "mp_gained", amount);
      }
      return ok[0];
   }

   int applyIncomeBonus(UUID uuid, int base) {
      if (base <= 0) return 0;
      int bonus = this.plugin.getTotalIncomeBonus(uuid);
      long reward = base + (long) base * Math.max(0, bonus) / 100L;
      return (int) Math.min((long) MAX_EMERALDS, reward);
   }

   static int safeAdd(int current, int amount) {
      long result = (long) Math.max(0, current) + Math.max(0, amount);
      return (int) Math.min((long) MAX_EMERALDS, result);
   }

   static int safeMultiply(int left, int right) {
      long result = (long) Math.max(0, left) * Math.max(0, right);
      return (int) Math.min((long) MAX_EMERALDS, result);
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(MAX_EMERALDS, value));
   }
}
