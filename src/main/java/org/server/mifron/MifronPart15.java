package org.server.mifron;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

abstract class MifronPart15 extends MifronPart14x2 {
   protected void migrateDefaultMinigameLocation() {
      if (!"minigame".equalsIgnoreCase(this.getConfig().getString("servers.minigame.world", "minigame"))) return;
      boolean oldDefault = Math.abs(this.getConfig().getDouble("servers.minigame.x") - 0.5) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("servers.minigame.y") - 64.0) < 1.0E-4
         && Math.abs(this.getConfig().getDouble("servers.minigame.z") - 0.5) < 1.0E-4;
      boolean missing = !this.getConfig().contains("servers.minigame.x") || !this.getConfig().contains("servers.minigame.y") || !this.getConfig().contains("servers.minigame.z");
      if (!(oldDefault || missing)) return;
      this.getConfig().set("servers.minigame.world", "minigame");
      this.getConfig().set("servers.minigame.x", 0.0);
      this.getConfig().set("servers.minigame.y", 0.0);
      this.getConfig().set("servers.minigame.z", 0.0);
      this.getConfig().set("servers.minigame.yaw", 0.0);
      this.getConfig().set("servers.minigame.pitch", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.minigame.world", "minigame");
      this.mifron().setIfMissing("world-rules.spawn.minigame.x", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.minigame.y", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.minigame.z", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.minigame.yaw", 0.0);
      this.mifron().setIfMissing("world-rules.spawn.minigame.pitch", 0.0);
      this.saveConfig();
   }

   protected void setIfMissing(String path, Object value) {
      if (!this.getConfig().contains(path)) this.getConfig().set(path, value);
   }

   protected void writeLocation(String path, Location location) {
      this.getConfig().set(path + ".world", location.getWorld().getName());
      this.getConfig().set(path + ".x", location.getX());
      this.getConfig().set(path + ".y", location.getY());
      this.getConfig().set(path + ".z", location.getZ());
      this.getConfig().set(path + ".yaw", location.getYaw());
      this.getConfig().set(path + ".pitch", location.getPitch());
      this.saveConfig();
   }

   @EventHandler
   public void onAdvancement(PlayerAdvancementDoneEvent event) {
      Advancement advancement = event.getAdvancement();
      if (!this.mifron().shouldTrackAdvancement(advancement)) return;
      Player player = event.getPlayer();
      // Advancements earned in the main world do not count: no recording,
      // no reward, no titles. The vanilla criteria are revoked so the
      // advancement stays re-earnable elsewhere.
      if (this.mainWorldFeature.isMainWorld(player.getWorld())) {
         try {
            var progress = player.getAdvancementProgress(advancement);
            for (String criterion : advancement.getCriteria()) {
               if (progress.getDateAwarded(criterion) != null) progress.revokeCriteria(criterion);
            }
         } catch (Throwable ignored) {
         }
         return;
      }
      String fullKey = advancement.getKey().toString();
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
      completed.add(fullKey);
      section.set("completed-advancements", new ArrayList<>(completed));
      this.notifyUnlockedTitles(player, completed);
      this.mifron().rewardAdvancement(player, advancement, section);
      this.mifron().updateAdvancementBonus(player.getUniqueId(), completed);
      this.mifron().checkAllAdvancementsCompleted(player);
      this.mifron().refreshPlayerName(player);
      this.queueDataSave();
   }

   /**
    * Pays the reward for one tracked advancement exactly once. The rewarded
    * marker is written only after the deposit call, so a crash or a failed
    * payment cannot silently swallow the reward, and a replayed event cannot
    * pay it twice.
    */
   protected boolean rewardAdvancement(Player player, Advancement advancement, ConfigurationSection section) {
      if (player == null || advancement == null || section == null) return false;
      String fullKey = advancement.getKey().toString();
      Set<String> rewarded = new HashSet<>(section.getStringList("rewarded-advancements"));
      if (rewarded.contains(fullKey)) return false;
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();
      int reward = this.advancementReward(frame, "emeralds");
      ConfigurationSection special = this.getConfig().getConfigurationSection("advancement-unlocks." + advancement.getKey().getKey());
      if (special != null) {
         reward = special.getInt("emeralds", reward);
         this.mifron().applyUnlocks(player, special);
      }
      double multiplier = Math.max(0.0D, Math.min(10.0D, this.getConfig().getDouble("advancement-rewards.multiplier", 5.0D)));
      reward = (int) Math.min(2000000000L, Math.max(0L, Math.round(reward * multiplier)));
      int paidReward = this.mifron().applyIncomeBonus(player.getUniqueId(), reward);
      // The rewarded marker is only meaningful once the full amount lands.
      // Without the exact-fit gate a capped wallet would keep the marker while
      // receiving nothing, with no retry path left.
      if (paidReward > 0 && !this.mifron().economyManager.depositExact(player.getUniqueId(), paidReward, false)) {
         player.sendMessage("\u00a7cMP\u4e0a\u9650\u306e\u305f\u3081\u9032\u6357\u5831\u916c\u3092\u53d7\u3051\u53d6\u308c\u307e\u305b\u3093\u3067\u3057\u305f\u3002MP\u3092\u6d88\u8cbb\u3057\u3066\u304b\u3089\u518d\u5ea6\u304a\u8a66\u3057\u304f\u3060\u3055\u3044\u3002");
         return false;
      }
      rewarded.add(fullKey);
      section.set("rewarded-advancements", new ArrayList<>(rewarded));
      player.sendMessage("\u00a7a\u9032\u6357\u5831\u916c: +" + this.mifron().formatNumber(paidReward) + "MP");
      this.queueDataSave();
      return true;
   }

   protected int advancementReward(Frame frame, String field) {
      String path = switch (frame) {
         case CHALLENGE -> "advancement-rewards.challenge.";
         case GOAL -> "advancement-rewards.goal.";
         default -> "advancement-rewards.default.";
      };
      int fallback = switch (frame) {
         case CHALLENGE -> 100;
         case GOAL -> 50;
         default -> 10;
      };
      return this.getConfig().getInt(path + field, fallback);
   }

   protected int advancementBonus(Frame frame) {
      String path = switch (frame) {
         case CHALLENGE -> "advancement-rewards.challenge.bonus-percent";
         case GOAL -> "advancement-rewards.goal.bonus-percent";
         default -> "advancement-rewards.default.bonus-percent";
      };
      int fallback = switch (frame) {
         case CHALLENGE -> 5;
         case GOAL -> 2;
         default -> 1;
      };
      return Math.max(0, this.getConfig().getInt(path, fallback));
   }
}
