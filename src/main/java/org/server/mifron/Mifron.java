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

public final class Mifron extends MifronPart18 implements Listener, TabExecutor {
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && this.isMifronRootCommand(command)) {
         return List.of(
            "check", "list", "tp", "text", "ffa", "structure", "build", "proposal", "gamerules",
            "info", "reload", "kit", "balance", "pay", "merchant", "marchant", "minigame", "athletic",
            "quest", "mp", "regen", "chunk", "status", "tutorial", "shelfshop", "shopwand", "slotwand",
            "jumppadwand", "serverwand", "sethub", "setserver", "delserver", "warning"
         );
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "text".equalsIgnoreCase(args[0])) {
         return this.textDisplayFeature.tabComplete(args);
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "ffa".equalsIgnoreCase(args[0])) {
         return this.ffaManager.tabComplete(args, sender);
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "structure".equalsIgnoreCase(args[0])) {
         return this.structureManager.tabComplete(args);
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "build".equalsIgnoreCase(args[0])) {
         return this.buildWorldManager.tabComplete(args);
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "proposal".equalsIgnoreCase(args[0])) {
         return this.proposalManager.tabComplete(args);
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "shopwand".equalsIgnoreCase(args[0])) {
         return List.of("shelf", "barrel", "frame");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "shelfshop".equalsIgnoreCase(args[0])) {
         return List.of("configure", "set", "clearall", "clear", "reorder", "renumber", "resetstock", "reset");
      }
      if (args.length == 3 && this.isMifronRootCommand(command) && "shelfshop".equalsIgnoreCase(args[0])
         && ("configure".equalsIgnoreCase(args[1]) || "set".equalsIgnoreCase(args[1]))) {
         return List.of("sell", "buy", "both");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "slotwand".equalsIgnoreCase(args[0])) {
         return List.of("easy", "normal", "hard", "expert");
      }
      if ((args.length == 2 || args.length == 3) && this.isMifronRootCommand(command) && "jumppadwand".equalsIgnoreCase(args[0])) {
         return List.of("1", "5", "10", "25", "50", "75", "100");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "tp".equalsIgnoreCase(args[0])) {
         ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
         List<String> values = new ArrayList<>();
         if (servers != null) values.addAll(servers.getKeys(false));
         values.addAll(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
         return values;
      }
      if (args.length == 3 && this.isMifronRootCommand(command) && "setserver".equalsIgnoreCase(args[0])) {
         return this.serverIconSuggestions(args[2]);
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "gamerules".equalsIgnoreCase(args[0])) {
         return Bukkit.getWorlds().stream().map(WorldInfo::getName).toList();
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "regen".equalsIgnoreCase(args[0])) {
         return List.of("allow", "deny", "list", "0", "1", "2", "4", "force");
      }
      if (args.length == 3 && this.isMifronRootCommand(command) && "regen".equalsIgnoreCase(args[0]) && "force".equalsIgnoreCase(args[1])) {
         return List.of("0", "1", "2", "4");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "status".equalsIgnoreCase(args[0])) {
         return List.of("reset");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "minigame".equalsIgnoreCase(args[0])) {
         return List.of("play", "win", "unlock");
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "athletic".equalsIgnoreCase(args[0])) {
         return this.athleticManager.tabComplete(args);
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && "quest".equalsIgnoreCase(args[0])) {
         return List.of("progress", "propose", "cancel");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && ("merchant".equalsIgnoreCase(args[0]) || "marchant".equalsIgnoreCase(args[0]))) {
         return List.of("spawn", "reroll", "clear");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && ("mp".equalsIgnoreCase(args[0]) || "em".equalsIgnoreCase(args[0]) || "emerald".equalsIgnoreCase(args[0]))) {
         return List.of("give");
      }
      if (args.length == 2 && this.isMifronRootCommand(command) && ("delserver".equalsIgnoreCase(args[0]) || "removeserver".equalsIgnoreCase(args[0]))) {
         ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
         return servers == null ? List.of() : new ArrayList<>(servers.getKeys(false));
      }
      if (args.length == 1 && "friend".equalsIgnoreCase(command.getName())) {
         return List.of("add", "accept", "remove", "chat");
      }
      return args.length == 2 && "status".equalsIgnoreCase(command.getName()) ? List.of("reset") : Collections.emptyList();
   }

   protected boolean isMifronRootCommand(Command command) {
      return "mifron".equalsIgnoreCase(command.getName()) || "mf".equalsIgnoreCase(command.getName());
   }
}
