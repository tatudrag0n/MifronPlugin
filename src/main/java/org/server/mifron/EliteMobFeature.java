package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EliteMobFeature implements Listener {
    private static final double DEFAULT_SPAWN_CHANCE = 0.02;
    private final Mifron plugin;
    private final Random random = new Random();
    private final NamespacedKey eliteKey;
    private static final double BOSS_BAR_RANGE = 40.0;
    private final Map<UUID, EliteState> eliteStates = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> eliteTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> eliteBars = new ConcurrentHashMap<>();
    private boolean allowEliteSpawn;

    private static final class EliteState {
        boolean phase2;
        /** Set once we know this type never gets a second phase. */
        boolean phase2Blocked;
        long nextSpecialAt;
        long nextBuildAt;
        Location lastLocation;
        int stuckTicks;
    }

    public EliteMobFeature(Mifron plugin) {
        this.plugin = plugin;
        this.eliteKey = new NamespacedKey(plugin, "elite_mob");
    }

    /** Admin-summoned elites bypass region/plugin spawn denials. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = false)
    public void onForcedEliteSpawn(CreatureSpawnEvent event) {
        if (this.allowEliteSpawn
            && (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND)
            && event.getEntity() instanceof Mob) {
            event.setCancelled(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        // Every hostile mob is a Mob in Bukkit, but Mob also covers passive
        // animals, so require Enemy as well.
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) return;
        switch (event.getSpawnReason()) {
            case SPAWNER, SPAWNER_EGG, CUSTOM, COMMAND -> { return; }
            default -> { }
        }
        String worldName = mob.getWorld().getName();
        String hubWorld = plugin.getConfig().getString("hub.world", "world");
        if (worldName.equalsIgnoreCase(hubWorld)) return;
        double chance = plugin.getConfig().getDouble("elite-mobs.spawn-chance", DEFAULT_SPAWN_CHANCE);
        if (this.isRagnarokActive() || random.nextDouble() < Math.max(0.0, Math.min(1.0, chance))) {
            makeElite(mob);
        }
    }

    /** True when the entity type is a hostile mob that can become an elite. */
    public static boolean isEliteCandidate(EntityType type) {
        if (type == null || type.getEntityClass() == null) return false;
        return Mob.class.isAssignableFrom(type.getEntityClass())
            && Enemy.class.isAssignableFrom(type.getEntityClass());
    }

    /** Elitizes the nearest monster to the given block position (admin events). */
    public boolean elitizeNearest(World world, int x, int y, int z, double radius) {
        if (world == null) return false;
        Location center = new Location(world, x + 0.5, y, z + 0.5);
        Mob best = null;
        double bestDist = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)
                || mob.isDead() || !mob.isValid()) continue;
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

    private void makeElite(Mob mob) {
        // PDC survives chunk unload/restart, unlike transient metadata.
        mob.getPersistentDataContainer().set(this.eliteKey, PersistentDataType.BYTE, (byte) 1);
        double healthMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.health-multiplier", 8.0));
        double damageMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.damage-multiplier", 1.5));
        double speedMultiplier = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.speed-multiplier", 1.2));
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            // Weak mobs (silverfish, endermite) would stay trivial even at x8,
            // so every elite also gets a hard health floor.
            double floor = Math.max(1.0, plugin.getConfig().getDouble("elite-mobs.min-health", 40.0));
            double newHp = Math.max(maxHealth.getBaseValue() * healthMultiplier, floor);
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
        mob.setCustomName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ ELITE ✦ " + ChatColor.RED + eliteTitle(mob));
        mob.setCustomNameVisible(true);

        UUID id = mob.getUniqueId();
        BukkitTask old = this.eliteTasks.remove(id);
        if (old != null) old.cancel();
        EliteState state = new EliteState();
        this.eliteStates.put(id, state);
        this.createBossBar(mob, state);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> this.tickElite(mob), 20L, 10L);
        this.eliteTasks.put(id, task);
    }

    private void createBossBar(Mob mob, EliteState state) {
        BossBar previous = this.eliteBars.remove(mob.getUniqueId());
        if (previous != null) {
            previous.removeAll();
            previous.setVisible(false);
        }
        BossBar bar = Bukkit.createBossBar(
            this.bossBarTitle(mob, state),
            this.bossBarColor(mob.getType()),
            state.phase2 ? BarStyle.SOLID : BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        bar.setVisible(true);
        this.eliteBars.put(mob.getUniqueId(), bar);
    }

    private String bossBarTitle(Mob mob, EliteState state) {
        String prefix = state.phase2
            ? ChatColor.DARK_PURPLE + "✦ ELITE II ✦ "
            : ChatColor.LIGHT_PURPLE + "✦ ELITE ✦ ";
        return prefix + ChatColor.RED + eliteTitle(mob)
            + ChatColor.GRAY + "  " + (int) Math.ceil(Math.max(0.0, mob.getHealth()))
            + "/" + (int) Math.ceil(Math.max(1.0, mob.getMaxHealth()));
    }

    private BarColor bossBarColor(EntityType type) {
        if (type == null) return BarColor.PINK;
        return switch (type) {
            case SLIME -> BarColor.GREEN;
            case MAGMA_CUBE, BLAZE, GHAST, HOGLIN, ZOGLIN, RAVAGER -> BarColor.RED;
            case GUARDIAN, ELDER_GUARDIAN, DROWNED -> BarColor.BLUE;
            case PHANTOM, SKELETON, STRAY, BOGGED, SILVERFISH, ENDERMITE -> BarColor.WHITE;
            case WITHER, WITHER_SKELETON, WARDEN, ENDER_DRAGON, ENDERMAN, SHULKER -> BarColor.PURPLE;
            case EVOKER, ILLUSIONER, WITCH, VEX -> BarColor.PINK;
            default -> BarColor.PINK;
        };
    }

    /** Summons an elite of the requested type near the player (admin command). */
    public boolean spawnElite(Player player, EntityType type) {
        if (player == null || !isEliteCandidate(type)) return false;
        Location base = player.getLocation().clone();
        Vector direction = base.getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() > 1.0E-6) direction.normalize();
        else direction = new Vector(0.0, 0.0, 1.0);
        Vector side = direction.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        Location[] candidates = {
            base.clone().add(direction.clone().multiply(2.0)),
            base.clone(),
            base.clone().add(direction.clone().multiply(3.0)),
            base.clone().add(side.clone().multiply(2.0)),
            base.clone().add(side.clone().multiply(-2.0)),
            base.clone().add(0.0, 1.0, 0.0)
        };
        this.allowEliteSpawn = true;
        try {
            for (Location candidate : candidates) {
                candidate.setY(base.getY());
                if (!candidate.getBlock().isPassable()
                    || !candidate.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) continue;
                if (!player.getWorld().isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)
                    && !player.getWorld().getChunkAt(candidate).load(true)) continue;
                Entity spawned = player.getWorld().spawnEntity(candidate, type,
                    CreatureSpawnEvent.SpawnReason.COMMAND);
                if (spawned instanceof Mob mob && mob.isValid()) {
                    this.makeElite(mob);
                    plugin.getLogger().info("Spawned elite " + type.name() + " at "
                        + candidate.getWorld().getName() + " " + candidate.getBlockX() + ","
                        + candidate.getBlockY() + "," + candidate.getBlockZ());
                    return true;
                }
                if (spawned != null) spawned.remove();
            }
        } finally {
            this.allowEliteSpawn = false;
        }
        plugin.getLogger().warning("Failed to spawn elite " + type.name() + " near " + player.getName());
        return false;
    }

    private void tickElite(Mob mob) {
        if (mob.isDead() || !mob.isValid()) {
            this.stopElite(mob);
            return;
        }
        Location location = mob.getLocation();
        if (!mob.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        EliteState state = this.eliteStates.computeIfAbsent(mob.getUniqueId(), ignored -> new EliteState());
        this.updateBossBar(mob, state);
        LivingEntity target = mob.getTarget();
        if (target == null || target.isDead() || !this.isValidTarget(target)) {
            target = this.findNearestPlayer(mob, 24.0);
            if (target != null) mob.setTarget(target);
        }
        if (!state.phase2 && !state.phase2Blocked && mob.getHealth() <= mob.getMaxHealth() * 0.5) {
            if (supportsPhase2(mob.getType())) {
                this.enterPhase2(mob, state);
            } else {
                // Only the headline bosses escalate. Everything else stays single-phase.
                state.phase2Blocked = true;
            }
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

    private void updateBossBar(Mob mob, EliteState state) {
        BossBar bar = this.eliteBars.computeIfAbsent(mob.getUniqueId(), id -> {
            BossBar created = Bukkit.createBossBar(
                this.bossBarTitle(mob, state),
                this.bossBarColor(mob.getType()),
                BarStyle.SEGMENTED_10);
            created.setVisible(true);
            return created;
        });
        double maxHealth = Math.max(1.0, mob.getMaxHealth());
        bar.setProgress(Math.max(0.0, Math.min(1.0, mob.getHealth() / maxHealth)));
        bar.setTitle(this.bossBarTitle(mob, state));
        bar.setColor(state.phase2 ? BarColor.RED : this.bossBarColor(mob.getType()));
        bar.setStyle(state.phase2 ? BarStyle.SOLID : BarStyle.SEGMENTED_10);

        double rangeSquared = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
        for (Player player : new ArrayList<>(bar.getPlayers())) {
            if (!player.isOnline() || player.isDead()
                || !player.getWorld().equals(mob.getWorld())
                || player.getLocation().distanceSquared(mob.getLocation()) > rangeSquared) {
                bar.removePlayer(player);
            }
        }
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) continue;
            if (player.getLocation().distanceSquared(mob.getLocation()) > rangeSquared) continue;
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        }
        return true;
    }

    private LivingEntity findNearestPlayer(Mob mob, double range) {
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

    private void enterPhase2(Mob mob, EliteState state) {
        state.phase2 = true;
        AttributeInstance damage = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.3);
        AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.2);
        mob.setCustomName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ ELITE II ✦ " + ChatColor.RED + eliteTitle(mob));
        // Phase two is a real escalation: heal, shield, summon help and knock
        // nearby players back in one telegraphed burst.
        this.healSelf(mob, mob.getMaxHealth() * 0.2);
        this.applyPotion(mob, PotionEffectType.ABSORPTION, 240, 1);
        this.applyPotion(mob, PotionEffectType.SPEED, 240, 0);
        this.summonPhase2Minions(mob);
        this.shockwave(mob, 6.5, 6.0, false);
        Location location = mob.getLocation().add(0.0, 1.0, 0.0);
        mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 60, 1.0, 1.0, 1.0, 0.08);
        mob.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        mob.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
        this.updateBossBar(mob, state);
    }

    private void summonPhase2Minions(Mob mob) {
        EntityType minion = switch (mob.getType()) {
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> EntityType.ZOMBIE;
            case SKELETON, STRAY, BOGGED -> EntityType.SKELETON;
            case SPIDER -> EntityType.CAVE_SPIDER;
            case CAVE_SPIDER -> EntityType.SILVERFISH;
            case WITHER_SKELETON -> EntityType.WITHER_SKELETON;
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> EntityType.ZOMBIFIED_PIGLIN;
            case HOGLIN, ZOGLIN -> EntityType.ZOGLIN;
            case EVOKER, ILLUSIONER -> EntityType.VEX;
            case SLIME -> EntityType.SLIME;
            case MAGMA_CUBE -> EntityType.MAGMA_CUBE;
            case PHANTOM -> EntityType.PHANTOM;
            case SILVERFISH -> EntityType.SILVERFISH;
            case ENDERMITE -> EntityType.ENDERMITE;
            case CREEPER -> EntityType.CREEPER;
            case WITCH -> EntityType.WITCH;
            default -> null;
        };
        if (minion == null) return;
        int count = switch (mob.getType()) {
            case SILVERFISH, ENDERMITE -> 3;
            case EVOKER, PHANTOM, SLIME, MAGMA_CUBE -> 2;
            default -> 2;
        };
        for (int i = 0; i < count; i++) {
            Location spawn = mob.getLocation().clone().add(
                (this.random.nextDouble() - 0.5) * 4.0,
                0.0,
                (this.random.nextDouble() - 0.5) * 4.0);
            if (!spawn.getBlock().isPassable()
                || !spawn.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) continue;
            Entity entity = mob.getWorld().spawnEntity(spawn, minion, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (entity instanceof Mob helper) {
                helper.setTarget(mob.getTarget());
                if (helper.getAttribute(Attribute.MAX_HEALTH) != null) {
                    double hp = helper.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                    helper.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp * 1.5);
                    helper.setHealth(hp * 1.5);
                }
            }
        }
        mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation(), 25, 1.0, 0.6, 1.0, 0.02);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
    }

    private void tryAvoidHazards(Mob mob) {
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

    private void tryBuildPath(Mob mob, LivingEntity target, EliteState state) {
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

    private void useEliteSpecial(Mob mob, LivingEntity target, EliteState state) {
        double multiplier = state.phase2 ? 1.5 : 1.0;
        switch (mob.getType()) {
            case SKELETON, STRAY -> this.shootVolley(mob, target, state.phase2 ? 6 : 3, 7.0 * multiplier);
            case BOGGED -> {
                this.shootVolley(mob, target, state.phase2 ? 6 : 3, 6.0 * multiplier);
                this.applyPotion(target, PotionEffectType.POISON, 100, 1);
            }
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER ->
                this.leapAttack(mob, target, 7.0 * multiplier, false);
            case CREEPER -> {
                mob.getWorld().strikeLightningEffect(mob.getLocation());
                this.shockwave(mob, 6.0, 10.0 * multiplier, true);
            }
            case SPIDER, CAVE_SPIDER -> {
                Block trap = target.getLocation().getBlock();
                if (trap.getType().isAir()) trap.setType(Material.COBWEB);
                this.applyPotion(target, PotionEffectType.SLOWNESS, 80, 1);
                this.applyPotion(target, PotionEffectType.POISON, 80, 0);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.8f);
            }
            case ENDERMAN -> {
                // 異空の支配者: 上空送りと、地面から掘り出した TNT の2種を使い分ける。
                if (this.random.nextBoolean()) {
                    this.voidSlam(mob, target, multiplier);
                } else {
                    this.buriedTnt(mob, target, multiplier);
                }
            }
            case WITHER_SKELETON ->
                this.witherSkullVolley(mob, target, state.phase2 ? 3 : 1, state.phase2);
            case BLAZE -> {
                // 極炎の化身: 10連射 / 爆発する火球 / 降下して周囲を焼く の3種。
                int blazeRoll = this.random.nextInt(3);
                if (blazeRoll == 0) {
                    this.fireballBarrage(mob, target, 10, false);
                } else if (blazeRoll == 1) {
                    this.explosiveFireball(mob, target, 8.0 * multiplier);
                } else {
                    this.infernoDescent(mob, target, 6.0 * multiplier);
                }
            }
            case WARDEN -> this.sonicBurst(mob, target, 12.0 * multiplier);
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN ->
                this.chargeTarget(mob, target, 1.6, 8.0 * multiplier, false);
            case HOGLIN, ZOGLIN -> this.chargeTarget(mob, target, 1.8, 9.0 * multiplier, false);
            case GHAST -> this.fireballBarrage(mob, target, state.phase2 ? 3 : 1, true);
            case SLIME -> this.leapAttack(mob, target, 6.0 * multiplier, false);
            case MAGMA_CUBE -> this.leapAttack(mob, target, 7.0 * multiplier, true);
            case SHULKER -> {
                this.shulkerBullet(mob, target);
                if (state.phase2) this.shulkerTeleport(mob);
            }
            case PHANTOM -> {
                this.diveAttack(mob, target, 8.0 * multiplier);
                if (state.phase2) this.summonMinions(mob, EntityType.PHANTOM, 2);
            }
            case WITCH -> this.witchHex(mob, target, 6.0 * multiplier);
            case RAVAGER -> this.ravagerRoar(mob, target, 9.0 * multiplier);
            case EVOKER -> {
                this.evokerFangs(mob, target);
                this.summonMinions(mob, EntityType.VEX, state.phase2 ? 3 : 1);
            }
            case VINDICATOR -> this.chargeTarget(mob, target, 1.7, 9.0 * multiplier, false);
            case PILLAGER -> this.shootVolley(mob, target, state.phase2 ? 5 : 2, 6.0 * multiplier);
            case ILLUSIONER -> {
                this.applyPotion(target, PotionEffectType.BLINDNESS, 100, 0);
                this.shootVolley(mob, target, state.phase2 ? 5 : 3, 6.0 * multiplier);
            }
            case GUARDIAN, ELDER_GUARDIAN -> this.guardianLaser(mob, target, 9.0 * multiplier);
            case SILVERFISH, ENDERMITE -> this.swarmAttack(mob, target, 5.0 * multiplier);
            case ENDER_DRAGON -> {
                this.dragonFireball(mob, target);
                this.shockwave(mob, 7.0, 10.0 * multiplier, false);
            }
            case WITHER -> this.witherSkullVolley(mob, target, state.phase2 ? 4 : 2, true);
            case VEX -> {
                this.chargeTarget(mob, target, 1.5, 7.0 * multiplier, false);
                this.applyPotion(target, PotionEffectType.BLINDNESS, 60, 0);
            }
            case BREEZE -> this.windBurst(mob, target, 7.0 * multiplier);
            case CREAKING -> this.darknessBurst(mob, target, 8.0 * multiplier);
            default -> this.shockwave(mob, 5.0, 8.0 * multiplier, state.phase2);
        }
        // Phase two adds a second, unpredictable action on top of the
        // mob-specific attack so fights keep escalating.
        if (state.phase2 && this.random.nextDouble() < 0.35) {
            this.phase2Extra(mob, target);
        }
    }

    private void phase2Extra(Mob mob, LivingEntity target) {
        switch (this.random.nextInt(6)) {
            case 0 -> this.arrowRain(mob, target, 8, 6.0);
            case 1 -> {
                this.pullTarget(mob, target, 1.0);
                this.applyPotion(target, PotionEffectType.SLOWNESS, 60, 1);
            }
            case 2 -> {
                this.applyPotion(target, PotionEffectType.DARKNESS, 100, 0);
                this.applyPotion(target, PotionEffectType.POISON, 100, 1);
            }
            case 3 -> {
                this.healSelf(mob, mob.getMaxHealth() * 0.08);
                this.applyPotion(mob, PotionEffectType.ABSORPTION, 120, 1);
            }
            case 4 -> this.summonPhase2Minions(mob);
            default -> this.shockwave(mob, 5.0, 6.0,
                mob.getType() == EntityType.BLAZE || mob.getType() == EntityType.MAGMA_CUBE);
        }
    }

    private void leapAttack(Mob mob, LivingEntity target, double damage, boolean fire) {
        Vector leap = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        leap.setY(0.0);
        if (leap.lengthSquared() > 1.0E-6) mob.setVelocity(leap.normalize().multiply(1.2).setY(0.55));
        this.shockwave(mob, 4.5, damage, fire);
    }

    private void chargeTarget(Mob mob, LivingEntity target, double speed, double damage, boolean fire) {
        Vector charge = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        charge.setY(0.0);
        if (charge.lengthSquared() > 1.0E-6) mob.setVelocity(charge.normalize().multiply(speed).setY(0.25));
        this.shockwave(mob, 4.0, damage, fire);
    }

    private void diveAttack(Mob mob, LivingEntity target, double damage) {
        Vector dive = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        if (dive.lengthSquared() > 1.0E-6) mob.setVelocity(dive.normalize().multiply(1.4).setY(-0.8));
        target.damage(damage, mob);
        this.applyPotion(target, PotionEffectType.SLOWNESS, 60, 0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.2f, 0.8f);
    }

    private void fireballBarrage(Mob mob, LivingEntity target, int count, boolean large) {
        for (int i = 0; i < count; i++) {
            Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
            if (direction.lengthSquared() < 1.0E-6) return;
            direction.normalize().add(new Vector(
                (this.random.nextDouble() - 0.5) * 0.12,
                (this.random.nextDouble() - 0.5) * 0.12,
                (this.random.nextDouble() - 0.5) * 0.12)).normalize();
            if (large) {
                org.bukkit.entity.LargeFireball fireball =
                    mob.launchProjectile(org.bukkit.entity.LargeFireball.class, direction.multiply(1.0));
                fireball.setYield(2.0f);
            } else {
                org.bukkit.entity.SmallFireball fireball =
                    mob.launchProjectile(org.bukkit.entity.SmallFireball.class, direction.multiply(1.2));
                fireball.setIsIncendiary(true);
            }
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.2f, 0.8f);
    }

    private void witherSkullVolley(Mob mob, LivingEntity target, int count, boolean charged) {
        for (int i = 0; i < count; i++) {
            Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
            if (direction.lengthSquared() < 1.0E-6) return;
            org.bukkit.entity.WitherSkull skull = mob.launchProjectile(
                org.bukkit.entity.WitherSkull.class, direction.normalize().multiply(1.2));
            skull.setCharged(charged);
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.8f);
    }

    private void shulkerBullet(Mob mob, LivingEntity target) {
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        org.bukkit.entity.ShulkerBullet bullet = mob.launchProjectile(
            org.bukkit.entity.ShulkerBullet.class, direction.normalize().multiply(0.8));
        bullet.setTarget(target);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.2f, 0.8f);
    }

    private void shulkerTeleport(Mob mob) {
        Location destination = mob.getLocation().clone().add(
            (this.random.nextDouble() - 0.5) * 10.0, 2.0, (this.random.nextDouble() - 0.5) * 10.0);
        if (!destination.getBlock().isPassable()
            || !destination.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) return;
        mob.getWorld().spawnParticle(Particle.PORTAL, mob.getLocation(), 30, 0.5, 0.8, 0.5, 0.1);
        mob.teleport(destination);
        mob.getWorld().spawnParticle(Particle.PORTAL, destination, 30, 0.5, 0.8, 0.5, 0.1);
    }

    private void guardianLaser(Mob mob, LivingEntity target, double damage) {
        Location start = mob.getEyeLocation();
        Location end = target.getEyeLocation();
        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length < 1.0E-6) return;
        direction.normalize();
        for (double step = 0.5; step < length; step += 0.5) {
            mob.getWorld().spawnParticle(Particle.END_ROD,
                start.clone().add(direction.clone().multiply(step)), 1, 0.0, 0.0, 0.0, 0.0);
        }
        target.damage(damage, mob);
        this.applyPotion(target, PotionEffectType.MINING_FATIGUE, 100, 1);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 1.0f);
    }

    private void evokerFangs(Mob mob, LivingEntity target) {
        Location base = target.getLocation();
        for (int i = 0; i < 4; i++) {
            Location location = base.clone().add(
                (this.random.nextDouble() - 0.5) * 3.0, 0.0, (this.random.nextDouble() - 0.5) * 3.0);
            org.bukkit.entity.EvokerFangs fangs = mob.getWorld().spawn(location, org.bukkit.entity.EvokerFangs.class);
            if (fangs != null) {
                fangs.setOwner(mob);
                fangs.setAttackDelay(i);
            }
        }
        mob.getWorld().playSound(base, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.8f);
    }

    private void summonMinions(Mob mob, EntityType type, int count) {
        for (int i = 0; i < count; i++) {
            Location spawn = mob.getLocation().clone().add(
                (this.random.nextDouble() - 0.5) * 4.0,
                0.0,
                (this.random.nextDouble() - 0.5) * 4.0);
            if (!spawn.getBlock().isPassable()
                || !spawn.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) continue;
            Entity entity = mob.getWorld().spawnEntity(spawn, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (entity instanceof Mob helper) {
                helper.setTarget(mob.getTarget());
                if (helper instanceof org.bukkit.entity.Vex vex) {
                    vex.setBound(mob.getLocation());
                    vex.setCharging(true);
                }
            }
        }
        mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation(), 20, 1.0, 0.6, 1.0, 0.02);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
    }

    private void witchHex(Mob mob, LivingEntity target, double damage) {
        this.applyPotion(target, PotionEffectType.POISON, 120, 1);
        this.applyPotion(target, PotionEffectType.WEAKNESS, 120, 0);
        this.applyPotion(target, PotionEffectType.SLOWNESS, 100, 1);
        target.damage(damage, mob);
        this.healSelf(mob, mob.getMaxHealth() * 0.1);
        mob.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0.0, 1.0, 0.0),
            25, 0.6, 0.8, 0.6, 0.0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 0.8f);
    }

    private void ravagerRoar(Mob mob, LivingEntity target, double damage) {
        this.shockwave(mob, 7.0, damage, false);
        this.applyPotion(target, PotionEffectType.SLOWNESS, 80, 1);
        for (Entity entity : mob.getWorld().getNearbyEntities(mob.getLocation(), 8, 8, 8)) {
            if (entity instanceof Player player && this.isValidTarget(player)) {
                this.pullTarget(mob, player, 0.6);
            }
        }
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
    }

    private void swarmAttack(Mob mob, LivingEntity target, double damage) {
        this.applyPotion(mob, PotionEffectType.SPEED, 80, 1);
        this.applyPotion(target, PotionEffectType.SLOWNESS, 60, 1);
        target.damage(damage, mob);
        this.summonMinions(mob, mob.getType(), 2);
        Sound sound = mob.getType() == EntityType.ENDERMITE
            ? Sound.ENTITY_ENDERMITE_AMBIENT : Sound.ENTITY_SILVERFISH_AMBIENT;
        mob.getWorld().playSound(mob.getLocation(), sound, 1.2f, 1.2f);
    }

    private void windBurst(Mob mob, LivingEntity target, double damage) {
        this.shockwave(mob, 5.0, damage, false);
        this.applyPotion(target, PotionEffectType.LEVITATION, 60, 1);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 1.0f);
    }

    private void darknessBurst(Mob mob, LivingEntity target, double damage) {
        this.applyPotion(target, PotionEffectType.DARKNESS, 120, 0);
        this.applyPotion(target, PotionEffectType.SLOWNESS, 100, 1);
        target.damage(damage, mob);
        mob.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0.0, 1.0, 0.0),
            40, 0.8, 0.8, 0.8, 0.02);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_CREAKING_TWITCH, 1.2f, 0.7f);
    }

    private void dragonFireball(Mob mob, LivingEntity target) {
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        mob.launchProjectile(org.bukkit.entity.DragonFireball.class, direction.normalize().multiply(1.0));
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 0.7f);
    }

    private void arrowRain(Mob mob, LivingEntity target, int count, double damage) {
        Location center = target.getLocation();
        for (int i = 0; i < count; i++) {
            Location spawn = center.clone().add(
                (this.random.nextDouble() - 0.5) * 6.0,
                8.0 + this.random.nextDouble() * 3.0,
                (this.random.nextDouble() - 0.5) * 6.0);
            org.bukkit.entity.Arrow arrow = mob.getWorld().spawn(spawn, org.bukkit.entity.Arrow.class);
            arrow.setDamage(damage);
            arrow.setCritical(true);
            arrow.setShooter(mob);
            arrow.setVelocity(new Vector(0.0, -1.2, 0.0));
        }
        mob.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0.0, 6.0, 0.0),
            30, 3.0, 1.0, 3.0, 0.1);
        mob.getWorld().playSound(center, Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.6f);
    }

    private void pullTarget(Mob mob, LivingEntity target, double strength) {
        Vector pull = mob.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() < 1.0E-6) return;
        target.setVelocity(pull.normalize().multiply(strength).setY(0.35));
        mob.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 12, 0.3, 0.3, 0.3, 0.05);
        mob.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
    }

    private void healSelf(Mob mob, double amount) {
        if (amount <= 0.0) return;
        mob.setHealth(Math.min(mob.getMaxHealth(), mob.getHealth() + amount));
        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0.0, 1.0, 0.0),
            6, 0.4, 0.4, 0.4, 0.0);
    }

    private void applyPotion(LivingEntity entity, PotionEffectType type, int duration, int amplifier) {
        if (entity == null || entity.isDead()) return;
        entity.addPotionEffect(new PotionEffect(type, duration, amplifier, true, true));
    }

    private void shootVolley(Mob mob, LivingEntity target, int count, double damage) {
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

    private void shockwave(Mob mob, double radius, double damage, boolean fire) {
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

    private void sonicBurst(Mob mob, LivingEntity target, double damage) {
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

    private void stopElite(Mob mob) {
        UUID id = mob.getUniqueId();
        this.eliteStates.remove(id);
        BukkitTask task = this.eliteTasks.remove(id);
        if (task != null) task.cancel();
        this.removeBossBar(id);
    }

    private void removeBossBar(UUID id) {
        BossBar bar = this.eliteBars.remove(id);
        if (bar == null) return;
        bar.removeAll();
        bar.setVisible(false);
    }

    /** Cancels every elite task and hides all boss bars on plugin disable. */
    public void shutdown() {
        for (BukkitTask task : this.eliteTasks.values()) {
            if (task != null) task.cancel();
        }
        this.eliteTasks.clear();
        this.eliteStates.clear();
        for (BossBar bar : this.eliteBars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        this.eliteBars.clear();
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

    /** ラグナロク終了時刻（epoch millis）。0 なら未開催。 */
    private volatile long ragnarokUntil;

    /** ラグナロク開催中か。 */
    public boolean isRagnarokActive() {
        return System.currentTimeMillis() < this.ragnarokUntil;
    }

    /**
     * イベント「ラグナロク」: 全MOBをエリート化する。
     *
     * <p>開催中は新しく自然湧きするMOBもすべてエリートになり、
     * すでに読み込まれているMOBもその場でエリート化される。
     *
     * @return エリート化した既存MOBの数
     */
    public int startRagnarok(int seconds) {
        int duration = Math.max(10, seconds);
        this.ragnarokUntil = System.currentTimeMillis() + duration * 1000L;
        String hubWorld = plugin.getConfig().getString("hub.world", "world");
        int converted = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(hubWorld)) continue;
            for (LivingEntity living : world.getLivingEntities()) {
                if (!(living instanceof Mob mob) || !(mob instanceof Enemy)) continue;
                if (mob.isDead() || !mob.isValid() || this.isElite(mob)) continue;
                this.makeElite(mob);
                converted++;
            }
        }
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD
            + "⚔ ラグナロク ⚔ " + ChatColor.RED + "全MOBがエリート化した。"
            + ChatColor.GRAY + " (" + duration + "秒間 / 既存 " + converted + " 体)");
        return converted;
    }

    /** ラグナロクを即時終了する。 */
    public void stopRagnarok() {
        this.ragnarokUntil = 0L;
        Bukkit.broadcastMessage(ChatColor.GOLD + "ラグナロクは終息した。");
    }

    /** Per-type display name for an elite. Every elite gets a distinct identity. */
    static String eliteTitle(Mob mob) {
        EntityType type = mob.getType();
        if (type == null) return "エリート";
        return switch (type) {
            case ZOMBIE -> "腐死王";
            case HUSK -> "砂漠の腐死王";
            case DROWNED -> "海淵の腐死王";
            case ZOMBIE_VILLAGER -> "堕落した村人王";
            case SKELETON -> "骸の射手王";
            case STRAY -> "凍てつく射手王";
            case BOGGED -> "沼の射手王";
            case WITHER_SKELETON -> "凋零の騎士王";
            case CREEPER -> "爆裂の王";
            case SPIDER -> "巣食う女王";
            case CAVE_SPIDER -> "洞窟の女王";
            case ENDERMAN -> "異空の支配者";
            case BLAZE -> "極炎の化身";
            case GHAST -> "泣き叫ぶ巨影";
            case SLIME -> "粘性の王";
            case MAGMA_CUBE -> "溶岩の王";
            case SHULKER -> "殻の監視者";
            case PHANTOM -> "夜翔の王";
            case WITCH -> "呪いの女王";
            case RAVAGER -> "蹂躙の巨獣";
            case EVOKER -> "呪術王";
            case VINDICATOR -> "処刑王";
            case PILLAGER -> "狙撃隊長";
            case ILLUSIONER -> "幻影の王";
            case VEX -> "騒霊の王";
            case GUARDIAN -> "深海の番人";
            case ELDER_GUARDIAN -> "深海の古王";
            case SILVERFISH -> "蝕みの群王";
            case ENDERMITE -> "次元虫の王";
            case WARDEN -> "深淵の守護王";
            case ENDER_DRAGON -> "終焉の竜王";
            case WITHER -> "凋零の王";
            case PIGLIN -> "灼熱の将軍";
            case PIGLIN_BRUTE -> "豚人の大将";
            case ZOMBIFIED_PIGLIN -> "亡国の将軍";
            case HOGLIN -> "暴走獣王";
            case ZOGLIN -> "腐乱獣王";
            case BREEZE -> "旋風の王";
            case CREAKING -> "軋みの王";
            default -> "エリート・" + type.name().toLowerCase().replace('_', '-');
        };
    }

    /** Only the headline bosses escalate into a second phase. */
    static boolean supportsPhase2(EntityType type) {
        if (type == null) return false;
        return switch (type) {
            case ELDER_GUARDIAN, PIGLIN_BRUTE, WITHER, ENDER_DRAGON, WARDEN -> true;
            default -> false;
        };
    }

    /** 腐死王系: can only be finished off with Smite. */
    static boolean isUndeadKing(EntityType type) {
        if (type == null) return false;
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED -> true;
            default -> false;
        };
    }

    /** True when the killing blow came from a weapon enchanted with Smite (アンデット特攻). */
    private boolean slainBySmite(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return false;
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        return weapon != null
            && weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SMITE) > 0;
    }

    /** Brings an undead king back a moment after a non-Smite kill. */
    private void scheduleUndeadKingRevival(Mob dead) {
        Location where = dead.getLocation().clone();
        EntityType type = dead.getType();
        World world = dead.getWorld();
        world.playSound(where, Sound.ENTITY_ZOMBIE_AMBIENT, 1.4f, 0.6f);
        world.spawnParticle(Particle.SOUL, where.clone().add(0.0, 1.0, 0.0), 40, 0.6, 0.8, 0.6, 0.02);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!world.isChunkLoaded(where.getBlockX() >> 4, where.getBlockZ() >> 4)) return;
            Entity spawned = world.spawnEntity(where, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (spawned instanceof Mob revived) {
                this.makeElite(revived);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, where.clone().add(0.0, 1.0, 0.0),
                    50, 0.6, 0.9, 0.6, 0.05);
                world.playSound(where, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
            }
        }, 60L);
    }

    /** 異空の支配者: flings the target high into the air. */
    private void voidSlam(Mob mob, LivingEntity target, double multiplier) {
        World world = mob.getWorld();
        Location from = target.getLocation().clone();
        Location up = from.clone().add(0.0, 26.0, 0.0);
        world.spawnParticle(Particle.PORTAL, from.clone().add(0.0, 1.0, 0.0), 60, 0.5, 1.0, 0.5, 0.4);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 0.7f);
        target.teleport(up);
        target.damage(6.0 * multiplier, mob);
        world.spawnParticle(Particle.REVERSE_PORTAL, up, 40, 0.5, 0.8, 0.5, 0.1);
        world.playSound(up, Sound.ENTITY_ENDERMAN_SCREAM, 1.2f, 0.6f);
    }

    /** 異空の支配者: tears TNT out of the ground underneath the target. */
    private void buriedTnt(Mob mob, LivingEntity target, double multiplier) {
        World world = mob.getWorld();
        Location at = target.getLocation().clone();
        Location ground = at.clone();
        // Dig down to the first solid block so the TNT looks excavated.
        for (int i = 0; i < 4; i++) {
            Block below = ground.getBlock().getRelative(BlockFace.DOWN);
            if (!below.isPassable()) break;
            ground = below.getLocation();
        }
        world.spawnParticle(Particle.SMOKE, ground.clone().add(0.0, 0.4, 0.0), 30, 0.5, 0.2, 0.5, 0.02);
        world.playSound(ground, Sound.BLOCK_GRAVEL_BREAK, 1.4f, 0.6f);
        org.bukkit.entity.TNTPrimed tnt = world.spawn(ground, org.bukkit.entity.TNTPrimed.class);
        tnt.setFuseTicks(45);
        tnt.setYield(4.0f);
        tnt.setIsIncendiary(false);
        target.damage(4.0 * multiplier, mob);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, ground.clone().add(0.0, 1.0, 0.0), 1);
    }

    /** 極炎の化身: lobs a large, exploding fireball. */
    private void explosiveFireball(Mob mob, LivingEntity target, double damage) {
        World world = mob.getWorld();
        Location from = mob.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0E-6) return;
        org.bukkit.entity.LargeFireball ball = world.spawn(from, org.bukkit.entity.LargeFireball.class);
        ball.setShooter(mob);
        ball.setDirection(direction.normalize());
        ball.setYield(3.0f);
        ball.setIsIncendiary(true);
        world.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f);
        target.damage(damage, mob);
    }

    /** 極炎の化身: drops to the ground and sets its surroundings ablaze. */
    private void infernoDescent(Mob mob, LivingEntity target, double damage) {
        World world = mob.getWorld();
        Location dest = target.getLocation().clone();
        mob.teleport(dest.clone().add(0.0, 0.5, 0.0));
        world.spawnParticle(Particle.FLAME, dest.clone().add(0.0, 1.0, 0.0), 120, 3.0, 0.6, 3.0, 0.02);
        world.spawnParticle(Particle.LAVA, dest.clone().add(0.0, 1.0, 0.0), 40, 2.5, 0.4, 2.5, 0.0);
        world.playSound(dest, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.6f);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9) continue;
                Block b = dest.clone().add(dx, 0.0, dz).getBlock();
                Block under = b.getRelative(BlockFace.DOWN);
                if (b.isPassable() && !under.isPassable()) {
                    b.setType(Material.FIRE, false);
                }
            }
        }
        target.damage(damage, mob);
        target.setFireTicks(Math.max(target.getFireTicks(), 100));
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (BossBar bar : this.eliteBars.values()) {
            bar.removePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Mob mob) {
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
        // 腐死王系はアンデット特攻（Smite）を付けた武器でしか止められない。
        // それ以外で倒された場合はその場で蘇り、討伐報酬も与えない。
        if (entity instanceof Mob undeadKing && isUndeadKing(undeadKing.getType())
            && !this.slainBySmite(event)) {
            this.stopElite(undeadKing);
            this.scheduleUndeadKingRevival(undeadKing);
            return;
        }
        if (entity instanceof Mob mob) this.stopElite(mob);
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
