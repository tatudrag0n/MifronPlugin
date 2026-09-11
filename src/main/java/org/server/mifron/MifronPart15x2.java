package org.server.mifron;

import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart15x2 extends MifronPart15x1 {
   protected Object economyLock(UUID uuid) {
      return this.economyLocks.computeIfAbsent(uuid, key -> new Object());
   }

   protected void withEconomyLocks(UUID first, UUID second, Runnable action) {
      if (first.equals(second)) {
         synchronized (this.economyLock(first)) { action.run(); }
         return;
      }
      UUID a = first.compareTo(second) < 0 ? first : second;
      UUID b = first.compareTo(second) < 0 ? second : first;
      synchronized (this.economyLock(a)) {
         synchronized (this.economyLock(b)) { action.run(); }
      }
   }

   boolean transferEmeralds(UUID from, UUID to, int amount) {
      if (from == null || to == null || amount <= 0 || from.equals(to)) return false;
      boolean[] ok = {false};
      this.mifron().withEconomyLocks(from, to, () -> {
         if (!this.mifron().withdrawEmeralds(from, amount, false)) return;
         this.mifron().depositEmeralds(to, amount, false);
         this.queueDataSave();
         ok[0] = true;
      });
      if (ok[0]) {
         Player online = Bukkit.getPlayer(to);
         if (online != null) this.recordQuestProgress(online, "mp_gained", amount);
      }
      return ok[0];
   }

   protected void depositEmeralds(UUID uuid, int amount, boolean persist) {
      if (uuid == null || amount <= 0) return;
      synchronized (this.mifron().economyLock(uuid)) {
         ConfigurationSection section = this.mifron().getPlayerSection(uuid);
         int added = Math.min(amount, 2000000000);
         int before = this.mifron().getEmeralds(uuid);
         int after = this.mifron().safeAdd(before, added);
         int credited = Math.max(0, after - before);
         section.set("emeralds", after);
         this.trackEconomyAnalytics(uuid, "mp_earned", credited, after, "unclassified");
         section.set("total-earned-emeralds", this.mifron().safeAdd(section.getInt("total-earned-emeralds", 0), credited));
         if (credited > 0 && !section.getBoolean("analytics.first-mp-earned-recorded", false)) {
            section.set("analytics.first-mp-earned-pending", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) this.flushPendingFirstMpEvent(player);
         }
         if (credited > 0 && !section.getBoolean("analytics.first-reward-recorded", false)) {
            section.set("analytics.first-reward-recorded", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) this.mifron().trackAnalytics(player, "first_reward", "first-reward:" + uuid);
         }
      }
      if (persist) this.queueDataSave();
   }

   boolean withdrawEmeralds(UUID uuid, int amount) {
      return this.mifron().withdrawEmeralds(uuid, amount, true);
   }

   protected boolean withdrawEmeralds(UUID uuid, int amount, boolean persist) {
      if (uuid == null || amount <= 0) return false;
      boolean ok;
      synchronized (this.mifron().economyLock(uuid)) {
         ConfigurationSection section = this.mifron().getPlayerSection(uuid);
         int current = this.mifron().getEmeralds(uuid);
         if (current < amount) return false;
         section.set("emeralds", current - amount);
         this.trackEconomyAnalytics(uuid, "mp_spent", amount, current - amount, "unclassified");
         ok = true;
      }
      if (persist) this.queueDataSave();
      return ok;
   }

   protected int safeAdd(int current, int amount) {
      long result = (long) Math.max(0, current) + Math.max(0, amount);
      return (int) Math.min(2000000000L, result);
   }

   protected int safeMultiply(int left, int right) {
      long result = (long) Math.max(0, left) * Math.max(0, right);
      return (int) Math.min(2000000000L, result);
   }

   int applyIncomeBonus(UUID uuid, int base) {
      if (base <= 0) return 0;
      int bonus = this.getTotalIncomeBonus(uuid);
      long reward = base + (long) base * Math.max(0, bonus) / 100L;
      return (int) Math.min(2000000000L, reward);
   }

   protected int getMfl(UUID uuid) {
      ConfigurationSection section = this.mifron().getPlayerSection(uuid);
      long score = (long) Math.max(0, section.getInt("total-blocks-broken", 0))
         + Math.max(0L, section.getInt("total-blocks-placed", 0))
         + (long) Math.max(0, section.getInt("total-trades", 0)) * 5L
         + Math.max(0L, section.getInt("total-minutes", 0))
         + (long) Math.max(0, section.getInt("total-play-count", 0)) * 10L
         + (long) Math.max(0, section.getInt("total-mob-kills", 0)) * 3L
         + (long) section.getStringList("completed-advancements").size() * 50L;
      return (int) Math.max(1L, Math.min(2000000000L, score / 100L + 1L));
   }

   protected String getMflRank(UUID uuid) {
      int mfl = this.mifron().getMfl(uuid);
      if (mfl >= 100) return "S";
      if (mfl >= 60) return "A";
      if (mfl >= 30) return "B";
      return mfl >= 10 ? "C" : "D";
   }

   protected int getAdvancementBonus(UUID uuid) {
      Set<String> completed = new HashSet<>(this.mifron().getPlayerSection(uuid).getStringList("completed-advancements"));
      return completed.isEmpty() ? 0 : this.calculateAdvancementBonus(completed);
   }

   int getReincarnationBonus(UUID uuid) {
      return this.mifron().getPlayerSection(uuid).getInt("reincarnation-bonus-percent", 0);
   }

   protected int getTotalIncomeBonus(UUID uuid) {
      return this.mifron().safeAdd(this.mifron().getReincarnationBonus(uuid), this.mifron().getAdvancementBonus(uuid));
   }

   protected int updateAdvancementBonus(UUID uuid, Set<String> completed) {
      ConfigurationSection section = this.mifron().getPlayerSection(uuid);
      int bonus = this.calculateAdvancementBonus(completed);
      section.set("advancement-bonus-percent", bonus);
      section.set("income-bonus-percent", null);
      this.queueDataSave();
      return bonus;
   }

   protected int calculateAdvancementBonus(Set<String> completed) {
      if (completed == null || completed.isEmpty()) return 0;
      int total = 0;
      for (String key : completed) {
         total = this.mifron().safeAdd(total, this.bonusPercentForAdvancementKey(key));
      }
      return total;
   }

   protected int bonusPercentForAdvancementKey(String fullKey) {
      if (fullKey == null || fullKey.isBlank()) return 0;
      String shortKey = fullKey.contains(":") ? fullKey.substring(fullKey.indexOf(':') + 1) : fullKey;
      ConfigurationSection special = this.getConfig().getConfigurationSection("advancement-unlocks." + shortKey);
      if (special != null && special.contains("bonus-percent")) {
         return Math.max(0, special.getInt("bonus-percent"));
      }
      Advancement advancement = Bukkit.getAdvancement(NamespacedKey.fromString(fullKey));
      AdvancementDisplay display = advancement == null ? null : advancement.getDisplay();
      AdvancementDisplay.Frame frame = display == null ? AdvancementDisplay.Frame.TASK : display.frame();
      return this.mifron().advancementBonus(frame);
   }

   protected void addAdvancementBonus(UUID uuid, int percent) {
      this.mifron().updateAdvancementBonus(uuid, new HashSet<>(this.mifron().getPlayerSection(uuid).getStringList("completed-advancements")));
   }

   protected void addReincarnationBonus(UUID uuid, int percent) {
      ConfigurationSection section = this.mifron().getPlayerSection(uuid);
      section.set("reincarnation-bonus-percent", this.mifron().safeAdd(this.mifron().getReincarnationBonus(uuid), percent));
      this.queueDataSave();
   }

   void addPlayerStat(UUID uuid, String key, int amount) {
      this.mifron().addPlayerStat(uuid, key, amount, true);
   }
}
