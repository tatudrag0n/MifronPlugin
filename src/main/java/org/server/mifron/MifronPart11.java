package org.server.mifron;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart11 extends MifronPart10x2 {
   protected void fillFriendRows(Player player, Inventory inventory, String filter) {
      Set<UUID> friends = this.mifron().getUuidSet(player.getUniqueId(), "friends");
      Set<UUID> listed = new HashSet<>(friends);
      for (Player online : Bukkit.getOnlinePlayers()) if (!online.getUniqueId().equals(player.getUniqueId())) listed.add(online.getUniqueId());
      List<OfflinePlayer> users = listed.stream().map(Bukkit::getOfflinePlayer).filter(user -> this.matchesFriendFilter(user, filter))
         .sorted((a, b) -> a.isOnline() != b.isOnline() ? (a.isOnline() ? -1 : 1) : this.mifron().safePlayerName(a).compareToIgnoreCase(this.mifron().safePlayerName(b))).toList();
      int row = 1;
      for (OfflinePlayer user : users) {
         if (row > 3) break;
         boolean friend = friends.contains(user.getUniqueId());
         boolean online = user.isOnline();
         int base = row * 9;
         inventory.setItem(base, this.mifron().actionItem(Material.PLAYER_HEAD, "\u00a7f" + this.mifron().safePlayerName(user), List.of("\u00a77\u30d7\u30ec\u30a4\u30e4\u30fc"), "friend_profile", user.getUniqueId().toString()));
         inventory.setItem(base + 2, this.mifron().actionItem(online ? Material.LIME_DYE : Material.GRAY_DYE, online ? "\u00a7a\u30aa\u30f3\u30e9\u30a4\u30f3" : "\u00a78\u30aa\u30d5\u30e9\u30a4\u30f3", List.of(), "friend_profile", user.getUniqueId().toString()));
         inventory.setItem(base + 3, this.mifron().actionItem(friend ? Material.RED_DYE : Material.EMERALD, friend ? "\u00a7c\u30d5\u30ec\u30f3\u30c9\u89e3\u9664" : "\u00a7a\u30d5\u30ec\u30f3\u30c9\u7533\u8acb", List.of(), friend ? "friend_remove" : "friend_request", user.getUniqueId().toString()));
         inventory.setItem(base + 4, this.mifron().actionItem(Material.WRITABLE_BOOK, "\u00a7b\u30c1\u30e3\u30c3\u30c8", List.of(), friend ? "friend_chat_open" : "friend_request", user.getUniqueId().toString()));
         row++;
      }
      if (row == 1) inventory.setItem(9, this.mifron().named(Material.GRAY_STAINED_GLASS_PANE, "\u00a77\u8a72\u5f53\u30d7\u30ec\u30a4\u30e4\u30fc\u306a\u3057", List.of()));
   }

   protected boolean matchesFriendFilter(OfflinePlayer player, String filter) {
      return filter == null || filter.isBlank() || this.mifron().safePlayerName(player).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
   }

   protected void fillChatBoxOnly(Player player, Inventory inventory) {
      UUID chatTarget = this.friendChatService.activeTarget(player.getUniqueId());
      if (chatTarget == null) return;
      String draft = this.friendChatService.draft(player.getUniqueId());
      inventory.setItem(7, this.mifron().actionItem(Material.WRITABLE_BOOK, "\u00a7e\u672c\u6587\u5165\u529b", List.of("\u00a77" + (draft.isBlank() ? "\u672a\u5165\u529b" : draft)), "friend_chat_input", chatTarget.toString()));
      inventory.setItem(8, this.mifron().actionItem(Material.LIME_CONCRETE, "\u00a7a\u9001\u4fe1", List.of(), "friend_chat_send", chatTarget.toString()));
      inventory.setItem(15, this.mifron().actionItem(Material.BARRIER, "\u00a7c\u30c1\u30e3\u30c3\u30c8\u6b04\u3092\u9589\u3058\u308b", List.of(), "friend_chat_close", chatTarget.toString()));
   }

   protected void fillStatusBox(Player player, Inventory inventory) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      inventory.setItem(
         36,
         this.mifron().actionItem(
            Material.EXPERIENCE_BOTTLE,
            "\u00a7bMFL",
            List.of("\u00a77" + this.mifron().getMfl(player.getUniqueId()) + " \u00a78(\u30e9\u30f3\u30af " + this.mifron().getMflRank(player.getUniqueId()) + ")"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         37,
         this.mifron().actionItem(
            Material.EMERALD,
            "\u00a7a\u6240\u6301MP",
            List.of("\u00a77" + this.mifron().formatNumber(this.mifron().getEmeralds(player.getUniqueId())) + "MP"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         38,
         this.mifron().actionItem(
            Material.EMERALD_BLOCK,
            "\u00a7a\u7dcf\u7372\u5f97MP",
            List.of("\u00a77" + this.mifron().formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         39,
         this.mifron().actionItem(
            Material.CLOCK,
            "\u00a7e\u7dcf\u30d7\u30ec\u30a4\u6642\u9593",
            List.of("\u00a77" + this.mifron().formatPlayTime(section.getInt("total-minutes", 0))),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         40,
         this.mifron().actionItem(
            Material.IRON_PICKAXE,
            "\u00a76\u63a1\u6398\u30d6\u30ed\u30c3\u30af",
            List.of("\u00a77" + this.mifron().formatNumber(section.getInt("total-blocks-broken", 0)) + " \u500b"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         41,
         this.mifron().actionItem(
            Material.BRICKS,
            "\u00a76\u8a2d\u7f6e\u30d6\u30ed\u30c3\u30af",
            List.of("\u00a77" + this.mifron().formatNumber(section.getInt("total-blocks-placed", 0)) + " \u500b"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         42,
         this.mifron().actionItem(
            Material.IRON_SWORD,
            "\u00a7c\u7dcf\u30e2\u30d6\u8a0e\u4f10\u6570",
            List.of("\u00a77" + this.mifron().formatNumber(section.getInt("total-mob-kills", 0)) + " \u4f53"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         43,
         this.mifron().actionItem(
            Material.SKELETON_SKULL,
            "\u00a7c\u8a0e\u4f10\u6e08\u307fMob",
            List.of("\u00a77" + section.getStringList("killed-mobs").size() + " \u7a2e\u985e"),
            "friend_status_detail",
            null
         )
      );
      inventory.setItem(
         44,
         this.mifron().actionItem(
            Material.WRITABLE_BOOK,
            "\u00a7d\u9054\u6210\u9032\u6357",
            List.of("\u00a77" + this.mifron().countCompletedAdvancements(player) + " / " + this.mifron().countTrackableAdvancements()),
            "friend_status_detail",
            null
         )
      );
   }

   protected void openDetailedStatusUi(Player player) { this.mifron().openStatusUi(player, "progress:0"); }
}
