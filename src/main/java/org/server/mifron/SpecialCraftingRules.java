package org.server.mifron;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Pure recipe and station arithmetic for the special crafting stations. */
final class SpecialCraftingRules {
    private SpecialCraftingRules() {
    }

    private static final int[][] IRON_SQUARES = {
        {0, 1, 3, 4}, {1, 2, 4, 5}, {3, 4, 6, 7}, {4, 5, 7, 8}
    };

    static boolean isAdvancedCraftingTable(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) return false;
        for (int[] square : IRON_SQUARES) {
            if (allAirExcept(matrix, square) && allMaterial(matrix, square, Material.IRON_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    static final double STEEL_FURNACE_COOK_MULTIPLIER = 0.5;
    static final double STEEL_FURNACE_FUEL_MULTIPLIER = 2.0;
    static final int ADVANCED_TABLE_IRON_BLOCKS = 4;
    static final int STEEL_FURNACE_IRON_BLOCKS = 8;

    static boolean isAdvancedTableRecipe(ItemStack[] matrix) {
        return isAdvancedCraftingTable(matrix);
    }

    static boolean isSteelFurnaceRecipe(ItemStack[] matrix) {
        return isSteelFurnace(matrix);
    }

    static boolean matchesAdvancedCraftingTable(ItemStack[] matrix) {
        return isAdvancedCraftingTable(matrix);
    }

    static boolean matchesSteelFurnace(ItemStack[] matrix) {
        return isSteelFurnace(matrix);
    }

    static int smeltTime(int baseCookTime) {
        return steelFurnaceCookTime(baseCookTime);
    }

    static int fuelBurnTime(int baseBurnTime) {
        return steelFurnaceBurnTime(baseBurnTime);
    }

    static boolean isSteelFurnace(ItemStack[] matrix) {
        if (matrix == null || matrix.length != 9) return false;
        if (matrix[4] == null || matrix[4].getType() != Material.FURNACE) return false;
        for (int i = 0; i < matrix.length; i++) {
            if (i == 4) continue;
            if (matrix[i] == null || matrix[i].getType() != Material.IRON_BLOCK) return false;
        }
        return true;
    }

    static int steelFurnaceCookTime(int totalCookTime) {
        return Math.max(1, Math.max(0, totalCookTime) / 2);
    }

    static int steelFurnaceBurnTime(int burnTime) {
        return Math.max(1, Math.max(0, burnTime) * 2);
    }

    private static boolean allAirExcept(ItemStack[] matrix, int[] occupied) {
        outer:
        for (int i = 0; i < matrix.length; i++) {
            for (int slot : occupied) {
                if (slot == i) continue outer;
            }
            ItemStack item = matrix[i];
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) return false;
        }
        return true;
    }

    private static boolean allMaterial(ItemStack[] matrix, int[] slots, Material material) {
        for (int slot : slots) {
            ItemStack item = matrix[slot];
            if (item == null || item.getType() != material || item.getAmount() <= 0) return false;
        }
        return true;
    }
}
