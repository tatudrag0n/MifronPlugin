package org.server.mifron;

import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class MifronPart18 extends MifronPart17x1 {
   protected void handleQuestCommand(CommandSender sender, String[] args) {
      if (sender instanceof Player player && args.length < 2) {
         this.mifron().openQuestUi(player, "categories");
         return;
      }
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

   protected void handleTradeCommand(Player player, String[] args) {
      if (args.length == 0) {
         player.sendMessage("\u00a7e/trade <player>|accept|deny|cancel|money <\u984d>");
         return;
      }
      String sub = args[0].toLowerCase(java.util.Locale.ROOT);
      switch (sub) {
         case "accept" -> this.mifron().tradeService.accept(player);
         case "deny" -> this.mifron().tradeService.deny(player);
         case "cancel" -> this.mifron().tradeService.cancel(player, null);
         case "money" -> {
            if (args.length < 2) { player.sendMessage("\u00a7c/trade money <\u984d>"); return; }
            this.mifron().tradeService.setMoney(player, this.mifron().parsePositiveInt(args[1], -1));
         }
         default -> {
            org.bukkit.OfflinePlayer target = this.resolveKnownPlayer(player, args[0]);
            if (target == null || !target.isOnline() || target.getPlayer() == null) {
               player.sendMessage("\u00a7c\u76f8\u624b\u304c\u30aa\u30d5\u30e9\u30a4\u30f3\u3067\u3059\u3002");
               return;
            }
            this.mifron().tradeService.request(player, target.getPlayer());
         }
      }
   }

   protected void handleTravelCommand(Player player, String[] args) {
      if (args.length == 0) {
         player.sendMessage("\u00a77/travel <hub|survival|main>");
         player.sendMessage("\u00a77\u6599\u91d1: " + this.mifron().formatNumber(this.mifron().jobService.travelCost(player)) + "MP");
         return;
      }
      this.mifron().jobService.travel(player, args[0]);
   }

   protected void handlePayCommand(Player player, String[] args) {
      if (args.length < 3) { player.sendMessage("\u00a7c/mifron pay <player> <amount>"); return; }
      OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
      if (target == null) return;
      if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("\u00a7c\u81ea\u5206\u306b\u306f\u652f\u6255\u3048\u307e\u305b\u3093\u3002"); return; }
      int amount = this.mifron().parsePositiveInt(args[2], -1);
      if (amount > 0 && this.mifron().transferEmeralds(player.getUniqueId(), target.getUniqueId(), amount)) {
         player.sendMessage("\u00a7a" + this.mifron().safePlayerName(target) + " \u306b " + this.mifron().formatNumber(amount) + "MP \u652f\u6255\u3044\u307e\u3057\u305f\u3002");
         if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage("\u00a7a" + this.mifron().safePlayerName(player) + " \u304b\u3089 " + this.mifron().formatNumber(amount) + "MP \u3092\u53d7\u3051\u53d6\u308a\u307e\u3057\u305f\u3002");
         }
      } else player.sendMessage("\u00a7c\u652f\u6255\u3044\u3067\u304d\u307e\u305b\u3093\u3002");
   }
}
