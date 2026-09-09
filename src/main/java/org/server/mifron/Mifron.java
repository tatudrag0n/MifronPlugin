package org.server.mifron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.generator.WorldInfo;

public final class Mifron extends MifronPart18x2 implements Listener, TabExecutor {
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && this.isMifronRootCommand(command)) {
         return List.of(
            "check", "list", "tp", "text", "ffa", "structure", "build", "proposal", "gamerules",
            "info", "reload", "kit", "balance", "pay", "merchant", "marchant", "minigame", "athletic",
            "quest", "mp", "regen", "chunk", "status", "tutorial", "shelfshop", "shopwand", "slotwand",
            "jumppadwand", "serverwand", "sethub", "setserver", "delserver", "warning"
         );
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "text".equalsIgnoreCase(args[0])) return this.textDisplayFeature.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "ffa".equalsIgnoreCase(args[0])) return this.ffaManager.tabComplete(args, sender);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "structure".equalsIgnoreCase(args[0])) return this.structureManager.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "build".equalsIgnoreCase(args[0])) return this.buildWorldManager.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "proposal".equalsIgnoreCase(args[0])) return this.proposalManager.tabComplete(args);
      if (args.length == 2 && this.isMifronRootCommand(command) && "quest".equalsIgnoreCase(args[0])) return List.of("progress", "propose", "cancel");
      if (args.length == 1 && "friend".equalsIgnoreCase(command.getName())) return List.of("add", "accept", "remove", "chat");
      return Collections.emptyList();
   }

   protected boolean isMifronRootCommand(Command command) {
      return "mifron".equalsIgnoreCase(command.getName()) || "mf".equalsIgnoreCase(command.getName());
   }
}
