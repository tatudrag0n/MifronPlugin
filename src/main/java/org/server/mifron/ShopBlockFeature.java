package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ShopBlockFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keyShopType;
    private final NamespacedKey keyOwnerUuid;
    private final NamespacedKey keyPlacementTime;

    // 設置制限管理：プレイヤーごとに設置時刻リスト
    private final Map<UUID, List<Long>> playerShopPlacementTimes = new HashMap<>();

    public ShopBlockFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyShopType = new NamespacedKey(plugin, "shop_type");
        this.keyOwnerUuid = new NamespacedKey(plugin, "shop_owner");
        this.keyPlacementTime = new NamespacedKey(plugin, "shop_placement_time");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        if (!isShopBlockType(type)) return;

        // PDC からショップ情報を取得
        var pdc = block.getPersistentDataContainer();
        if (!pdc.has(keyShopType, PersistentDataType.STRING)) return;

        String shopType = pdc.get(keyShopType, PersistentDataType.STRING);
        String ownerUuidStr = pdc.get(keyOwnerUuid, PersistentDataType.STRING);
        if (ownerUuidStr == null) return;

        UUID ownerUuid = UUID.fromString(ownerUuidStr);
        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null) return;

        event.setCancelled(true);

        if (shopType.equals("SELL_SHELF") || shopType.equals("BUY_SHELF")) {
            handleShelfShop(event.getPlayer(), block, shopType, owner);
        } else if (shopType.equals("SELL_BARREL") || shopType.equals("BUY_BARREL")) {
            handleBarrelShop(event.getPlayer(), block, shopType, owner);
        }
    }

    private boolean isShopBlockType(Material type) {
        return type == Material.OAK_SHELF || type == Material.SPRUCE_SHELF ||
               type == Material.BIRCH_SHELF || type == Material.JUNGLE_SHELF ||
               type == Material.ACACIA_SHELF || type == Material.DARK_OAK_SHELF ||
               type == Material.MANGROVE_SHELF || type == Material.CHERRY_SHELF ||
               type == Material.BAMBOO_SHELF || type == Material.CRIMSON_SHELF ||
               type == Material.WARPED_SHELF || type == Material.BARREL ||
               type == Material.HOPPER;
    }

    private void handleShelfShop(Player player, Block block, String shopType, Player owner) {
        Inventory inv = block.getState().getInventory();
        // 棚ショップは既存仕様を維持：アイテムを置くと自動で価格表ベースで販売/買取
        // 簡易実装：プレイヤーにメッセージで案内
        if (shopType.equals("SELL_SHELF")) {
            player.sendMessage(ChatColor.GREEN + "販売棚ショップです。ここにアイテムを置くと販売されます。");
        } else {
            player.sendMessage(ChatColor.GOLD + "買取棚ショップです。ここにアイテムを置くと買取されます。");
        }
        // 実際の販売/買取処理は既存の棚ショップロジックに委譲（またはここに実装）
    }

    private void handleBarrelShop(Player player, Block block, String shopType, Player owner) {
        if (shopType.equals("SELL_BARREL")) {
            Inventory inv = block.getState().getInventory();
            player.sendMessage(ChatColor.GREEN + "販売樽ショップです。ここにアイテムを置くと販売されます。");
            // 中身販売方式：自動補充なし
        } else if (shopType.equals("BUY_BARREL")) {
            // 買取樽：ホッパー接続チェック
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType() != Material.HOPPER) {
                player.sendMessage(ChatColor.RED + "買取樽ショップには下にホッパーの接続が必要です。");
                return;
            }
            Hopper hopper = (Hopper) below.getState();
            Inventory hopperInv = hopper.getInventory();
            if (hopperInv.firstEmpty() == -1) {
                player.sendMessage(ChatColor.RED + "ホッパーがいっぱいです。買取できません。");
                return;
            }
            Inventory barrelInv = block.getState().getInventory();
            player.sendMessage(ChatColor.GOLD + "買取樽ショップです。ここにアイテムを置くとホッパーに送られ、MP が減算されます。");
            // 実際の買取処理：アイテムをホッパーに転送し、設置者の MP を減算
        }
    }

    public ItemStack createShopBlockItem(String shopType, Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            var pdc = meta.getPersistentDataContainer();
            pdc.set(keyShopType, PersistentDataType.STRING, shopType);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void placeShopBlock(Player player, Block block, String shopType) {
        var pdc = block.getPersistentDataContainer();
        pdc.set(keyShopType, PersistentDataType.STRING, shopType);
        pdc.set(keyOwnerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(keyPlacementTime, PersistentDataType.LONG, System.currentTimeMillis());
        recordShopBlockPlacement(player);
    }

    public boolean canPlaceShopBlock(Player player) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000; // 60 秒以内
        List<Long> times = playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        times.removeIf(t -> t < cutoff);
        return times.size() < 10; // 10 個まで
    }

    public void recordShopBlockPlacement(Player player) {
        long now = System.currentTimeMillis();
        List<Long> times = playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        times.add(now);
    }
}
