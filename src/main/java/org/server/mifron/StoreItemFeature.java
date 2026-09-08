package org.server.mifron;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class StoreItemFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keyStoreItem;
    private static final String STORE_URL = "https://mifron.tebex.io";

    public StoreItemFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyStoreItem = new NamespacedKey(plugin, "mifron_store_activator");
    }

    public ItemStack createStoreItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Store [公式ストア]");
            meta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "右クリックで公式Tebexストアを開きます。",
                    ChatColor.GRAY + "サポーターパスやカスタム装飾を購入できます。"
            ));
            meta.getPersistentDataContainer().set(keyStoreItem, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isStoreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyStoreItem, PersistentDataType.BYTE);
    }

    public boolean hasStoreItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isStoreItem(item)) return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!hasStoreItem(player)) {
            player.getInventory().addItem(createStoreItem());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (isStoreItem(item)) {
                event.setCancelled(true);
                Player player = event.getPlayer();

                TextComponent msg = new TextComponent(ChatColor.GOLD + "🛒 [ここをクリックしてMifron公式ストアを開く]");
                msg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, STORE_URL));
                msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§bブラウザでTebexストアを開きます").create()));

                player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GREEN + "Mifron 公式Webストア (Tebex)");
                player.spigot().sendMessage(msg);
                player.sendMessage(ChatColor.GRAY + "URL: " + STORE_URL);
                player.sendMessage(ChatColor.YELLOW + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            }
        }
    }
}
