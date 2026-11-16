package org.server.mifron;

<<<<<<< HEAD
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
=======
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
<<<<<<< HEAD
=======
import org.bukkit.event.player.PlayerJoinEvent;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
<<<<<<< HEAD
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
=======
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637

/**
 * レリックとは別枠の「特殊クラフト」装備。
 *
<<<<<<< HEAD
 * <p>どちらも普通のバニラ装備と同じ攻撃力・耐久で、クラフトでのみ入手できる。
 * <ul>
 *   <li>狙撃弓 — 望遠鏡 + 弓。通常の弓と同じ威力だが、2倍の時間をかけて引き絞ると
 *       弾速・威力が最大2倍まで伸びる。</li>
 *   <li>連射式弩弓 — 上から発射装置 / クロスボウ / 樽。攻撃力と耐久は通常のクロスボウと同じで、
 *       右クリックを押し続けると連射できる。</li>
=======
 * <p>どちらも通常のバニラ装備と同じ攻撃力・耐久で、作業台でのクラフトでのみ入手できる。
 * <ul>
 *   <li>狙撃弓 — 望遠鏡 + 弓（不定形）。通常の弓と同じ威力だが、2倍の時間をかけて
 *       引き絞ると弾速・威力が最大2倍まで伸びる。</li>
 *   <li>連射式弩弓 — 上から発射装置 / クロスボウ / 樽（縦3マス）。攻撃力と耐久は通常の
 *       クロスボウと同じで、右クリックを押し続けると連射できる。</li>
 *   <li>暗視鏡 — 暗視ポーション + 望遠鏡。高度な作業台でのみ作れる。
 *       望遠鏡を覗いている間だけ暗視を得る（旧レリックから移行した装備）。</li>
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
 * </ul>
 */
public final class SpecialCraftFeature implements Listener {

<<<<<<< HEAD
    /** PDC key holding which special craft an item is. */
    public static final String SNIPER_BOW_ID = "sniper_bow";
    public static final String REPEATING_CROSSBOW_ID = "repeating_crossbow";

    /** バニラの弓がフルチャージになるまでの所要tick。 */
    private static final int VANILLA_FULL_DRAW_TICKS = 20;
    /** 狙撃弓の引き絞り上限（バニラ比）。 */
    private static final double SNIPER_MAX_MULTIPLIER = 2.0;
    /** 連射式弩弓の連射間隔（tick）。 */
    private static final int REPEATER_INTERVAL_TICKS = 6;
=======
    /** PDC に保存する特殊クラフト種別。 */
    public static final String SNIPER_BOW_ID = "sniper_bow";
    public static final String REPEATING_CROSSBOW_ID = "repeating_crossbow";
    /** 暗視スコープ。高度な作業台で「暗視ポーション + 望遠鏡」から作る。 */
    public static final String NIGHT_VISION_SCOPE_ID = "night_vision_scope";

    /** 旧「暗視鏡」レリックの PDC 値。移行の判定にだけ使う。 */
    private static final String LEGACY_VISION_RELIC_ID = "vision";

    /** 高度な作業台のレシピ判定に使うスロット種別（純粋ルール用）。 */
    static final int SLOT_EMPTY = 0;
    static final int SLOT_SPYGLASS = 1;
    static final int SLOT_NIGHT_VISION_POTION = 2;

    /** 暗視スコープの暗視を付け直す間隔（tick）。 */
    private static final int SCOPED_VISION_TICK_PERIOD = 4;
    /** 1回の付与で持たせる暗視の長さ（tick）。切れ目でちらつかない長さにする。 */
    private static final int SCOPED_VISION_DURATION_TICKS = 260;

    /**
     * 暗視スコープのレシピ判定（純粋ルール）。
     * 望遠鏡1個と暗視ポーション1個だけを並べたときだけ成立する。
     */
    static boolean matchesNightVisionScope(int[] slotKinds) {
        if (slotKinds == null || slotKinds.length != 9) return false;
        int spyglass = 0;
        int potion = 0;
        for (int kind : slotKinds) {
            if (kind == SLOT_SPYGLASS) {
                spyglass++;
            } else if (kind == SLOT_NIGHT_VISION_POTION) {
                potion++;
            } else if (kind != SLOT_EMPTY) {
                return false;
            }
        }
        return spyglass == 1 && potion == 1;
    }

    /** バニラの弓がフルチャージになるまでの所要tick。 */
    static final int VANILLA_FULL_DRAW_TICKS = 20;
    /** 狙撃弓が到達できる引き絞り倍率の上限。 */
    static final double SNIPER_MAX_MULTIPLIER = 2.0;
    /** 連射式弩弓の連射間隔（tick）。 */
    static final int REPEATER_INTERVAL_TICKS = 6;
    /** 連射監視タスクの周期（tick）。 */
    private static final int REPEATER_TICK_PERIOD = 2;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637

    /**
     * 狙撃弓の引き絞り倍率。バニラのフルチャージ（20tick）を 1.0 とし、
     * 40tick で上限 2.0 に達する。引きが浅すぎる場合は 0.1 に丸める。
     */
    static double sniperRatio(int drawTicks) {
        double ratio = Math.min(SNIPER_MAX_MULTIPLIER,
            Math.max(0, drawTicks) / (double) VANILLA_FULL_DRAW_TICKS);
        return Math.max(0.1, ratio);
    }

    private final Mifron plugin;
    private final NamespacedKey craftKey;
    private final Map<UUID, Long> sniperDrawStart = new HashMap<>();
<<<<<<< HEAD
    private final Map<UUID, Integer> repeaterTaskId = new HashMap<>();
=======
    private final Map<UUID, RepeaterState> repeaters = new HashMap<>();
    /** 暗視スコープの暗視ループ（プレイヤーごと）。 */
    private final Map<UUID, BukkitTask> visionTasks = new HashMap<>();
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637

    public SpecialCraftFeature(Mifron plugin) {
        this.plugin = plugin;
        this.craftKey = new NamespacedKey(plugin, "special_craft");
    }

<<<<<<< HEAD
    /** レシピを登録する。onEnable から一度だけ呼ぶ。 */
    public void registerRecipes() {
        try {
            Bukkit.removeRecipe(new NamespacedKey(this.plugin, SNIPER_BOW_ID));
            Bukkit.removeRecipe(new NamespacedKey(this.plugin, REPEATING_CROSSBOW_ID));
        } catch (Throwable ignored) {
            // 未登録なら何もしない
        }

        ShapelessRecipe sniper = new ShapelessRecipe(
            new NamespacedKey(this.plugin, SNIPER_BOW_ID), this.sniperBow());
        sniper.addIngredient(Material.SPYGLASS);
        sniper.addIngredient(Material.BOW);
        Bukkit.addRecipe(sniper);

        ShapedRecipe repeater = new ShapedRecipe(
            new NamespacedKey(this.plugin, REPEATING_CROSSBOW_ID), this.repeatingCrossbow());
        repeater.shape("D", "C", "B");
        repeater.setIngredient('D', Material.DISPENSER);
        repeater.setIngredient('C', Material.CROSSBOW);
        repeater.setIngredient('B', Material.BARREL);
        Bukkit.addRecipe(repeater);

        this.plugin.getLogger().info("Special craft recipes registered: 狙撃弓 / 連射式弩弓");
    }

    private ItemStack sniperBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2§l狙撃弓");
            meta.setLore(java.util.List.of(
                "§7望遠鏡と弓を組み合わせた長弓。",
                "§7威力は通常の弓と同じだが、",
                "§72倍まで引き絞ると弾速と威力が増す。",
                "§8特殊クラフト"));
            meta.getPersistentDataContainer().set(this.craftKey, PersistentDataType.STRING, SNIPER_BOW_ID);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack repeatingCrossbow() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8§l連射式弩弓");
            meta.setLore(java.util.List.of(
                "§7発射装置・クロスボウ・樽を組み合わせた連弩。",
                "§7攻撃力と耐久は通常のクロスボウと同じ。",
                "§7右クリック長押しで連射する。",
                "§8特殊クラフト"));
            meta.getPersistentDataContainer().set(this.craftKey, PersistentDataType.STRING,
                REPEATING_CROSSBOW_ID);
=======
    /** 狙撃弓のアイテム（定義は SpecialCraftCatalog が持つ）。 */
    public ItemStack sniperBow() {
        ItemStack created = this.plugin.specialCraftKit.create(SNIPER_BOW_ID);
        return created == null ? new ItemStack(Material.BOW) : created;
    }

    /** 連射式弩弓のアイテム（定義は SpecialCraftCatalog が持つ）。 */
    public ItemStack repeatingCrossbow() {
        ItemStack created = this.plugin.specialCraftKit.create(REPEATING_CROSSBOW_ID);
        return created == null ? new ItemStack(Material.CROSSBOW) : created;
    }

    /** 暗視スコープ（特殊クラフト品）。望遠鏡を覗いている間だけ暗視を得る。 */
    public ItemStack nightVisionScope() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8§l暗視鏡");
            meta.setLore(List.of(
                "§7望遠鏡に暗視の力を込めたスコープ。",
                "§7覗いている間だけ暗視を得る。",
                "§8特殊クラフト"));
            meta.getPersistentDataContainer().set(this.craftKey, PersistentDataType.STRING,
                NIGHT_VISION_SCOPE_ID);
            meta.setItemModel(new org.bukkit.NamespacedKey(this.plugin, "special/night_vision_scope"));
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
            item.setItemMeta(meta);
        }
        return item;
    }

<<<<<<< HEAD
    /** アイテムがどの特殊クラフトか（違えば null）。 */    public String craftId(ItemStack item) {
=======
    /** アイテムがどの特殊クラフトか（違えば null）。 */
    public String craftId(ItemStack item) {
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(this.craftKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
<<<<<<< HEAD
    // 狙撃弓
=======
    // 管理者向け入手コマンド
    // ------------------------------------------------------------------

    /** SpecialCraftCatalog に定義された全ID。 */
    public static List<String> catalogIds() {
        List<String> ids = new ArrayList<>();
        for (SpecialCraftCatalog.Def def : SpecialCraftCatalog.all()) {
            ids.add(def.id());
        }
        return ids;
    }

    /** カタログ外の特殊クラフト品（暗視鏡・高度な作業台・鋼鉄かまど）。 */
    private static final List<String> EXTRA_IDS = List.of(
        NIGHT_VISION_SCOPE_ID,
        SpecialCraftingFeature.ADVANCED_TABLE_ID,
        SpecialCraftingFeature.STEEL_FURNACE_ID);

    /** 管理者向け入手コマンドで扱う全ID（カタログ22種＋カタログ外3種）。 */
    public static List<String> grantableIds() {
        List<String> ids = catalogIds();
        for (String id : EXTRA_IDS) {
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    /**
     * 指定IDの特殊クラフト品を生成する。カタログ品も、カタログ外の暗視鏡・
     * 高度な作業台・鋼鉄かまども、実際にクラフトで得られるものと同一の
     * アイテムを返す。未知のIDなら null。
     */
    public ItemStack createGrantable(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        ItemStack fromCatalog = this.plugin.specialCraftKit.create(key);
        if (fromCatalog != null) return fromCatalog;
        return switch (key) {
            case NIGHT_VISION_SCOPE_ID -> this.nightVisionScope();
            case SpecialCraftingFeature.ADVANCED_TABLE_ID ->
                this.plugin.specialCraftingFeature.createAdvancedCraftingTable();
            case SpecialCraftingFeature.STEEL_FURNACE_ID ->
                this.plugin.specialCraftingFeature.createSteelFurnace();
            default -> null;
        };
    }

    /** アイテムの表示名。名前が無ければ fallback を返す。 */
    private static String displayNameOf(ItemStack item, String fallback) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return fallback;
    }

    /**
     * /mf specialcraft &lt;id|all&gt; [player] を処理する。
     * 個別IDでも all でも、特殊クラフトとして入手できる全種を付与する。
     */
    public void handleCommand(CommandSender sender, String[] args) {
        if (sender == null) return;
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)
            && !sender.isOp() && !sender.hasPermission("mifron.admin")
            && !sender.hasPermission("mifron.specialcraft.admin")) {
            sender.sendMessage("§cOP権限が必要です。");
            return;
        }
        if (args == null || args.length < 2) {
            sender.sendMessage("§e/mf specialcraft <id|all> [player]");
            sender.sendMessage("§7IDs: " + String.join(", ", grantableIds()));
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cPlayer not found.");
            return;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        if ("all".equals(raw)) {
            int given = 0;
            for (String id : grantableIds()) {
                ItemStack item = createGrantable(id);
                if (item == null) continue;
                giveTo(target, item);
                given++;
            }
            target.sendMessage("§a全特殊クラフトを入手しました: " + given + "種");
            if (sender != target) {
                sender.sendMessage("§a" + target.getName() + " に全特殊クラフトを付与しました。");
            }
            return;
        }
        ItemStack item = createGrantable(raw);
        if (item == null) {
            sender.sendMessage("§cUnknown special craft item: " + args[1]);
            sender.sendMessage("§7IDs: " + String.join(", ", grantableIds()));
            return;
        }
        SpecialCraftCatalog.Def def = SpecialCraftCatalog.byId(raw);
        String label = def != null ? def.displayName() : displayNameOf(item, raw);
        giveTo(target, item);
        target.sendMessage("§a特殊クラフトを入手しました: " + label);
        if (sender != target) {
            sender.sendMessage("§a" + target.getName() + " に " + raw + " を付与しました。");
        }
    }

    private static void giveTo(Player target, ItemStack item) {
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    /** Tab completion for /mf specialcraft <id|all> [player]. */
    public List<String> tabComplete(String[] args) {
        if (args == null) return List.of();
        if (args.length == 2) {
            List<String> values = new ArrayList<>();
            values.add("all");
            values.addAll(grantableIds());
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matched = new ArrayList<>();
            for (String value : values) if (value.startsWith(prefix)) matched.add(value);
            return matched;
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            List<String> matched = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matched.add(online.getName());
                }
            }
            return matched;
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // 狙撃弓: 2倍引き絞り
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
    // ------------------------------------------------------------------

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
<<<<<<< HEAD
        if (!SNIPER_BOW_ID.equals(this.craftId(bow))) return;
=======
        String id = this.craftId(bow);
        if (REPEATING_CROSSBOW_ID.equals(id)) {
            // 連射式弩弓は独自に発射するので、バニラの装填射撃は止める。
            event.setCancelled(true);
            return;
        }
        if (!SNIPER_BOW_ID.equals(id)) return;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637

        Long start = this.sniperDrawStart.remove(player.getUniqueId());
        int drawTicks = start == null ? VANILLA_FULL_DRAW_TICKS
            : (int) Math.max(0L, (System.currentTimeMillis() - start) / 50L);

<<<<<<< HEAD
        // バニラのフルチャージを1.0倍とし、2倍まで伸ばす。
        double ratio = sniperRatio(drawTicks);

        if (event.getProjectile() instanceof AbstractArrow arrow) {
            Vector velocity = arrow.getVelocity().multiply(ratio);
            arrow.setVelocity(velocity);
=======
        // 引きが浅い（バニラのフルチャージ未満）なら通常の弓として扱う。
        if (event.getForce() < 0.99f) {
            player.sendActionBar("§2引き絞り 1.00x");
            return;
        }

        // バニラのフルチャージを1.0倍とし、2倍まで伸ばす。
        double ratio = Math.max(1.0, sniperRatio(drawTicks));

        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setVelocity(arrow.getVelocity().multiply(ratio));
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
            // 速度に応じて威力も伸びるよう、ダメージ倍率を明示する。
            arrow.setDamage(Math.max(0.1, arrow.getDamage() * ratio));
            arrow.setCritical(ratio >= 1.5);
        }
        player.sendActionBar("§2引き絞り " + String.format("%.2f", ratio) + "x");
    }

    // ------------------------------------------------------------------
<<<<<<< HEAD
    // 連射式弩弓
=======
    // 連射式弩弓: 右クリック長押しで連射
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
    // ------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
<<<<<<< HEAD
        String id = this.craftId(held);
        if (id == null) return;
=======

        // 旧「暗視鏡」レリックはここで見つけ次第、新しい特殊クラフト品へ置き換える。
        if (this.isLegacyVisionRelic(held)) {
            held = this.nightVisionScope();
            player.getInventory().setItemInMainHand(held);
        }

        String id = this.craftId(held);
        if (id == null) {
            // オフハンドのスコープでも覗けるようにしておく。
            if (this.isNightVisionScope(player.getInventory().getItemInOffHand())) {
                this.startScopedVision(player);
            }
            return;
        }

        if (NIGHT_VISION_SCOPE_ID.equals(id)) {
            // 覗き始め。終了はタスク側が「今使っているアイテム」を見て判定する。
            this.startScopedVision(player);
            return;
        }
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637

        if (SNIPER_BOW_ID.equals(id)) {
            this.sniperDrawStart.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (!REPEATING_CROSSBOW_ID.equals(id)) return;

<<<<<<< HEAD
        // 連射: 押した瞬間に撃ち始め、離す/持替えで止まる。
        event.setCancelled(true);
        if (this.repeaterTaskId.containsKey(player.getUniqueId())) return;
        int taskId = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin,
            () -> this.fireRepeater(player), 0L, REPEATER_INTERVAL_TICKS).getTaskId();
        this.repeaterTaskId.put(player.getUniqueId(), taskId);
    }

    private void fireRepeater(Player player) {
        if (player == null || !player.isOnline()) {
=======
        // 右クリックを押した瞬間から撃ち始め、離す/持替えで止まる。
        this.startRepeater(player);
    }

    /** 連射を開始する。既に連射中なら何もしない。 */
    private void startRepeater(Player player) {
        UUID uuid = player.getUniqueId();
        if (this.repeaters.containsKey(uuid)) return;
        RepeaterState state = new RepeaterState();
        state.lastFireMillis = 0L;
        this.repeaters.put(uuid, state);
        this.fireRepeater(player, state);
        state.task = this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, () -> this.tickRepeater(player, state), 0L, REPEATER_TICK_PERIOD);
    }

    private void tickRepeater(Player player, RepeaterState state) {
        if (!player.isOnline()) {
            this.stopRepeater(player);
            return;
        }
        // 右クリックを離したら止める（クロスボウの使用中判定を利用）。
        if (!player.isHandRaised()) {
            this.stopRepeater(player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!REPEATING_CROSSBOW_ID.equals(this.craftId(held))) {
            this.stopRepeater(player);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastFireMillis < REPEATER_INTERVAL_TICKS * 50L) return;
        this.fireRepeater(player, state);
    }

    private void fireRepeater(Player player, RepeaterState state) {
        if (!player.isOnline()) {
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
            this.stopRepeater(player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!REPEATING_CROSSBOW_ID.equals(this.craftId(held))) {
            this.stopRepeater(player);
            return;
        }
        if (!this.consumeArrow(player)) {
            this.stopRepeater(player);
            return;
        }
        Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(3.0));
        arrow.setCritical(true);
<<<<<<< HEAD
        // 通常のクロスボウと同じく1発ごとに耐久を1消費する。
        ItemMeta meta = held.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
=======
        state.lastFireMillis = System.currentTimeMillis();
        // 通常のクロスボウと同じく1発ごとに耐久を1消費する。
        if (held.getItemMeta() instanceof Damageable damageable) {
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
            int next = damageable.getDamage() + 1;
            int max = held.getType().getMaxDurability();
            if (max > 0 && next >= max) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
<<<<<<< HEAD
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
=======
                player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
                this.stopRepeater(player);
                return;
            }
            damageable.setDamage(next);
<<<<<<< HEAD
            held.setItemMeta(meta);
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_SHOOT, 0.7f, 1.2f);
=======
            held.setItemMeta(damageable);
        }
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.7f, 1.2f);
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
    }

    /** 矢を1本消費する。クリエイティブなら消費しない。 */
    private boolean consumeArrow(Player player) {
<<<<<<< HEAD
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return true;
=======
        if (player.getGameMode() == GameMode.CREATIVE) return true;
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.ARROW) {
                stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private void stopRepeater(Player player) {
        if (player == null) return;
<<<<<<< HEAD
        Integer taskId = this.repeaterTaskId.remove(player.getUniqueId());
        if (taskId != null) {
            this.plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.stopRepeater(event.getPlayer());
        this.sniperDrawStart.remove(event.getPlayer().getUniqueId());
    }
=======
        RepeaterState state = this.repeaters.remove(player.getUniqueId());
        if (state != null && state.task != null) {
            state.task.cancel();
            state.task = null;
        }
    }

    // ------------------------------------------------------------------
    // 暗視スコープ: 覗いている間だけ暗視
    // ------------------------------------------------------------------

    /** 暗視ループを開始する（冪等）。 */
    private void startScopedVision(Player player) {
        UUID uuid = player.getUniqueId();
        if (this.visionTasks.containsKey(uuid)) return;
        this.visionTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline()) {
                this.stopScopedVision(uuid);
                return;
            }
            if (this.isNightVisionScope(current.getItemInUse())) {
                current.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    SCOPED_VISION_DURATION_TICKS, 0, true, false));
            } else {
                current.removePotionEffect(PotionEffectType.NIGHT_VISION);
                this.stopScopedVision(uuid);
            }
        }, 0L, SCOPED_VISION_TICK_PERIOD));
    }

    private void stopScopedVision(UUID uuid) {
        BukkitTask task = this.visionTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private boolean isNightVisionScope(ItemStack item) {
        return NIGHT_VISION_SCOPE_ID.equals(this.craftId(item));
    }

    // ------------------------------------------------------------------
    // 旧レリック「暗視鏡」の移行
    // ------------------------------------------------------------------

    /**
     * 旧レリック（PDC relic_id = vision）はレリック定義を削除したので、
     * インベントリ確認時に新しい特殊クラフト品へ置き換える。
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.migrateLegacyVisionRelics(event.getPlayer());
    }

    private void migrateLegacyVisionRelics(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        this.migrateLegacyVisionRelics(inventory.getContents(), inventory);
        org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
        this.migrateLegacyVisionRelics(enderChest.getContents(), enderChest);
    }

    private void migrateLegacyVisionRelics(ItemStack[] contents,
        org.bukkit.inventory.Inventory owner) {
        if (contents == null) return;
        for (int slot = 0; slot < contents.length; slot++) {
            if (this.isLegacyVisionRelic(contents[slot])) {
                owner.setItem(slot, this.nightVisionScope());
            }
        }
    }

    /** 旧「暗視鏡」レリックかどうか（PDC relic_id = vision）。 */
    private boolean isLegacyVisionRelic(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String relicId = meta.getPersistentDataContainer().get(
            new NamespacedKey(this.plugin, "relic_id"), PersistentDataType.STRING);
        return LEGACY_VISION_RELIC_ID.equals(relicId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.stopRepeater(event.getPlayer());
        this.stopScopedVision(event.getPlayer().getUniqueId());
        this.sniperDrawStart.remove(event.getPlayer().getUniqueId());
    }

    /** 1人分の連射状態。 */
    private static final class RepeaterState {
        private long lastFireMillis;
        private BukkitTask task;
    }
>>>>>>> e55d970dca986acaa065acd3ac9118d54a716637
}
