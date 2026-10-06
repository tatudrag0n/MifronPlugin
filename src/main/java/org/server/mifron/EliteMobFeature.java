package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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

    public EliteMobFeature(Mifron plugin) {
        this.plugin = plugin;
        this.eliteKey = new NamespacedKey(plugin, "elite_mob");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        switch (event.getSpawnReason()) {
            case SPAWNER, SPAWNER_EGG, CUSTOM, COMMAND -> { return; }
            default -> { }
        }
        String worldName = event.getEntity().getWorld().getName();
        String hubWorld = plugin.getConfig().getString("hub.world", "world");
        if (worldName.equalsIgnoreCase(hubWorld)) return;
        Monster mob = (Monster) event.getEntity();
        if (random.nextDouble() < 0.002) {
            makeElite(mob);
        }
    }

    /** Elitizes the nearest monster to the given block position (admin events). */
    public boolean elitizeNearest(World world, int x, int y, int z, double radius) {
        if (world == null) return false;
        Location center = new Location(world, x + 0.5, y, z + 0.5);
        Monster best = null;
        double bestDist = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Monster mob) || mob.isDead() || !mob.isValid()) continue;
            if (this.isElite(mob)) continue;
            double d = entity.getLocation().distanceSquared(center);
            if (d < bestDist) {
               bestDist = d;
               best = mob;
            }
        }
        if (best == null) return false;
        this.makeElite(best);
        return true;
    }

    public boolean isElite(LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer().has(this.eliteKey, PersistentDataType.BYTE);
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

    /**
     * Extra elite loot table (on top of the vanilla 5x drops). Chances are
     * config-tunable under elite-drops.* and default to the survival-friendly
     * rates: apple 30%, diamond 5%, head 5%, book 8%, god-apple 1%,
     * netherite-scrap 0.5%, scratch 2%.
     */
    private void dropEliteBonus(LivingEntity entity, Player killer) {
        java.util.Random rng = this.random;
        Location loc = entity.getLocation();
        if (roll("golden-apple", 0.30)) loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.GOLDEN_APPLE));
        if (roll("diamond", 0.05)) loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 1 + rng.nextInt(2)));
        Material head = eliteHead(entity.getType());
        if (head != null && roll("mob-head", 0.05)) loc.getWorld().dropItemNaturally(loc, new ItemStack(head));
        if (roll("enchanted-book", 0.08)) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
               (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
            org.bukkit.enchantments.Enchantment[] pool = {
               org.bukkit.enchantments.Enchantment.EFFICIENCY,
               org.bukkit.enchantments.Enchantment.UNBREAKING,
               org.bukkit.enchantments.Enchantment.SHARPNESS,
               org.bukkit.enchantments.Enchantment.PROTECTION,
               org.bukkit.enchantments.Enchantment.FORTUNE,
               org.bukkit.enchantments.Enchantment.LOOTING,
            };
            org.bukkit.enchantments.Enchantment pick = pool[rng.nextInt(pool.length)];
            meta.addStoredEnchant(pick, 1 + rng.nextInt(Math.min(3, pick.getMaxLevel())), true);
            book.setItemMeta(meta);
            loc.getWorld().dropItemNaturally(loc, book);
        }
        if (roll("god-apple", 0.01)) loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE));
        if (roll("netherite-scrap", 0.005)) loc.getWorld().dropItemNaturally(loc, new ItemStack(Material.NETHERITE_SCRAP));
        if (roll("scratch", 0.02)) {
            ItemStack scratch = plugin.specialItemsFeature.createSpecialItem(SpecialItemsFeature.SpecialType.SCRATCH);
            if (scratch != null) loc.getWorld().dropItemNaturally(loc, scratch);
        }
    }

    private boolean roll(String key, double def) {
        return this.random.nextDouble() < plugin.getConfig().getDouble("elite-drops." + key, def);
    }

    private static Material eliteHead(EntityType type) {
        if (type == null) return null;
        switch (type) {
            case ZOMBIE:
            case ZOMBIE_VILLAGER:
            case HUSK:
            case DROWNED: return Material.ZOMBIE_HEAD;
            case SKELETON:
            case STRAY:
            case BOGGED: return Material.SKELETON_SKULL;
            case CREEPER: return Material.CREEPER_HEAD;
            case WITHER_SKELETON: return Material.WITHER_SKELETON_SKULL;
            case ENDER_DRAGON: return Material.DRAGON_HEAD;
            case PIGLIN:
            case PIGLIN_BRUTE: return Material.PIGLIN_HEAD;
            default: return null;
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Monster mob) {
            if (this.isElite(mob)) {
                event.setDamage(event.getDamage() * 2.0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!this.isElite(entity)) {
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            // Cap at the material's own max stack size: armour/tools are
            // unstackable, and a 5-stack of those corrupts pickup behaviour.
            drop.setAmount(Math.min(drop.getAmount() * 5, drop.getMaxStackSize()));
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
        plugin.addEliteKilledMob(killer.getUniqueId(), typeName);
        int base = plugin.getConfig().getInt("elite-mobs.base-mp." + typeName, 10);
        int reward = Math.max(5, base) * 5;
        plugin.depositEmeralds(killer.getUniqueId(), reward);
        killer.sendMessage(ChatColor.GOLD + "\u2694\uFE0F \u30a8\u30ea\u30fc\u30c8\u30e2\u30d6\u3092\u8a0e\u4f10\u3057\u305f\uff01 (+" + reward + " MP / 5\u500d & \u30c9\u30ed\u30c3\u30d75\u500d)");
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        this.dropEliteBonus(entity, killer);
    }
}
