package org.server.mifron;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Blocks directed private-message commands so the server only provides
 * public chat. Player-to-player message mediation would make the operator
 * a telecommunications carrier under the 電気通信事業法 and require a
 * filing, so this guard must stay in place. Additional command labels can
 * be added through {@code chat.blocked-private-commands} in config.yml.
 */
final class ChatPolicyFeature implements Listener {
   private final Mifron plugin;
   private final Set<String> extraBlocked = new HashSet<>();

   ChatPolicyFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.extraBlocked.clear();
      for (String entry : this.plugin.getConfig().getStringList("chat.blocked-private-commands")) {
         if (entry == null) continue;
         String label = ChatPolicyRules.normalizeLabel(entry.trim());
         if (!label.isBlank()) this.extraBlocked.add(label);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
      if (!ChatPolicyRules.isPrivateMessageCommand(event.getMessage(), this.extraBlocked)) return;
      Player player = event.getPlayer();
      event.setCancelled(true);
      player.sendMessage("\u00a7c\u500b\u4eba\u5b9b\u30e1\u30c3\u30bb\u30fc\u30b8(DM)\u306f\u5ec3\u6b62\u3057\u307e\u3057\u305f\u3002\u5168\u4f53\u30c1\u30e3\u30c3\u30c8\u304bDiscord\u3092\u3054\u5229\u7528\u304f\u3060\u3055\u3044\u3002");
   }
}
