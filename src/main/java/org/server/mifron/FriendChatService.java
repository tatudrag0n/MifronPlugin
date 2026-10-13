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
 * Owns the friend-list search prompt and the {@link AsyncChatEvent} listener
 * that consumes it. Global chat is intentionally left untouched so players
 * can talk in-game; player-to-player private messaging is not provided
 * (the command block that enforces this lives in {@link ChatPolicyFeature}).
 *
 * <p>{@code AsyncChatEvent} fires off the main thread. The search branch
 * cancels the message and hops back to the main thread before touching
 * inventory, config or player state, so no async thread performs world
 * operations.
 */
final class FriendChatService implements Listener {

   static final int MAX_FILTER_LENGTH = 32;

   private final Mifron plugin;
   private final Map<UUID, String> searchFilters = new ConcurrentHashMap<>();
   private final Map<UUID, String> pendingSearch = new ConcurrentHashMap<>();

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

   @EventHandler
   public void onChat(AsyncChatEvent event) {
      Player sender = event.getPlayer();
      UUID senderId = sender.getUniqueId();
      if (!this.pendingSearch.containsKey(senderId)) return;
      String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      event.setCancelled(true);
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         String plainMessage = this.plugin.sanitizeTextInput(rawMessage, MAX_FILTER_LENGTH);
         this.pendingSearch.remove(senderId);
         if (!plainMessage.equalsIgnoreCase("clear") && !plainMessage.isBlank()) this.searchFilters.put(senderId, plainMessage);
         else this.searchFilters.remove(senderId);
         this.plugin.openFriendUi(sender);
      });
   }
}
