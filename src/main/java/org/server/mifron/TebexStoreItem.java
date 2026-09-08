package org.server.mifron;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class TebexStoreItem implements Listener {
    private static final String TEBEX_URL = "https://mifron.tebex.io";
    private final JavaPlugin plugin;
    private final NamespacedKey storeKey;

    public TebexStoreItem(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.storeKey = new NamespacedKey(plugin, "tebex_store_item");
    }

    public void register() { plugin.getServer().getPluginManager().registerEvents(this, plugin); }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isStoreItem(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.sendMessage(Component.text("Mifron Store: ", NamedTextColor.GOLD)
            .append(Component.text(TEBEX_URL, NamedTextColor.AQUA)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.openUrl(TEBEX_URL))));
        p.sendMessage(Component.text("クリックするかブラウザで開いてください。", NamedTextColor.GRAY));
    }

    public ItemStack createStoreItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Store", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("右クリックでTebexストアを開く", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(storeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isStoreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(storeKey, PersistentDataType.BYTE);
    }
}
