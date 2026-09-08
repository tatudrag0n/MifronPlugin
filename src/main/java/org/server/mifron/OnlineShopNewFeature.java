package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * NEW Online Shop Feature (Creative-style UI)
 * - 9x6 inventory UI (54 slots)
 * - Category tabs (General, Special)
 * - Purchase cooldown per item
 * - Sell-only (no buyback)
 * - Initial item: Iron Door
 */
public class OnlineShopNewFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keyOnlineShopItem;
    private final NamespacedKey keyProductId;
    private final Map<UUID, Map<String, Long>> playerCooldowns = new HashMap<>();

    public OnlineShopNewFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyOnlineShopItem = new NamespacedKey(plugin, "online_shop_new");
        this.keyProductId = new NamespacedKey(plugin, "online_shop_product_new");
    }

    public ItemStack createOnlineShopItem() {
        ItemStack item = new ItemStack(Material.IRON_DOOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + "" + ChatColor.BOLD + "ONLINE SHOP");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "右クリックで ONLINE SHOP を開きます",
                ChatColor.GRAY + "どこでもアイテムを購入可能"
            ));
            meta.getPersistentDataContainer().set(keyOnlineShopItem, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(keyOnlineShopItem, PersistentDataType.BYTE)) return;
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openShop(event.getPlayer());
        }
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.BLUE + "ONLINE SHOP");
        setupCategoryTabs(inv);
        setupProducts(inv, player, "general");
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    private void setupCategoryTabs(Inventory inv) {
        ItemStack generalTab = new ItemStack(Material.BOOK);
        ItemMeta meta = generalTab.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "一般アイテム");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "通常のアイテム"));
            generalTab.setItemMeta(meta);
        }
        inv.setItem(45, generalTab);
        
        ItemStack specialTab = new ItemStack(Material.BLAZE_ROD);
        ItemMeta specialMeta = specialTab.getItemMeta();
        if (specialMeta != null) {
            specialMeta.setDisplayName(ChatColor.GOLD + "特殊アイテム");
            specialMeta.setLore(Arrays.asList(ChatColor.GRAY + "棚/樽ショップ等"));
            specialTab.setItemMeta(specialMeta);
        }
        inv.setItem(46, specialTab);
        
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 47; i < 54; i++) inv.setItem(i, glass);
    }

    private void setupProducts(Inventory inv, Player player, String category) {
        var section = plugin.getConfig().getConfigurationSection("online-shop-new.items");
        if (section == null) return;
        int slot = 0;
        for (String productId : section.getKeys(false)) {
            if (slot >= 45) break;
            String configCategory = section.getString(productId + ".category", "general");
            if (!configCategory.equals(category)) continue;
            String materialName = section.getString(productId + ".material", "PAPER");
            Material material = Material.matchMaterial(materialName);
            if (material == null) material = Material.PAPER;
            ItemStack product = new ItemStack(material);
            ItemMeta meta = product.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(section.getString(productId + ".display-name", productId));
                int price = section.getInt(productId + ".price", 0);
                String rarity = section.getString(productId + ".rarity", "COMMON");
                int cooldown = section.getInt(productId + ".cooldown-seconds", 0);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "価格：" + price + " MP");
                lore.add(ChatColor.GRAY + "レア度：" + rarity);
                lore.add(ChatColor.GRAY + "クールダウン：" + cooldown + "秒");
                lore.add("");
                long now = System.currentTimeMillis();
                long expiry = getPlayerCooldown(player, productId);
                if (expiry > now) {
                    lore.add(ChatColor.RED + "購入クールダウン中：" + ((expiry - now + 999) / 1000) + "秒");
                } else {
                    lore.add(ChatColor.GREEN + "クリックで購入");
                }
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(keyProductId, PersistentDataType.STRING, productId);
                product.setItemMeta(meta);
            }
            inv.setItem(slot++, product);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv == null) return;
        if (!inv.getTitle().equals(ChatColor.BLUE + "ONLINE SHOP")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;
        if (slot >= 45) {
            if (slot == 45) {
                setupProducts(inv, player, "general");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (slot == 46) {
                setupProducts(inv, player, "special");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String productId = clicked.getItemMeta().getPersistentDataContainer().get(keyProductId, PersistentDataType.STRING);
        if (productId == null) return;
        purchaseProduct(player, productId, clicked);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory inv = event.getInventory();
        if (inv == null) return;
        if (!inv.getTitle().equals(ChatColor.BLUE + "ONLINE SHOP")) return;
        event.setCancelled(true);
    }

    private void purchaseProduct(Player player, String productId, ItemStack productItem) {
        long now = System.currentTimeMillis();
        long expiry = getPlayerCooldown(player, productId);
        if (expiry > now) {
            player.sendMessage(ChatColor.RED + "このアイテムは購入クールダウン中です。残り：" + ((expiry - now + 999) / 1000) + "秒");
            return;
        }
        var section = plugin.getConfig().getConfigurationSection("online-shop-new.items." + productId);
        if (section == null) {
            player.sendMessage(ChatColor.RED + "この商品は現在利用できません。");
            return;
        }
        int price = section.getInt("price", 0);
        String materialName = section.getString("material", "PAPER");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.PAPER;
        if (!plugin.withdrawEmeralds(player.getUniqueId(), price)) {
            player.sendMessage(ChatColor.RED + "MP が足りません。必要 MP: " + price);
            return;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(section.getString("display-name", productId));
            String shopType = section.getString("shop-type", null);
            if (shopType != null) {
                NamespacedKey keyShopType = new NamespacedKey(plugin, "shop_type");
                meta.getPersistentDataContainer().set(keyShopType, PersistentDataType.STRING, shopType);
            }
            item.setItemMeta(meta);
        }
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
            player.sendMessage(ChatColor.YELLOW + "インベントリがいっぱいのため、一部アイテムがドロップされました。");
        }
        int cooldownSeconds = section.getInt("cooldown-seconds", 0);
        if (cooldownSeconds > 0) {
            setPlayerCooldown(player, productId, now + (cooldownSeconds * 1000L));
        }
        player.sendMessage(ChatColor.GREEN + "購入しました：" + productId + " (" + price + " MP)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    private long getPlayerCooldown(Player player, String productId) {
        return playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).getOrDefault(productId, 0L);
    }

    private void setPlayerCooldown(Player player, String productId, long expiryTime) {
        playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(productId, expiryTime);
    }

    public void clearPlayerCooldowns(UUID playerUuid) {
        playerCooldowns.remove(playerUuid);
    }
}
