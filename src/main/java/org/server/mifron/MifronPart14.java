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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

abstract class MifronPart14 extends MifronPart13x1 {
   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player) || !"\u00a76Mifron Merchant".equals(this.mifron().inventoryTitle(event.getView().title()))) return;
      this.activeMerchantPages.remove(player.getUniqueId());
      UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
      if (merchantId == null || this.activeMerchantViews.containsValue(merchantId)) return;
      Entity entity = this.mifron().findEntity(merchantId);
      if (entity instanceof AbstractVillager villager && this.mifron().isMifronMerchant(entity)) { villager.setAI(true); villager.setInvulnerable(false); }
   }

   /**
    * A player who quits (or is kicked) with the merchant GUI open never fires
    * InventoryCloseEvent, which would leave the merchant's AI disabled and the
    * view maps leaking. Mirror the close-time restore here.
    */
   @EventHandler
   public void onMerchantViewQuit(PlayerQuitEvent event) {
      if (!(event.getPlayer() instanceof Player player)) return;
      this.activeMerchantPages.remove(player.getUniqueId());
      UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
      if (merchantId == null || this.activeMerchantViews.containsValue(merchantId)) return;
      Entity entity = this.mifron().findEntity(merchantId);
      if (entity instanceof AbstractVillager villager && this.mifron().isMifronMerchant(entity)) { villager.setAI(true); villager.setInvulnerable(false); }
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
      String action = this.mifron().getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      UUID targetId = this.mifron().getUiTarget(clicked);
      switch (action) {
         case "menu_back" -> this.utilityItemsFeature.openMenuUi(player);
         case "friend_request" -> { if (targetId != null) { this.mifron().sendFriendRequest(player, Bukkit.getOfflinePlayer(targetId)); this.mifron().openFriendUi(player); } }
         case "friend_accept" -> { if (targetId != null) { this.mifron().acceptFriendRequest(player, Bukkit.getOfflinePlayer(targetId)); this.mifron().openFriendUi(player); } }
         case "friend_remove" -> { if (targetId != null) { this.mifron().removeFriend(player, Bukkit.getOfflinePlayer(targetId)); this.mifron().openFriendUi(player); } }
         case "friend_chat_open" -> { if (targetId != null) { this.friendChatService.openChat(player.getUniqueId(), targetId); this.mifron().openFriendUi(player); } }
         case "friend_chat_close" -> { this.friendChatService.closeChat(player.getUniqueId()); this.mifron().openFriendUi(player); }
         case "friend_status_detail" -> {
            // The bottom stats box is display-only: jumping to the progress
            // tab from here is surprising, and the top tabs (slots 0-3)
            // already cover that navigation.
         }
         case "status_tab_progress" -> this.mifron().openStatusUi(player, "progress:0");
         case "status_tab_reincarnation" -> this.mifron().openStatusUi(player, "reincarnation");
         case "status_tab_titles" -> this.mifron().openStatusUi(player, "titles:0");
         case "status_tab_kills" -> this.mifron().openStatusUi(player, "kills:0");
         default -> {}
      }
   }

   protected void handleStatusUiClick(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "menu_back" -> this.utilityItemsFeature.openMenuUi(player);
         case "friend_status_back" -> this.mifron().openFriendUi(player);
         case "status_tab_progress" -> this.mifron().openStatusUi(player, "progress:0");
         case "status_tab_reincarnation" -> this.mifron().openStatusUi(player, "reincarnation");
         case "status_tab_titles" -> this.mifron().openStatusUi(player, "titles:0");
         case "status_tab_kills" -> this.mifron().openStatusUi(player, "kills:0");
         case "progress_page" -> this.mifron().openStatusUi(player, "progress:" + this.mifron().parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "titles_page" -> this.mifron().openStatusUi(player, "titles:" + this.mifron().parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "kills_page" -> this.mifron().openStatusUi(player, "kills:" + this.mifron().parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "reincarnate_now" -> { this.mifron().tryReincarnate(player, null); this.mifron().openStatusUi(player, "reincarnation"); }
         case "select_title" -> {
            String title = this.getUiTargetString(clicked);
            if (title != null && this.mifron().canUseTitle(player, title)) {
               this.mifron().getPlayerSection(player.getUniqueId()).set("selected-title", title);
               this.queueDataSave();
               this.mifron().refreshPlayerName(player);
               this.mifron().openStatusUi(player, "titles:0");
            }
         }
         case "clear_title" -> {
            this.mifron().getPlayerSection(player.getUniqueId()).set("selected-title", null);
            this.queueDataSave();
            this.mifron().refreshPlayerName(player);
            this.mifron().openStatusUi(player, "titles:0");
         }
         default -> {}
      }
   }

   protected void handleQuestUiClick(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "menu_back" -> this.utilityItemsFeature.openMenuUi(player);
         case "quest_close" -> player.closeInventory();
         case "quest_home" -> this.mifron().openQuestUi(player, "categories");
         case "quest_category" -> this.mifron().openQuestUi(player, this.getUiTargetString(clicked));
         case "quest_claim" -> {
            String questId = this.getUiTargetString(clicked);
            if (questId != null && this.questService.claim(player, questId)) {
               QuestDefinition definition = this.questService.definition(questId);
               this.mifron().openQuestUi(player, definition == null ? "categories" : definition.type().key());
            }
         }
         default -> {}
      }
   }

   protected void handleMenuUiClick(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "menu_shop" -> {
            if (this.onlineShopFeature != null) this.onlineShopFeature.openShop(player);
         }
         case "menu_wallet" -> player.sendMessage("\u00a7a\u6240\u6301MP: " + this.mifron().formatNumber(this.mifron().getEmeralds(player.getUniqueId())));
         case "menu_status" -> this.mifron().openFriendUi(player);
         case "menu_quests" -> this.mifron().openQuestUi(player, "categories");
         case "menu_teleporter" -> this.mifron().openTeleportUi(player);
         default -> {}
      }
   }

   protected void handleProposalUiClick(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      if (action == null) return;
      this.playUiClickSound(player);
      switch (action) {
         case "proposal_close" -> player.closeInventory();
         case "proposal_page" -> this.mifron().openProposalUi(player, this.mifron().parsePositiveInt(this.getUiTargetString(clicked), 0));
         case "proposal_vote" -> {
            String raw = this.getUiTargetString(clicked);
            String[] parts = raw == null ? new String[0] : raw.split("\\|", 2);
            if (parts.length == 0 || parts[0].isBlank()) return;
            int page = parts.length > 1 ? this.mifron().parsePositiveInt(parts[1], 0) : 0;
            boolean voted = this.proposalManager.toggleVote(parts[0], player.getUniqueId());
            player.sendMessage(voted ? "\u00a7a\u8cdb\u6210\u6295\u7968\u3057\u307e\u3057\u305f\u3002" : "\u00a7e\u6295\u7968\u3092\u53d6\u308a\u6d88\u3057\u307e\u3057\u305f\u3002");
            this.mifron().openProposalUi(player, page);
         }
         default -> {}
      }
   }

   BedrockUiFeature bedrockUiFeature() { return this.bedrockUiFeature; }

   void teleportToConfigLocation(Player player, String path) {
      Location location = this.mifron().readLocation(path);
      if (location == null) player.sendMessage("\u00a7c\u79fb\u52d5\u5148\u304c\u672a\u8a2d\u5b9a\u3067\u3059: " + path);
      else { player.teleport(location); this.playTeleportSound(player); player.sendMessage("\u00a7a\u79fb\u52d5\u3057\u307e\u3057\u305f\u3002"); }
   }
}
