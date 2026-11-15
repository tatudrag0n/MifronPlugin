package org.server.mifron;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 高度な作業台での特殊クラフト20種のレシピを受け持つ。
 *
 * <p>ここで結果を差し替えるのは「高度な作業台の上」だけで、通常の作業台では
 * 何もしない（＝通常作業台では作れない）。
 */
final class SpecialCraftBenchFeature implements Listener {

    private final Mifron plugin;
    private final SpecialCraftKit kit;

    SpecialCraftBenchFeature(Mifron plugin, SpecialCraftKit kit) {
        this.plugin = plugin;
        this.kit = kit;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        ItemStack[] matrix = inventory.getMatrix();
        if (matrix == null || matrix.length != 9) {
            return;
        }
        Material[] grid = new Material[9];
        boolean empty = true;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = matrix[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            grid[slot] = item.getType();
            empty = false;
        }
        if (empty) {
            return;
        }
        // 高度な作業台のときだけ特殊クラフトを成立させる。
        if (!this.plugin.specialCraftingFeature.isAtAdvancedTable(inventory)) {
            return;
        }
        String id = SpecialCraftRecipeRules.findMatch(SpecialCraftCatalog.all(), grid);
        if (id == null) {
            return;
        }
        ItemStack result = this.kit.create(id);
        if (result != null) {
            inventory.setResult(result);
        }
    }
}
