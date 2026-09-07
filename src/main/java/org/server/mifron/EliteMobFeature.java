package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * EliteMobFeature
 * -----------------
 * 0.2% chance for a spawned mob to become an "Elite":
 *  - HP x3, attack damage x2 (attribute base value scaling)
 *  - Drops and exp x5 (MP reward should be multiplied by 5 by the economy
 *    service wherever it checks isElite(entity))
 *  - Enchant-aura visual approximated with a glowing effect + [ELITE] name
 *
 * Wiring (Mifron.java onEnable):
 *   EliteMobFeature.register(this);
 */
public final class EliteMobFeature implements Listener {

    public static final NamespacedKey ELITE_KEY = new NamespacedKey("mifron", "elite_mob");
    private static final double ELITE_CHANCE = 0.002; // 0.2%
    private static final Random RANDOM = new Random();

    private EliteMobFeature() {}

    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new EliteMobFeature(), plugin);
    }

    public static boolean isElite(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(ELITE_KEY, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (RANDOM.nextDouble() > ELITE_CHANCE) return;
        promoteToElite(event.getEntity());
    }

    private void promoteToElite(LivingEntity entity) {
        entity.getPersistentDataContainer().set(ELITE_KEY, PersistentDataType.BYTE, (byte) 1);

        if (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            double baseMaxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseMaxHealth * 3.0);
            entity.setHealth(Math.min(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), baseMaxHealth * 3.0));
        }

        if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double baseAttack = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(baseAttack * 2.0);
        }

        entity.setCustomName("§6[ELITE] §f" + entity.getType().name());
        entity.setCustomNameVisible(true);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
    }

    /** Hook for special-ability elites; extra per-mob logic can be added here. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity)) return;
        // Attribute-based scaling already applied at spawn.
    }

    /** Drop and exp multiplier. MP reward should be multiplied by 5 wherever
     *  the economy service grants mob-kill MP (check isElite(entity)). */
    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!isElite(event.getEntity())) return;
        event.setDroppedExp(event.getDroppedExp() * 5);
        event.getDrops().forEach(drop -> drop.setAmount(Math.min(drop.getMaxStackSize(), drop.getAmount() * 5)));
    }
}
