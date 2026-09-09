package org.server.mifron;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart18x1 extends MifronPart18 {
   protected void handleMerchantCommand(Player player, String[] args) {
      if (!player.hasPermission("mifron.admin")) { player.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (args.length < 2) { player.sendMessage("\u00a7c/mifron merchant spawn|reroll|clear"); return; }
      switch (args[1].toLowerCase(Locale.ROOT)) {
         case "spawn" -> {
            if (this.spawnMerchant(player.getLocation())) player.sendMessage("\u00a7aMifron\u5546\u4eba\u3092\u30b9\u30dd\u30fc\u30f3\u3057\u307e\u3057\u305f\u3002");
            else player.sendMessage("\u00a7c\u4e2d\u592e\u5e83\u5834\u306b\u306fMifron\u5546\u4eba\u3092\u30b9\u30dd\u30fc\u30f3\u3067\u304d\u307e\u305b\u3093\u3002");
         }
         case "reroll" -> {
            int count = 0;
            for (World world : Bukkit.getWorlds()) {
               for (Entity entity : world.getEntities()) {
                  if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
                     this.rerollMerchant(villager);
                     entity.getPersistentDataContainer().set(this.merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
                     entity.getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
                     count++;
                  }
               }
            }
            player.sendMessage("\u00a7a\u5546\u4eba\u306e\u8ca9\u58f2\u54c1\u3092\u518d\u62bd\u9078\u3057\u307e\u3057\u305f: " + count + "\u4f53");
         }
         case "clear" -> {
            int cleared = 0;
            for (World world : Bukkit.getWorlds()) {
               for (Entity entity : world.getEntities()) {
                  if (this.isMifronMerchant(entity)) { entity.remove(); cleared++; }
               }
            }
            player.sendMessage("\u00a7aMifron\u5546\u4eba\u3092\u524a\u9664\u3057\u307e\u3057\u305f: " + cleared + "\u4f53");
         }
         default -> player.sendMessage("\u00a7c/mifron merchant spawn|reroll|clear");
      }
   }

   protected void handleWarningCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) { sender.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (args.length < 3) { sender.sendMessage("\u00a7c/mifron warning <player> <0-4>"); return; }
      OfflinePlayer target = this.resolveKnownPlayer(sender, args[1]);
      if (target == null) return;
      int level = Math.min(4, this.parsePositiveInt(args[2], 0));
      this.getPlayerSection(target.getUniqueId()).set("warning-level", level);
      this.saveData();
      sender.sendMessage("\u00a7a" + this.safePlayerName(target) + " \u306e\u8b66\u6212\u5024\u3092 " + level + " \u306b\u3057\u307e\u3057\u305f\u3002");
      if (target.isOnline()) this.routeByWarningLevel(target.getPlayer());
   }

   protected void sendFriendRequest(Player player, OfflinePlayer target) {
      if (target.getUniqueId().equals(player.getUniqueId())) { player.sendMessage("\u00a7c\u81ea\u5206\u306b\u306f\u7533\u8acb\u3067\u304d\u307e\u305b\u3093\u3002"); return; }
      if (this.getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) { player.sendMessage("\u00a7e\u3059\u3067\u306b\u30d5\u30ec\u30f3\u30c9\u3067\u3059\u3002"); return; }
      Set<UUID> requests = this.getUuidSet(target.getUniqueId(), "requests");
      if (requests.size() >= 100) { player.sendMessage("\u00a7c\u76f8\u624b\u306e\u30d5\u30ec\u30f3\u30c9\u7533\u8acb\u304c\u4e0a\u9650\u306b\u9054\u3057\u3066\u3044\u307e\u3059\u3002"); return; }
      if (!requests.add(player.getUniqueId())) { player.sendMessage("\u00a7e\u3059\u3067\u306b\u7533\u8acb\u6e08\u307f\u3067\u3059\u3002"); return; }
      this.setUuidSet(target.getUniqueId(), "requests", requests);
      player.sendMessage("\u00a7a\u30d5\u30ec\u30f3\u30c9\u7533\u8acb\u3092\u9001\u4fe1\u3057\u307e\u3057\u305f: " + this.safePlayerName(target));
      if (target.isOnline()) target.getPlayer().sendMessage("\u00a7e" + player.getName() + " \u304b\u3089\u30d5\u30ec\u30f3\u30c9\u7533\u8acb\u304c\u5c4a\u304d\u307e\u3057\u305f\u3002");
   }

   protected void acceptFriendRequest(Player player, OfflinePlayer requester) {
      Set<UUID> requests = this.getUuidSet(player.getUniqueId(), "requests");
      if (!requests.remove(requester.getUniqueId())) { player.sendMessage("\u00a7c\u7533\u8acb\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002"); return; }
      this.setUuidSet(player.getUniqueId(), "requests", requests);
      Set<UUID> playerFriends = this.getUuidSet(player.getUniqueId(), "friends");
      Set<UUID> requesterFriends = this.getUuidSet(requester.getUniqueId(), "friends");
      playerFriends.add(requester.getUniqueId());
      requesterFriends.add(player.getUniqueId());
      this.setUuidSet(player.getUniqueId(), "friends", playerFriends);
      this.setUuidSet(requester.getUniqueId(), "friends", requesterFriends);
      player.sendMessage("\u00a7a" + this.safePlayerName(requester) + " \u3068\u30d5\u30ec\u30f3\u30c9\u306b\u306a\u308a\u307e\u3057\u305f\u3002");
      if (requester.isOnline()) requester.getPlayer().sendMessage("\u00a7a" + player.getName() + " \u304c\u30d5\u30ec\u30f3\u30c9\u7533\u8acb\u3092\u627f\u8a8d\u3057\u307e\u3057\u305f\u3002");
   }

   protected void removeFriend(Player player, OfflinePlayer target) {
      Set<UUID> playerFriends = this.getUuidSet(player.getUniqueId(), "friends");
      Set<UUID> targetFriends = this.getUuidSet(target.getUniqueId(), "friends");
      playerFriends.remove(target.getUniqueId());
      targetFriends.remove(player.getUniqueId());
      this.setUuidSet(player.getUniqueId(), "friends", playerFriends);
      this.setUuidSet(target.getUniqueId(), "friends", targetFriends);
      player.sendMessage("\u00a7a\u30d5\u30ec\u30f3\u30c9\u3092\u89e3\u9664\u3057\u307e\u3057\u305f: " + this.safePlayerName(target));
   }

   protected void sendFriendChatDraft(Player player, OfflinePlayer target) {
      String message = this.friendChatDrafts.getOrDefault(player.getUniqueId(), "").trim();
      if (message.isBlank()) { player.sendMessage("\u00a7c\u672c\u6587\u304c\u672a\u5165\u529b\u3067\u3059\u3002"); return; }
      this.sendFriendChat(player, target, message);
      this.friendChatDrafts.remove(player.getUniqueId());
   }

   protected void sendFriendChat(Player player, OfflinePlayer target, String message) {
      message = this.sanitizeTextInput(message, 256);
      if (message.isBlank()) { player.sendMessage("\u00a7c\u672c\u6587\u304c\u672a\u5165\u529b\u3067\u3059\u3002"); return; }
      if (!this.getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) { player.sendMessage("\u00a7c\u30d5\u30ec\u30f3\u30c9\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      player.sendMessage("\u00a7b[Friend -> " + this.safePlayerName(target) + "] " + message);
      if (target.isOnline()) {
         target.getPlayer().sendMessage("\u00a7b[Friend <- " + player.getName() + "] " + message);
         return;
      }
      List<String> notifications = this.getPlayerSection(target.getUniqueId()).getStringList("offline-messages");
      notifications.add(this.safePlayerName(player) + ": " + message);
      while (notifications.size() > 50) notifications.remove(0);
      this.getPlayerSection(target.getUniqueId()).set("offline-messages", notifications);
      this.saveData();
   }
}
