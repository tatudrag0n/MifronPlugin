package org.server.mifron;

import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public class BarrelShopFeature implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey keyOwner, keyWand;

    public BarrelShopFeature(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.keyOwner = new NamespacedKey(plugin, "barrel_shop_owner");
        this.keyWand  = new NamespacedKey(plugin, "shop_wand_type");
    }

    public void register() { plugin.getServer().getPluginManager().registerEvents(this, plugin); }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isBarrelWand(hand)) return;
        e.setCancelled(true);
        if (!(block.getState() instanceof Barrel barrel)) return;
        PersistentDataContainer pdc = barrel.getPersistentDataContainer();
        if (pdc.has(keyOwner, PersistentDataType.STRING)) {
            e.getPlayer().sendMessage(ChatColor.RED + "この樽はすでにショップ登録済みです。");
            return;
        }
        pdc.set(keyOwner, PersistentDataType.STRING, e.getPlayer().getUniqueId().toString());
        barrel.update();
        hand.setAmount(hand.getAmount() - 1);
        e.getPlayer().sendMessage(ChatColor.GREEN + "樽ショップを設置しました！");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.BARREL) return;
        if (!(block.getState() instanceof Barrel barrel)) return;
        PersistentDataContainer pdc = barrel.getPersistentDataContainer();
        if (!pdc.has(keyOwner, PersistentDataType.STRING)) return;
        String owner = pdc.get(keyOwner, PersistentDataType.STRING);
        Player p = e.getPlayer();
        if (p.hasPermission("mifron.admin")) return;
        if (!p.getUniqueId().toString().equals(owner)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "この樽ショップはあなたのものではありません。");
        }
    }

    public static ItemStack createBarrelWand(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "樽ショップ設置アイテム");
        meta.setLore(List.of(ChatColor.GRAY + "樽に右クリックでショップ化", ChatColor.GRAY + "ONLINE SHOPで購入可能"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_wand_type"), PersistentDataType.STRING, "BARREL_SHOP");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isBarrelWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "BARREL_SHOP".equals(item.getItemMeta().getPersistentDataContainer().get(keyWand, PersistentDataType.STRING));
    }
}
