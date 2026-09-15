package org.server.mifron;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Owns the transient friend-chat state (search filter, pending input prompts,
 * active conversation and drafts) and the {@link AsyncChatEvent} listener that
 * consumes those prompts. Extracted from the MifronPart15 chain so chat handling
 * is independent of the inheritance chain.
 *
 * <p>{@code AsyncChatEvent} fires off the main thread. Every branch cancels the
 * message and hops back to the main thread before touching inventory, config or
 * player state, so no async thread performs world/server operations.
 */
final class FriendChatService implements Listener {

   static final int MAX_MESSAGE_LENGTH = 256;
   static final int MAX_FILTER_LENGTH = 32;

   private final Mifron plugin;
   private final Map<UUID, String> searchFilters = new ConcurrentHashMap<>();
   private final Map<UUID, String> pendingSearch = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> pendingChatInput = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> activeChatTarget = new ConcurrentHashMap<>();
   private final Map<UUID, String> chatDrafts = new ConcurrentHashMap<>();

   FriendChatService(Mifron plugin) {
      this.plugin = plugin;
   }

   String searchFilter(UUID playerId) {
      return this.searchFilters.getOrDefault(playerId, "");
   }

   void beginSearch(Player player) {
      this.pendingSearch.put(player.getUniqueId(), "");
      player.closeInventory();
      player.sendMessage("\u00a7e\u691c\u7d22\u3059\u308b\u30e6\u30fc\u30b6\u30fc\u540d\u3092\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
   }

   UUID activeTarget(UUID playerId) {
      return this.activeChatTarget.get(playerId);
   }

   void openChat(UUID playerId, UUID targetId) {
      this.activeChatTarget.put(playerId, targetId);
   }

   void closeChat(UUID playerId) {
      this.activeChatTarget.remove(playerId);
      this.chatDrafts.remove(playerId);
   }

   String draft(UUID playerId) {
      return this.chatDrafts.getOrDefault(playerId, "");
   }

   String takeDraft(UUID playerId) {
      return this.chatDrafts.remove(playerId);
   }

   @EventHandler
   public void onChat(AsyncChatEvent event) {
      Player sender = event.getPlayer();
      UUID senderId = sender.getUniqueId();
      String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      if (this.pendingSearch.containsKey(senderId)) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            String plainMessage = this.plugin.sanitizeTextInput(rawMessage, MAX_FILTER_LENGTH);
            this.pendingSearch.remove(senderId);
            if (!plainMessage.equalsIgnoreCase("clear") && !plainMessage.isBlank()) this.searchFilters.put(senderId, plainMessage);
            else this.searchFilters.remove(senderId);
            this.plugin.openFriendUi(sender);
         });
         return;
      }
      if (this.pendingChatInput.containsKey(senderId)) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            UUID chatTarget = this.pendingChatInput.remove(senderId);
            if (chatTarget == null) return;
            String plainMessage = this.plugin.sanitizeTextInput(rawMessage, MAX_MESSAGE_LENGTH);
            if (!plainMessage.isBlank()) {
               this.activeChatTarget.put(senderId, chatTarget);
               this.chatDrafts.put(senderId, plainMessage);
            }
            this.plugin.openFriendUi(sender);
         });
         return;
      }
      event.setCancelled(true);
      Bukkit.getScheduler().runTask(this.plugin, () -> sender.sendMessage("\u00a77\u901a\u5e38\u30c1\u30e3\u30c3\u30c8\u306fDiscord\u3067\u3054\u5229\u7528\u304f\u3060\u3055\u3044\u3002"));
   }
}
