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

public class Mifron extends MifronPart18x2 implements Listener, TabExecutor {
   /**
    * Exposes the flat bedrock generator for the main world so
    * {@code bukkit.yml} ({@code worlds.main.generator: Mifron:mifron-flat})
    * regenerates it as structure-free bedrock at Y=-64.
    */
   @Override
   public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
      if ("mifron-flat".equalsIgnoreCase(id)) return new MainFlatGenerator();
      String main = "main";
      try {
         String configured = this.getConfig().getString("main-world.name", "main");
         if (configured != null && !configured.isBlank()) main = configured;
      } catch (IllegalStateException notEnabledYet) {
         // Config unavailable during very early world creation; fall back.
      }
      if (main.equalsIgnoreCase(worldName)) return new MainFlatGenerator();
      return super.getDefaultWorldGenerator(worldName, id);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && this.isMifronRootCommand(command)) {
          List<String> all = new ArrayList<>(List.of(
             "list", "text", "structure", "build", "main", "proposal", "vote", "gamerules",
             "info", "reload", "kit", "balance", "pay", "merchant",
             "quest", "mp", "regen", "chunk", "chunkprotect", "protect", "menu", "status", "tutorial", "guide", "shelfshop", "shopwand",
             "jumppadwand", "jumpblock", "makeelite", "sethub", "warning", "refer", "event", "relic"
          ));
          // Disabled world features are not advertised.
          if (!this.getConfig().getBoolean("build-world.enabled", false)) all.remove("build");
          if (!this.getConfig().getBoolean("main-world.enabled", false)) all.remove("main");
          if (!sender.isOp() && !sender.hasPermission("mifron.admin")) {
             all.removeAll(List.of("reload", "regen", "gamerules", "kit", "shopwand", "jumppadwand", "jumpblock",
                "sethub", "text", "structure", "warning", "mp", "build", "main", "shelfshop", "makeelite",
                "chunkprotect", "proposal", "relic", "event"));
          }
         String prefix = args[0].toLowerCase();
         List<String> matched = new ArrayList<>();
         for (String value : all) if (value.startsWith(prefix)) matched.add(value);
         return matched;
      }
      if (args.length >= 2 && this.isMifronRootCommand(command) && "text".equalsIgnoreCase(args[0])) return this.textDisplayFeature.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "structure".equalsIgnoreCase(args[0])) return this.structureManager.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "build".equalsIgnoreCase(args[0])) return this.buildWorldManager.tabComplete(args);
      if (args.length >= 2 && this.isMifronRootCommand(command) && "proposal".equalsIgnoreCase(args[0])) return this.proposalManager.tabComplete(args);
      if (args.length == 2 && this.isMifronRootCommand(command) && "quest".equalsIgnoreCase(args[0])) return List.of("progress", "propose", "cancel");
      if (args.length == 2 && this.isMifronRootCommand(command) && "tutorial".equalsIgnoreCase(args[0])) return List.of("restart", "skip");
      return Collections.emptyList();
   }

   protected boolean isMifronRootCommand(Command command) {
      return "mifron".equalsIgnoreCase(command.getName()) || "mf".equalsIgnoreCase(command.getName());
   }
}
