package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * MerchantBuybackFeature
 * ------------------------
 * Converts the merchant trade UI into a BUY-ONLY (buyback) interface.
 * On open, 18 random items are drawn from the configured item pool and
 * displayed with their buy price; players sell by clicking a slot while
 * holding the matching item. Other merchant behaviour is unchanged.
 *
 * Wiring (Mifron.java onEnable):
 *   MerchantBuybackFeature.register(this);
 * Wiring (existing villager-interact listener):
 *   Replace the call that opens the vanilla trade window with:
 *   MerchantBuybackFeature.open(player);
 */
public final class MerchantBuybackFeature implements Listener {

    private static final int GUI_SIZE = 27; // 3 rows, first 18 slots are buy offers
    private static final String GUI_TITLE = "§6商人 - 買取";

    /** Item pool: material -> unit buy price. Tune freely without touching logic. */
    private static final Map<Material, Integer> ITEM_POOL = new HashMap<>();
    static {
        ITEM_POOL.put(Material.DIAMOND, 120);
        ITEM_POOL.put(Material.EMERALD, 40);
        ITEM_POOL.put(Material.GOLD_INGOT, 25);
        ITEM_POOL.put(Material.IRON_INGOT, 12);
        ITEM_POOL.put(Material.COAL, 3);
        ITEM_POOL.put(Material.REDSTONE, 4);
        ITEM_POOL.put(Material.LAPIS_LAZULI, 6);
        ITEM_POOL.put(Material.NETHERITE_SCRAP, 400);
        ITEM_POOL.put(Material.ANCIENT_DEBRIS, 300);
        ITEM_POOL.put(Material.AMETHYST_SHARD, 8);
        ITEM_POOL.put(Material.ECHO_SHARD, 90);
        ITEM_POOL.put(Material.GHAST_TEAR, 60);
        ITEM_POOL.put(Material.NAUTILUS_SHELL, 50);
        ITEM_POOL.put(Material.HEART_OF_THE_SEA, 500);
        ITEM_POOL.put(Material.TOTEM_OF_UNDYING, 800);
        ITEM_POOL.put(Material.ELYTRA, 1000);
        ITEM_POOL.put(Material.TRIDENT, 350);
        ITEM_POOL.put(Material.SHULKER_SHELL, 150);
        ITEM_POOL.put(Material.PRISMARINE_CRYSTALS, 5);
        ITEM_POOL.put(Material.SLIME_BALL, 2);
        ITEM_POOL.put(Material.PHANTOM_MEMBRANE, 30);
        ITEM_POOL.put(Material.WITHER_SKELETON_SKULL, 200);
        ITEM_POOL.put(Material.DRAGON_BREATH, 90);
        ITEM_POOL.put(Material.NETHER_STAR, 900);
        ITEM_POOL.put(Material.SPONGE, 40);
    }

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Map<Integer, Material>> OPEN_SESSIONS = new HashMap<>();

    private MerchantBuybackFeature() {}

    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new MerchantBuybackFeature(), plugin);
    }

    /** Opens the buyback-only GUI for the player, replacing vanilla trade UI. */
    public static void open(Player player) {
        List<Material> pool = new ArrayList<>(ITEM_POOL.keySet());
        Collections.shuffle(pool, RANDOM);
        List<Material> selected = new ArrayList<>(pool.subList(0, Math.min(18, pool.size())));

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        Map<Integer, Material> slotMap = new HashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            Material mat = selected.get(i);
            ItemStack display = new ItemStack(mat);
            ItemMeta meta = display.getItemMeta();
            int price = ITEM_POOL.get(mat);
            meta.setDisplayName("§e" + mat.name() + " §7(買取: " + price + " MP)");
            meta.setLore(Collections.singletonList("§fクリックして所持しているこのアイテムを1個売却"));
            display.setItemMeta(meta);
            gui.setItem(i, display);
            slotMap.put(i, mat);
        }
        OPEN_SESSIONS.put(player.getUniqueId(), slotMap);
        player.openInventory(gui);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Map<Integer, Material> slotMap = OPEN_SESSIONS.get(player.getUniqueId());
        if (slotMap == null) return;
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        Material target = slotMap.get(event.getRawSlot());
        if (target == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != target) {
            player.sendMessage("§c売却するアイテムをメインハンドに持ってください。");
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        int price = ITEM_POOL.get(target);
        // Hook into the existing MP/economy service instead of this stub call:
        // Mifron.getEconomyService().deposit(player.getUniqueId(), price);
        player.sendMessage("§a" + target.name() + " を売却し " + price + " MP を獲得しました。(連携: 既存エコノミーAPIへの接続が必要)");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (GUI_TITLE.equals(event.getView().getTitle())) {
            OPEN_SESSIONS.remove(event.getPlayer().getUniqueId());
        }
    }
}
