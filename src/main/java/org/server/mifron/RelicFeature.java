package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.entity.Fireball;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
    private final BlockData ghostIceData;
    private final Map<UUID, GhostPatch> ghostIcePatches = new HashMap<>();

    public RelicFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyRelic = new NamespacedKey(plugin, "relic_id");
        this.ghostIceData = Bukkit.createBlockData(Material.BLUE_ICE);
    }

    /** Starts the background magnet pull task (called after plugin enable). */
    public void startMagnetTask() {
        if (this.magnetTask != null) return;
        this.magnetTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickMagnet, 20L, 4L);
    }

    /** Cancels background tasks owned by this feature. */
    public void shutdown() {
        if (this.magnetTask != null) { this.magnetTask.cancel(); this.magnetTask = null; }
        this.stopDragTask();
    }

    public enum RelicType {
        HOOKSHOT("hookshot", "§6§lフックショット", Material.FISHING_ROD, 0.0005, "§7ウキを引っ掛けた所へ急接近する。"),
        ENCHBOOK("enchbook", "§e§l全適用の魔導書", Material.ENCHANTED_BOOK, 0.0008, "§7右クリックでどんなアイテムにも強力エンチャントを付与できる。"),
        JETPACK("jetpack", "§b§lジェットパック", Material.CHAINMAIL_CHESTPLATE, 0.01, "§7ブレイズパウダーを燃料に、スペース長押しでゆっくり上昇(推進力強化)。後退(S)キーでふわっと着地。"),
        ROLLER_SKATES("skates", "§f§lローラースケート", Material.IRON_BOOTS, 0.0001, "§7足元が青氷のように滑る。スニークでブレーキ。"),
        RESONANCE_CRYSTAL("resonance", "§3§l残響の結晶", Material.ECHO_SHARD, 0.02, "§7ウォーデンのソニックビームを放つ。(クールダウン10秒)"),
        FIREBALL("fireball", "§c§lファイアーボール", Material.FIRE_CHARGE, 0.001, "§7ガストの火の玉を撃ち出す。(クールダウン3秒)"),
        DECAYED_SWORD("decayed_sword", "§8§l朽ちた剣", Material.NETHERITE_SWORD, 0.00001, "§7いつかの勇者の剣。全エンチャントMAXで真価を発揮する。"),
        ENCHGRIND("enchgrind", "§d§l錬金の砥石", Material.GRINDSTONE, 0.0015, "§7右クリックで手元のエンチャントを本にして返還する。"),
        CHAIN("chain", "§7§lチェーン", Material.IRON_CHAIN, 0.0002, "§7エンティティに左クリックで鎖を伸ばし、拘束・引っ張りができる。モブなら基本すべて。"),
        GIANTCORE("giantcore", "§2§l巨神のヘビーコア", Material.HEAVY_CORE, 0.0006, "§7右クリックで25秒間、巨神サイズに巨大化。"),
        SEASWIM("seaswim", "§b§l海洋の心", Material.HEART_OF_THE_SEA, 0.0008, "§7持っている間、空中を泳げる。スペースで上昇、離すとゆっくり降下。"),
        DASHFOOT("dashfoot", "§f§l疾風のウサギの足", Material.RABBIT_FOOT, 0.001, "§7右クリックで前方へ強くダッシュする。"),
        REFLECT("reflect", "§9§l反射の守護盾", Material.SHIELD, 0.0005, "§7盾を構えている間、受けた攻撃のダメージを相手に跳ね返す。"),
        NIGHTCLOCK("nightclock", "§3§l常夜渡りの時計", Material.CLOCK, 0.0006, "§7右クリックで夜をそのまま朝へスキップする。"),
        MAGNET("magnet", "§e§l磁力の石", Material.LODESTONE, 0.0004, "§7持っている間、周囲のアイテムと経験値を強く引き寄せる。右クリックで一気に引き寄せる。"),
        SHRINKSTAR("shrinkstar", "§a§l微粒のネザースター", Material.NETHER_STAR, 0.0006, "§7右クリックで25秒間、小さくなる。狭い隙間も通り抜けられる。"),
        VISION("vision", "§8§l暗視スコープ", Material.SPYGLASS, 0.0003, "§7覗いている間だけ暗視を得る。");

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
        String wanted = LEGACY_RELIC_IDS.getOrDefault(args[1].toLowerCase(), args[1]);
        for (RelicType candidate : RelicType.values()) {
            if (candidate.id.equalsIgnoreCase(wanted)) {
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
    private final java.util.Set<java.util.UUID> landingAssist = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, Long> lastJetpackDescendFuel = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> beamCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> fireballCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> scaleExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> seaswimRising = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> dragFollowers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> reflectCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> magnetCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask dragTask;
    private org.bukkit.scheduler.BukkitTask magnetTask;

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
        id = this.migrateRelicId(player, item, id);
        if (id == null) return;
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;

        if ("enchbook".equals(id) && rightClick) {
            event.setCancelled(true);
            this.openEnchantUi(player);
        } else if ("enchgrind".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryGrind(player);
        } else if ("resonance".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryResonanceBeam(player);
        } else if ("fireball".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryFireball(player);
        } else if ("nightclock".equals(id) && rightClick) {
            event.setCancelled(true);
            this.trySkipNight(player);
        } else if ("dashfoot".equals(id) && rightClick && !player.isSneaking()) {
            event.setCancelled(true);
            this.tryDash(player);
        } else if ("magnet".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, magnetCooldown, 2000L, "磁力バースト")) return;
            this.pullMagnet(player, 24.0, 1.35);
            player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 40, 1.5, 1.5, 1.5, 0.5);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
            player.sendMessage(ChatColor.YELLOW + "磁力バースト！周囲のアイテムを一気に引き寄せた。");
        }
    }

    // ------------------------------------------------------------------
    // Legacy relic ids baked into previously-issued items map onto their
    // newest form, so old drops keep working as the redesigned relic.
    // ------------------------------------------------------------------

    private static final Map<String, String> LEGACY_RELIC_IDS = Map.of(
        "scratch", "enchbook",
        "recall", "enchgrind",
        "phoenix", "giantcore",
        "tide", "seaswim",
        "swift", "dashfoot",
        "guardian", "reflect",
        "magnet", "nightclock",
        "wind", "shrinkstar");

    /**
     * Rewrites a stale relic in place (id, name, material, lore) to its
     * newest form and returns the id the handlers should work with. The
     * interact event hands out a copy, so the rewrite is written back to
     * the hand slot before returning.
     */
    private String migrateRelicId(Player player, ItemStack item, String id) {
        if (id == null) return null;
        for (RelicType type : RelicType.values()) {
            if (type.id.equals(id)) return id;
        }
        String legacy = LEGACY_RELIC_IDS.get(id);
        if (legacy == null) return id;
        RelicType target = null;
        for (RelicType type : RelicType.values()) {
            if (type.id.equals(legacy)) { target = type; break; }
        }
        if (target == null) return id;
        ItemStack fresh = createRelicItem(target);
        player.getInventory().setItemInMainHand(fresh);
        player.sendMessage(ChatColor.GRAY + "手持ちのレリックを新しい形に更新した: " + target.displayName);
        return target.id;
    }

    // ------------------------------------------------------------------
    // Right-click relic actions, cooldown based (nothing is consumed).
    // ------------------------------------------------------------------

    /** Warden sonic beam on a 10s cooldown instead of shattering the shard. */
    private void tryResonanceBeam(Player player) {
        if (!this.rollingCooldown(player, beamCooldown, 10000L, "ソニックビーム")) return;
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
        player.sendMessage(ChatColor.DARK_AQUA + "ソニックビームを放った！(次まで10秒)");
    }

    /** Gust fireball on a 3s cooldown instead of eating the charge. */
    private void tryFireball(Player player) {
        if (!this.rollingCooldown(player, fireballCooldown, 3000L, "ファイアーボール")) return;
        Location eye = player.getEyeLocation();
        Fireball fireball = player.launchProjectile(Fireball.class, eye.getDirection().multiply(2.0));
        fireball.setYield(2.5f);
        fireball.setIsIncendiary(true);
        player.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
    }

    /** Shared cooldown gate: reports the remaining seconds when blocked. */
    private boolean rollingCooldown(Player player, java.util.Map<java.util.UUID, Long> map, long millis, String label) {
        long now = System.currentTimeMillis();
        java.util.UUID uuid = player.getUniqueId();
        Long last = map.get(uuid);
        if (last != null && now - last < millis) {
            long left = (millis - (now - last) + 999) / 1000;
            player.sendMessage(ChatColor.GRAY + label + "のクールダウン中…あと " + left + " 秒");
            return false;
        }
        map.put(uuid, now);
        return true;
    }

    /** Spring forward like a startled rabbit (sneaking cancels the hop). */
    private void tryDash(Player player) {
        Vector facing = player.getLocation().getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() < 1.0E-9) facing = new Vector(1.0, 0.0, 0.0);
        facing.normalize().multiply(1.15);
        player.setVelocity(new Vector(facing.getX(), 0.28, facing.getZ()));
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0.0, 0.4, 0.0), 14, 0.3, 0.1, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1.0f, 1.0f);
    }

    /** Night-skip clock: pressing it during the night jumps to morning. */
    private void trySkipNight(Player player) {
        World world = player.getWorld();
        if (world.getTime() % 24000L < 12542L) {
            player.sendMessage(ChatColor.YELLOW + "日中だ。夜を待ってから回してね。");
            return;
        }
        world.setTime(0L);
        world.playSound(world.getSpawnLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2.0f, 1.2f);
        player.sendMessage(ChatColor.AQUA + "時計を回して夜はスキップした！");
    }

    /** Giant/tiny transform: scale comes back on its own after 25s. */
    private void tryResize(Player player, boolean giant) {
        double scale = giant ? 3.0 : 0.45;
        String label = giant ? "巨大化" : "縮小";
        AttributeInstance attr = player.getAttribute(Attribute.SCALE);
        if (attr == null) return;
        long now = System.currentTimeMillis();
        java.util.UUID uuid = player.getUniqueId();
        Long expiry = scaleExpiry.get(uuid);
        if (expiry != null && expiry > now) {
            player.sendMessage(ChatColor.YELLOW + label + "の効果が続いている…");
            return;
        }
        attr.setBaseValue(scale);
        scaleExpiry.put(uuid, now + 25000L);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, giant ? 1.0f : 0.7f, giant ? 0.7f : 1.6f);
        player.sendMessage(ChatColor.GOLD + label + "した！(25秒)");
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            AttributeInstance current = player.getAttribute(Attribute.SCALE);
            Long end = scaleExpiry.get(uuid);
            if (current == null || end == null) return;
            if (end > System.currentTimeMillis()) return; // re-used before expiry
            current.setBaseValue(1.0);
            scaleExpiry.remove(uuid);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 1.4f);
        }, 540L);
    }

    // ------------------------------------------------------------------
    // The apply-any-enchant book: an 18-card chest of preset max-tier
    // enchantments, each writing straight onto the off-hand item.
    // ------------------------------------------------------------------

    private static final String ENCH_UI_TITLE = "\u00a75Mifron Enchant";

    /** One preset card: display, enchantment to apply, and its level. */
    private record EnchCard(String label, Enchantment enchant, int level) { }

    private static final List<EnchCard> ENCH_CARDS = List.of(
        new EnchCard("\u00a7c鋭さ V", Enchantment.SHARPNESS, 5),
        new EnchCard("\u00a7e力 V", Enchantment.POWER, 5),
        new EnchCard("\u00a7f効率強化 V", Enchantment.EFFICIENCY, 5),
        new EnchCard("\u00a7a耐久力 III", Enchantment.UNBREAKING, 3),
        new EnchCard("\u00a7d修繕", Enchantment.MENDING, 1),
        new EnchCard("\u00a7bダメージ軽減 IV", Enchantment.PROTECTION, 4),
        new EnchCard("\u00a7f落下緩和 IV", Enchantment.FEATHER_FALLING, 4),
        new EnchCard("\u00a73水中歩行 III", Enchantment.DEPTH_STRIDER, 3),
        new EnchCard("\u00a78ソウルスピード III", Enchantment.SOUL_SPEED, 3),
        new EnchCard("\u00a7e幸運 III", Enchantment.FORTUNE, 3),
        new EnchCard("\u00a7bシルクタッチ", Enchantment.SILK_TOUCH, 1),
        new EnchCard("\u00a7c略奪 III", Enchantment.LOOTING, 3),
        new EnchCard("\u00a77忍足 III", Enchantment.SWIFT_SNEAK, 3),
        new EnchCard("\u00a7b水中作業 I", Enchantment.AQUA_AFFINITY, 1),
        new EnchCard("\u00a73水中呼吸 III", Enchantment.RESPIRATION, 3));

    private void openEnchantUi(Player player) {
        Inventory ui = Bukkit.createInventory(player, 54, ENCH_UI_TITLE);
        for (int slot = 0; slot <= 8; slot++) ui.setItem(slot, this.framePane());
        for (int slot = 45; slot <= 53; slot++) ui.setItem(slot, this.framePane());
        for (int slot = 37; slot <= 43; slot++) ui.setItem(slot, this.framePane());
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31};
        for (int i = 0; i < slots.length && i < ENCH_CARDS.size(); i++) {
            EnchCard card = ENCH_CARDS.get(i);
            ui.setItem(slots[i], plugin.actionItem(Material.ENCHANTED_BOOK, card.label(), List.of("\u00a7eクリック", "\u00a77オフハンドのアイテムに付与"), "ench_apply", String.valueOf(i)));
        }
        ui.setItem(40, plugin.actionItem(Material.PAPER, "\u00a7f使い方", List.of(
            "\u00a771. エンチャントしたいアイテムを", "\u00a77   オフハンドに持つ", "\u00a772. 好みのエンチャントをクリック"), "ench_hint", null));
        player.openInventory(ui);
    }

    private ItemStack framePane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) { meta.setDisplayName("\u00a77"); pane.setItemMeta(meta); }
        return pane;
    }

    @EventHandler
    public void onEnchantUiClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!ENCH_UI_TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = plugin.getUiAction(clicked);
        if (!"ench_apply".equals(action)) return;
        int index = plugin.parsePositiveInt(plugin.getUiTargetString(clicked), -1);
        if (index < 0 || index >= ENCH_CARDS.size()) return;
        EnchCard card = ENCH_CARDS.get(index);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "エンチャントしたいアイテムをオフハンドに持ってね。");
            return;
        }
        if (isRelicItem(off)) {
            player.sendMessage(ChatColor.RED + "レリックには重ねて付与できない。");
            return;
        }
        off.addUnsafeEnchantment(card.enchant(), card.level());
        player.getInventory().setItemInOffHand(off);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.sendMessage(ChatColor.GOLD + card.label() + ChatColor.GOLD + " を付与した！");
    }

    // ------------------------------------------------------------------
    // The alchemy grindstone: turns the main-hand item's enchantments into
    // a book each, restoring plain gear and an economy of stored pops.
    // ------------------------------------------------------------------

    private void tryGrind(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "錬りたいアイテムをメインハンドに持ってね。");
            return;
        }
        if (isRelicItem(held)) {
            player.sendMessage(ChatColor.RED + "レリックは錬れない。");
            return;
        }
        Map<Enchantment, Integer> enchants = held.getEnchantments();
        if (enchants.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "このアイテムには返還できるエンチャントがない。");
            return;
        }
        ItemStack ground = held.clone();
        ground.setAmount(1);
        int stripped = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta bookMeta = book.getItemMeta();
            if (bookMeta instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                book.setItemMeta(storage);
            } else {
                book.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
            for (ItemStack leftover : player.getInventory().addItem(book).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            ground.removeEnchantment(entry.getKey());
            stripped++;
        }
        for (ItemStack leftover : player.getInventory().addItem(ground).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "エンチャントを本にして返還した！(×" + stripped + ")");
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
        java.util.Set<java.util.UUID> followers = dragFollowers.get(player.getUniqueId());
        boolean dragHeld = followers != null && followers.contains(target.getUniqueId());
        if (target.getLeashHolder() == player || dragHeld) {
            target.setLeashHolder(null);
            if (dragHeld && followers != null) {
                followers.remove(target.getUniqueId());
                if (followers.isEmpty()) { this.stopDragTask(); }
            }
            player.sendMessage(ChatColor.GRAY + "鎖を解いた。");
        } else if (target.setLeashHolder(player)) {
            player.sendMessage(ChatColor.GRAY + "鎖で拘束した。引っ張って移動できる。");
        } else {
            // Leash-not-supporting entities (bosses, armor stands, …): bind
            // them as drag followers instead — they are teleported back to
            // the player's side whenever they stray too far.
            dragFollowers.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashSet<>()).add(target.getUniqueId());
            this.startDragTask();
            player.sendMessage(ChatColor.GRAY + "鎖で無理やり拘束した。引っ張って移動できる。");
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2.0, 0), 8, 0.2, 0.2, 0.2, 0.1);
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
    }

    /** Pulls drag-bound followers back to the player whenever they stray. */
    private void startDragTask() {
        if (this.dragTask != null) return;
        this.dragTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (java.util.Map.Entry<java.util.UUID, java.util.Set<java.util.UUID>> entry : List.copyOf(dragFollowers.entrySet())) {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner == null || entry.getValue().isEmpty()) {
                    dragFollowers.remove(entry.getKey());
                    continue;
                }
                for (java.util.UUID followerId : List.copyOf(entry.getValue())) {
                    if (!(Bukkit.getEntity(followerId) instanceof org.bukkit.entity.LivingEntity follower) || follower.isDead()) {
                        entry.getValue().remove(followerId);
                        continue;
                    }
                    if (!follower.getWorld().equals(owner.getWorld())) {
                        follower.teleport(owner.getLocation().add(0.0, 0.5, 0.0));
                        continue;
                    }
                    double distance = follower.getLocation().distance(owner.getLocation());
                    if (distance < 3.0) continue;
                    if (distance > 12.0) {
                        follower.teleport(owner.getLocation().add(owner.getLocation().getDirection().multiply(0.6)));
                    } else {
                        org.bukkit.util.Vector pull = owner.getLocation().toVector().subtract(follower.getLocation().toVector()).normalize().multiply(0.32);
                        follower.setVelocity(new org.bukkit.util.Vector(pull.getX(), Math.max(0.0, pull.getY() + 0.1), pull.getZ()));
                    }
                }
                if (entry.getValue().isEmpty()) dragFollowers.remove(entry.getKey());
            }
            if (dragFollowers.isEmpty()) this.stopDragTask();
        }, 10L, 10L);
    }

    private void stopDragTask() {
        if (this.dragTask != null) { this.dragTask.cancel(); this.dragTask = null; }
    }

    // ------------------------------------------------------------------
    // Reflecting shield: while the relic shield is raised, an attacker's
    // damage bounces back at them.
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void onReflectDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isBlocking()) return;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean holdsReflect = (isRelicItem(mainHand) && "reflect".equals(getRelicId(mainHand)))
            || (isRelicItem(offHand) && "reflect".equals(getRelicId(offHand)));
        if (!holdsReflect) return;
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
            && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE) return;
        org.bukkit.entity.Entity source = event.getDamager();
        if (source instanceof org.bukkit.entity.Projectile projectile
            && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            source = shooter;
        }
        if (!(source instanceof org.bukkit.entity.LivingEntity attacker) || attacker == player) return;
        long now = System.currentTimeMillis();
        Long last = reflectCooldown.get(attacker.getUniqueId());
        if (last != null && now - last < 300L) return;
        reflectCooldown.put(attacker.getUniqueId(), now);
        double bounced = Math.max(1.0, event.getDamage());
        attacker.damage(bounced, player);
        attacker.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
            attacker.getLocation().add(0.0, attacker.getHeight() / 2.0, 0.0), 10, 0.2, 0.2, 0.2, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.3f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && isRelicItem(boots) && "skates".equals(getRelicId(boots)) && !player.isSneaking()) {
            this.refreshGhostIce(player);
        } else {
            this.clearGhostIce(player.getUniqueId());
        }
        ItemStack inUse = player.getItemInUse();
        if (inUse != null && isRelicItem(inUse) && "vision".equals(getRelicId(inUse))) {
            // Covers relogs and other paths where no interact event fired.
            this.startScopedVision(player);
        }
        if (jetpackActive.contains(player.getUniqueId())) {
            this.tryHoverJetpack(player);
        }
        if (landingAssist.contains(player.getUniqueId())) {
            if (player.isOnGround()) {
                // Soft touchdown: landing while holding the assist never hurts.
                player.setFallDistance(0.0f);
            } else {
                tryGentleDescent(player);
            }
        }
        this.refreshAirSwim(player);
    }

    // ------------------------------------------------------------------
    // Roller skates: paint a client-side 3×3 packed-ice "ghost" patch under
    // the player's feet (sendBlockChange only — the world itself is never
    // touched), so vanilla ice friction and momentum happen for real while
    // wearing them. Sneaking lifts the patch so the player stands on the
    // real block again: vanilla friction brakes them instead.
    // ------------------------------------------------------------------

    /** One painted ghost patch, remembered so it can be un-painted later. */
    private record GhostPatch(org.bukkit.World world, int x, int y, int z) { }

    private void refreshGhostIce(Player player) {
        UUID uuid = player.getUniqueId();
        Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        // Liquids and air never get frosted: no fake ground over water, no
        // phantom ice floating mid-air.
        if (!below.getType().isSolid()) {
            this.clearGhostIce(uuid);
            return;
        }
        GhostPatch want = new GhostPatch(below.getWorld(), below.getX(), below.getY(), below.getZ());
        GhostPatch painted = ghostIcePatches.get(uuid);
        if (want.equals(painted)) return;
        this.clearGhostIce(uuid); // melt the previous patch before painting the next one
        ghostIcePatches.put(uuid, want);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                player.sendBlockChange(below.getWorld().getBlockAt(want.x() + dx, want.y(), want.z() + dz).getLocation(), ghostIceData);
            }
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
            player.getLocation().add(0.0, 0.9, 0.0), 3, 0.4, 0.05, 0.4, 0.01);
    }

    /**
     * Un-paints the player's patch by re-sending the real block data. Other
     * players never saw the ghost ice, and a relog or world switch re-syncs
     * the client's chunks anyway, so only the online same-world owner needs
     * the restore.
     */
    private void clearGhostIce(UUID uuid) {
        GhostPatch patch = ghostIcePatches.remove(uuid);
        if (patch == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        if (!player.getWorld().equals(patch.world())) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = patch.world().getBlockAt(patch.x() + dx, patch.y(), patch.z() + dz);
                player.sendBlockChange(block.getLocation(), block.getBlockData());
            }
        }
    }

    /** Melts every patch — used from the plugin's own shutdown. */
    public void clearAllGhostIce() {
        for (UUID uuid : List.copyOf(this.ghostIcePatches.keySet())) {
            this.clearGhostIce(uuid);
        }
    }

    // ------------------------------------------------------------------
    // Heart of the Sea (seaswim): while the relic is held the player can
    // swim through the air. Jumping yields gently upward water, releasing
    // glides back down (slow falling), sneaking drops faster.
    // ------------------------------------------------------------------

    /** Refreshes the air-swim state from what the player is holding. */
    private void refreshAirSwim(Player player) {
        if (!this.hasSeaswim(player)) {
            this.seaswimRising.remove(player.getUniqueId());
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            player.setSwimming(false);
            return;
        }
        player.setSwimming(true);
        boolean rising = this.seaswimRising.contains(player.getUniqueId());
        if (rising) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.LEVITATION, 45, 0, true, false));
        } else {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SLOW_FALLING, 45, 0, true, false));
            player.setFallDistance(0.0f);
        }
    }

    /** True while the sea heart sits in either hand. */
    private boolean hasSeaswim(Player player) {
        for (ItemStack hand : new ItemStack[]{player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (hand != null && isRelicItem(hand) && "seaswim".equals(getRelicId(hand))) return true;
        }
        return false;
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

    /** Background pull while the magnet relic is held (items + XP). */
    private void tickMagnet() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!this.hasMagnet(p)) continue;
            this.pullMagnet(p, 12.0, 0.55);
        }
    }

    private boolean hasMagnet(Player player) {
        for (ItemStack hand : new ItemStack[]{player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (hand != null && isRelicItem(hand) && "magnet".equals(getRelicId(hand))) return true;
        }
        return false;
    }

    /** Pulls nearby items and XP orbs toward the player. */
    private void pullMagnet(Player player, double radius, double strength) {
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e == player) continue;
            if (!(e instanceof org.bukkit.entity.Item) && !(e instanceof org.bukkit.entity.ExperienceOrb)) continue;
            org.bukkit.util.Vector to = player.getLocation().add(0.0, 0.5, 0.0).toVector().subtract(e.getLocation().toVector());
            double dist = to.length();
            if (dist < 0.6) continue;
            double pull = strength * (1.0 - dist / (radius + 1.0)) + 0.12;
            e.setVelocity(to.normalize().multiply(Math.min(strength, pull)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.stopScopedVision(uuid);
        this.clearGhostIce(uuid);
        event.getPlayer().setSwimming(false);
        AttributeInstance attr = event.getPlayer().getAttribute(Attribute.SCALE);
        if (attr != null && scaleExpiry.remove(uuid) != null) attr.setBaseValue(1.0);
        dragFollowers.remove(uuid);
        if (dragFollowers.isEmpty()) this.stopDragTask();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.clearGhostIce(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInput(org.bukkit.event.player.PlayerInputEvent event) {
        Player player = event.getPlayer();
        ItemStack chest = player.getInventory().getChestplate();
        boolean jetpack = chest != null && isRelicItem(chest) && "jetpack".equals(getRelicId(chest));
        // The hold flags only mark intent: the climb/float itself is applied
        // on every move event so it follows the player smoothly.
        if (jetpack && event.getInput().isJump()) {
            jetpackActive.add(player.getUniqueId());
        } else {
            jetpackActive.remove(player.getUniqueId());
        }
        // Back (S) key: gentle landing assist flag; the hop loop lives in the
        // move-event ticker so a steadily held key keeps floating the player.
        if (jetpack && event.getInput().isBackward()) {
            landingAssist.add(player.getUniqueId());
        } else {
            landingAssist.remove(player.getUniqueId());
        }
        // Sea heart: jump means "swim up" while the relic is in hand.
        if (this.hasSeaswim(player) && event.getInput().isJump()) {
            seaswimRising.add(player.getUniqueId());
        } else if (this.hasSeaswim(player)) {
            seaswimRising.remove(player.getUniqueId());
        } else {
            seaswimRising.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------
    // Jetpack landing assist: holding Back (S) floats the player down at a
    // capped sink speed and wipes fall distance on touchdown — a soft
    // landing instead of a crash. It still burns powder, just slower.
    // ------------------------------------------------------------------

    /** Sink speed cap (blocks/tick) while the landing assist is held. */
    static final double JETPACK_DESCEND_SPEED = 0.12;
    /** Fuel burn cadence for the landing assist (ms). */
    static final long JETPACK_DESCEND_FUEL_MILLIS = 800L;

    /** Clamps vertical speed to the gentle sink; upward speed is untouched. */
    static double descendY(double currentY) {
        return currentY < -JETPACK_DESCEND_SPEED ? -JETPACK_DESCEND_SPEED : currentY;
    }

    private void tryGentleDescent(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !isRelicItem(chest) || !"jetpack".equals(getRelicId(chest))) {
            landingAssist.remove(player.getUniqueId());
            return;
        }
        Vector vel = player.getVelocity();
        if (vel.getY() >= -JETPACK_DESCEND_SPEED) return; // rising or gently drifting: nothing to do yet
        long now = System.currentTimeMillis();
        Long last = lastJetpackDescendFuel.get(player.getUniqueId());
        if (last == null || now - last >= JETPACK_DESCEND_FUEL_MILLIS) {
            if (!player.getInventory().contains(Material.BLAZE_POWDER)) {
                return; // out of fuel: fall normally, boost path reports the refill hint
            }
            player.getInventory().removeItem(new ItemStack(Material.BLAZE_POWDER, 1));
            lastJetpackDescendFuel.put(player.getUniqueId(), now);
            player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                player.getLocation().add(0.0, 0.3, 0.0), 8, 0.2, 0.1, 0.2, 0.01);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 0.6f);
        }
        // Hold a steady float: the clamp runs every move event while airborne,
        // so gravity cannot build a fast fall between fuel ticks.
        player.setVelocity(new Vector(vel.getX(), descendY(vel.getY()), vel.getZ()));
        player.setFallDistance(0.0f);
    }

    /** Gentle climb: only ever nudges upward speed to the hover cap. */
    static final double JETPACK_CLIMB_SPEED = 0.40;
    /** Speed added per move tick while holding Space (grows to the cap). */
    static final double JETPACK_CLIMB_ACCEL = 0.20;

    /** Caps vertical speed to a gentle climb; existing rises are untouched. */
    static double climbY(double currentY) {
        if (currentY >= JETPACK_CLIMB_SPEED) return currentY;
        return Math.min(currentY + JETPACK_CLIMB_ACCEL, JETPACK_CLIMB_SPEED);
    }

    /**
     * Hold-Space thrust: instead of discrete jolt bursts the jetpack burns
     * fuel steadily and climbs slowly at the cap while the key is held.
     */
    private void tryHoverJetpack(Player player) {
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
        Vector vel = player.getVelocity();
        boolean onGround = player.isOnGround();
        if (!onGround && vel.getY() >= JETPACK_CLIMB_SPEED) return;
        player.getInventory().removeItem(new ItemStack(Material.BLAZE_POWDER, 1));
        lastJetpackBoost.put(player.getUniqueId(), now);
        double targetY = onGround ? 0.60 : climbY(vel.getY());
        player.setVelocity(new Vector(vel.getX(), targetY, vel.getZ()));
        player.setFallDistance(0.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0.0, 0.5, 0.0), 10, 0.3, 0.2, 0.3, 0.04);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 0.9f);
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
