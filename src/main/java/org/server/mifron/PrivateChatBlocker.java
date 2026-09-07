package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * PrivateChatBlocker
 * --------------------
 * Disables direct player-to-player private messaging commands
 * (/tell, /msg, /w, /r, /reply, /whisper and common aliases).
 * Private chat should route through Discord custom voice channels instead.
 *
 * Wiring (Mifron.java onEnable):
 *   PrivateChatBlocker.register(this);
 */
public final class PrivateChatBlocker implements Listener {

    private static final List<String> BLOCKED = Arrays.asList(
            "tell", "msg", "w", "r", "reply", "whisper", "message", "pm", "t"
    );

    private PrivateChatBlocker() {}

    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new PrivateChatBlocker(), plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) return;
        String withoutSlash = message.substring(1);
        String label = withoutSlash.split(" ")[0].toLowerCase(Locale.ROOT);
        // Strip plugin-prefix form like "bukkit:tell"
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        if (BLOCKED.contains(label)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.RED + "個人チャットコマンドは無効化されています。"
                    + ChatColor.GRAY + " 代わりにDiscordのカスタムVCをご利用ください。");
        }
    }
}
