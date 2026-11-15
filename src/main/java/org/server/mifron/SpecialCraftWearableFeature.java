package org.server.mifron;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 特殊クラフト品のうち「装備/所持している間だけ効果を発揮する」ウェアラブル系をまとめて扱う。
 *
 * <ul>
 *   <li>fireproof_cloak: 装着中、炎/火/溶岩ダメージを35%軽減</li>
 *   <li>safety_harness: 装着中、落下ダメージを40%軽減</li>
 *   <li>artisan_apron: 装着中、Haste I を維持</li>
 *   <li>deep_diver_helm: 装着中、水中で Water Breathing を維持</li>
 *   <li>wind_feather: メイン/オフハンドに持っている間、Slow Falling を維持</li>
 * </ul>
 *
 * 効果は装着者(自分)のみに作用し、他プレイヤーには干渉しない。
 */
final class SpecialCraftWearableFeature implements Listener {
    /** 耐火の外套。 */
    static final String FIREPROOF_CLOAK = "fireproof_cloak";
    /** 登山の命綱。 */
    static final String SAFETY_HARNESS = "safety_harness";
    /** 職人の前掛け。 */
    static final String ARTISAN_APRON = "artisan_apron";
    /** 深海の兜。 */
    static final String DEEP_DIVER_HELM = "deep_diver_helm";
    /** 風読みの羽根。 */
    static final String WIND_FEATHER = "wind_feather";

    /** 炎/火/溶岩ダメージの残存倍率(35%軽減)。 */
    private static final double FIRE_DAMAGE_MULTIPLIER = 0.65D;
    /** 落下ダメージの残存倍率(40%軽減)。 */
    private static final double FALL_DAMAGE_MULTIPLIER = 0.60D;
    /** ポーション効果の再付与間隔(1秒)。 */
    private static final long EFFECT_PERIOD_TICKS = 20L;
    /** 1回の付与で持たせる長さ(60tick=3秒)。切れ目でちらつかないよう効果より長めに配る。 */
    private static final int EFFECT_DURATION_TICKS = 60;

    private final Mifron plugin;
    private final SpecialCraftKit kit;
    /** 効果維持タスクを起動済みか。プラグイン有効前にタスクを開始すると例外になるため join 起点で遅延起動する。 */
    private boolean effectTaskStarted;

    SpecialCraftWearableFeature(Mifron plugin, SpecialCraftKit kit) {
        this.plugin = plugin;
        this.kit = kit;
    }

    // ------------------------------------------------------------------
    // 純粋関数(テスト用・Bukkit状態に依存しない)
    // ------------------------------------------------------------------

    /** 炎/火/溶岩ダメージの残存倍率。0.65 = 35%軽減。 */
    static double fireDamageMultiplier() {
        return FIRE_DAMAGE_MULTIPLIER;
    }

    /** 落下ダメージの残存倍率。0.60 = 40%軽減。 */
    static double fallDamageMultiplier() {
        return FALL_DAMAGE_MULTIPLIER;
    }

    /** 炎/火/溶岩系のダメージ要因か。 */
    static boolean isFireDamage(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return false;
        }
        return cause == EntityDamageEvent.DamageCause.FIRE
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA
            || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    /** 落下ダメージの要因か。 */
    static boolean isFallDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FALL;
    }

    /**
     * ダメージ要因と装着状況から残存倍率を求める。軽減なしは 1.0。
     *
     * @param cause ダメージ要因
     * @param wearingFireproofCloak 耐火の外套を装着しているか
     * @param wearingSafetyHarness 登山の命綱を装着しているか
     * @return 掛けるべき倍率(1.0 で軽減なし)
     */
    static double damageMultiplier(EntityDamageEvent.DamageCause cause,
                                   boolean wearingFireproofCloak,
                                   boolean wearingSafetyHarness) {
        double multiplier = 1.0D;
        if (wearingFireproofCloak && isFireDamage(cause)) {
            multiplier *= FIRE_DAMAGE_MULTIPLIER;
        }
        if (wearingSafetyHarness && isFallDamage(cause)) {
            multiplier *= FALL_DAMAGE_MULTIPLIER;
        }
        return multiplier;
    }

    // ------------------------------------------------------------------
    // ダメージ軽減
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double multiplier = damageMultiplier(
            event.getCause(),
            this.kit.isWearing(player, FIREPROOF_CLOAK),
            this.kit.isWearing(player, SAFETY_HARNESS));
        if (multiplier >= 1.0D) {
            return;
        }
        event.setDamage(event.getDamage() * multiplier);
    }

    // ------------------------------------------------------------------
    // ポーション効果の維持
    // ------------------------------------------------------------------

    /** 初回参加時にだけ効果維持タスクを開始する(2回目以降は何もしない)。 */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.startEffectTask();
    }

    private void startEffectTask() {
        if (this.effectTaskStarted) {
            return;
        }
        if (this.plugin == null || !this.plugin.isEnabled()) {
            // 有効化前はスケジューラに載せられない。次の join で改めて試す。
            return;
        }
        this.effectTaskStarted = true;
        this.plugin.getServer().getScheduler()
            .runTaskTimer(this.plugin, this::tickEffects, EFFECT_PERIOD_TICKS, EFFECT_PERIOD_TICKS);
    }

    /** 1秒周期で、装着/所持に応じた効果を3秒分ずつ付け直す。 */
    private void tickEffects() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (this.kit.isWearing(player, ARTISAN_APRON)) {
                this.maintainEffect(player, PotionEffectType.HASTE);
            }
            if (this.kit.isWearing(player, DEEP_DIVER_HELM) && player.isInWater()) {
                this.maintainEffect(player, PotionEffectType.WATER_BREATHING);
            }
            if (this.kit.isHeld(player, WIND_FEATHER)) {
                this.maintainEffect(player, PotionEffectType.SLOW_FALLING);
            }
        }
    }

    /**
     * 効果を3秒分付け直す。ただし外部由来のより強い/より長い効果が乗っている場合は上書きしない。
     */
    private void maintainEffect(Player player, PotionEffectType type) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() > EFFECT_DURATION_TICKS)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION_TICKS, 0, true, false));
    }
}
