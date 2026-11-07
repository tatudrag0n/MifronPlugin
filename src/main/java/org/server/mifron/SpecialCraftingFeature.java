package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Survival quality-of-life crafting stations:
 * - advanced crafting table: four iron blocks in a square, made in a normal table.
 * - steel furnace: eight iron blocks around a furnace, only craftable at the
 *   advanced crafting table. Smelts twice as fast and burns fuel for twice as
 *   long.
 */
final class SpecialCraftingFeature implements Listener {
    static final String ADVANCED_TABLE_ID = "advanced_crafting_table";
    static final String STEEL_FURNACE_ID = "steel_furnace";

    private final Mifron plugin;
    private final NamespacedKey specialItemKey;
    private final NamespacedKey steelFurnaceBlockKey;
    private final java.util.Set<org.bukkit.Location> advancedTables = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.io.File tableFile;

    SpecialCraftingFeature(Mifron plugin) {
        this.plugin = plugin;
        this.specialItemKey = new NamespacedKey(plugin, "special_craft_item");
        this.steelFurnaceBlockKey = new NamespacedKey(plugin, "steel_furnace_block");
        this.tableFile = new java.io.File(plugin.getDataFolder(), "advanced-tables.yml");
    }

    void load() {
        this.advancedTables.clear();
        if (!this.tableFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.tableFile);
        for (String key : config.getKeys(false)) {
            String[] parts = key.split(";");
            if (parts.length != 4) continue;
            try {
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                this.advancedTables.add(new org.bukkit.Location(world, x, y, z));
            } catch (NumberFormatException ignored) {
                // Ignore malformed entries instead of disabling the feature.
            }
        }
    }

    private void save() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        for (org.bukkit.Location location : this.advancedTables) {
            if (location.getWorld() == null) continue;
            String key = location.getWorld().getName() + ";" + location.getBlockX() + ";"
                + location.getBlockY() + ";" + location.getBlockZ();
            config.set(key, true);
        }
        try {
            config.save(this.tableFile);
        } catch (java.io.IOException exception) {
            this.plugin.getLogger().warning("Could not save advanced crafting tables: " + exception.getMessage());
        }
    }

    ItemStack createAdvancedCraftingTable() {
        return this.createSpecialItem(Material.CRAFTING_TABLE, ChatColor.AQUA + "高度な作業台",
            List.of(ChatColor.GRAY + "特殊クラフト専用の作業台。",
                ChatColor.GRAY + "鋼鉄かまどなどの特殊クラフトはここで行う。"), ADVANCED_TABLE_ID);
    }

    ItemStack createSteelFurnace() {
        return this.createSpecialItem(Material.FURNACE, ChatColor.GOLD + "鋼鉄かまど",
            List.of(ChatColor.GRAY + "精錬速度2倍。",
                ChatColor.GRAY + "燃料の持ち時間2倍（燃料消費1/2）。"), STEEL_FURNACE_ID);
    }

    private ItemStack createSpecialItem(Material material, String name, List<String> lore, String id) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.getPersistentDataContainer().set(this.specialItemKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String specialItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(this.specialItemKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        if (matrix == null || matrix.length != 9) return;

        // The advanced table itself is the one recipe that works anywhere.
        if (SpecialCraftingRules.isAdvancedCraftingTable(matrix)) {
            inventory.setResult(this.createAdvancedCraftingTable());
            return;
        }

        if (!this.isAtAdvancedTable(inventory)) return;
        if (SpecialCraftingRules.isSteelFurnace(matrix)) {
            inventory.setResult(this.createSteelFurnace());
        }
    }

    private boolean isAtAdvancedTable(CraftingInventory inventory) {
        if (inventory.getLocation() == null) return false;
        Block block = inventory.getLocation().getBlock();
        if (block.getType() != Material.CRAFTING_TABLE) return false;
        return this.advancedTables.contains(block.getLocation());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String id = this.specialItemId(item);
        if (id == null) return;
        Block block = event.getBlockPlaced();
        if (ADVANCED_TABLE_ID.equals(id) && block.getType() == Material.CRAFTING_TABLE) {
            this.advancedTables.add(block.getLocation());
            this.save();
            event.getPlayer().sendMessage(ChatColor.AQUA + "高度な作業台を設置した。");
        } else if (STEEL_FURNACE_ID.equals(id) && block.getType() == Material.FURNACE) {
            this.markBlock(block, this.steelFurnaceBlockKey);
            event.getPlayer().sendMessage(ChatColor.GOLD + "鋼鉄かまどを設置した。");
        }
    }

    private void markBlock(Block block, NamespacedKey key) {
        BlockState state = block.getState();
        if (state instanceof org.bukkit.block.TileState tile) {
            tile.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            tile.update(true, false);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CRAFTING_TABLE) {
            if (this.advancedTables.remove(block.getLocation())) {
                this.save();
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), this.createAdvancedCraftingTable());
            }
        } else if (block.getType() == Material.FURNACE) {
            if (this.isSteelFurnace(block)) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), this.createSteelFurnace());
            }
        }
    }

    @EventHandler
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        if (!this.isSteelFurnace(event.getBlock())) return;
        event.setTotalCookTime(SpecialCraftingRules.steelFurnaceCookTime(event.getTotalCookTime()));
    }

    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (!this.isSteelFurnace(event.getBlock())) return;
        event.setBurnTime(SpecialCraftingRules.steelFurnaceBurnTime(event.getBurnTime()));
    }

    private boolean isSteelFurnace(Block block) {
        if (block == null || block.getType() != Material.FURNACE) return false;
        if (!(block.getState() instanceof org.bukkit.block.TileState tile)) return false;
        Byte marker = tile.getPersistentDataContainer().get(this.steelFurnaceBlockKey, PersistentDataType.BYTE);
        return marker != null && marker != 0;
    }
}
