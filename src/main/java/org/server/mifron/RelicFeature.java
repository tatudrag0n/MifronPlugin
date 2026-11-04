package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.entity.Fireball;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
    private final NamespacedKey relicGrindstoneKey;
    private final NamespacedKey seaswimSpeedKey;
    /**
     * Water sprint-swimming is about 2.2 blocks/s; normal sprinting is ~5.6.
     * A 0.4 multiplier makes air-swimming feel like water instead of walking.
     */
    static final double SEASWIM_SPEED_MULTIPLIER = 0.4;
    /**
     * Vanilla water swimming rises noticeably faster than Levitation I.
     * Levitation II (amplifier 1) matches that climb while keeping the
     * smooth, server-controlled ascent.
     */
    static final int SEASWIM_ASCEND_AMPLIFIER = 1;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> visionTasks = new HashMap<>();
    private final BlockData ghostIceData;
    private final Map<UUID, GhostPatch> ghostIcePatches = new HashMap<>();

    public RelicFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyRelic = new NamespacedKey(plugin, "relic_id");
        this.relicGrindstoneKey = new NamespacedKey(plugin, "relic_grindstone");
        this.seaswimSpeedKey = new NamespacedKey(plugin, "seaswim_speed");
        this.ghostIceData = Bukkit.createBlockData(Material.BLUE_ICE);
    }

    /** Starts the background magnet pull task (called after plugin enable). */
    public void startMagnetTask() {
        if (this.magnetTask != null) return;
        this.magnetTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickMagnet, 20L, 4L);
        this.passiveTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickPassive, 20L, 1L);
        if (this.initPoseReflection()) this.plugin.getLogger().info("Swim pose packet bridge ready.");
    }

    /** Cancels background tasks owned by this feature. */
    public void shutdown() {
        if (this.magnetTask != null) { this.magnetTask.cancel(); this.magnetTask = null; }
        if (this.passiveTask != null) { this.passiveTask.cancel(); this.passiveTask = null; }
        for (java.util.Set<UUID> followers : dragFollowers.values()) {
            for (UUID followerId : followers) this.restoreChainAI(followerId);
        }
        dragFollowers.clear();
        this.stopDragTask();
        for (BukkitTask task : this.hookshotFlights.values()) task.cancel();
        this.hookshotFlights.clear();
        for (BukkitTask task : this.hookshotHolds.values()) task.cancel();
        this.hookshotHolds.clear();
        for (BukkitTask task : this.hookshotPulls.values()) task.cancel();
        this.hookshotPulls.clear();
        this.hookshotAnchors.clear();
        this.dashWaitingForGround.clear();
        this.dashAirborne.clear();
        this.dashStarted.clear();
        for (Player player : Bukkit.getOnlinePlayers()) this.removeSeaswimSpeed(player);
    }

    /**
     * Scarcity of the item that transforms. A rare base material is harder to
     * obtain, so it gets a proportionally better chance per unit; otherwise
     * netherite-sword relics would be effectively unobtainable.
     */
    public enum MaterialRarity {
        // Calibrated so RARE / MEDIUM (Resonance Crystal) is 0.20%.
        COMMON(0.00100),
        UNCOMMON(0.00200),
        RARE(0.00400),
        EPIC(0.00800),
        LEGENDARY(0.01600);

        public final double baseChance;

        MaterialRarity(double baseChance) {
            this.baseChance = baseChance;
        }
    }

    /** How much the relic changes play; stronger relics are deliberately rarer. */
    public enum RelicPower {
        MINOR(1.0),
        MEDIUM(0.5),
        MAJOR(0.2);

        public final double multiplier;

        RelicPower(double multiplier) {
            this.multiplier = multiplier;
        }
    }

    public enum RelicType {
        HOOKSHOT("hookshot", "§6§lフックショット", Material.FISHING_ROD, MaterialRarity.COMMON, RelicPower.MAJOR, "§7ウキを引っ掛けた所へ急接近する。"),
        ENCHBOOK("enchbook", "§6§l全適用の魔導書", Material.ENCHANTED_BOOK, MaterialRarity.UNCOMMON, RelicPower.MAJOR, "§7右クリックでどんなアイテムにも強力エンチャントを付与できる。"),
        JETPACK("jetpack", "§3§lジェットパック", Material.CHAINMAIL_CHESTPLATE, MaterialRarity.UNCOMMON, RelicPower.MAJOR, "§7ブレイズパウダーを燃料に、スペース長押しで上昇＆前進。燃料消費1/0.8秒。後退(S)キーでふわっと着地。"),
        ROLLER_SKATES("skates", "§7§lスケートブーツ", Material.IRON_BOOTS, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7足元が青氷のように滑る。スニークでブレーキ。"),
        RESONANCE_CRYSTAL("resonance", "§3§l残響の結晶", Material.ECHO_SHARD, MaterialRarity.RARE, RelicPower.MEDIUM, "§7ウォーデンのソニックビームを放つ。(クールダウン8秒)"),
        FIREBALL("fireball", "§4§lファイアーボール", Material.FIRE_CHARGE, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7ガストの火の玉を撃ち出す。(クールダウン3秒)"),
        DECAYED_SWORD("decayed_sword", "§8§l朽ちた剣", Material.NETHERITE_SWORD, MaterialRarity.LEGENDARY, RelicPower.MAJOR, "§7いつかの勇者の剣。全エンチャントMAXで真価を発揮する。"),
        ENCHGRIND("enchgrind", "§d§l錬金の砥石", Material.GRINDSTONE, MaterialRarity.COMMON, RelicPower.MAJOR, "§7右クリックで手元のエンチャントを本にして返還する。"),
        CHAIN("chain", "§8§lサプライズ・チェーン", Material.IRON_CHAIN, MaterialRarity.UNCOMMON, RelicPower.MEDIUM, "§7左クリックで最大30ブロック先のMOBを拘束。再左クリックで解放。右クリックでは外れない。"),
        GIANTCORE("giantcore", "§2§l巨人の心臓", Material.HEAVY_CORE, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで25秒間、巨神サイズに巨大化。"),
        SEASWIM("seaswim", "§3§l空に海写す蒼球", Material.HEART_OF_THE_SEA, MaterialRarity.RARE, RelicPower.MEDIUM, "§7持っている間、空中を泳げる。移動速度は水中泳ぎ相当。スペースで上昇、離すとゆっくり降下。"),
        DASHFOOT("dashfoot", "§7§lバニーホップ", Material.RABBIT_FOOT, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7右クリックで前方へ強くダッシュする。着地するまで再使用不可。"),
        REFLECT("reflect", "§1§l反射の守護盾", Material.SHIELD, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7盾を構えている間、受けた攻撃のダメージを相手に跳ね返す。"),
        NIGHTCLOCK("nightclock", "§3§l常夜渡りの時計", Material.CLOCK, MaterialRarity.COMMON, RelicPower.MINOR, "§7右クリックで夜をそのまま朝へスキップする。"),
        MAGNET("magnet", "§6§l磁力の石", Material.LODESTONE, MaterialRarity.UNCOMMON, RelicPower.MINOR, "§7持っている間、周囲のアイテムと経験値を強く引き寄せる。右クリックで一気に引き寄せる。"),
        SHRINKSTAR("shrinkstar", "§2§l剝蝕矮星", Material.NETHER_STAR, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで25秒間、小さくなる。狭い隙間も通り抜けられる。"),
        LIGHTNING("lightning", "§6§l雷鳴の杖", Material.LIGHTNING_ROD, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7右クリックで視線先に落雷を落とす。(クールダウン4秒)"),
        BLINK("blink", "§5§l次元の眼", Material.ENDER_EYE, MaterialRarity.UNCOMMON, RelicPower.MEDIUM, "§7右クリックで前方へ瞬間移動する。(クールダウン3秒)"),
        TOTEM("totem", "§6§l死不知のトーテム", Material.TOTEM_OF_UNDYING, MaterialRarity.RARE, RelicPower.MAJOR, "§7持っている間、耐性と再生を得る。"),
        WITHER("wither", "§8§l凋零の頭骨", Material.WITHER_SKELETON_SKULL, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックでウィザースクロウを撃ち出す。(クールダウン4秒)"),
        BELL("bell", "§2§l癒やしの鐘", Material.BELL, MaterialRarity.COMMON, RelicPower.MINOR, "§7右クリックで自分と周囲の味方を回復する。(クールダウン6秒)"),
        DRAGONBREATH("dragonbreath", "§5§l竜の吐息", Material.DRAGON_BREATH, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで前方に竜のブレスを放つ。(クールダウン6秒)"),
        VISION("vision", "§8§l暗視鏡", Material.SPYGLASS, MaterialRarity.COMMON, RelicPower.MINOR, "§7覗いている間だけ暗視を得る。");

        public final String id;
        public final String displayName;
        public final Material baseMaterial;
        public final MaterialRarity materialRarity;
        public final RelicPower power;
        public final double chance;
        public final String description;

        RelicType(String id, String displayName, Material baseMaterial, MaterialRarity materialRarity,
                  RelicPower power, String description) {
            this.id = id;
            this.displayName = displayName;
            this.baseMaterial = baseMaterial;
            this.materialRarity = materialRarity;
            this.power = power;
            this.chance = materialRarity.baseChance * power.multiplier;
            this.description = description;
        }
    }

    public ItemStack createRelicItem(RelicType type) {
        ItemStack item = new ItemStack(type.baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Per-item muted colour from the enum, rendered as plain text:
            // no gradient/effects, just one dignified title colour.
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(type.displayName));
            meta.setUnbreakable(true);
            List<String> lore = new ArrayList<>();
            lore.add(type.description);
            lore.add("§8レリック");
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
        String raw = args[1].toLowerCase();
        String wanted = raw;
        boolean known = false;
        for (RelicType candidate : RelicType.values()) {
            if (candidate.id.equalsIgnoreCase(raw)) { known = true; break; }
        }
        // Only fall back to the legacy alias when the id no longer exists.
        if (!known) wanted = LEGACY_RELIC_IDS.getOrDefault(raw, raw);
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
    private final java.util.Set<java.util.UUID> seaswimActive = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> seaswimAirborne = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> poseInjected = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, org.bukkit.entity.Pose> lastServerPose = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean poseReflectInit;
    private boolean poseReflectOk;
    private boolean poseReflectWarned;
    private Object poseAccessor;
    private Class<?> poseClass;
    private java.lang.reflect.Constructor<?> dataValueCtor;
    private java.lang.reflect.Constructor<?> packetCtor;
    private Class<?> packetBaseClass;
    /** Maximum distance at which a new mob can be hooked. */
    private static final double CHAIN_BIND_RANGE = 30.0;
    /** The bound mob stops this far from the owner instead of pushing into them. */
    private static final double CHAIN_FOLLOW_STOP = 2.0;
    /** Per-tick speed cap keeps the pull smooth instead of snapping/teleporting. */
    private static final double CHAIN_MAX_PULL_SPEED = 0.7;
    /** Straight-line bobber speed for the hookshot. */
    private static final double HOOKSHOT_SPEED = 3.0;
    /** The bobber stops when any solid block is within this radius. */
    private static final double HOOKSHOT_BLOCK_RADIUS = 0.4;
    /** Reel-in speed toward the anchored bobber (blocks/tick). */
    private static final double HOOKSHOT_PULL_SPEED = 0.8;
    /** Maximum hookshot range in blocks. */
    private static final double HOOKSHOT_MAX_RANGE = 100.0;
    /** Particle spacing for the custom long-distance hookshot line. */
    private static final double HOOKSHOT_LINE_STEP = 0.75;
    /** Bunny hop stays unavailable until landing; this is only the visual timer. */
    static final int DASH_COOLDOWN_TICKS = 400;
    /** A stopped bobber and the block location it hit. */
    private record HookAnchor(Location location, UUID hookId) { }
    private final java.util.Map<java.util.UUID, HookAnchor> hookshotAnchors = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> dragFollowers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> reflectCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> magnetCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lightningCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> blinkCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> witherCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> bellCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> breathCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    /** Hookshot bobbers fly straight; keyed by the hook entity id. */
    private final java.util.Map<java.util.UUID, BukkitTask> hookshotFlights = new java.util.concurrent.ConcurrentHashMap<>();
    /** Keeps a stopped bobber pinned to the block-contact point. */
    private final java.util.Map<java.util.UUID, BukkitTask> hookshotHolds = new java.util.concurrent.ConcurrentHashMap<>();
    /** Active reel-in tasks, keyed by the player being pulled. */
    private final java.util.Map<java.util.UUID, BukkitTask> hookshotPulls = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bunny hop stays locked until the player has left and touched the ground again. */
    private final java.util.Set<java.util.UUID> dashWaitingForGround = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> dashAirborne = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, Long> dashStarted = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> chainToggleMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Boolean> chainHadAI = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, ItemStack> lastGrindInput = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<org.bukkit.Location> relicGrindstoneLocations = new java.util.HashSet<>();
    private org.bukkit.scheduler.BukkitTask dragTask;
    private org.bukkit.scheduler.BukkitTask magnetTask;
    private org.bukkit.scheduler.BukkitTask passiveTask;

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

        // Second right-click during a hookshot reel: abort the movement but
        // keep the current velocity (inertia), retract the line, and leave the
        // rod ready for another cast. Checked before the generic debounce.
        if ("hookshot".equals(getRelicId(item)) && event.getAction().isRightClick()
                && this.hookshotPulls.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            this.stopHookshotPull(player.getUniqueId());
            this.clearHookshotAnchor(player);
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
        if (rightClick) this.plugin.tutorialFeature.onSpecialUse(player);

        if ("enchbook".equals(id) && rightClick) {
            event.setCancelled(true);
            this.openEnchantUi(player);
        } else if ("resonance".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryResonanceBeam(player, item);
        } else if ("fireball".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryFireball(player, item);
        } else if ("nightclock".equals(id) && rightClick) {
            event.setCancelled(true);
            this.trySkipNight(player);
        } else if ("dashfoot".equals(id) && rightClick && !player.isSneaking()) {
            event.setCancelled(true);
            this.tryDash(player, item);
        } else if ("magnet".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, magnetCooldown, 2000L, item)) return;
            this.pullMagnet(player, 24.0, 1.35);
            player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 40, 1.5, 1.5, 1.5, 0.5);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
            player.sendMessage(ChatColor.YELLOW + "磁力バースト！周囲のアイテムを一気に引き寄せた。");
        } else if ("lightning".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, lightningCooldown, 4000L, item)) return;
            this.tryLightning(player);
        } else if ("blink".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, blinkCooldown, 3000L, item)) return;
            this.tryBlink(player);
        } else if ("wither".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, witherCooldown, 4000L, item)) return;
            org.bukkit.entity.WitherSkull skull = player.launchProjectile(org.bukkit.entity.WitherSkull.class,
                player.getEyeLocation().getDirection().multiply(2.4));
            skull.setCharged(true);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.0f);
        } else if ("bell".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, bellCooldown, 6000L, item)) return;
            this.tryBell(player);
        } else if ("dragonbreath".equals(id) && rightClick) {
            event.setCancelled(true);
            if (!this.rollingCooldown(player, breathCooldown, 6000L, item)) return;
            this.tryDragonBreath(player);
        } else if ("chain".equals(id) && event.getAction() == Action.LEFT_CLICK_AIR) {
            event.setCancelled(true);
            this.tryChainAtRange(player);
        } else if (("giantcore".equals(id) || "shrinkstar".equals(id)) && rightClick) {
            event.setCancelled(true);
            this.tryResize(player, "giantcore".equals(id), item);
        }
    }

    /** Lightning rod: a damaging strike lands where the player looks. */
    private void tryLightning(Player player) {
        org.bukkit.block.Block target = player.getTargetBlockExact(30);
        org.bukkit.Location loc = target != null
            ? target.getLocation().add(0.5, 1.0, 0.5)
            : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(10));
        player.getWorld().strikeLightningEffect(loc);
        player.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 1.0f);
        java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
            if (!(e instanceof org.bukkit.entity.LivingEntity living) || living == player) continue;
            if (hit.add(living.getUniqueId())) living.damage(22.0, player);
        }
        player.sendMessage(ChatColor.YELLOW + "雷鳴が落ちた！");
    }

    /** Ender eye: blink forward up to 20 blocks along the look direction. */
    private void tryBlink(Player player) {
        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        org.bukkit.Location dest = player.getLocation().clone();
        org.bukkit.Location base = player.getLocation().clone();
        World world = player.getWorld();
        // No gameplay distance cap: keep going until a block, the world
        // border, the build height, or an unloaded chunk. The large numeric
        // guard only prevents a pathological loop when aiming at open sky.
        double maxDistance = Math.min(4096.0, world.getWorldBorder().getSize());
        for (int i = 1; i <= maxDistance; i++) {
            org.bukkit.Location check = base.clone().add(dir.clone().multiply(i));
            if (check.getY() < world.getMinHeight() || check.getY() >= world.getMaxHeight()) break;
            if (!world.getWorldBorder().isInside(check)) break;
            if (!world.isChunkLoaded(check.getBlockX() >> 4, check.getBlockZ() >> 4)) break;
            if (!check.getBlock().isPassable() || !check.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) break;
            dest = check;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
        player.teleport(dest);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, dest, 30, 0.4, 0.6, 0.4, 0.1);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    /** Healing bell: heals the user and nearby players, granting regen. */
    private void tryBell(Player player) {
        this.healWithBell(player);
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (e instanceof Player other && other != player) this.healWithBell(other);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.4f);
        player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0.0, 1.5, 0.0), 10, 0.5, 0.5, 0.5, 0.0);
    }

    private void healWithBell(Player target) {
        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + 14.0));
        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.REGENERATION, 120, 2, true, false));
    }

    /** Dragon breath: a cone of damaging breath particles. */
    private void tryDragonBreath(Player player) {
        final org.bukkit.Location start = player.getEyeLocation().clone();
        final org.bukkit.util.Vector dir = start.getDirection().normalize();
        final double maxRange = 18.0;
        final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        final double[] traveled = {0.0};
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 1.4f);
        // Directional breath beam: travels forward, damaging what it touches.
        Bukkit.getScheduler().runTaskTimer(this.plugin, task -> {
            traveled[0] += 1.6;
            org.bukkit.Location pos = start.clone().add(dir.clone().multiply(traveled[0]));
            pos.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, pos, 10, 0.35, 0.35, 0.35, 0.02, 0.06f);
            for (org.bukkit.entity.Entity e : pos.getWorld().getNearbyEntities(pos, 1.8, 1.8, 1.8)) {
                if (!(e instanceof org.bukkit.entity.LivingEntity living) || living == player) continue;
                if (hit.add(living.getUniqueId())) {
                    living.damage(18.0, player);
                    living.setFireTicks(Math.max(living.getFireTicks(), 80));
                    living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 1, true, false));
                    org.bukkit.util.Vector push = dir.clone().multiply(0.9);
                    living.setVelocity(new org.bukkit.util.Vector(push.getX(), 0.45, push.getZ()));
                    living.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                        living.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.3, 0.3, 0.15);
                }
            }
            boolean blocked = !pos.getBlock().isPassable();
            if (blocked || traveled[0] >= maxRange) {
                pos.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, pos, 40, 1.6, 1.6, 1.6, 0.02, 0.06f);
                pos.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, pos, 1);
                for (org.bukkit.entity.Entity e : pos.getWorld().getNearbyEntities(pos, 3.5, 3.5, 3.5)) {
                    if (!(e instanceof org.bukkit.entity.LivingEntity living) || living == player) continue;
                    if (hit.add(living.getUniqueId())) {
                        living.damage(14.0, player);
                        living.setFireTicks(Math.max(living.getFireTicks(), 60));
                    }
                }
                player.sendActionBar("\u00a75竜の吐息: " + hit.size() + "体に命中");
                task.cancel();
            }
        }, 0L, 1L);
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
            if (type.id.equals(id)) {
                // Refresh stale baked names/lore (e.g. renamed or re-styled relics).
                String expected = org.bukkit.ChatColor.stripColor(type.displayName);
                String actual = "";
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    actual = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName());
                }
                if (!expected.equals(actual)) {
                    ItemStack fresh = createRelicItem(type);
                    player.getInventory().setItemInMainHand(fresh);
                }
                return id;
            }
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

    /** Warden sonic beam on an 8s cooldown instead of shattering the shard. */
    private void tryResonanceBeam(Player player, ItemStack relic) {
        if (!this.rollingCooldown(player, beamCooldown, 8000L, relic)) return;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range = 22.0;
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
            living.damage(30.0, player);
        }
        player.sendMessage(ChatColor.DARK_AQUA + "ソニックビームを放った！");
    }

    /** Gust fireball on a 3s cooldown instead of eating the charge. */
    private void tryFireball(Player player, ItemStack relic) {
        if (!this.rollingCooldown(player, fireballCooldown, 3000L, relic)) return;
        Location eye = player.getEyeLocation();
        Fireball fireball = player.launchProjectile(Fireball.class, eye.getDirection().multiply(2.0));
        fireball.setYield(4.0f);
        fireball.setIsIncendiary(true);
        player.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
    }

    /**
     * Shared cooldown gate. Cooldowns are shown through the vanilla item
     * cooldown sweep instead of chat messages.
     */
    boolean rollingCooldown(Player player, java.util.Map<java.util.UUID, Long> map, long millis, ItemStack relic) {
        long now = System.currentTimeMillis();
        java.util.UUID uuid = player.getUniqueId();
        Long last = map.get(uuid);
        if (last != null && now - last < millis) return false;
        map.put(uuid, now);
        if (relic != null && relic.getType() != Material.AIR) {
            int ticks = Math.max(1, (int) Math.ceil(millis / 50.0));
            player.setCooldown(relic.getType(), ticks);
        }
        return true;
    }

    /** Spring forward like a startled rabbit (sneaking cancels the hop). */
    void tryDash(Player player, ItemStack relic) {
        java.util.UUID uuid = player.getUniqueId();
        if (this.dashWaitingForGround.contains(uuid) || this.hookshotPulls.containsKey(uuid)) return;
        this.dashWaitingForGround.add(uuid);
        this.dashAirborne.remove(uuid);
        this.dashStarted.put(uuid, System.currentTimeMillis());
        if (relic != null && relic.getType() != Material.AIR) {
            player.setCooldown(relic.getType(), DASH_COOLDOWN_TICKS);
        }
        Vector facing = player.getLocation().getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() < 1.0E-9) facing = new Vector(1.0, 0.0, 0.0);
        facing.normalize().multiply(1.45);
        player.setVelocity(new Vector(facing.getX(), 0.32, facing.getZ()));
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

    /** Resolves the player's scale attribute regardless of key naming. */
    private AttributeInstance scaleAttribute(Player player) {
        for (String keyName : new String[]{"scale", "generic.scale", "player.scale"}) {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(keyName));
            if (attr == null) continue;
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) return inst;
        }
        return null;
    }

    /** Giant/tiny transform: scale comes back on its own after 25s. */
    private void tryResize(Player player, boolean giant, ItemStack relic) {
        double scale = giant ? 3.0 : 0.45;
        String label = giant ? "巨大化" : "縮小";
        AttributeInstance attr = this.scaleAttribute(player);
        if (attr == null) {
            player.sendMessage(ChatColor.RED + "このサーバーはサイズ変更に対応していません。");
            return;
        }
        long now = System.currentTimeMillis();
        java.util.UUID uuid = player.getUniqueId();
        Long expiry = scaleExpiry.get(uuid);
        if (expiry != null && expiry > now) return;
        attr.setBaseValue(scale);
        scaleExpiry.put(uuid, now + 25000L);
        if (relic != null && relic.getType() != Material.AIR) player.setCooldown(relic.getType(), 500);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, giant ? 1.0f : 0.7f, giant ? 0.7f : 1.6f);
        player.sendMessage(ChatColor.GOLD + label + "した！(25秒)");
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            AttributeInstance current = this.scaleAttribute(player);
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
    /** Cards per page in the enchant GUI. */
    private static final int ENCH_PAGE_SIZE = 45;

    /** Pretty-prints an enchantment key for the GUI card. */
    private static String prettyEnchantName(Enchantment enchant) {
        String raw = enchant.getKey().getKey().replace('_', ' ');
        if (raw.isEmpty()) return "enchant";
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split(" ")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void openEnchantUi(Player player) {
        this.openEnchantUi(player, 0);
    }

    private void openEnchantUi(Player player, int page) {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) all.add(e);
        all.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
        int totalPages = Math.max(1, (all.size() + ENCH_PAGE_SIZE - 1) / ENCH_PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        Inventory ui = Bukkit.createInventory(player, 54, ENCH_UI_TITLE);
        int start = page * ENCH_PAGE_SIZE;
        int end = Math.min(start + ENCH_PAGE_SIZE, all.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Enchantment ench = all.get(i);
            int level = Math.max(1, ench.getMaxLevel());
            String label = "\u00a7b" + prettyEnchantName(ench) + " \u00a77Lv." + level;
            ui.setItem(slot++, plugin.actionItem(Material.ENCHANTED_BOOK, label,
                List.of("\u00a7eクリック", "\u00a77オフハンドのアイテムに付与"), "ench_apply",
                ench.getKey().toString() + ":" + level));
        }
        while (slot < ENCH_PAGE_SIZE) {
            ui.setItem(slot++, this.framePane());
        }
        // Navigation row (slots 45..53).
        for (int s = 45; s <= 53; s++) ui.setItem(s, this.framePane());
        if (page > 0) {
            ui.setItem(45, plugin.actionItem(Material.ARROW, "\u00a77◀ 前のページ",
                List.of("\u00a77ページ " + page + " / " + totalPages), "ench_page", String.valueOf(page - 1)));
        }
        ui.setItem(49, plugin.actionItem(Material.PAPER, "\u00a7f使い方",
            List.of("\u00a771. エンチャントしたいアイテムを", "\u00a77   オフハンドに持つ",
                "\u00a772. 好みのエンチャントをクリック",
                "\u00a77ページ " + (page + 1) + " / " + totalPages), "ench_hint", null));
        if (page + 1 < totalPages) {
            ui.setItem(53, plugin.actionItem(Material.ARROW, "\u00a77次のページ ▶",
                List.of("\u00a77ページ " + (page + 2) + " / " + totalPages), "ench_page", String.valueOf(page + 1)));
        }
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
        if ("ench_page".equals(action)) {
            int page = plugin.parsePositiveInt(plugin.getUiTargetString(clicked), 0);
            this.openEnchantUi(player, page);
            return;
        }
        if (!"ench_apply".equals(action)) return;
        String target = plugin.getUiTargetString(clicked);
        if (target == null) return;
        int split = target.lastIndexOf(':');
        if (split < 0) return;
        String keyStr = target.substring(0, split);
        int level = plugin.parsePositiveInt(target.substring(split + 1), 1);
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(keyStr);
        if (key == null) return;
        Enchantment ench = Registry.ENCHANTMENT.get(key);
        if (ench == null) return;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "エンチャントしたいアイテムをオフハンドに持ってね。");
            return;
        }
        if (isRelicItem(off)) {
            player.sendMessage(ChatColor.RED + "レリックには重ねて付与できない。");
            return;
        }
        off.addUnsafeEnchantment(ench, Math.max(1, level));
        player.getInventory().setItemInOffHand(off);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.sendMessage(ChatColor.GOLD + prettyEnchantName(ench) + " Lv." + level + " を付与した！");
    }

    // ------------------------------------------------------------------
    // Relic grindstone block: placed from the relic item, it returns the
    // ground item's enchantments as books whenever it is used to disenchant.
    // ------------------------------------------------------------------



    @EventHandler
    public void onPlaceRelicGrindstone(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isRelicItem(item) || !"enchgrind".equals(getRelicId(item))) return;
        if (event.getBlockPlaced().getType() != Material.GRINDSTONE) return;
        relicGrindstoneLocations.add(event.getBlockPlaced().getLocation());
        event.getPlayer().sendMessage(ChatColor.GREEN + "錬金の砥石を設置した。エンチャントを剥がすと本に戻る。");
    }

    @EventHandler
    public void onBreakRelicGrindstone(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.GRINDSTONE) return;
        if (!relicGrindstoneLocations.remove(block.getLocation())) return;
        event.setDropItems(false);
        RelicType type = null;
        for (RelicType candidate : RelicType.values()) {
            if (candidate.id.equals("enchgrind")) { type = candidate; break; }
        }
        if (type != null) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createRelicItem(type));
        }
    }

    @EventHandler
    public void onPrepareRelicGrindstone(PrepareGrindstoneEvent event) {
        org.bukkit.Location loc = event.getInventory().getLocation();
        if (loc == null) return;
        Block block = loc.getBlock();
        if (block.getType() != Material.GRINDSTONE) return;
        if (!relicGrindstoneLocations.contains(block.getLocation())) return;
        GrindstoneInventory inv = event.getInventory();
        ItemStack input = inv.getItem(0);
        if (input == null || input.getType().isAir() || input.getEnchantments().isEmpty()) {
            if (!inv.getViewers().isEmpty()) lastGrindInput.remove(inv.getViewers().get(0).getUniqueId());
            return;
        }
        org.bukkit.entity.HumanEntity viewer = inv.getViewers().isEmpty() ? null : inv.getViewers().get(0);
        if (viewer == null) return;
        UUID viewerId = viewer.getUniqueId();
        ItemStack last = lastGrindInput.get(viewerId);
        if (last != null && last.isSimilar(input) && last.getEnchantments().equals(input.getEnchantments())) return;
        for (Map.Entry<Enchantment, Integer> entry : input.getEnchantments().entrySet()) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta bookMeta = book.getItemMeta();
            if (bookMeta instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                book.setItemMeta(storage);
            } else {
                book.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
            if (viewer instanceof Player player) {
                for (ItemStack leftover : player.getInventory().addItem(book).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
        lastGrindInput.put(viewerId, input.clone());
        if (viewer instanceof Player player) {
            player.sendMessage(ChatColor.GREEN + "錬金の砥石がエンチャントを本に戻した！");
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isRelicItem(item)) item = player.getInventory().getItemInOffHand();
        if (!isRelicItem(item) || !"hookshot".equals(getRelicId(item))) return;


        PlayerFishEvent.State state = event.getState();
        FishHook hook = event.getHook();
        if (state == PlayerFishEvent.State.FISHING) {
            this.clearHookshotAnchor(player);
            this.startHookshotFlight(player, hook);
            return;
        }
        if (state == PlayerFishEvent.State.REEL_IN) {
            UUID playerId = player.getUniqueId();
            if (this.hookshotPulls.containsKey(playerId)) {
                // Second right-click while reeling: stop the pull where it is,
                // keep the current inertia, and retract the line immediately.
                this.stopHookshotPull(playerId);
                this.clearHookshotAnchor(player);
                event.setCancelled(true);
                return;
            }
            HookAnchor anchor = this.hookshotAnchors.get(playerId);
            this.stopHookshotFlight(hook.getUniqueId());
            if (anchor != null && anchor.hookId().equals(hook.getUniqueId())) {
                // Keep the hook and line attached until the player arrives.
                event.setCancelled(true);
                this.startHookshotPull(player, anchor.location());
                return;
            }
            return;
        }
        if (state == PlayerFishEvent.State.IN_GROUND) {
            // Cancel vanilla's auto-retract; the bobber must stay pinned.
            event.setCancelled(true);
            if (!this.hookshotAnchors.containsKey(player.getUniqueId())) {
                this.anchorHookshot(player, hook, hook.getLocation());
            }
            return;
        }
        if (state == PlayerFishEvent.State.CAUGHT_ENTITY
                || state == PlayerFishEvent.State.CAUGHT_FISH) {
            event.setCancelled(true);
            this.stopHookshotFlight(hook.getUniqueId());
            if (!this.hookshotAnchors.containsKey(player.getUniqueId())) {
                this.anchorHookshot(player, hook, hook.getLocation());
            }
            return;
        }
        this.stopHookshotFlight(hook.getUniqueId());
    }

    /**
     * Keeps the hookshot bobber on a straight line and stops it as soon as any
     * solid block is within a small radius. A stopped bobber stays in place
     * until the player right-clicks to reel themselves toward it.
     */
    private void startHookshotFlight(Player player, FishHook hook) {
        this.stopHookshotFlight(hook.getUniqueId());
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(HOOKSHOT_SPEED);
        hook.setGravity(false);
        hook.setVelocity(direction);
        int[] ticks = {0};
        Location[] lastSafe = {hook.getLocation().clone()};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!hook.isValid() || hook.isDead() || ++ticks[0] > 200) {
                this.stopHookshotFlight(hook.getUniqueId());
                if (hook.isValid()) hook.remove();
                return;
            }
            if (hook.getLocation().distanceSquared(player.getLocation()) > HOOKSHOT_MAX_RANGE * HOOKSHOT_MAX_RANGE) {
                this.stopHookshotFlight(hook.getUniqueId());
                if (hook.isValid()) hook.remove();
                return;
            }
            Location current = hook.getLocation().clone();
            // Give the bobber a few ticks to leave the player's body, then
            // stop it on floor, wall, ceiling or any other solid block.
            if (ticks[0] > 3) {
                if (this.hookshotBlocked(current)) {
                    this.anchorHookshot(player, hook, lastSafe[0]);
                    return;
                }
                // Sample the flight step every quarter block so a thin wall
                // cannot be skipped even at high bobber speed, and stop at the
                // last free position just before the block.
                Vector unit = direction.clone().normalize();
                int samples = Math.max(1, (int) Math.ceil(direction.length() / 0.25));
                Location safe = current.clone();
                for (int i = 1; i <= samples; i++) {
                    Location probe = current.clone().add(unit.clone().multiply(i * 0.25));
                    if (this.hookshotBlocked(probe)) {
                        this.anchorHookshot(player, hook, safe);
                        return;
                    }
                    safe = probe;
                }
            }
            lastSafe[0] = current;
            hook.setVelocity(direction);
            if (ticks[0] % 2 == 0) this.drawHookshotLine(player, current);
        }, 1L, 1L);
        this.hookshotFlights.put(hook.getUniqueId(), task);
    }

    /**
     * The vanilla fishing line stops rendering once the bobber leaves the
     * client's entity/particle range. Draw a forced particle line so the
     * 100-block hookshot stays visibly connected.
     */
    private void drawHookshotLine(Player player, Location target) {
        Location from = player.getEyeLocation().clone();
        Vector span = target.toVector().subtract(from.toVector());
        double distance = span.length();
        if (distance < 0.5) return;
        Vector step = span.normalize().multiply(HOOKSHOT_LINE_STEP);
        int points = (int) Math.ceil(distance / HOOKSHOT_LINE_STEP);
        for (int i = 0; i <= points; i++) {
            Location point = from.clone().add(step.clone().multiply(i));
            player.spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0, true);
        }
    }

    /** True when a solid (non-passable) block is within the bobber's radius. */
    boolean hookshotBlocked(Location location) {
        World world = location.getWorld();
        if (world == null) return true;
        int minX = (int) Math.floor(location.getX() - HOOKSHOT_BLOCK_RADIUS);
        int maxX = (int) Math.floor(location.getX() + HOOKSHOT_BLOCK_RADIUS);
        int minY = (int) Math.floor(location.getY() - HOOKSHOT_BLOCK_RADIUS);
        int maxY = (int) Math.floor(location.getY() + HOOKSHOT_BLOCK_RADIUS);
        int minZ = (int) Math.floor(location.getZ() - HOOKSHOT_BLOCK_RADIUS);
        int maxZ = (int) Math.floor(location.getZ() + HOOKSHOT_BLOCK_RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.getBlockAt(x, y, z).isPassable()) return true;
                }
            }
        }
        return false;
    }

    private void anchorHookshot(Player player, FishHook hook, Location location) {
        this.stopHookshotFlight(hook.getUniqueId());
        this.stopHookshotHold(hook.getUniqueId());
        hook.teleport(location);
        hook.setGravity(false);
        hook.setVelocity(new Vector(0.0, 0.0, 0.0));
        HookAnchor anchor = new HookAnchor(location.clone(), hook.getUniqueId());
        this.hookshotAnchors.put(player.getUniqueId(), anchor);
        // Vanilla fishing-hook physics still applies drag/gravity and can make
        // the bobber drift. Pin it to the contact point until it is reeled in.
        int[] holdTicks = {0};
        BukkitTask hold = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            HookAnchor current = this.hookshotAnchors.get(player.getUniqueId());
            if (current == null || !current.hookId().equals(hook.getUniqueId())
                    || !hook.isValid() || hook.isDead()) {
                this.stopHookshotHold(hook.getUniqueId());
                return;
            }
            if (!hook.getWorld().equals(current.location().getWorld())
                    || hook.getLocation().distanceSquared(current.location()) > 0.0025) {
                hook.teleport(current.location());
            }
            hook.setGravity(false);
            hook.setVelocity(new Vector(0.0, 0.0, 0.0));
            if (++holdTicks[0] % 2 == 0) this.drawHookshotLine(player, current.location());
        }, 1L, 1L);
        this.hookshotHolds.put(hook.getUniqueId(), hold);
        player.getWorld().playSound(location, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, location, 16, 0.25, 0.25, 0.25, 0.1);
        player.sendMessage(ChatColor.GRAY + "ウキがブロックに当たった。右クリックで引き寄せ。");
    }

    private void stopHookshotHold(UUID hookId) {
        BukkitTask task = this.hookshotHolds.remove(hookId);
        if (task != null) task.cancel();
    }

    /**
     * Reels the player along the line to the anchored bobber instead of
     * launching a single jump impulse. The task keeps steering every tick so
     * the player actually ends up at the hook.
     */
    private void startHookshotPull(Player player, Location hookLocation) {
        UUID uuid = player.getUniqueId();
        this.stopHookshotPull(uuid);
        // The bobber is at eye-ish height; put the player's feet one block
        // below it so their body arrives at the hook rather than above it.
        Location destination = hookLocation.clone().add(0.0, -1.0, 0.0);
        jetpackActive.remove(uuid);
        landingAssist.remove(uuid);
        int[] ticks = {0};
        double[] lastDistance = {Double.MAX_VALUE};
        int[] stuck = {0};
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.2f);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline() || current.isDead()
                    || ++ticks[0] > 160 || !current.getWorld().equals(destination.getWorld())) {
                this.clearHookshotAnchor(player);
                return;
            }
            Vector delta = destination.toVector().subtract(current.getLocation().toVector());
            double distance = delta.length();
            if (distance <= 1.0) {
                current.setVelocity(new Vector(0.0, 0.0, 0.0));
                current.setFallDistance(0.0f);
                current.getWorld().playSound(current.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.8f);
                // Arrived: now retract the line and free the rod for a new cast.
                this.clearHookshotAnchor(current);
                return;
            }
            if (distance >= lastDistance[0] - 0.02) {
                if (++stuck[0] > 20) {
                    this.clearHookshotAnchor(current);
                    return;
                }
            } else {
                stuck[0] = 0;
            }
            lastDistance[0] = distance;
            current.setVelocity(delta.normalize().multiply(Math.min(HOOKSHOT_PULL_SPEED, distance)));
            current.setFallDistance(0.0f);
            if (ticks[0] % 2 == 0) {
                current.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    current.getLocation().add(0.0, 1.0, 0.0), 3, 0.2, 0.2, 0.2, 0.02);
            }
        }, 1L, 1L);
        this.hookshotPulls.put(uuid, task);
    }

    private void stopHookshotPull(UUID playerId) {
        BukkitTask task = this.hookshotPulls.remove(playerId);
        if (task != null) task.cancel();
    }

    private void clearHookshotAnchor(Player player) {
        this.stopHookshotPull(player.getUniqueId());
        HookAnchor anchor = this.hookshotAnchors.remove(player.getUniqueId());
        if (anchor == null) return;
        this.stopHookshotHold(anchor.hookId());
        org.bukkit.entity.Entity hook = Bukkit.getEntity(anchor.hookId());
        if (hook != null) hook.remove();
    }

    private void stopHookshotFlight(UUID hookId) {
        BukkitTask task = this.hookshotFlights.remove(hookId);
        if (task != null) task.cancel();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onChainDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!this.holdsRelic(player, "chain")) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        event.setCancelled(true);
        this.toggleChain(player, target);
    }

    /** Left-click attack hook: fires even where damage events are suppressed. */
    @EventHandler
    public void onChainAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (!this.holdsRelic(player, "chain")) return;
        if (!(event.getAttacked() instanceof org.bukkit.entity.LivingEntity target)) return;
        event.setCancelled(true);
        this.toggleChain(player, target);
    }

    /** Right-click only hooks a new mob; it never releases an existing chain. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onChainInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!this.holdsRelic(player, "chain")) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.LivingEntity target)) return;
        event.setCancelled(true);
        this.bindChainOnly(player, target);
    }

    /** Left-click in the air can hook a mob up to CHAIN_BIND_RANGE blocks away. */
    private void tryChainAtRange(Player player) {
        org.bukkit.entity.Entity found = player.getTargetEntity((int) CHAIN_BIND_RANGE, false);
        if (found instanceof org.bukkit.entity.LivingEntity target && target != player) {
            this.toggleChain(player, target);
            return;
        }
        // Sneak + left-click is the panic release; a normal miss does nothing.
        if (player.isSneaking()) this.releaseAllChains(player);
    }

    private void toggleChain(Player player, org.bukkit.entity.LivingEntity target) {
        this.changeChain(player, target, true);
    }

    private void bindChainOnly(Player player, org.bukkit.entity.LivingEntity target) {
        this.changeChain(player, target, false);
    }

    /** Binds or releases a mob; tracked for the drag-follow task either way. */
    private void changeChain(Player player, org.bukkit.entity.LivingEntity target, boolean allowRelease) {
        long now = System.currentTimeMillis();
        Long lastToggle = chainToggleMs.get(player.getUniqueId());
        if (lastToggle != null && now - lastToggle < 400L) return; // two events, one click
        chainToggleMs.put(player.getUniqueId(), now);
        UUID pid = player.getUniqueId();
        java.util.Set<UUID> followers = dragFollowers.get(pid);
        boolean held = followers != null && followers.contains(target.getUniqueId());
        if (held) {
            if (!allowRelease) return;
            followers.remove(target.getUniqueId());
            this.restoreChainAI(target.getUniqueId());
            if (followers.isEmpty()) { dragFollowers.remove(pid); this.stopDragTask(); }
            player.sendMessage(ChatColor.GRAY + "鎖を解いた。");
        } else {
            if (!this.chainInRange(player, target)) {
                player.sendMessage(ChatColor.GRAY + "鎖が届かない。(最大" + (int) CHAIN_BIND_RANGE + "ブロック)");
                return;
            }
            followers = dragFollowers.computeIfAbsent(pid, k -> new java.util.HashSet<>());
            // The chain itself is the only tether: no vanilla lead is created.
            if (target.isLeashed()) { try { target.setLeashHolder(null); } catch (Exception ignored) { } }
            followers.add(target.getUniqueId());
            // Immobilize the bound mob (no wandering/attacks) until released.
            chainHadAI.put(target.getUniqueId(), target.hasAI());
            target.setAI(false);
            this.startDragTask();
            player.sendMessage(ChatColor.GRAY + "鎖で拘束した。もう一度左クリックで解ける。");
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2.0, 0), 16, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
    }

    private boolean chainInRange(Player player, org.bukkit.entity.LivingEntity target) {
        if (!target.getWorld().equals(player.getWorld())) return false;
        org.bukkit.Location center = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
        return player.getEyeLocation().distanceSquared(center) <= CHAIN_BIND_RANGE * CHAIN_BIND_RANGE;
    }

    /** Releases every mob bound by this player. */
    private void releaseAllChains(Player player) {
        java.util.Set<UUID> followers = dragFollowers.remove(player.getUniqueId());
        if (followers == null || followers.isEmpty()) return;
        for (UUID followerId : followers) this.restoreChainAI(followerId);
        if (dragFollowers.isEmpty()) this.stopDragTask();
        player.sendMessage(ChatColor.GRAY + "鎖をすべて解いた。");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
    }

    private void restoreChainAI(UUID followerId) {
        Boolean hadAI = chainHadAI.remove(followerId);
        if (hadAI != null && hadAI && Bukkit.getEntity(followerId) instanceof org.bukkit.entity.LivingEntity mob) {
            mob.setAI(true);
        }
    }

    /** Draws the chain every tick so it reads as a continuous tether. */
    private void startDragTask() {
        if (this.dragTask != null) return;
        this.dragTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickChains, 1L, 1L);
    }

    /** Pulls drag-bound followers toward the player with eased, per-tick motion. */
    private void tickChains() {
        for (java.util.Map.Entry<java.util.UUID, java.util.Set<java.util.UUID>> entry : List.copyOf(dragFollowers.entrySet())) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            if (owner == null || entry.getValue().isEmpty()) {
                if (owner == null) {
                    for (UUID followerId : List.copyOf(entry.getValue())) this.restoreChainAI(followerId);
                }
                dragFollowers.remove(entry.getKey());
                continue;
            }
            for (java.util.UUID followerId : List.copyOf(entry.getValue())) {
                if (!(Bukkit.getEntity(followerId) instanceof org.bukkit.entity.LivingEntity follower) || follower.isDead()) {
                    entry.getValue().remove(followerId);
                    chainHadAI.remove(followerId);
                    continue;
                }
                if (!follower.getWorld().equals(owner.getWorld())) {
                    follower.teleport(owner.getLocation().add(0.0, 0.5, 0.0));
                    follower.setFallDistance(0.0f);
                    continue;
                }
                this.drawChainEffect(owner, follower);
                this.pullChainedMob(owner, follower);
            }
            if (entry.getValue().isEmpty()) dragFollowers.remove(entry.getKey());
        }
        if (dragFollowers.isEmpty()) this.stopDragTask();
    }

    private void drawChainEffect(Player owner, org.bukkit.entity.LivingEntity follower) {
        org.bukkit.Location from = owner.getLocation().add(0.0, 1.0, 0.0);
        org.bukkit.Location to = follower.getLocation().add(0.0, follower.getHeight() / 2.0, 0.0);
        org.bukkit.util.Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length < 0.1) return;
        org.bukkit.util.Vector direction = line.clone().multiply(1.0 / length);
        // Keep it dense up close, but bound the particle count on very long chains.
        double stepSize = length > 32.0 ? length / 72.0 : 0.45;
        int index = 0;
        for (double d = 0.2; d < length; d += stepSize, index++) {
            org.bukkit.Location point = from.clone().add(direction.clone().multiply(d));
            owner.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, point, 1, 0.0, 0.0, 0.0, 0.0);
            if ((index & 3) == 0) {
                owner.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void pullChainedMob(Player owner, org.bukkit.entity.LivingEntity follower) {
        org.bukkit.Location followerLocation = follower.getLocation();
        org.bukkit.util.Vector delta = owner.getLocation().toVector().subtract(followerLocation.toVector());
        double horizontal = Math.hypot(delta.getX(), delta.getZ());
        org.bukkit.util.Vector velocity = follower.getVelocity();

        if (horizontal > CHAIN_FOLLOW_STOP) {
            double speed = Math.min(CHAIN_MAX_PULL_SPEED, (horizontal - CHAIN_FOLLOW_STOP) * 0.12);
            double targetX = delta.getX() / horizontal * speed;
            double targetZ = delta.getZ() / horizontal * speed;
            // Ease toward the target speed instead of snapping every tick.
            velocity.setX(velocity.getX() + (targetX - velocity.getX()) * 0.35);
            velocity.setZ(velocity.getZ() + (targetZ - velocity.getZ()) * 0.35);
        } else {
            velocity.setX(velocity.getX() * 0.7);
            velocity.setZ(velocity.getZ() * 0.7);
        }

        double dy = owner.getLocation().getY() - followerLocation.getY();
        if (follower.isOnGround()) {
            if (dy > 1.0) velocity.setY(0.42);
        } else if (dy > 0.8) {
            velocity.setY(Math.min(0.5, velocity.getY() + 0.06));
        }
        follower.setVelocity(velocity);
        follower.setFallDistance(0.0f);

        if (horizontal > 0.2) {
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
            follower.setRotation(yaw, follower.getLocation().getPitch());
        }
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
        this.tickDashGrounding(player);
        this.enforceSeaswimSpeed(player);
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
        boolean pulling = hookshotPulls.containsKey(player.getUniqueId());
        if (!pulling && jetpackActive.contains(player.getUniqueId())) {
            this.tryHoverJetpack(player);
        }
        if (!pulling && landingAssist.contains(player.getUniqueId())) {
            if (player.isOnGround()) {
                // Soft touchdown: landing while holding the assist never hurts.
                player.setFallDistance(0.0f);
            } else {
                tryGentleDescent(player);
            }
        }
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

    /** Background upkeep: totem buffs and stable air-swim control. */
    private void tickPassive() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.tickDashGrounding(p);
            if (this.holdsRelic(p, "totem")) {
                org.bukkit.potion.PotionEffect resist = p.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
                if (resist == null || resist.getDuration() <= 20) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 1, true, false));
                }
                org.bukkit.potion.PotionEffect regen = p.getPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
                if (regen == null || regen.getDuration() <= 20) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION, 60, 0, true, false));
                }
            }
            this.tickSeaswim(p);
        }
    }

    /** Bunny hop is reusable only after leaving the ground and landing again. */
    private void tickDashGrounding(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        if (!this.dashWaitingForGround.contains(uuid)) return;
        if (!player.isOnGround()) {
            this.dashAirborne.add(uuid);
            return;
        }
        Long started = this.dashStarted.get(uuid);
        boolean neverLeftGround = started != null && System.currentTimeMillis() - started > 3000L;
        if (this.dashAirborne.remove(uuid) || neverLeftGround) {
            this.dashWaitingForGround.remove(uuid);
            this.dashStarted.remove(uuid);
            player.setCooldown(Material.RABBIT_FOOT, 0);
        }
    }

    /**
     * Air swimming is active only while holding the orb, airborne, out of
     * water, and not using another airborne ability. Jetpack thrust and
     * bunny-hop arcs suppress the whole swim state (speed, pose, levitation),
     * otherwise they feel much slower than intended.
     */
    static boolean shouldAirSwim(boolean held, boolean onGround, boolean inWater,
                                 boolean jetpacking, boolean dashing) {
        return held && !onGround && !inWater && !jetpacking && !dashing;
    }

    /** Apply the water-swim horizontal speed while the player is air-swimming. */
    void applySeaswimSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        for (AttributeModifier modifier : new ArrayList<>(speed.getModifiers())) {
            if (this.seaswimSpeedKey.equals(modifier.getKey())) return;
        }
        speed.addModifier(new AttributeModifier(this.seaswimSpeedKey,
            SEASWIM_SPEED_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
            EquipmentSlotGroup.ANY));
    }

    /**
     * Move-path safety net: normal walking, water, jetpack and bunny-hop
     * states must never keep the x0.4 air-swim modifier.
     */
    private void enforceSeaswimSpeed(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.isOnGround() || player.isInWater() || !this.hasSeaswim(player)
                || jetpackActive.contains(uuid) || dashWaitingForGround.contains(uuid)
                || hookshotPulls.containsKey(uuid)) {
            this.removeSeaswimSpeed(player);
        }
    }

    /** Restore normal movement as soon as the swim ends. */
    void removeSeaswimSpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        for (AttributeModifier modifier : new ArrayList<>(speed.getModifiers())) {
            if (this.seaswimSpeedKey.equals(modifier.getKey())) {
                speed.removeModifier(modifier.getUniqueId());
            }
        }
        // Belt-and-braces: Paper's keyed removal also catches a modifier whose
        // UUID lookup was skipped by an API change.
        try {
            speed.removeModifier(this.seaswimSpeedKey);
        } catch (NoSuchMethodError ignored) {
            // Older API: the UUID removal above is the only option.
        }
    }

    /** Stable air swim: keep one pose while held; only clamp while airborne. */
    private void tickSeaswim(Player p) {
        UUID uuid = p.getUniqueId();
        boolean held = this.hasSeaswim(p);
        boolean jetpacking = jetpackActive.contains(uuid) || hookshotPulls.containsKey(uuid);
        boolean dashing = dashWaitingForGround.contains(uuid);
        // Real water/lava swimming keeps vanilla physics. Jetpack, bunny hop
        // and the hookshot reel also suppress the swim state entirely,
        // including the slow
        // movement modifier and the swimming pose.
        if (!shouldAirSwim(held, p.isOnGround(), p.isInWater(), jetpacking, dashing)) {
            this.seaswimRising.remove(uuid);
            boolean wasActive = this.seaswimActive.remove(uuid);
            boolean poseInjected = this.poseInjected.remove(uuid);
            if (wasActive) {
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
                p.setFallDistance(0.0f);
            }
            this.removeSeaswimSpeed(p);
            if (poseInjected) this.sendPose(p, "STANDING");
            this.lastServerPose.remove(uuid);
            if (wasActive || poseInjected) {
                p.setSwimming(false);
                if (p.getPose() == org.bukkit.entity.Pose.SWIMMING) {
                    p.setPose(org.bukkit.entity.Pose.STANDING);
                }
            }
            return;
        }
        this.seaswimActive.add(uuid);
        this.applySeaswimSpeed(p);
        boolean rising = this.seaswimRising.contains(uuid);
        if (rising) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
            org.bukkit.potion.PotionEffect levitation = p.getPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            if (levitation == null || levitation.getDuration() <= 20) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.LEVITATION, 40, SEASWIM_ASCEND_AMPLIFIER, true, false));
            }
        } else {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
            org.bukkit.potion.PotionEffect slow = p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
            if (slow == null || slow.getDuration() <= 20) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW_FALLING, 40, 0, true, false));
            }
            p.setFallDistance(0.0f);
        }
        // Client-only swim pose. Send once (and again only when the server's
        // own pose changes), otherwise re-sending restarts the animation.
        org.bukkit.entity.Pose serverPose = p.getPose();
        org.bukkit.entity.Pose previousPose = this.lastServerPose.put(uuid, serverPose);
        if (!this.poseInjected.contains(uuid) || (previousPose != null && previousPose != serverPose)) {
            this.sendPose(p, "SWIMMING");
            this.poseInjected.add(uuid);
        }
        p.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE, p.getLocation().add(0.0, 1.0, 0.0),
            2, 0.35, 0.45, 0.35, 0.0);
    }

    /** Resolves the NMS pose bridge once; returns true when usable. */
    private boolean initPoseReflection() {
        if (this.poseReflectInit) return this.poseReflectOk;
        this.poseReflectInit = true;
        try {
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            java.lang.reflect.Field poseField = entityClass.getDeclaredField("DATA_POSE");
            poseField.setAccessible(true);
            this.poseAccessor = poseField.get(null);
            this.poseClass = Class.forName("net.minecraft.world.entity.Pose");
            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            this.dataValueCtor = dataValueClass.getConstructor(int.class,
                Class.forName("net.minecraft.network.syncher.EntityDataSerializer"), Object.class);
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            this.packetCtor = packetClass.getConstructor(int.class, java.util.List.class);
            this.packetBaseClass = Class.forName("net.minecraft.network.protocol.Packet");
            this.poseReflectOk = true;
        } catch (Throwable t) {
            this.poseReflectOk = false;
            if (!this.poseReflectWarned) {
                this.poseReflectWarned = true;
                this.plugin.getLogger().warning("Swim pose packet bridge unavailable: " + t);
            }
        }
        return this.poseReflectOk;
    }

    /** Sends a client-only pose packet so the server cannot fight it each tick. */
    private void sendPose(Player player, String poseName) {
        try {
            if (!this.initPoseReflection()) return;
            Object pose = this.poseClass.getField(poseName).get(null);
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            int entityId = (int) handle.getClass().getMethod("getId").invoke(handle);
            int accessorId = 0;
            try {
                accessorId = (int) this.poseAccessor.getClass().getMethod("getId").invoke(this.poseAccessor);
            } catch (NoSuchMethodException e) {
                try { accessorId = (int) this.poseAccessor.getClass().getMethod("id").invoke(this.poseAccessor); }
                catch (NoSuchMethodException ignored) { }
            }
            Object serializer;
            try {
                serializer = this.poseAccessor.getClass().getMethod("serializer").invoke(this.poseAccessor);
            } catch (NoSuchMethodException e) {
                serializer = this.poseAccessor.getClass().getMethod("getSerializer").invoke(this.poseAccessor);
            }
            Object dataValue = this.dataValueCtor.newInstance(accessorId, serializer, pose);
            Object packet = this.packetCtor.newInstance(entityId, java.util.List.of(dataValue));
            Object connection;
            try {
                java.lang.reflect.Field connField = handle.getClass().getDeclaredField("connection");
                connField.setAccessible(true);
                connection = connField.get(handle);
            } catch (NoSuchFieldException e) {
                connection = handle.getClass().getField("connection").get(handle);
            }
            connection.getClass().getMethod("send", this.packetBaseClass).invoke(connection, packet);
        } catch (Throwable t) {
            this.poseReflectOk = false;
            if (!this.poseReflectWarned) {
                this.poseReflectWarned = true;
                this.plugin.getLogger().warning("Swim pose packet bridge failed: " + t);
            }
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

    /** True if either hand holds the given relic id. */
    private boolean holdsRelic(Player player, String id) {
        for (ItemStack hand : new ItemStack[]{player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
            if (hand != null && isRelicItem(hand) && id.equals(getRelicId(hand))) return true;
        }
        return false;
    }

    /** Creative-inventory picks roll the same chance as a normal pickup. */
    @EventHandler
    public void onCreativePickup(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getType() != org.bukkit.event.inventory.InventoryType.CREATIVE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            ItemStack cursor = player.getItemOnCursor();
            if (cursor == null || cursor.getType().isAir()) return;
            ItemStack before = cursor.clone();
            this.tryTransform(cursor, player);
            if (!cursor.equals(before)) player.setItemOnCursor(cursor);
        });
    }

    /** /give grants roll the same chance as a normal pickup. */
    @EventHandler
    public void onGiveCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("/give ") && !msg.startsWith("/minecraft:give ")) return;
        String[] parts = msg.split("\\s+");
        if (parts.length < 3) return;
        Material mat = Material.matchMaterial(parts[2]);
        if (mat == null) return;
        boolean matches = false;
        for (RelicType type : RelicType.values()) {
            if (type.baseMaterial == mat) { matches = true; break; }
        }
        if (!matches) return;
        Player target = event.getPlayer();
        if (parts.length >= 2) {
            Player named = Bukkit.getPlayerExact(parts[1]);
            if (named != null) target = named;
        }
        final Player finalTarget = target;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            org.bukkit.inventory.Inventory inv = finalTarget.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.getType() != mat) continue;
                this.tryTransform(stack, finalTarget);
                if (stack != null) inv.setItem(i, stack);
                break;
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.stopScopedVision(uuid);
        this.clearGhostIce(uuid);
        event.getPlayer().setSwimming(false);
        if (event.getPlayer().getPose() == org.bukkit.entity.Pose.SWIMMING) {
            event.getPlayer().setPose(org.bukkit.entity.Pose.STANDING);
        }
        this.removeSeaswimSpeed(event.getPlayer());
        seaswimActive.remove(uuid);
        seaswimAirborne.remove(uuid);
        poseInjected.remove(uuid);
        seaswimRising.remove(uuid);
        dashWaitingForGround.remove(uuid);
        dashAirborne.remove(uuid);
        dashStarted.remove(uuid);
        this.clearHookshotAnchor(event.getPlayer());
        AttributeInstance attr = this.scaleAttribute(event.getPlayer());
        if (attr != null && scaleExpiry.remove(uuid) != null) attr.setBaseValue(1.0);
        java.util.Set<UUID> chained = dragFollowers.remove(uuid);
        if (chained != null) {
            for (UUID mobId : chained) this.restoreChainAI(mobId);
        }
        if (dragFollowers.isEmpty()) this.stopDragTask();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.clearGhostIce(event.getPlayer().getUniqueId());
        this.clearHookshotAnchor(event.getPlayer());
        this.resetSeaswimState(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.resetSeaswimState(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.resetSeaswimState(event.getPlayer());
    }

    /** Clears any stale swim speed/pose, including after a crash or relog. */
    private void resetSeaswimState(Player player) {
        UUID uuid = player.getUniqueId();
        this.stopHookshotPull(uuid);
        seaswimActive.remove(uuid);
        seaswimAirborne.remove(uuid);
        seaswimRising.remove(uuid);
        poseInjected.remove(uuid);
        lastServerPose.remove(uuid);
        this.removeSeaswimSpeed(player);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        player.setSwimming(false);
        if (player.getPose() == org.bukkit.entity.Pose.SWIMMING) {
            player.setPose(org.bukkit.entity.Pose.STANDING);
        }
    }

    @EventHandler
    public void onPlayerInput(org.bukkit.event.player.PlayerInputEvent event) {
        Player player = event.getPlayer();
        ItemStack chest = player.getInventory().getChestplate();
        boolean jetpack = chest != null && isRelicItem(chest) && "jetpack".equals(getRelicId(chest));
        boolean pulling = this.hookshotPulls.containsKey(player.getUniqueId());
        // The hold flags only mark intent: the climb/float itself is applied
        // on every move event so it follows the player smoothly.
        if (jetpack && !pulling && event.getInput().isJump()) {
            jetpackActive.add(player.getUniqueId());
        } else {
            jetpackActive.remove(player.getUniqueId());
        }
        // Back (S) key: gentle landing assist flag; the hop loop lives in the
        // move-event ticker so a steadily held key keeps floating the player.
        if (jetpack && !pulling && event.getInput().isBackward()) {
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
    static final double JETPACK_CLIMB_SPEED = 1.00;
    /** Speed added per move tick while holding Space (grows to the cap). */
    static final double JETPACK_CLIMB_ACCEL = 0.50;
    /** Minimum upward speed guaranteed while holding Space (overcomes gravity). */
    static final double JETPACK_CLIMB_FLOOR = 0.50;

    /** Caps vertical speed to a gentle climb; existing rises are untouched. */
    static double climbY(double currentY) {
        if (currentY >= JETPACK_CLIMB_SPEED) return currentY;
        // Never let fall speed cancel the thrust: floor the result upward.
        return Math.min(Math.max(currentY + JETPACK_CLIMB_ACCEL, JETPACK_CLIMB_FLOOR), JETPACK_CLIMB_SPEED);
    }

    /** Forward speed cap while the jetpack pushes (blocks/tick). */
    static final double JETPACK_FORWARD_SPEED = 0.85;
    /** Forward acceleration per tick (blocks/tick). */
    static final double JETPACK_FORWARD_ACCEL = 0.14;
    /** Fuel burn cadence while thrusting (ms). Thrust runs every tick. */
    static final long JETPACK_FUEL_MILLIS = 1000L;

    /**
     * Hold-Space thrust: applied every tick so gravity never has a gap to
     * pull the player back down (the old 800ms throttle made it bounce).
     * Fuel is only burned once per second.
     */
    private void tryHoverJetpack(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !isRelicItem(chest) || !"jetpack".equals(getRelicId(chest))) return;
        if (!jetpackActive.contains(player.getUniqueId())) return;
        if (!player.getInventory().contains(Material.BLAZE_POWDER)) {
            player.sendMessage(ChatColor.RED + "ジェットパックの燃料(ブレイズパウダー)がありません！");
            jetpackActive.remove(player.getUniqueId());
            return;
        }
        Vector vel = player.getVelocity();
        boolean onGround = player.isOnGround();
        // Vertical: smooth sustained climb every tick.
        double targetY = onGround ? 0.9 : climbY(vel.getY());
        // Forward: accelerate along the look direction up to the cap.
        Vector wish = player.getLocation().getDirection();
        wish.setY(0.0);
        if (wish.lengthSquared() > 1.0E-9) {
            wish.normalize();
            double along = vel.getX() * wish.getX() + vel.getZ() * wish.getZ();
            if (along < JETPACK_FORWARD_SPEED) {
                double add = Math.min(JETPACK_FORWARD_ACCEL, JETPACK_FORWARD_SPEED - along);
                vel.setX(vel.getX() + wish.getX() * add);
                vel.setZ(vel.getZ() + wish.getZ() * add);
            }
        }
        player.setVelocity(new Vector(vel.getX(), targetY, vel.getZ()));
        player.setFallDistance(0.0f);
        // Fuel + effects throttled to once a second while thrusting.
        long now = System.currentTimeMillis();
        Long last = lastJetpackBoost.get(player.getUniqueId());
        if (last == null || now - last >= JETPACK_FUEL_MILLIS) {
            player.getInventory().removeItem(new ItemStack(Material.BLAZE_POWDER, 1));
            lastJetpackBoost.put(player.getUniqueId(), now);
            player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0.0, 0.5, 0.0), 10, 0.3, 0.2, 0.3, 0.04);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.0f);
        }
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
