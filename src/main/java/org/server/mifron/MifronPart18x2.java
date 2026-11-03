package org.server.mifron;

import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class MifronPart18x2 extends MifronPart18x1 {
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
