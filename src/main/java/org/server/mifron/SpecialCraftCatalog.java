package org.server.mifron;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * 特殊クラフト（高度な作業台専用）のカタログ。
 *
 * <p>レリックより弱い「暮らしの道具」を20種定義する。材料と機能が対応するように
 * してあり、効果は小さい数値＋クールダウンで抑えてある。
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

    private static List<Def> build() {
        return List.of(
            // ---------------- 装備（パッシブ） ----------------
            new Def("fireproof_cloak", Material.LEATHER_CHESTPLATE, 1, "§6§l耐火の外套",
                List.of("§7マグマを織り込んだ外套。",
                    "§7装着している間、炎と溶岩のダメージを35%軽減する。"),
                shapeless(Material.LEATHER_CHESTPLATE, Material.MAGMA_CREAM, Material.BLAZE_POWDER)),
            new Def("safety_harness", Material.LEATHER_BOOTS, 1, "§6§l登山の命綱",
                List.of("§7革の靴に滑り止めの綱を結んだもの。",
                    "§7装着している間、落下ダメージを40%軽減する。"),
                shapeless(Material.LEATHER_BOOTS, Material.LEAD, Material.STRING)),
            new Def("artisan_apron", Material.LEATHER_LEGGINGS, 1, "§6§l職人の前掛け",
                List.of("§7鉄板を仕込んだ作業用の前掛け。",
                    "§7装着している間、採掘速度上昇Iが付き続ける。"),
                shapeless(Material.LEATHER_LEGGINGS, Material.IRON_BLOCK)),
            new Def("deep_diver_helm", Material.TURTLE_HELMET, 1, "§3§l深海の兜",
                List.of("§7亀の甲羅に海の結晶を嵌めた兜。",
                    "§7装着中、水中で息が続き、水中採掘も速くなる。"),
                shapeless(Material.TURTLE_HELMET, Material.PRISMARINE_SHARD, Material.NAUTILUS_SHELL)),
            new Def("wind_feather", Material.FEATHER, 1, "§f§l風読みの羽根",
                List.of("§7ファントムの膜を貼った羽根。",
                    "§7手に持っている間、ゆっくりと落下する。"),
                shapeless(Material.FEATHER, Material.PHANTOM_MEMBRANE)),

            // ---------------- 道具（アクティブ） ----------------
            new Def("portable_crafting_table", Material.CRAFTING_TABLE, 1, "§b§l携帯作業台",
                List.of("§7折り畳める鉄枠の作業台。",
                    "§7右クリックで3x3の作業台を開く。(CD 10秒)"),
                shaped(new String[] {"ISI", " C "},
                    Map.of('I', Material.IRON_INGOT, 'S', Material.STICK, 'C', Material.CRAFTING_TABLE))),
            new Def("portable_ender_chest", Material.ENDER_CHEST, 1, "§5§l携帯エンダーチェスト",
                List.of("§7エンダーパールを埋め込んだ小箱。",
                    "§7右クリックで自分のエンダーチェストを開く。(CD 15秒)"),
                shapeless(Material.ENDER_CHEST, Material.ENDER_PEARL)),
            new Def("campfire_kit", Material.CAMPFIRE, 1, "§6§l野営セット",
                List.of("§7焚き火と薪をまとめた携帯セット。",
                    "§7右クリックした面に焚き火を設置する。(CD 10秒)"),
                shapeless(Material.CAMPFIRE, Material.OAK_LOG)),
            new Def("harvest_scythe", Material.IRON_HOE, 1, "§a§l収穫の鎌",
                List.of("§7刃を麦に合わせて鍛えた鎌。",
                    "§7右クリックで周囲3x3の実った作物をまとめて収穫する。(CD 4秒)"),
                shaped(new String[] {"IIW", "  S"},
                    Map.of('I', Material.IRON_INGOT, 'W', Material.WHEAT, 'S', Material.STICK))),
            new Def("folding_boat", Material.OAK_BOAT, 1, "§9§l折り畳みボート",
                List.of("§7コンパクトに畳める小型ボート。",
                    "§7右クリックで足元にボートを出して乗り込む。(CD 20秒)"),
                shapeless(Material.OAK_BOAT, Material.OAK_PLANKS)),
            new Def("scaffold_pack", Material.SCAFFOLDING, 4, "§e§l足場の束",
                List.of("§7縄で束ねた足場ブロック4つ。",
                    "§7右クリックで前方へ足場を敷く。(CD 8秒)"),
                shapeless(Material.SCAFFOLDING, Material.STRING, Material.STICK)),
            new Def("light_drop", Material.GLOWSTONE_DUST, 1, "§e§l光のしずく",
                List.of("§7瓶に閉じ込めた光。",
                    "§7右クリックした面に光を灯す。35秒で消える。(CD 8秒)"),
                shapeless(Material.GLOWSTONE_DUST, Material.GLASS_BOTTLE)),

            // ---------------- 戦闘補助 ----------------
            new Def("blow_dart", Material.BAMBOO, 1, "§2§l吹き矢",
                List.of("§7竹筒と毒袋で作った吹き矢。",
                    "§7右クリックで8ブロック先までダーツを飛ばす。毒Iを付与。(CD 8秒)"),
                shapeless(Material.BAMBOO, Material.SLIME_BALL, Material.ARROW)),
            new Def("throw_net", Material.COBWEB, 1, "§7§l投網",
                List.of("§7鉄の重りを編み込んだ網。",
                    "§7右クリックで前方8ブロック・半径4のMOBを鈍足にする。(CD 15秒)"),
                shapeless(Material.STRING, Material.STRING, Material.IRON_BARS)),
            new Def("smoke_bomb", Material.GUNPOWDER, 1, "§8§l煙幕玉",
                List.of("§7炭と火薬を固めた煙幕。1回で消費する。",
                    "§7右クリックで周囲8ブロックのMOBを盲目にする。(CD 20秒)"),
                shapeless(Material.GUNPOWDER, Material.COAL_BLOCK)),
            new Def("spider_lens", Material.SPYGLASS, 1, "§5§l蜘蛛の目鏡",
                List.of("§7蜘蛛の複眼を磨いた目鏡。",
                    "§7右クリックで16ブロック以内のMOBを発光させる。(CD 20秒)"),
                shapeless(Material.SPIDER_EYE, Material.GLASS)),
            new Def("frost_vial", Material.GLASS_BOTTLE, 1, "§b§l氷結の瓶",
                List.of("§7粉末雪を瓶に詰めたもの。1回で消費する。",
                    "§7右クリックで視線先に粉末雪をまき、周囲を鈍足にする。(CD 12秒)"),
                shapeless(Material.POWDER_SNOW_BUCKET, Material.GLASS_BOTTLE)),

            // ---------------- 回復・整備 ----------------
            new Def("field_bandage", Material.WHITE_WOOL, 1, "§f§l応急包帯",
                List.of("§7清潔な羊毛の包帯。",
                    "§7右クリックで体力を4回復する。(CD 25秒)"),
                shapeless(Material.WHITE_WOOL, Material.STRING)),
            new Def("antidote_honey", Material.HONEY_BOTTLE, 1, "§6§l毒消しの蜂蜜",
                List.of("§7蜂蜜と乳を混ぜた解毒剤。1回で消費する。",
                    "§7右クリックで毒と衰弱を消し、再生Iを5秒得る。(CD 30秒)"),
                shapeless(Material.HONEY_BOTTLE, Material.MILK_BUCKET)),
            new Def("repair_hammer", Material.IRON_AXE, 1, "§7§l名工の金槌",
                List.of("§7叩いて直すための鉄鎚。",
                    "§7右クリックで装備4部位の耐久を15ずつ回復する。(CD 60秒)"),
                shaped(new String[] {"IBI", " S "},
                    Map.of('I', Material.IRON_INGOT, 'B', Material.IRON_BLOCK, 'S', Material.STICK))),

            // ---------------- 既存の特殊クラフト（高度な作業台へ移行） ----------------
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
                    Map.of('D', Material.DISPENSER, 'C', Material.CROSSBOW, 'B', Material.BARREL)))
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
