package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class WorldEditHook implements Listener {

    private final JavaPlugin plugin;
    private static final int MAX_BLOCK_LIMIT = 5000;

    public WorldEditHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        String msg = e.getMessage().toLowerCase();

        // 大量設置系コマンドの検知
        if (msg.startsWith("//set") || msg.startsWith("//replace") || 
            msg.startsWith("//sphere") || msg.startsWith("//hsphere") || 
            msg.startsWith("//cyl") || msg.startsWith("//hcyl") || msg.startsWith("//copy")) {
            
            World world = player.getWorld();

            // 旧BuildワールドをCreativeに変更。Creative共有ワールド以外での実行を遮断
            if (!world.getName().equalsIgnoreCase("creative")) {
                player.sendMessage(ChatColor.RED + "WorldEditは「creative」ワールドでのみ使用可能です。");
                e.setCancelled(true);
                return;
            }

            // 一般ユーザーの場合、自動的に上限ブロック数を制限コマンドに差し替えて安全化
            if (!player.isOp() && !player.hasPermission("mifron.admin.bypass")) {
                player.performCommand("/limit " + MAX_BLOCK_LIMIT);
            }
        }
    }
}
