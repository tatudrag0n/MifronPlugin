package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class EliteMobFeature implements Listener {
    private final Mifron plugin;
    private final Random random = new Random();
    private static final String ELITE_TAG = "is_elite_mob";

    public EliteMobFeature(Mifron plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        Monster mob = (Monster) event.getEntity();
        if (random.nextDouble() < 0.002) {
            makeElite(mob);
        }
    }

    private void makeElite(Monster mob) {
        mob.setMetadata(ELITE_TAG, new FixedMetadataValue(plugin, true));
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
        if (event.getDamager() instanceof Monster) {
            Monster mob = (Monster) event.getDamager();
            if (mob.hasMetadata(ELITE_TAG)) {
                event.setDamage(event.getDamage() * 2.0);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasMetadata(ELITE_TAG)) {
            for (ItemStack drop : event.getDrops()) {
                drop.setAmount(Math.min(drop.getAmount() * 5, 64));
            }
            event.setDroppedExp(event.getDroppedExp() * 5);
            Player killer = entity.getKiller();
            if (killer != null) {
                plugin.depositEmeralds(killer.getUniqueId(), 250);
                killer.sendMessage(ChatColor.GOLD + "⚔️ エリートモブを討伐した！ (+250 MP & ドロップ5倍)");
                killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            }
        }
    }
}
