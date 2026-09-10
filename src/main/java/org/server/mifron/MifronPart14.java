package org.server.mifron;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart14 extends MifronPart13x1 {
   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player) || !"\u00a76Mifron Merchant".equals(this.inventoryTitle(event.getView().title()))) return;
      this.activeMerchantPages.remove(player.getUniqueId());
      UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
      if (merchantId == null || this.activeMerchantViews.containsValue(merchantId)) return;
      Entity entity = this.findEntity(merchantId);
      if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) { villager.setAI(true); villager.setInvulnerable(false); }
   }

   protected String inventoryTitle(Component title) { return PlainTextComponentSerializer.plainText().serialize(title); }

   protected Entity findEntity(UUID entityId) {
      for (World world : Bukkit.getWorlds()) {
         Entity entity = world.getEntity(entityId);
         if (entity != null) return entity;
      }
      return null;
   }

   protected void handleFriendUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      UUID targetId = this.getUiTarget(clicked);
      switch (action) {
         case "friend_search" -> { this.pendingFriendSearch.put(player.getUniqueId(), ""); player.closeInventory(); player.sendMessage("\u00a7e\u691c\u7d22\u3059\u308b\u30e6\u30fc\u30b6\u30fc\u540d\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); }
         case "friend_request" -> { if (targetId != null) { this.sendFriendRequest(player, Bukkit.getOfflinePlayer(targetId)); this.openFriendUi(player); } }
         case "friend_accept" -> { if (targetId != null) { this.acceptFriendRequest(player, Bukkit.getOfflinePlayer(targetId)); this.openFriendUi(player); } }
         case "friend_remove" -> { if (targetId != null) { this.removeFriend(player, Bukkit.getOfflinePlayer(targetId)); this.openFriendUi(player); } }
         case "friend_chat_open" -> { if (targetId != null) { this.activeFriendChatTarget.put(player.getUniqueId(), targetId); this.openFriendUi(player); } }
         case "friend_chat_close" -> { this.activeFriendChatTarget.remove(player.getUniqueId()); this.friendChatDrafts.remove(player.getUniqueId()); this.openFriendUi(player); }
         case "friend_status_detail" -> this.openDetailedStatusUi(player);
         default -> {}
      }
   }

   protected void handleStatusUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "friend_status_back" -> this.openFriendUi(player);
         case "status_tab_progress" -> this.openStatusUi(player, "progress:0");
         case "status_tab_reincarnation" -> this.openStatusUi(player, "reincarnation");
         case "status_tab_titles" -> this.openStatusUi(player, "titles:0");
         case "status_tab_kills" -> this.openStatusUi(player, "kills:0");
         case "progress_page" -> this.openStatusUi(player, "progress:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "titles_page" -> this.openStatusUi(player, "titles:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "kills_page" -> this.openStatusUi(player, "kills:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "reincarnate_now" -> { this.tryReincarnate(player, null); this.openStatusUi(player, "reincarnation"); }
         case "select_title" -> {
            String title = this.getUiTargetString(clicked);
            if (title != null && this.canUseTitle(player, title)) {
               this.getPlayerSection(player.getUniqueId()).set("selected-title", title);
               this.queueDataSave();
               this.refreshPlayerName(player);
               this.openStatusUi(player, "titles:0");
            }
         }
         case "clear_title" -> {
            this.getPlayerSection(player.getUniqueId()).set("selected-title", null);
            this.queueDataSave();
            this.refreshPlayerName(player);
            this.openStatusUi(player, "titles:0");
         }
         default -> {}
      }
   }

   protected void handleQuestUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "quest_close" -> player.closeInventory();
         case "quest_home" -> this.openQuestUi(player, "categories");
         case "quest_category" -> this.openQuestUi(player, this.getUiTargetString(clicked));
         case "quest_claim" -> {
            String questId = this.getUiTargetString(clicked);
            if (questId != null && this.questService.claim(player, questId)) {
               QuestDefinition definition = this.questService.definition(questId);
               this.openQuestUi(player, definition == null ? "categories" : definition.type().key());
            }
         }
         default -> {}
      }
   }

   BedrockUiFeature bedrockUiFeature() { return this.bedrockUiFeature; }

   void teleportToConfigLocation(Player player, String path) {
      Location location = this.readLocation(path);
      if (location == null) player.sendMessage("\u00a7c\u79fb\u52d5\u5148\u304c\u672a\u8a2d\u5b9a\u3067\u3059: " + path);
      else { player.teleport(location); this.playTeleportSound(player); player.sendMessage("\u00a7a\u79fb\u52d5\u3057\u307e\u3057\u305f\u3002"); }
   }
}
