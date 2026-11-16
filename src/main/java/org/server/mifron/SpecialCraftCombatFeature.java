package org.server.mifron;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 特殊クラフトの戦闘補助（高度な作業台専用）。
 *
 * <p>投網: 前方8ブロック・横4ブロック以内のMOBを鈍足にする。
 * プレイヤーには付与しない（PvPの公平性のため）。
 */
final class SpecialCraftCombatFeature implements Listener {

    private static final String THROW_NET = "throw_net";
    private static final int THROW_NET_RANGE = 8;
    /** 投網の横方向の広がり（前方8ブロック・横4ブロック以内）。 */
    private static final double THROW_NET_SPREAD = 4.0;
    private static final int THROW_NET_COOLDOWN = 15;
    private static final int THROW_NET_SLOW_TICKS = 80;

    private final Mifron plugin;
    private final SpecialCraftKit kit;

    SpecialCraftCombatFeature(Mifron plugin, SpecialCraftKit kit) {
        this.plugin = plugin;
        this.kit = kit;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        String id = this.kit.heldId(player);
        if (!THROW_NET.equals(id)) {
            return;
        }
        // チェスト・ドアなどは通常操作を優先する。
        if (SpecialCraftKit.opensOwnUi(event)) {
            return;
        }
        event.setCancelled(true);
        this.throwNet(player);
    }

    /** 投網: 前方8ブロック以内（視線方向）のMOBに鈍足III 4秒。耐久1消費（CD 15秒）。 */
    private void throwNet(Player player) {
        if (!this.kit.tryUse(player, THROW_NET, THROW_NET_COOLDOWN)) {
            return;
        }
        this.kit.damageHeld(player, 1);
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(),
                THROW_NET_RANGE, THROW_NET_RANGE, THROW_NET_RANGE)) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (!withinNetReach(eye, look, living.getLocation(), THROW_NET_RANGE, THROW_NET_SPREAD)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, THROW_NET_SLOW_TICKS, 2, true, false));
        }
    }

    /**
     * target が eye から見て look 方向の前方（内積が正）かつ maxDistance 以内にあるか。
     * ワールドが違う場合や引数が null の場合は false。
     */
    static boolean inFront(Location eye, Vector look, Location target, double maxDistance) {
        if (eye == null || look == null || target == null) {
            return false;
        }
        if (eye.getWorld() == null || !eye.getWorld().equals(target.getWorld())) {
            return false;
        }
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) {
            return false;
        }
        return look.getX() * dx + look.getY() * dy + look.getZ() * dz > 0.0;
    }

    /**
     * 投網の到達判定: eye から見て look 方向の前方 maxDistance 以内、かつ
     * 視線からの横ずれが maxLateral 以内にあるか（＝細長い範囲に網を投げる）。
     */
    static boolean withinNetReach(Location eye, Vector look, Location target,
        double maxDistance, double maxLateral) {
        if (!inFront(eye, look, target, maxDistance)) {
            return false;
        }
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        double forward = look.getX() * dx + look.getY() * dy + look.getZ() * dz;
        double lateralSquared = dx * dx + dy * dy + dz * dz - forward * forward;
        return lateralSquared <= maxLateral * maxLateral;
    }

    /**
     * target が center から maxDistance 以内にあるか。ワールドが違う場合や引数が null の場合は false。
     */
    static boolean withinRadius(Location center, Location target, double maxDistance) {
        if (center == null || target == null) {
            return false;
        }
        if (center.getWorld() == null || !center.getWorld().equals(target.getWorld())) {
            return false;
        }
        return SpecialCraftKit.squared(target.getX() - center.getX(),
                target.getY() - center.getY(),
                target.getZ() - center.getZ()) <= maxDistance * maxDistance;
    }
}
