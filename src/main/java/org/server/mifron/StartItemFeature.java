package org.server.mifron;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class StartItemFeature implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey givenKey;
    private final TebexStoreItem tebexStoreItem;

    public StartItemFeature(JavaPlugin plugin, TebexStoreItem tebexStoreItem) {
        this.plugin         = plugin;
        this.givenKey       = new NamespacedKey(plugin, "start_items_given_v2");
        this.tebexStoreItem = tebexStoreItem;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.getPersistentDataContainer().has(givenKey, PersistentDataType.BYTE)) return;

        // ONLINE SHOP アイテムと Store エメラルドを配布
        p.getInventory().addItem(createOnlineShopItem(), tebexStoreItem.createStoreItem());
        p.getPersistentDataContainer().set(givenKey, PersistentDataType.BYTE, (byte) 1);
        p.sendMessage(Component.text("Mifronへようこそ！初期アイテムを配布しました。", NamedTextColor.GOLD));
    }

    public ItemStack createOnlineShopItem() {
        ItemStack item = new ItemStack(Material.IRON_DOOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("ONLINE SHOP", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("右クリックでオンラインショップを開く", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "online_shop_item"), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
