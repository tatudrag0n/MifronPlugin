package org.server.mifron;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * レリック「アルテミス」の挙動。
 *
 * <p>右クリックで獲物を追う3本の矢を放つ。RelicFeature の巨大な分岐には手を入れず、
 * PDC の {@code relic_id} だけを見て独立に動く。
 */
public final class ArtemisFeature implements Listener {

    private static final String ARTEMIS_ID = "artemis";
    private static final long COOLDOWN_MILLIS = 5000L;
    private static final int ARROWS = 3;
    private static final double HOMING_RANGE = 24.0;
    private static final int HOMING_TICKS = 60;

    private final Mifron plugin;
    private final NamespacedKey relicIdKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ArtemisFeature(Mifron plugin) {
        this.plugin = plugin;
        this.relicIdKey = new NamespacedKey(plugin, "relic_id");
    }

    private boolean isArtemis(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer().get(this.relicIdKey, PersistentDataType.STRING);
        return ARTEMIS_ID.equals(id);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!this.isArtemis(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = this.cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            long remain = (COOLDOWN_MILLIS - (now - last)) / 1000L + 1L;
            player.sendActionBar("§aアルテミス §7冷却中 " + remain + "s");
            return;
        }
        if (!this.consumeArrow(player)) {
            player.sendActionBar("§c矢がない");
            return;
        }
        this.cooldowns.put(player.getUniqueId(), now);
        this.fireArrows(player);
    }

    private boolean consumeArrow(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return true;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == org.bukkit.Material.ARROW) {
                stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private void fireArrows(Player player) {
        Location eye = player.getEyeLocation();
        Vector base = eye.getDirection().normalize();
        LivingEntity homingTarget = this.findTarget(player, eye);

        for (int i = 0; i < ARROWS; i++) {
            double spread = (i - (ARROWS - 1) / 2.0) * 0.12;
            Vector direction = base.clone();
            direction.add(new Vector(-base.getZ(), 0.0, base.getX()).multiply(spread));
            direction.normalize();

            Arrow arrow = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(1.2)), Arrow.class);
            arrow.setShooter(player);
            arrow.setVelocity(direction.multiply(2.6));
            arrow.setDamage(4.0);
            arrow.setCritical(true);
            if (homingTarget != null) {
                this.homing(arrow, homingTarget);
            }
        }
        player.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.2f, 1.4f);
        player.sendActionBar("§aアルテミス §7三連矢");
    }

    private LivingEntity findTarget(Player player, Location eye) {
        Vector look = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestScore = -1.0;
        for (Entity entity : player.getWorld().getNearbyEntities(eye, HOMING_RANGE, HOMING_RANGE, HOMING_RANGE)) {
            if (entity == player || !(entity instanceof LivingEntity living) || living.isDead()) continue;
            if (entity instanceof Player) continue;
            Vector to = entity.getLocation().toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance < 0.5) continue;
            double dot = to.normalize().dot(look);
            if (dot < 0.4) continue;
            double score = dot / Math.max(1.0, distance);
            if (score > bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    /** 一定時間だけ対象へ向きを寄せる。 */
    private void homing(Arrow arrow, LivingEntity target) {
        final int[] ticks = {0};
        this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task -> {
            ticks[0]++;
            if (arrow.isDead() || !arrow.isValid() || target.isDead()
                || ticks[0] > HOMING_TICKS) {
                task.cancel();
                return;
            }
            Vector desired = target.getEyeLocation().toVector()
                .subtract(arrow.getLocation().toVector()).normalize().multiply(2.8);
            arrow.setVelocity(arrow.getVelocity().multiply(0.65).add(desired.multiply(0.35)));
        }, 1L, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.cooldowns.remove(event.getPlayer().getUniqueId());
    }
}
