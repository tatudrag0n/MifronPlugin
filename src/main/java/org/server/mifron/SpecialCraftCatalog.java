package org.server.mifron;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * 特殊クラフト（高度な作業台専用）のカタログ。
 *
 * <p>「バニラやレリックで代替できない遊び」だけを残す方針で選んだ5種を定義する。
 * どれも高度な作業台でしか作れない。
 */
final class SpecialCraftCatalog {

    /** 1レシピ。shapeless のときは ingredients の多重集合だけを見る。 */
    record Recipe(boolean shapeless, Material[] pattern, Material[] ingredients) {
    }

    /** 1つの特殊クラフト定義。 */
    record Def(String id, Material material, int amount, String displayName, List<String> lore,
        Recipe recipe) {
    }

    private static final List<Def> ALL = build();

    private SpecialCraftCatalog() {
    }

    static List<Def> all() {
        return ALL;
    }

    static Def byId(String id) {
        if (id == null) {
            return null;
        }
        for (Def def : ALL) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // レシピ定義ヘルパ
    // ------------------------------------------------------------------

    private static Recipe shaped(String[] rows, Map<Character, Material> keys) {
        Material[] cells = new Material[9];
        for (int row = 0; row < rows.length && row < 3; row++) {
            String line = rows[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                char symbol = line.charAt(col);
                if (symbol == ' ') {
                    continue;
                }
                cells[row * 3 + col] = keys.get(symbol);
            }
        }
        return new Recipe(false, cells, null);
    }

    private static Recipe shapeless(Material... ingredients) {
        return new Recipe(true, null, ingredients);
    }

    /**
     * 残す5種。
     *
     * <ul>
     *   <li>狙撃弓・連射式弩弓 — 既存の特殊クラフト（武器）。</li>
     *   <li>収穫の鎌 — 3x3の一括収穫。バニラに無い。</li>
     *   <li>光のしずく — 一時的な光源。置く手段がバニラに無い。</li>
     *   <li>投網 — 1アイテムでの範囲鈍足。バニラに無い。</li>
     * </ul>
     */
    private static List<Def> build() {
        return List.of(
            new Def("sniper_bow", Material.BOW, 1, "§2§l狙撃弓",
                List.of("§7望遠鏡と弓を組み合わせた長弓。",
                    "§7威力は通常の弓と同じだが、",
                    "§72倍まで引き絞ると弾速と威力が増す。"),
                shapeless(Material.SPYGLASS, Material.BOW)),
            new Def("repeating_crossbow", Material.CROSSBOW, 1, "§8§l連射式弩弓",
                List.of("§7発射装置・クロスボウ・樽を組み合わせた連弩。",
                    "§7攻撃力と耐久は通常のクロスボウと同じ。",
                    "§7右クリック長押しで連射する。"),
                shaped(new String[] {"D", "C", "B"},
                    Map.of('D', Material.DISPENSER, 'C', Material.CROSSBOW, 'B', Material.BARREL))),
            new Def("harvest_scythe", Material.IRON_HOE, 1, "§a§l収穫の鎌",
                List.of("§7刃を麦に合わせて鍛えた鎌。",
                    "§7右クリックで周囲3x3の実った作物をまとめて収穫する。(CD 4秒)"),
                shaped(new String[] {"IIW", "  S"},
                    Map.of('I', Material.IRON_INGOT, 'W', Material.WHEAT, 'S', Material.STICK))),
            new Def("light_drop", Material.GLOWSTONE_DUST, 1, "§e§l光のしずく",
                List.of("§7瓶に閉じ込めた光。",
                    "§7右クリックした面に光を灯す。35秒で消える。(CD 8秒)"),
                shapeless(Material.GLOWSTONE_DUST, Material.GLASS_BOTTLE)),
            new Def("throw_net", Material.COBWEB, 1, "§7§l投網",
                List.of("§7鉄の重りを編み込んだ網。",
                    "§7右クリックで前方8ブロック・半径4のMOBを鈍足にする。(CD 15秒)"),
                shapeless(Material.STRING, Material.STRING, Material.IRON_BARS))
        );
    }

    /**
     * リソースパックのアイテムモデルキー（無ければ null）。
     * 狙撃弓と連射式弩弓だけは専用テクスチャを持つ。
     */
    static String itemModel(String id) {
        if ("sniper_bow".equals(id)) {
            return "special/sniper_bow";
        }
        if ("repeating_crossbow".equals(id)) {
            return "special/repeating_crossbow";
        }
        return null;
    }
}
