package org.server.mifron;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart17 extends MifronPart16 {
   protected boolean handleMifronCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         sender.sendMessage("\u00a7e/mf check|list|balance|pay|quest|status|tutorial|build|proposal|vote");
         return true;
      }
      if (!(sender instanceof Player player) && !List.of("warning", "mp", "em", "emerald", "regen", "reload", "info", "list", "gamerules", "text", "structure", "proposal", "shelfshop", "makeelite", "shelfshop").contains(args[0].toLowerCase(Locale.ROOT))) {
         sender.sendMessage("Player only.");
         return true;
      }
      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "check" -> { if (this.mifron().hasPermission(sender, "mifron.command.chunk")) this.mifron().handleChunkCommand((Player) sender); }
         case "list" -> this.mifron().handleListCommand(sender);
         case "text" -> { if (this.denyUnlessAdmin(sender)) return true; this.textDisplayFeature.handleCommand(sender, args); }
         case "structure" -> { if (this.denyUnlessAdmin(sender)) return true; this.structureManager.handleCommand(sender, args); }
         case "build" -> this.buildWorldManager.handleCommand(sender, args);
         case "proposal" -> this.proposalManager.handleCommand(sender, args);
         case "vote" -> {
            if (sender instanceof Player voter) this.openProposalUi(voter, 0);
            else sender.sendMessage("Player only.");
         }
         case "gamerules" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleGamerulesCommand(sender, args); }
         case "info" -> this.mifron().handleInfoCommand(sender);
         case "reload" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleReloadCommand(sender); }
         case "kit" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().giveInitialItems((Player) sender); sender.sendMessage("\u00a7a\u521d\u671f\u914d\u5e03\u7269\u3092\u78ba\u8a8d\u3057\u307e\u3057\u305f\u3002"); }
         case "balance" -> sender.sendMessage("\u00a7a\u6240\u6301MP: " + this.mifron().formatNumber(this.mifron().getEmeralds(((Player) sender).getUniqueId())));
         case "pay" -> this.mifron().handlePayCommand((Player) sender, args);
         case "merchant", "marchant" -> this.mifron().handleMerchantCommand((Player) sender, args);
         case "quest" -> this.mifron().handleQuestCommand(sender, args);
         case "mp", "em", "emerald" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleEmeraldCommand(sender, args); }
         case "regen" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleRegenCommand(sender, args); }
         case "chunk" -> { if (this.mifron().hasPermission(sender, "mifron.command.chunk")) this.mifron().handleChunkCommand((Player) sender); }
         case "protect" -> this.chunkProtectionFeature.handleProtectCommand(sender, args);
         case "chunkprotect" -> this.chunkProtectionFeature.handleChunkProtectCommand(sender, args);
         case "menu" -> { if (sender instanceof Player menuPlayer) this.utilityItemsFeature.openMenuUi(menuPlayer); }
         case "main" -> this.mifron().handleMainCommand(sender, args);
         case "debugopen" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            if (!(sender instanceof Player debugPlayer)) return true;
            String debugWhich = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "quest";
            if ("status".equals(debugWhich)) this.mifron().openFriendUi(debugPlayer);
            else this.mifron().openQuestUi(debugPlayer, "categories");
         }
         case "status" -> { if (this.mifron().hasPermission(sender, "mifron.command.status")) this.mifron().handleMifronStatusCommand((Player) sender, args); }
         case "tutorial" -> this.mifron().handleTutorialCommand(sender, args);
         case "job" -> this.mifron().handleJobCommand(sender, args);
         case "shelfshop" -> this.handleShelfShopCommand(sender, args);
         case "shopwand" -> this.giveTypedWand(sender, args.length < 2 ? this.createShopWand() : this.createShopWand(ShopWandType.fromKey(args[1])), "\u00a7a\u30b7\u30e7\u30c3\u30d7\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         case "jumppadwand" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            int verticalPower = args.length >= 2 ? this.mifron().parsePositiveInt(args[1], 5) : 5;
            int horizontalPower = args.length >= 3 ? this.mifron().parsePositiveInt(args[2], verticalPower) : verticalPower;
            this.giveTypedWand(sender, this.createJumpPadWand(verticalPower, horizontalPower), "\u00a7a\u30b8\u30e3\u30f3\u30d7\u30d1\u30c3\u30c9\u30ef\u30f3\u30c9\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         }
         case "jumpblock" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            int jumpVertical = args.length >= 2 ? this.mifron().parsePositiveInt(args[1], 10) : 10;
            int jumpHorizontal = args.length >= 3 ? this.mifron().parsePositiveInt(args[2], jumpVertical) : jumpVertical;
            this.giveTypedWand(sender, this.utilityItemsFeature.createJumpBlock(jumpVertical, jumpHorizontal), "\u00a7a\u30b8\u30e3\u30f3\u30d7\u30d6\u30ed\u30c3\u30af\u3092\u5165\u624b\u3057\u307e\u3057\u305f\u3002");
         }
         case "makeelite" -> {
            if (this.denyUnlessAdmin(sender)) return true;
            this.handleMakeEliteCommand(sender, args);
         }
         case "sethub" -> { if (this.isAdminOp(sender)) { this.writeLocation("hub", ((Player) sender).getLocation()); sender.sendMessage("\u00a7a\u4e2d\u592e\u5e83\u5834\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002"); } }
         case "warning" -> { if (this.denyUnlessAdmin(sender)) return true; this.mifron().handleWarningCommand(sender, args); }
         default -> sender.sendMessage("\u00a7e/mf check|list|balance|pay|quest|status|tutorial");
      }
      return true;
   }

    private boolean isAdminOp(CommandSender sender) {
      return sender != null && (sender.isOp() || sender.hasPermission("mifron.admin"));
   }

   protected boolean handleMakeEliteCommand(CommandSender sender, String[] args) {
      if (args.length < 4) {
         sender.sendMessage("§e/mf makeelite <x> <y> <z> [world] [radius]");
         return true;
      }
      int x;
      int y;
      int z;
      try {
         x = Integer.parseInt(args[1]);
         y = Integer.parseInt(args[2]);
         z = Integer.parseInt(args[3]);
      } catch (NumberFormatException e) {
         sender.sendMessage("§c座標は整数で指定してください。");
         return true;
      }
      World world;
      int arg = 4;
      if (args.length > arg && !args[arg].matches("-?\\d+(\\.\\d+)?")) {
         world = Bukkit.getWorld(args[arg++]);
         if (world == null) {
            sender.sendMessage("§cワールドが見つかりません: " + args[4]);
            return true;
         }
      } else if (sender instanceof Player player) {
         world = player.getWorld();
      } else {
         sender.sendMessage("§e/mf makeelite <x> <y> <z> [world] [radius]");
         return true;
      }
      double radius = 15.0;
      if (args.length > arg) {
         try {
            radius = Math.max(1.0, Math.min(64.0, Double.parseDouble(args[arg])));
         } catch (NumberFormatException e) {
            sender.sendMessage("§c半径は数値で指定してください。");
            return true;
         }
      }
      if (!world.getChunkAt(x >> 4, z >> 4).load(true)) {
         sender.sendMessage("§cチャンクをロードできませんでした。");
         return true;
      }
      if (this.mifron().eliteMobFeature.elitizeNearest(world, x, y, z, radius)) {
         sender.sendMessage("§d最寄りのモブをElite化しました: " + world.getName() + " " + x + "," + y + "," + z);
      } else {
         sender.sendMessage("§c範囲内にElite化できるモブがいません。");
      }
      return true;
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
