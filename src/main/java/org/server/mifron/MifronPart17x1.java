package org.server.mifron;

import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;

abstract class MifronPart17x1 extends MifronPart17 {
   protected boolean handleTutorialCommand(CommandSender sender) {
      if (sender instanceof Player player) {
         this.mifron().startTutorial(player, true);
         return true;
      }
      sender.sendMessage("Player only.");
      return true;
   }

   protected void handleRegenCommand(CommandSender sender, String[] args) {
      this.chunkProtectionFeature.handleRegenCommand(sender, args);
   }

   boolean hasPermission(CommandSender sender, String permission) {
      if (!sender.hasPermission(permission) && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return false;
      }
      return true;
   }

   protected void handleListCommand(CommandSender sender) {
      List<String> worlds = Bukkit.getWorlds().stream().map(WorldInfo::getName).sorted().toList();
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      List<String> serverKeys = servers == null ? Collections.emptyList() : servers.getKeys(false).stream().sorted().toList();
      sender.sendMessage("\u00a7aWorlds: " + String.join(", ", worlds));
      sender.sendMessage("\u00a7aConfigured servers: " + (serverKeys.isEmpty() ? "(none)" : String.join(", ", serverKeys)));
   }

   protected void handleWorldTpCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("\u00a7c/mifron tp <worldKey>");
         return;
      }
      String key = args[1];
      if (this.getConfig().contains("servers." + key)) {
         this.mifron().teleportToConfigLocation(player, "servers." + key);
         return;
      }
      World world = Bukkit.getWorld(key);
      if (world == null) {
         player.sendMessage("\u00a7c\u79fb\u52d5\u5148\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + key);
         return;
      }
      player.teleport(world.getSpawnLocation());
      this.playTeleportSound(player);
      player.sendMessage("\u00a7a" + world.getName() + " \u306e\u30b9\u30dd\u30fc\u30f3\u3078\u79fb\u52d5\u3057\u307e\u3057\u305f\u3002");
   }

   protected void handleGamerulesCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (args.length >= 2) {
         World world = Bukkit.getWorld(args[1]);
         if (world == null) {
            sender.sendMessage("\u00a7c\u30ef\u30fc\u30eb\u30c9\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093: " + args[1]);
            return;
         }
         this.worldRulesFeature.apply(world);
         sender.sendMessage("\u00a7a\u30b2\u30fc\u30e0\u30eb\u30fc\u30eb\u3092\u9069\u7528\u3057\u307e\u3057\u305f: " + world.getName());
         return;
      }
      this.worldRulesFeature.apply();
      sender.sendMessage("\u00a7a\u5168\u30ef\u30fc\u30eb\u30c9\u3078Mifron\u30b2\u30fc\u30e0\u30eb\u30fc\u30eb\u3092\u9069\u7528\u3057\u307e\u3057\u305f\u3002");
   }

   protected void handleInfoCommand(CommandSender sender) {
      sender.sendMessage("\u00a7aMifron " + this.getDescription().getVersion());
      sender.sendMessage("\u00a77Commands: /mifron, /mf");
      sender.sendMessage("\u00a77/mv \u306fMultiverse-Core\u5c02\u7528\u3067\u3059\u3002Mifron\u306f\u767b\u9332\u3057\u307e\u305b\u3093\u3002");
   }

   protected void handleReloadCommand(CommandSender sender) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      this.reloadConfig();
      this.applyMainWorldBorder();
      this.economyPriceTable.load();
      this.questService.load();
      this.loadShopPrices();
      this.applyEconomyPriceTable();
      this.rebuildShelfShopCatalog();
      this.mifron().syncShelfShopDisplays();
      this.structureManager.load();
      this.proposalManager.load();
      this.ffaManager.load();
      sender.sendMessage("\u00a7aMifron\u8a2d\u5b9a\u3001\u4fa1\u683c\u8868\u3001\u30af\u30a8\u30b9\u30c8\u5b9a\u7fa9\u3092\u518d\u8aad\u8fbc\u3057\u307e\u3057\u305f\u3002");
   }

   protected void handleChunkCommand(Player player) {
      this.chunkProtectionFeature.handleChunkCommand(player);
   }

   protected void handleMifronStatusCommand(Player player, String[] args) {
      if (args.length < 2 || !"reset".equalsIgnoreCase(args[1])) {
         ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
         player.sendMessage("\u00a7aMifron\u30b9\u30c6\u30fc\u30bf\u30b9");
         player.sendMessage("\u00a77MFL: " + this.mifron().getMfl(player.getUniqueId()) + " / \u30e9\u30f3\u30af: " + this.getMflRank(player.getUniqueId()));
         player.sendMessage("\u00a77\u6240\u6301MP: " + this.mifron().formatNumber(this.mifron().getEmeralds(player.getUniqueId())) + "MP");
         player.sendMessage("\u00a77\u8ee2\u751f\u30dc\u30fc\u30ca\u30b9: +" + this.mifron().getReincarnationBonus(player.getUniqueId()) + "%");
         player.sendMessage("\u00a77\u7dcf\u30d7\u30ec\u30a4\u6642\u9593: " + this.mifron().formatPlayTime(section.getInt("total-minutes", 0)));
         player.sendMessage("\u00a7e\u30ea\u30bb\u30c3\u30c8: /mifron status reset");
         return;
      }
      if (!player.hasPermission("mifron.admin")) {
         player.sendMessage("\u00a7c\u30b9\u30c6\u30fc\u30bf\u30b9\u30ea\u30bb\u30c3\u30c8\u306f\u7ba1\u7406\u8005\u306e\u307f\u5b9f\u884c\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      this.resetStatusData(player.getUniqueId());
      player.sendMessage("\u00a7a\u81ea\u5206\u306eMifron\u30b9\u30c6\u30fc\u30bf\u30b9\u3092\u30ea\u30bb\u30c3\u30c8\u3057\u307e\u3057\u305f\u3002");
   }

   QuestService getQuestService() {
      return this.questService;
   }
}
