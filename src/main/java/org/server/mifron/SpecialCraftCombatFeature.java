package org.server.mifron;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
 * 特殊クラフト（戦闘系）5種の右クリック効果。
 *
 * <p>blow_dart / throw_net / smoke_bomb / spider_lens / frost_vial を扱う。発動はメインハンドの
 * 右クリック（空中・ブロック）のみで、BAMBOO / COBWEB / GUNPOWDER / SPYGLASS / GLASS_BOTTLE の
 * バニラ使用・設置を止めるため、対象アイテムを握っているときは必ずイベントをキャンセルする。
 *
 * <p>効果はすべて {@code new PotionEffect(type, ticks, amplifier, true, false)}
 * （ambient あり・パーティクルなし）で付与し、プレイヤーには付与しない（PvP 公平性のため）。
 */
final class SpecialCraftCombatFeature implements Listener {

    private static final String BLOW_DART = "blow_dart";
    private static final String THROW_NET = "throw_net";
    private static final String SMOKE_BOMB = "smoke_bomb";
    private static final String SPIDER_LENS = "spider_lens";
    private static final String FROST_VIAL = "frost_vial";

    private static final int BLOW_DART_RANGE = 8;
    private static final int BLOW_DART_COOLDOWN = 8;
    private static final double BLOW_DART_DAMAGE = 3.0;
    private static final int BLOW_DART_POISON_TICKS = 80;

    private static final int THROW_NET_RANGE = 8;
    /** 投網の横方向の広がり（前方8ブロック・横4ブロック以内）。 */
    private static final double THROW_NET_SPREAD = 4.0;
    private static final int THROW_NET_COOLDOWN = 15;
    private static final int THROW_NET_SLOW_TICKS = 80;

    private static final int SMOKE_BOMB_RADIUS = 8;
    private static final int SMOKE_BOMB_COOLDOWN = 20;
    private static final int SMOKE_BOMB_BLIND_TICKS = 60;

    private static final int SPIDER_LENS_RADIUS = 16;
    private static final int SPIDER_LENS_COOLDOWN = 20;
    private static final int SPIDER_LENS_GLOW_TICKS = 120;

    private static final int FROST_VIAL_RANGE = 8;
    private static final int FROST_VIAL_RADIUS = 4;
    private static final int FROST_VIAL_COOLDOWN = 12;
    private static final int FROST_VIAL_SLOW_TICKS = 60;

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
        if (id == null) {
            return;
        }
        // チェスト・ドアなどは通常操作を優先する。
        if (SpecialCraftKit.opensOwnUi(event)) {
            return;
        }
        if (BLOW_DART.equals(id)) {
            event.setCancelled(true);
            this.blowDart(player);
        } else if (THROW_NET.equals(id)) {
            event.setCancelled(true);
            this.throwNet(player);
        } else if (SMOKE_BOMB.equals(id)) {
            event.setCancelled(true);
            this.smokeBomb(player);
        } else if (SPIDER_LENS.equals(id)) {
            event.setCancelled(true);
            this.spiderLens(player);
        } else if (FROST_VIAL.equals(id)) {
            event.setCancelled(true);
            this.frostVial(player);
        }
    }

    /** 吹き矢: 8ブロック先の対象に3ダメージ＋毒I 4秒。耐久1消費（CD 8秒）。 */
    private void blowDart(Player player) {
        if (!this.kit.tryUse(player, BLOW_DART, BLOW_DART_COOLDOWN)) {
            return;
        }
        this.kit.damageHeld(player, 1);
        Entity target = player.getTargetEntity(BLOW_DART_RANGE);
        if (!(target instanceof LivingEntity) || target instanceof Player) {
            this.kit.actionBar(player, "§7外れた");
            return;
        }
        LivingEntity living = (LivingEntity) target;
        living.damage(BLOW_DART_DAMAGE, player);
        living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, BLOW_DART_POISON_TICKS, 0, true, false));
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

    /** 煙幕玉: 周囲8ブロックのMOBに盲目3秒。1個消費（CD 20秒）。 */
    private void smokeBomb(Player player) {
        if (!this.kit.tryUse(player, SMOKE_BOMB, SMOKE_BOMB_COOLDOWN)) {
            return;
        }
        this.kit.consumeHeld(player);
        Location center = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(center,
                SMOKE_BOMB_RADIUS, SMOKE_BOMB_RADIUS, SMOKE_BOMB_RADIUS)) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (!withinRadius(center, living.getLocation(), SMOKE_BOMB_RADIUS)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, SMOKE_BOMB_BLIND_TICKS, 0, true, false));
        }
    }

    /** 蜘蛛の目鏡: 16ブロック以内のMOBに発光6秒。消費なし（CD 20秒）。 */
    private void spiderLens(Player player) {
        if (!this.kit.tryUse(player, SPIDER_LENS, SPIDER_LENS_COOLDOWN)) {
            return;
        }
        Location center = player.getLocation();
        for (Entity entity : player.getWorld().getNearbyEntities(center,
                SPIDER_LENS_RADIUS, SPIDER_LENS_RADIUS, SPIDER_LENS_RADIUS)) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (!withinRadius(center, living.getLocation(), SPIDER_LENS_RADIUS)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, SPIDER_LENS_GLOW_TICKS, 0, true, false));
        }
    }

    /**
     * 氷結の瓶: 視線先8ブロックの面に粉末雪を置き、その周囲4ブロックのMOBに鈍足II 3秒。
     * 1個消費（CD 12秒）。置ける面が決められないときは何もせず終了する（消費しない）。
     */
    private void frostVial(Player player) {
        Location eye = player.getEyeLocation();
        Block hit = player.getTargetBlockExact(FROST_VIAL_RANGE);
        Block placement;
        Location center;
        if (hit != null) {
            placement = faceBlock(hit, eye);
            if (placement == null) {
                return;
            }
            center = placement.getLocation().add(0.5, 0.5, 0.5);
        } else {
            // 視線先にブロックが無いときは自分の足元に置く。
            placement = player.getLocation().getBlock();
            center = player.getLocation();
        }
        if (placement == null || !placement.getType().isAir()) {
            return;
        }
        if (!this.kit.tryUse(player, FROST_VIAL, FROST_VIAL_COOLDOWN)) {
            return;
        }
        placement.setType(Material.POWDER_SNOW, false);
        this.kit.consumeHeld(player);
        for (Entity entity : player.getWorld().getNearbyEntities(center,
                FROST_VIAL_RADIUS, FROST_VIAL_RADIUS, FROST_VIAL_RADIUS)) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            if (!withinRadius(center, living.getLocation(), FROST_VIAL_RADIUS)) {
                continue;
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, FROST_VIAL_SLOW_TICKS, 1, true, false));
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

    /**
     * 視線から見て hit の手前（プレイヤー側）にある隣接ブロックを返す。
     * 面が決められない（視線位置がブロック中心と一致する）場合は null。
     */
    private static Block faceBlock(Block hit, Location eye) {
        if (hit == null || eye == null || hit.getWorld() == null || !hit.getWorld().equals(eye.getWorld())) {
            return null;
        }
        double dx = eye.getX() - (hit.getX() + 0.5);
        double dy = eye.getY() - (hit.getY() + 0.5);
        double dz = eye.getZ() - (hit.getZ() + 0.5);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax == 0.0 && ay == 0.0 && az == 0.0) {
            return null;
        }
        if (ay >= ax && ay >= az) {
            return hit.getRelative(0, dy > 0.0 ? 1 : -1, 0);
        }
        if (ax >= az) {
            return hit.getRelative(dx > 0.0 ? 1 : -1, 0, 0);
        }
        return hit.getRelative(0, 0, dz > 0.0 ? 1 : -1);
    }
}
