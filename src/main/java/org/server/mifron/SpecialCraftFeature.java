package org.server.mifron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * レリックとは別枠の「特殊クラフト」装備。
 *
 * <p>どちらも通常のバニラ装備と同じ攻撃力・耐久で、作業台でのクラフトでのみ入手できる。
 * <ul>
 *   <li>狙撃弓 — 望遠鏡 + 弓（不定形）。通常の弓と同じ威力だが、2倍の時間をかけて
 *       引き絞ると弾速・威力が最大2倍まで伸びる。</li>
 *   <li>連射式弩弓 — 上から発射装置 / クロスボウ / 樽（縦3マス）。攻撃力と耐久は通常の
 *       クロスボウと同じで、右クリックを押し続けると連射できる。</li>
 *   <li>暗視鏡 — 暗視ポーション + 望遠鏡。高度な作業台でのみ作れる。
 *       望遠鏡を覗いている間だけ暗視を得る（旧レリックから移行した装備）。</li>
 * </ul>
 */
public final class SpecialCraftFeature implements Listener {

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
    private final Map<UUID, RepeaterState> repeaters = new HashMap<>();
    /** 暗視スコープの暗視ループ（プレイヤーごと）。 */
    private final Map<UUID, BukkitTask> visionTasks = new HashMap<>();

    public SpecialCraftFeature(Mifron plugin) {
        this.plugin = plugin;
        this.craftKey = new NamespacedKey(plugin, "special_craft");
    }

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
            item.setItemMeta(meta);
        }
        return item;
    }

    /** アイテムがどの特殊クラフトか（違えば null）。 */
    public String craftId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(this.craftKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    // 狙撃弓: 2倍引き絞り
    // ------------------------------------------------------------------

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        String id = this.craftId(bow);
        if (REPEATING_CROSSBOW_ID.equals(id)) {
            // 連射式弩弓は独自に発射するので、バニラの装填射撃は止める。
            event.setCancelled(true);
            return;
        }
        if (!SNIPER_BOW_ID.equals(id)) return;

        Long start = this.sniperDrawStart.remove(player.getUniqueId());
        int drawTicks = start == null ? VANILLA_FULL_DRAW_TICKS
            : (int) Math.max(0L, (System.currentTimeMillis() - start) / 50L);

        // 引きが浅い（バニラのフルチャージ未満）なら通常の弓として扱う。
        if (event.getForce() < 0.99f) {
            player.sendActionBar("§2引き絞り 1.00x");
            return;
        }

        // バニラのフルチャージを1.0倍とし、2倍まで伸ばす。
        double ratio = Math.max(1.0, sniperRatio(drawTicks));

        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setVelocity(arrow.getVelocity().multiply(ratio));
            // 速度に応じて威力も伸びるよう、ダメージ倍率を明示する。
            arrow.setDamage(Math.max(0.1, arrow.getDamage() * ratio));
            arrow.setCritical(ratio >= 1.5);
        }
        player.sendActionBar("§2引き絞り " + String.format("%.2f", ratio) + "x");
    }

    // ------------------------------------------------------------------
    // 連射式弩弓: 右クリック長押しで連射
    // ------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

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

        if (SNIPER_BOW_ID.equals(id)) {
            this.sniperDrawStart.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }
        if (!REPEATING_CROSSBOW_ID.equals(id)) return;

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
        state.lastFireMillis = System.currentTimeMillis();
        // 通常のクロスボウと同じく1発ごとに耐久を1消費する。
        if (held.getItemMeta() instanceof Damageable damageable) {
            int next = damageable.getDamage() + 1;
            int max = held.getType().getMaxDurability();
            if (max > 0 && next >= max) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
                this.stopRepeater(player);
                return;
            }
            damageable.setDamage(next);
            held.setItemMeta(damageable);
        }
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.7f, 1.2f);
    }

    /** 矢を1本消費する。クリエイティブなら消費しない。 */
    private boolean consumeArrow(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
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
}
