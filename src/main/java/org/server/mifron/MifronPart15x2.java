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
      return this.economyManager.lock(uuid);
   }

   protected void withEconomyLocks(UUID first, UUID second, Runnable action) {
      this.economyManager.withLocks(first, second, action);
   }

   boolean transferEmeralds(UUID from, UUID to, int amount) {
      return this.economyManager.transfer(from, to, amount);
   }

   protected void depositEmeralds(UUID uuid, int amount, boolean persist) {
      this.economyManager.deposit(uuid, amount, persist);
   }

   boolean withdrawEmeralds(UUID uuid, int amount) {
      return this.mifron().withdrawEmeralds(uuid, amount, true);
   }

   protected boolean withdrawEmeralds(UUID uuid, int amount, boolean persist) {
      return this.economyManager.withdraw(uuid, amount, persist);
   }

   protected int safeAdd(int current, int amount) {
      return EconomyManager.safeAdd(current, amount);
   }

   protected int safeMultiply(int left, int right) {
      return EconomyManager.safeMultiply(left, right);
   }

   int applyIncomeBonus(UUID uuid, int base) {
      return this.economyManager.applyIncomeBonus(uuid, base);
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
      NamespacedKey namespacedKey = fullKey == null ? null : NamespacedKey.fromString(fullKey);
      // Hand-edited or legacy garbage keys must not break bonus math (or the
      // Status UI that calls it): treat them as worth 0 instead of throwing.
      if (namespacedKey == null) return 0;
      Advancement advancement = Bukkit.getAdvancement(namespacedKey);
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
