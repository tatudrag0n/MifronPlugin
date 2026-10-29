package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RelicFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keyRelic;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> visionTasks = new HashMap<>();

    public RelicFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyRelic = new NamespacedKey(plugin, "relic_id");
    }

    public enum RelicType {
        HOOKSHOT("hookshot", "§6§lフックショット", Material.FISHING_ROD, 0.0005, "§7ウキを引っ掛けた所へ急接近する。"),
        SCRATCH("scratch", "§e§lスクラッチ", Material.PAPER, 0.01, "§7右クリックで運試し！MPを獲得できる。"),
        JETPACK("jetpack", "§b§lジェットパック", Material.CHAINMAIL_CHESTPLATE, 0.01, "§7ブレイズパウダーを燃料に空を飛ぶ。"),
        ROLLER_SKATES("skates", "§f§lローラースケート", Material.IRON_BOOTS, 0.0001, "§7陸上を氷の上のように滑る。スニークでブレーキ。"),
        RESONANCE_CRYSTAL("resonance", "§3§l残響の結晶", Material.ECHO_SHARD, 0.02, "§7ウォーデンのソニックビームを放つ。(使い捨て)"),
        FIREBALL("fireball", "§c§lファイアーボール", Material.FIRE_CHARGE, 0.001, "§7ガストの火の玉を撃ち出す。(使い捨て)"),
        DECAYED_SWORD("decayed_sword", "§8§l朽ちた剣", Material.NETHERITE_SWORD, 0.00001, "§7いつかの勇者の剣。全エンチャントMAXで真価を発揮する。"),
        RECALL_CHARM("recall", "§b§l帰還チャーム", Material.ENDER_PEARL, 0.002, "§7右クリックでsurvivalスポーンへ帰還する。(使い捨て)"),
        CHAIN("chain", "§7§lチェーン", Material.IRON_CHAIN, 0.0002, "§7エンティティに左クリックで鎖を伸ばし、拘束・引っ張りができる。"),
        PHOENIX("phoenix", "§c§l不死鳥の羽", Material.BLAZE_ROD, 0.0003, "§7持っている間、火炎耐性を得る。"),
        TIDE("tide", "§b§l潮汐の核", Material.HEART_OF_THE_SEA, 0.0003, "§7持っている間、水中呼吸とイルカの好意を得る。"),
        SWIFT("swift", "§f§l疾風の足", Material.RABBIT_FOOT, 0.0003, "§7持っている間、移動速度が上がる。"),
        GUARDIAN("guardian", "§9§l守護者の盾", Material.SHIELD, 0.0003, "§7持っている間、耐性を得る。"),
        MAGNET("magnet", "§e§l磁力の石", Material.LODESTONE, 0.0003, "§7持っている間、近くのアイテムを引き寄せる。"),
        WIND("wind", "§a§l風の翼", Material.FEATHER, 0.0003, "§7持っている間、跳躍力が上がる。"),
        VISION("vision", "§8§l千里眼", Material.SPYGLASS, 0.0003, "§7覗いている間だけ暗視を得る。");

        public final String id;
        public final String displayName;
        public final Material baseMaterial;
        public final double chance;
        public final String description;

        RelicType(String id, String displayName, Material baseMaterial, double chance, String description) {
            this.id = id;
            this.displayName = displayName;
            this.baseMaterial = baseMaterial;
            this.chance = chance;
            this.description = description;
        }
    }

    public ItemStack createRelicItem(RelicType type) {
        ItemStack item = new ItemStack(type.baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String plainName = org.bukkit.ChatColor.stripColor(type.displayName);
            net.kyori.adventure.text.Component fancyName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gradient:#ffd700:#ff4500:#c0392b><bold>" + plainName + "</bold></gradient>");
            meta.displayName(fancyName);
            meta.setUnbreakable(true);
            List<String> lore = new ArrayList<>();
            lore.add(type.description);
            lore.add("§8[レリック / 売買不可]");
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyRelic, PersistentDataType.STRING, type.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e/mf relic <id> [player]");
            sender.sendMessage("§7IDs: " + String.join(", ", java.util.Arrays.stream(RelicType.values()).map(t -> t.id).toList()));
            return;
        }
        RelicType type = null;
        for (RelicType candidate : RelicType.values()) {
            if (candidate.id.equalsIgnoreCase(args[1])) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            sender.sendMessage("§cUnknown special item: " + args[1]);
            sender.sendMessage("§7IDs: " + String.join(", ", java.util.Arrays.stream(RelicType.values()).map(t -> t.id).toList()));
            return;
        }
        Player target = sender instanceof Player player ? player : null;
        if (target == null && args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        }
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }
        ItemStack item = createRelicItem(type);
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        target.sendMessage("§aレリックを入手しました: " + type.displayName);
        if (sender != target) {
            sender.sendMessage("§a" + target.getName() + " に " + type.id + " を付与しました。");
        }
        this.plugin.tutorialFeature.onRelicObtained(target);
    }

    public boolean isRelicItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyRelic, PersistentDataType.STRING);
    }

    public String getRelicId(ItemStack item) {
        if (!isRelicItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyRelic, PersistentDataType.STRING);
    }

    private void tryTransform(ItemStack item, Player player) {
        if (item == null || isRelicItem(item)) return;
        for (RelicType type : RelicType.values()) {
            if (item.getType() == type.baseMaterial) {
                if (random.nextDouble() < type.chance) {
                    // Transform exactly one unit: converting the whole stack
                    // would multiply a 64-stack into 64 lottery items.
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                        ItemStack single = createRelicItem(type);
                        for (ItemStack leftover : player.getInventory().addItem(single).values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                        }
                    } else {
                        ItemStack special = createRelicItem(type);
                        item.setType(special.getType());
                        item.setItemMeta(special.getItemMeta());
                    }
                    player.sendMessage(ChatColor.GOLD + "✨ 奇跡が起きた！ 手に入れたアイテムが " + type.displayName + ChatColor.GOLD + " に変化した！");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    plugin.recordRareCollection(player, type.id);
                    plugin.tutorialFeature.onRelicObtained(player);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            tryTransform(event.getItem().getItemStack(), (Player) event.getEntity());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack result = event.getCurrentItem();
            if (result != null) {
                tryTransform(result, player);
            }
        }
    }

    private final java.util.Map<java.util.UUID, Long> lastUseMillis = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> jetpackActive = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, Long> lastJetpackBoost = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !isRelicItem(item)) return;

        // Vision relic: scoping starts on right click, stop is handled by the
        // task itself (it ends when the player is no longer using the item).
        // Checked before the HAND-only guard so an off-hand spyglass works
        // too; starting a scope task twice is idempotent.
        if ("vision".equals(getRelicId(item)) && event.getAction().isRightClick()) {
            this.startScopedVision(player);
            return;
        }

        // NORMAL priority + no hand check used to double-fire on dual-hand
        // dispatch (item consumed twice, reward paid twice).
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        long now = System.currentTimeMillis();
        Long last = this.lastUseMillis.get(player.getUniqueId());
        if (last != null && now - last < 500L) {
            event.setCancelled(true);
            return;
        }
        this.lastUseMillis.put(player.getUniqueId(), now);

        String id = getRelicId(item);
        if (id == null) return;

        if (id.equals("scratch") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            plugin.tutorialFeature.onSpecialUse(player);
            item.setAmount(item.getAmount() - 1);
            int winMp = (random.nextInt(100) < 10) ? 500 : 50;
            plugin.depositEmeralds(player.getUniqueId(), winMp);
            player.sendMessage(ChatColor.YELLOW + "🎰 スクラッチを削った！ " + winMp + " MP を手に入れた！");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else if (id.equals("resonance") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            double range = 18.0;
            player.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
            for (double d = 1.0; d <= range; d += 0.5) {
                Location p = eye.clone().add(dir.clone().multiply(d));
                player.getWorld().spawnParticle(org.bukkit.Particle.SONIC_BOOM, p, 1);
            }
            for (org.bukkit.entity.Entity e : eye.getWorld().getNearbyEntities(eye, range, range, range)) {
                if (e == player || !(e instanceof org.bukkit.entity.LivingEntity living)) continue;
                Location center = living.getLocation().add(0, living.getHeight() / 2.0, 0);
                Vector to = center.toVector().subtract(eye.toVector());
                double dist = to.length();
                if (dist < 0.5 || dist > range) continue;
                if (to.normalize().dot(dir) < 0.85) continue;
                living.damage(20.0, player);
            }
            item.setAmount(item.getAmount() - 1);
            for (ItemStack leftover : player.getInventory().addItem(new ItemStack(Material.ECHO_SHARD)).values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage(ChatColor.DARK_AQUA + "残響の結晶の力が解放され、通常の欠片に戻った。");
        } else if (id.equals("fireball") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            Location eye = player.getEyeLocation();
            Fireball fireball = player.launchProjectile(Fireball.class, eye.getDirection().multiply(2.0));
            fireball.setYield(2.5f);
            fireball.setIsIncendiary(true);
            player.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        } else if (id.equals("recall") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            Location dest = plugin.readLocation("world-rules.spawn.main");
            if (dest == null || dest.getWorld() == null) dest = player.getWorld().getSpawnLocation();
            player.teleport(dest);
            player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.sendMessage(ChatColor.AQUA + "帰還チャームでスポーンへ戻った！");
        }

    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isRelicItem(item)) item = player.getInventory().getItemInOffHand();
        if (!isRelicItem(item) || !"hookshot".equals(getRelicId(item))) return;

        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.IN_GROUND
                && state != PlayerFishEvent.State.CAUGHT_ENTITY
                && state != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Location hookLoc = event.getHook().getLocation();
        Vector pull = hookLoc.toVector().subtract(player.getLocation().toVector());
        double dist = pull.length();
        if (dist < 0.5) return;
        pull = pull.normalize().multiply(Math.min(2.4, 1.0 + dist * 0.1));
        pull.setY(Math.min(pull.getY() + 0.4, 1.3));
        player.setVelocity(pull);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getLocation(), 12, 0.2, 0.2, 0.2, 0.1);
    }

    @EventHandler
    public void onChainDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isRelicItem(item) || !"chain".equals(getRelicId(item))) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        event.setCancelled(true);
        try {
            if (target.getLeashHolder() == player) {
                target.setLeashHolder(null);
                player.sendMessage(ChatColor.GRAY + "鎖を解いた。");
            } else {
                target.setLeashHolder(player);
                player.sendMessage(ChatColor.GRAY + "鎖で拘束した。引っ張って移動できる。");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "このエンティティは鎖で拘束できません。");
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2.0, 0), 8, 0.2, 0.2, 0.2, 0.1);
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && isRelicItem(boots) && "skates".equals(getRelicId(boots))) {
            this.updateSkating(player, event);
        }
        ItemStack inUse = player.getItemInUse();
        if (inUse != null && isRelicItem(inUse) && "vision".equals(getRelicId(inUse))) {
            // Covers relogs and other paths where no interact event fired.
            this.startScopedVision(player);
        }
        if (jetpackActive.contains(player.getUniqueId())) {
            tryBoostJetpack(player);
        }
        applyPassiveRelics(player);
    }

    // ------------------------------------------------------------------
    // Roller skates: emulate real ice-riding physics instead of a raw
    // speed boost. Movement the client reports per tick is taken as the
    // current momentum, ice friction keeps it, and small steering thrust
    // curves the glide — so long slides and wide turns feel like ice.
    // ------------------------------------------------------------------

    /** Fraction of horizontal momentum kept per tick (vanilla ice is 0.98). */
    static final double SKATE_FRICTION = 0.982;
    /** Strong momentum bleed used as a brake while sneaking. */
    static final double SKATE_SNEAK_FRICTION = 0.70;
    /** Per-tick thrust while sprinting on skates (blocks/tick). */
    static final double SKATE_SPRINT_ACCEL = 0.05;
    /** Per-tick steering/thrust below sprint (blocks/tick). */
    static final double SKATE_WALK_ACCEL = 0.02;
    /** Top glide speed (blocks/tick, ~12 blocks/s). */
    static final double SKATE_MAX_SPEED = 0.62;
    /** Below this speed the ice friction alone has already stopped the player. */
    static final double SKATE_STOP_SPEED = 0.01;

    /** One ice-model step for the given momentum and wish direction. */
    static org.bukkit.util.Vector iceStep(org.bukkit.util.Vector current, org.bukkit.util.Vector wish, boolean sprinting, boolean sneaking) {
        double hx = current.getX() * (sneaking ? SKATE_SNEAK_FRICTION : SKATE_FRICTION);
        double hz = current.getZ() * (sneaking ? SKATE_SNEAK_FRICTION : SKATE_FRICTION);
        if (wish != null && wish.lengthSquared() > 1.0E-9) {
            double accel = sprinting ? SKATE_SPRINT_ACCEL : SKATE_WALK_ACCEL;
            org.bukkit.util.Vector dir = wish.clone().normalize();
            hx += dir.getX() * accel;
            hz += dir.getZ() * accel;
        }
        double speed = Math.sqrt(hx * hx + hz * hz);
        if (speed > SKATE_MAX_SPEED) {
            double scale = SKATE_MAX_SPEED / speed;
            hx *= scale;
            hz *= scale;
        }
        if (speed < SKATE_STOP_SPEED) {
            hx = 0.0;
            hz = 0.0;
        }
        return new org.bukkit.util.Vector(hx, 0.0, hz);
    }

    private void updateSkating(Player player, PlayerMoveEvent event) {
        if (!player.isOnGround()) return;
        // Actual displacement since the last event is the true momentum —
        // player.getVelocity() only mirrors the last server-set/knockback
        // velocity, not what the client produced this tick.
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        org.bukkit.util.Vector momentum = new org.bukkit.util.Vector(dx, 0.0, dz);
        boolean moving = momentum.lengthSquared() > 1.0E-6;
        if (!moving && !player.isSprinting()) return;
        org.bukkit.util.Vector facing = player.getLocation().getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() <= 1.0E-9) facing = null;
        org.bukkit.util.Vector slid = iceStep(momentum, facing, player.isSprinting(), player.isSneaking());
        double speed = Math.sqrt(slid.getX() * slid.getX() + slid.getZ() * slid.getZ());
        if (speed <= 0.02) return;
        player.setVelocity(new org.bukkit.util.Vector(slid.getX(), player.getVelocity().getY(), slid.getZ()));
        int count = Math.max(1, Math.min(6, (int) Math.round(speed * 8)));
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
            player.getLocation().add(0.0, 0.1, 0.0), count, 0.25, 0.05, 0.25, 0.01);
    }

    private void applyPassiveRelics(Player player) {
        for (ItemStack hand : new ItemStack[]{player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (!isRelicItem(hand)) continue;
            String id = getRelicId(hand);
            if (id == null) continue;
            switch (id) {
                case "phoenix" -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false));
                case "tide" -> {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING, 60, 0, true, false));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false));
                }
                case "swift" -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 60, 0, true, false));
                case "guardian" -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 0, true, false));
                case "wind" -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 60, 0, true, false));
                case "magnet" -> {
                    for (org.bukkit.entity.Entity e : player.getNearbyEntities(6, 6, 6)) {
                        if (e instanceof org.bukkit.entity.Item itemEntity) {
                            org.bukkit.util.Vector to = player.getLocation().toVector().subtract(itemEntity.getLocation().toVector());
                            if (to.lengthSquared() > 0 && to.lengthSquared() < 36) {
                                itemEntity.setVelocity(to.normalize().multiply(0.35));
                            }
                        }
                    }
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------
    // Vision relic: night vision only while the player is actually scoping
    // through the spyglass (right click held), not while merely holding it.
    // ------------------------------------------------------------------

    /** Starts the while-scoped night vision loop for one player (idempotent). */
    private void startScopedVision(Player player) {
        UUID uuid = player.getUniqueId();
        if (this.visionTasks.containsKey(uuid)) return;
        this.visionTasks.put(uuid, Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline()) {
                this.stopScopedVision(uuid);
                return;
            }
            ItemStack inUse = current.getItemInUse();
            if (inUse != null && isRelicItem(inUse) && "vision".equals(getRelicId(inUse))) {
                // Stay above 10 seconds so the overlay never starts to blur.
                current.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NIGHT_VISION, 260, 0, true, false));
            } else {
                current.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
                this.stopScopedVision(uuid);
            }
        }, 0L, 4L));
    }

    private void stopScopedVision(UUID uuid) {
        BukkitTask task = this.visionTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onVisionQuit(PlayerQuitEvent event) {
        this.stopScopedVision(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInput(org.bukkit.event.player.PlayerInputEvent event) {
        Player player = event.getPlayer();
        ItemStack chest = player.getInventory().getChestplate();
        boolean jetpack = chest != null && isRelicItem(chest) && "jetpack".equals(getRelicId(chest));
        if (jetpack && event.getInput().isJump()) {
            jetpackActive.add(player.getUniqueId());
            tryBoostJetpack(player);
        } else {
            jetpackActive.remove(player.getUniqueId());
        }
    }

    private void tryBoostJetpack(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !isRelicItem(chest) || !"jetpack".equals(getRelicId(chest))) return;
        if (!jetpackActive.contains(player.getUniqueId())) return;
        long now = System.currentTimeMillis();
        Long last = lastJetpackBoost.get(player.getUniqueId());
        if (last != null && now - last < 350L) return;
        if (!player.getInventory().contains(Material.BLAZE_POWDER)) {
            player.sendMessage(ChatColor.RED + "ジェットパックの燃料(ブレイズパウダー)がありません！");
            jetpackActive.remove(player.getUniqueId());
            return;
        }
        player.getInventory().removeItem(new ItemStack(Material.BLAZE_POWDER, 1));
        Vector v = player.getVelocity();
        Vector facing = player.getLocation().getDirection();
        facing.setY(0);
        if (facing.lengthSquared() > 0) facing.normalize();
        v.setY(1.2);
        v.add(facing.multiply(0.7));
        player.setVelocity(v);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.08);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.1f);
        lastJetpackBoost.put(player.getUniqueId(), now);
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player)) return;
        Location loc = event.getHitEntity() != null ? event.getHitEntity().getLocation() : (event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5) : fireball.getLocation());
        fireball.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 1);
        fireball.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
    }
}
