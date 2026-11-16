package org.server.mifron;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * 高度な作業台のレシピ照合ロジック（純粋関数のみ）。
 *
 * <p>Bukkit のイベントや ItemStack に依存しないので、ユニットテストで
 * 「材料の並びが正しいか」を直接検証できる。
 */
final class SpecialCraftRecipeRules {

    private SpecialCraftRecipeRules() {
    }

    /** 3x3 グリッドを、空の行・列を削った最小の形に正規化したもの。 */
    record Shape(int rows, int cols, Material[] cells) {
        boolean isEmpty() {
            return this.rows == 0 || this.cols == 0;
        }
    }

    /**
     * 3x3 グリッド（null = 空きスロット）を最小の形へ正規化する。
     * レシピがテーブルのどこに置かれても一致させられるようにするため。
     */
    static Shape shape(Material[] grid) {
        if (grid == null || grid.length != 9) {
            return new Shape(0, 0, new Material[0]);
        }
        int minRow = 3;
        int maxRow = -1;
        int minCol = 3;
        int maxCol = -1;
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null) {
                continue;
            }
            int row = i / 3;
            int col = i % 3;
            if (row < minRow) minRow = row;
            if (row > maxRow) maxRow = row;
            if (col < minCol) minCol = col;
            if (col > maxCol) maxCol = col;
        }
        if (maxRow < 0) {
            return new Shape(0, 0, new Material[0]);
        }
        int rows = maxRow - minRow + 1;
        int cols = maxCol - minCol + 1;
        Material[] cells = new Material[rows * cols];
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null) {
                continue;
            }
            int row = i / 3;
            int col = i % 3;
            cells[(row - minRow) * cols + (col - minCol)] = grid[i];
        }
        return new Shape(rows, cols, cells);
    }

    /** 形（サイズと各マス）が完全一致するか。null は「空きであること」を要求する。 */
    static boolean matchesShaped(Shape pattern, Shape grid) {
        if (pattern == null || grid == null) {
            return false;
        }
        if (pattern.rows() != grid.rows() || pattern.cols() != grid.cols()) {
            return false;
        }
        for (int i = 0; i < pattern.cells().length; i++) {
            if (pattern.cells()[i] != grid.cells()[i]) {
                return false;
            }
        }
        return true;
    }

    /** 不定形レシピ: 空きを除いた材料の多重集合が完全一致するか。 */
    static boolean matchesShapeless(Material[] ingredients, Material[] grid) {
        if (ingredients == null || grid == null || grid.length != 9) {
            return false;
        }
        Map<Material, Integer> want = new HashMap<>();
        for (Material material : ingredients) {
            if (material != null) {
                want.merge(material, 1, Integer::sum);
            }
        }
        Map<Material, Integer> have = new HashMap<>();
        for (Material material : grid) {
            if (material != null) {
                have.merge(material, 1, Integer::sum);
            }
        }
        return want.equals(have);
    }

    /** カタログのレシピ定義とグリッドが一致するか。 */
    static boolean matches(SpecialCraftCatalog.Recipe recipe, Material[] grid) {
        if (recipe == null) {
            return false;
        }
        if (recipe.shapeless()) {
            return matchesShapeless(recipe.ingredients(), grid);
        }
        return matchesShaped(shape(recipe.pattern()), shape(grid));
    }

    /** グリッドから一致するレシピを探し、その完成品IDを返す（無ければ null）。 */
    static String findMatch(java.util.List<SpecialCraftCatalog.Def> defs, Material[] grid) {
        if (defs == null || grid == null) {
            return null;
        }
        for (SpecialCraftCatalog.Def def : defs) {
            if (matches(def.recipe(), grid)) {
                return def.id();
            }
        }
        return null;
    }
}
