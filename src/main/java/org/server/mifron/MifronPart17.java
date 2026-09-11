package org.server.mifron;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart17 extends MifronPart16 {
   protected boolean handleMifronCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         sender.sendMessage("\u00a7e/mf check|list|tp|balance|pay|quest|status|tutorial|athletic|minigame|ffa|build|proposal");
         return true;
      }
      if (!(sender instanceof Player player) && !List.of("warning", "mp", "em", "emerald", "regen", "reload", "info", "list", "gamerules", "text", "ffa", "structure", "proposal", "shelfshop").contains(args[0].toLowerCase(Locale.ROOT))) {
         sender.sendMessage("Player only.");
         return true;
      }
      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "check" -> { if (this.mifron().hasPermission(sender, "mifron.command.chunk")) this.mifron().handleChunkCommand((Player) sender); }
         case "list" -> this.mifron().handleListCommand(sender);
         case "tp" -> this.mifron().handleWorldTpCommand((Player) sender, args);
         case "text" -> { if (this.denyUnlessAdmin(sender)) return true; this.textDisplayFeature.handleCommand(sender, args); }
         case "ffa" -> this.ffaManager.handleCommand(sender, args);
         case "structure" -> { if (this.denyUnlessAdmin(sender)) return true; this.structureManager.handleCommand(sender, args); }
         case "build" -> this.buildWorldManager.handleCommand(sender, args);
         case "proposal" -> this.proposalManager.handleCommand(sender, args);
         case "gamerules" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleGamerulesCommand(sender, args); }
         case "info" -> this.mifron().handleInfoCommand(sender);
         case "reload" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleReloadCommand(sender); }
         case "kit" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().giveInitialItems((Player) sender); sender.sendMessage("\u00a7a\u521d\u671f\u914d\u5e03\u7269\u3092\u78ba\u8a8d\u3057\u307e\u3057\u305f\u3002"); }
         case "balance" -> sender.sendMessage("\u00a7a\u6240\u6301MP: " + this.mifron().formatNumber(this.mifron().getEmeralds(((Player) sender).getUniqueId())));
         case "pay" -> this.mifron().handlePayCommand((Player) sender, args);
         case "merchant", "marchant" -> this.mifron().handleMerchantCommand((Player) sender, args);
         case "minigame" -> this.mifron().handleMinigameCommand((Player) sender, args);
         case "athletic" -> { if (!this.athleticManager.handleCommand((Player) sender, args)) this.mifron().handleAthleticCommand((Player) sender, args); }
         case "quest" -> this.mifron().handleQuestCommand(sender, args);
         case "mp", "em", "emerald" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleEmeraldCommand(sender, args); }
         case "regen" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleRegenCommand(sender, args); }
         case "chunk" -> { if (this.mifron().hasPermission(sender, "mifron.command.chunk")) this.mifron().handleChunkCommand((Player) sender); }
         case "status" -> { if (this.mifron().hasPermission(sender, "mifron.command.status")) this.mifron().handleMifronStatusCommand((Player) sender, args); }
         case "tutorial" -> this.mifron().handleTutorialCommand(sender);
         case "shelfshop" -> this.handleShelfShopCommand(sender, args);
         case "shopwand" -> this.giveTypedWand(sender, args.length < 2 ? this.createShopWand() : this.createShopWand(ShopWandType.fromKey(args[1])), "\u00a7a\u30b7\u30e7\u30c3\u30d7\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         case "jumppadwand" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            int verticalPower = args.length >= 2 ? this.mifron().parsePositiveInt(args[1], 5) : 5;
            int horizontalPower = args.length >= 3 ? this.mifron().parsePositiveInt(args[2], verticalPower) : verticalPower;
            this.giveTypedWand(sender, this.createJumpPadWand(verticalPower, horizontalPower), "\u00a7a\u30b8\u30e3\u30f3\u30d7\u30d1\u30c3\u30c9\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         }
         case "slotwand" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            ShopWandType type = args.length < 2 ? ShopWandType.SLOT_NORMAL : ShopWandType.fromKey(args[1]);
            this.giveTypedWand(sender, type == null ? this.createShopWand(ShopWandType.SLOT_NORMAL) : this.createShopWand(type), "\u00a7a\u30b9\u30ed\u30c3\u30c8\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         }
         case "serverwand" -> this.giveTypedWand(sender, this.serverPortalFeature.createServerWand(), "\u00a7a\u30b5\u30fc\u30d0\u30fc\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         case "sethub" -> { if (this.isAdminOp(sender)) { this.writeLocation("hub", ((Player) sender).getLocation()); sender.sendMessage("\u00a7a\u4e2d\u592e\u5e83\u5834\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002"); } }
         case "setserver" -> {
            if (this.denyUnlessAdmin(sender) || args.length < 2 || !this.isSafeConfigKey(args[1])) break;
            this.writeLocation("servers." + args[1], ((Player) sender).getLocation());
            if (args.length >= 3) {
               Material icon = this.parseServerIcon(sender, args[2]);
               if (icon != null) { this.getConfig().set("servers." + args[1] + ".icon", icon.name().toLowerCase(Locale.ROOT)); this.saveConfig(); }
            }
            sender.sendMessage("\u00a7a\u30b5\u30fc\u30d0\u30fc\u79fb\u52d5\u5148\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f: " + args[1]);
         }
         case "serverorder", "servermove" -> this.handleServerOrderCommand(sender, args);
         case "servericon", "setservericon" -> this.handleServerIconCommand(sender, args);
         case "delserver", "removeserver" -> {
            if (this.denyUnlessAdmin(sender) || args.length < 2 || !this.isSafeConfigKey(args[1])) break;
            String key = args[1];
            this.getConfig().set("servers." + key, null);
            java.util.List<String> removed = new java.util.ArrayList<>(this.getConfig().getStringList("deleted-servers"));
            if (!removed.contains(key)) removed.add(key);
            this.getConfig().set("deleted-servers", removed);
            this.saveConfig();
            sender.sendMessage("\u00a7a\u30b5\u30fc\u30d0\u30fc\u79fb\u52d5\u5148\u3092\u524a\u9664\u3057\u307e\u3057\u305f: " + key);
         }
         case "warning" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleWarningCommand(sender, args); }
         default -> sender.sendMessage("\u00a7e/mf check|list|tp|balance|pay|quest|status|tutorial");
      }
      return true;
   }

   private boolean isAdminOp(CommandSender sender) {
      return sender != null && (sender.isOp() || sender.hasPermission("mifron.admin"));
   }

   private boolean denyUnlessAdmin(CommandSender sender) {
      if (this.isAdminOp(sender)) return false;
      sender.sendMessage("\u00a7cOP\u6a29\u9650\u304c\u5fc5\u8981\u3067\u3059\u3002");
      return true;
   }

   private void giveTypedWand(CommandSender sender, ItemStack wand, String message) {
      if (!(sender instanceof Player player)) return;
      if (!this.isAdminOp(sender) && !sender.hasPermission("mifron.shop.admin")) { sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (wand == null) { sender.sendMessage("\u00a7c\u30ef\u30f3\u30c9\u3092\u4f5c\u6210\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f\u3002"); return; }
      Map leftovers = player.getInventory().addItem(wand);
      sender.sendMessage(leftovers.isEmpty() ? message : "\u00a7c\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
   }
}
