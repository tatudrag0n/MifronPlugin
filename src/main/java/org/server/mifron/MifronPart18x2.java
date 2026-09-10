package org.server.mifron;

import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class MifronPart18x2 extends MifronPart18x1 {
   protected boolean handleFriendCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Player only.");
         return true;
      }
      if (args.length == 0) { this.mifron().openFriendUi(player); return true; }
      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "add" -> {
            if (args.length < 2) { player.sendMessage("\u00a7c/friend add <player>"); return true; }
            OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
            if (target != null) this.mifron().sendFriendRequest(player, target);
         }
         case "accept" -> {
            if (args.length < 2) { player.sendMessage("\u00a7c/friend accept <player>"); return true; }
            OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
            if (target != null) this.mifron().acceptFriendRequest(player, target);
         }
         case "remove" -> {
            if (args.length < 2) { player.sendMessage("\u00a7c/friend remove <player>"); return true; }
            OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
            if (target != null) this.mifron().removeFriend(player, target);
         }
         case "chat" -> {
            if (args.length < 3) { player.sendMessage("\u00a7c/friend chat <player> <message>"); return true; }
            OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
            if (target != null) {
               String message = this.sanitizeTextInput(String.join(" ", List.of(args).subList(2, args.length)), 256);
               this.sendFriendChat(player, target, message);
            }
         }
         default -> player.sendMessage("\u00a7e/friend add|accept|remove|chat");
      }
      return true;
   }
   protected boolean handleStatusCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return true;
      }
      if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[0]);
         if (target == null) return true;
         this.resetStatusData(target.getUniqueId());
         sender.sendMessage("\u00a7a" + this.mifron().safePlayerName(target) + " \u306e\u30b9\u30c6\u30fc\u30bf\u30b9\u3092\u30ea\u30bb\u30c3\u30c8\u3057\u307e\u3057\u305f\u3002");
         if (target.isOnline() && target.getPlayer() != null) {
            this.resetAdvancements(target.getPlayer());
            this.mifron().getPlayerSection(target.getUniqueId()).set("pending-advancement-reset", null);
            this.mifron().saveData();
            target.getPlayer().sendMessage("\u00a7e\u30b9\u30c6\u30fc\u30bf\u30b9\u304c\u30ea\u30bb\u30c3\u30c8\u3055\u308c\u307e\u3057\u305f\u3002");
         }
         return true;
      }
      sender.sendMessage("\u00a7c/status <player> reset");
      return true;
   }
}
