package org.server.mifron;

import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class MifronPart18 extends MifronPart17x1 {
   protected void handleQuestCommand(CommandSender sender, String[] args) {
      if (sender instanceof Player player && this.questProposalFeature.handleCommand(player, args)) return;
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7e/mf quest propose  \u3067\u30af\u30a8\u30b9\u30c8\u3092\u63d0\u6848\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      if (args.length >= 5 && "progress".equalsIgnoreCase(args[1])) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[2]);
         if (target != null && target.isOnline() && target.getPlayer() != null) {
            int amount = this.mifron().parsePositiveInt(args[4], -1);
            if (amount < 0) sender.sendMessage("\u00a7camount \u306f0\u4ee5\u4e0a\u306e\u6570\u5b57\u306b\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
            else this.questService.setQuestProgress(target.getPlayer(), args[3].toUpperCase(Locale.ROOT), amount);
         } else sender.sendMessage("\u00a7c\u30aa\u30f3\u30e9\u30a4\u30f3\u306e\u30d7\u30ec\u30a4\u30e4\u30fc\u3092\u6307\u5b9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
         return;
      }
      sender.sendMessage("\u00a7c/mifron quest progress <player> <questId> <amount>");
   }

   protected void handleAthleticCommand(Player player, String[] args) {
      if (!this.mifron().hasPermission(player, "mifron.reward.grant")) return;
      if (args.length >= 3 && "complete".equalsIgnoreCase(args[1])) {
         String difficulty = args[2].toLowerCase(Locale.ROOT);
         int base = switch (difficulty) {
            case "easy", "\u30a4\u30fc\u30b8\u30fc" -> 5;
            case "normal", "\u30ce\u30fc\u30de\u30eb" -> 10;
            case "hard", "\u30cf\u30fc\u30c9" -> 50;
            case "hardcore", "\u30cf\u30fc\u30c9\u30b3\u30a2" -> 100;
            default -> -1;
         };
         if (base < 0) {
            player.sendMessage("\u00a7c\u96e3\u6613\u5ea6\u306f easy, normal, hard, hardcore \u306e\u3044\u305a\u308c\u304b\u3067\u3059\u3002");
            return;
         }
         int misses = args.length >= 4 ? this.mifron().parsePositiveInt(args[3], 0) : 0;
         int reward = this.mifron().applyIncomeBonus(player.getUniqueId(), Math.max(0, base - misses));
         this.mifron().depositEmeralds(player.getUniqueId(), reward);
         this.mifron().addPlayerStat(player.getUniqueId(), "athletic-clears", 1);
         if ("hardcore".equals(difficulty) || "\u30cf\u30fc\u30c9\u30b3\u30a2".equals(difficulty)) this.recordQuestProgress(player, "hardcore_athletic", 1);
         player.sendMessage("\u00a7a\u30a2\u30b9\u30ec\u30c1\u30c3\u30af\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
         return;
      }
      player.sendMessage("\u00a7c/mifron athletic complete <easy|normal|hard|hardcore> [misses]");
   }

   protected void handleMinigameCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("\u00a7c/mifron minigame play|win|unlock <name> <amount>");
         return;
      }
      switch (args[1].toLowerCase(Locale.ROOT)) {
         case "play" -> {
            if (!this.mifron().hasPermission(player, "mifron.reward.grant")) return;
            int reward = this.mifron().applyIncomeBonus(player.getUniqueId(), 10);
            this.mifron().depositEmeralds(player.getUniqueId(), reward);
            this.mifron().addPlayerStat(player.getUniqueId(), "minigame-plays", 1);
            player.sendMessage("\u00a7a\u30df\u30cb\u30b2\u30fc\u30e0\u53c2\u52a0\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
         }
         case "win" -> {
            if (!this.mifron().hasPermission(player, "mifron.reward.grant")) return;
            int reward = this.mifron().applyIncomeBonus(player.getUniqueId(), 10);
            this.mifron().depositEmeralds(player.getUniqueId(), reward);
            this.mifron().addPlayerStat(player.getUniqueId(), "minigame-wins", 1);
            this.recordQuestProgress(player, "minigame_champion", 1);
            player.sendMessage("\u00a7a\u30df\u30cb\u30b2\u30fc\u30e0\u52dd\u5229\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
         }
         case "unlock" -> {
            if (args.length < 4) { player.sendMessage("\u00a7c/mifron minigame unlock <name> <amount>"); return; }
            int amount = this.mifron().parsePositiveInt(args[3], -1);
            String key = args[2].toLowerCase(Locale.ROOT);
            if (!this.isSafeConfigKey(key)) { this.sendInvalidConfigKeyMessage(player, "\u30df\u30cb\u30b2\u30fc\u30e0\u540d"); return; }
            if (amount <= 0 || !this.mifron().withdrawEmeralds(player.getUniqueId(), amount)) { player.sendMessage("\u00a7c\u7d0d\u54c1\u3067\u304d\u307e\u305b\u3093\u3002"); return; }
            String path = "minigames." + key + ".donated";
            int donated = this.mifron().safeAdd(this.data.getInt(path, 0), amount);
            this.data.set(path, donated);
            this.recordQuestProgress(player, "community_donations", amount);
            this.recordQuestProgress(player, "community_participation", 1);
            this.recordQuestProgress(player, "server_unlock_contribution", amount);
            int required = this.getConfig().getInt("minigame-unlocks." + key + ".required-emeralds", 0);
            if (required > 0 && donated >= required) this.data.set("minigames." + key + ".unlocked", true);
            this.mifron().saveData();
            player.sendMessage("\u00a7a" + key + " \u306b " + this.mifron().formatNumber(amount) + "MP \u7d0d\u54c1\u3057\u307e\u3057\u305f\u3002");
         }
         default -> player.sendMessage("\u00a7c/mifron minigame play|win|unlock <name> <amount>");
      }
   }

   protected void handleEmeraldCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) { sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (args.length >= 4 && List.of("give", "grant", "add").contains(args[1].toLowerCase(Locale.ROOT))) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[2]);
         if (target == null) return;
         int amount = this.mifron().parsePositiveInt(args[3], -1);
         if (amount <= 0) { sender.sendMessage("\u00a7c\u914d\u5e03MP\u306f1\u4ee5\u4e0a\u306e\u6570\u5b57\u306b\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); return; }
         this.mifron().depositEmeralds(target.getUniqueId(), amount);
         sender.sendMessage("\u00a7a" + this.mifron().safePlayerName(target) + " \u306b " + this.mifron().formatNumber(amount) + "MP \u3092\u914d\u5e03\u3057\u307e\u3057\u305f\u3002");
         if (target.isOnline() && target.getPlayer() != null) target.getPlayer().sendMessage("\u00a7a\u7ba1\u7406\u8005\u304b\u3089 " + this.mifron().formatNumber(amount) + "MP \u304c\u914d\u5e03\u3055\u308c\u307e\u3057\u305f\u3002");
         return;
      }
      sender.sendMessage("\u00a7c/mifron mp give <player> <amount>");
   }

   protected void handlePayCommand(Player player, String[] args) {
      if (args.length < 3) { player.sendMessage("\u00a7c/mifron pay <player> <amount>"); return; }
      OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
      if (target == null) return;
      if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("\u00a7c\u81ea\u5206\u306b\u306f\u652f\u6255\u3048\u307e\u305b\u3093\u3002"); return; }
      int amount = this.mifron().parsePositiveInt(args[2], -1);
      if (amount > 0 && this.mifron().withdrawEmeralds(player.getUniqueId(), amount)) {
         this.mifron().depositEmeralds(target.getUniqueId(), amount);
         player.sendMessage("\u00a7a" + this.mifron().safePlayerName(target) + " \u306b " + this.mifron().formatNumber(amount) + "MP \u652f\u6255\u3044\u307e\u3057\u305f\u3002");
      } else player.sendMessage("\u00a7c\u652f\u6255\u3044\u3067\u304d\u307e\u305b\u3093\u3002");
   }
}
