package org.server.mifron;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ShelfShopFeature implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey keyOwner, keyType, keyPrice, keyLastTrade;
    public static final Material SELL_SHELF = Material.ORANGE_WOOL;
    public static final Material BUY_SHELF  = Material.BLUE_WOOL;

    public ShelfShopFeature(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyOwner     = new NamespacedKey(plugin, "shelf_owner");
        this.keyType      = new NamespacedKey(plugin, "shelf_type");
        this.keyPrice     = new NamespacedKey(plugin, "shelf_price");
        this.keyLastTrade = new NamespacedKey(plugin, "shelf_last_trade");
        startAutoCloseTask();
    }

    public void register() { plugin.getServer().getPluginManager().registerEvents(this, plugin); }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isShelfWand(hand)) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        e.setCancelled(true);
        p.sendMessage(ChatColor.YELLOW + "棚ショップ設置: チャットで設定してください。");
    }

    @EventHandler
    public void onShelfClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (b.getType() != SELL_SHELF && b.getType() != BUY_SHELF) return;
        if (!(b.getState() instanceof org.bukkit.block.TileState ts)) return;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        if (!pdc.has(keyOwner, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        int price = pdc.getOrDefault(keyPrice, PersistentDataType.INTEGER, 0);
        String type = pdc.getOrDefault(keyType, PersistentDataType.STRING, "SELL");
        if ("SELL".equals(type)) {
            p.sendMessage(ChatColor.GREEN + "" + price + " MPで購入しますか？ もう一度クリックで確定");
        } else {
            p.sendMessage(ChatColor.AQUA + "" + price + " MPで売却しますか？ もう一度クリックで確定");
        }
        pdc.set(keyLastTrade, PersistentDataType.LONG, System.currentTimeMillis());
        ts.update();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != SELL_SHELF && b.getType() != BUY_SHELF) return;
        if (!(b.getState() instanceof org.bukkit.block.TileState ts)) return;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        if (!pdc.has(keyOwner, PersistentDataType.STRING)) return;
        String owner = pdc.get(keyOwner, PersistentDataType.STRING);
        Player p = e.getPlayer();
        if (p.hasPermission("mifron.admin")) return;
        if (!p.getUniqueId().toString().equals(owner)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "このショップはあなたのものではありません。");
        }
    }

    private void startAutoCloseTask() {
        long limit = 30L * 24 * 60 * 60 * 1000;
        new BukkitRunnable() {
            public void run() {
                long now = System.currentTimeMillis();
                for (World w : Bukkit.getWorlds()) {
                    if (!w.getName().equalsIgnoreCase("world_survival")) continue;
                    for (org.bukkit.Chunk chunk : w.getLoadedChunks()) {
                        for (int x = 0; x < 16; x++) for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++) for (int z = 0; z < 16; z++) {
                            Block b = chunk.getBlock(x, y, z);
                            if (b.getType() != SELL_SHELF && b.getType() != BUY_SHELF) continue;
                            if (!(b.getState() instanceof org.bukkit.block.TileState ts)) continue;
                            Long last = ts.getPersistentDataContainer().get(keyLastTrade, PersistentDataType.LONG);
                            if (last != null && now - last > limit) b.setType(Material.AIR);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 3600, 20L * 3600);
    }

    private boolean isShelfWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "SHELF_SHOP".equals(item.getItemMeta().getPersistentDataContainer()
            .get(new NamespacedKey(plugin, "shop_wand_type"), PersistentDataType.STRING));
    }
}
