package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class OnlineShopFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keyShopItem;

    public OnlineShopFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyShopItem = new NamespacedKey(plugin, "mifron_shop_activator");
    }

    public ItemStack createShopItem() {
        ItemStack item = new ItemStack(Material.IRON_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Online Shop");
            meta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "右クリックでオンラインショップを開きます。",
                    ChatColor.GRAY + "どこでも売買・サービス利用が可能です。"
            ));
            meta.getPersistentDataContainer().set(keyShopItem, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean hasShopItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isShopItem(item)) return true;
        }
        return false;
    }

    public boolean isShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyShopItem, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!hasShopItem(player)) {
            player.getInventory().addItem(createShopItem());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (isShopItem(item)) {
                event.setCancelled(true);
                openShop(event.getPlayer());
            }
        }
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "Mifron Online Shop");

        inv.setItem(11, createCategoryItem(Material.DIAMOND_SWORD, ChatColor.RED + "武器・防具", "戦闘用装備を購入"));
        inv.setItem(13, createCategoryItem(Material.OAK_LOG, ChatColor.GREEN + "建築・素材", "各種ブロック・素材を購入"));
        inv.setItem(15, createCategoryItem(Material.GOLDEN_APPLE, ChatColor.YELLOW + "消耗品・便利アイテム", "ポーションや食料を購入"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private ItemStack createCategoryItem(Material mat, String name, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(ChatColor.GRAY + desc));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_AQUA + "Mifron Online Shop")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            Player player = (Player) event.getWhoClicked();
            player.sendMessage(ChatColor.YELLOW + "選択したカテゴリ: " + event.getCurrentItem().getItemMeta().getDisplayName());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        }
    }
}
