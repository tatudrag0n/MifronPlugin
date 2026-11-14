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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
 * </ul>
 */
public final class SpecialCraftFeature implements Listener {

    /** PDC に保存する特殊クラフト種別。 */
    public static final String SNIPER_BOW_ID = "sniper_bow";
    public static final String REPEATING_CROSSBOW_ID = "repeating_crossbow";

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

    public SpecialCraftFeature(Mifron plugin) {
        this.plugin = plugin;
        this.craftKey = new NamespacedKey(plugin, "special_craft");
    }

    /** レシピを登録する。起動時に一度だけ呼ぶ。 */
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

    /** 狙撃弓のアイテム。 */
    public ItemStack sniperBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2§l狙撃弓");
            meta.setLore(List.of(
                "§7望遠鏡と弓を組み合わせた長弓。",
                "§7威力は通常の弓と同じだが、",
                "§72倍まで引き絞ると弾速と威力が増す。",
                "§8特殊クラフト"));
            meta.getPersistentDataContainer().set(this.craftKey, PersistentDataType.STRING, SNIPER_BOW_ID);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 連射式弩弓のアイテム。 */
    public ItemStack repeatingCrossbow() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8§l連射式弩弓");
            meta.setLore(List.of(
                "§7発射装置・クロスボウ・樽を組み合わせた連弩。",
                "§7攻撃力と耐久は通常のクロスボウと同じ。",
                "§7右クリック長押しで連射する。",
                "§8特殊クラフト"));
            meta.getPersistentDataContainer().set(this.craftKey, PersistentDataType.STRING,
                REPEATING_CROSSBOW_ID);
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
        String id = this.craftId(held);
        if (id == null) return;

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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.stopRepeater(event.getPlayer());
        this.sniperDrawStart.remove(event.getPlayer().getUniqueId());
    }

    /** 1人分の連射状態。 */
    private static final class RepeaterState {
        private long lastFireMillis;
        private BukkitTask task;
    }
}
