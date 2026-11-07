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
import org.bukkit.entity.Display;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    private final NamespacedKey hellfireKey;
    private final NamespacedKey excaliburReplicaUsesKey;
    private final NamespacedKey sixshotAmmoKey;
    private final NamespacedKey noahsArkKey;
    private final NamespacedKey excaliburHealthKey;
    private final NamespacedKey endlessKey;
    private final NamespacedKey sniperZoomKey;
    private final NamespacedKey armorPenaltyKey;
    private final NamespacedKey bundleIdKey;
    private final java.io.File bundleFile;
    private final Map<UUID, List<ItemStack>> bundleContents = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, UUID> openBundleViewers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Integer> bundlePages = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ClockSnapshot> clockSnapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> maceAscended = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> maceLandingProtectUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> maceLaunchUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Double> maceLastFallSpeed = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> gaybolgCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, UUID> gaybolgDashTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> gaybolgDashTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> excaliburCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> excaliburReplicaCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> excaliburReviveCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> sniperDrawStart = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> pendingShotArrows = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<org.bukkit.Location, ItemStack> endlessBrewIngredients = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> excaliburHealthActive = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record ClockSnapshot(Location location, double health, int food, float saturation,
                                 int level, float exp, ItemStack[] inventory, int heldSlot,
                                 java.util.List<org.bukkit.potion.PotionEffect> effects,
                                 org.bukkit.GameMode gameMode) { }

    public RelicFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keyRelic = new NamespacedKey(plugin, "relic_id");
        this.relicGrindstoneKey = new NamespacedKey(plugin, "relic_grindstone");
        this.seaswimSpeedKey = new NamespacedKey(plugin, "seaswim_speed");
        this.hellfireKey = new NamespacedKey(plugin, "hellfire");
        this.excaliburReplicaUsesKey = new NamespacedKey(plugin, "excalibur_replica_uses");
        this.sixshotAmmoKey = new NamespacedKey(plugin, "sixshot_ammo");
        this.noahsArkKey = new NamespacedKey(plugin, "noahs_ark");
        this.excaliburHealthKey = new NamespacedKey(plugin, "excalibur_health");
        this.endlessKey = new NamespacedKey(plugin, "endless");
        this.sniperZoomKey = new NamespacedKey(plugin, "sniper_zoom_speed");
        this.armorPenaltyKey = new NamespacedKey(plugin, "armor_movement_penalty");
        this.bundleIdKey = new NamespacedKey(plugin, "bundle_id");
        this.bundleFile = new java.io.File(plugin.getDataFolder(), "infinite-bundles.yml");
        this.loadBundles();
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
        this.saveBundles();
        if (this.magnetTask != null) { this.magnetTask.cancel(); this.magnetTask = null; }
        if (this.passiveTask != null) { this.passiveTask.cancel(); this.passiveTask = null; }
        for (BukkitTask task : this.gaybolgDashTasks.values()) task.cancel();
        this.gaybolgDashTasks.clear();
        this.gaybolgDashTargets.clear();
        this.pendingShotArrows.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.clearExcaliburHealth(player);
            this.removeSniperZoom(player);
            this.removeArmorMovementPenalty(player);
        }
        for (java.util.Set<UUID> followers : dragFollowers.values()) {
            for (UUID followerId : followers) this.restoreChainAI(followerId);
        }
        dragFollowers.clear();
        this.stopDragTask();
        for (UUID markerId : List.copyOf(this.hookshotFlights.keySet())) {
            org.bukkit.entity.Entity marker = Bukkit.getEntity(markerId);
            if (marker != null) marker.remove();
        }
        for (HookAnchor anchor : List.copyOf(this.hookshotAnchors.values())) {
            org.bukkit.entity.Entity marker = Bukkit.getEntity(anchor.hookId());
            if (marker != null) marker.remove();
        }
        this.hookshotFlightOwners.clear();
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
        JETPACK("jetpack", "§3§lジェットパック", Material.CHAINMAIL_CHESTPLATE, MaterialRarity.UNCOMMON, RelicPower.MAJOR, "§7ブレイズパウダーを燃料に、スペース長押しで上昇＆前進。燃料消費1/0.8秒。後退(S)キーでふわっと着地。"),
        ROLLER_SKATES("skates", "§7§lスケートブーツ", Material.IRON_BOOTS, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7足元が青氷のように滑る。スニークでブレーキ。"),
        RESONANCE_CRYSTAL("resonance", "§3§l残響の結晶", Material.ECHO_SHARD, MaterialRarity.RARE, RelicPower.MEDIUM, "§7ウォーデンのソニックビームを放つ。(クールダウン8秒)"),
        FIREBALL("fireball", "§4§lファイアーボール", Material.FIRE_CHARGE, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7ガストの火の玉を撃ち出す。(クールダウン3秒)"),
        DECAYED_SWORD("decayed_sword", "§8§l朽ちた剣", Material.NETHERITE_SWORD, MaterialRarity.LEGENDARY, RelicPower.MAJOR, "§7いつかの勇者の剣。全エンチャントMAXで真価を発揮する。"),
        ENCHGRIND("enchgrind", "§d§l金剛の砥石", Material.GRINDSTONE, MaterialRarity.COMMON, RelicPower.MAJOR, "§7右クリックで手元のエンチャントを本にして返還する。"),
        CHAIN("chain", "§8§lサプライズ・チェーン", Material.IRON_CHAIN, MaterialRarity.UNCOMMON, RelicPower.MEDIUM, "§7左クリックで最大30ブロック先のMOBを拘束。再左クリックで解放。右クリックでは外れない。"),
        GIANTCORE("giantcore", "§2§l巨人の心臓", Material.HEAVY_CORE, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで25秒間、巨神サイズに巨大化。"),
        SEASWIM("seaswim", "§3§l空に海写す蒼球", Material.HEART_OF_THE_SEA, MaterialRarity.RARE, RelicPower.MEDIUM, "§7持っている間、空中を泳げる。移動速度は水中泳ぎ相当。スペースで上昇、離すとゆっくり降下。"),
        DASHFOOT("dashfoot", "§7§lバニーホップ", Material.RABBIT_FOOT, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7右クリックで前方へ強くダッシュする。着地するまで再使用不可。"),
        REFLECT("reflect", "§1§l反射の守護盾", Material.SHIELD, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7盾を構えている間、受けた攻撃のダメージを相手に跳ね返す。"),
        MAGNET("magnet", "§6§l磁力の石", Material.LODESTONE, MaterialRarity.UNCOMMON, RelicPower.MINOR, "§7持っている間、周囲のアイテムと経験値を強く引き寄せる。右クリックで一気に引き寄せる。"),
        SHRINKSTAR("shrinkstar", "§2§l剝蝕矮星", Material.NETHER_STAR, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで25秒間、小さくなる。狭い隙間も通り抜けられる。"),
        LIGHTNING("lightning", "§6§l呼雷の銅杖", Material.LIGHTNING_ROD, MaterialRarity.COMMON, RelicPower.MEDIUM, "§7右クリックで視線先に落雷を落とす。射程50ブロック。(クールダウン4秒)"),
        BLINK("blink", "§5§l次元の眼", Material.ENDER_EYE, MaterialRarity.UNCOMMON, RelicPower.MEDIUM, "§7右クリックで前方へ瞬間移動する。(クールダウン3秒)"),
        TOTEM("totem", "§6§l死不知のトーテム", Material.TOTEM_OF_UNDYING, MaterialRarity.RARE, RelicPower.MAJOR, "§7持っている間、耐性と再生を得る。"),
        WITHER("wither", "§8§l凋零の頭骨", Material.WITHER_SKELETON_SKULL, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックでウィザースクロウを撃ち出す。(クールダウン4秒)"),
        BELL("bell", "§2§lシングル・ベル", Material.BELL, MaterialRarity.COMMON, RelicPower.MINOR, "§7半径10ブロック以内のエンティティを10秒間停止させる。(クールダウン10秒)"),
        DRAGONBREATH("dragonbreath", "§5§l竜の吐息", Material.DRAGON_BREATH, MaterialRarity.RARE, RelicPower.MEDIUM, "§7右クリックで前方に竜のブレスを放つ。(クールダウン6秒)"),
        VISION("vision", "§8§l暗視鏡", Material.SPYGLASS, MaterialRarity.COMMON, RelicPower.MINOR, "§7覗いている間だけ暗視を得る。"),
        TIME_CLOCK("time_clock", "§b§lときを刻む時計", Material.CLOCK, MaterialRarity.COMMON, RelicPower.MINOR, "§7右クリックで現在の状態を保存し、もう一度でその状態へ戻る。"),
        ANTIGRAVITY_MACE("antigravity_mace", "§9§l反重力戦鎚", Material.MACE, MaterialRarity.RARE, RelicPower.MAJOR, "§7右クリックで上昇する。落下速度に応じて周囲を吹き飛ばす。"),
        ANCIENT_SLASH_ARMOR("ancient_slash_armor", "§4§l古代の斬鎧", Material.NETHERITE_CHESTPLATE, MaterialRarity.RARE, RelicPower.MAJOR, "§7攻撃を受けると周囲5ブロックへ5ダメージの斬撃を放つ。"),
        HELLFIRE_BOOK("hellfire_book", "§c§l獄炎の魔導書", Material.ENCHANTED_BOOK, MaterialRarity.UNCOMMON, RelicPower.MAJOR, "§7金床で武器に「獄炎」を付与する。"),
        ENDLESS_GRIMOIRE("endless_grimoire", "§d§l無尽の魔導書", Material.ENCHANTED_BOOK, MaterialRarity.EPIC, RelicPower.MAJOR, "§7金床でどんなアイテムにも「無尽」を付与する。"),
        ANCIENT_ANVIL("ancient_anvil", "§6§l古匠の金床", Material.ANVIL, MaterialRarity.EPIC, RelicPower.MEDIUM, "§7あらゆるアイテムに全エンチャントを付与できる。XP・MP不要、修繕ボーナス+50%。"),
        GAYBOLG("gaybolg", "§5§lゲイ・ボルグ", Material.DIAMOND_SPEAR, MaterialRarity.RARE, RelicPower.MAJOR, "§7右クリックで最も近い敵を追尾して突進。攻撃射程50ブロック、距離でダメージ増加。(クールダウン10秒)"),
        EXCALIBUR_REPLICA("excalibur_replica", "§e§lエクスカリバー・レプリカ", Material.GOLDEN_SWORD, MaterialRarity.RARE, RelicPower.MAJOR, "§7聖剣の力を1/10だけ、5回まで発揮する。"),
        SIXSHOT_CROSSBOW("sixshot_crossbow", "§8§l六連装弩弓", Material.CROSSBOW, MaterialRarity.UNCOMMON, RelicPower.MEDIUM, "§7最大6連射のリボルバー式クロスボウ。リロード速度80%、ダメージ120%。"),
        SNIPER_BOW("sniper_bow", "§2§l狙撃弓", Material.BOW, MaterialRarity.RARE, RelicPower.MEDIUM, "§7溜め時間はバニラ相当、動的FOVズーム、ダメージ5倍、矢は貫通して直進する。"),
        INFINITE_BUNDLE("infinite_bundle", "§6§l無限のバンドル", Material.BUNDLE, MaterialRarity.EPIC, RelicPower.MAJOR, "§7容量無制限。バンドルに重ねるかShiftクリックで収納し、好きな順番で取り出せる。"),
        NOAHS_ARK("noahs_ark", "§3§lノアの方舟", Material.PALE_OAK_BOAT, MaterialRarity.RARE, RelicPower.MEDIUM, "§7空を飛べる高速な船。");

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
            List<String> lore = new ArrayList<>();
            lore.add(type.description);
            lore.add("§8レリック");
            // Only materials with a real durability can safely carry a custom
            // max_damage component. Applying max_damage to non-damageable items
            // (boats, blaze powder, books, etc.) produces invalid stacks that
            // crash the client when they are placed inside a bundle.
            boolean damageableMaterial = item.getType().getMaxDurability() > 0;
            if (this.plugin.getConfig().getBoolean("relic-durability.enabled", true) && damageableMaterial
                && meta instanceof Damageable damageable) {
                damageable.setUnbreakable(false);
                damageable.setMaxDamage(this.relicMaxDurability(type));
                damageable.setDamage(0);
            } else if (meta instanceof Damageable damageable) {
                damageable.setUnbreakable(true);
            } else {
                meta.setUnbreakable(true);
            }
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyRelic, PersistentDataType.STRING, type.id);
            if (type == RelicType.INFINITE_BUNDLE) {
                meta.getPersistentDataContainer().set(this.bundleIdKey, PersistentDataType.STRING,
                    UUID.randomUUID().toString());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int relicMaxDurability(RelicType type) {
        int fallback = Math.max(1, this.plugin.getConfig().getInt("relic-durability.max-durability", 500));
        int configured = this.plugin.getConfig().getInt("relic-durability.per-relic." + type.id, fallback);
        return Math.max(1, Math.min(32767, configured));
    }

    private int relicDurabilityLossOnUse() {
        return Math.max(0, this.plugin.getConfig().getInt("relic-durability.durability-loss-on-use", 1));
    }

    /**
     * Older builds attached max_damage to every relic material. That is
     * invalid for non-damageable items and makes the client crash when the
     * stack is stored in a bundle. Strip that component in place.
     */
    private boolean normalizeRelicDurability(ItemStack item) {
        if (item == null || item.getType().getMaxDurability() > 0) return false;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return false;
        boolean changed = damageable.hasMaxDamage() || damageable.hasDamage();
        if (!changed) return false;
        damageable.setMaxDamage(null);
        damageable.resetDamage();
        damageable.setUnbreakable(true);
        item.setItemMeta(damageable);
        return true;
    }

    /**
     * Applies configured wear to a relic. Returns true when the item broke and
     * was removed, so an action can stop before using a destroyed relic.
     */
    boolean damageRelic(Player player, ItemStack item, int amount) {
        if (player == null || item == null || amount <= 0) return false;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return false;
        if (!this.plugin.getConfig().getBoolean("relic-durability.enabled", true)) return false;
        if (this.hasEndless(item)) return false;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return false;
        int maximum = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (maximum <= 0) return false;
        int next = Math.min(maximum, damageable.getDamage() + amount);
        damageable.setDamage(next);
        item.setItemMeta(damageable);
        // PlayerInteractEvent may hand out a copy; write the worn item back.
        // Avoid replacing an item that is already the live inventory object,
        // because that can interrupt a spear/bow use animation mid-stance.
        String materialName = item.getType().name();
        if (materialName.endsWith("_HELMET")) {
            if (player.getInventory().getHelmet() != item) player.getInventory().setHelmet(item);
        } else if (materialName.endsWith("_CHESTPLATE") || item.getType() == Material.ELYTRA) {
            if (player.getInventory().getChestplate() != item) player.getInventory().setChestplate(item);
        } else if (materialName.endsWith("_LEGGINGS")) {
            if (player.getInventory().getLeggings() != item) player.getInventory().setLeggings(item);
        } else if (materialName.endsWith("_BOOTS")) {
            if (player.getInventory().getBoots() != item) player.getInventory().setBoots(item);
        } else if (this.getRelicId(player.getInventory().getItemInMainHand()) != null) {
            if (player.getInventory().getItemInMainHand() != item) player.getInventory().setItemInMainHand(item);
        } else {
            if (player.getInventory().getItemInOffHand() != item) player.getInventory().setItemInOffHand(item);
        }
        if (next < maximum) return false;
        this.removeOneMatching(player, item);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.9f);
        player.sendActionBar(ChatColor.RED + "レリックの耐久値が尽きた。");
        return true;
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (sender == null) return;
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)
            && !sender.isOp() && !sender.hasPermission("mifron.admin")) {
            sender.sendMessage("§cOP権限が必要です。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/mf relic <id|all> [player]");
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
        String raw = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("all".equals(raw)) {
            int given = 0;
            for (RelicType candidate : RelicType.values()) {
                ItemStack item = createRelicItem(candidate);
                for (ItemStack leftover : target.getInventory().addItem(item).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                given++;
            }
            target.sendMessage("§a全レリックを入手しました: " + given + "種");
            if (sender != target) sender.sendMessage("§a" + target.getName() + " に全レリックを付与しました。");
            return;
        }
        RelicType type = null;
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
        ItemStack item = createRelicItem(type);
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        target.sendMessage("§aレリックを入手しました: " + type.displayName);
        if (sender != target) {
            sender.sendMessage("§a" + target.getName() + " に " + type.id + " を付与しました。");
        }
    }

    /** Opens the read-only relic compendium for every player. */
    public void openCompendium(Player player) {
        this.openCompendium(player, 0);
    }

    private static final String COMPENDIUM_TITLE = "\u00a76\u00a7lレリック図鑑";
    private static final int COMPENDIUM_PAGE_SIZE = 45;

    private void openCompendium(Player player, int requestedPage) {
        RelicType[] types = RelicType.values();
        int pages = Math.max(1, (types.length + COMPENDIUM_PAGE_SIZE - 1) / COMPENDIUM_PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inventory = Bukkit.createInventory(player, 54, COMPENDIUM_TITLE);
        int start = page * COMPENDIUM_PAGE_SIZE;
        int end = Math.min(types.length, start + COMPENDIUM_PAGE_SIZE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            RelicType type = types[i];
            ItemStack display = createRelicItem(type);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
                lore.add("§8ID: " + type.id);
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot++, display);
        }
        while (slot < COMPENDIUM_PAGE_SIZE) inventory.setItem(slot++, this.framePane());
        for (int i = 45; i < 54; i++) inventory.setItem(i, this.framePane());
        if (page > 0) {
            inventory.setItem(45, this.plugin.actionItem(Material.ARROW, "§7◀ 前のページ",
                List.of("§7ページ " + (page + 1) + " / " + pages), "relic_page", String.valueOf(page - 1)));
        }
        inventory.setItem(49, this.plugin.actionItem(Material.BOOK, "§6レリック図鑑",
            List.of("§7全 " + types.length + " 種", "§7ページ " + (page + 1) + " / " + pages), "relic_page_info", null));
        if (page + 1 < pages) {
            inventory.setItem(53, this.plugin.actionItem(Material.ARROW, "§7次のページ ▶",
                List.of("§7ページ " + (page + 2) + " / " + pages), "relic_page", String.valueOf(page + 1)));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onCompendiumClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!COMPENDIUM_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        if ("relic_page".equals(this.plugin.getUiAction(clicked))) {
            this.openCompendium(player,
                this.plugin.parsePositiveInt(this.plugin.getUiTargetString(clicked), 0));
        }
    }

    // ------------------------------------------------------------------
    // Infinite bundle relic: file-backed, unlimited storage with a paginated
    // withdrawal GUI. Shift-click inventory items to store them; click a
    // stored item to take it back in any order.
    // ------------------------------------------------------------------

    private static final String BUNDLE_TITLE = "\u00a76\u00a7l無限のバンドル";
    private static final int BUNDLE_PAGE_SIZE = 45;

    private void loadBundles() {
        this.bundleContents.clear();
        if (!this.bundleFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.bundleFile);
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("bundles");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                for (Object raw : config.getList("bundles." + key, List.of())) {
                    if (raw instanceof ItemStack stack && stack.getType() != Material.AIR) items.add(stack.clone());
                }
                this.bundleContents.put(id, items);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed bundle ids rather than disabling the file.
            }
        }
    }

    private void saveBundles() {
        try {
            java.io.File parent = this.bundleFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            org.bukkit.configuration.file.YamlConfiguration config =
                new org.bukkit.configuration.file.YamlConfiguration();
            for (Map.Entry<UUID, List<ItemStack>> entry : this.bundleContents.entrySet()) {
                config.set("bundles." + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            config.save(this.bundleFile);
        } catch (java.io.IOException error) {
            this.plugin.getLogger().warning("Failed to save infinite bundle data: " + error.getMessage());
        }
    }

    private List<ItemStack> bundleItems(UUID id) {
        return this.bundleContents.computeIfAbsent(id, ignored -> new ArrayList<>());
    }

    private UUID bundleIdForItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return UUID.randomUUID();
        String raw = meta.getPersistentDataContainer().get(this.bundleIdKey, PersistentDataType.STRING);
        UUID id = null;
        if (raw != null) {
            try { id = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { }
        }
        if (id == null) {
            id = UUID.randomUUID();
            meta.getPersistentDataContainer().set(this.bundleIdKey, PersistentDataType.STRING, id.toString());
            item.setItemMeta(meta);
        }
        return id;
    }

    private UUID bundleId(Player player, ItemStack item) {
        UUID id = this.bundleIdForItem(item);
        player.getInventory().setItemInMainHand(item);
        return id;
    }

    private boolean isInfiniteBundle(ItemStack item) {
        return item != null && "infinite_bundle".equals(this.getRelicId(item));
    }

    private void addToBundle(UUID id, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return;
        List<ItemStack> items = this.bundleItems(id);
        ItemStack remaining = stack.clone();
        for (ItemStack existing : items) {
            if (remaining.getAmount() <= 0) break;
            if (!existing.isSimilar(remaining)) continue;
            int max = Math.max(1, existing.getMaxStackSize());
            int space = max - existing.getAmount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining.getAmount());
            existing.setAmount(existing.getAmount() + move);
            remaining.setAmount(remaining.getAmount() - move);
        }
        if (remaining.getAmount() > 0) items.add(remaining);
    }

    private void openBundle(Player player, ItemStack item) {
        UUID id = this.bundleId(player, item);
        this.openBundleViewers.put(player.getUniqueId(), id);
        this.openBundlePage(player, id, 0);
    }

    private void openBundlePage(Player player, UUID id, int requestedPage) {
        List<ItemStack> items = this.bundleItems(id);
        int pages = Math.max(1, (items.size() + BUNDLE_PAGE_SIZE - 1) / BUNDLE_PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory inventory = Bukkit.createInventory(player, 54, BUNDLE_TITLE);
        int start = page * BUNDLE_PAGE_SIZE;
        for (int slot = 0; slot < BUNDLE_PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index < items.size()) {
                ItemStack display = items.get(index).clone();
                if (this.isRelicItem(display)) this.normalizeRelicDurability(display);
                inventory.setItem(slot, display);
            } else inventory.setItem(slot, this.framePane());
        }
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, this.framePane());
        if (page > 0) {
            inventory.setItem(45, this.plugin.actionItem(Material.ARROW, "§7◀ 前のページ",
                List.of("§7ページ " + (page + 1) + " / " + pages), "bundle_page", String.valueOf(page - 1)));
        }
        inventory.setItem(49, this.plugin.actionItem(Material.BUNDLE, "§6無限のバンドル",
            List.of("§7収納: " + items.size() + "個",
                "§7ページ " + (page + 1) + " / " + pages,
                "§eインベントリでShiftクリック → 収納",
                "§e収納アイテムをクリック → 取り出し",
                "§7右クリックで1個ずつ取り出し"), "bundle_info", null));
        if (page + 1 < pages) {
            inventory.setItem(53, this.plugin.actionItem(Material.ARROW, "§7次のページ ▶",
                List.of("§7ページ " + (page + 2) + " / " + pages), "bundle_page", String.valueOf(page + 1)));
        }
        // Re-open after the previous view's close event so the viewer mapping
        // survives page changes and item withdrawals.
        player.openInventory(inventory);
        this.openBundleViewers.put(player.getUniqueId(), id);
        this.bundlePages.put(player.getUniqueId(), page);
    }

    @EventHandler
    public void onBundleClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!BUNDLE_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID bundleId = this.openBundleViewers.get(player.getUniqueId());
        if (bundleId == null) return;
        int page = this.bundlePages.getOrDefault(player.getUniqueId(), 0);
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && "bundle_page".equals(this.plugin.getUiAction(clicked))) {
            this.openBundlePage(player, bundleId,
                this.plugin.parsePositiveInt(this.plugin.getUiTargetString(clicked), 0));
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == top) {
            if (event.getSlot() >= BUNDLE_PAGE_SIZE) return;
            List<ItemStack> items = this.bundleItems(bundleId);
            int index = page * BUNDLE_PAGE_SIZE + event.getSlot();
            if (index < 0 || index >= items.size()) return;
            ItemStack stored = items.get(index);
            ItemStack give = stored.clone();
            if (this.isRelicItem(give)) this.normalizeRelicDurability(give);
            if (event.getClick().isRightClick() && give.getAmount() > 1) {
                give.setAmount(1);
                stored.setAmount(stored.getAmount() - 1);
            } else {
                items.remove(index);
            }
            this.giveOrDrop(player, give);
            this.saveBundles();
            this.openBundlePage(player, bundleId, page);
            return;
        }
        ItemStack moving = event.getCurrentItem();
        if (!event.isShiftClick() || moving == null || moving.getType().isAir()) return;
        if (this.isRelicItem(moving) && "infinite_bundle".equals(this.getRelicId(moving))) return;
        ItemStack deposit = moving.clone();
        if (this.isRelicItem(deposit)) this.normalizeRelicDurability(deposit);
        this.addToBundle(bundleId, deposit);
        event.getClickedInventory().setItem(event.getSlot(), null);
        this.saveBundles();
        this.openBundlePage(player, bundleId, page);
    }

    @EventHandler
    public void onBundleClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!BUNDLE_TITLE.equals(event.getView().getTitle())) return;
        UUID id = this.openBundleViewers.remove(event.getPlayer().getUniqueId());
        this.bundlePages.remove(event.getPlayer().getUniqueId());
        if (id != null) this.saveBundles();
    }

    /**
     * Stacking a cursor item onto the bundle in an inventory stores it
     * immediately, without requiring the player to open the GUI first.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBundleStack(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (BUNDLE_TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        boolean currentBundle = this.isInfiniteBundle(current);
        boolean cursorBundle = this.isInfiniteBundle(cursor);
        if (currentBundle && !cursorBundle && cursor != null && !cursor.getType().isAir()) {
            UUID id = this.bundleIdForItem(current);
            ItemStack deposit = cursor.clone();
            if (this.isRelicItem(deposit)) this.normalizeRelicDurability(deposit);
            this.addToBundle(id, deposit);
            event.setCurrentItem(current);
            event.setCursor(null);
            event.setCancelled(true);
            this.saveBundles();
            player.updateInventory();
        } else if (cursorBundle && !currentBundle && current != null && !current.getType().isAir()) {
            UUID id = this.bundleIdForItem(cursor);
            ItemStack deposit = current.clone();
            if (this.isRelicItem(deposit)) this.normalizeRelicDurability(deposit);
            this.addToBundle(id, deposit);
            event.setCursor(cursor);
            event.setCurrentItem(null);
            event.setCancelled(true);
            this.saveBundles();
            player.updateInventory();
        }
    }

    /** True when the player carries the given relic anywhere in the inventory. */
    public boolean hasRelicInInventory(Player player, String id) {
        if (player == null || id == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (this.isRelicItem(item) && id.equals(this.getRelicId(item))) return true;
        }
        return false;
    }

    public boolean isRelicItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyRelic, PersistentDataType.STRING);
    }

    public String getRelicId(ItemStack item) {
        if (!isRelicItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyRelic, PersistentDataType.STRING);
    }

    /** True when an item carries the custom Endless (無尽) enchantment. */
    public boolean hasEndless(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer()
            .get(this.endlessKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    /** Applies Endless to any item and adds its visible lore marker. */
    public void applyEndless(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(this.endlessKey, PersistentDataType.BYTE, (byte) 1);
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> (ChatColor.LIGHT_PURPLE + "無尽").equals(line));
        lore.add(ChatColor.LIGHT_PURPLE + "無尽");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private boolean isEndlessGrimoire(ItemStack item) {
        return item != null && "endless_grimoire".equals(this.getRelicId(item));
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir() || item.getAmount() <= 0) return;
        for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void removeOneMatching(Player player, ItemStack target) {
        if (player == null || target == null) return;
        ItemStack wanted = target.clone();
        wanted.setAmount(1);
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current == null || !current.isSimilar(wanted)) continue;
            if (current.getAmount() <= 1) player.getInventory().setItem(i, null);
            else current.setAmount(current.getAmount() - 1);
            return;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.isSimilar(wanted)) continue;
            if (armor.getAmount() <= 1) {
                if (armor.getType().name().endsWith("_HELMET")) player.getInventory().setHelmet(null);
                else if (armor.getType().name().endsWith("_CHESTPLATE")) player.getInventory().setChestplate(null);
                else if (armor.getType().name().endsWith("_LEGGINGS")) player.getInventory().setLeggings(null);
                else if (armor.getType().name().endsWith("_BOOTS")) player.getInventory().setBoots(null);
            } else {
                armor.setAmount(armor.getAmount() - 1);
            }
            return;
        }
    }

    private void tryTransform(ItemStack item, Player player) {
        if (item == null || isRelicItem(item)) return;
        for (RelicType type : RelicType.values()) {
            if (item.getType() == type.baseMaterial) {
                if (type == RelicType.HELLFIRE_BOOK && !this.isFireEnchantedBook(item)) continue;
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

    /** The hellfire tome only awakens from a Fire Aspect or Flame book. */
    private boolean isFireEnchantedBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            return storage.hasStoredEnchant(Enchantment.FIRE_ASPECT)
                || storage.hasStoredEnchant(Enchantment.FLAME);
        }
        return meta != null && (meta.hasEnchant(Enchantment.FIRE_ASPECT) || meta.hasEnchant(Enchantment.FLAME));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack picked = event.getItem().getItemStack();
        boolean changed = this.isRelicItem(picked) && this.normalizeRelicDurability(picked);
        changed |= this.normalizeStoredRelics(picked);
        if (changed) event.getItem().setItemStack(picked);
        tryTransform(picked, player);
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
    private static final double HOOKSHOT_SPEED = 6.0;
    /** The bobber stops when any solid block is within this radius. */
    private static final double HOOKSHOT_BLOCK_RADIUS = 0.4;
    /** Reel-in speed toward the anchored bobber (blocks/tick). */
    private static final double HOOKSHOT_PULL_SPEED = 1.4;
    /** Maximum hookshot range in blocks. */
    private static final double HOOKSHOT_MAX_RANGE = 100.0;
    /** Particle spacing for the custom long-distance hookshot line. */
    private static final double HOOKSHOT_LINE_STEP = 0.45;
    /** Bunny hop stays unavailable until landing; this is only the visual timer. */
    static final int DASH_COOLDOWN_TICKS = 400;

    // Public balance constants for the newer special items. Keeping them here
    // makes the gameplay and the rule checks share one source of truth.
    static final int SIXSHOT_MAX_AMMO = 6;
    static final int VANILLA_CROSSBOW_RELOAD_TICKS = 25;
    static final double SIXSHOT_RELOAD_SPEED_MULTIPLIER = 0.8;
    /** 80% of vanilla crossbow reload speed means 25 / 0.8 = 31.25 ticks. */
    static final int SIXSHOT_RELOAD_TICKS = (int) Math.ceil(
        VANILLA_CROSSBOW_RELOAD_TICKS / SIXSHOT_RELOAD_SPEED_MULTIPLIER);
    static final double SIXSHOT_DAMAGE = 7.2;
    /**
     * Vanilla's client-side bow animation completes at 20 ticks and cannot be
     * slowed by a server plugin. Keep the release gate in lockstep with that
     * animation so the arrow never fires after the bow already looks fully drawn.
     */
    static final int SNIPER_DRAW_TICKS = 20;
    static final long SNIPER_DRAW_MILLIS = SNIPER_DRAW_TICKS * 50L;
    static final double SNIPER_DAMAGE = 45.0;
    static final int SNIPER_PIERCE_LEVEL = 10;
    /** FOV multiplier at full draw. Lower values zoom in more. */
    static final double SNIPER_FULL_DRAW_FOV = 0.55;
    static final double EXCALIBUR_ATTACK_DAMAGE = 50.0;
    static final double EXCALIBUR_SLASH_DAMAGE = 1000.0;
    static final double EXCALIBUR_REPLICA_SLASH_DAMAGE = 100.0;
    static final int EXCALIBUR_REPLICA_USES = 5;
    static final long EXCALIBUR_COOLDOWN_MILLIS = 30000L;
    static final long EXCALIBUR_REVIVE_COOLDOWN_MILLIS = 60000L;
    static final int EXCALIBUR_CHARGE_TICKS = 60;
    static final double SINGLE_BELL_RADIUS = 10.0;
    static final int SINGLE_BELL_FREEZE_TICKS = 200;
    static final long SINGLE_BELL_COOLDOWN_MILLIS = 10000L;
    static final long GAYBOLG_COOLDOWN_MILLIS = 10000L;
    static final double GAYBOLG_RANGE = 50.0;
    static final double GAYBOLG_DASH_SPEED = 3.1;
    static final int GAYBOLG_DASH_TICKS = 40;
    static final double GAYBOLG_DASH_ARRIVE_DISTANCE = 1.8;
    static final long ANCIENT_SLASH_COOLDOWN_MILLIS = 0L;
    static final double ANCIENT_SLASH_DAMAGE = 5.0;
    static final double ANCIENT_SLASH_RADIUS = 5.0;
    static final long MACE_LANDING_PROTECT_MILLIS = 2000L;
    static final long MACE_LAUNCH_MILLIS = 650L;
    static final double MACE_LAUNCH_VELOCITY = 0.9;
    static final double MACE_CLIMB_SPEED = 1.0;
    static final double MACE_CLIMB_ACCEL = 0.45;
    static final double ARK_SPEED = 0.9;
    static final double ARK_CLIMB_SPEED = 0.55;
    static final double ARK_CLIMB_ACCEL = 0.3;
    static final double ARK_DESCEND_SPEED = 0.55;
    static final double ARK_DESCEND_ACCEL = 0.3;
    static final double LIGHTNING_STRIKE_RANGE = 50.0;
    // Common aliases used by balance notes and tests.
    static final int SIXSHOT_CAPACITY = SIXSHOT_MAX_AMMO;
    static final int SIXSHOT_RELOAD_TIME_TICKS = SIXSHOT_RELOAD_TICKS;
    static final double SIXSHOT_DAMAGE_MULTIPLIER = 1.2;
    static final double SNIPER_DRAW_TIME_MULTIPLIER = 1.0;
    static final double SNIPER_DAMAGE_MULTIPLIER = 5.0;
    static final double EXCALIBUR_REPLICA_DAMAGE = EXCALIBUR_REPLICA_SLASH_DAMAGE;
    static final int EXCALIBUR_REPLICA_MAX_USES = EXCALIBUR_REPLICA_USES;
    static final double EXCALIBUR_REVIVE_DURABILITY_FRACTION = 0.2;
    static final double BELL_RADIUS = SINGLE_BELL_RADIUS;
    static final int BELL_FREEZE_TICKS = SINGLE_BELL_FREEZE_TICKS;
    static final long BELL_COOLDOWN_MILLIS = SINGLE_BELL_COOLDOWN_MILLIS;
    static final double MACE_LANDING_RADIUS = 5.0;
    static final double MACE_ATTACK_RADIUS = 6.0;

    /** Upward speed while the anti-gravity mace is held. */
    static double maceClimbY(double currentY) {
        return Math.min(currentY + MACE_CLIMB_ACCEL, MACE_CLIMB_SPEED);
    }

    /** Knockback strength for a landing, scaling with the fall speed. */
    static double maceLandingKnockback(double fallSpeed) {
        return Math.min(2.0, 0.4 + Math.max(0.0, fallSpeed) * 0.8);
    }

    /** Knockback strength for a successful mace smash, scaling with fall speed. */
    static double maceAttackKnockback(double fallSpeed) {
        return Math.min(3.5, 0.8 + Math.max(0.0, fallSpeed) * 1.2);
    }

    /** Vertical component of the mace knockback: intentionally very lift-heavy. */
    static double maceKnockbackY(double horizontalPower) {
        return Math.min(3.2, 0.9 + Math.max(0.0, horizontalPower) * 0.75);
    }

    /** One reload loads at most six arrows, capped by the arrows available. */
    static int sixshotReloadAmount(int availableArrows) {
        return Math.max(0, Math.min(SIXSHOT_MAX_AMMO, availableArrows));
    }

    /** The spear deals more damage the farther the target is. */
    static double gaybolgDamage(double distance) {
        return Math.min(50.0, 8.0 + Math.max(0.0, distance) * 0.8);
    }

    /** FOV multiplier for the sniper bow, from 1.0 at rest to a zoomed value. */
    static double sniperZoomFactor(long elapsedMillis) {
        double progress = Math.max(0.0, Math.min(1.0, elapsedMillis / (double) SNIPER_DRAW_MILLIS));
        // Ease-out keeps the first few ticks subtle and the full draw obvious.
        double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
        return 1.0 - (1.0 - SNIPER_FULL_DRAW_FOV) * eased;
    }

    /**
     * Vanilla derives FOV from movement speed. A negative speed modifier is
     * therefore the server-side way to produce the sniper zoom without a
     * client mod; it is removed as soon as the bow is released.
     */
    static double sniperMovementSpeedModifier(double zoomFactor) {
        return Math.max(-0.9, Math.min(0.0, 2.0 * zoomFactor - 2.0));
    }

    /** Each worn armor piece slows the player slightly, capped by config. */
    static double armorMovementPenalty(int pieces, double perPiece, double maxPenalty) {
        if (pieces <= 0) return 0.0;
        return -Math.min(Math.abs(maxPenalty), pieces * Math.abs(perPiece));
    }
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
    /** Active flying hookshot marker, keyed by owner. */
    private final java.util.Map<java.util.UUID, java.util.UUID> hookshotFlightOwners = new java.util.concurrent.ConcurrentHashMap<>();
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
    private final java.util.Map<java.util.UUID, java.util.List<ItemStack>> pendingGrindBooks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<org.bukkit.Location> relicGrindstoneLocations = new java.util.HashSet<>();
    private org.bukkit.scheduler.BukkitTask dragTask;
    private org.bukkit.scheduler.BukkitTask magnetTask;
    private org.bukkit.scheduler.BukkitTask passiveTask;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !isRelicItem(item)) return;

        // Boat placement must reach vanilla untouched. This handler used to
        // rewrite the in-hand stack while handling durability/migration, which
        // could swallow the boat placement interaction.
        if ("noahs_ark".equals(this.getRelicId(item)) && event.getAction().isRightClick()) {
            this.normalizeRelicDurability(item);
            return;
        }

        // Vision relic: scoping starts on right click, stop is handled by the
        // task itself (it ends when the player is no longer using the item).
        // Checked before the HAND-only guard so an off-hand spyglass works
        // too; starting a scope task twice is idempotent.
        if ("vision".equals(getRelicId(item)) && event.getAction().isRightClick()) {
            if (this.damageRelic(player, item, this.relicDurabilityLossOnUse())) return;
            this.startScopedVision(player);
            return;
        }

        // Hookshot is handled entirely here so the vanilla fishing rod (and
        // its 32-block bobber limit) is never used.
        if ("hookshot".equals(getRelicId(item)) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            if (this.damageRelic(player, item, this.relicDurabilityLossOnUse())) return;
            UUID uuid = player.getUniqueId();
            if (this.hookshotPulls.containsKey(uuid)) {
                // During a reel: stop where we are, keep inertia, retract.
                this.stopHookshotPull(uuid);
                this.clearHookshotAnchor(player);
                return;
            }
            if (this.hookshotFlightOwners.containsKey(uuid)) {
                // During flight: retract the current marker and stop.
                this.stopHookshotFlightFor(uuid);
                return;
            }
            HookAnchor anchor = this.hookshotAnchors.get(uuid);
            if (anchor != null) {
                this.startHookshotPull(player, anchor.location());
                return;
            }
            this.startHookshotFlight(player);
            return;
        }

        // NORMAL priority + no hand check used to double-fire on dual-hand
        // dispatch (item consumed twice, reward paid twice).
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        long now = System.currentTimeMillis();
        String id = getRelicId(item);
        Long last = this.lastUseMillis.get(player.getUniqueId());
        if (!"sixshot_crossbow".equals(id) && !"antigravity_mace".equals(id)
            && last != null && now - last < 500L) {
            event.setCancelled(true);
            return;
        }
        this.lastUseMillis.put(player.getUniqueId(), now);

        id = this.migrateRelicId(player, item, id);
        if (id == null) return;
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (rightClick) this.plugin.tutorialFeature.onSpecialUse(player);
        boolean opensAnvil = rightClick && event.getClickedBlock() != null
            && (event.getClickedBlock().getType() == Material.ANVIL
                || event.getClickedBlock().getType() == Material.CHIPPED_ANVIL
                || event.getClickedBlock().getType() == Material.DAMAGED_ANVIL)
            && ("hellfire_book".equals(id) || "ancient_anvil".equals(id));
        boolean usesRelic = rightClick || event.getAction() == Action.LEFT_CLICK_AIR
            || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (usesRelic && !opensAnvil && this.damageRelic(player, item, this.relicDurabilityLossOnUse())) return;

        if ("resonance".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryResonanceBeam(player, item);
        } else if ("fireball".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryFireball(player, item);
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
            if (!this.rollingCooldown(player, bellCooldown, SINGLE_BELL_COOLDOWN_MILLIS, item)) return;
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
        } else if ("time_clock".equals(id) && rightClick) {
            event.setCancelled(true);
            this.toggleClockSnapshot(player);
        } else if ("antigravity_mace".equals(id) && rightClick) {
            event.setCancelled(true);
            this.startMaceAscension(player);
        } else if ("gaybolg".equals(id)) {
            if (rightClick) {
                // Keep the vanilla spear ready/charging stance alive: only the
                // left-click ranged attack cancels the interaction.
                if (!this.rollingCooldown(player, gaybolgCooldown, GAYBOLG_COOLDOWN_MILLIS, item)) return;
                this.startGaybolgDash(player);
            } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                this.tryGaybolgRangedAttack(player);
            }
        } else if ("sixshot_crossbow".equals(id) && rightClick) {
            event.setCancelled(true);
            this.trySixshotCrossbow(player, item);
        } else if ("sniper_bow".equals(id) && rightClick) {
            // Do not cancel: vanilla keeps drawing the bow; the shoot event
            // enforces the draw time and rewrites the arrow.
            this.startSniperDraw(player);
        } else if ("infinite_bundle".equals(id) && rightClick) {
            event.setCancelled(true);
            this.openBundle(player, item);
        } else if ("excalibur_replica".equals(id) && rightClick) {
            event.setCancelled(true);
            this.tryExcaliburReplica(player, item);
        } else if ("decayed_sword".equals(id) && rightClick && this.isExcalibur(item)) {
            event.setCancelled(true);
            this.tryExcaliburSlash(player, item);
        } else if ("hellfire_book".equals(id) && rightClick) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && (event.getClickedBlock().getType() == Material.ANVIL
                    || event.getClickedBlock().getType() == Material.CHIPPED_ANVIL
                    || event.getClickedBlock().getType() == Material.DAMAGED_ANVIL)) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "金床で武器と一緒に合成すると「獄炎」を付与できる。");
        } else if ("ancient_anvil".equals(id) && rightClick) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && (event.getClickedBlock().getType() == Material.ANVIL
                    || event.getClickedBlock().getType() == Material.CHIPPED_ANVIL
                    || event.getClickedBlock().getType() == Material.DAMAGED_ANVIL)) {
                // Let the normal anvil GUI open; the artisan bonus is applied
                // by AdvancedAnvilFeature while this relic is in the inventory.
                return;
            }
            event.setCancelled(true);
            player.sendMessage(ChatColor.GOLD + "古匠の金床をインベントリに入れた状態で金床を使うと、XP・MPなしで全エンチャントと修繕ができる。");
        }
    }

    // ------------------------------------------------------------------
    // New special items
    // ------------------------------------------------------------------

    private void toggleClockSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        ClockSnapshot snapshot = this.clockSnapshots.get(uuid);
        if (snapshot == null) {
            this.clockSnapshots.put(uuid, this.captureClock(player));
            player.sendMessage(ChatColor.AQUA + "ときを刻んだ。もう一度右クリックするとこの状態へ戻る。");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
            return;
        }
        this.clockSnapshots.remove(uuid);
        this.restoreClock(player, snapshot);
        player.sendMessage(ChatColor.AQUA + "刻んだ時点の状態へ戻った。");
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.3f);
    }

    private ClockSnapshot captureClock(Player player) {
        java.util.List<org.bukkit.potion.PotionEffect> effects = new ArrayList<>();
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            effects.add(new org.bukkit.potion.PotionEffect(effect.getType(), effect.getDuration(),
                effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return new ClockSnapshot(player.getLocation().clone(), player.getHealth(), player.getFoodLevel(),
            player.getSaturation(), player.getLevel(), player.getExp(), copy,
            player.getInventory().getHeldItemSlot(), effects, player.getGameMode());
    }

    private void restoreClock(Player player, ClockSnapshot snapshot) {
        player.teleport(snapshot.location());
        player.getInventory().setContents(snapshot.inventory());
        player.getInventory().setHeldItemSlot(snapshot.heldSlot());
        player.setFoodLevel(snapshot.food());
        player.setSaturation(snapshot.saturation());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());
        player.setFireTicks(0);
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (org.bukkit.potion.PotionEffect effect : snapshot.effects()) {
            player.addPotionEffect(effect);
        }
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0, snapshot.health())));
        if (snapshot.gameMode() != null) player.setGameMode(snapshot.gameMode());
    }

    private LivingEntity findNearestEnemy(Player player, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) continue;
            if (!(living instanceof Enemy) && !(living instanceof Player)) continue;
            if (living instanceof Player other
                && (other.getGameMode() == org.bukkit.GameMode.SPECTATOR || other.getGameMode() == org.bukkit.GameMode.CREATIVE)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(living.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private void startGaybolgDash(Player player) {
        LivingEntity target = this.findNearestEnemy(player, GAYBOLG_RANGE);
        if (target == null) {
            player.sendMessage(ChatColor.GRAY + "近くに突進できる敵がいない。");
            return;
        }
        UUID uuid = player.getUniqueId();
        this.stopGaybolgDash(uuid);
        this.gaybolgDashTargets.put(uuid, target.getUniqueId());
        this.maceLaunchUntil.remove(uuid);
        player.setFallDistance(0.0f);
        this.aimGaybolgDash(player, target);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 1.1f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 30, 0.4, 0.3, 0.4, 0.08);
        int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline() || current.isDead()
                || ++ticks[0] > GAYBOLG_DASH_TICKS
                || !this.holdsRelic(current, "gaybolg")) {
                this.stopGaybolgDash(uuid);
                return;
            }
            LivingEntity tracked = null;
            UUID trackedId = this.gaybolgDashTargets.get(uuid);
            if (trackedId != null && Bukkit.getEntity(trackedId) instanceof LivingEntity living
                && !living.isDead() && living.getWorld().equals(current.getWorld())) {
                tracked = living;
            }
            if (tracked == null) {
                tracked = this.findNearestEnemy(current, GAYBOLG_RANGE);
                if (tracked == null) {
                    this.stopGaybolgDash(uuid);
                    return;
                }
                this.gaybolgDashTargets.put(uuid, tracked.getUniqueId());
            }
            double distance = current.getEyeLocation().distance(tracked.getLocation().add(0.0, tracked.getHeight() / 2.0, 0.0));
            if (distance <= GAYBOLG_DASH_ARRIVE_DISTANCE) {
                current.setVelocity(new Vector(0.0, 0.0, 0.0));
                current.setFallDistance(0.0f);
                current.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                    tracked.getLocation().add(0.0, tracked.getHeight() / 2.0, 0.0), 12, 0.4, 0.4, 0.4, 0.0);
                current.getWorld().playSound(current.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 1.0f);
                this.stopGaybolgDash(uuid);
                return;
            }
            this.aimGaybolgDash(current, tracked);
            current.setFallDistance(0.0f);
            current.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                current.getLocation().add(0.0, 1.0, 0.0), 6, 0.2, 0.2, 0.2, 0.02);
        }, 1L, 1L);
        this.gaybolgDashTasks.put(uuid, task);
    }

    private void aimGaybolgDash(Player player, LivingEntity target) {
        Vector direction = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0)
            .toVector().subtract(player.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.01) return;
        direction.normalize();
        // Turn the player toward the tracked mob every tick, not just once at
        // the start of the dash.
        org.bukkit.Location look = player.getLocation().clone();
        look.setDirection(direction);
        player.setRotation(look.getYaw(), look.getPitch());
        double speed = GAYBOLG_DASH_SPEED;
        double y = Math.max(-0.6, Math.min(0.8, direction.getY() * speed));
        player.setVelocity(direction.multiply(speed).setY(y));
    }

    private void stopGaybolgDash(UUID uuid) {
        this.gaybolgDashTargets.remove(uuid);
        BukkitTask task = this.gaybolgDashTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void tryGaybolgRangedAttack(Player player) {
        org.bukkit.Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, 50.0, 0.7,
            entity -> entity instanceof LivingEntity && entity != player && !entity.isDead());
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            player.sendMessage(ChatColor.GRAY + "射程内に敵がいない。");
            return;
        }
        double distance = player.getLocation().distance(target.getLocation());
        double damage = gaybolgDamage(distance);
        target.damage(damage, player);
        target.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
            target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0), 6, 0.4, 0.3, 0.4, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.2f);
        player.sendActionBar(ChatColor.LIGHT_PURPLE + "ゲイ・ボルグ: " + (int) Math.round(damage) + " ダメージ (" + (int) distance + "m)");
    }

    private int countArrows(Player player) {
        int count = 0;
        boolean endless = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!this.isSupportedArrow(item)) continue;
            if (this.hasEndless(item)) endless = true;
            else count += item.getAmount();
        }
        // One Endless arrow stack can supply a full six-shot load forever.
        return endless ? Math.max(count, SIXSHOT_MAX_AMMO) : count;
    }

    private void consumeArrows(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        // Consume ordinary arrows first. Endless arrows are skipped entirely;
        // they satisfy any remaining load without losing a single item.
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!this.isSupportedArrow(item) || this.hasEndless(item)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
            remaining -= take;
        }
    }

    private int sixshotAmmo(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer()
            .get(this.sixshotAmmoKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, Math.min(SIXSHOT_MAX_AMMO, value));
    }

    private void setSixshotAmmo(Player player, ItemStack item, int ammo) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.sixshotAmmoKey, PersistentDataType.INTEGER,
            Math.max(0, Math.min(SIXSHOT_MAX_AMMO, ammo)));
        java.util.List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> line != null && line.contains("装填"));
        lore.add(ChatColor.GRAY + "装填: " + ChatColor.YELLOW + Math.max(0, Math.min(SIXSHOT_MAX_AMMO, ammo)) + "/" + SIXSHOT_MAX_AMMO);
        meta.setLore(lore);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
    }

    private void trySixshotCrossbow(Player player, ItemStack item) {
        int ammo = this.sixshotAmmo(item);
        if (ammo > 0) {
            org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
            arrow.setVelocity(player.getEyeLocation().getDirection().multiply(2.8));
            arrow.setDamage(SIXSHOT_DAMAGE);
            arrow.setCritical(true);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            this.setSixshotAmmo(player, item, ammo - 1);
            player.setCooldown(Material.CROSSBOW, 2);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.1f);
            return;
        }
        int arrows = this.countArrows(player);
        if (arrows <= 0) {
            player.sendMessage(ChatColor.RED + "矢がない。");
            return;
        }
        int load = sixshotReloadAmount(arrows);
        this.consumeArrows(player, load);
        player.setCooldown(Material.CROSSBOW, SIXSHOT_RELOAD_TICKS);
        player.sendActionBar(ChatColor.YELLOW + "六連装弩弓をリロード中...");
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1.0f, 1.0f);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && this.isRelicItem(hand) && "sixshot_crossbow".equals(this.getRelicId(hand))) {
                this.setSixshotAmmo(player, hand, load);
                player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.0f);
                player.sendActionBar(ChatColor.YELLOW + "装填完了: " + load + "/" + SIXSHOT_MAX_AMMO);
            }
        }, (long) SIXSHOT_RELOAD_TICKS);
    }

    private boolean isExcalibur(ItemStack item) {
        return item != null && this.isRelicItem(item)
            && "decayed_sword".equals(this.getRelicId(item))
            && item.getEnchantmentLevel(Enchantment.SMITE) >= 20;
    }

    private ItemStack findExcalibur(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (this.isExcalibur(item)) return item;
        }
        return null;
    }

    private int replicaUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 5;
        Integer value = item.getItemMeta().getPersistentDataContainer()
            .get(this.excaliburReplicaUsesKey, PersistentDataType.INTEGER);
        return value == null ? 5 : Math.max(0, Math.min(5, value));
    }

    private void setReplicaUses(Player player, ItemStack item, int uses) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.excaliburReplicaUsesKey, PersistentDataType.INTEGER,
            Math.max(0, Math.min(5, uses)));
        java.util.List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> line != null && line.contains("残り使用回数"));
        lore.add(ChatColor.GRAY + "残り使用回数: " + ChatColor.YELLOW + Math.max(0, Math.min(5, uses)) + "/5");
        meta.setLore(lore);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
    }

    private void tryExcaliburSlash(Player player, ItemStack item) {
        if (!this.rollingCooldown(player, this.excaliburCooldown, EXCALIBUR_COOLDOWN_MILLIS, item)) return;
        player.sendActionBar(ChatColor.YELLOW + "聖剣の光を溜めている...");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!this.isExcalibur(hand)) return;
            this.fireLightSlash(player, EXCALIBUR_SLASH_DAMAGE, 18.0);
        }, EXCALIBUR_CHARGE_TICKS);
    }

    private void tryExcaliburReplica(Player player, ItemStack item) {
        int uses = this.replicaUses(item);
        if (uses <= 0) {
            player.sendMessage(ChatColor.GRAY + "このレプリカはもう力を使い切っている。");
            return;
        }
        if (!this.rollingCooldown(player, this.excaliburReplicaCooldown, EXCALIBUR_COOLDOWN_MILLIS, item)) return;
        player.sendActionBar(ChatColor.YELLOW + "レプリカの光を溜めている...");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || !this.isRelicItem(hand) || !"excalibur_replica".equals(this.getRelicId(hand))) return;
            int remaining = this.replicaUses(hand);
            if (remaining <= 0) return;
            this.setReplicaUses(player, hand, remaining - 1);
            this.fireLightSlash(player, EXCALIBUR_REPLICA_SLASH_DAMAGE, 18.0);
        }, EXCALIBUR_CHARGE_TICKS);
    }

    private void fireLightSlash(Player player, double damage, double range) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        org.bukkit.Location start = player.getEyeLocation();
        for (double step = 1.0; step <= range; step += 1.0) {
            org.bukkit.Location point = start.clone().add(direction.clone().multiply(step));
            point.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, point, 6, 0.2, 0.2, 0.2, 0.01);
            point.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, point, 3, 0.2, 0.2, 0.2, 0.01);
        }
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) continue;
            Vector to = living.getLocation().add(0.0, living.getHeight() / 2.0, 0.0)
                .toVector().subtract(player.getEyeLocation().toVector());
            if (to.length() > range) continue;
            if (to.lengthSquared() > 0.01 && to.normalize().dot(direction) < 0.35) continue;
            living.damage(damage, player);
            living.setFireTicks(Math.max(living.getFireTicks(), 60));
            living.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                living.getLocation().add(0.0, living.getHeight() / 2.0, 0.0), 8, 0.5, 0.4, 0.5, 0.0);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 1.4f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1.0f);
    }

    private boolean hasHellfire(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer()
            .get(this.hellfireKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private static boolean isWeapon(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_SPEAR")
            || name.endsWith("_MACE") || material == Material.TRIDENT || material == Material.BOW
            || material == Material.CROSSBOW;
    }

    private void applyExcaliburPassive(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SPEED, 60, 2, true, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.JUMP_BOOST, 60, 2, true, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 2, true, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.REGENERATION, 60, 2, true, false));
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) return;
        if (this.excaliburHealthActive.add(player.getUniqueId())) {
            for (AttributeModifier modifier : new ArrayList<>(health.getModifiers())) {
                if (this.excaliburHealthKey.equals(modifier.getKey())) {
                    this.excaliburHealthActive.remove(player.getUniqueId());
                    break;
                }
            }
        }
        boolean present = health.getModifiers().stream()
            .anyMatch(modifier -> this.excaliburHealthKey.equals(modifier.getKey()));
        if (!present) {
            health.addModifier(new AttributeModifier(this.excaliburHealthKey, 20.0,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        // Excalibur is a real, breakable blade once it awakens.
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!this.isExcalibur(item) || item.getItemMeta() == null) continue;
            ItemMeta meta = item.getItemMeta();
            boolean changed = false;
            if (meta.isUnbreakable()) {
                meta.setUnbreakable(false);
                changed = true;
            }
            String plain = meta.hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName())
                : "";
            if (!plain.contains("エクスカリバー")) {
                meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§6§l聖剣エクスカリバー"));
                changed = true;
            }
            if (changed) {
                item.setItemMeta(meta);
                player.getInventory().setItem(slot, item);
            }
        }
    }

    private void clearExcaliburHealth(Player player) {
        this.excaliburHealthActive.remove(player.getUniqueId());
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) return;
        boolean removed = false;
        for (AttributeModifier modifier : new ArrayList<>(health.getModifiers())) {
            if (this.excaliburHealthKey.equals(modifier.getKey())) {
                health.removeModifier(modifier);
                removed = true;
            }
        }
        if (removed && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private void tickNewSpecialItems(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (this.hasHellfire(main) || this.hasHellfire(off)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 40));
        }
        if (this.findExcalibur(player) != null) {
            this.applyExcaliburPassive(player);
        } else {
            this.clearExcaliburHealth(player);
        }
        this.tickMace(player);
        this.tickNoahsArk(player);
    }

    private void startMaceAscension(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        this.maceLaunchUntil.put(uuid, now + MACE_LAUNCH_MILLIS);
        this.maceAscended.add(uuid);
        this.maceLastFallSpeed.put(uuid, 0.0);
        Vector velocity = player.getVelocity();
        player.setVelocity(new Vector(velocity.getX(),
            Math.max(MACE_LAUNCH_VELOCITY, velocity.getY()), velocity.getZ()));
        player.setFallDistance(0.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1.0f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
            player.getLocation().add(0.0, 0.2, 0.0), 12, 0.3, 0.1, 0.3, 0.03);
    }

    private void tickMace(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean holdingMace = this.isRelicItem(hand) && "antigravity_mace".equals(this.getRelicId(hand));
        Long launchUntil = this.maceLaunchUntil.get(uuid);
        boolean launchActive = launchUntil != null && now < launchUntil && holdingMace;
        if (launchUntil != null && !launchActive) this.maceLaunchUntil.remove(uuid);
        if (launchActive) {
            Vector velocity = player.getVelocity();
            player.setVelocity(new Vector(velocity.getX(), maceClimbY(velocity.getY()), velocity.getZ()));
            player.setFallDistance(0.0f);
            this.maceAscended.add(uuid);
            this.maceLastFallSpeed.put(uuid, 0.0);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                player.getLocation().add(0.0, 0.2, 0.0), 3, 0.2, 0.05, 0.2, 0.01);
            return;
        }
        if (this.maceAscended.contains(uuid) && player.isOnGround()) {
            double fallSpeed = this.maceLastFallSpeed.getOrDefault(uuid, 0.0);
            this.maceAscended.remove(uuid);
            this.maceLandingProtectUntil.put(uuid, now + MACE_LANDING_PROTECT_MILLIS);
            player.setFallDistance(0.0f);
            this.knockbackAround(player, 5.0, maceLandingKnockback(fallSpeed));
            player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                player.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
            return;
        }
        if (!player.isOnGround() && player.getVelocity().getY() < 0.0) {
            this.maceLastFallSpeed.put(uuid, -player.getVelocity().getY());
        }
    }

    private void knockbackAround(Player player, double radius, double power) {
        org.bukkit.Location center = player.getLocation();
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity == player || !(entity instanceof LivingEntity)) continue;
            Vector away = entity.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0.0, 0.0, 1.0);
            away.normalize().multiply(power).setY(maceKnockbackY(power));
            entity.setVelocity(away);
            entity.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                entity.getLocation().add(0.0, 0.5, 0.0), 5, 0.2, 0.15, 0.2, 0.03);
        }
    }

    private void playMaceImpact(Player player, LivingEntity target) {
        org.bukkit.Location hit = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
        org.bukkit.World world = target.getWorld();
        world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, hit, 1);
        world.spawnParticle(org.bukkit.Particle.CRIT, hit, 48, 0.8, 0.7, 0.8, 0.28);
        world.spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, hit, 24, 1.0, 0.6, 1.0, 0.0);
        org.bukkit.Particle.DustOptions shockwave =
            new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(135, 205, 255), 1.25f);
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2.0 * i / 72.0;
            org.bukkit.Location point = hit.clone().add(
                Math.cos(angle) * 2.6, 0.15, Math.sin(angle) * 2.6);
            world.spawnParticle(org.bukkit.Particle.DUST, point, 2, 0.1, 0.25, 0.1, 0.02, shockwave);
            if (i % 3 == 0) {
                world.spawnParticle(org.bukkit.Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.01);
            }
        }
        world.playSound(hit, Sound.ITEM_MACE_SMASH_GROUND, 1.4f, 0.82f);
        world.playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.45f);
        world.playSound(hit, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.7f);
    }

    private boolean isArk(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof Boat)) return false;
        Byte value = entity.getPersistentDataContainer().get(this.noahsArkKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private void tickNoahsArk(Player player) {
        org.bukkit.entity.Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Boat boat)) return;
        if (!this.isArk(boat)) {
            // Older boats placed before the marker existed can be adopted once
            // while the owner still carries the relic.
            if (!this.hasRelicInInventory(player, "noahs_ark")) return;
            boat.getPersistentDataContainer().set(this.noahsArkKey, PersistentDataType.BYTE, (byte) 1);
        }
        boat.setGravity(false);
        boat.setWorkOnLand(true);
        boat.setMaxSpeed(2.0);
        var input = player.getCurrentInput();
        Vector forward = player.getLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() < 1.0E-9) forward = new Vector(0.0, 0.0, 1.0);
        else forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        double forwardInput = (input.isForward() ? 1.0 : 0.0) - (input.isBackward() ? 1.0 : 0.0);
        double strafeInput = (input.isRight() ? 1.0 : 0.0) - (input.isLeft() ? 1.0 : 0.0);
        Vector wish = forward.multiply(forwardInput).add(right.multiply(strafeInput));
        if (wish.lengthSquared() > 1.0E-9) wish.normalize().multiply(ARK_SPEED);
        Vector velocity = boat.getVelocity();
        double y;
        if (input.isJump()) y = Math.min(ARK_CLIMB_SPEED, velocity.getY() + ARK_CLIMB_ACCEL);
        else if (input.isSneak()) y = Math.max(-ARK_DESCEND_SPEED, velocity.getY() - ARK_DESCEND_ACCEL);
        else y = 0.0;
        boat.setVelocity(new Vector(wish.getX(), y, wish.getZ()));
        boat.setFallDistance(0.0f);
        if (input.isJump() || input.isSneak() || wish.lengthSquared() > 0.01) {
            boat.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                boat.getLocation().add(0.0, 0.25, 0.0), 4, 0.25, 0.08, 0.25, 0.01);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onHellfireAnvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        org.bukkit.inventory.AnvilInventory inventory = event.getInventory();
        ItemStack left = inventory.getItem(0);
        ItemStack right = inventory.getItem(1);
        if (left == null || right == null) return;
        if (!this.isRelicItem(right) || !"hellfire_book".equals(this.getRelicId(right))) return;
        if (!isWeapon(left.getType())) return;
        ItemStack result = left.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(this.hellfireKey, PersistentDataType.BYTE, (byte) 1);
        java.util.List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.removeIf(line -> line != null && line.contains("獄炎"));
        lore.add(ChatColor.RED + "獄炎");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        result.setItemMeta(meta);
        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpecialAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target == player) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (this.isExcalibur(hand)) {
            event.setDamage(Math.max(event.getDamage(), EXCALIBUR_ATTACK_DAMAGE));
        }
        if (this.hasHellfire(hand)) {
            event.setDamage(event.getDamage() + 10.0);
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
        }
        if (this.isRelicItem(hand) && "gaybolg".equals(this.getRelicId(hand))) {
            double distance = player.getLocation().distance(target.getLocation());
            event.setDamage(event.getDamage() + Math.min(40.0, distance * 0.8));
        }
        if (this.isRelicItem(hand) && "antigravity_mace".equals(this.getRelicId(hand))) {
            double fallSpeed = this.maceLastFallSpeed.getOrDefault(player.getUniqueId(), 0.0);
            this.knockbackAround(player, 6.0, maceAttackKnockback(fallSpeed));
            this.playMaceImpact(player, target);
            player.setFallDistance(0.0f);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onAncientSlashArmor(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
            && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
            && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE
            && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC) {
            return;
        }
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !this.isRelicItem(chest) || !"ancient_slash_armor".equals(this.getRelicId(chest))) return;
        this.damageRelic(player, chest, this.relicDurabilityLossOnUse());
        org.bukkit.Location center = player.getLocation().add(0.0, 1.0, 0.0);
        org.bukkit.Particle.DustOptions red =
            new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(190, 20, 20), 1.1f);
        // A full visible ring makes the 5-block reach obvious to nearby players.
        for (int i = 0; i < 96; i++) {
            double angle = Math.PI * 2.0 * i / 96.0;
            org.bukkit.Location point = center.clone().add(
                Math.cos(angle) * ANCIENT_SLASH_RADIUS, 0.0, Math.sin(angle) * ANCIENT_SLASH_RADIUS);
            player.getWorld().spawnParticle(org.bukkit.Particle.DUST, point, 2, 0.08, 0.15, 0.08, 0.0, red);
            if (i % 4 == 0) {
                player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, point, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(
            player.getLocation(), ANCIENT_SLASH_RADIUS, ANCIENT_SLASH_RADIUS, ANCIENT_SLASH_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || living == player || living.isDead()) continue;
            if (living.getLocation().distanceSquared(player.getLocation()) > ANCIENT_SLASH_RADIUS * ANCIENT_SLASH_RADIUS) continue;
            living.damage(ANCIENT_SLASH_DAMAGE, player);
            org.bukkit.Location hit = living.getLocation().add(0.0, living.getHeight() / 2.0, 0.0);
            living.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, hit, 12, 0.5, 0.4, 0.5, 0.0);
            living.getWorld().spawnParticle(org.bukkit.Particle.DUST, hit, 18, 0.35, 0.35, 0.35, 0.02, red);
            living.getWorld().spawnParticle(org.bukkit.Particle.CRIT, hit, 12, 0.25, 0.3, 0.25, 0.12);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
        player.sendActionBar(ChatColor.RED + "古代の斬鎧: 周囲へ斬撃を放った！");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        long now = System.currentTimeMillis();
        Long until = this.maceLandingProtectUntil.get(player.getUniqueId());
        Long launchUntil = this.maceLaunchUntil.get(player.getUniqueId());
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean holdingMace = (this.isRelicItem(main) && "antigravity_mace".equals(this.getRelicId(main)))
            || (this.isRelicItem(off) && "antigravity_mace".equals(this.getRelicId(off)));
        if (holdingMace || this.maceAscended.contains(player.getUniqueId())
            || (until != null && now < until) || (launchUntil != null && now < launchUntil)) {
            event.setCancelled(true);
            player.setFallDistance(0.0f);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExcaliburRevive(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getFinalDamage() < player.getHealth()) return;
        int excaliburSlot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (this.isExcalibur(candidate)) {
                excaliburSlot = i;
                break;
            }
        }
        if (excaliburSlot < 0) return;
        ItemStack excalibur = player.getInventory().getItem(excaliburSlot);
        long now = System.currentTimeMillis();
        Long last = this.excaliburReviveCooldown.get(player.getUniqueId());
        if (last != null && now - last < EXCALIBUR_REVIVE_COOLDOWN_MILLIS) return;
        if (!(excalibur.getItemMeta() instanceof Damageable damageable)) return;
        int maxDurability = excalibur.getType().getMaxDurability();
        if (maxDurability <= 0) return;
        int damage = damageable.getDamage() + Math.max(1, maxDurability / 5);
        this.excaliburReviveCooldown.put(player.getUniqueId(), now);
        event.setCancelled(true);
        player.setHealth(player.getMaxHealth());
        player.setFireTicks(0);
        if (damage >= maxDurability) {
            player.getInventory().setItem(excaliburSlot, null);
        } else {
            damageable.setDamage(damage);
            excalibur.setItemMeta(damageable);
            player.getInventory().setItem(excaliburSlot, excalibur);
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
            player.getLocation().add(0.0, 1.0, 0.0), 80, 0.6, 1.0, 0.6, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.2f, 1.0f);
        player.sendMessage(ChatColor.GOLD + "聖剣エクスカリバーがあなたを死から引き戻した。");
    }

    private void startSniperDraw(Player player) {
        this.sniperDrawStart.put(player.getUniqueId(), System.currentTimeMillis());
        this.applySniperZoom(player, 1.0);
    }

    private void tickSniperDraw(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = this.sniperDrawStart.get(uuid);
        if (start == null) {
            this.removeSniperZoom(player);
            return;
        }
        ItemStack active = player.getActiveItem();
        if (active == null || !this.isRelicItem(active) || !"sniper_bow".equals(this.getRelicId(active))) {
            this.sniperDrawStart.remove(uuid);
            this.removeSniperZoom(player);
            return;
        }
        this.applySniperZoom(player, sniperZoomFactor(System.currentTimeMillis() - start));
    }

    private void applySniperZoom(Player player, double zoomFactor) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double desired = sniperMovementSpeedModifier(zoomFactor);
        AttributeModifier existing = null;
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (this.sniperZoomKey.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }
        if (existing != null && Math.abs(existing.getAmount() - desired) < 0.005) return;
        speed.removeModifier(this.sniperZoomKey);
        speed.addModifier(new AttributeModifier(this.sniperZoomKey, desired,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
    }

    private void removeSniperZoom(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(this.sniperZoomKey);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onSniperShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack bow = event.getBow();
        if (bow == null || !this.isRelicItem(bow) || !"sniper_bow".equals(this.getRelicId(bow))) return;
        this.removeSniperZoom(player);
        Long start = this.sniperDrawStart.remove(player.getUniqueId());
        long elapsed = start == null ? Long.MAX_VALUE : System.currentTimeMillis() - start;
        if (elapsed < SNIPER_DRAW_MILLIS) {
            event.setCancelled(true);
            event.setConsumeArrow(false);
            player.sendActionBar(ChatColor.RED + "狙撃弓は2倍の時間をかけて引き絞る必要がある。");
            return;
        }
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setDamage(Math.max(SNIPER_DAMAGE, arrow.getDamage() * 5.0));
            arrow.setPierceLevel(SNIPER_PIERCE_LEVEL);
            arrow.setGravity(false);
            Vector velocity = arrow.getVelocity();
            if (velocity.lengthSquared() > 0.01) {
                arrow.setVelocity(velocity.normalize().multiply(4.0));
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
    }

    @EventHandler
    public void onNoahsArkPlace(EntityPlaceEvent event) {
        if (event.getPlayer() == null) return;
        ItemStack item = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
            ? event.getPlayer().getInventory().getItemInOffHand()
            : event.getPlayer().getInventory().getItemInMainHand();
        if (!this.isRelicItem(item) || !"noahs_ark".equals(this.getRelicId(item))) return;
        if (!(event.getEntity() instanceof Boat boat)) return;
        boat.getPersistentDataContainer().set(this.noahsArkKey, PersistentDataType.BYTE, (byte) 1);
        boat.setGravity(false);
        boat.setMaxSpeed(2.0);
        boat.setWorkOnLand(true);
        event.getPlayer().sendMessage(ChatColor.AQUA + "ノアの方舟を出航させた。WASDで移動、ジャンプで上昇、スニークで下降。");
    }

    // ------------------------------------------------------------------
    // Endless (無尽): a custom enchantment that prevents its item from ever
    // being consumed. The grimoire applies it through the anvil.
    // ------------------------------------------------------------------

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEndlessBrewingFuel(BrewingStandFuelEvent event) {
        if (!this.hasEndless(event.getFuel())) return;
        event.setConsuming(false);
        event.setFuelPower(Math.max(20, event.getFuelPower()));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEndlessFurnaceBurn(FurnaceBurnEvent event) {
        if (!this.hasEndless(event.getFuel())) return;
        event.setConsumeFuel(false);
        event.setBurning(true);
        event.setBurnTime(Math.max(event.getBurnTime(), 1200));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndlessCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof org.bukkit.inventory.CraftingInventory inventory)) return;
        java.util.Map<Integer, ItemStack> restore = new java.util.HashMap<>();
        for (int i = 1; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (this.hasEndless(slot)) restore.put(i, slot.clone());
        }
        if (restore.isEmpty()) return;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (java.util.Map.Entry<Integer, ItemStack> entry : restore.entrySet()) {
                ItemStack original = entry.getValue();
                if (inventory.getViewers().contains(player)) {
                    ItemStack current = inventory.getItem(entry.getKey());
                    if (current == null || current.getType().isAir() || current.getAmount() < original.getAmount()) {
                        inventory.setItem(entry.getKey(), original);
                    }
                } else {
                    this.giveOrDrop(player, original);
                }
            }
        });
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndlessSmelt(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        if (!this.hasEndless(source)) return;
        ItemStack keep = source.clone();
        org.bukkit.block.Block block = event.getBlock();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!(block.getState() instanceof org.bukkit.block.Furnace furnace)) return;
            org.bukkit.inventory.FurnaceInventory inventory = furnace.getInventory();
            ItemStack current = inventory.getSmelting();
            if (current == null || current.getType().isAir() || current.getAmount() < keep.getAmount()) {
                inventory.setSmelting(keep);
            }
        });
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndlessBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!this.hasEndless(item)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        ItemStack keep = item.clone();
        keep.setAmount(1);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveOrDrop(player, keep));
    }

    private static final java.util.Set<Material> ENDLESS_THROWABLES = java.util.Set.of(
        Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE,
        Material.WIND_CHARGE, Material.FIREWORK_ROCKET, Material.SPLASH_POTION,
        Material.LINGERING_POTION);

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndlessTrialKey(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = event.getItem();
        if (!this.hasEndless(item)) return;
        Material type = item.getType();
        boolean trialKey = type == Material.TRIAL_KEY || type == Material.OMINOUS_TRIAL_KEY;
        if (trialKey) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.VAULT) return;
        } else if (!ENDLESS_THROWABLES.contains(type)) {
            return;
        }
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        ItemStack keep = item.clone();
        keep.setAmount(1);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveOrDrop(player, keep));
    }

    /**
     * Captures the arrow stack that vanilla will select before the shot. This
     * survives server builds where EntityShootBowEvent loses the original
     * ammo metadata after the projectile has been split off.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBowDrawCapture(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        ItemStack bow = event.getItem();
        if (bow == null) return;
        if (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW) return;
        Player player = event.getPlayer();
        ItemStack selected = this.findSelectedArrow(player);
        if (bow.getType() == Material.BOW) {
            if (selected != null && this.hasEndless(selected)) {
                this.pendingShotArrows.put(player.getUniqueId(), selected.clone());
            } else {
                this.pendingShotArrows.remove(player.getUniqueId());
            }
            return;
        }
        // A charged crossbow can fire with no arrow stack left in the
        // inventory, so keep the previous pending Endless arrow in that case.
        if (selected != null) {
            if (this.hasEndless(selected)) this.pendingShotArrows.put(player.getUniqueId(), selected.clone());
            else this.pendingShotArrows.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndlessArrow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack pending = this.pendingShotArrows.remove(player.getUniqueId());
        ItemStack ammo = event.getArrowItem();
        if (!this.hasEndless(ammo)) ammo = event.getConsumable();
        if (!this.hasEndless(ammo)) ammo = pending;
        if (!this.hasEndless(ammo) && event.getBow() != null
            && event.getBow().getType() == Material.CROSSBOW) {
            // Crossbows keep the loaded projectile outside the inventory.
            ammo = this.findEndlessArrow(player);
        }
        if (!this.hasEndless(ammo)) return;
        event.setConsumeArrow(false);
        event.setConsumeItem(false);
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        // Vanilla removes the ammo before firing the event on this server
        // build, so setConsumeArrow alone is not enough. Reimburse exactly one
        // Endless arrow next tick.
        ItemStack keep = ammo.clone();
        keep.setAmount(1);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveOrDrop(player, keep));
    }

    private ItemStack findSelectedArrow(Player player) {
        ItemStack off = player.getInventory().getItemInOffHand();
        if (this.isSupportedArrow(off)) return off;
        for (ItemStack item : player.getInventory().getContents()) {
            if (this.isSupportedArrow(item)) return item;
        }
        return null;
    }

    private boolean isSupportedArrow(ItemStack item) {
        return item != null && (item.getType() == Material.ARROW
            || item.getType() == Material.TIPPED_ARROW || item.getType() == Material.SPECTRAL_ARROW);
    }

    private ItemStack findEndlessArrow(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!this.isSupportedArrow(item)) continue;
            if (this.hasEndless(item)) return item;
        }
        return null;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndlessDurability(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (this.hasEndless(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onEndlessBreak(org.bukkit.event.player.PlayerItemBreakEvent event) {
        ItemStack broken = event.getBrokenItem();
        if (!this.hasEndless(broken)) return;
        ItemStack keep = broken.clone();
        if (keep.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            keep.setItemMeta(damageable);
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveOrDrop(event.getPlayer(), keep));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEndlessConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!this.hasEndless(item)) return;
        ItemStack keep = item.clone();
        keep.setAmount(1);
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveOrDrop(event.getPlayer(), keep));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onEndlessBrewStart(org.bukkit.event.block.BrewingStartEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.BrewingStand stand)) return;
        ItemStack ingredient = stand.getInventory().getIngredient();
        if (this.hasEndless(ingredient)) {
            this.endlessBrewIngredients.put(event.getBlock().getLocation(), ingredient.clone());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onEndlessBrew(org.bukkit.event.inventory.BrewEvent event) {
        ItemStack keep = this.endlessBrewIngredients.remove(event.getBlock().getLocation());
        if (keep == null) return;
        org.bukkit.block.Block block = event.getBlock();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (block.getState() instanceof org.bukkit.block.BrewingStand stand) {
                stand.getInventory().setIngredient(keep);
            }
        });
    }

    /** Calling-thunder copper staff: a long-range strike where the player looks. */
    private void tryLightning(Player player) {
        int range = (int) LIGHTNING_STRIKE_RANGE;
        org.bukkit.block.Block target = player.getTargetBlockExact(range);
        org.bukkit.Location loc = target != null
            ? target.getLocation().add(0.5, 1.0, 0.5)
            : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
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

    /** Single bell: freezes entities within 10 blocks for 10 seconds. */
    private void tryBell(Player player) {
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(SINGLE_BELL_RADIUS, SINGLE_BELL_RADIUS, SINGLE_BELL_RADIUS)) {
            if (e instanceof org.bukkit.entity.LivingEntity living && living != player) {
                living.setAI(false);
                living.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (living.isValid()) living.setAI(true);
                }, SINGLE_BELL_FREEZE_TICKS);
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f, 1.4f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, player.getLocation().add(0.0, 1.0, 0.0), 30, 5, 1, 5, 0.0);
        player.sendMessage(ChatColor.AQUA + "周囲のエンティティを停止させた。");
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
        if (this.normalizeRelicDurability(item)) {
            player.getInventory().setItemInMainHand(item);
        }
        // Awakened Excalibur intentionally has a different display name and
        // must never be reset to the plain decayed sword.
        if ("decayed_sword".equals(id) && item.getEnchantmentLevel(Enchantment.SMITE) >= 20) return id;
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
    public void onPlaceNonPlaceableRelic(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isRelicItem(item)) return;
        String id = getRelicId(item);
        if ("chain".equals(id) || "magnet".equals(id) || "ancient_anvil".equals(id)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "このレリックは設置できません。");
        }
    }

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
        java.util.List<ItemStack> books = new java.util.ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : input.getEnchantments().entrySet()) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta bookMeta = book.getItemMeta();
            if (bookMeta instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                book.setItemMeta(storage);
            } else {
                book.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
            books.add(book);
        }
        pendingGrindBooks.put(viewerId, books);
        lastGrindInput.put(viewerId, input.clone());
        if (viewer instanceof Player player) {
            player.sendMessage(ChatColor.GREEN + "錬金の砥石がエンチャントを本に戻した！");
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onGrindstoneResultTake(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory() instanceof GrindstoneInventory inv)) return;
        org.bukkit.Location loc = inv.getLocation();
        if (loc == null) return;
        if (!relicGrindstoneLocations.contains(loc)) return;
        if (event.getSlot() != 2) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        java.util.List<ItemStack> books = pendingGrindBooks.remove(player.getUniqueId());
        if (books == null || books.isEmpty()) {
            ItemStack input = inv.getItem(0);
            if (input != null && !input.getEnchantments().isEmpty()) {
                books = new java.util.ArrayList<>();
                for (Map.Entry<Enchantment, Integer> entry : input.getEnchantments().entrySet()) {
                    ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                    ItemMeta bookMeta = book.getItemMeta();
                    if (bookMeta instanceof EnchantmentStorageMeta storage) {
                        storage.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                        book.setItemMeta(storage);
                    } else {
                        book.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                    }
                    books.add(book);
                }
            }
        }
        if (books != null) {
            for (ItemStack book : books) {
                for (ItemStack leftover : player.getInventory().addItem(book).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            player.sendMessage(ChatColor.GREEN + "金剛の砥石がエンチャントを本に戻した！");
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        }
    }

    /**
     * Keeps the hookshot bobber on a straight line and stops it as soon as any
     * solid block is within a small radius. A stopped bobber stays in place
     * until the player right-clicks to reel themselves toward it.
     */
    private void startHookshotFlight(Player player) {
        this.clearHookshotAnchor(player);
        this.stopHookshotFlightFor(player.getUniqueId());
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(HOOKSHOT_SPEED);
        Location start = player.getEyeLocation().clone().add(direction.clone().normalize().multiply(0.5));
        // A real fishing bobber is discarded by vanilla once it is 32 blocks
        // from the owner. Use a display marker instead, with a forced particle
        // line, so the full 100-block range is usable.
        ItemDisplay marker = player.getWorld().spawn(start, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.SLIME_BALL));
            display.setBillboard(Display.Billboard.CENTER);
            display.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setGlowing(true);
            display.setViewRange(8.0f);
            display.setTeleportDuration(1);
        });
        int[] ticks = {0};
        Location[] lastSafe = {start.clone()};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!marker.isValid() || ++ticks[0] > 200) {
                this.stopHookshotFlight(marker.getUniqueId());
                if (marker.isValid()) marker.remove();
                return;
            }
            if (marker.getLocation().distanceSquared(player.getLocation()) > HOOKSHOT_MAX_RANGE * HOOKSHOT_MAX_RANGE) {
                this.stopHookshotFlight(marker.getUniqueId());
                if (marker.isValid()) marker.remove();
                return;
            }
            Location current = marker.getLocation().clone();
            // Give the marker a few ticks to leave the player's body, then
            // stop it on floor, wall, ceiling or any other solid block.
            if (ticks[0] > 3) {
                if (this.hookshotBlocked(current)) {
                    this.anchorHookshot(player, marker, lastSafe[0]);
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
                        this.anchorHookshot(player, marker, safe);
                        return;
                    }
                    safe = probe;
                }
            }
            lastSafe[0] = current;
            marker.teleport(current.clone().add(direction));
            this.drawHookshotLine(player, marker.getLocation());
        }, 1L, 1L);
        this.hookshotFlights.put(marker.getUniqueId(), task);
        this.hookshotFlightOwners.put(player.getUniqueId(), marker.getUniqueId());
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
        org.bukkit.Particle.DustOptions thread =
            new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(230, 240, 255), 0.55f);
        for (int i = 0; i <= points; i++) {
            Location point = from.clone().add(step.clone().multiply(i));
            player.spawnParticle(org.bukkit.Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, thread, true);
        }
        // A brighter tip so the end of the line stays readable at range.
        player.spawnParticle(org.bukkit.Particle.END_ROD, target, 2, 0.06, 0.06, 0.06, 0.0, true);
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

    private void anchorHookshot(Player player, ItemDisplay marker, Location location) {
        this.stopHookshotFlight(marker.getUniqueId());
        this.stopHookshotHold(marker.getUniqueId());
        marker.teleport(location);
        marker.setGravity(false);
        HookAnchor anchor = new HookAnchor(location.clone(), marker.getUniqueId());
        this.hookshotAnchors.put(player.getUniqueId(), anchor);
        // Keep the marker exactly on the contact point until it is reeled in.
        BukkitTask hold = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            HookAnchor current = this.hookshotAnchors.get(player.getUniqueId());
            if (current == null || !current.hookId().equals(marker.getUniqueId())
                    || !marker.isValid()) {
                this.stopHookshotHold(marker.getUniqueId());
                return;
            }
            if (!marker.getWorld().equals(current.location().getWorld())
                    || marker.getLocation().distanceSquared(current.location()) > 0.0025) {
                marker.teleport(current.location());
            }
            marker.setGravity(false);
            this.drawHookshotLine(player, current.location());
        }, 1L, 1L);
        this.hookshotHolds.put(marker.getUniqueId(), hold);
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
        this.stopHookshotFlightFor(player.getUniqueId());
        HookAnchor anchor = this.hookshotAnchors.remove(player.getUniqueId());
        if (anchor == null) return;
        this.stopHookshotHold(anchor.hookId());
        org.bukkit.entity.Entity hook = Bukkit.getEntity(anchor.hookId());
        if (hook != null) hook.remove();
    }

    private void stopHookshotFlight(UUID hookId) {
        this.hookshotFlightOwners.values().remove(hookId);
        BukkitTask task = this.hookshotFlights.remove(hookId);
        if (task != null) task.cancel();
    }

    private void stopHookshotFlightFor(UUID playerId) {
        UUID markerId = this.hookshotFlightOwners.remove(playerId);
        if (markerId == null) return;
        BukkitTask task = this.hookshotFlights.remove(markerId);
        if (task != null) task.cancel();
        org.bukkit.entity.Entity marker = Bukkit.getEntity(markerId);
        if (marker != null) marker.remove();
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
            this.tickNewSpecialItems(p);
            this.tickSniperDraw(p);
            this.tickArmorMovementPenalty(p);
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

    private void tickArmorMovementPenalty(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean enabled = this.plugin.getConfig().getBoolean("armor-movement-penalty.enabled", true);
        int pieces = 0;
        if (enabled) {
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor != null && !armor.getType().isAir() && armor.getAmount() > 0) pieces++;
            }
        }
        double perPiece = this.plugin.getConfig().getDouble("armor-movement-penalty.per-piece", 0.02);
        double maxPenalty = this.plugin.getConfig().getDouble("armor-movement-penalty.max-penalty", 0.08);
        double desired = armorMovementPenalty(pieces, perPiece, maxPenalty);
        AttributeModifier existing = null;
        for (AttributeModifier modifier : speed.getModifiers()) {
            if (this.armorPenaltyKey.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }
        if (existing != null && Math.abs(existing.getAmount() - desired) < 0.0001) return;
        speed.removeModifier(this.armorPenaltyKey);
        if (desired < 0.0) {
            speed.addModifier(new AttributeModifier(this.armorPenaltyKey, desired,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    private void removeArmorMovementPenalty(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(this.armorPenaltyKey);
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
        this.stopGaybolgDash(uuid);
        this.sniperDrawStart.remove(uuid);
        this.pendingShotArrows.remove(uuid);
        this.removeSniperZoom(event.getPlayer());
        this.removeArmorMovementPenalty(event.getPlayer());
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
        this.normalizeInventoryRelics(event.getPlayer());
    }

    private void normalizeInventoryRelics(Player player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null) continue;
            boolean itemChanged = this.isRelicItem(item) && this.normalizeRelicDurability(item);
            itemChanged |= this.normalizeStoredRelics(item);
            if (itemChanged) {
                player.getInventory().setItem(slot, item);
                changed = true;
            }
        }
        org.bukkit.inventory.Inventory enderChest = player.getEnderChest();
        for (int slot = 0; slot < enderChest.getSize(); slot++) {
            ItemStack item = enderChest.getItem(slot);
            if (item == null) continue;
            boolean itemChanged = this.isRelicItem(item) && this.normalizeRelicDurability(item);
            itemChanged |= this.normalizeStoredRelics(item);
            if (itemChanged) {
                enderChest.setItem(slot, item);
                changed = true;
            }
        }
        if (changed) player.updateInventory();
    }

    /** Cleans old invalid relic stacks stored inside vanilla bundles. */
    private boolean normalizeStoredRelics(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.BundleMeta bundle)) return false;
        List<ItemStack> contents = new ArrayList<>(bundle.getItems());
        boolean changed = false;
        for (int i = 0; i < contents.size(); i++) {
            ItemStack child = contents.get(i);
            if (child == null) continue;
            boolean childChanged = this.isRelicItem(child) && this.normalizeRelicDurability(child);
            childChanged |= this.normalizeStoredRelics(child);
            if (childChanged) {
                contents.set(i, child);
                changed = true;
            }
        }
        if (changed) {
            bundle.setItems(contents);
            item.setItemMeta(bundle);
        }
        return changed;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.resetSeaswimState(event.getPlayer());
    }

    /** Clears any stale swim speed/pose, including after a crash or relog. */
    private void resetSeaswimState(Player player) {
        UUID uuid = player.getUniqueId();
        this.clearHookshotAnchor(player);
        this.stopGaybolgDash(uuid);
        this.sniperDrawStart.remove(uuid);
        this.removeSniperZoom(player);
        this.removeArmorMovementPenalty(player);
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
        // Holding the sea heart takes priority over a worn jetpack; otherwise
        // the jump input that drives air-swimming would trigger the jetpack
        // and immediately suppress the swim state.
        boolean seaswim = this.hasSeaswim(player);
        // The hold flags only mark intent: the climb/float itself is applied
        // on every move event so it follows the player smoothly.
        if (jetpack && !seaswim && !pulling && event.getInput().isJump()) {
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
        // Anti-gravity mace and Noah's Ark now read the actual current input
        // in their own tick handlers. PlayerInputEvent#isRight() is the D
        // strafe key, not the right mouse button, so it must not drive them.
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
