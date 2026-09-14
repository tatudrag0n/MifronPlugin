package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class EliteMobFeature implements Listener {
    private final Mifron plugin;
    private final Random random = new Random();
    private final NamespacedKey eliteKey;
    private final NamespacedKey ffaKindKey;

    public EliteMobFeature(Mifron plugin) {
        this.plugin = plugin;
        this.eliteKey = new NamespacedKey(plugin, "elite_mob");
        this.ffaKindKey = new NamespacedKey(plugin, "ffa_entity_kind");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        switch (event.getSpawnReason()) {
            case SPAWNER, SPAWNER_EGG, CUSTOM, COMMAND -> { return; }
            default -> { }
        }
        String worldName = event.getEntity().getWorld().getName();
        if (plugin.ffaManager != null && plugin.ffaManager.isFfaWorld(event.getEntity().getWorld())) return;
        String hubWorld = plugin.getConfig().getString("hub.world", "world");
        if (worldName.equalsIgnoreCase(hubWorld)) return;
        Monster mob = (Monster) event.getEntity();
        if (random.nextDouble() < 0.002) {
            makeElite(mob);
        }
    }

    private boolean isElite(LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer().has(this.eliteKey, PersistentDataType.BYTE);
    }

    private boolean isFfaEntity(LivingEntity entity) {
        String kind = entity.getPersistentDataContainer().get(this.ffaKindKey, PersistentDataType.STRING);
        return "summon".equals(kind) || "bug_silverfish".equals(kind);
    }

    private void makeElite(Monster mob) {
        // PDC survives chunk unload/restart, unlike transient metadata.
        mob.getPersistentDataContainer().set(this.eliteKey, PersistentDataType.BYTE, (byte) 1);
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double newHp = maxHealth.getBaseValue() * 3.0;
            maxHealth.setBaseValue(newHp);
            mob.setHealth(newHp);
        }
        mob.setCustomName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ ELITE ✦ " + ChatColor.RED + mob.getName());
        mob.setCustomNameVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (mob.isDead() || !mob.isValid()) {
                    cancel();
                    return;
                }
                Location loc = mob.getLocation().add(0, 1.0, 0);
                mob.getWorld().spawnParticle(Particle.ENCHANT, loc, 8, 0.4, 0.6, 0.4, 0.1);
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Monster mob) {
            if (this.isElite(mob)) {
                event.setDamage(event.getDamage() * 2.0);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!this.isElite(entity) || this.isFfaEntity(entity)) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            drop.setAmount(Math.min(drop.getAmount() * 5, 64));
        }
        event.setDroppedExp(event.getDroppedExp() * 5);
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        // Idempotency guard: never pay the elite bonus twice even if the death
        // is processed again for the same entity.
        NamespacedKey rewardedKey = new NamespacedKey(plugin, "elite_rewarded");
        if (entity.getPersistentDataContainer().has(rewardedKey, PersistentDataType.BYTE)) {
            return;
        }
        entity.getPersistentDataContainer().set(rewardedKey, PersistentDataType.BYTE, (byte) 1);
        String typeName = entity.getType().name();
        int base = plugin.getConfig().getInt("elite-mobs.base-mp." + typeName, 10);
        int reward = Math.max(5, base) * 5;
        plugin.depositEmeralds(killer.getUniqueId(), reward);
        killer.sendMessage(ChatColor.GOLD + "\u2694\uFE0F \u30a8\u30ea\u30fc\u30c8\u30e2\u30d6\u3092\u8a0e\u4f10\u3057\u305f\uff01 (+" + reward + " MP / 5\u500d & \u30c9\u30ed\u30c3\u30d75\u500d)");
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }
}
