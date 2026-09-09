package org.server.mifron;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
      this.setIfMissing("world-rules.spawn.minigame.world", "minigame");
      this.setIfMissing("world-rules.spawn.minigame.x", 0.0);
      this.setIfMissing("world-rules.spawn.minigame.y", 0.0);
      this.setIfMissing("world-rules.spawn.minigame.z", 0.0);
      this.setIfMissing("world-rules.spawn.minigame.yaw", 0.0);
      this.setIfMissing("world-rules.spawn.minigame.pitch", 0.0);
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
   public void onChat(AsyncChatEvent event) {
      Player sender = event.getPlayer();
      String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      if (this.pendingFriendSearch.containsKey(sender.getUniqueId())) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> {
            String plainMessage = this.sanitizeTextInput(rawMessage, 32);
            this.pendingFriendSearch.remove(sender.getUniqueId());
            if (!plainMessage.equalsIgnoreCase("clear") && !plainMessage.isBlank()) this.friendSearchFilters.put(sender.getUniqueId(), plainMessage);
            else this.friendSearchFilters.remove(sender.getUniqueId());
            this.openFriendUi(sender);
         });
         return;
      }
      if (this.pendingFriendChatInput.containsKey(sender.getUniqueId())) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> {
            UUID chatTarget = this.pendingFriendChatInput.remove(sender.getUniqueId());
            if (chatTarget == null) return;
            String plainMessage = this.sanitizeTextInput(rawMessage, 256);
            if (!plainMessage.isBlank()) {
               this.activeFriendChatTarget.put(sender.getUniqueId(), chatTarget);
               this.friendChatDrafts.put(sender.getUniqueId(), plainMessage);
            }
            this.openFriendUi(sender);
         });
         return;
      }
      event.setCancelled(true);
      Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("\u00a77\u901a\u5e38\u30c1\u30e3\u30c3\u30c8\u306fDiscord\u3067\u3054\u5229\u7528\u304f\u3060\u3055\u3044\u3002"));
   }

   @EventHandler
   public void onAdvancement(PlayerAdvancementDoneEvent event) {
      Advancement advancement = event.getAdvancement();
      if (!this.shouldTrackAdvancement(advancement)) return;
      String fullKey = advancement.getKey().toString();
      Player player = event.getPlayer();
      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      completed.add(fullKey);
      this.getPlayerSection(player.getUniqueId()).set("completed-advancements", new ArrayList<>(completed));
      this.notifyUnlockedTitles(player, completed);
      Set<String> rewarded = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("rewarded-advancements"));
      if (!rewarded.add(fullKey)) { this.queueDataSave(); return; }
      this.getPlayerSection(player.getUniqueId()).set("rewarded-advancements", new ArrayList<>(rewarded));
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();
      int reward = this.advancementReward(frame, "emeralds");
      ConfigurationSection special = this.getConfig().getConfigurationSection("advancement-unlocks." + advancement.getKey().getKey());
      if (special != null) {
         reward = special.getInt("emeralds", reward);
         this.applyUnlocks(player, special);
      }
      double multiplier = Math.max(0.0D, Math.min(10.0D, this.getConfig().getDouble("advancement-rewards.multiplier", 5.0D)));
      reward = (int) Math.min(2000000000L, Math.max(0L, Math.round(reward * multiplier)));
      int paidReward = this.applyIncomeBonus(player.getUniqueId(), reward);
      this.depositEmeralds(player.getUniqueId(), paidReward);
      this.updateAdvancementBonus(player.getUniqueId(), completed);
      player.sendMessage("\u00a7a\u9032\u6357\u5831\u916c: +" + this.formatNumber(paidReward) + "MP");
      this.checkAllAdvancementsCompleted(player);
      this.refreshPlayerName(player);
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

   protected int advancementBonus(Frame frame) { return 0; }
}
