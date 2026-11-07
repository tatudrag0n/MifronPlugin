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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EliteMobFeature implements Listener {
    private static final double DEFAULT_SPAWN_CHANCE = 0.02;
    private final Mifron plugin;
    private final Random random = new Random();
    private final NamespacedKey eliteKey;
    private final Map<UUID, EliteState> eliteStates = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> eliteTasks = new ConcurrentHashMap<>();

    private static final class EliteState {
        boolean phase2;
        long nextSpecialAt;
        long nextBuildAt;
        Location lastLocation;
        int stuckTicks;
    }

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
        double chance = plugin.getConfig().getDouble("elite-mobs.spawn-chance", DEFAULT_SPAWN_CHANCE);
        if (random.nextDouble() < Math.max(0.0, Math.min(1.0, chance))) {
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
        double healthMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.health-multiplier", 4.0));
        double damageMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.damage-multiplier", 1.5));
        double speedMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.speed-multiplier", 1.2));
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double newHp = maxHealth.getBaseValue() * healthMultiplier;
            maxHealth.setBaseValue(newHp);
            mob.setHealth(newHp);
        }
        AttributeInstance damage = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * damageMultiplier);
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * speedMultiplier);
        AttributeInstance armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null) armor.setBaseValue(armor.getBaseValue() + plugin.getConfig().getDouble("elite-mobs.armor-bonus", 4.0));
        AttributeInstance knockback = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.setBaseValue(Math.max(0.5, knockback.getBaseValue()));
        mob.setCustomName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ ELITE ✦ " + ChatColor.RED + mob.getName());
        mob.setCustomNameVisible(true);

        UUID id = mob.getUniqueId();
        BukkitTask old = this.eliteTasks.remove(id);
        if (old != null) old.cancel();
        this.eliteStates.put(id, new EliteState());
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> this.tickElite(mob), 20L, 10L);
        this.eliteTasks.put(id, task);
    }

    /** Summons an elite of the requested type near the player (admin command). */
    public boolean spawnElite(Player player, EntityType type) {
        if (player == null || type == null || type.getEntityClass() == null
            || !Monster.class.isAssignableFrom(type.getEntityClass())) return false;
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() > 1.0E-6) {
            location.add(direction.normalize().multiply(2.0));
        }
        location.setY(player.getLocation().getY());
        if (!location.getBlock().isPassable() || !location.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
            location = player.getLocation().clone();
        }
        Entity spawned = player.getWorld().spawnEntity(location, type);
        if (!(spawned instanceof Monster mob)) {
            spawned.remove();
            return false;
        }
        this.makeElite(mob);
        return true;
    }

    private void tickElite(Monster mob) {
        if (mob.isDead() || !mob.isValid()) {
            this.stopElite(mob);
            return;
        }
        Location location = mob.getLocation();
        if (!mob.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        EliteState state = this.eliteStates.computeIfAbsent(mob.getUniqueId(), ignored -> new EliteState());
        LivingEntity target = mob.getTarget();
        if (target == null || target.isDead() || !this.isValidTarget(target)) {
            target = this.findNearestPlayer(mob, 24.0);
            if (target != null) mob.setTarget(target);
        }
        if (!state.phase2 && mob.getHealth() <= mob.getMaxHealth() * 0.5) {
            this.enterPhase2(mob, state);
        }
        long now = System.currentTimeMillis();
        if (target != null) {
            this.tryAvoidHazards(mob);
            if (now >= state.nextBuildAt) {
                this.tryBuildPath(mob, target, state);
                state.nextBuildAt = now + 900L;
            }
            if (now >= state.nextSpecialAt) {
                this.useEliteSpecial(mob, target, state);
                state.nextSpecialAt = now + (state.phase2 ? 3500L : 6000L);
            }
        }
        Location effect = location.clone().add(0.0, 1.0, 0.0);
        mob.getWorld().spawnParticle(Particle.ENCHANT, effect, 8, 0.4, 0.6, 0.4, 0.1);
        if (state.phase2) {
            mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effect, 4, 0.35, 0.5, 0.35, 0.02);
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        }
        return true;
    }

    private LivingEntity findNearestPlayer(Monster mob, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Entity entity : mob.getWorld().getNearbyEntities(mob.getLocation(), range, range, range)) {
            if (!(entity instanceof Player player) || player.isDead() || !this.isValidTarget(player)) continue;
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void enterPhase2(Monster mob, EliteState state) {
        state.phase2 = true;
        AttributeInstance damage = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.3);
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.2);
        mob.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ ELITE II ✦ " + ChatColor.RED + mob.getName());
        Location location = mob.getLocation().add(0.0, 1.0, 0.0);
        mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 60, 1.0, 1.0, 1.0, 0.08);
        mob.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        mob.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
    }

    private void tryAvoidHazards(Monster mob) {
        Location location = mob.getLocation();
        if (mob.getFireTicks() > 0 && !mob.isInWater()) {
            Block feet = location.getBlock();
            Block below = feet.getRelative(BlockFace.DOWN);
            Material extinguish = mob.getType() == EntityType.ENDERMAN || mob.getType() == EntityType.BLAZE
                ? Material.HAY_BLOCK : Material.WATER;
            if (feet.getType().isAir()) feet.setType(extinguish);
            else if (below.getType().isAir()) below.setType(extinguish);
            mob.setFireTicks(0);
            mob.getWorld().spawnParticle(Particle.CLOUD, location.add(0.0, 0.5, 0.0), 8, 0.3, 0.2, 0.3, 0.02);
        }
        if (mob.isInWater() && mob.getRemainingAir() < mob.getMaximumAir() / 2) {
            Block feet = location.getBlock();
            Block above = feet.getRelative(BlockFace.UP);
            if (feet.getType() == Material.WATER && above.getType().isAir()) {
                feet.setType(Material.HAY_BLOCK);
                mob.teleport(location.clone().add(0.0, 1.0, 0.0));
            } else {
                Block below = feet.getRelative(BlockFace.DOWN);
                if (below.getType() == Material.WATER) below.setType(Material.HAY_BLOCK);
            }
            mob.setRemainingAir(mob.getMaximumAir());
            mob.getWorld().playSound(location, Sound.BLOCK_GRASS_PLACE, 0.7f, 1.2f);
        }
    }

    private void tryBuildPath(Monster mob, LivingEntity target, EliteState state) {
        if (!mob.isOnGround() || mob.isInWater()) return;
        Location location = mob.getLocation();
        if (state.lastLocation != null && state.lastLocation.distanceSquared(location) < 0.04) {
            state.stuckTicks++;
        } else {
            state.stuckTicks = 0;
        }
        state.lastLocation = location.clone();
        if (mob.getLocation().distanceSquared(target.getLocation()) < 4.0) return;
        Vector direction = target.getLocation().toVector().subtract(location.toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        Block front = location.clone().add(direction.multiply(1.2)).getBlock();
        Block below = front.getRelative(BlockFace.DOWN);
        Block head = front.getRelative(BlockFace.UP);
        if (!head.isPassable()) return;
        if (front.isPassable() && below.isPassable()) {
            below.setType(Material.COBBLESTONE);
        } else if (front.isPassable() && state.stuckTicks > 2) {
            front.setType(Material.COBBLESTONE);
            state.stuckTicks = 0;
        }
    }

    private void useEliteSpecial(Monster mob, LivingEntity target, EliteState state) {
        double multiplier = state.phase2 ? 1.5 : 1.0;
        switch (mob.getType()) {
            case SKELETON, STRAY, BOGGED -> this.shootVolley(mob, target, state.phase2 ? 5 : 3, 7.0 * multiplier);
            case ZOMBIE, HUSK, DROWNED -> {
                Vector leap = target.getLocation().toVector().subtract(mob.getLocation().toVector());
                leap.setY(0.0);
                if (leap.lengthSquared() > 1.0E-6) mob.setVelocity(leap.normalize().multiply(1.2).setY(0.55));
                this.shockwave(mob, 4.5, 7.0 * multiplier, false);
            }
            case CREEPER -> {
                mob.getWorld().strikeLightningEffect(mob.getLocation());
                this.shockwave(mob, 6.0, 10.0 * multiplier, true);
            }
            case SPIDER, CAVE_SPIDER -> {
                Block trap = target.getLocation().getBlock();
                if (trap.getType().isAir()) trap.setType(Material.COBWEB);
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 80, 1, true, false));
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.8f);
            }
            case ENDERMAN -> {
                Vector behind = target.getLocation().getDirection().setY(0.0);
                if (behind.lengthSquared() > 1.0E-6) {
                    behind.normalize().multiply(-2.0);
                    mob.teleport(target.getLocation().add(behind));
                }
                target.damage(12.0 * multiplier, mob);
                mob.getWorld().spawnParticle(Particle.PORTAL, target.getLocation(), 30, 0.5, 0.8, 0.5, 0.1);
            }
            case WITHER_SKELETON -> {
                Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
                if (direction.lengthSquared() > 1.0E-6) {
                    org.bukkit.entity.WitherSkull skull = mob.launchProjectile(
                        org.bukkit.entity.WitherSkull.class, direction.normalize().multiply(1.2));
                    skull.setCharged(state.phase2);
                }
            }
            case BLAZE -> {
                for (int i = 0; i < (state.phase2 ? 3 : 1); i++) {
                    Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
                    if (direction.lengthSquared() < 1.0E-6) break;
                    org.bukkit.entity.SmallFireball fireball = mob.launchProjectile(
                        org.bukkit.entity.SmallFireball.class, direction.normalize().multiply(1.2));
                    fireball.setIsIncendiary(true);
                }
            }
            case WARDEN -> this.sonicBurst(mob, target, 12.0 * multiplier);
            case PIGLIN, PIGLIN_BRUTE -> {
                Vector charge = target.getLocation().toVector().subtract(mob.getLocation().toVector());
                charge.setY(0.0);
                if (charge.lengthSquared() > 1.0E-6) mob.setVelocity(charge.normalize().multiply(1.6).setY(0.25));
                this.shockwave(mob, 4.0, 8.0 * multiplier, false);
            }
            default -> this.shockwave(mob, 5.0, 8.0 * multiplier, state.phase2);
        }
    }

    private void shootVolley(Monster mob, LivingEntity target, int count, double damage) {
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        for (int i = 0; i < count; i++) {
            Vector spread = direction.clone().add(new Vector(
                (this.random.nextDouble() - 0.5) * 0.12,
                (this.random.nextDouble() - 0.5) * 0.08,
                (this.random.nextDouble() - 0.5) * 0.12)).normalize();
            org.bukkit.entity.Arrow arrow = mob.launchProjectile(org.bukkit.entity.Arrow.class, spread.multiply(2.0));
            arrow.setDamage(damage);
            arrow.setCritical(true);
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.8f);
    }

    private void shockwave(Monster mob, double radius, double damage, boolean fire) {
        Location center = mob.getLocation();
        for (Entity entity : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player player) || !this.isValidTarget(player)) continue;
            player.damage(damage, mob);
            Vector away = player.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 1.0E-6) away = new Vector(0.0, 1.0, 0.0);
            player.setVelocity(away.normalize().multiply(0.8).setY(0.45));
            if (fire) player.setFireTicks(Math.max(player.getFireTicks(), 60));
        }
        mob.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.add(0.0, 0.5, 0.0), 1);
        mob.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 20, radius / 2.0, 0.5, radius / 2.0, 0.0);
        mob.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
    }

    private void sonicBurst(Monster mob, LivingEntity target, double damage) {
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        direction.normalize();
        Location start = mob.getEyeLocation();
        for (double step = 1.0; step <= 12.0; step += 0.7) {
            Location point = start.clone().add(direction.clone().multiply(step));
            mob.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
        for (Entity entity : mob.getWorld().getNearbyEntities(mob.getLocation(), 12, 12, 12)) {
            if (!(entity instanceof Player player) || !this.isValidTarget(player)) continue;
            Vector to = player.getLocation().toVector().subtract(start.toVector());
            if (to.length() > 12 || (to.lengthSquared() > 0.01 && to.normalize().dot(direction) < 0.6)) continue;
            player.damage(damage, mob);
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);
    }

    private void stopElite(Monster mob) {
        UUID id = mob.getUniqueId();
        this.eliteStates.remove(id);
        BukkitTask task = this.eliteTasks.remove(id);
        if (task != null) task.cancel();
    }

    /**
     * Extra elite loot table (on top of the vanilla 5x drops). Chances are
     * config-tunable under elite-drops.* and default to the survival-friendly
     * rates: apple 30%, diamond 5%, head 5%, book 8%, god-apple 1%,
     * netherite-scrap 0.5%, dashfoot 2%. An elite Warden always drops the
     * Resonance Crystal relic in addition to this table.
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
        if (roll("dashfoot", 0.02)) {
            ItemStack dashfoot = plugin.relicFeature.createRelicItem(RelicFeature.RelicType.DASHFOOT);
            if (dashfoot != null) loc.getWorld().dropItemNaturally(loc, dashfoot);
        }
        if (guaranteesResonanceDrop(entity.getType())) {
            ItemStack resonance = plugin.relicFeature.createRelicItem(RelicFeature.RelicType.RESONANCE_CRYSTAL);
            if (resonance != null) loc.getWorld().dropItemNaturally(loc, resonance);
        }
    }

    /** Elite Wardens are the dedicated source of the Resonance Crystal. */
    static boolean guaranteesResonanceDrop(EntityType type) {
        return type == EntityType.WARDEN;
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
        if (entity instanceof Monster mob) this.stopElite(mob);
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
