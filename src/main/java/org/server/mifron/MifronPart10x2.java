package org.server.mifron;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

abstract class MifronPart10x2 extends MifronPart10x1 {
   protected void openFriendUi(Player player) {
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text("\u00a73Mifron Friends"));
      String filter = this.friendSearchFilters.getOrDefault(player.getUniqueId(), "");
      inventory.setItem(0, this.mifron().actionItem(Material.OAK_SIGN, "\u00a7e\u691c\u7d22\u30d0\u30fc",
         List.of("\u00a77" + (filter.isBlank() ? "\u30af\u30ea\u30c3\u30af\u3057\u3066\u691c\u7d22\u8a9e\u3092\u5165\u529b" : "\u691c\u7d22\u4e2d: " + filter), "\u00a77\u7a7a\u5165\u529b\u3067\u691c\u7d22\u89e3\u9664"), "friend_search", null));
      this.fillFriendTopTabs(inventory);
      this.mifron().fillFriendRows(player, inventory, filter);
      this.mifron().fillNotificationOrChatBox(player, inventory);
      this.mifron().fillStatusBox(player, inventory);
      this.mifron().fillEmptyGuiSlots(inventory);
      if (this.bedrockUiFeature != null && this.bedrockUiFeature.showMenu(player, "Mifron Friends / Status", inventory,
            item -> this.mifron().getUiAction(item) != null, (slot, item) -> this.mifron().handleFriendUiClick(player, item))) return;
      player.openInventory(inventory);
   }
   protected void fillFriendTopTabs(Inventory inventory) {
      inventory.setItem(1, this.mifron().actionItem(Material.BOOK, "\u00a7d\u9032\u6357", List.of("\u00a77\u9032\u6357\u4e00\u89a7"), "status_tab_progress", null));
      inventory.setItem(2, this.mifron().actionItem(Material.NETHER_STAR, "\u00a7d\u8ee2\u751f", List.of("\u00a77\u8ee2\u751f\u56de\u6570\u3001\u6761\u4ef6\u3001\u8ee2\u751f\u30dc\u30fc\u30ca\u30b9"), "status_tab_reincarnation", null));
      inventory.setItem(3, this.mifron().actionItem(Material.NAME_TAG, "\u00a76\u79f0\u53f7", List.of("\u00a77\u79f0\u53f7\u4e00\u89a7\u3068\u9078\u629e"), "status_tab_titles", null));
      inventory.setItem(4, this.mifron().actionItem(Material.ZOMBIE_SPAWN_EGG, "\u00a7c\u8a0e\u4f10", List.of("\u00a77Mob\u8a0e\u4f10\u72b6\u6cc1"), "status_tab_kills", null));
   }
}
