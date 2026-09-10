package org.server.mifron;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart15x1 extends MifronPart15 {
   protected void syncAdvancementState(Player player) {
      if (!player.isOnline()) return;
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
      Set<String> rewarded = new HashSet<>(section.getStringList("rewarded-advancements"));
      boolean changed = false;
      Iterator<Advancement> iterator = Bukkit.advancementIterator();
      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (!this.mifron().shouldTrackAdvancement(advancement)) continue;
         String key = advancement.getKey().toString();
         AdvancementProgress progress = player.getAdvancementProgress(advancement);
         if (progress.isDone()) {
            changed |= completed.add(key);
            changed |= rewarded.add(key);
         } else if (completed.contains(key)) {
            changed |= rewarded.add(key);
            for (String criterion : new ArrayList<>(progress.getRemainingCriteria())) progress.awardCriteria(criterion);
            changed = true;
         }
      }
      if (changed) {
         section.set("completed-advancements", new ArrayList<>(completed));
         section.set("rewarded-advancements", new ArrayList<>(rewarded));
      }
      this.mifron().updateAdvancementBonus(player.getUniqueId(), completed);
      this.queueDataSave();
      this.mifron().checkAllAdvancementsCompleted(player);
      this.notifyUnlockedTitles(player, completed);
      this.mifron().refreshPlayerName(player);
   }

   protected boolean shouldTrackAdvancement(Advancement advancement) {
      return !advancement.getKey().getKey().startsWith("recipes/");
   }

   protected void checkAllAdvancementsCompleted(Player player) {
      int total = this.mifron().countTrackableAdvancements();
      if (total <= 0 || this.mifron().countCompletedAdvancements(player) < total) return;
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      if (section.getBoolean("all-advancements-rewarded", false)) return;
      int paidReward = this.mifron().applyIncomeBonus(player.getUniqueId(), 10000);
      this.mifron().depositEmeralds(player.getUniqueId(), paidReward);
      this.mifron().updateAdvancementBonus(player.getUniqueId(), new HashSet<>(section.getStringList("completed-advancements")));
      section.set("all-advancements-rewarded", true);
      this.queueDataSave();
      player.sendMessage("\u00a76\u5168\u9032\u6357\u9054\u6210\u5831\u916c: +" + this.mifron().formatNumber(paidReward) + "MP");
   }

   protected void applyUnlocks(Player player, ConfigurationSection section) {
      if (section.contains("shop-discount")) {
         this.mifron().getPlayerSection(player.getUniqueId()).set("shop-discount",
            Math.max(this.mifron().getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0), section.getInt("shop-discount")));
      }
      for (String listKey : List.of("unlocks", "skins", "pets", "ffa-classes")) {
         List<String> values = section.getStringList(listKey);
         if (values.isEmpty()) continue;
         Set<String> current = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList(listKey));
         current.addAll(values);
         this.mifron().getPlayerSection(player.getUniqueId()).set(listKey, new ArrayList<>(current));
      }
      this.queueDataSave();
   }

   protected void handleLoginReward(Player player) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      String today = LocalDate.now(ZoneId.systemDefault()).toString();
      if (today.equals(section.getString("last-login-reward"))) return;
      LocalDate last = null;
      String lastValue = section.getString("last-login-reward");
      if (lastValue != null) last = LocalDate.parse(lastValue);
      int streak = last != null && last.plusDays(1L).toString().equals(today) ? this.mifron().safeAdd(section.getInt("login-streak", 0), 1) : 1;
      int total = this.mifron().safeAdd(section.getInt("total-logins", 0), 1);
      section.set("last-login-reward", today);
      section.set("login-streak", streak);
      section.set("total-logins", total);
      int reward = this.mifron().applyIncomeBonus(player.getUniqueId(), 10 + streak);
      if (total % 10 == 0) reward = this.mifron().safeAdd(reward, this.mifron().safeMultiply(100, total / 10));
      this.mifron().depositEmeralds(player.getUniqueId(), reward);
      player.sendMessage("\u00a7a\u30ed\u30b0\u30a4\u30f3\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
      this.queueDataSave();
   }

   protected void grantPlaytimeRewards() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
         int minutes = this.mifron().safeAdd(section.getInt("session-minutes", 0), 1);
         section.set("session-minutes", minutes);
         int totalMinutes = this.mifron().safeAdd(section.getInt("total-minutes", 0), 1);
         section.set("total-minutes", totalMinutes);
         if (minutes % 10 != 0) continue;
         int sessionRewards = section.getInt("session-playtime-rewards", 0);
         if (sessionRewards >= 20) continue;
         int reward = this.mifron().applyIncomeBonus(player.getUniqueId(), 10);
         if (totalMinutes % 6000 == 0) reward = this.mifron().safeAdd(reward, this.mifron().safeMultiply(100, totalMinutes / 6000));
         section.set("session-playtime-rewards", sessionRewards + 1);
         this.mifron().depositEmeralds(player.getUniqueId(), reward);
         player.sendMessage("\u00a7a\u6ede\u5728\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
      }
      this.queueDataSave();
   }

   protected void routeByWarningLevel(Player player) {
      int warning = this.mifron().getPlayerSection(player.getUniqueId()).getInt("warning-level", 0);
      if (warning >= 4) { player.kick(Component.text("BAN: warning level 4")); return; }
      String path = switch (warning) {
         case 1 -> "warning-servers.caution";
         case 2 -> "warning-servers.detention";
         case 3 -> "warning-servers.imprisonment";
         default -> "warning-servers.basic";
      };
      Location location = this.mifron().readLocation(path);
      if (location != null) Bukkit.getScheduler().runTaskLater(this, () -> player.teleport(location), 5L);
   }

   int getEmeralds(UUID uuid) {
      return Math.max(0, Math.min(2000000000, this.mifron().getPlayerSection(uuid).getInt("emeralds", 0)));
   }

   void depositEmeralds(UUID uuid, int amount) {
      this.mifron().depositEmeralds(uuid, amount, true);
      Player online = Bukkit.getPlayer(uuid);
      if (online != null && amount > 0) this.recordQuestProgress(online, "mp_gained", amount);
   }

   void refundEmeralds(UUID uuid, int amount) {
      if (uuid == null || amount <= 0) return;
      ConfigurationSection section = this.mifron().getPlayerSection(uuid);
      int before = this.mifron().getEmeralds(uuid);
      int after = this.mifron().safeAdd(before, amount);
      section.set("emeralds", after);
      this.trackEconomyAnalytics(uuid, "mp_earned", Math.max(0, after - before), after, "refund");
      this.queueDataSave();
   }
}
