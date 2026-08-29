package org.server.mifron;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Shelf;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class Mifron extends JavaPlugin implements Listener, TabExecutor {
   private static final String FRIEND_UI_TITLE = "§3Mifron Friends";
   private static final String FRIEND_STATUS_UI_TITLE = "§2Mifron Status";
   private static final String QUEST_UI_TITLE = "§bMifron Quests";
   private static final String TELEPORT_UI_TITLE = "§5Mifron Teleporter";
   private static final String MERCHANT_UI_TITLE = "§6Mifron Merchant";
   private static final long MERCHANT_REROLL_MILLIS = 3600000L;
   private static final long MERCHANT_TRANSACTION_COOLDOWN_MILLIS = 150L;
   private static final long SHOP_WAND_ACTION_COOLDOWN_MILLIS = 400L;
   private static final long JUMP_PAD_COOLDOWN_MILLIS = 650L;
   private static final long JUMP_PAD_FALL_PROTECTION_MILLIS = 60000L;
   private static final int MAX_JUMP_PAD_POWER = 100;
   private static final int MAX_EMERALDS = 2000000000;
   private static final int MAX_FRIEND_REQUESTS = 100;
   private static final int MAX_OFFLINE_MESSAGES = 50;
   private static final int MAX_FRIEND_MESSAGE_LENGTH = 256;
   private static final int MAX_FRIEND_FILTER_LENGTH = 32;
   private static final int MAX_SHOP_STACKS_PER_CLICK = 64;
   private static final int BARREL_SHOP_OFFER_SLOTS = 27;
   private static final int SHELF_SHOP_OFFER_SLOTS = 3;
   private static final int MOB_REWARD_FARM_THRESHOLD_PER_HOUR = 100;
   private static final Pattern SAFE_CONFIG_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
   private static final Map<String, Mifron.TitleDefinition> TITLE_DEFINITIONS = Map.ofEntries(
      Map.entry("狙撃手", new Mifron.TitleDefinition(Material.BOW, List.of("adventure/sniper_duel", "adventure/bullseye"))),
      Map.entry("狩人", new Mifron.TitleDefinition(Material.CROSSBOW, List.of("adventure/two_birds_one_arrow", "adventure/arbalistic"))),
      Map.entry(
         "重戦士",
         new Mifron.TitleDefinition(Material.MACE, List.of("adventure/overoverkill|adventure/over_overkill", "adventure/blowback|adventure/reverse_wind"))
      ),
      Map.entry("槍使い", new Mifron.TitleDefinition(Material.TRIDENT, List.of("adventure/mob_kebab|adventure/throw_trident"))),
      Map.entry("海の戦士", new Mifron.TitleDefinition(Material.PRISMARINE_SHARD, List.of("adventure/very_very_frightening"))),
      Map.entry("猫好き", new Mifron.TitleDefinition(Material.COD, List.of("husbandry/complete_catalogue"))),
      Map.entry("犬好き", new Mifron.TitleDefinition(Material.BONE, List.of("husbandry/whole_pack|husbandry/tame_an_animal"))),
      Map.entry("生物観察の鬼", new Mifron.TitleDefinition(Material.FROGSPAWN, List.of("husbandry/froglights"))),
      Map.entry("友好的", new Mifron.TitleDefinition(Material.CAKE, List.of("husbandry/allay_deliver_cake_to_note_block"))),
      Map.entry("猪突猛進", new Mifron.TitleDefinition(Material.GOAT_HORN, List.of("husbandry/ride_a_boat_with_a_goat"))),
      Map.entry("癒し系", new Mifron.TitleDefinition(Material.AXOLOTL_BUCKET, List.of("husbandry/kill_axolotl_target"))),
      Map.entry("歴史マニア", new Mifron.TitleDefinition(Material.PITCHER_POD, List.of("husbandry/plant_any_sniffer_seed"))),
      Map.entry("闇の商人", new Mifron.TitleDefinition(Material.LEAD, List.of("nether/uneasy_alliance"))),
      Map.entry("全能", new Mifron.TitleDefinition(Material.NETHER_STAR, List.of("nether/all_effects"))),
      Map.entry("魔導師", new Mifron.TitleDefinition(Material.BREWING_STAND, List.of("adventure/totem_of_undying", "nether/all_potions"))),
      Map.entry(
         "冒険家",
         new Mifron.TitleDefinition(
            Material.MAP, List.of("adventure/adventuring_time", "adventure/this_way_goes_on_forever|nether/ride_strider_in_overworld_lava|nether/fast_travel")
         )
      ),
      Map.entry("農家", new Mifron.TitleDefinition(Material.DIAMOND_HOE, List.of("husbandry/obtain_netherite_hoe", "husbandry/bred_all_animals"))),
      Map.entry("料理人", new Mifron.TitleDefinition(Material.COOKED_BEEF, List.of("husbandry/balanced_diet", "nether/all_potions"))),
      Map.entry("英雄", new Mifron.TitleDefinition(Material.DIAMOND_SWORD, List.of("adventure/kill_all_mobs"))),
      Map.entry(
         "鍛冶師",
         new Mifron.TitleDefinition(
            Material.SMITHING_TABLE, List.of("adventure/trim_with_all_exclusive_armor_patterns|adventure/trim_with_all_armor_patterns")
         )
      ),
      Map.entry("黒き鎧", new Mifron.TitleDefinition(Material.NETHERITE_HELMET, List.of("nether/netherite_armor"))),
      Map.entry("鉄は熱いうちに掘れ", new Mifron.TitleDefinition(Material.IRON_PICKAXE, List.of("story/iron_tools"))),
      Map.entry("ダイヤの原石", new Mifron.TitleDefinition(Material.DIAMOND, List.of("story/mine_diamond"))),
      Map.entry("まだ舞える", new Mifron.TitleDefinition(Material.TOTEM_OF_UNDYING, List.of("adventure/totem_of_undying"))),
      Map.entry("村公認", new Mifron.TitleDefinition(Material.EMERALD, List.of("adventure/hero_of_the_village"))),
      Map.entry("ハチ合わせ職人", new Mifron.TitleDefinition(Material.HONEYCOMB, List.of("husbandry/safely_harvest_honey", "husbandry/silk_touch_nest"))),
      Map.entry("粉雪ソムリエ", new Mifron.TitleDefinition(Material.LEATHER_BOOTS, List.of("adventure/walk_on_powder_snow_with_leather_boots"))),
      Map.entry("しーっ、セーフ", new Mifron.TitleDefinition(Material.ECHO_SHARD, List.of("adventure/avoid_vibration"))),
      Map.entry("雷様のコンセント", new Mifron.TitleDefinition(Material.LIGHTNING_ROD, List.of("adventure/lightning_rod_with_villager_no_fire"))),
      Map.entry("古代の落とし物", new Mifron.TitleDefinition(Material.ANCIENT_DEBRIS, List.of("nether/obtain_ancient_debris"))),
      Map.entry("エンドロール係", new Mifron.TitleDefinition(Material.DRAGON_HEAD, List.of("end/kill_dragon"))),
      Map.entry("花火で通勤", new Mifron.TitleDefinition(Material.FIREWORK_ROCKET, List.of("end/elytra"))),
      Map.entry("照明係長", new Mifron.TitleDefinition(Material.BEACON, List.of("nether/create_full_beacon"))),
      Map.entry("全知", new Mifron.TitleDefinition(Material.KNOWLEDGE_BOOK, List.of()))
   );
   private static final Set<Material> MERCHANT_EXCLUDED_ITEMS = Set.of(
      Material.AIR,
      Material.BARRIER,
      Material.BEDROCK,
      Material.COMMAND_BLOCK,
      Material.CHAIN_COMMAND_BLOCK,
      Material.REPEATING_COMMAND_BLOCK,
      Material.COMMAND_BLOCK_MINECART,
      Material.STRUCTURE_BLOCK,
      Material.STRUCTURE_VOID,
      Material.JIGSAW,
      Material.LIGHT,
      Material.DEBUG_STICK,
      Material.KNOWLEDGE_BOOK,
      Material.SPAWNER,
      Material.DRAGON_EGG
   );
   private static final Map<String, Integer> MOB_KILL_REWARDS = Map.ofEntries(
      Map.entry("BEE", 5),
      Map.entry("BLAZE", 25),
      Map.entry("BOGGED", 20),
      Map.entry("BREEZE", 25),
      Map.entry("CAVE_SPIDER", 15),
      Map.entry("CREAKING", 30),
      Map.entry("CREEPER", 12),
      Map.entry("DOLPHIN", 5),
      Map.entry("DROWNED", 10),
      Map.entry("ELDER_GUARDIAN", 80),
      Map.entry("ENDER_DRAGON", 900),
      Map.entry("ENDERMAN", 25),
      Map.entry("ENDERMITE", 4),
      Map.entry("EVOKER", 40),
      Map.entry("GHAST", 22),
      Map.entry("GOAT", 5),
      Map.entry("GUARDIAN", 18),
      Map.entry("HOGLIN", 25),
      Map.entry("HUSK", 10),
      Map.entry("LLAMA", 5),
      Map.entry("MAGMA_CUBE", 8),
      Map.entry("NAUTILUS", 40),
      Map.entry("PHANTOM", 15),
      Map.entry("PIGLIN", 15),
      Map.entry("PIGLIN_BRUTE", 45),
      Map.entry("PILLAGER", 20),
      Map.entry("POLAR_BEAR", 5),
      Map.entry("RAVAGER", 50),
      Map.entry("SHULKER", 35),
      Map.entry("SILVERFISH", 4),
      Map.entry("SKELETON", 10),
      Map.entry("SLIME", 6),
      Map.entry("SPIDER", 10),
      Map.entry("STRAY", 15),
      Map.entry("SULFUR_CUBE", 25),
      Map.entry("TRADER_LLAMA", 5),
      Map.entry("VEX", 30),
      Map.entry("VINDICATOR", 45),
      Map.entry("WARDEN", 250),
      Map.entry("WITCH", 25),
      Map.entry("WITHER", 650),
      Map.entry("WITHER_SKELETON", 30),
      Map.entry("ZOGLIN", 40),
      Map.entry("ZOMBIE", 10),
      Map.entry("ZOMBIE_VILLAGER", 10),
      Map.entry("ZOMBIFIED_PIGLIN", 8)
   );
   private NamespacedKey mifronItemKey;
   private NamespacedKey merchantKey;
   private NamespacedKey merchantSpawnKey;
   private NamespacedKey merchantTradedKey;
   private NamespacedKey merchantTypeKey;
   private NamespacedKey merchantOfferKey;
   private NamespacedKey merchantOfferPriceKey;
   private NamespacedKey merchantOfferMaterialKey;
   private NamespacedKey merchantOfferAmountKey;
   private NamespacedKey merchantOfferMerchantKey;
   private NamespacedKey merchantOfferActionKey;
   private NamespacedKey merchantOfferRarityKey;
   private NamespacedKey barrelOfferPriceKey;
   private NamespacedKey barrelOfferRarityKey;
   private NamespacedKey ffaEntityKindKey;
   private NamespacedKey reincarnationStarKey;
   private NamespacedKey uiActionKey;
   private NamespacedKey uiTargetKey;
   private File dataFile;
   private FileConfiguration data;
   private final EconomyPriceTable economyPriceTable = new EconomyPriceTable(this);
   private final QuestService questService = new QuestService(this);
   private final ChunkProtectionFeature chunkProtectionFeature = new ChunkProtectionFeature(this);
   private final ProtectionService protectionService = new ProtectionService(this, this.chunkProtectionFeature);
   private final ServerPortalFeature serverPortalFeature = new ServerPortalFeature(this);
   private final SurvivalDimensionFeature survivalDimensionFeature = new SurvivalDimensionFeature(this);
   private final CompassFeature compassFeature = new CompassFeature(this);
   private final WorldRulesFeature worldRulesFeature = new WorldRulesFeature(this);
   private final UtilityItemsFeature utilityItemsFeature = new UtilityItemsFeature(this);
   private final AdvancedAnvilFeature advancedAnvilFeature = new AdvancedAnvilFeature(this);
   private BedrockUiFeature bedrockUiFeature;
   private final TextDisplayFeature textDisplayFeature = new TextDisplayFeature(this);
   private final AuctionFeature auctionFeature = new AuctionFeature(this, this.economyPriceTable);
   private final BuildWorldManager buildWorldManager = new BuildWorldManager(this);
   private final StructureManager structureManager = new StructureManager(this);
   private final ProposalManager proposalManager = new ProposalManager(this);
   private final QuestProgressListener questProgressListener = new QuestProgressListener(this, this.questService);
   private final ProtectedInteractionListener protectedInteractionListener = new ProtectedInteractionListener(this, this.protectionService);
   private final FfaManager ffaManager = new FfaManager(this);
   private final FfaListener ffaListener = new FfaListener(this, this.ffaManager);
   private final SlotMachineManager slotMachineManager = new SlotMachineManager(this);
   private final AthleticManager athleticManager = new AthleticManager(this);
   private final MinoruBridgeFeature minoruBridgeFeature = new MinoruBridgeFeature(this);
   private final Random random = new Random();
   private final Map<String, Integer> shopSalePrices = new HashMap<>();
   private final Map<String, Integer> shopBuyPrices = new HashMap<>();
   private final Map<String, Integer> merchantBuyWeights = new HashMap<>();
   private final Map<String, Integer> merchantSellWeights = new HashMap<>();
   private final Map<String, Mifron.BarrelShopConfig> barrelShopConfigs = new HashMap<>();
   private final Map<UUID, String> friendSearchFilters = new ConcurrentHashMap<>();
   private final Map<UUID, String> pendingFriendSearch = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> pendingFriendChatInput = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> activeFriendChatTarget = new ConcurrentHashMap<>();
   private final Map<UUID, String> friendChatDrafts = new ConcurrentHashMap<>();
   private final Set<UUID> merchantTransactions = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> lastMerchantTransaction = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> activeMerchantViews = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> activeMerchantPages = new ConcurrentHashMap<>();
   private final Map<UUID, String> temporaryActionBarMessages = new ConcurrentHashMap<>();
   private final Set<UUID> activeTutorials = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> temporaryActionBarUntil = new ConcurrentHashMap<>();
   private final Map<UUID, Long> shelfShopTransactionUntil = new ConcurrentHashMap<>();
   private final Map<UUID, Long> shopWandActionUntil = new ConcurrentHashMap<>();
   private final Map<UUID, Map<String, Mifron.KillRewardWindow>> mobRewardWindows = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastJumpPadUse = new ConcurrentHashMap<>();
   private final Map<UUID, Long> jumpPadFallProtectionUntil = new ConcurrentHashMap<>();
   private BukkitTask scheduledShutdownTask;
   private BukkitTask pendingDataSaveTask;
   private List<Material> shelfShopCatalog = List.of();
   private Map<Material, Integer> shelfShopCatalogNumbers = Map.of();
   private boolean shelfShopCatalogReady;
   private final Object shutdownLock = new Object();

   public void onEnable() {
      this.mifronItemKey = new NamespacedKey(this, "item");
      this.merchantKey = new NamespacedKey(this, "merchant");
      this.merchantSpawnKey = new NamespacedKey(this, "merchant_spawned_at");
      this.merchantTradedKey = new NamespacedKey(this, "merchant_traded");
      this.merchantTypeKey = new NamespacedKey(this, "merchant_type");
      this.merchantOfferKey = new NamespacedKey(this, "merchant_offer");
      this.merchantOfferPriceKey = new NamespacedKey(this, "merchant_offer_price");
      this.merchantOfferMaterialKey = new NamespacedKey(this, "merchant_offer_material");
      this.merchantOfferAmountKey = new NamespacedKey(this, "merchant_offer_amount");
      this.merchantOfferMerchantKey = new NamespacedKey(this, "merchant_offer_merchant");
      this.merchantOfferActionKey = new NamespacedKey(this, "merchant_offer_action");
      this.merchantOfferRarityKey = new NamespacedKey(this, "merchant_offer_rarity");
      this.barrelOfferPriceKey = new NamespacedKey(this, "barrel_offer_price");
      this.barrelOfferRarityKey = new NamespacedKey(this, "barrel_offer_rarity");
      this.ffaEntityKindKey = new NamespacedKey(this, "ffa_entity_kind");
      this.reincarnationStarKey = new NamespacedKey(this, "reincarnation_star");
      this.uiActionKey = new NamespacedKey(this, "ui_action");
      this.uiTargetKey = new NamespacedKey(this, "ui_target");
      this.saveDefaultConfig();
      this.getConfig().addDefault("advancement-rewards.multiplier", 5.0D);
      this.getConfig().addDefault("mob-kill-rewards.first-kill-bonus-multiplier", 5.0D);
      this.getConfig().addDefault("mob-kill-rewards.first-kill-bonus-minimum", 25);
      this.getConfig().addDefault("advanced-enchanting.enabled", true);
      this.getConfig().addDefault("advanced-enchanting.allowed-worlds", List.of("survival"));
      this.getConfig().addDefault("advanced-enchanting.max-enchantment-level", 20);
      this.getConfig().addDefault("advanced-enchanting.maximum-level-cost", 1000000);
      this.getConfig().addDefault("advanced-enchanting.mp-cost-per-level", 1);
      this.getConfig().addDefault("advanced-enchanting.allowed-materials", List.of());
      this.getConfig().addDefault("advanced-enchanting.blocked-materials", List.of());
      this.getConfig().addDefault("advanced-enchanting.blocked-enchantments", List.of());
      this.getConfig().addDefault("survival-dimensions.enabled", true);
      this.getConfig().addDefault("survival-dimensions.overworld", "survival");
      this.getConfig().addDefault("survival-dimensions.nether", "survival_nether");
      this.getConfig().addDefault("survival-dimensions.end", "survival_the_end");
      this.getConfig().addDefault("advanced-enchanting.excluded-item-ids", List.of(
         "emerald_bundle", "friend_book", "quest_book", "teleporter", "shelf_shop_wand",
         "shop_wand", "slot_wand", "server_wand", "jump_pad_wand", "hub_compass"
      ));
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      this.runStartupStep("migrate barrel shop offer slots", this::migrateBarrelShopOfferSlots);
      this.runStartupStep("migrate hub location", this::migrateDefaultHubLocation);
      this.runStartupStep("migrate minigame location", this::migrateDefaultMinigameLocation);
      this.runStartupStep("configure survival spawn location", this::configureSurvivalSpawnLocation);
      this.runStartupStep("normalize spawn locations to origin", this::normalizeSpawnLocationsToOrigin);
      this.runStartupStep("load economy price table", this.economyPriceTable::load);
      this.runStartupStep("load quest definitions", this.questService::load);
      this.runStartupStep("load shop prices", this::loadShopPrices);
      this.runStartupStep("apply economy price table", this::applyEconomyPriceTable);
      this.runStartupStep("cache shelf shop catalog", this::rebuildShelfShopCatalog);
      this.loadData();
      this.runStartupStep("ensure Survival dimensions", this.survivalDimensionFeature::ensureWorlds);
      this.runStartupStep("load build worlds", this.buildWorldManager::load);
      this.runStartupStep("start Minoru bridge API", this.minoruBridgeFeature::start);
      this.runStartupStep("sync shelf shop displays", this::syncShelfShopDisplays);
      this.runStartupStep("load structures", this.structureManager::load);
      this.runStartupStep("load proposals", this.proposalManager::load);
      this.runStartupStep("load text displays", this.textDisplayFeature::load);
      this.runStartupStep("load FFA", this.ffaManager::load);
      this.runStartupStep("load athletic", this.athleticManager::load);
      this.runStartupStep("start auction settlement", this.auctionFeature::start);
      this.runStartupStep("register Mifron events", () -> Bukkit.getPluginManager().registerEvents(this, this));
      this.runStartupStep("register chunk protection events", () -> Bukkit.getPluginManager().registerEvents(this.chunkProtectionFeature, this));
      this.runStartupStep("register protected interaction events", () -> Bukkit.getPluginManager().registerEvents(this.protectedInteractionListener, this));
      this.runStartupStep("register quest progress events", () -> Bukkit.getPluginManager().registerEvents(this.questProgressListener, this));
      this.runStartupStep("register auction events", () -> Bukkit.getPluginManager().registerEvents(this.auctionFeature, this));
      this.runStartupStep("register structure events", () -> Bukkit.getPluginManager().registerEvents(this.structureManager, this));
      this.runStartupStep("register build world events", () -> Bukkit.getPluginManager().registerEvents(this.buildWorldManager, this));
      this.runStartupStep("register FFA events", () -> Bukkit.getPluginManager().registerEvents(this.ffaListener, this));
      this.runStartupStep("register text display events", () -> Bukkit.getPluginManager().registerEvents(this.textDisplayFeature, this));
      this.runStartupStep("register server portal events", () -> Bukkit.getPluginManager().registerEvents(this.serverPortalFeature, this));
      this.runStartupStep("register Survival dimension portal events", () -> Bukkit.getPluginManager().registerEvents(this.survivalDimensionFeature, this));
      this.runStartupStep("register slot machine events", () -> Bukkit.getPluginManager().registerEvents(this.slotMachineManager, this));
      this.runStartupStep("register athletic events", () -> Bukkit.getPluginManager().registerEvents(this.athleticManager, this));
      this.runStartupStep("register compass events", () -> Bukkit.getPluginManager().registerEvents(this.compassFeature, this));
      this.runStartupStep("register utility item events", () -> Bukkit.getPluginManager().registerEvents(this.utilityItemsFeature, this));
      this.runStartupStep("register advanced anvil events", () -> Bukkit.getPluginManager().registerEvents(this.advancedAnvilFeature, this));
      if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
         this.bedrockUiFeature = new BedrockUiFeature(this);
         this.getLogger().info("Bedrock mobile Forms UI enabled through Geyser.");
      }
      this.registerCommand("mifron");
      this.registerCommand("mf");
      this.registerCommand("friend");
      this.registerCommand("status");
      this.registerCommand("tutorial");
      this.runStartupStep("apply world rules", this.worldRulesFeature::apply);
      this.runStartupStep("apply world spawn locations", this::applyWorldSpawnLocations);
      this.runStartupStep("normalize merchants", this::normalizeMerchants);
      Bukkit.getScheduler().runTaskTimer(this, this.worldRulesFeature::enforceFixedDayWorlds, 1L, 100L);
      Bukkit.getScheduler().runTaskTimer(this, this::grantPlaytimeRewards, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this::tickMerchants, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this::tickShelfShopActionBars, 10L, 10L);
      InventoryGroupFeature.install(this);
   }

   private void runStartupStep(String name, Runnable step) {
      try {
         step.run();
      } catch (Throwable e) {
         this.getLogger().severe("Startup step failed: " + name);
         e.printStackTrace();
      }
   }

   private void registerCommand(String name) {
      PluginCommand command = this.getCommand(name);
      if (command == null) {
         this.getLogger().severe("Command is missing from plugin.yml: " + name);
      } else {
         command.setExecutor(this);
         command.setTabCompleter(this);
      }
   }

   public void onDisable() {
      this.cancelScheduledAutoShutdown("the plugin is disabling");
      this.minoruBridgeFeature.stop();

      try {
         this.auctionFeature.shutdown();
      } catch (Throwable e) {
         this.getLogger().severe("Failed to disable auction settlement cleanly.");
         e.printStackTrace();
      }

      try {
         this.ffaManager.shutdown();
      } catch (Throwable e) {
         this.getLogger().severe("Failed to disable FFA cleanly.");
         e.printStackTrace();
      }

      try {
         this.athleticManager.shutdown();
      } catch (Throwable e) {
         this.getLogger().severe("Failed to disable athletic cleanly.");
         e.printStackTrace();
      }

      try {
         this.buildWorldManager.shutdown();
      } catch (Throwable e) {
         this.getLogger().severe("Failed to disable build worlds cleanly.");
         e.printStackTrace();
      }

      try {
         this.textDisplayFeature.disable();
      } catch (Throwable e) {
         this.getLogger().severe("Failed to disable text displays cleanly.");
         e.printStackTrace();
      }

      this.saveData();
      InventoryGroupFeature.shutdown(this);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      if (this.getConfig().getBoolean("auto-shutdown.enabled", true)) {
         this.cancelScheduledAutoShutdown("a player joined");
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.shopWandActionUntil.remove(player.getUniqueId());
      ConfigurationSection session = this.getPlayerSection(player.getUniqueId());
      String sessionId = session.getString("analytics.session-id", "");
      this.minoruBridgeFeature.sendAnalyticsEvent(player, "play_session_end", sessionId, "session-end:" + (sessionId.isBlank() ? player.getUniqueId() + ":" + System.currentTimeMillis() : sessionId));
      if (this.getConfig().getBoolean("auto-shutdown.enabled", true)) {
          long remaining = Bukkit.getOnlinePlayers().stream().filter(online -> !online.getUniqueId().equals(event.getPlayer().getUniqueId())).count();
         if (remaining == 0L) {
            this.scheduleAutoShutdown();
         }
      }
   }

   @EventHandler
   public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      String worldName = player.getWorld().getName();
      String survivalWorld = this.getConfig().getString("servers.survival.world", "survival");
      String minigameWorld = this.getConfig().getString("servers.minigame.world", "minigame");
      if (worldName.equalsIgnoreCase(survivalWorld) || worldName.equalsIgnoreCase("survival")) {
         this.trackAnalytics(player, "survival_join", "survival:" + player.getUniqueId() + ":" + LocalDate.now());
      } else if (worldName.equalsIgnoreCase(minigameWorld) || worldName.equalsIgnoreCase("minigame")) {
         this.trackAnalytics(player, "minigame_join", "minigame:" + player.getUniqueId() + ":" + LocalDate.now());
      }
   }

   private void scheduleAutoShutdown() {
      int delay = Math.max(1, this.getConfig().getInt("auto-shutdown.delay-seconds", 600));
      synchronized (this.shutdownLock) {
         if (this.scheduledShutdownTask != null) {
            this.scheduledShutdownTask.cancel();
         }

         this.scheduledShutdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            synchronized (this.shutdownLock) {
               if (Bukkit.getOnlinePlayers().isEmpty()) {
                  this.performAutoShutdown(delay);
               } else {
                  this.getLogger().info("Players returned before auto-shutdown; aborting.");
               }

               this.scheduledShutdownTask = null;
            }
         }, 20L * delay);
         this.getLogger().info("Scheduled auto-shutdown in " + delay + " seconds.");
      }
   }

   private void cancelScheduledAutoShutdown(String reason) {
      synchronized (this.shutdownLock) {
         if (this.scheduledShutdownTask != null) {
            this.scheduledShutdownTask.cancel();
            this.scheduledShutdownTask = null;
            this.getLogger().info("Cancelled scheduled auto-shutdown because " + reason + ".");
         }
      }
   }

   private void performAutoShutdown(int delaySeconds) {
      this.getLogger().info("No players online for " + delaySeconds + "s; stopping host VM.");

      try {
         this.saveData();
         Bukkit.savePlayers();

         for (World world : Bukkit.getWorlds()) {
            world.save();
         }
      } catch (Throwable e) {
         this.getLogger().warning("Failed to save before VM stop: " + e.getMessage());
      }

      Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
         try {
            String command = this.resolveVmStopCommand();
            this.getLogger().info("Executing VM stop command: " + command);
            int exitCode = this.runShellCommand(command);
            this.getLogger().info("VM stop command exited with code " + exitCode + ".");
            if (exitCode != 0) {
               this.getLogger().severe("VM stop command failed with exit code " + exitCode + ".");
            }
         } catch (Exception e) {
            this.getLogger().severe("Failed to stop VM: " + e.getMessage());
            e.printStackTrace();
         }
      });
   }

   private String resolveVmStopCommand() throws IOException {
      String provider = this.getConfig().getString("auto-shutdown.provider", "gcloud");
      if (provider != null && provider.equalsIgnoreCase("command")) {
         String command = this.getConfig().getString("auto-shutdown.vm-stop-command", "sudo shutdown -h now");
         if (command != null && !command.isBlank()) {
            String normalized = command.trim().replaceAll("\\s+", " ");
            if (!normalized.matches("(?i)(sudo )?(shutdown -h now|poweroff)")) {
               throw new IOException("auto-shutdown.vm-stop-command is not an allowed stop command");
            }
            return normalized;
         } else {
            throw new IOException("auto-shutdown.vm-stop-command is empty");
         }
      } else {
         String instance = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.instance"), readGceMetadata("instance/name"));
         String zonePath = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.zone"), readGceMetadata("instance/zone"));
         String zone = zoneFromMetadataPath(zonePath);
         String project = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.project"), readGceMetadata("project/project-id"));
         if (instance == null || instance.isBlank()) {
            throw new IOException("Could not resolve GCE instance name (set auto-shutdown.gcloud.instance)");
         }

         if (zone != null && !zone.isBlank()) {
            StringBuilder command = new StringBuilder("gcloud compute instances stop ")
               .append(shellQuote(instance.trim()))
               .append(" --zone=")
               .append(shellQuote(zone.trim()));
            if (project != null && !project.isBlank()) {
               command.append(" --project=").append(shellQuote(project.trim()));
            }

            command.append(" --quiet");
            return command.toString();
         } else {
            throw new IOException("Could not resolve GCE zone (set auto-shutdown.gcloud.zone)");
         }
      }
   }

   private static String zoneFromMetadataPath(String zonePathOrName) {
      if (zonePathOrName != null && !zonePathOrName.isBlank()) {
         String value = zonePathOrName.trim();
         int slash = value.lastIndexOf(47);
         return slash >= 0 ? value.substring(slash + 1) : value;
      } else {
         return null;
      }
   }

   /** Returns whether the player is currently in their own private Build world. */
   boolean isPlayerBuildWorld(Player player) {
      return player != null && this.buildWorldManager.isOwner(player, player.getWorld());
   }

   private static String firstNonBlank(String... values) {
      if (values == null) {
         return null;
      }

      for (String value : values) {
         if (value != null && !value.isBlank()) {
            return value.trim();
         }
      }

      return null;
   }

   private static String readGceMetadata(String path) {
      try {
         URI uri = URI.create("http://metadata.google.internal/computeMetadata/v1/" + path);
         HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3L)).header("Metadata-Flavor", "Google").GET().build();
         HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3L))
            .build()
            .send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() != 200) {
            return null;
         }

         String body = response.body();
         return body == null ? null : body.trim();
      } catch (Exception e) {
         return null;
      }
   }

   private static String shellQuote(String value) {
      return "'" + value.replace("'", "'\"'\"'") + "'";
   }

   private int runShellCommand(String command) throws IOException, InterruptedException {
      ProcessBuilder builder = newProcessBuilder(command);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

      String output;
      try {
         output = reader.lines().collect(Collectors.joining("\n"));
      } catch (Throwable var9) {
         try {
            reader.close();
         } catch (Throwable var8) {
            var9.addSuppressed(var8);
         }

         throw var9;
      }

      reader.close();
      if (!output.isBlank()) {
         this.getLogger().info("VM stop command output:\n" + output);
      }

      return process.waitFor();
   }

   private static ProcessBuilder newProcessBuilder(String command) {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      return os.contains("win") ? new ProcessBuilder("cmd.exe", "/c", command) : new ProcessBuilder("sh", "-c", command);
   }

   private void loadData() {
      this.dataFile = new File(this.getDataFolder(), "data.yml");
      if (!this.dataFile.exists()) {
         this.saveResource("data.yml", false);
      }

      this.data = YamlConfiguration.loadConfiguration(this.dataFile);
   }

   private void loadShopPrices() {
      this.shopSalePrices.clear();
      this.shopBuyPrices.clear();
      this.merchantBuyWeights.clear();
      this.merchantSellWeights.clear();
      this.barrelShopConfigs.clear();

      try (InputStream input = this.getResource("shop-prices.yml")) {
         if (input != null) {
            YamlConfiguration prices = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection section = prices.getConfigurationSection("prices");
            if (section == null) {
               this.getLogger().severe("shop-prices.yml does not contain a prices section.");
            } else {
               for (String materialName : section.getKeys(false)) {
                  List<Integer> values = section.getIntegerList(materialName);
                  if (values.size() >= 2) {
                     String key = materialName.toUpperCase(Locale.ROOT);
                     this.shopSalePrices.put(key, Math.max(0, values.get(0)));
                     this.shopBuyPrices.put(key, Math.max(0, values.get(1)));
                  }
               }

               this.loadWeightedPool(prices.getConfigurationSection("merchant.buy"), this.merchantBuyWeights);
               this.loadWeightedPool(prices.getConfigurationSection("merchant.sell"), this.merchantSellWeights);
               this.loadBarrelShopPool(prices.getConfigurationSection("barrel"));
               ConfigurationSection aliases = prices.getConfigurationSection("aliases");
               if (aliases != null) {
                  for (String targetName : aliases.getKeys(false)) {
                     String sourceName = aliases.getString(targetName, "").toUpperCase(Locale.ROOT);
                     Integer salePrice = this.shopSalePrices.get(sourceName);
                     Integer buyPrice = this.shopBuyPrices.get(sourceName);
                     if (salePrice != null && buyPrice != null) {
                        String targetKey = targetName.toUpperCase(Locale.ROOT);
                        this.shopSalePrices.putIfAbsent(targetKey, salePrice);
                        this.shopBuyPrices.putIfAbsent(targetKey, buyPrice);
                        this.copyWeightAlias(this.merchantBuyWeights, sourceName, targetKey);
                        this.copyWeightAlias(this.merchantSellWeights, sourceName, targetKey);
                        Mifron.BarrelShopConfig barrelConfig = this.barrelShopConfigs.get(sourceName);
                        if (barrelConfig != null) {
                           this.barrelShopConfigs.putIfAbsent(targetKey, barrelConfig);
                        }
                     }
                  }
               }

               this.getLogger()
                  .info(
                     "Loaded "
                        + this.shopSalePrices.size()
                        + " shop prices, "
                        + this.merchantSellWeights.size()
                        + " merchant sell weights, "
                        + this.merchantBuyWeights.size()
                        + " merchant buy weights, and "
                        + this.barrelShopConfigs.size()
                        + " barrel shop entries from the 26.2 price table."
                  );
            }
         } else {
            this.getLogger().severe("Bundled shop-prices.yml is missing. Falling back to legacy prices.");
         }
      } catch (IOException e) {
         this.getLogger().severe("Could not load shop-prices.yml: " + e.getMessage());
      }
   }

   private void loadWeightedPool(ConfigurationSection section, Map<String, Integer> target) {
      if (section != null) {
         for (String materialName : section.getKeys(false)) {
            int weight = section.getInt(materialName + ".weight", 0);
            if (weight > 0) {
               target.put(materialName.toUpperCase(Locale.ROOT), weight);
            }
         }
      }
   }

   private void loadBarrelShopPool(ConfigurationSection section) {
      if (section != null) {
         for (String materialName : section.getKeys(false)) {
            int weight = section.getInt(materialName + ".weight", 0);
            if (weight > 0) {
               String tier = section.getString(materialName + ".tier", "junk").toLowerCase(Locale.ROOT);
               this.barrelShopConfigs.put(materialName.toUpperCase(Locale.ROOT), new Mifron.BarrelShopConfig(tier, weight));
            }
         }
      }
   }

   private void copyWeightAlias(Map<String, Integer> weights, String sourceName, String targetName) {
      Integer weight = weights.get(sourceName);
      if (weight != null) {
         weights.putIfAbsent(targetName, weight);
      }
   }

   private void applyEconomyPriceTable() {
      int applied = 0;

      for (EconomyPriceTable.Entry entry : this.economyPriceTable.entries()) {
         String key = entry.material().name();
         if (entry.priceEm() > 0) {
            this.shopSalePrices.put(key, entry.priceEm());
         }

         if (entry.sellEm() > 0) {
            this.shopBuyPrices.put(key, entry.sellEm());
         }

         if (entry.merchantBuyPool() && entry.merchantBuyWeight() > 0) {
            this.merchantBuyWeights.put(key, entry.merchantBuyWeight());
         }

         if (entry.merchantSellPool() && entry.merchantSellWeight() > 0) {
            this.merchantSellWeights.put(key, entry.merchantSellWeight());
         }

         if (entry.barrelShopPool() && entry.barrelShopWeight() > 0) {
            this.barrelShopConfigs.put(key, new Mifron.BarrelShopConfig(entry.barrelTierKey(), entry.barrelShopWeight()));
         }

         applied++;
      }

      if (applied > 0) {
         this.getLogger().info("Applied " + applied + " economy price table entries over shop-prices.yml.");
      }
   }

   private void queueDataSave() {
      if (this.pendingDataSaveTask != null && !this.pendingDataSaveTask.isCancelled()) {
         return;
      }

      this.pendingDataSaveTask = Bukkit.getScheduler().runTaskLater(this, () -> {
         this.pendingDataSaveTask = null;
         this.saveData();
      }, 20L);
   }

   void trackAnalytics(Player player, String eventName, String dedupeKey) {
      if (player == null) return;
      this.minoruBridgeFeature.sendAnalyticsEvent(player, eventName, player.getUniqueId() + ":active", dedupeKey);
   }

   private void trackEconomyAnalytics(UUID uuid, String eventName, int amount, int balanceAfter, String reason) {
      if (uuid == null || amount <= 0) return;
      Player player = Bukkit.getPlayer(uuid);
      if (player == null) return;
      this.minoruBridgeFeature.sendEconomyAnalyticsEvent(player, eventName, player.getUniqueId() + ":active",
         "economy:" + eventName + ":" + uuid + ":" + UUID.randomUUID(), amount, balanceAfter, reason);
   }

   private void flushPendingFirstMpEvent(Player player) {
      if (player == null) return;
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("analytics.first-mp-earned-pending", false)
         || section.getBoolean("analytics.first-mp-earned-recorded", false)) {
         return;
      }
      section.set("analytics.first-mp-earned-pending", false);
      section.set("analytics.first-mp-earned-recorded", true);
      this.trackAnalytics(player, "first_mp_earned", "first-mp-earned:" + player.getUniqueId());
   }

   private void trackFirstAction(Player player) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      if (section.getBoolean("analytics.first-action-recorded", false)) {
         return;
      }
      section.set("analytics.first-action-recorded", true);
      this.trackAnalytics(player, "first_action", "first-action:" + player.getUniqueId());
      this.queueDataSave();
   }

   void saveData() {
      if (this.data != null && this.dataFile != null) {
         File parent = this.dataFile.getParentFile();
         if (parent != null && !parent.exists() && !parent.mkdirs()) {
            this.getLogger().severe("Could not create data folder: " + parent.getAbsolutePath());
         } else {
            File tempFile = new File(parent == null ? new File(".") : parent, this.dataFile.getName() + ".tmp");

            try {
               this.data.save(tempFile);

               try {
                  Files.move(tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               } catch (AtomicMoveNotSupportedException e) {
                  Files.move(tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }
            } catch (IOException e) {
               this.getLogger().severe("Could not save data.yml: " + e.getMessage());
               if (tempFile.exists() && !tempFile.delete()) {
                  this.getLogger().warning("Could not delete temporary data file: " + tempFile.getAbsolutePath());
               }
            }
         }
      }
   }

   FileConfiguration data() {
      return this.data;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.startPlayerSession(player);
      ConfigurationSection session = this.getPlayerSection(player.getUniqueId());
      String sessionId = player.getUniqueId() + ":" + System.currentTimeMillis();
      session.set("analytics.session-id", sessionId);
      session.set("analytics.session-start", System.currentTimeMillis());
      // Use Bukkit's persisted first-play marker for the acquisition cohort.
      // total-play-count is an in-game statistic and can be reset by an
      // administrator, so it must not decide whether a player is new.
      boolean firstJoin = !player.hasPlayedBefore();
      this.minoruBridgeFeature.sendAnalyticsEvent(player, firstJoin ? "first_join" : "return_join", sessionId, "join:" + player.getUniqueId() + ":" + java.time.LocalDate.now());
      this.minoruBridgeFeature.sendAnalyticsEvent(player, "play_session_start", sessionId, "session-start:" + sessionId);
      this.flushPendingFirstMpEvent(player);
      this.queueDataSave();
      this.giveInitialItems(player);
      this.applyPendingAdvancementReset(player);
      this.handleLoginReward(player);
      this.routeByWarningLevel(player);
      this.refreshPlayerName(player);
      Bukkit.getScheduler().runTaskLater(this, () -> this.syncAdvancementState(player), 20L);
      if (this.isFirstJoin(player)) {
         Bukkit.getScheduler().runTaskLater(this, () -> this.startTutorial(player, false), 40L);
      }
   }

   private void startPlayerSession(Player player) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      section.set("session-minutes", 0);
      section.set("session-playtime-rewards", 0);
      section.set("total-play-count", this.safeAdd(section.getInt("total-play-count", 0), 1));
      this.queueDataSave();
   }

   private boolean isFirstJoin(Player player) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      return section.getInt("total-play-count", 0) == 1 && !section.getBoolean("tutorial.started", false);
   }

   private void startTutorial(Player player, boolean manual) {
      if (player != null && player.isOnline()) {
         if (!this.activeTutorials.add(player.getUniqueId())) {
            if (manual) {
               player.sendMessage("§eチュートリアルはすでに進行中です。");
            }
         } else {
            ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
            section.set("tutorial.started", true);
            section.set("tutorial.last-started-at", System.currentTimeMillis());
            this.queueDataSave();
            List<String> steps = List.of(
               "§6Mifronへようこそ。まずは配布された4つのアイテムを確認しましょう。",
               "§eウォレット§7: 左クリックでMP残高を確認します。棚ショップ・スロットでは持って右クリックします。アイテム収納はできません。",
               "§eMPの注意§7: 通常のエメラルドを拾ってもMPにはなりません。エメラルドはアイテムとして残り、MPは報酬・クエスト・取引などで受け取ります。",
               "§6最初に遊ぶ§7: テレポーターで移動先を表示し、FFAでは中央のキット選択から好きなキットを選んで参加します。退出は /mf ffa leave、戦績は /mf ffa stats です。",
               "§dステータス・クエスト・移動§7: ステータス（ネザースター）で記録を、クエスト（レシピ本）で各クエストを確認できます。テレポーターの表示アイテムを左クリックすると移動できます。",
               "§a建築の注意§7: SurvivalではTNTと溶岩を使えません。中央広場などの保護チャンクではコンテナ・扉・額縁などを操作できません。詳しい案内は /tutorial で再表示できます。"
            );
            player.sendMessage("§6=== Mifron Tutorial ===");

            for (int i = 0; i < steps.size(); i++) {
               int index = i;
               Bukkit.getScheduler().runTaskLater(this, () -> {
                  if (!player.isOnline()) {
                     this.activeTutorials.remove(player.getUniqueId());
                  } else {
                     player.sendMessage(steps.get(index));
                     player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.0F + index * 0.08F);
                     if (index == steps.size() - 1) {
                        this.getPlayerSection(player.getUniqueId()).set("tutorial.completed", true);
                        this.getPlayerSection(player.getUniqueId()).set("tutorial.completed-at", System.currentTimeMillis());
                        this.queueDataSave();
                        this.activeTutorials.remove(player.getUniqueId());
                     }
                  }
               }, 20L * i);
            }
         }
      }
   }

   private void giveInitialItems(Player player) {
      this.utilityItemsFeature.giveInitialItems(player);
   }

   void giveInitialItemsAfterInventoryRestore(Player player) {
      this.utilityItemsFeature.giveInitialItems(player);
   }

   private boolean hasMifronItem(Player player, String id) {
      return this.utilityItemsFeature.hasMifronItem(player, id);
   }

   private ItemStack createShopWand() {
      return this.utilityItemsFeature.createShopWand();
   }

   private ItemStack createShopWand(ShopWandType type) {
      return this.utilityItemsFeature.createShopWand(type);
   }

   private ItemStack createJumpPadWand(int verticalPower, int horizontalPower) {
      return this.utilityItemsFeature.createJumpPadWand(verticalPower, horizontalPower);
   }

   boolean isMifronItem(ItemStack item, String id) {
      return this.utilityItemsFeature.isMifronItem(item, id);
   }

   boolean isShopWand(ItemStack item) {
      return this.utilityItemsFeature.isShopWand(item);
   }

   boolean isLegacyShopWand(ItemStack item) {
      return this.utilityItemsFeature.isLegacyShopWand(item);
   }

   private ShopWandType shopWandType(ItemStack item) {
      return this.utilityItemsFeature.getShopWandType(item);
   }

   private boolean isReincarnationStar(ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && Boolean.TRUE.equals(MifronPdc.get(item.getItemMeta().getPersistentDataContainer(), this.reincarnationStarKey, PersistentDataType.BOOLEAN));
   }

   private void consumeOne(ItemStack item) {
      item.setAmount(item.getAmount() - 1);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onWorldEditPasteCommand(PlayerCommandPreprocessEvent event) {
      String command = event.getMessage().trim().toLowerCase(Locale.ROOT);
      if (!command.equals("//paste") && !command.startsWith("//paste ")
         && !command.equals("/worldedit:paste") && !command.startsWith("/worldedit:paste ")) {
         return;
      }

      Player player = event.getPlayer();
      if (!this.canCreateShop(player)) {
         return;
      }
      Mifron.ShopPasteSnapshot before = this.snapshotWorldEditShops(player.getWorld());
      Bukkit.getScheduler().runTaskLater(this, () -> this.registerPastedShops(player, before), 2L);
   }

   private Mifron.ShopPasteSnapshot snapshotWorldEditShops(World world) {
      Map<String, String> unregisteredShelves = new HashMap<>();
      Set<String> registeredShelfSignatures = new HashSet<>();
      Map<String, String> unregisteredBarrels = new HashMap<>();
      Set<String> registeredBarrelSignatures = new HashSet<>();
      List<Material> catalog = this.shelfShopCatalogMaterials();
      for (int start = 0; start < catalog.size(); start += SHELF_SHOP_OFFER_SLOTS) {
         registeredShelfSignatures.add(this.shelfOfferSignature(catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS))));
      }
      for (Chunk chunk : world.getLoadedChunks()) {
         for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Shelf shelf) {
               Block block = shelf.getBlock();
               String signature = this.shelfInventorySignature(shelf);
               if (this.isShelfShop(block)) {
                  if (!this.isEmptyShelfSignature(signature)) {
                     registeredShelfSignatures.add(signature);
                  }
               } else if (!this.slotMachineManager.isMachine(block)) {
                  unregisteredShelves.put(this.shopPositionKey(block), signature);
               }
            } else if (state instanceof Barrel barrel) {
               Block block = barrel.getBlock();
               String signature = this.barrelInventorySignature(barrel);
               if (this.isBarrelShop(block)) {
                  if (this.isBarrelOfferSignature(signature)) {
                     registeredBarrelSignatures.add(signature);
                  }
               } else {
                  unregisteredBarrels.put(this.shopPositionKey(block), signature);
               }
            }
         }
      }
      return new Mifron.ShopPasteSnapshot(
         world,
         Map.copyOf(unregisteredShelves),
         Set.copyOf(registeredShelfSignatures),
         Map.copyOf(unregisteredBarrels),
         Set.copyOf(registeredBarrelSignatures)
      );
   }

   private void registerPastedShops(Player player, Mifron.ShopPasteSnapshot before) {
      World world = before.world();
      if (world == null) {
         return;
      }

      List<Block> pastedShelves = new ArrayList<>();
      List<Block> pastedBarrels = new ArrayList<>();
      for (Chunk chunk : world.getLoadedChunks()) {
         for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Shelf shelf) {
               Block block = shelf.getBlock();
               if (this.isShelfShop(block) || this.slotMachineManager.isMachine(block)) {
                  continue;
               }
               String signature = this.shelfInventorySignature(shelf);
               String previous = before.unregisteredShelfSignatures().get(this.shopPositionKey(block));
               if (!Objects.equals(previous, signature)
                  && !this.isEmptyShelfSignature(signature)
                  && before.registeredShelfSignatures().contains(signature)) {
                  pastedShelves.add(block);
               }
            } else if (state instanceof Barrel barrel) {
               Block block = barrel.getBlock();
               if (this.isBarrelShop(block)) {
                  continue;
               }
               String signature = this.barrelInventorySignature(barrel);
               String previous = before.unregisteredBarrelSignatures().get(this.shopPositionKey(block));
               if (!Objects.equals(previous, signature)
                  && this.isBarrelOfferSignature(signature)
                  && before.registeredBarrelSignatures().contains(signature)) {
                  pastedBarrels.add(block);
               }
            }
         }
      }

      if (pastedShelves.isEmpty() && pastedBarrels.isEmpty()) {
         return;
      }

      BlockFace facing = player.getFacing();
      pastedShelves.sort((a, b) -> this.comparePastedShelves(a, b, facing));
      int firstOrder = this.nextSequentialShelfOrder();
      for (Block block : pastedShelves) {
         this.configureSequentialShelfShop(block);
         this.setShopOwner(block, player.getUniqueId());
      }
      for (Block block : pastedBarrels) {
         this.setBarrelShopMeta(block);
         this.setBarrelShop(block, true);
         this.setShopOwner(block, player.getUniqueId());
      }
      this.queueDataSave();
      if (player.isOnline()) {
         List<String> details = new ArrayList<>();
         if (!pastedShelves.isEmpty()) {
            int lastOrder = firstOrder + pastedShelves.size() - 1;
            details.add("棚 " + pastedShelves.size() + "台（棚番号 " + firstOrder + "～" + lastOrder + "）");
         }
         if (!pastedBarrels.isEmpty()) {
            details.add("樽 " + pastedBarrels.size() + "台");
         }
         player.sendMessage("§aWorldEditで貼り付けたショップを自動登録しました: " + String.join(" / ", details));
      }
      this.getLogger().info(
         "Auto-registered " + pastedShelves.size() + " pasted shelf shops and " + pastedBarrels.size() + " pasted barrel shops for " + player.getName() + "."
      );
   }

   private int comparePastedShelves(Block a, Block b, BlockFace facing) {
      int row = Integer.compare(b.getY(), a.getY());
      if (row != 0) {
         return row;
      }
      int horizontal = switch (facing) {
         case SOUTH -> Integer.compare(b.getX(), a.getX());
         case EAST -> Integer.compare(a.getZ(), b.getZ());
         case WEST -> Integer.compare(b.getZ(), a.getZ());
         default -> Integer.compare(a.getX(), b.getX());
      };
      if (horizontal != 0) {
         return horizontal;
      }
      int depth = switch (facing) {
         case SOUTH -> Integer.compare(a.getZ(), b.getZ());
         case EAST -> Integer.compare(a.getX(), b.getX());
         case WEST -> Integer.compare(b.getX(), a.getX());
         default -> Integer.compare(b.getZ(), a.getZ());
      };
      return depth != 0 ? depth : this.shopPositionKey(a).compareTo(this.shopPositionKey(b));
   }

   private String shelfInventorySignature(Shelf shelf) {
      return java.util.Arrays.stream(shelf.getSnapshotInventory().getContents())
         .map(item -> item == null || item.getType() == Material.AIR ? "AIR" : item.getType().name())
         .collect(Collectors.joining(","));
   }

   private String shelfOfferSignature(List<Material> materials) {
      List<String> slots = new ArrayList<>();
      for (int slot = 0; slot < SHELF_SHOP_OFFER_SLOTS; slot++) {
         Material material = slot < materials.size() ? materials.get(slot) : Material.AIR;
         slots.add(material == null || material == Material.AIR ? "AIR" : material.name());
      }
      return String.join(",", slots);
   }

   private boolean isEmptyShelfSignature(String signature) {
      return signature == null || signature.isBlank() || java.util.Arrays.stream(signature.split(",", -1)).allMatch("AIR"::equals);
   }

   private String shopPositionKey(Block block) {
      return block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private String barrelInventorySignature(Barrel barrel) {
      return java.util.Arrays.stream(barrel.getInventory().getContents()).map(item -> {
         if (item == null || item.getType() == Material.AIR) {
            return "AIR";
         }
         ItemMeta meta = item.getItemMeta();
         Integer price = MifronPdc.get(meta.getPersistentDataContainer(), this.barrelOfferPriceKey, PersistentDataType.INTEGER);
         String rarity = MifronPdc.get(meta.getPersistentDataContainer(), this.barrelOfferRarityKey, PersistentDataType.STRING);
         if (price == null || price <= 0 || rarity == null || rarity.isBlank()) {
            return "INVALID:" + item.getType().name();
         }
         int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
         return "OFFER:" + item.getType().name() + ":" + item.getAmount() + ":" + price + ":" + rarity + ":" + damage;
      }).collect(Collectors.joining("|"));
   }

   private boolean isBarrelOfferSignature(String signature) {
      if (signature == null || signature.isBlank()) {
         return false;
      }
      boolean hasOffer = false;
      for (String slot : signature.split("\\|", -1)) {
         if (slot.startsWith("OFFER:")) {
            hasOffer = true;
         } else if (!"AIR".equals(slot)) {
            return false;
         }
      }
      return hasOffer;
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (event.getHand() == null || event.getHand() == EquipmentSlot.HAND || this.serverPortalFeature.isServerWand(item)) {
         Player player = event.getPlayer();
         this.trackFirstAction(player);
         if (!this.compassFeature.handleCompassClick(event)) {
            if (this.isShopWand(item)) {
               this.handleShopWandClick(event);
            } else if (this.isMifronItem(item, "jump_pad_wand")) {
               this.handleJumpPadWandClick(event);
            } else if (this.serverPortalFeature.isServerWand(item)) {
               this.serverPortalFeature.handleWandClick(event);
            } else if (event.getAction().isRightClick()
               && event.getClickedBlock() != null
               && this.isShelf(event.getClickedBlock().getType())
               && this.isShelfShop(event.getClickedBlock())) {
               if (!this.slotMachineManager.isMachine(event.getClickedBlock())) {
                  // Cancel the vanilla shelf interaction immediately so its use animation/inventory
                  // handling cannot overlap the Mifron transaction. Bedrock clients can also emit
                  // duplicate interact packets for one tap, so collapse only near-identical packets.
                  event.setCancelled(true);
                  long now = System.currentTimeMillis();
                  long blockedUntil = this.shelfShopTransactionUntil.getOrDefault(player.getUniqueId(), 0L);
                  if (now < blockedUntil) {
                     return;
                  }
                  this.shelfShopTransactionUntil.put(player.getUniqueId(), now + 90L);

                  if (this.isMifronItem(item, "emerald_bundle")) {
                     this.tryShopPayment(player, event.getClickedBlock());
                  } else {
                     this.tryShopSell(player, event.getClickedBlock(), item);
                  }
               }
            } else if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.isBarrelShop(event.getClickedBlock())) {
               if (event.getClickedBlock().getState() instanceof Barrel barrel) {
                  Inventory barrelInventory = barrel.getInventory();
                  if (this.bedrockUiFeature == null
                     || !this.bedrockUiFeature.showMenu(
                        player,
                        "Mifron Barrel Shop",
                        barrelInventory,
                        itemx -> itemx != null && itemx.getType() != Material.AIR,
                        (slot, itemx) -> {
                           this.buyBarrelOffer(player, itemx, slot, barrelInventory);
                           if (player.isOnline() && this.bedrockUiFeature != null) {
                              this.bedrockUiFeature.showMenu(
                                 player,
                                 "Mifron Barrel Shop",
                                 barrelInventory,
                                 next -> next != null && next.getType() != Material.AIR,
                                 (nextSlot, nextItem) -> this.buyBarrelOffer(player, nextItem, nextSlot, barrelInventory)
                              );
                           }
                        }
                     )) {
                     player.openInventory(barrelInventory);
                  }
               }

               event.setCancelled(true);
            } else if (item != null) {
               if (this.isMifronItem(item, "emerald_bundle")) {
                  if (event.getAction().isRightClick() && event.getClickedBlock() == null) {
                     event.setCancelled(true);
                     return;
                  }

                  if (event.getAction().isLeftClick()) {
                     player.sendMessage("§a所持MP: " + this.formatNumber(this.getEmeralds(player.getUniqueId())));
                     event.setCancelled(true);
                     return;
                  }

                  if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.tryShopPayment(player, event.getClickedBlock())) {
                     event.setCancelled(true);
                     return;
                  }
               }

                if (this.isMifronItem(item, "friend_book") && event.getAction().isRightClick()) {
                   this.openFriendUi(player);
                   event.setCancelled(true);
               } else if (this.isMifronItem(item, "quest_book") && event.getAction().isRightClick()) {
                  this.openQuestUi(player, "categories");
                  event.setCancelled(true);
               } else if (this.isReincarnationStar(item) && event.getAction().isRightClick()) {
                  event.setCancelled(true);
                  this.tryReincarnate(player, item);
               } else {
                  if (this.isMifronItem(item, "teleporter") && event.getAction().isRightClick()) {
                     // Teleporter use is handled by ServerPortalFeature. Cancelling here also
                     // prevents the custom Ender Eye from being thrown in the air.
                     event.setCancelled(true);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onPickupItem(EntityPickupItemEvent event) {
      if (event.getEntity() instanceof Player player) {
         ItemStack var4 = event.getItem().getItemStack();
         this.tryConvertProposedItem(event, var4);
         this.recordAcquiredItem(player, var4.getType());
         // 通常のエメラルドはアイテムとして拾う。MPへの自動変換は行わない。
      }
   }

   private void tryConvertProposedItem(EntityPickupItemEvent event, ItemStack source) {
      ConfigurationSection proposals = this.getConfig().getConfigurationSection("custom-items");
      if (proposals == null || source == null || source.getAmount() <= 0) {
         return;
      }
      for (String id : proposals.getKeys(false)) {
         String path = "custom-items." + id;
         if (!this.getConfig().getBoolean(path + ".conversion-enabled", true)) {
            continue;
         }
         Material base = Material.matchMaterial(this.getConfig().getString(path + ".base-material", ""));
         Material result = Material.matchMaterial(this.getConfig().getString(path + ".material", ""));
         double chance = Math.max(0.0, Math.min(0.01, this.getConfig().getDouble(path + ".conversion-chance", 0.001)));
         if (base == null || result == null || base == result || source.getType() != base || chance <= 0.0 || this.random.nextDouble() >= chance) {
            continue;
         }
         ItemStack converted = this.named(result, this.getConfig().getString(path + ".display-name", id), this.getConfig().getStringList(path + ".lore"));
         ItemMeta meta = converted.getItemMeta();
         meta.setEnchantmentGlintOverride(this.getConfig().getBoolean(path + ".glint", false));
         converted.setItemMeta(meta);
         source.setAmount(source.getAmount() - 1);
         event.getItem().getWorld().dropItem(event.getItem().getLocation(), converted);
         event.getItem().setItemStack(source);
         break;
      }
   }

   @EventHandler
   public void onMobDeath(EntityDeathEvent event) {
      LivingEntity entity = event.getEntity();
      Player killer = entity.getKiller();
      if (killer != null && !(entity instanceof Player) && !this.isMifronMerchant(entity)) {
         if (!this.isFfaSummonedMob(entity)) {
            if (this.isDirectPlayerKill(entity, killer)) {
               int baseReward = this.mobKillReward(entity.getType());
               if (baseReward > 0) {
                  int reward = this.applyIncomeBonus(killer.getUniqueId(), this.adjustedMobKillReward(killer.getUniqueId(), entity.getType(), baseReward));
                  this.addPlayerStat(killer.getUniqueId(), "total-mob-kills", 1);
                  boolean firstKill = this.addKilledMob(killer.getUniqueId(), entity.getType());
                  this.depositEmeralds(killer.getUniqueId(), reward);
                  killer.sendMessage("§a討伐報酬: +" + this.formatNumber(reward) + "MP");
                  if (firstKill) {
                     int firstKillBonus = this.applyIncomeBonus(killer.getUniqueId(), this.firstMobKillBonus(baseReward));
                     if (firstKillBonus > 0) {
                        this.depositEmeralds(killer.getUniqueId(), firstKillBonus);
                        killer.sendMessage("§6初討伐ボーナス: +" + this.formatNumber(firstKillBonus) + "MP");
                     }
                  }
               }
            }
         }
      }
   }

   private int mobKillReward(EntityType type) {
      return type == null ? 0 : MOB_KILL_REWARDS.getOrDefault(type.name(), 0);
   }

   private int firstMobKillBonus(int baseReward) {
      double multiplier = Math.max(0.0D, Math.min(100.0D, this.getConfig().getDouble("mob-kill-rewards.first-kill-bonus-multiplier", 5.0D)));
      int minimum = Math.max(0, this.getConfig().getInt("mob-kill-rewards.first-kill-bonus-minimum", 25));
      return (int)Math.min(2000000000L, Math.max((long)minimum, Math.round(baseReward * multiplier)));
   }

   private boolean isFfaSummonedMob(LivingEntity entity) {
      String kind = (String)entity.getPersistentDataContainer().get(this.ffaEntityKindKey, PersistentDataType.STRING);
      return "summon".equals(kind);
   }

   private int adjustedMobKillReward(UUID uuid, EntityType type, int baseReward) {
      if (baseReward > 0 && type != null) {
         long now = System.currentTimeMillis();
         Map<String, Mifron.KillRewardWindow> playerWindows = this.mobRewardWindows.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
         Mifron.KillRewardWindow window = playerWindows.computeIfAbsent(type.name(), ignored -> new Mifron.KillRewardWindow(now));
         if (now - window.startedAtMillis >= 3600000L) {
            window.startedAtMillis = now;
            window.count = 0;
         }

         window.count++;
         return window.count > 100 && this.isFarmAdjustedMob(type) ? Math.max(1, baseReward / 2) : baseReward;
      } else {
         return 0;
      }
   }

   private boolean isFarmAdjustedMob(EntityType type) {
      return switch (type) {
         case BEE, CAVE_SPIDER, DOLPHIN, ENDERMITE, GOAT, GUARDIAN, LLAMA, MAGMA_CUBE, POLAR_BEAR, SILVERFISH, SLIME, TRADER_LLAMA, ZOMBIFIED_PIGLIN -> true;
         default -> false;
      };
   }

   private boolean isDirectPlayerKill(LivingEntity entity, Player killer) {
      if (!(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent entityDamage)) {
         return false;
      } else {
         Entity damager = entityDamage.getDamager();
         if (damager instanceof Player player) {
            return player.getUniqueId().equals(killer.getUniqueId());
         } else {
            return !(damager instanceof Projectile projectile)
               ? false
               : projectile.getShooter() instanceof Player player && player.getUniqueId().equals(killer.getUniqueId());
         }
      }
   }

   private boolean addKilledMob(UUID uuid, EntityType type) {
      Set<String> killed = new HashSet<>(this.getPlayerSection(uuid).getStringList("killed-mobs"));
      if (killed.add(type.name())) {
         this.getPlayerSection(uuid).set("killed-mobs", new ArrayList<>(killed));
         this.queueDataSave();
         return true;
      }
      return false;
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (!this.ffaManager.isPlaying(player)) {
         int current = this.getEmeralds(player.getUniqueId());
         int lost = current / 2;
         if (lost > 0) {
            this.withdrawEmeralds(player.getUniqueId(), lost);
            player.sendMessage("§c死亡により所持MPの50%を失いました: -" + this.formatNumber(lost) + "MP");
         }
      }
   }

   private boolean tryShopPayment(Player player, Block block) {
      Mifron.ShelfShopOffer offer = this.readShelfShopOffer(player, block);
      if (offer == null) {
         return false;
      }

      if (offer.material() != null && offer.price() > 0) {
         int discountedPrice = this.applyShopDiscount(player, offer.price());
         int currentEmeralds = this.getEmeralds(player.getUniqueId());
         int stock = this.shelfShopStock(offer.material());
         if (stock < offer.amount()) {
            this.showTemporaryActionBar(player, "在庫切れです。プレイヤーが売却すると再入荷します。");
            return true;
         } else if (currentEmeralds < discountedPrice) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice - currentEmeralds) + "MP");
            return true;
         } else if (this.inventorySpaceFor(player, offer.material()) < offer.amount()) {
            this.showTemporaryActionBar(player, "インベントリに空きがありません。");
            return true;
         } else if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice, false)) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice) + "MP");
            return true;
         } else {
            this.changeShelfShopStock(offer.material(), -offer.amount());
            this.giveShopPurchasedItems(player, offer.material(), offer.amount());
            this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount(), false);
            this.queueDataSave();
            this.playPurchaseSound(player);
            this.sendItemMessage(
               player,
               NamedTextColor.GREEN,
               "購入しました: ",
               offer.material(),
               " x" + offer.amount() + " (" + this.formatNumber(discountedPrice) + "MP / 在庫 " + this.formatNumber(this.shelfShopStock(offer.material())) + ")"
            );
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean tryShopSell(Player player, Block block, ItemStack held) {
      if (player == null || block == null || held == null || held.getAmount() <= 0) {
         return false;
      }

      Mifron.ShelfShopOffer offer = this.readShelfShopOffer(player, block);
      if (offer == null || offer.material() == null || held.getType() != offer.material()) {
         return false;
      }

      Material material = offer.material();
      int currentSellPrice = this.applyShopDiscount(player, offer.price());
      int price = Math.max(0, Math.min(this.materialBuyPrice(material), currentSellPrice - 1));
      if (price <= 0) {
         this.showTemporaryActionBar(player, "このアイテムは買い取り対象外です。");
         return true;
      }

      if (this.utilityItemsFeature.getMifronItemId(held) != null || this.isShopWand(held)) {
         return true;
      }

      held.setAmount(held.getAmount() - 1);
      this.changeShelfShopStock(material, 1);
      this.depositEmeralds(player.getUniqueId(), price, false);
      this.addPlayerStat(player.getUniqueId(), "total-trades", 1, false);
      this.recordFarmingSubmission(player, material);
      this.queueDataSave();
      this.playPurchaseSound(player);
      this.sendItemMessage(
         player,
         NamedTextColor.GREEN,
         "買い取りました: ",
         material,
         " (" + this.formatNumber(price) + "MP / 在庫 " + this.formatNumber(this.shelfShopStock(material)) + ")"
      );
      return true;
   }

   private void handleShopWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block block = event.getClickedBlock();
      ItemStack wand = event.getItem();
      long now = System.currentTimeMillis();
      long blockedUntil = this.shopWandActionUntil.getOrDefault(player.getUniqueId(), 0L);
      if (blockedUntil > now) {
         event.setCancelled(true);
         return;
      }
      this.shopWandActionUntil.put(player.getUniqueId(), now + SHOP_WAND_ACTION_COOLDOWN_MILLIS);
      ShopWandType type = this.shopWandType(wand);
      if (type == null) {
         player.sendMessage("§cこのワンドの種類を判別できません。再発行してください。");
         event.setCancelled(true);
      } else if (type.isSlotWand()) {
         this.handleSlotWandClick(event, player, block, type);
      } else if (type == ShopWandType.FRAME) {
         player.sendMessage("§c額縁ショップは未実装です。額縁は既存のオークション機能を使用してください。");
         event.setCancelled(true);
      } else if (block == null || !this.isValidShopWandTarget(block, type)) {
         player.sendMessage("§c" + this.shopWandTargetMessage(type));
         event.setCancelled(true);
      } else if (event.getAction().isRightClick() && !this.canCreateShop(player)) {
         player.sendMessage("§c権限がありません。");
         event.setCancelled(true);
      } else if (event.getAction().isLeftClick() && !this.canManageShop(player, block)) {
         player.sendMessage("§cこのショップを解除できるのは作成者または管理者のみです。");
         event.setCancelled(true);
      } else if (block.getType() == Material.BARREL) {
         this.handleBarrelShopWandClick(player, block, event, type);
      } else if (event.getAction().isRightClick()) {
         if (player.isSneaking()) {
            ItemStack specified = player.getInventory().getItemInOffHand();
            Material material = specified == null ? Material.AIR : specified.getType();
            if (material == Material.AIR || !this.isRandomShopItem(material) || this.utilityItemsFeature.getMifronItemId(specified) != null) {
               player.sendMessage("§e指定配置: オフハンドに販売したい通常アイテムを持ち、設定したい棚の枠をShift+右クリックしてください。");
               event.setCancelled(true);
               return;
            }

            int selectedSlot = this.selectedShelfSlot(player, block);
            this.assignCustomShelfShopSlot(block, selectedSlot, material);
            this.setShopOwner(block, player.getUniqueId());
            player.sendMessage(
               "§a指定配置に設定: 枠" + (selectedSlot + 1) + " → No." + String.format("%03d", this.shelfShopCatalogNumber(material)) + " " + this.japaneseItemName(material)
            );
         } else {
            this.configureSequentialShelfShop(block);
            this.setShopOwner(block, player.getUniqueId());
            List<Material> materials = this.shelfShopRandomOffers(block);
            if (materials.isEmpty()) {
               player.sendMessage("§e順番配置の対象商品がありません。");
            } else {
               int first = this.shelfShopCatalogNumber(materials.get(0));
               int last = this.shelfShopCatalogNumber(materials.get(materials.size() - 1));
               player.sendMessage("§a順番配置に設定しました: No." + String.format("%03d", first) + "～No." + String.format("%03d", last));
            }
         }
         event.setCancelled(true);
      } else if (event.getAction().isLeftClick()) {
         if (player.isSneaking() && "custom".equals(this.shelfShopMode(block))) {
            int selectedSlot = this.selectedShelfSlot(player, block);
            this.clearCustomShelfShopSlot(block, selectedSlot);
            player.sendMessage("§a指定配置の枠" + (selectedSlot + 1) + "を空欄にしました。");
         } else if (this.setShelfShop(block, false)) {
            player.sendMessage("§a棚のショップ化を解除しました。");
         } else {
            player.sendMessage("§eこの棚はショップ化されていません。");
         }
         event.setCancelled(true);
      }
   }

   private void handleSlotWandClick(PlayerInteractEvent event, Player player, Block block, ShopWandType type) {
      event.setCancelled(true);
      if (block != null && this.isShelf(block.getType())) {
         if (event.getAction().isRightClick()) {
            SlotMachineManager.Difficulty difficulty = type.getSlotDifficulty();
            if (difficulty == null) {
               player.sendMessage("§c無効なスロットワンドです。");
               event.setCancelled(true);
               return;
            }

            SlotMachineManager manager = this.slotMachineManager;
            if (manager == null) {
               player.sendMessage("§cスロットマシンシステムが初期化されていません。");
               event.setCancelled(true);
               return;
            }

            // Slot machines and shelf shops share the same physical shelf block type.
            // Purge every shelf-shop marker before registering the slot machine so the
            // machine cannot be treated as a numbered shop shelf later.
            this.setShelfShop(block, false);
            this.data.set(this.shelfShopPath(block), null);
            this.data.set(this.shelfShopOfferPath(block), null);
            this.clearShopOwner(block);
            this.queueDataSave();
            if (!manager.registerMachine(block, difficulty)) {
               player.sendMessage("§cスロットマシンの初期化に失敗しました。");
               event.setCancelled(true);
               return;
            }

            player.sendMessage("§b棚をスロットマシン化しました！");
            player.sendMessage("§7ウォレット（バンドル）を持って右クリックで回転します。");
         }
      } else {
         player.sendMessage("§c棚をクリックしてください。");
         event.setCancelled(true);
      }
   }

   private boolean isValidShopWandTarget(Block block, ShopWandType type) {
      if (type == ShopWandType.SHELF) {
         return this.isShelf(block.getType());
      } else {
         return type == ShopWandType.BARREL ? block.getType() == Material.BARREL : this.isShelf(block.getType()) || block.getType() == Material.BARREL;
      }
   }

   private String shopWandTargetMessage(ShopWandType type) {
      if (type == ShopWandType.SHELF) {
         return "棚をクリックしてください。";
      } else {
         return type == ShopWandType.BARREL ? "樽をクリックしてください。" : "棚または樽をクリックしてください。";
      }
   }

   private void handleBarrelShopWandClick(Player player, Block block, PlayerInteractEvent event, ShopWandType type) {
      if (event.getAction().isRightClick()) {
         if (block.getState() instanceof Barrel barrel) {
            if (type == ShopWandType.BARREL) {
               this.populateBarrelShop(barrel);
               this.setBarrelShopMeta(block);
            } else {
               this.populateBarrelShop(barrel);
               this.clearBarrelShopMeta(block);
            }

            this.setBarrelShop(block, true);
            this.setShopOwner(block, player.getUniqueId());
            player.sendMessage("§a樽をショップ化し、商品を生成しました。");
            event.setCancelled(true);
         } else {
            player.sendMessage("§c樽を読み込めませんでした。");
            event.setCancelled(true);
         }
      } else {
         if (event.getAction().isLeftClick()) {
            if (this.setBarrelShop(block, false)) {
               if (block.getState() instanceof Barrel barrel) {
                  barrel.getInventory().clear();
               }

               this.clearBarrelShopMeta(block);
               player.sendMessage("§a樽のショップ化を解除しました。");
            } else {
               player.sendMessage("§eこの樽はショップ化されていません。");
            }

            event.setCancelled(true);
         }
      }
   }

   private void tickShelfShopActionBars() {
      long now = System.currentTimeMillis();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.recordInventoryAcquisitions(player);
         String temporaryMessage = this.temporaryActionBarMessages.get(player.getUniqueId());
         long temporaryUntil = this.temporaryActionBarUntil.getOrDefault(player.getUniqueId(), 0L);
         if (temporaryMessage != null && temporaryUntil > now) {
            player.sendActionBar(Component.text(temporaryMessage, NamedTextColor.RED));
         } else {
            this.temporaryActionBarMessages.remove(player.getUniqueId());
            this.temporaryActionBarUntil.remove(player.getUniqueId());
            Block target = player.getTargetBlockExact(5);
            Mifron.ShelfShopOffer offer = this.readShelfShopOffer(player, target);
            if (offer != null) {
               int catalogNumber = this.shelfShopCatalogNumber(offer.material());
               int sellPrice = this.applyShopDiscount(player, offer.price());
               int buyPrice = Math.max(0, Math.min(this.materialBuyPrice(offer.material()), sellPrice - 1));
               int stock = this.shelfShopStock(offer.material());
               Component prefix = Component.text(String.format("No.%03d ", Math.max(0, catalogNumber)), NamedTextColor.GRAY);
               player.sendActionBar(
                  prefix.append(
                     ((TranslatableComponent)Component.translatable(offer.material().translationKey())
                           .color(this.rarityTextColor(this.merchantRarity(offer.material()))))
                        .append(Component.text(
                           "  販売:" + this.formatNumber(sellPrice) + "MP / 買取:" + this.formatNumber(buyPrice) + "MP / 在庫:" + this.formatNumber(stock),
                           stock > 0 ? NamedTextColor.GOLD : NamedTextColor.RED
                        ))
                  )
               );
            }
         }
      }
   }

   private void showTemporaryActionBar(Player player, String message) {
      this.temporaryActionBarMessages.put(player.getUniqueId(), message);
      this.temporaryActionBarUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
      player.sendActionBar(Component.text(message, NamedTextColor.RED));
   }

   private Mifron.ShelfShopOffer readShelfShopOffer(Player player, Block block) {
      if (block == null || !this.isShelf(block.getType()) || !this.isShelfShop(block)) {
         return null;
      }

      List<Material> configuredMaterials = this.shelfShopRandomOffers(block);
      if (configuredMaterials.isEmpty()) {
         return null;
      }

      Material configuredMaterial = this.materialForShelfSlot(configuredMaterials, this.selectedShelfSlot(player, block));
      if (configuredMaterial == null || configuredMaterial == Material.AIR || !this.isPricedShopItem(configuredMaterial)) {
         return null;
      }

      int price = this.materialPrice(configuredMaterial);
      return price <= 0 ? null : new Mifron.ShelfShopOffer(configuredMaterial, 1, price);
   }

   private List<Material> shelfShopRandomOffers(Block block) {
      if (block == null || !this.isShelfShop(block)) {
         return List.of();
      }

      if ("custom".equals(this.shelfShopMode(block))) {
         return this.customShelfShopMaterials(block);
      }

      int order = this.data.getInt(this.shelfShopPath(block) + ".order", 0);
      if (order <= 0) {
         return List.of();
      }

      List<Material> catalog = this.shelfShopCatalogMaterials();
      int start = (order - 1) * SHELF_SHOP_OFFER_SLOTS;
      return start >= catalog.size() ? List.of() : catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS));
   }

   private String shelfShopMode(Block block) {
      if (block == null) {
         return "sequential";
      }
      String mode = this.data.getString(this.shelfShopPath(block) + ".mode", "sequential");
      return "custom".equalsIgnoreCase(mode) ? "custom" : "sequential";
   }

   private void configureSequentialShelfShop(Block block) {
      if (block == null) {
         return;
      }
      this.slotMachineManager.unregisterMachine(block);
      String path = this.shelfShopPath(block);
      int order = "sequential".equals(this.shelfShopMode(block)) ? this.data.getInt(path + ".order", 0) : 0;
      if (order <= 0) {
         order = this.nextSequentialShelfOrder();
      }
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "sequential");
      this.data.set(path + ".order", order);
      this.data.set(path + ".custom", null);
      this.clearShelfShopRandomOffer(block);
      this.displayShelfShopOffers(block, this.shelfShopRandomOffers(block));
      this.queueDataSave();
   }

   private int nextSequentialShelfOrder() {
      int nextOrder = 1;
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops != null) {
         for (String worldId : shops.getKeys(false)) {
            ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
            if (worldShops == null) {
               continue;
            }
            for (String coordinates : worldShops.getKeys(false)) {
               String base = coordinates;
               String mode = worldShops.getString(base + ".mode", "sequential");
               if (!"custom".equalsIgnoreCase(mode) && worldShops.getBoolean(base + ".enabled", worldShops.getBoolean(base, false))) {
                  nextOrder = Math.max(nextOrder, worldShops.getInt(base + ".order", 0) + 1);
               }
            }
         }
      }
      return nextOrder;
   }

   private List<Material> customShelfShopMaterials(Block block) {
      List<Material> materials = new ArrayList<>();
      String base = this.shelfShopPath(block) + ".custom";
      for (int slot = 0; slot < SHELF_SHOP_OFFER_SLOTS; slot++) {
         String raw = this.data.getString(base + "." + slot, "");
         Material material = raw.isBlank() ? Material.AIR : Material.matchMaterial(raw);
         materials.add(material == null ? Material.AIR : material);
      }
      return materials;
   }

   private void assignCustomShelfShopSlot(Block block, int selectedSlot, Material material) {
      if (block == null || material == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) {
         return;
      }
      this.slotMachineManager.unregisterMachine(block);
      String path = this.shelfShopPath(block);
      if (!"custom".equals(this.shelfShopMode(block))) {
         this.data.set(path + ".custom", null);
      }
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "custom");
      this.data.set(path + ".order", null);
      this.data.set(path + ".custom." + selectedSlot, material.name());
      this.clearShelfShopRandomOffer(block);
      this.displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.queueDataSave();
   }

   private void clearCustomShelfShopSlot(Block block, int selectedSlot) {
      if (block == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) {
         return;
      }
      this.data.set(this.shelfShopPath(block) + ".custom." + selectedSlot, null);
      this.displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.queueDataSave();
   }

   private List<Material> shelfShopCatalogMaterials() {
      if (!this.shelfShopCatalogReady) {
         this.rebuildShelfShopCatalog();
      }
      return this.shelfShopCatalog;
   }

   private void rebuildShelfShopCatalog() {
      List<Material> materials = new ArrayList<>();
      for (Material material : Material.values()) {
         if (this.isRandomShopItem(material)) {
            materials.add(material);
         }
      }

      // Keep catalog numbers stable and human-readable: category first, then related
      // material families, then the vanilla material id as a deterministic tie-breaker.
      materials.sort((a, b) -> {
         int category = Integer.compare(this.shelfShopCategoryRank(a), this.shelfShopCategoryRank(b));
         if (category != 0) {
            return category;
         }
         int family = this.shelfShopFamilyKey(a).compareTo(this.shelfShopFamilyKey(b));
         return family != 0 ? family : a.name().compareTo(b.name());
      });

      Map<Material, Integer> catalogNumbers = new HashMap<>();
      for (int index = 0; index < materials.size(); index++) {
         catalogNumbers.put(materials.get(index), index + 1);
      }
      this.shelfShopCatalog = List.copyOf(materials);
      this.shelfShopCatalogNumbers = Map.copyOf(catalogNumbers);
      this.shelfShopCatalogReady = true;
      this.getLogger().info("Cached " + materials.size() + " shelf shop catalog entries.");
   }

   private int shelfShopCategoryRank(Material material) {
      String name = material.name();
      // Equipment must be classified before material names such as IRON or STONE.
      // The old broad contains() checks put iron swords and redstone parts among terrain.
      if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
         || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.equals("BOW") || name.equals("CROSSBOW")
         || name.equals("TRIDENT") || name.equals("MACE") || name.equals("SHIELD") || name.equals("FISHING_ROD")
         || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL") || name.equals("BRUSH")) {
         return 8;
      }
      if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
         || name.equals("ELYTRA") || name.contains("HORSE_ARMOR") || name.equals("TURTLE_HELMET")) {
         return 9;
      }
      if (name.contains("SMITHING_TEMPLATE") || name.contains("MUSIC_DISC") || name.contains("POTTERY_SHERD")
         || name.contains("HEAD") || name.contains("SKULL") || name.equals("TOTEM_OF_UNDYING") || name.equals("NETHER_STAR")
         || name.equals("HEART_OF_THE_SEA") || name.equals("CONDUIT") || name.equals("BEACON") || name.equals("DRAGON_EGG")
         || name.equals("ENCHANTED_GOLDEN_APPLE")) {
         return 11;
      }
      if (material.isEdible() || name.contains("SEEDS") || name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO")
         || name.equals("BEETROOT") || name.contains("MELON") || name.contains("PUMPKIN") || name.contains("COCOA")
         || name.contains("BERRIES") || name.contains("HONEY") || name.equals("SUGAR_CANE") || name.equals("EGG")) {
         return 6;
      }
      if (name.equals("ROTTEN_FLESH") || name.equals("BONE") || name.equals("STRING") || name.contains("SPIDER_EYE")
         || name.equals("GUNPOWDER") || name.contains("BLAZE") || name.contains("GHAST") || name.equals("ENDER_PEARL")
         || name.equals("MAGMA_CREAM") || name.equals("SLIME_BALL") || name.equals("PHANTOM_MEMBRANE")
         || name.equals("SHULKER_SHELL") || name.contains("POTION") || name.contains("FERMENTED") || name.equals("RABBIT_FOOT")
         || name.equals("DRAGON_BREATH") || name.equals("GLISTERING_MELON_SLICE") || name.equals("NETHER_WART")) {
         return 7;
      }
      if (name.contains("PISTON") || name.equals("OBSERVER") || name.equals("COMPARATOR") || name.equals("REPEATER")
         || name.contains("HOPPER") || name.contains("DISPENSER") || name.contains("DROPPER") || name.equals("CRAFTER")
         || name.contains("RAIL") || name.equals("LEVER") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")
         || name.contains("TRIPWIRE") || name.equals("DAYLIGHT_DETECTOR") || name.equals("TARGET") || name.equals("NOTE_BLOCK")
         || name.equals("REDSTONE_TORCH") || name.equals("REDSTONE_LAMP") || name.equals("TNT")) {
         return 5;
      }
      if (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT") || name.endsWith("_NUGGET")
         || name.equals("COAL") || name.equals("CHARCOAL") || name.equals("IRON") || name.equals("GOLD")
         || name.equals("COPPER") || name.equals("DIAMOND") || name.equals("EMERALD") || name.equals("REDSTONE")
         || name.equals("LAPIS_LAZULI") || name.equals("QUARTZ") || name.startsWith("AMETHYST_") || name.startsWith("NETHERITE_")
         || name.equals("ANCIENT_DEBRIS") || name.matches("(COAL|IRON|GOLD|COPPER|DIAMOND|EMERALD|REDSTONE|LAPIS|QUARTZ|AMETHYST|NETHERITE)_BLOCK")) {
         return 4;
      }
      if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX") || name.contains("BUNDLE")
         || name.contains("MINECART") || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.equals("SADDLE")
         || name.equals("LEAD") || name.equals("COMPASS") || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK")
         || name.equals("NAME_TAG") || name.contains("LANTERN") || name.endsWith("TORCH") || name.equals("LADDER")
         || name.equals("SCAFFOLDING") || name.equals("CRAFTING_TABLE") || name.contains("FURNACE") || name.equals("ANVIL")) {
         return 10;
      }

      if (material.isBlock()) {
         if (this.isShelfShopWoodFamily(name)) {
            return 1;
         }
         if (name.contains("WOOL") || name.contains("CARPET") || name.contains("CONCRETE") || name.contains("TERRACOTTA")
            || name.contains("GLASS") || name.contains("BANNER") || name.contains("CANDLE") || name.contains("CORAL")
            || name.contains("FLOWER") || name.contains("TULIP") || name.contains("ORCHID") || name.contains("POPPY")) {
            return 2;
         }
         if (name.equals("STONE") || name.equals("COBBLESTONE") || name.equals("DEEPSLATE") || name.equals("COBBLED_DEEPSLATE")
            || name.equals("TUFF") || name.equals("CALCITE") || name.equals("DRIPSTONE_BLOCK") || name.equals("DIRT")
            || name.equals("GRASS_BLOCK") || name.equals("PODZOL") || name.equals("MYCELIUM") || name.equals("SAND")
            || name.equals("RED_SAND") || name.equals("SANDSTONE") || name.equals("RED_SANDSTONE") || name.equals("GRAVEL")
            || name.equals("CLAY") || name.equals("MUD") || name.equals("PACKED_MUD") || name.equals("SNOW")
            || name.equals("SNOW_BLOCK") || name.equals("POWDER_SNOW") || name.equals("ICE") || name.equals("PACKED_ICE")
            || name.equals("BLUE_ICE") || name.equals("FROSTED_ICE") || name.equals("NETHERRACK") || name.equals("END_STONE") || name.equals("BASALT")
            || name.equals("BLACKSTONE")) {
            return 0;
         }
         return 3;
      }
      return 12;
   }

   private String shelfShopFamilyKey(Material material) {
      String name = material.name();
      for (String family : List.of("WOOL", "CARPET", "CONCRETE_POWDER", "CONCRETE", "TERRACOTTA", "GLASS_PANE", "GLASS", "BANNER", "BED", "CANDLE")) {
         if (name.endsWith("_" + family) || name.equals(family)) {
            return family + ":" + name;
         }
      }
      for (String wood : List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED")) {
         if (name.startsWith(wood + "_")) {
            return "WOOD:" + String.format("%02d", List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED").indexOf(wood)) + ":" + name;
         }
      }
      List<String> tools = List.of("SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "SPEAR");
      for (int i = 0; i < tools.size(); i++) {
         if (name.endsWith("_" + tools.get(i))) {
            return "TOOL:" + String.format("%02d:%02d", i, this.shelfShopEquipmentTier(name)) + ":" + name;
         }
      }
      List<String> armor = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "HORSE_ARMOR");
      for (int i = 0; i < armor.size(); i++) {
         if (name.endsWith("_" + armor.get(i))) {
            return "ARMOR:" + String.format("%02d:%02d", i, this.shelfShopEquipmentTier(name)) + ":" + name;
         }
      }
      List<String> resources = List.of("COAL", "IRON", "COPPER", "GOLD", "REDSTONE", "LAPIS", "DIAMOND", "EMERALD", "QUARTZ", "AMETHYST", "NETHERITE");
      for (int i = 0; i < resources.size(); i++) {
         if (name.contains(resources.get(i))) {
            return "RESOURCE:" + String.format("%02d", i) + ":" + name;
         }
      }
      List<String> buildingFamilies = List.of(
         "STONE_BRICK", "COBBLESTONE", "STONE", "MOSSY_COBBLESTONE", "DEEPSLATE_BRICK", "DEEPSLATE_TILE",
         "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "BLACKSTONE", "POLISHED_BLACKSTONE", "BRICK", "MUD_BRICK",
         "SANDSTONE", "RED_SANDSTONE", "PRISMARINE", "NETHER_BRICK", "PURPUR", "QUARTZ", "TUFF"
      );
      for (int i = 0; i < buildingFamilies.size(); i++) {
         String family = buildingFamilies.get(i);
         if (name.equals(family) || name.startsWith(family + "_")) {
            return "BUILD:" + String.format("%02d", i) + ":" + name;
         }
      }
      return name;
   }

   private boolean isShelfShopWoodFamily(String name) {
      return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
         || name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING") || name.contains("BAMBOO")
         || name.startsWith("OAK_") || name.startsWith("SPRUCE_") || name.startsWith("BIRCH_") || name.startsWith("JUNGLE_")
         || name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_") || name.startsWith("MANGROVE_")
         || name.startsWith("CHERRY_") || name.startsWith("PALE_OAK_") || name.startsWith("CRIMSON_") || name.startsWith("WARPED_");
   }

   private int shelfShopEquipmentTier(String name) {
      List<String> tiers = List.of("WOODEN_", "STONE_", "COPPER_", "IRON_", "GOLDEN_", "DIAMOND_", "NETHERITE_");
      for (int i = 0; i < tiers.size(); i++) {
         if (name.startsWith(tiers.get(i))) {
            return i;
         }
      }
      return tiers.size();
   }

   private void renumberSequentialShelfShops() {
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) {
         return;
      }

      List<String> paths = new ArrayList<>();
      for (String worldId : shops.getKeys(false)) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) {
            continue;
         }
         for (String coordinates : worldShops.getKeys(false)) {
            String relative = worldId + "." + coordinates;
            boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
            String mode = worldShops.getString(coordinates + ".mode", "sequential");
            boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
            if (enabled && !"custom".equalsIgnoreCase(mode) && !slotMachine) {
               paths.add(relative);
            }
         }
      }

      paths.sort((a, b) -> {
         int ao = this.data.getInt("shelf-shops." + a + ".order", Integer.MAX_VALUE);
         int bo = this.data.getInt("shelf-shops." + b + ".order", Integer.MAX_VALUE);
         int order = Integer.compare(ao, bo);
         return order != 0 ? order : a.compareTo(b);
      });

      for (int i = 0; i < paths.size(); i++) {
         this.data.set("shelf-shops." + paths.get(i) + ".order", i + 1);
      }
      this.queueDataSave();
      this.syncShelfShopDisplays();
   }

   private int shelfShopCatalogNumber(Material material) {
      if (material == null) {
         return 0;
      }
      if (!this.shelfShopCatalogReady) {
         this.rebuildShelfShopCatalog();
      }
      return this.shelfShopCatalogNumbers.getOrDefault(material, 0);
   }

   private String shelfShopStockPath(Material material) {
      return "shelf-shop-stock." + material.name();
   }

   private int shelfShopStock(Material material) {
      return material == null ? 0 : Math.max(0, this.data.getInt(this.shelfShopStockPath(material), 0));
   }

   private void changeShelfShopStock(Material material, int delta) {
      if (material == null || delta == 0) {
         return;
      }
      int next = Math.max(0, this.shelfShopStock(material) + delta);
      this.data.set(this.shelfShopStockPath(material), next);
      // The surrounding purchase/sell transaction persists the complete data update.
      // Avoiding a second synchronous YAML write keeps rapid shelf-shop selling responsive.
   }

   private void recordAcquiredItem(Player player, Material material) {
      // Shelf shops now use a fixed global catalog; acquiring an item no longer unlocks shop entries.
   }

   private void recordInventoryAcquisitions(Player player) {
      // Kept as a compatibility no-op. Global stock changes only through shelf-shop buy/sell transactions.
   }

   void recordShelfShopAcquisition(Player player, Material material) {
      // No-op: catalog availability is independent of player acquisition history.
   }

   private int clearAllShelfShopRegistrations() {
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) {
         this.data.set("shelf-shop-offers", null);
         this.saveData();
         return 0;
      }

      List<String> removablePaths = new ArrayList<>();
      for (String worldId : new ArrayList<>(shops.getKeys(false))) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) {
            continue;
         }

         World world = this.worldFromId(worldId);
         for (String coordinates : new ArrayList<>(worldShops.getKeys(false))) {
            Block block = world == null ? null : this.blockFromCoordinates(world, coordinates);
            boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
            if (!slotMachine && block != null && this.slotMachineManager != null) {
               slotMachine = this.slotMachineManager.isMachine(block);
            }
            if (slotMachine) {
               // Slot machines may share the same storage namespace. They are not
               // shelf shops and must survive a bulk shelf-shop clear.
               continue;
            }

            removablePaths.add(worldId + "." + coordinates);
            if (block != null) {
               try {
                  this.clearShelfShopDisplay(block);
               } catch (Throwable e) {
                  this.getLogger().warning("棚ショップ表示の解除に失敗しました: " + worldId + " " + coordinates + " (metadata will still be cleared)");
               }
            }
         }
      }

      // Do not call setShelfShop() here. It saves and renumbers while the same
      // ConfigurationSection is being traversed, which can corrupt the YAML
      // section or crash the server on a large/partially stale registration set.
      // Remove all entries first, then persist and refresh displays exactly once.
      for (String path : removablePaths) {
         this.data.set("shelf-shops." + path, null);
         this.data.set("shop-owners." + path, null);
         this.data.set("shelf-shop-offers." + path, null);
      }
      this.saveData();
      this.syncShelfShopDisplays();
      return removablePaths.size();
   }

   private void handleShelfShopCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.shop.admin") && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else if (args.length >= 2 && ("clearall".equalsIgnoreCase(args[1]) || "removeall".equalsIgnoreCase(args[1]) || "disableall".equalsIgnoreCase(args[1]) || "clear".equalsIgnoreCase(args[1]))) {
         int registered = this.clearAllShelfShopRegistrations();
         sender.sendMessage("§a棚ショップを全解除しました: " + registered + "件");
         sender.sendMessage("§7スロットマシンの登録と棚ブロック自体は維持されます。");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.0F);
         }
      } else if (args.length >= 2 && ("reorder".equalsIgnoreCase(args[1]) || "renumber".equalsIgnoreCase(args[1]) || "resetorder".equalsIgnoreCase(args[1]))) {
         ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
         int before = 0;
         if (shops != null) {
            for (String worldId : shops.getKeys(false)) {
               ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
               if (worldShops == null) continue;
               for (String coordinates : worldShops.getKeys(false)) {
                  boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
                  String mode = worldShops.getString(coordinates + ".mode", "sequential");
                  boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
                  if (enabled && !"custom".equalsIgnoreCase(mode) && !slotMachine) before++;
               }
            }
         }
         this.renumberSequentialShelfShops();
         sender.sendMessage("§a順番配置の棚をNo.001から振り直しました: " + before + "棚");
         sender.sendMessage("§7商品カタログもジャンル別の固定順に再構成されています。");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.2F);
         }
      } else if (args.length >= 2 && ("reset".equalsIgnoreCase(args[1]) || "resetstock".equalsIgnoreCase(args[1]))) {
         ConfigurationSection stock = this.data.getConfigurationSection("shelf-shop-stock");
         int stockedTypes = stock == null ? 0 : stock.getKeys(false).size();
         this.data.set("shelf-shop-stock", null);
         this.data.set("shelf-shop-unlocked", null);
         this.saveData();
         this.syncShelfShopDisplays();
         sender.sendMessage("§a棚ショップの共有在庫を0にリセットしました: " + stockedTypes + "種類");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.8F);
         }
      } else {
         sender.sendMessage("§e/mf shelfshop clearall §7- 登録済みの棚ショップをすべて解除する");
         sender.sendMessage("§e/mf shelfshop reorder §7- 順番配置の棚番号を1から振り直す");
         sender.sendMessage("§e/mf shelfshop resetstock §7- 共有在庫を0にする");
      }
   }

   private Material materialForShelfSlot(List<Material> materials, int selectedSlot) {
      if (materials.isEmpty()) {
         return null;
      } else {
         return selectedSlot >= 0 && selectedSlot < materials.size() ? materials.get(selectedSlot) : materials.get(0);
      }
   }

   private void setShelfShopRandomOffers(Block block, List<Material> materials) {
      String path = this.shelfShopOfferPath(block);
      this.data.set(path + ".type", ShopWandType.SHELF.key());
      this.data.set(path + ".material", materials.get(0).name());
      this.data.set(path + ".materials", materials.stream().map(Enum::name).toList());
      this.data.set(path + ".created-at", System.currentTimeMillis());
      this.displayShelfShopOffers(block, materials);
      this.queueDataSave();
   }

   private void clearShelfShopRandomOffer(Block block) {
      this.data.set(this.shelfShopOfferPath(block), null);
   }

   private void syncShelfShopDisplays() {
      ConfigurationSection worlds = this.data.getConfigurationSection("shelf-shops");
      if (worlds != null) {
         int synced = 0;

         for (String worldId : worlds.getKeys(false)) {
            World world = this.worldFromId(worldId);
            if (world != null) {
               ConfigurationSection offers = worlds.getConfigurationSection(worldId);
               if (offers != null) {
                  for (String coordinates : offers.getKeys(false)) {
                     Block block = this.blockFromCoordinates(world, coordinates);
                     if (block != null && this.isShelfShop(block)) {
                        List<Material> materials = this.shelfShopRandomOffers(block);
                        if (materials.isEmpty()) {
                           this.clearShelfShopDisplay(block);
                        } else if (this.displayShelfShopOffers(block, materials)) {
                           synced++;
                        }
                     }
                  }
               }
            }
         }

         if (synced > 0) {
            this.getLogger().info("Synced " + synced + " shelf shop display items.");
         }
      }
   }

   private World worldFromId(String worldId) {
      try {
         return Bukkit.getWorld(UUID.fromString(worldId));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private Block blockFromCoordinates(World world, String coordinates) {
      String[] parts = coordinates.split("_", 3);
      if (parts.length != 3) {
         return null;
      }

      try {
         return world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private boolean displayShelfShopOffers(Block block, List<Material> materials) {
      if (block != null && materials != null && block.getState() instanceof Shelf shelfState) {
         Inventory inventory = shelfState.getInventory();
         inventory.clear();
         int slots = Math.min(SHELF_SHOP_OFFER_SLOTS, Math.min(inventory.getSize(), materials.size()));

         for (int slot = 0; slot < slots; slot++) {
            Material material = materials.get(slot);
            if (material != null && material != Material.AIR) {
               // This is a product sample only. Global stock is stored separately and never represented by stack size.
               inventory.setItem(slot, new ItemStack(material, 1));
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private void clearShelfShopDisplay(Block block) {
      if (block != null && block.getState() instanceof Shelf shelfState) {
         shelfState.getInventory().clear();
      }
   }

   private String shelfShopOfferPath(Block block) {
      return "shelf-shop-offers." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private int selectedShelfSlot(Player player, Block shelf) {
      if (player != null && shelf != null) {
         RayTraceResult result = player.rayTraceBlocks(5.0);
         if (result != null && result.getHitBlock() != null && result.getHitBlock().equals(shelf)) {
            double local = this.shelfSlotAxisPosition(shelf, result.getHitPosition().getX(), result.getHitPosition().getZ());
            if (local < 0.3333333333333333) {
               return 2;
            } else {
               return local < 0.6666666666666666 ? 1 : 0;
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private double shelfSlotAxisPosition(Block shelf, double hitX, double hitZ) {
      BlockFace facing = this.shelfFacing(shelf);

      double local = switch (facing) {
         case NORTH -> hitX - shelf.getX();
         case SOUTH -> 1.0 - (hitX - shelf.getX());
         case EAST -> hitZ - shelf.getZ();
         case WEST -> 1.0 - (hitZ - shelf.getZ());
         default -> hitX - shelf.getX();
      };
      return Math.max(0.0, Math.min(0.999999, local));
   }

   private BlockFace shelfFacing(Block shelf) {
      return shelf.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.NORTH;
   }

   private Material shelfShopMaterial(Block shelf, int selectedSlot) {
      if (shelf.getState() instanceof Shelf shelfState) {
         ItemStack[] contents = shelfState.getSnapshotInventory().getContents();
         if (selectedSlot >= 0 && selectedSlot < contents.length) {
            ItemStack selected = contents[selectedSlot];
            if (selected != null && selected.getType() != Material.AIR) {
               return selected.getType();
            }
         }

         for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
               return item.getType();
            }
         }
      }

      Location center = shelf.getLocation().add(0.5, 0.5, 0.5);
      return shelf.getWorld()
         .getNearbyEntities(center, 1.25, 1.25, 1.25)
         .stream()
         .filter(ItemFrame.class::isInstance)
         .map(ItemFrame.class::cast)
         .<ItemStack>map(ItemFrame::getItem)
         .filter(itemx -> itemx != null && itemx.getType() != Material.AIR)
         .<Material>map(ItemStack::getType)
         .findFirst()
         .orElse(null);
   }

   private boolean isShelf(Material material) {
      String name = material.name();
      return name.endsWith("_SHELF") || name.equals("CHISELED_BOOKSHELF");
   }

   boolean isShelfShop(Block block) {
      if (block == null) {
         return false;
      }

      // Slot machines also use shelf blocks, but they are never shelf shops.
      // This prevents slot shelves from receiving shelf-shop numbering,
      // stock handling, action bars, or display synchronisation.
      if (this.slotMachineManager != null && this.slotMachineManager.isMachine(block)) {
         return false;
      }

      String path = this.shelfShopPath(block);
      return this.data.getBoolean(path, false) || this.data.getBoolean(path + ".enabled", false);
   }

   boolean setShelfShop(Block block, boolean enabled) {
      String path = this.shelfShopPath(block);
      boolean existed = this.isShelfShop(block);
      if (enabled) {
         this.configureSequentialShelfShop(block);
         return existed;
      }

      this.data.set(path, null);
      this.clearShelfShopDisplay(block);
      this.clearShopOwner(block);
      this.clearShelfShopRandomOffer(block);
      // Removing a sequential shelf must also compact the remaining order values.
      // Otherwise the next shelf can inherit a stale high number from deleted data.
      this.renumberSequentialShelfShops();
      return existed;
   }

   private String shelfShopPath(Block block) {
      return "shelf-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private boolean canCreateShop(Player player) {
      return player.hasPermission("mifron.shop.admin") || player.hasPermission("mifron.admin");
   }

   private boolean canManageShop(Player player, Block block) {
      if (this.canCreateShop(player)) {
         return true;
      }

      String owner = this.data.getString(this.shopOwnerPath(block), "");
      return owner.equals(player.getUniqueId().toString());
   }

   private void setShopOwner(Block block, UUID owner) {
      this.data.set(this.shopOwnerPath(block), owner.toString());
      this.queueDataSave();
   }

   private void clearShopOwner(Block block) {
      this.data.set(this.shopOwnerPath(block), null);
   }

   private String shopOwnerPath(Block block) {
      return "shop-owners." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   boolean isBarrelShop(Block block) {
      return block != null && block.getType() == Material.BARREL && this.data.getBoolean(this.barrelShopPath(block), false);
   }

   boolean isShopBlock(Block block) {
      return this.isShelfShop(block) || this.isBarrelShop(block);
   }

   boolean isAuctionFrame(Entity entity) {
      return this.auctionFeature.isAuctionFrame(entity);
   }

   boolean isAuctionInteractionItem(ItemStack item) {
      return this.auctionFeature.isAuctionInteractionItem(item);
   }

   void recordQuestProgress(Player player, String progressKey, int amount) {
      this.questService.addProgress(player, progressKey, amount);
   }

   void recordSpecialQuestProgress(UUID uuid, String progressKey, int amount) {
      this.questService.addSpecialProgress(uuid, progressKey, amount);
   }

   void completeSpecialQuestProgress(UUID uuid, String progressKey) {
      this.questService.completeSpecialProgress(uuid, progressKey);
   }

   private void recordFarmingSubmission(Player player, Material material) {
      if (material != null && Set.of(
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.NETHER_WART,
            Material.MELON_SLICE,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.CACTUS,
            Material.SWEET_BERRIES,
            Material.GLOW_BERRIES,
            Material.COCOA_BEANS
         ).contains(material)) {
         this.recordQuestProgress(player, "farming_submission", 1);
      }
   }

   boolean setBarrelShop(Block block, boolean enabled) {
      String path = this.barrelShopPath(block);
      boolean existed = this.data.getBoolean(path, false);
      this.data.set(path, enabled ? true : null);
      if (!enabled) {
         this.clearShopOwner(block);
         this.clearBarrelShopMeta(block);
      }

      this.queueDataSave();
      return existed;
   }

   private String barrelShopPath(Block block) {
      return "barrel-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private void populateBarrelShop(Barrel barrel) {
      List<Mifron.MerchantOffer> pool = this.barrelShopOffers();
      this.populateBarrelShop(barrel, pool);
   }

   private void populateBarrelShop(Barrel barrel, List<Mifron.MerchantOffer> pool) {
      Set<Material> used = new HashSet<>();
      Inventory inventory = barrel.getInventory();
      inventory.clear();
      if (pool.isEmpty()) {
         this.getLogger().warning("Barrel shop pool is empty. No offers were generated.");
      } else {
         int offerSlots = Math.min(inventory.getSize(), Math.max(1, this.getConfig().getInt("barrel-shop.offer-slots", 27)));
         int bargainSlots = Math.max(0, Math.min(offerSlots, this.getConfig().getInt("barrel-shop.bargain-slots", 3)));

         for (int slot = 0; slot < offerSlots; slot++) {
            Mifron.MerchantOffer offer = this.randomBarrelOffer(pool, used, slot < bargainSlots);
            inventory.setItem(slot, this.createBarrelOfferItem(offer));
         }
      }
   }

   private void setBarrelShopMeta(Block block) {
      String path = this.barrelShopMetaPath(block);
      this.data.set(path + ".type", ShopWandType.BARREL.key());
      this.data.set(path + ".category", null);
      this.data.set(path + ".created-at", System.currentTimeMillis());
      this.queueDataSave();
   }

   private void clearBarrelShopMeta(Block block) {
      this.data.set(this.barrelShopMetaPath(block), null);
   }

   private String barrelShopMetaPath(Block block) {
      return "barrel-shop-meta." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private void handleJumpPadWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block block = event.getClickedBlock();
      if (!player.hasPermission("mifron.admin")) {
         player.sendMessage("§c権限がありません。");
         event.setCancelled(true);
      } else if (block == null) {
         player.sendMessage("§cブロックをクリックしてください。");
         event.setCancelled(true);
      } else if (event.getAction().isRightClick()) {
         int verticalPower = this.utilityItemsFeature.getJumpPadVerticalPower(event.getItem());
         int horizontalPower = this.utilityItemsFeature.getJumpPadHorizontalPower(event.getItem());
         this.setJumpPad(block, verticalPower, horizontalPower);
         player.sendMessage("§aジャンプパッドを設定しました。縦: " + verticalPower + " / 横: " + horizontalPower);
         event.setCancelled(true);
      } else {
         if (event.getAction().isLeftClick()) {
            boolean existed = this.setJumpPad(block, false);
            player.sendMessage((existed ? "§a" : "§e") + (existed ? "ジャンプパッドを解除しました。" : "このブロックはジャンプパッドではありません。"));
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onPlayerMove(PlayerMoveEvent event) {
      Location to = event.getTo();
      Location from = event.getFrom();
      if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
         Player player = event.getPlayer();
         Block block = to.clone().subtract(0.0, 1.0, 0.0).getBlock();
         Mifron.JumpPadPower power = this.jumpPadPower(block);
         if (power != null) {
            long now = System.currentTimeMillis();
            long last = this.lastJumpPadUse.getOrDefault(player.getUniqueId(), 0L);
            if (now - last >= 650L) {
               this.lastJumpPadUse.put(player.getUniqueId(), now);
               this.jumpPadFallProtectionUntil.put(player.getUniqueId(), now + 60000L);
               player.setFallDistance(0.0F);
               double horizontalVelocity = this.jumpPadHorizontalVelocity(power.horizontal());
               double verticalVelocity = this.jumpPadVerticalVelocity(power.vertical());
               Vector direction = player.getLocation().getDirection().setY(0.0);
               if (direction.lengthSquared() > 0.0) {
                  direction.normalize().multiply(horizontalVelocity);
               }

               player.setVelocity(direction.setY(verticalVelocity));
               player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, Math.min(2.0F, 1.0F + power.vertical() * 0.01F));
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onJumpPadFallDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player && event.getCause() == DamageCause.FALL) {
         UUID playerId = player.getUniqueId();
         Long until = this.jumpPadFallProtectionUntil.get(playerId);
         if (until != null) {
            long now = System.currentTimeMillis();
            if (now > until) {
               this.jumpPadFallProtectionUntil.remove(playerId);
            } else {
               this.jumpPadFallProtectionUntil.remove(playerId);
               player.setFallDistance(0.0F);
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onJumpPadBlockBreak(BlockBreakEvent event) {
      if (this.jumpPadPower(event.getBlock()) != null) {
         this.setJumpPad(event.getBlock(), false);
      }
   }

   private void setJumpPad(Block block, int verticalPower, int horizontalPower) {
      String path = this.jumpPadPath(block);
      this.data.set(path + ".verticalPower", this.clampJumpPadPower(verticalPower));
      this.data.set(path + ".horizontalPower", this.clampJumpPadPower(horizontalPower));
      this.data.set(path + ".material", block.getType().name());
      this.saveData();
   }

   private boolean setJumpPad(Block block, boolean enabled) {
      String path = this.jumpPadPath(block);
      boolean existed = this.jumpPadPower(block) != null;
      if (enabled) {
         this.setJumpPad(block, 5, 5);
         return existed;
      } else {
         this.data.set(path, null);
         this.saveData();
         return existed;
      }
   }

   private Mifron.JumpPadPower jumpPadPower(Block block) {
      if (block == null) {
         return null;
      }

      String path = this.jumpPadPath(block);
      Object raw = this.data.get(path);
      if (raw instanceof Boolean value) {
         if (value) {
            this.setJumpPad(block, 5, 5);
            return new Mifron.JumpPadPower(5, 5);
         } else {
            return null;
         }
      } else if (raw instanceof Number value) {
         int power = this.oldJumpPadPowerToNewPower(value.intValue());
         this.setJumpPad(block, power, power);
         return new Mifron.JumpPadPower(power, power);
      } else {
         ConfigurationSection section = this.data.getConfigurationSection(path);
         if (section == null) {
            return null;
         } else {
            String material = section.getString("material", "");
            if (!block.getType().name().equals(material)) {
               return null;
            } else if (section.contains("power")) {
               int power = this.oldJumpPadPowerToNewPower(section.getInt("power", 3));
               this.setJumpPad(block, power, power);
               return new Mifron.JumpPadPower(power, power);
            } else {
               return new Mifron.JumpPadPower(
                  this.clampJumpPadPower(section.getInt("verticalPower", 5)), this.clampJumpPadPower(section.getInt("horizontalPower", 5))
               );
            }
         }
      }
   }

   private String jumpPadPath(Block block) {
      return "jump-pads." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   private void migrateBarrelShopOfferSlots() {
      String path = "barrel-shop.offer-slots";
      if (this.getConfig().getInt(path, 27) == 18) {
         this.getConfig().set(path, 27);
         this.saveConfig();
      }
   }

   private int clampJumpPadPower(int power) {
      return Math.max(1, Math.min(100, power));
   }

   private int oldJumpPadPowerToNewPower(int power) {
      return this.clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2);
   }

   private double jumpPadHorizontalVelocity(int power) {
      int safePower = this.clampJumpPadPower(power);
      int basePower = Math.min(10, safePower);
      int extraPower = Math.max(0, safePower - 10);
      return 0.45 + basePower * 0.18 + extraPower * 0.04;
   }

   private double jumpPadVerticalVelocity(int power) {
      int safePower = this.clampJumpPadPower(power);
      int basePower = Math.min(10, safePower);
      int extraPower = Math.max(0, safePower - 10);
      return 0.75 + basePower * 0.15 + extraPower * 0.05;
   }

   private Mifron.MerchantOffer randomBarrelOffer(List<Mifron.MerchantOffer> pool, Set<Material> used, boolean bargain) {
      List<Mifron.MerchantOffer> candidates = pool.stream()
         .filter(offerx -> !used.contains(offerx.material()))
         .filter(offerx -> !bargain || this.isMerchantRarityAtLeast(this.merchantRarity(offerx.material()), "rare"))
         .toList();
      if (candidates.isEmpty()) {
         candidates = pool.stream().filter(offerx -> !used.contains(offerx.material())).toList();
      }

      if (candidates.isEmpty()) {
         used.clear();
         candidates = pool;
      }

      Mifron.MerchantOffer offer = this.randomWeightedBarrelOffer(candidates);
      used.add(offer.material());
      return new Mifron.MerchantOffer(offer.material(), offer.amount(), bargain ? "bargain" : "junk", offer.price());
   }

   private Mifron.MerchantOffer randomWeightedBarrelOffer(List<Mifron.MerchantOffer> candidates) {
      int totalWeight = candidates.stream().mapToInt(offerx -> Math.max(1, this.barrelShopWeight(offerx.material()))).sum();
      int selected = this.random.nextInt(Math.max(1, totalWeight));

      for (Mifron.MerchantOffer offer : candidates) {
         selected -= Math.max(1, this.barrelShopWeight(offer.material()));
         if (selected < 0) {
            return offer;
         }
      }

      return candidates.get(this.random.nextInt(candidates.size()));
   }

   private ItemStack createBarrelOfferItem(Mifron.MerchantOffer offer) {
      ItemStack item = new ItemStack(offer.material());
      boolean equipment = item.getItemMeta() instanceof Damageable && offer.material().getMaxDurability() > 0;
      ItemMeta meta = item.getItemMeta();
      boolean damaged = equipment && this.random.nextInt(10) != 0;
      if (damaged && meta instanceof Damageable damageable) {
         int damagePercent = 20 + this.random.nextInt(61);
         int damage = Math.max(1, offer.material().getMaxDurability() * damagePercent / 100);
         damageable.setDamage(Math.min(offer.material().getMaxDurability() - 1, damage));
      }

      int price = this.barrelOfferPrice(offer);
      if (damaged) {
         int discountPercent = 10 + this.random.nextInt(61);
         price = this.discountedPrice(price, discountPercent);
      }

      meta.lore(
         List.of(
            (TextComponent)Component.text("枠: ", NamedTextColor.GRAY)
               .append(Component.text(this.barrelTierName(offer.rarity()), this.barrelTierColor(offer.rarity()))),
            Component.text("価格: " + this.formatNumber(price) + "MP", NamedTextColor.GOLD),
            Component.text("クリックで購入", NamedTextColor.GRAY)
         )
      );
      meta.getPersistentDataContainer().set(this.barrelOfferPriceKey, PersistentDataType.INTEGER, price);
      meta.getPersistentDataContainer().set(this.barrelOfferRarityKey, PersistentDataType.STRING, offer.rarity());
      item.setItemMeta(meta);
      return item;
   }

   private int barrelOfferPrice(Mifron.MerchantOffer offer) {
      int basePrice = Math.max(1, offer.price());
      return (int)Math.max(1L, Math.min(2000000000L, (long)basePrice * (75 + this.random.nextInt(51)) / 100L));
   }

   private String barrelTierName(String tier) {
      return "bargain".equals(tier) ? "掘り出し物" : "ジャンク";
   }

   private NamedTextColor barrelTierColor(String tier) {
      return "bargain".equals(tier) ? NamedTextColor.AQUA : NamedTextColor.GRAY;
   }

   private int discountedPrice(int price, int discountPercent) {
      return Math.max(1, (int)((long)Math.max(1, price) * (100 - discountPercent) / 100L));
   }

   private void buyBarrelOffer(Player player, ItemStack displayed, int selectedSlot, Inventory inventory) {
      if (displayed != null && displayed.getType() != Material.AIR && displayed.hasItemMeta()) {
         Integer basePrice = (Integer)displayed.getItemMeta().getPersistentDataContainer().get(this.barrelOfferPriceKey, PersistentDataType.INTEGER);
         if (basePrice != null && basePrice > 0) {
            int price = this.applyShopDiscount(player, basePrice);
            if (this.getEmeralds(player.getUniqueId()) < price) {
               this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(price) + "MP");
            } else {
               ItemStack purchased = displayed.clone();
               purchased.setAmount(1);
               ItemMeta meta = purchased.getItemMeta();
               meta.lore(null);
               meta.getPersistentDataContainer().remove(this.barrelOfferPriceKey);
               meta.getPersistentDataContainer().remove(this.barrelOfferRarityKey);
               purchased.setItemMeta(meta);
               if (this.inventorySpaceFor(player, purchased.getType()) < 1) {
                  this.showTemporaryActionBar(player, "インベントリに空きがありません。");
               } else if (!this.withdrawEmeralds(player.getUniqueId(), price)) {
                  this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(price) + "MP");
               } else {
                  Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{purchased});
                  if (!leftovers.isEmpty()) {
                     this.depositEmeralds(player.getUniqueId(), price);
                     this.showTemporaryActionBar(player, "インベントリに空きがありません。");
                  } else {
                     this.addPlayerStat(player.getUniqueId(), "total-trades", 1);
                     this.playPurchaseSound(player);
                     this.sendItemMessage(player, NamedTextColor.GREEN, "購入しました: ", purchased.getType(), " (" + this.formatNumber(price) + "MP)");
                     List<Mifron.MerchantOffer> pool = this.barrelShopOffers();
                     if (!pool.isEmpty() && selectedSlot >= 0 && selectedSlot < inventory.getSize()) {
                        Set<Material> used = new HashSet<>();

                        for (int slot = 0; slot < inventory.getSize(); slot++) {
                           if (slot != selectedSlot) {
                              ItemStack item = inventory.getItem(slot);
                              if (item != null && item.getType() != Material.AIR) {
                                 used.add(item.getType());
                              }
                           }
                        }

                        int bargainSlots = Math.max(0, this.getConfig().getInt("barrel-shop.bargain-slots", 3));
                        inventory.setItem(selectedSlot, this.createBarrelOfferItem(this.randomBarrelOffer(pool, used, selectedSlot < bargainSlots)));
                     }
                  }
               }
            }
         }
      }
   }

   private void normalizeMerchants() {
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
               if (!"survival".equalsIgnoreCase(world.getName())) {
                  entity.remove();
               } else {
                  villager.setAI(true);
                  villager.setInvulnerable(false);
               }
            }
         }
      }
   }

   private ItemStack createShopPurchasedItem(Material material, int amount) {
      return new ItemStack(material, amount);
   }

   private int applyShopDiscount(Player player, int price) {
      int discount = Math.max(0, Math.min(95, this.getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0)));
      long discounted = Math.max(1, price) * (100L - discount) / 100L;
      return (int)Math.max(1L, Math.min(2000000000L, discounted));
   }

   private boolean spawnMerchant(Location location) {
      if (location != null
         && location.getWorld() != null
         && "survival".equalsIgnoreCase(location.getWorld().getName())
         && !this.isCentralPlazaLocation(location)) {
         WanderingTrader trader = (WanderingTrader)location.getWorld().spawnEntity(location, EntityType.WANDERING_TRADER);
         String merchantType = this.randomMerchantType();
         trader.customName(Component.text(this.merchantTypeColor(merchantType) + this.merchantTypeName(merchantType) + "商人"));
         trader.setCustomNameVisible(true);
         trader.setDespawnDelay(Integer.MAX_VALUE);
         trader.setCanDrinkMilk(false);
         trader.setCanDrinkPotion(false);
         trader.setAI(true);
         trader.setInvulnerable(false);
         PersistentDataContainer container = trader.getPersistentDataContainer();
         container.set(this.merchantKey, PersistentDataType.BOOLEAN, true);
         container.set(this.merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
         container.set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
         container.set(this.merchantTypeKey, PersistentDataType.STRING, merchantType);
         this.rerollMerchant(trader);
         return true;
      } else {
         return false;
      }
   }

   private void rerollMerchant(AbstractVillager villager) {
      String merchantType = (String)villager.getPersistentDataContainer().get(this.merchantTypeKey, PersistentDataType.STRING);
      if (merchantType == null || merchantType.isBlank()) {
         merchantType = this.randomMerchantType();
         villager.getPersistentDataContainer().set(this.merchantTypeKey, PersistentDataType.STRING, merchantType);
      }

      villager.customName(Component.text(this.merchantTypeColor(merchantType) + this.merchantTypeName(merchantType) + "商人"));
      villager.setRecipes(Collections.emptyList());
      List<Mifron.MerchantOffer> sellOffers = this.randomMerchantOffers(8, this.merchantSellWeights, true, merchantType);
      List<Mifron.MerchantOffer> buyOffers = this.randomMerchantOffers(8, this.merchantBuyWeights, false, merchantType);
      this.saveMerchantOffers(villager.getUniqueId(), sellOffers, buyOffers);
   }

   private String randomMerchantType() {
      int roll = this.random.nextInt(100);
      if (roll < 33) {
         return "red";
      } else if (roll < 83) {
         return "blue";
      } else {
         return roll < 98 ? "yellow" : "purple";
      }
   }

   private String merchantTypeName(String type) {
      return switch (type) {
         case "red" -> "赤";
         case "yellow" -> "黄";
         case "purple" -> "紫";
         default -> "青";
      };
   }

   private String merchantTypeColor(String type) {
      return switch (type) {
         case "red" -> "§c";
         case "yellow" -> "§e";
         case "purple" -> "§d";
         default -> "§b";
      };
   }

   private List<Mifron.MerchantOffer> randomMerchantOffers(int count, Map<String, Integer> weights, boolean selling, String merchantType) {
      List<Mifron.MerchantOffer> pool = this.merchantOffers(weights, selling)
         .stream()
         .filter(offerx -> this.merchantTypeAllows(merchantType, offerx.material(), selling))
         .toList();
      if (pool.isEmpty()) {
         pool = (selling ? this.allMerchantOffers() : this.allMerchantOffers().stream().filter(offerx -> this.materialBuyPrice(offerx.material()) > 0).toList())
            .stream()
            .filter(offerx -> this.merchantTypeAllows(merchantType, offerx.material(), selling))
            .toList();
      }

      if (pool.isEmpty()) {
         pool = this.merchantOffers(weights, selling);
      }

      List<Mifron.MerchantOffer> offers = new ArrayList<>();
      Set<Material> used = new HashSet<>();
      if ("purple".equals(merchantType)) {
         List<Mifron.MerchantOffer> epicPool = pool.stream().filter(offerx -> this.isMerchantRarityAtLeast(offerx.rarity(), "epic")).toList();
         if (!epicPool.isEmpty()) {
            Mifron.MerchantOffer epic = this.randomWeightedMerchantOffer(used, epicPool, weights);
            offers.add(new Mifron.MerchantOffer(epic.material(), epic.amount(), epic.rarity(), this.randomMerchantPrice(epic.material(), selling)));
         }

         pool = pool.stream().filter(offerx -> this.isMerchantRarityAtLeast(offerx.rarity(), "rare")).toList();
         if (pool.isEmpty()) {
            pool = this.merchantOffers(weights, selling).stream().filter(offerx -> this.isMerchantRarityAtLeast(offerx.rarity(), "rare")).toList();
         }
      }

      for (int i = 0; i < count; i++) {
         Mifron.MerchantOffer offer = this.randomWeightedMerchantOffer(used, pool, weights);
         int price = this.randomMerchantPrice(offer.material(), selling);
         offers.add(new Mifron.MerchantOffer(offer.material(), offer.amount(), offer.rarity(), price));
      }

      return offers.stream().limit(count).toList();
   }

   private boolean merchantTypeAllows(String merchantType, Material material, boolean selling) {
      if ("purple".equals(merchantType)) {
         return true;
      } else {
         String name = material.name();
         if ("red".equals(merchantType)) {
            return selling
               ? material.getMaxDurability() > 0
                  || name.endsWith("_SWORD")
                  || name.endsWith("_AXE")
                  || name.endsWith("_PICKAXE")
                  || name.endsWith("_SHOVEL")
                  || name.endsWith("_HOE")
                  || name.endsWith("_HELMET")
                  || name.endsWith("_CHESTPLATE")
                  || name.endsWith("_LEGGINGS")
                  || name.endsWith("_BOOTS")
                  || name.contains("BOW")
                  || name.contains("ARROW")
                  || name.equals("SHIELD")
                  || name.equals("TRIDENT")
                  || name.equals("MACE")
               : name.contains("STONE")
                  || name.contains("ORE")
                  || name.contains("INGOT")
                  || name.contains("COAL")
                  || name.contains("COPPER")
                  || name.contains("IRON")
                  || name.contains("GOLD")
                  || name.contains("REDSTONE")
                  || name.contains("LAPIS")
                  || name.contains("QUARTZ");
         } else {
            return "yellow".equals(merchantType)
               ? this.materialPrice(material) >= 100 && !name.endsWith("_SPAWN_EGG") && !name.equals("SPAWNER") && !name.equals("TRIAL_SPAWNER")
               : !name.endsWith("_SPAWN_EGG") && !name.equals("SPAWNER") && !name.equals("TRIAL_SPAWNER") && this.materialPrice(material) < 1000;
         }
      }
   }

   private Mifron.MerchantOffer randomWeightedMerchantOffer(Set<Material> used, List<Mifron.MerchantOffer> sourcePool, Map<String, Integer> weights) {
      List<Mifron.MerchantOffer> pool = sourcePool.stream().filter(offerx -> !used.contains(offerx.material())).toList();
      if (pool.isEmpty()) {
         used.clear();
         pool = sourcePool;
      }

      int totalWeight = pool.stream().mapToInt(offerx -> Math.max(1, weights.getOrDefault(offerx.material().name(), 1))).sum();
      int selected = this.random.nextInt(Math.max(1, totalWeight));

      for (Mifron.MerchantOffer offer : pool) {
         selected -= Math.max(1, weights.getOrDefault(offer.material().name(), 1));
         if (selected < 0) {
            used.add(offer.material());
            return offer;
         }
      }

      Mifron.MerchantOffer offer = pool.get(this.random.nextInt(pool.size()));
      used.add(offer.material());
      return offer;
   }

   private List<Mifron.MerchantOffer> merchantOffers(Map<String, Integer> weights, boolean selling) {
      List<Mifron.MerchantOffer> offers = new ArrayList<>();

      for (Material material : Material.values()) {
         if (this.isMerchantWeightedPoolItem(material, weights)) {
            int price = selling ? this.materialPrice(material) : this.materialBuyPrice(material);
            if (price > 0) {
               offers.add(new Mifron.MerchantOffer(material, 1, this.merchantRarity(material), price));
            }
         }
      }

      return offers;
   }

   private int randomMerchantPrice(Material material, boolean selling) {
      int basePrice = selling ? this.materialPrice(material) : this.materialBuyPrice(material);
      int multiplierPercent = 95 + this.random.nextInt(11);
      long price = (long)Math.max(1, basePrice) * multiplierPercent / 100L;
      return (int)Math.max(1L, Math.min(2000000000L, price));
   }

   private List<Mifron.MerchantOffer> barrelShopOffers() {
      List<Mifron.MerchantOffer> offers = new ArrayList<>();

      for (Material material : Material.values()) {
         Mifron.BarrelShopConfig config = this.barrelShopConfigs.get(material.name());
         if (config != null && this.isBarrelShopPoolItem(material)) {
            offers.add(new Mifron.MerchantOffer(material, 1, config.tier(), this.materialPrice(material)));
         }
      }

      return offers;
   }

   private List<Material> randomShopMaterials(int count) {
      if (count <= 0) {
         return List.of();
      }

      List<Material> materials = new ArrayList<>();

      for (Material material : Material.values()) {
         if (this.isRandomShopItem(material)) {
            materials.add(material);
         }
      }

      if (materials.isEmpty()) {
         return List.of();
      }

      Collections.shuffle(materials, this.random);
      return materials.stream().limit(count).toList();
   }

   private boolean isRandomShopItem(Material material) {
      return material != null
         && material.isItem()
         && this.randomShopPrice(material) > 0
         && !MERCHANT_EXCLUDED_ITEMS.contains(material)
         && !material.name().startsWith("LEGACY_")
         && !material.name().endsWith("_SPAWN_EGG");
   }

   private int randomShopPrice(Material material) {
      if (material == null) {
         return 0;
      }

      int configuredPrice = this.shopSalePrices.getOrDefault(material.name(), 0);
      if (configuredPrice > 0) {
         return configuredPrice;
      }

      int economyPrice = this.economyPriceTable.price(material);
      if (economyPrice > 0) {
         return economyPrice;
      }

      Integer exact = this.exactMaterialPrice(material);
      return exact == null ? 0 : Math.max(1, exact);
   }

   private int barrelShopWeight(Material material) {
      Mifron.BarrelShopConfig config = this.barrelShopConfigs.get(material.name());
      return config == null ? 0 : config.weight();
   }

   private List<Mifron.MerchantOffer> allMerchantOffers() {
      Map<Material, Mifron.MerchantOffer> configured = new HashMap<>();
      this.getConfig()
         .getMapList("merchant-items")
         .stream()
         .map(this::readMerchantOffer)
         .filter(Objects::nonNull)
         .forEach(offer -> configured.put(offer.material(), offer));
      List<Mifron.MerchantOffer> offers = new ArrayList<>();

      for (Material material : Material.values()) {
         if (this.isMerchantPoolItem(material)) {
            Mifron.MerchantOffer override = configured.get(material);
            if (override != null) {
               offers.add(override);
            } else {
               String rarity = this.merchantRarity(material);
               offers.add(new Mifron.MerchantOffer(material, 1, rarity, this.materialPrice(material)));
            }
         }
      }

      return offers;
   }

   private void saveMerchantOffers(UUID merchantId, List<Mifron.MerchantOffer> sellOffers, List<Mifron.MerchantOffer> buyOffers) {
      this.data.set("merchants." + merchantId + ".sell", this.serializeMerchantOffers(sellOffers));
      this.data.set("merchants." + merchantId + ".buy", this.serializeMerchantOffers(buyOffers));
      this.queueDataSave();
   }

   private List<String> serializeMerchantOffers(List<Mifron.MerchantOffer> offers) {
      return offers.stream().map(offer -> offer.material().name() + ":" + offer.rarity() + ":" + offer.price()).toList();
   }

   private List<Mifron.MerchantOffer> readMerchantOffers(UUID merchantId, String key) {
      List<Mifron.MerchantOffer> offers = new ArrayList<>();

      for (String raw : this.data.getStringList("merchants." + merchantId + "." + key)) {
         String[] parts = raw.split(":");
         if (parts.length >= 3) {
            Material material = Material.matchMaterial(parts[0]);
            if (material != null && this.isMerchantPoolItem(material)) {
               String rarity = this.merchantRarity(material);
               int normalizedPrice = this.parsePositiveInt(parts[2], "buy".equals(key) ? this.materialBuyPrice(material) : this.materialPrice(material));
               if (normalizedPrice > 0) {
                  offers.add(new Mifron.MerchantOffer(material, 1, rarity, normalizedPrice));
               }
            }
         }
      }

      return offers;
   }

   private Mifron.MerchantOffer readMerchantOffer(Map<?, ?> raw) {
      Object materialValue = raw.get("material");
      if (materialValue == null) {
         return null;
      } else {
         Material material = Material.matchMaterial(materialValue.toString());
         if (material != null && this.isMerchantPoolItem(material)) {
            String rarity = this.merchantRarity(material);
            int configuredPrice = raw.containsKey("price") ? this.parsePositiveInt(String.valueOf(raw.get("price")), -1) : -1;
            int price = Math.max(this.materialPrice(material), configuredPrice);
            return new Mifron.MerchantOffer(material, 1, rarity, price);
         } else {
            return null;
         }
      }
   }

   private boolean isMerchantPoolItem(Material material) {
      String name = material.name();
      return this.isPricedShopItem(material)
         && !name.startsWith("LEGACY_")
         && !name.startsWith("INFESTED_")
         && !name.endsWith("_COMMAND_BLOCK")
         && !Set.of(
               "AIR",
               "BARRIER",
               "BEDROCK",
               "COMMAND_BLOCK",
               "CHAIN_COMMAND_BLOCK",
               "REPEATING_COMMAND_BLOCK",
               "COMMAND_BLOCK_MINECART",
               "STRUCTURE_BLOCK",
               "STRUCTURE_VOID",
               "JIGSAW",
               "LIGHT",
               "DEBUG_STICK",
               "KNOWLEDGE_BOOK"
            )
            .contains(name);
   }

   private boolean isMerchantWeightedPoolItem(Material material, Map<String, Integer> weights) {
      return this.isMerchantPoolItem(material) && weights.getOrDefault(material.name(), 0) > 0;
   }

   private boolean isBarrelShopPoolItem(Material material) {
      String name = material.name();
      return this.isPricedShopItem(material)
         && this.barrelShopConfigs.containsKey(name)
         && !name.endsWith("_SPAWN_EGG")
         && !MERCHANT_EXCLUDED_ITEMS.contains(material);
   }

   private boolean isPricedShopItem(Material material) {
      return material != null && material.isItem() && this.shopSalePrices.getOrDefault(material.name(), 0) > 0;
   }

   private String merchantRarity(Material material) {
      int price = this.materialPrice(material);
      String name = material.name();
      if (price >= 1000
         || name.contains("NETHERITE")
         || name.equals("ELYTRA")
         || name.equals("ENCHANTED_GOLDEN_APPLE")
         || name.endsWith("_TEMPLATE")
         || name.endsWith("_HEAD")
         || name.endsWith("_SKULL")) {
         return "epic";
      } else if (price >= 100
         || name.contains("DIAMOND")
         || name.contains("EMERALD")
         || name.contains("GOLDEN")
         || name.contains("TOTMP")
         || name.contains("HEART_OF_THE_SEA")
         || name.contains("TRIDENT")
         || name.endsWith("_SPEAR")
         || name.contains("SHULKER_BOX")) {
         return "rare";
      } else {
         return price < 10
               && !name.contains("IRON")
               && !name.contains("GOLD")
               && !name.contains("COPPER")
               && !name.contains("REDSTONE")
               && !name.contains("LAPIS")
               && !name.contains("QUARTZ")
               && !name.contains("AMETHYST")
               && !name.contains("ENDER")
               && !name.contains("BLAZE")
            ? "common"
            : "uncommon";
      }
   }

   private boolean isMerchantRarityAtLeast(String rarity, String minimum) {
      return this.merchantRarityRank(rarity) >= this.merchantRarityRank(minimum);
   }

   private int merchantRarityRank(String rarity) {
      return switch ((rarity == null ? "" : rarity).toLowerCase(Locale.ROOT)) {
         case "epic" -> 3;
         case "rare" -> 2;
         case "uncommon" -> 1;
         default -> 0;
      };
   }

   private int materialPrice(Material material) {
      String name = material.name();
      Integer configuredPrice = this.shopSalePrices.get(name);
      if (configuredPrice != null && configuredPrice > 0) {
         return configuredPrice;
      }

      Integer exact = this.exactMaterialPrice(material);
      if (exact != null) {
         return exact;
      }

      if (name.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) {
         return 1000;
      }

      if (name.endsWith("_SMITHING_TEMPLATE")) {
         return 150;
      }

      if (name.equals("IRON_DOOR") || name.equals("IRON_TRAPDOOR")) {
         return 40;
      }

      if (name.equals("HEAVY_WEIGHTED_PRESSURE_PLATE")) {
         return 20;
      }

      if (name.equals("LIGHT_WEIGHTED_PRESSURE_PLATE")) {
         return 50;
      }

      int baseFromStorage = this.storageMaterialPrice(name);
      if (baseFromStorage > 0) {
         return baseFromStorage;
      }

      int equipment = this.equipmentPrice(name);
      if (equipment > 0) {
         return equipment;
      }

      if (name.endsWith("_ORE")) {
         return Math.max(8, this.priceByContainedResource(name));
      }

      if (name.startsWith("RAW_") && !name.endsWith("_BLOCK")) {
         return switch (name) {
            case "RAW_IRON" -> 8;
            case "RAW_GOLD" -> 20;
            case "RAW_COPPER" -> 3;
            default -> 4;
         };
      } else if (name.endsWith("_LOG") || name.endsWith("_STMP") || name.endsWith("_HYPHAE")) {
         return 2;
      } else if (name.endsWith("_PLANKS")
         || name.endsWith("_LEAVES")
         || name.endsWith("_SAPLING")
         || name.endsWith("_BUTTON")
         || name.endsWith("_PRESSURE_PLATE")) {
         return 1;
      } else if (name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_FENCE") || name.endsWith("_WALL")) {
         return 2;
      } else if (name.endsWith("_WOOL")
         || name.endsWith("_CARPET")
         || name.endsWith("_TERRACOTTA")
         || name.endsWith("_CONCRETE")
         || name.endsWith("_CONCRETE_POWDER")) {
         return 2;
      } else if (name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE")) {
         return 2;
      } else if (name.endsWith("_SHULKER_BOX")) {
         return 150;
      } else if (name.endsWith("_BANNER") || name.endsWith("_BED")) {
         return 5;
      } else if (name.endsWith("_BOAT") || name.endsWith("_SIGN") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")) {
         return 4;
      } else if (name.contains("POTION") || name.contains("ENCHANTED_BOOK")) {
         return 100;
      } else if (name.contains("MUSIC_DISC") || name.contains("POTTERY_SHERD") || name.contains("ARMOR_TRIM")) {
         return 100;
      } else if (name.contains("SPONGE") || name.contains("SEA_LANTERN") || name.contains("PRISMARINE") || name.contains("END_ROD") || name.contains("PURPUR")) {
         return 20;
      } else if (name.contains("SCULK") || name.contains("ECHO_SHARD") || name.contains("RECOVERY_COMPASS")) {
         return 50;
      } else if (name.contains("DEEPSLATE")
         || name.contains("TUFF")
         || name.contains("CALCITE")
         || name.contains("DRIPSTONE")
         || name.contains("BLACKSTONE")
         || name.contains("BASALT")) {
         return 2;
      } else if (name.contains("NETHER") || name.contains("END_")) {
         return 8;
      } else if (name.contains("FLOWER")
         || name.contains("CORAL")
         || name.contains("FUNGUS")
         || name.contains("MUSHROOM")
         || name.contains("ROOTS")
         || name.contains("VINES")) {
         return 3;
      } else if (!name.contains("SUSPICIOUS") && !name.contains("TRIAL") && !name.contains("OMINOUS")) {
         return material.isBlock() ? 1 : 3;
      } else {
         return 50;
      }
   }

   private int materialBuyPrice(Material material) {
      int configuredBuyPrice = this.shopBuyPrices.getOrDefault(material.name(), 0);
      return configuredBuyPrice <= 0 ? 0 : Math.max(1, Math.min(2000000000, configuredBuyPrice));
   }

   private Integer exactMaterialPrice(Material material) {
      return switch (material) {
         case DIRT, GRASS_BLOCK, SAND, GRAVEL, COBBLESTONE, STONE, NETHERRACK, END_STONE -> 1;
         case CLAY, BRICK, BRICKS -> 4;
         case COAL, CHARCOAL -> 4;
         case COAL_BLOCK -> 36;
         case COPPER_INGOT -> 4;
         case COPPER_BLOCK, WAXED_COPPER_BLOCK -> 36;
         case IRON_NUGGET -> 2;
         case IRON_INGOT -> 10;
         case IRON_BLOCK -> 90;
         case GOLD_NUGGET -> 3;
         case GOLD_INGOT -> 25;
         case GOLD_BLOCK -> 225;
         case REDSTONE -> 5;
         case REDSTONE_BLOCK -> 45;
         case LAPIS_LAZULI -> 8;
         case LAPIS_BLOCK -> 72;
         case QUARTZ -> 8;
         case QUARTZ_BLOCK -> 32;
         case AMETHYST_SHARD -> 8;
         case AMETHYST_BLOCK -> 32;
         case DIAMOND -> 100;
         case DIAMOND_BLOCK -> 900;
         case EMERALD -> 100;
         case EMERALD_BLOCK -> 900;
         case NETHERITE_SCRAP -> 250;
         case NETHERITE_INGOT -> 1100;
         case NETHERITE_BLOCK -> 9900;
         case OBSIDIAN -> 20;
         case CRYING_OBSIDIAN -> 35;
         case ANCIENT_DEBRIS -> 300;
         case BLAZE_ROD -> 20;
         case BLAZE_POWDER -> 10;
         case ENDER_PEARL -> 20;
         case ENDER_EYE -> 35;
         case GHAST_TEAR -> 50;
         case MAGMA_CREAM -> 12;
         case SLIME_BALL -> 10;
         case PHANTOM_MEMBRANE -> 30;
         case SHULKER_SHELL -> 75;
         case TOTEM_OF_UNDYING -> 250;
         case HEART_OF_THE_SEA -> 250;
         case NAUTILUS_SHELL -> 30;
         case TRIDENT -> 300;
         case ELYTRA -> 1500;
         case DRAGON_BREATH -> 100;
         case NETHER_STAR -> 2000;
         case EXPERIENCE_BOTTLE -> 20;
         case GOLDEN_APPLE -> 80;
         case ENCHANTED_GOLDEN_APPLE -> 1500;
         case APPLE, BREAD, CARROT, POTATO, BEETROOT, WHEAT, MELON_SLICE, SWEET_BERRIES, GLOW_BERRIES -> 2;
         case COOKED_BEEF, COOKED_PORKCHOP, COOKED_MUTTON, COOKED_CHICKEN, COOKED_COD, COOKED_SALMON -> 5;
         case BEEF, PORKCHOP, MUTTON, CHICKEN, COD, SALMON -> 3;
         case TORCH -> 1;
         case LANTERN, SOUL_LANTERN -> 12;
         case BOOK -> 6;
         case BOOKSHELF -> 20;
         case PAPER -> 1;
         case LEATHER -> 8;
         case STRING -> 3;
         case FEATHER -> 3;
         case GUNPOWDER -> 8;
         case BONE -> 3;
         case ROTTEN_FLESH -> 1;
         case SPIDER_EYE -> 4;
         case NAME_TAG -> 80;
         case SADDLE -> 80;
         case LEAD -> 15;
         case BEACON -> 2500;
         case CONDUIT -> 600;
         default -> null;
      };
   }

   private int storageMaterialPrice(String name) {
      if (name.endsWith("_BLOCK")) {
         String base = name.substring(0, name.length() - "_BLOCK".length());

         return switch (base) {
            case "RAW_IRON" -> 72;
            case "RAW_GOLD" -> 180;
            case "RAW_COPPER" -> 27;
            case "HONEY" -> 36;
            case "SLIME" -> 90;
            default -> 0;
         };
      } else {
         return !name.endsWith("_BUNDLE") && !name.endsWith("_BOX") ? 0 : 20;
      }
   }

   private int equipmentPrice(String name) {
      int material = this.equipmentMaterialBase(name);
      if (material <= 0) {
         return 0;
      }

      int units = this.equipmentUnits(name);
      if (units <= 0) {
         return 0;
      }

      int enchantablePremium = name.contains("NETHERITE") ? 200 : 0;
      return material * units + enchantablePremium;
   }

   private int equipmentMaterialBase(String name) {
      if (name.startsWith("WOODEN_")) {
         return 2;
      } else if (name.startsWith("STONE_")) {
         return 1;
      } else if (name.startsWith("LEATHER_")) {
         return 8;
      } else if (name.startsWith("CHAINMAIL_")) {
         return 12;
      } else if (name.startsWith("IRON_")) {
         return 10;
      } else if (name.startsWith("GOLDEN_")) {
         return 25;
      } else if (name.startsWith("COPPER_")) {
         return 4;
      } else if (name.startsWith("DIAMOND_")) {
         return 100;
      } else {
         return name.startsWith("NETHERITE_") ? 1100 : 0;
      }
   }

   private int equipmentUnits(String name) {
      if (name.endsWith("_HELMET")) {
         return 5;
      } else if (name.endsWith("_CHESTPLATE")) {
         return 8;
      } else if (name.endsWith("_LEGGINGS")) {
         return 7;
      } else if (name.endsWith("_BOOTS")) {
         return 4;
      } else if (name.endsWith("_SWORD") || name.endsWith("_HOE") || name.endsWith("_SPEAR")) {
         return 2;
      } else if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) {
         return 3;
      } else if (name.endsWith("_SHOVEL")) {
         return 1;
      } else {
         return name.endsWith("_HORSE_ARMOR") ? 6 : 0;
      }
   }

   private int priceByContainedResource(String name) {
      if (name.contains("DIAMOND")) {
         return 100;
      } else if (name.contains("EMERALD")) {
         return 100;
      } else if (name.contains("GOLD")) {
         return 25;
      } else if (name.contains("IRON")) {
         return 10;
      } else if (name.contains("LAPIS")) {
         return 8;
      } else if (name.contains("REDSTONE")) {
         return 5;
      } else if (name.contains("COPPER")) {
         return 4;
      } else if (name.contains("COAL")) {
         return 4;
      } else {
         return name.contains("QUARTZ") ? 8 : 4;
      }
   }

   private void tickMerchants() {
      long now = System.currentTimeMillis();

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
               villager.setInvulnerable(false);
               villager.setAI(!this.activeMerchantViews.containsValue(villager.getUniqueId()));
               PersistentDataContainer container = entity.getPersistentDataContainer();
               long spawnedAt = (Long)container.getOrDefault(this.merchantSpawnKey, PersistentDataType.LONG, now);
               if (now - spawnedAt >= 3600000L) {
                  boolean traded = Boolean.TRUE.equals(container.get(this.merchantTradedKey, PersistentDataType.BOOLEAN));
                  if (!traded) {
                     entity.remove();
                  } else {
                     this.rerollMerchant(villager);
                     container.set(this.merchantSpawnKey, PersistentDataType.LONG, now);
                     container.set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
                  }
               }
            }
         }

         this.trySpawnRandomMerchant(world);
      }
   }

   private void trySpawnRandomMerchant(World world) {
      if ("survival".equalsIgnoreCase(world.getName())
         && world.getEnvironment() == Environment.NORMAL
         && !world.getName().equalsIgnoreCase("elysion")
         && !world.getName().equalsIgnoreCase("ginnungagap")
         && !(this.random.nextDouble() >= 0.005)
         && !world.getPlayers().isEmpty()) {
         Player anchor = (Player)world.getPlayers().get(this.random.nextInt(world.getPlayers().size()));
         Location base = anchor.getLocation().clone().add(this.random.nextInt(33) - 16, 0.0, this.random.nextInt(33) - 16);
         int y = world.getHighestBlockYAt(base);
         Location spawn = new Location(world, base.getBlockX() + 0.5, y + 1.0, base.getBlockZ() + 0.5);
         if (!this.isCentralPlazaLocation(spawn)) {
            this.spawnMerchant(spawn);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (this.isCentralPlazaLocation(event.getLocation())) {
         event.setCancelled(true);
      }
   }

   private boolean isCentralPlazaLocation(Location location) {
      return location != null && this.protectionService.isSpawnProtected(location);
   }

   boolean isStructureProtectedLocation(Location location) {
      return this.protectionService.isProtected(location);
   }

   private boolean isMifronMerchant(Entity entity) {
      return Boolean.TRUE.equals(entity.getPersistentDataContainer().get(this.merchantKey, PersistentDataType.BOOLEAN));
   }

   @EventHandler
   public void onMerchantDamage(EntityDamageEvent event) {
      if (this.isMifronMerchant(event.getEntity())) {
         event.getEntity().setInvulnerable(false);
         if (!this.isPlayerCausedDamage(event)) {
            event.setCancelled(true);
            event.setDamage(0.0);
         }
      }
   }

   private boolean isPlayerCausedDamage(EntityDamageEvent event) {
      if (event instanceof EntityDamageByEntityEvent entityDamage) {
         Entity damager = entityDamage.getDamager();
         if (damager instanceof Player) {
            return true;
         } else {
            return damager instanceof Projectile projectile ? projectile.getShooter() instanceof Player : false;
         }
      } else {
         return false;
      }
   }

   @EventHandler
   public void onPlayerTrade(PlayerTradeEvent event) {
      this.addPlayerStat(event.getPlayer().getUniqueId(), "total-trades", 1);
      if (this.isMifronMerchant(event.getVillager())) {
         event.getVillager().getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, true);
      }
   }

   @EventHandler
   public void onMerchantInteract(PlayerInteractEntityEvent event) {
      if (event.getRightClicked() instanceof AbstractVillager villager && this.isMifronMerchant(villager)) {
         event.setCancelled(true);
         villager.setAI(false);
         villager.setInvulnerable(false);
         this.openMerchantUi(event.getPlayer(), villager);
      }
   }

   private void openMerchantUi(Player player, AbstractVillager villager) {
      this.activeMerchantViews.put(player.getUniqueId(), villager.getUniqueId());
      this.renderMerchantUi(player, villager);
   }

   private void renderMerchantUi(Player player, AbstractVillager villager) {
      List<Mifron.MerchantOffer> sellOffers = this.readMerchantOffers(villager.getUniqueId(), "sell");
      List<Mifron.MerchantOffer> buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      if (sellOffers.isEmpty() || buyOffers.isEmpty()) {
         this.rerollMerchant(villager);
         sellOffers = this.readMerchantOffers(villager.getUniqueId(), "sell");
         buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      }

      int page = Math.max(0, Math.min(1, this.activeMerchantPages.getOrDefault(player.getUniqueId(), 0)));
      this.activeMerchantPages.put(player.getUniqueId(), page);
      Inventory inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.DROPPER, Component.text(MERCHANT_UI_TITLE));
      List<Mifron.MerchantOffer> offers = page == 0 ? sellOffers : buyOffers;
      String action = page == 0 ? "sell" : "buy";
      inventory.setItem(0, this.named(page == 0 ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
         page == 0 ? "§a購入" : "§c売却",
         List.of("§7" + (page == 0 ? "商品をクリックして購入" : "アイテムをクリックして売却"))));
      for (int i = 0; i < Math.min(6, offers.size()); i++) {
         inventory.setItem(i + 1, this.createMerchantOfferIcon(villager, offers.get(i), action));
      }
      inventory.setItem(7, this.createMerchantNavigationIcon(Material.ARROW, "§e購入一覧", "merchant_sell", villager.getUniqueId()));
      inventory.setItem(8, this.createMerchantNavigationIcon(Material.ARROW, "§e売却一覧", "merchant_buy", villager.getUniqueId()));

      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(
            player,
            "Mifron Merchant",
            inventory,
            this::isMerchantOffer,
            (slot, item) -> {
               if (this.isMerchantOffer(item)) {
                  this.buyMerchantOffer(player, item, false);
               } else {
                  this.handleMerchantNavigation(player, item);
               }
            },
            () -> {
               this.activeMerchantPages.remove(player.getUniqueId());
               this.activeMerchantPages.remove(player.getUniqueId());
         UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
               if (merchantId != null && !this.activeMerchantViews.containsValue(merchantId)) {
                  Entity entity = this.findEntity(merchantId);
                  if (entity instanceof AbstractVillager merchant && this.isMifronMerchant(entity)) {
                     merchant.setAI(true);
                     merchant.setInvulnerable(false);
                  }
               }
            }
         )) {
         return;
      }
      player.openInventory(inventory);
   }

   private ItemStack createMerchantNavigationIcon(Material material, String name, String action, UUID merchantId) {
      ItemStack item = this.named(material, name, List.of("§7クリックで切り替え"));
      ItemMeta meta = item.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.uiActionKey, PersistentDataType.STRING, action);
      container.set(this.uiTargetKey, PersistentDataType.STRING, merchantId.toString());
      item.setItemMeta(meta);
      return item;
   }

   private void handleMerchantNavigation(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (!"merchant_sell".equals(action) && !"merchant_buy".equals(action)) {
         return;
      }
      UUID merchantId = this.getUiTarget(clicked);
      if (merchantId == null) {
         return;
      }
      Entity entity = this.findEntity(merchantId);
      if (!(entity instanceof AbstractVillager villager) || !this.isMifronMerchant(entity)) {
         player.sendMessage("§c商人が見つかりません。");
         player.closeInventory();
         return;
      }
      this.activeMerchantPages.put(player.getUniqueId(), "merchant_buy".equals(action) ? 1 : 0);
      this.renderMerchantUi(player, villager);
   }

   private ItemStack createMerchantOfferIcon(AbstractVillager villager, Mifron.MerchantOffer offer, String action) {
      ItemStack item = new ItemStack(offer.material(), 1);
      ItemMeta meta = item.getItemMeta();
      boolean selling = "sell".equals(action);
      meta.displayName(Component.translatable(offer.material().translationKey()).color(this.rarityTextColor(offer.rarity())));
      meta.lore(
         List.of(
            Component.text("§7レア度: " + this.rarityLabel(offer.rarity())),
            Component.text("§7" + (selling ? "価格: " : "買取額: ") + this.formatNumber(offer.price()) + "MP"),
            Component.text("§7" + (selling ? "クリックでMP残高から購入" : "クリックで1個売却")),
            Component.text("§7Shift+クリック: まとめて取引")
         )
      );
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.merchantOfferKey, PersistentDataType.BOOLEAN, true);
      container.set(this.merchantOfferPriceKey, PersistentDataType.INTEGER, offer.price());
      container.set(this.merchantOfferMaterialKey, PersistentDataType.STRING, offer.material().name());
      container.set(this.merchantOfferAmountKey, PersistentDataType.INTEGER, 1);
      container.set(this.merchantOfferMerchantKey, PersistentDataType.STRING, villager.getUniqueId().toString());
      container.set(this.merchantOfferActionKey, PersistentDataType.STRING, action);
      container.set(this.merchantOfferRarityKey, PersistentDataType.STRING, offer.rarity());
      item.setItemMeta(meta);
      return item;
   }

   private boolean isMerchantOffer(ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(this.merchantOfferKey, PersistentDataType.BOOLEAN));
   }

   private void buyMerchantOffer(Player player, ItemStack clicked, boolean bulk) {
      long now = System.currentTimeMillis();
      long last = this.lastMerchantTransaction.getOrDefault(player.getUniqueId(), 0L);
      if (now - last >= 150L) {
         if (this.merchantTransactions.add(player.getUniqueId())) {
            this.lastMerchantTransaction.put(player.getUniqueId(), now);

            try {
               this.buyMerchantOfferNow(player, clicked, bulk);
            } finally {
               Bukkit.getScheduler().runTaskLater(this, () -> this.merchantTransactions.remove(player.getUniqueId()), 3L);
            }
         }
      }
   }

   private void buyMerchantOfferNow(Player player, ItemStack clicked, boolean bulk) {
      ItemMeta meta = clicked.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      Integer price = (Integer)container.get(this.merchantOfferPriceKey, PersistentDataType.INTEGER);
      Integer amount = (Integer)container.get(this.merchantOfferAmountKey, PersistentDataType.INTEGER);
      String materialName = (String)container.get(this.merchantOfferMaterialKey, PersistentDataType.STRING);
      String action = (String)container.get(this.merchantOfferActionKey, PersistentDataType.STRING);
      if (price != null && amount != null && materialName != null && action != null) {
         Material material = Material.matchMaterial(materialName);
         if (material != null && material.isItem()) {
            price = Math.min(2000000000, Math.max(1, price));
            amount = Math.min(64, Math.max(1, amount));
            if ("buy".equals(action)) {
               int quantity = bulk ? this.maxMerchantSaleQuantity(player, material) : 1;
               if (bulk && quantity <= 0) {
                  player.sendMessage("§cこのアイテムは一括取引できません。");
               } else {
                  Mifron.MerchantSale sale = quantity <= 0 ? null : this.removeItemsForMerchantSale(player, material, quantity, price);
                  if (sale == null) {
                     this.sendItemMessage(player, NamedTextColor.RED, "", material, "を1個持っていません。");
                  } else {
                     this.depositEmeralds(player.getUniqueId(), sale.totalPrice());
                     this.addPlayerStat(player.getUniqueId(), "total-trades", sale.quantity());
                     this.recordFarmingSubmission(player, material);
                     this.markMerchantTraded((String)container.get(this.merchantOfferMerchantKey, PersistentDataType.STRING));
                     this.playPurchaseSound(player);
                     this.sendItemMessage(
                        player, NamedTextColor.GREEN, "売却しました: ", material, " x" + sale.quantity() + " (+" + this.formatNumber(sale.totalPrice()) + "MP)"
                     );
                  }
               }
            } else {
               int quantity = bulk ? this.maxMerchantPurchaseQuantity(player, material, price) : 1;
               if (bulk && material.getMaxStackSize() <= 1) {
                  player.sendMessage("§cこのアイテムは一括購入できません。");
               } else if (quantity <= 0) {
                  if (this.getEmeralds(player.getUniqueId()) < price) {
                     player.sendMessage("§cMPが足りません。必要MP: " + this.formatNumber(price));
                  } else {
                     player.sendMessage("§cインベントリに空きがありません。");
                  }
               } else if (this.inventorySpaceFor(player, material) < quantity) {
                  player.sendMessage("§cインベントリに空きがありません。");
               } else {
                  int total = this.safeMultiply(price, quantity);
                  if (!this.withdrawEmeralds(player.getUniqueId(), total)) {
                     player.sendMessage("§cMPが足りません。必要MP: " + this.formatNumber(total));
                  } else {
                     this.giveShopPurchasedItems(player, material, quantity);
                     this.addPlayerStat(player.getUniqueId(), "total-trades", quantity);
                     this.markMerchantTraded((String)container.get(this.merchantOfferMerchantKey, PersistentDataType.STRING));
                     this.playPurchaseSound(player);
                     this.sendItemMessage(player, NamedTextColor.GREEN, "購入しました: ", material, " x" + quantity + " (" + this.formatNumber(total) + "MP)");
                  }
               }
            }
         } else {
            player.sendMessage("§c商人の商品が不正です。");
         }
      } else {
         player.sendMessage("§c商人の商品データが不正です。");
      }
   }

   private void sendItemMessage(Player player, NamedTextColor color, String prefix, Material material, String suffix) {
      player.sendMessage(
         ((TextComponent)Component.text(prefix, color).append(Component.translatable(material.translationKey()).color(color)))
            .append(Component.text(suffix, color))
      );
   }

   private void tryReincarnate(Player player, ItemStack star) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("all-advancements-rewarded", false)) {
         player.sendMessage("§c全進捗達成後に転生できます。");
      } else {
         int next = this.safeAdd(section.getInt("reincarnations", 0), 1);
         int requiredEmeralds = this.safeMultiply(10000, next);
         int requiredLevel = Math.min(1000, this.safeAdd(20, this.safeMultiply(10, next)));
         int currentEmeralds = this.getEmeralds(player.getUniqueId());
         int currentLevel = player.getLevel();
         if (currentEmeralds >= requiredEmeralds && currentLevel >= requiredLevel) {
            int bonus = Math.max(0, currentEmeralds / 1000 + currentLevel);
            if (star != null) {
               this.consumeOne(star);
            }

            section.set("emeralds", 0);
            section.set("advancement-bonus-percent", 0);
            section.set("income-bonus-percent", null);
            section.set("reincarnations", next);
            section.set("reincarnation-bonus-percent", this.safeAdd(this.getReincarnationBonus(player.getUniqueId()), bonus));
            player.setLevel(0);
            player.setExp(0.0F);
            this.saveData();
            this.recordQuestProgress(player, "reincarnations", next);
            this.playReincarnationSound(player);
            player.sendMessage("§d転生しました: " + next + "回目 / 転生ボーナス+" + bonus + "%");
         } else {
            player.sendMessage("§c転生条件を満たしていません。必要: " + this.formatNumber(requiredEmeralds) + "MP / Lv" + requiredLevel);
         }
      }
   }

   private void resetAdvancements(Player player) {
      Iterator<Advancement> iterator = Bukkit.advancementIterator();

      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (this.shouldTrackAdvancement(advancement)) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);

            for (String criterion : new ArrayList<String>(progress.getAwardedCriteria())) {
               progress.revokeCriteria(criterion);
            }
         }
      }
   }

   private void applyPendingAdvancementReset(Player player) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      if (section.getBoolean("pending-advancement-reset", false)) {
         this.resetAdvancements(player);
         section.set("pending-advancement-reset", null);
         this.saveData();
      }
   }

   private void playPurchaseSound(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 1.1F);
   }

   private void playUiClickSound(Player player) {
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.25F);
   }

   private void playTeleportSound(Player player) {
      player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.0F);
   }

   private void playReincarnationSound(Player player) {
      Location location = player.getLocation();
      player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
      player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.8F);
      player.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.4F);
   }

   private Mifron.MerchantSale removeItemsForMerchantSale(Player player, Material material, int amount, int basePrice) {
      int available = 0;

      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMerchantSellableStack(item, material)) {
            available += item.getAmount();
         }
      }

      if (available < amount) {
         return null;
      }

      int remaining = amount;
      int totalPrice = 0;
      ItemStack[] contents = player.getInventory().getContents();

      for (int i = 0; i < contents.length && remaining > 0; i++) {
         ItemStack item = contents[i];
         if (this.isMerchantSellableStack(item, material)) {
            int removed = Math.min(item.getAmount(), remaining);
            totalPrice = this.safeAdd(totalPrice, this.safeMultiply(this.merchantSaleUnitPrice(basePrice, item), removed));
            item.setAmount(item.getAmount() - removed);
            remaining -= removed;
            if (item.getAmount() <= 0) {
               contents[i] = null;
            }
         }
      }

      player.getInventory().setContents(contents);
      return new Mifron.MerchantSale(amount, totalPrice);
   }

   private int countItemsByType(Player player, Material material) {
      int available = 0;

      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMerchantSellableStack(item, material)) {
            available += item.getAmount();
         }
      }

      return available;
   }

   private boolean isMerchantSellableStack(ItemStack item, Material material) {
      return item != null && item.getType() == material && this.utilityItemsFeature.getMifronItemId(item) == null && !this.isMerchantOffer(item);
   }

   private int merchantSaleUnitPrice(int basePrice, ItemStack item) {
      if (item.getItemMeta() instanceof Damageable damageable && item.getType().getMaxDurability() > 0) {
         int maxDurability = item.getType().getMaxDurability();
         int remainingDurability = Math.max(1, maxDurability - damageable.getDamage());
         return Math.max(1, (int)((long)Math.max(1, basePrice) * remainingDurability / maxDurability));
      } else {
         return Math.max(1, basePrice);
      }
   }

   private int maxMerchantPurchaseQuantity(Player player, Material material, int price) {
      int maxStack = material.getMaxStackSize();
      if (maxStack > 1 && price > 0) {
         int affordable = this.getEmeralds(player.getUniqueId()) / price;
         int space = this.inventorySpaceFor(player, material);
         return Math.max(0, Math.min(Math.min(affordable, space), maxStack));
      } else {
         return 0;
      }
   }

   private int maxMerchantSaleQuantity(Player player, Material material) {
      int maxStack = material.getMaxStackSize();
      return maxStack <= 1 ? 0 : Math.min(this.countItemsByType(player, material), maxStack);
   }

   private int inventorySpaceFor(Player player, Material material) {
      int space = 0;
      int maxStack = material.getMaxStackSize();

      for (ItemStack item : player.getInventory().getStorageContents()) {
         if (item == null || item.getType() == Material.AIR) {
            space += maxStack;
         } else if (item.getType() == material && item.getAmount() < maxStack) {
            space += maxStack - item.getAmount();
         }
      }

      return space;
   }

   private void giveShopPurchasedItems(Player player, Material material, int amount) {
      this.recordAcquiredItem(player, material);
      int remaining = amount;
      int maxStack = material.getMaxStackSize();

      while (remaining > 0) {
         int stackAmount = Math.min(maxStack, remaining);
         Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{this.createShopPurchasedItem(material, stackAmount)});
         if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers.values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
         }

         remaining -= stackAmount;
      }
   }

   private String rarityLabel(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) {
         case "epic" -> "§dエピック";
         case "rare" -> "§9レア";
         case "uncommon" -> "§aアンコモン";
         default -> "§fコモン";
      };
   }

   private String rarityName(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) {
         case "epic" -> "エピック";
         case "rare" -> "レア";
         case "uncommon" -> "アンコモン";
         default -> "コモン";
      };
   }

   private String rarityChatColor(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) {
         case "epic" -> "§d";
         case "rare" -> "§9";
         case "uncommon" -> "§a";
         default -> "§f";
      };
   }

   private NamedTextColor rarityTextColor(String rarity) {
      return switch (rarity.toLowerCase(Locale.ROOT)) {
         case "epic" -> NamedTextColor.LIGHT_PURPLE;
         case "rare" -> NamedTextColor.BLUE;
         case "uncommon" -> NamedTextColor.GREEN;
         default -> NamedTextColor.WHITE;
      };
   }

   private String japaneseItemName(Material material) {
      return switch (material) {
         case COBBLESTONE -> "丸石";
         case BRICKS -> "レンガブロック";
         case COAL -> "石炭";
         case IRON_INGOT -> "鉄インゴット";
         case GOLD_INGOT -> "金インゴット";
         case REDSTONE -> "レッドストーン";
         case LAPIS_LAZULI -> "ラピスラズリ";
         case QUARTZ -> "ネザークォーツ";
         case AMETHYST_SHARD -> "アメジストの欠片";
         case DIAMOND -> "ダイヤモンド";
         case EMERALD -> "エメラルド";
         case GOLDEN_APPLE -> "金のリンゴ";
         case ENCHANTED_GOLDEN_APPLE -> "エンチャントされた金のリンゴ";
         case BREAD -> "パン";
         case TORCH -> "松明";
         case OAK_LOG -> "オークの原木";
         case GLASS -> "ガラス";
         case NETHERITE_UPGRADE_SMITHING_TEMPLATE -> "ネザライト強化の鍛冶型";
         default -> this.toReadableMaterialName(material);
      };
   }

   private String toReadableMaterialName(Material material) {
      String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
      List<String> words = new ArrayList<>();

      for (String part : parts) {
         if (!part.isEmpty()) {
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
         }
      }

      return String.join(" ", words);
   }

   private void markMerchantTraded(String rawUuid) {
      if (rawUuid != null) {
         try {
            UUID merchantId = UUID.fromString(rawUuid);

            for (World world : Bukkit.getWorlds()) {
               Entity entity = world.getEntity(merchantId);
               if (entity != null && this.isMifronMerchant(entity)) {
                  entity.getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, true);
                  return;
               }
            }
         } catch (IllegalArgumentException var6) {
         }
      }
   }

   private int parsePositiveInt(String value, int fallback) {
      if (value == null) {
         return fallback;
      }

      String normalized = value.replaceAll("[^0-9]", "");
      if (!normalized.isEmpty() && normalized.length() <= 10) {
         try {
            long parsed = Long.parseLong(normalized);
            return parsed > 2147483647L ? fallback : (int)Math.max(0L, parsed);
         } catch (NumberFormatException e) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private String sanitizeTextInput(String value, int maxLength) {
      if (value == null) {
         return "";
      }

      String sanitized = value.replaceAll("\\p{Cntrl}", "").trim();
      return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
   }

   private void openFriendUi(Player player) {
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text("§3Mifron Friends"));
      String filter = this.friendSearchFilters.getOrDefault(player.getUniqueId(), "");
      inventory.setItem(
         0,
         this.actionItem(
            Material.OAK_SIGN, "§e検索バー", List.of("§7" + (filter.isBlank() ? "クリックして検索語を入力" : "検索中: " + filter), "§7空入力で検索解除"), "friend_search", null
         )
      );
      this.fillFriendTopTabs(inventory);
      this.fillFriendRows(player, inventory, filter);
      this.fillNotificationOrChatBox(player, inventory);
      this.fillStatusBox(player, inventory);
      this.fillEmptyGuiSlots(inventory);
      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Friends / Status", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleFriendUiClick(player, item))) {
         return;
      }
      player.openInventory(inventory);
   }

   private void fillFriendTopTabs(Inventory inventory) {
      inventory.setItem(1, this.actionItem(Material.BOOK, "§d進捗", List.of("§7進捗一覧"), "status_tab_progress", null));
      inventory.setItem(2, this.actionItem(Material.NETHER_STAR, "§d転生", List.of("§7転生回数、条件、転生ボーナス"), "status_tab_reincarnation", null));
      inventory.setItem(3, this.actionItem(Material.NAME_TAG, "§6称号", List.of("§7称号一覧と選択"), "status_tab_titles", null));
      inventory.setItem(4, this.actionItem(Material.ZOMBIE_SPAWN_EGG, "§c討伐", List.of("§7Mob討伐状況"), "status_tab_kills", null));
   }

   private void fillFriendRows(Player player, Inventory inventory, String filter) {
      Set<UUID> friends = this.getUuidSet(player.getUniqueId(), "friends");
      Set<UUID> listed = new HashSet<>(friends);

      for (Player online : Bukkit.getOnlinePlayers()) {
         if (!online.getUniqueId().equals(player.getUniqueId())) {
            listed.add(online.getUniqueId());
         }
      }

      List<OfflinePlayer> users = listed.stream()
         .<OfflinePlayer>map(Bukkit::getOfflinePlayer)
         .filter(userx -> this.matchesFriendFilter(userx, filter))
         .sorted((first, second) -> {
            if (first.isOnline() != second.isOnline()) {
               return first.isOnline() ? -1 : 1;
            } else {
               return this.safePlayerName(first).compareToIgnoreCase(this.safePlayerName(second));
            }
         })
         .toList();
      int row = 1;

      for (OfflinePlayer user : users) {
         if (row > 3) {
            break;
         }

         boolean friend = friends.contains(user.getUniqueId());
         boolean online = user.isOnline();
         int base = row * 9;
         inventory.setItem(
            base, this.actionItem(Material.PLAYER_HEAD, "§f" + this.safePlayerName(user), List.of("§7プレイヤー"), "friend_profile", user.getUniqueId().toString())
         );
         inventory.setItem(
            base + 1,
            this.actionItem(Material.NAME_TAG, "§f" + this.safePlayerName(user), List.of("§7ユーザーネーム"), "friend_profile", user.getUniqueId().toString())
         );
         inventory.setItem(
            base + 2,
            this.actionItem(
               online ? Material.LIME_DYE : Material.GRAY_DYE,
               (online ? "§a" : "§8") + (online ? "オンライン" : "オフライン"),
               List.of("§7オンライン状態"),
               "friend_profile",
               user.getUniqueId().toString()
            )
         );
         inventory.setItem(
            base + 3,
            this.actionItem(
               friend ? Material.RED_DYE : Material.EMERALD,
               friend ? "§cフレンド解除" : "§aフレンド申請",
               List.of(friend ? "§7クリックで解除" : "§7クリックで申請を送信"),
               friend ? "friend_remove" : "friend_request",
               user.getUniqueId().toString()
            )
         );
         inventory.setItem(
            base + 4,
            this.actionItem(
               Material.WRITABLE_BOOK,
               "§bチャット",
               List.of(friend ? "§7クリックでチャット欄を開く" : "§cフレンドのみチャット可能"),
               friend ? "friend_chat_open" : "friend_request",
               user.getUniqueId().toString()
            )
         );
         row++;
      }

      if (row == 1) {
         inventory.setItem(9, this.named(Material.GRAY_STAINED_GLASS_PANE, "§7該当プレイヤーなし", List.of("§7検索条件を変更してください")));
      }
   }

   private boolean matchesFriendFilter(OfflinePlayer player, String filter) {
      return filter == null || filter.isBlank() || this.safePlayerName(player).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
   }

   private void fillNotificationOrChatBox(Player player, Inventory inventory) {
      UUID chatTarget = this.activeFriendChatTarget.get(player.getUniqueId());
      if (chatTarget != null) {
         OfflinePlayer target = Bukkit.getOfflinePlayer(chatTarget);
         String draft = this.friendChatDrafts.getOrDefault(player.getUniqueId(), "");
         inventory.setItem(6, this.named(Material.BOOK, "§bチャット欄: " + this.safePlayerName(target), List.of("§7下の羽ペンで本文を入力")));
         inventory.setItem(
            7, this.actionItem(Material.WRITABLE_BOOK, "§e本文入力", List.of("§7" + (draft.isBlank() ? "未入力" : draft)), "friend_chat_input", chatTarget.toString())
         );
         inventory.setItem(
            8,
            this.actionItem(
               Material.LIME_CONCRETE, "§a送信", List.of("§7" + (draft.isBlank() ? "本文を入力してください" : "クリックで送信")), "friend_chat_send", chatTarget.toString()
            )
         );
         inventory.setItem(15, this.actionItem(Material.BARRIER, "§cチャット欄を閉じる", List.of("§7通知ボックスに戻る"), "friend_chat_close", chatTarget.toString()));
      } else {
         List<ItemStack> notifications = this.notificationItems(player);
         int[] slots = new int[]{6, 7, 8, 15, 16, 17, 24, 25, 26};
         inventory.setItem(6, this.named(Material.PAPER, "§b通知ボックス", List.of("§7申請とチャット通知")));
         if (notifications.isEmpty()) {
            inventory.setItem(15, this.named(Material.GRAY_STAINED_GLASS_PANE, "§7通知はありません", List.of()));
         } else {
            for (int i = 0; i < Math.min(notifications.size(), slots.length - 1); i++) {
               inventory.setItem(slots[i + 1], notifications.get(i));
            }
         }
      }
   }

   private List<ItemStack> notificationItems(Player player) {
      List<ItemStack> items = new ArrayList<>();

      for (UUID requesterId : this.getUuidSet(player.getUniqueId(), "requests")) {
         OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
         items.add(
            this.actionItem(Material.EMERALD, "§e" + this.safePlayerName(requester) + " から申請", List.of("§7クリックで承認"), "friend_accept", requesterId.toString())
         );
      }

      for (String message : this.getPlayerSection(player.getUniqueId()).getStringList("offline-messages")) {
         items.add(this.named(Material.MAP, "§bフレンドチャット", List.of("§7" + message)));
      }

      return items;
   }

   private void fillStatusBox(Player player, Inventory inventory) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      inventory.setItem(
         36,
         this.named(
            Material.EXPERIENCE_BOTTLE,
            "§bMFL / MFLランク",
            List.of("§7MFL: " + this.getMfl(player.getUniqueId()), "§7ランク: " + this.getMflRank(player.getUniqueId()))
         )
      );
      inventory.setItem(37, this.named(Material.EMERALD, "§a所持MP", List.of("§7" + this.formatNumber(this.getEmeralds(player.getUniqueId())) + "MP")));
      inventory.setItem(38, this.named(Material.EMERALD_BLOCK, "§a総獲得MP", List.of("§7" + this.formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP")));
      inventory.setItem(39, this.named(Material.CLOCK, "§e総プレイ時間", List.of("§7" + this.formatPlayTime(section.getInt("total-minutes", 0)))));
      inventory.setItem(40, this.named(Material.CAMPFIRE, "§6総プレイ回数", List.of("§7".toString() + section.getInt("total-play-count", 0))));
      inventory.setItem(41, this.named(Material.TRADER_LLAMA_SPAWN_EGG, "§e総取引回数", List.of("§7".toString() + section.getInt("total-trades", 0))));
      inventory.setItem(
         42,
         this.named(
            Material.IRON_PICKAXE,
            "§9破壊 / 設置",
            List.of("§7破壊: " + section.getInt("total-blocks-broken", 0), "§7設置: " + section.getInt("total-blocks-placed", 0))
         )
      );
      inventory.setItem(43, this.named(Material.IRON_SWORD, "§c総モブ討伐数", List.of("§7".toString() + section.getInt("total-mob-kills", 0))));
      inventory.setItem(
         44,
         this.named(
            Material.COMPASS,
            "§b現在地",
            List.of(
               "§7" + player.getWorld().getName(),
               "§7X " + player.getLocation().getBlockX() + " Y " + player.getLocation().getBlockY() + " Z " + player.getLocation().getBlockZ()
            )
         )
      );
   }

   private void openDetailedStatusUi(Player player) {
      this.openStatusUi(player, "progress:0");
   }

   private void openStatusUi(Player player, String tab) {
      this.syncAdvancementState(player);
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text("§2Mifron Status"));
      this.fillStatusTabs(inventory, tab);
      if (tab.startsWith("progress")) {
         this.fillProgressTab(player, inventory, this.tabPage(tab, "progress"));
      } else if (tab.startsWith("kills")) {
         this.fillKillsTab(player, inventory, this.tabPage(tab, "kills"));
      } else if (tab.startsWith("titles")) {
         this.fillTitlesTab(player, inventory, this.tabPage(tab, "titles"));
      } else if ("reincarnation".equals(tab)) {
         this.fillReincarnationTab(player, inventory);
      } else {
         this.fillProgressTab(player, inventory, 0);
      }

      inventory.setItem(53, this.actionItem(Material.ARROW, "§f戻る", List.of("§7フレンド画面に戻る"), "friend_status_back", null));
      this.fillEmptyGuiSlots(inventory);
      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Status", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleStatusUiClick(player, item))) {
         return;
      }
      player.openInventory(inventory);
   }

   private void fillStatusTabs(Inventory inventory, String activeTab) {
      String normalizedTab = this.activeStatusTab(activeTab);

      inventory.setItem(0, switch (normalizedTab) {
         case "reincarnation" -> this.named(Material.NETHER_STAR, "§6転生", List.of());
         case "titles" -> this.named(Material.NAME_TAG, "§6称号", List.of());
         case "kills" -> this.named(Material.ZOMBIE_SPAWN_EGG, "§6討伐", List.of());
         default -> this.named(Material.BOOK, "§6進捗", List.of());
      });
   }

   private String activeStatusTab(String activeTab) {
      if (activeTab == null) {
         return "progress";
      } else if (activeTab.startsWith("progress")) {
         return "progress";
      } else if (activeTab.startsWith("titles")) {
         return "titles";
      } else if (activeTab.startsWith("kills")) {
         return "kills";
      } else {
         return "reincarnation".equals(activeTab) ? "reincarnation" : "progress";
      }
   }

   private void openQuestUi(Player player, String tab) {
      this.questService.ensurePeriods(player);
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text(QUEST_UI_TITLE));
      if (tab == null || "categories".equals(tab)) {
         this.fillQuestCategoryTab(inventory);
      } else {
         this.fillQuestListTab(player, inventory, this.questTypeFromTab(tab));
      }
      inventory.setItem(53, this.actionItem(Material.BARRIER, "§c閉じる", List.of(), "quest_close", null));
      this.fillEmptyGuiSlots(inventory);
      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Quests", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleQuestUiClick(player, item))) {
         return;
      }
      player.openInventory(inventory);
   }

   private void fillQuestCategoryTab(Inventory inventory) {
      inventory.setItem(20, this.actionItem(Material.CLOCK, "§bデイリー", List.of("§7毎日5件抽選 + 完全達成"), "quest_category", QuestType.DAILY.key()));
      inventory.setItem(21, this.actionItem(Material.WRITABLE_BOOK, "§bウィークリー", List.of("§7毎週5件抽選 + 完全達成"), "quest_category", QuestType.WEEKLY.key()));
      inventory.setItem(23, this.actionItem(Material.MAP, "§bマンスリー", List.of("§710件固定表示"), "quest_category", QuestType.MONTHLY.key()));
      inventory.setItem(24, this.actionItem(Material.NETHER_STAR, "§dスペシャル", List.of("§7条件達成で解放"), "quest_category", QuestType.SPECIAL.key()));
   }

   private void fillQuestListTab(Player player, Inventory inventory, QuestType type) {
      this.questService.ensurePeriods(player);
      inventory.setItem(45, this.named(Material.PAPER, "§e" + type.label() + "クエスト", List.of("§7残り時間: " + this.questService.remainingTime(type))));
      inventory.setItem(48, this.actionItem(Material.ARROW, "§fカテゴリに戻る", List.of("§7クエストカテゴリ"), "quest_home", null));
      int slot = 18;

      for (QuestDefinition definition : this.questService.visibleQuests(player, type)) {
         if (slot >= 45) {
            break;
         }

         inventory.setItem(slot++, this.questItem(player, definition));
      }
   }

   private ItemStack questItem(Player player, QuestDefinition definition) {
      boolean unlocked = this.questService.isUnlocked(player, definition);
      if (!unlocked) {
         return this.actionItem(Material.GRAY_STAINED_GLASS_PANE, "§8？？？", List.of("§7未解放スペシャル"), "locked_quest", definition.id());
      }

      int progress = this.questService.progress(player, definition);
      boolean completed = this.questService.isCompleted(player, definition);
      boolean claimed = this.questService.isClaimed(player, definition);
      List<String> lore = new ArrayList<>();
      lore.add("§7条件: " + definition.condition());
      lore.add("§7現在進捗: " + Math.min(progress, definition.required()));
      lore.add("§7必要数: " + definition.required());
      lore.add("§7基礎報酬MP: " + this.formatNumber(definition.baseReward()));
      lore.add("§7転生補正後MP: " + this.formatNumber(this.questService.effectiveReward(player, definition)));
      lore.add("§7残り時間: " + this.questService.remainingTime(definition.type()));
      lore.add((completed ? "§a" : "§e") + "達成状態: " + (completed ? "達成済み" : "未達成"));
      lore.add((claimed ? "§a" : "§6") + "受取状態: " + (claimed ? "受取済み" : "未受取"));
      if (completed && !claimed) {
         lore.add("§eクリックで受取");
      }

      String color = claimed ? "§8" : (completed ? "§6" : "§f");
      ItemStack item = this.actionItem(
         claimed ? Material.LIME_STAINED_GLASS_PANE : definition.icon(),
         color + definition.id() + " " + definition.name(),
         lore,
         "quest_claim",
         definition.id()
      );
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(completed && !claimed);
      item.setItemMeta(meta);
      return item;
   }

   private QuestType questTypeFromTab(String tab) {
      String key = tab.startsWith("quests:") ? tab.substring("quests:".length()).toLowerCase(Locale.ROOT) : tab.toLowerCase(Locale.ROOT);

      for (QuestType type : QuestType.values()) {
         if (type.key().equals(key)) {
            return type;
         }
      }

      return QuestType.DAILY;
   }

   private void fillStatusTab(Player player, Inventory inventory) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      inventory.setItem(
         10,
         this.named(
            Material.EXPERIENCE_BOTTLE,
            "§bMFL / MFLランク",
            List.of("§7MFL: " + this.getMfl(player.getUniqueId()), "§7ランク: " + this.getMflRank(player.getUniqueId()))
         )
      );
      inventory.setItem(11, this.named(Material.EMERALD, "§a所持MP", List.of("§7" + this.formatNumber(this.getEmeralds(player.getUniqueId())) + "MP")));
      inventory.setItem(12, this.named(Material.EMERALD_BLOCK, "§a総獲得MP", List.of("§7" + this.formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP")));
      inventory.setItem(13, this.named(Material.CLOCK, "§e総プレイ時間", List.of("§7" + this.formatPlayTime(section.getInt("total-minutes", 0)))));
      inventory.setItem(
         14,
         this.named(
            Material.CAMPFIRE,
            "§6総プレイ回数 / 連続ログイン",
            List.of("§7総プレイ回数: " + section.getInt("total-play-count", 0), "§7連続ログイン: " + section.getInt("login-streak", 0) + "日")
         )
      );
      inventory.setItem(19, this.named(Material.IRON_SWORD, "§c総モブ討伐数", List.of("§7".toString() + section.getInt("total-mob-kills", 0))));
      inventory.setItem(20, this.named(Material.IRON_PICKAXE, "§9総ブロック破壊数", List.of("§7".toString() + section.getInt("total-blocks-broken", 0))));
      inventory.setItem(21, this.named(Material.GRASS_BLOCK, "§a総ブロック設置数", List.of("§7".toString() + section.getInt("total-blocks-placed", 0))));
      inventory.setItem(22, this.named(Material.TRADER_LLAMA_SPAWN_EGG, "§e総取引回数", List.of("§7".toString() + section.getInt("total-trades", 0))));
   }

   private void fillProgressTab(Player player, Inventory inventory, int page) {
      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      List<Advancement> advancements = this.trackableAdvancements();
      int pageSize = 27;
      int maxPage = Math.max(0, (advancements.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      inventory.setItem(
         45, this.named(Material.WRITABLE_BOOK, "§d達成進捗数", List.of("§7".toString() + this.countCompletedAdvancements(player) + "/" + advancements.size()))
      );
      inventory.setItem(49, this.named(Material.PAPER, "§eページ", List.of("§7".toString() + (safePage + 1) + "/" + (maxPage + 1))));
      if (safePage > 0) {
         inventory.setItem(48, this.actionItem(Material.ARROW, "§f前のページ", List.of("§7ページ " + safePage + " へ"), "progress_page", String.valueOf(safePage - 1)));
      }

      if (safePage < maxPage) {
         inventory.setItem(
            50, this.actionItem(Material.ARROW, "§f次のページ", List.of("§7ページ " + (safePage + 2) + " へ"), "progress_page", String.valueOf(safePage + 1))
         );
      }

      int slot = 9;
      int from = safePage * pageSize;
      int to = Math.min(advancements.size(), from + pageSize);

      for (Advancement advancement : advancements.subList(from, to)) {
         AdvancementDisplay display = advancement.getDisplay();
         boolean done = completed.contains(advancement.getKey().toString());
         Material icon = display != null && display.icon() != null ? display.icon().getType() : Material.PAPER;
         inventory.setItem(slot++, this.advancementItem(advancement, icon, done));
      }
   }

   private int tabPage(String tab, String prefix) {
      String marker = prefix + ":";
      return !tab.startsWith(marker) ? 0 : this.parsePositiveInt(tab.substring(marker.length()), 0);
   }

   private void fillReincarnationTab(Player player, Inventory inventory) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      int next = section.getInt("reincarnations", 0) + 1;
      int requiredEmeralds = this.safeMultiply(10000, next);
      int requiredLevel = Math.min(1000, 20 + Math.max(0, next) * 10);
      inventory.setItem(20, this.named(Material.EXPERIENCE_BOTTLE, "§b転生回数", List.of("§7".toString() + section.getInt("reincarnations", 0))));
      inventory.setItem(
         22,
         this.actionItem(
            Material.NETHER_STAR,
            "§d転生する",
            List.of(
               "§7必要: " + this.formatNumber(requiredEmeralds) + "MP / Lv" + requiredLevel,
               "§7現在: " + this.formatNumber(this.getEmeralds(player.getUniqueId())) + "MP / Lv" + player.getLevel(),
               "§eクリックで転生"
            ),
            "reincarnate_now",
            null
         )
      );
      inventory.setItem(24, this.named(Material.GOLD_INGOT, "§6転生ボーナス", List.of("§7+" + this.getReincarnationBonus(player.getUniqueId()) + "%")));
   }

   private void fillTitlesTab(Player player, Inventory inventory, int page) {
      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      String selected = this.selectedTitle(player);
      List<Entry<String, Mifron.TitleDefinition>> titles = this.titleDefinitions().entrySet().stream().sorted(Entry.comparingByKey()).toList();
      int pageSize = 27;
      int maxPage = Math.max(0, (titles.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      long unlockedCount = titles.stream().filter(entryx -> this.hasTitle(completed, (Mifron.TitleDefinition)entryx.getValue())).count();
      inventory.setItem(45, this.named(Material.NAME_TAG, "§6取得済み称号", List.of("§7".toString() + unlockedCount + "/" + titles.size())));
      inventory.setItem(46, this.actionItem(Material.BARRIER, "§f称号なし", List.of(selected.isBlank() ? "§6選択中" : "§7クリックで称号を外す"), "clear_title", null));
      inventory.setItem(49, this.named(Material.PAPER, "§eページ", List.of("§7".toString() + (safePage + 1) + "/" + (maxPage + 1))));
      if (safePage > 0) {
         inventory.setItem(48, this.actionItem(Material.ARROW, "§f前のページ", List.of("§7ページ " + safePage + " へ"), "titles_page", String.valueOf(safePage - 1)));
      }

      if (safePage < maxPage) {
         inventory.setItem(
            50, this.actionItem(Material.ARROW, "§f次のページ", List.of("§7ページ " + (safePage + 2) + " へ"), "titles_page", String.valueOf(safePage + 1))
         );
      }

      int slot = 9;
      int from = safePage * pageSize;
      int to = Math.min(titles.size(), from + pageSize);

      for (Entry<String, Mifron.TitleDefinition> entry : titles.subList(from, to)) {
         boolean unlocked = this.hasTitle(completed, entry.getValue());
         String title = unlocked ? entry.getKey() : "???";
         inventory.setItem(
            slot++,
            this.actionItem(
               entry.getValue().icon(),
               (unlocked ? "§a" : "§8") + title,
               List.of(unlocked ? "§a取得済" : "§7未取得", entry.getKey().equals(selected) ? "§6選択中" : "§7クリックで選択"),
               unlocked ? "select_title" : "locked_title",
               entry.getKey()
            )
         );
      }
   }

   private boolean hasTitle(Set<String> completed, Mifron.TitleDefinition definition) {
      return !definition.requiredAdvancements().isEmpty()
         ? definition.requiredAdvancements().stream().allMatch(requirement -> this.matchesTitleRequirement(completed, requirement))
         : completed.size() >= this.countTrackableAdvancements() && this.countTrackableAdvancements() > 0;
   }

   private boolean matchesTitleRequirement(Set<String> completed, String requirement) {
      for (String option : requirement.split("\\|")) {
         String key = option.trim();
         if (!key.isBlank() && (completed.contains(key) || completed.contains("minecraft:" + key))) {
            return true;
         }
      }

      return false;
   }

   private void notifyUnlockedTitles(Player player, Set<String> completed) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      List<String> newlyUnlocked = new ArrayList<>();

      for (Entry<String, Mifron.TitleDefinition> entry : this.titleDefinitions().entrySet()) {
         String title = entry.getKey();
         if (!notified.contains(title) && this.hasTitle(completed, entry.getValue())) {
            notified.add(title);
            newlyUnlocked.add(title);
         }
      }

      if (!newlyUnlocked.isEmpty()) {
         section.set("unlocked-titles", new ArrayList<>(notified));
         this.queueDataSave();
         newlyUnlocked.stream().sorted().forEach(titlex -> player.sendMessage("§6称号を獲得しました: §e" + titlex));
         player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
      }
   }

   public void unlockTitle(Player player, String title) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      if (!notified.contains(title)) {
         notified.add(title);
         section.set("unlocked-titles", new ArrayList<>(notified));
         this.queueDataSave();
         player.sendMessage("§6称号を獲得しました：§e" + title);
         player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
      }
   }

   private void fillKillsTab(Player player, Inventory inventory, int page) {
      Set<String> killed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("killed-mobs"));
      List<EntityType> mobs = this.killableMobTypes();
      int pageSize = 27;
      int maxPage = Math.max(0, (mobs.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      long killedCount = mobs.stream().filter(typex -> killed.contains(typex.name())).count();
      inventory.setItem(45, this.named(Material.IRON_SWORD, "§c討伐済みMob", List.of("§7".toString() + killedCount + "/" + mobs.size())));
      inventory.setItem(49, this.named(Material.PAPER, "§eページ", List.of("§7".toString() + (safePage + 1) + "/" + (maxPage + 1))));
      if (safePage > 0) {
         inventory.setItem(48, this.actionItem(Material.ARROW, "§f前のページ", List.of("§7ページ " + safePage + " へ"), "kills_page", String.valueOf(safePage - 1)));
      }

      if (safePage < maxPage) {
         inventory.setItem(
            50, this.actionItem(Material.ARROW, "§f次のページ", List.of("§7ページ " + (safePage + 2) + " へ"), "kills_page", String.valueOf(safePage + 1))
         );
      }

      int slot = 9;
      int from = safePage * pageSize;
      int to = Math.min(mobs.size(), from + pageSize);

      for (EntityType type : mobs.subList(from, to)) {
         Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
         boolean done = killed.contains(type.name());
         inventory.setItem(
            slot++,
            this.statusItem(
               done ? egg : Material.GRAY_STAINED_GLASS_PANE, (done ? "§a" : "§8") + this.mobDisplayName(type), List.of(done ? "§a討伐済" : "§7未討伐"), done
            )
         );
      }
   }

   private List<EntityType> killableMobTypes() {
      List<EntityType> mobs = new ArrayList<>();

      for (EntityType type : EntityType.values()) {
         if (type.isAlive() && type != EntityType.PLAYER && type != EntityType.UNKNOWN && Material.matchMaterial(type.name() + "_SPAWN_EGG") != null) {
            mobs.add(type);
         }
      }

      mobs.sort((first, second) -> {
         int difficulty = Integer.compare(this.killDifficulty(first), this.killDifficulty(second));
         return difficulty != 0 ? difficulty : first.name().compareToIgnoreCase(second.name());
      });
      return mobs;
   }

   private int killDifficulty(EntityType type) {
      return switch (type) {
         case CAVE_SPIDER, ENDERMITE, SILVERFISH, SLIME, ZOMBIE, SKELETON, SPIDER, DROWNED, HUSK, STRAY, PHANTOM -> 3;
         case DOLPHIN, GOAT, LLAMA, TRADER_LLAMA, BAT, FOX, FROG, OCELOT, PARROT, TURTLE, WOLF, CAT, PANDA, CAMEL, HORSE, DONKEY, MULE, SNIFFER -> 2;
         case GUARDIAN, MAGMA_CUBE, CREEPER, WITCH, PILLAGER, VINDICATOR, EVOKER, VEX, BLAZE, BOGGED, BREEZE -> 4;
         default -> 3;
         case ZOMBIFIED_PIGLIN, ENDERMAN, PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN, WITHER_SKELETON, ELDER_GUARDIAN, RAVAGER, SHULKER -> 5;
         case CHICKEN, COW, PIG, SHEEP, RABBIT, COD, SALMON, TROPICAL_FISH, PUFFERFISH, SQUID, GLOW_SQUID -> 1;
         case WARDEN, WITHER, ENDER_DRAGON -> 6;
      };
   }

   private String mobDisplayName(EntityType type) {
      return switch (type) {
         case BEE -> "ミツバチ";
         case CAVE_SPIDER -> "洞窟グモ";
         case DOLPHIN -> "イルカ";
         case ENDERMITE -> "エンダーマイト";
         case GOAT -> "ヤギ";
         case GUARDIAN -> "ガーディアン";
         case LLAMA -> "ラマ";
         case MAGMA_CUBE -> "マグマキューブ";
         case POLAR_BEAR -> "シロクマ";
         case SILVERFISH -> "シルバーフィッシュ";
         case SLIME -> "スライム";
         case TRADER_LLAMA -> "行商人のラマ";
         case ZOMBIFIED_PIGLIN -> "ゾンビピグリン";
         case CHICKEN -> "ニワトリ";
         case COW -> "ウシ";
         case PIG -> "ブタ";
         case SHEEP -> "ヒツジ";
         case RABBIT -> "ウサギ";
         case COD -> "タラ";
         case SALMON -> "サケ";
         case TROPICAL_FISH -> "熱帯魚";
         case PUFFERFISH -> "フグ";
         case SQUID -> "イカ";
         case GLOW_SQUID -> "ヒカリイカ";
         case BAT -> "コウモリ";
         case FOX -> "キツネ";
         case FROG -> "カエル";
         case OCELOT -> "ヤマネコ";
         case PARROT -> "オウム";
         case TURTLE -> "カメ";
         case WOLF -> "オオカミ";
         case CAT -> "ネコ";
         case PANDA -> "パンダ";
         case CAMEL -> "ラクダ";
         case HORSE -> "ウマ";
         case DONKEY -> "ロバ";
         case MULE -> "ラバ";
         case SNIFFER -> "スニッファー";
         case ZOMBIE -> "ゾンビ";
         case SKELETON -> "スケルトン";
         case SPIDER -> "クモ";
         case DROWNED -> "ドラウンド";
         case HUSK -> "ハスク";
         case STRAY -> "ストレイ";
         case PHANTOM -> "ファントム";
         case CREEPER -> "クリーパー";
         case WITCH -> "ウィッチ";
         case PILLAGER -> "ピリジャー";
         case VINDICATOR -> "ヴィンディケーター";
         case EVOKER -> "エヴォーカー";
         case VEX -> "ヴェックス";
         case BLAZE -> "ブレイズ";
         case BOGGED -> "ボグド";
         case BREEZE -> "ブリーズ";
         case ENDERMAN -> "エンダーマン";
         case PIGLIN -> "ピグリン";
         case PIGLIN_BRUTE -> "ピグリンブルート";
         case HOGLIN -> "ホグリン";
         case ZOGLIN -> "ゾグリン";
         case WITHER_SKELETON -> "ウィザースケルトン";
         case ELDER_GUARDIAN -> "エルダーガーディアン";
         case RAVAGER -> "ラヴェジャー";
         case SHULKER -> "シュルカー";
         case WARDEN -> "ウォーデン";
         case WITHER -> "ウィザー";
         case ENDER_DRAGON -> "エンダードラゴン";
         case ALLAY -> "アレイ";
         case ARMADILLO -> "アルマジロ";
         case AXOLOTL -> "ウーパールーパー";
         case GHAST -> "ガスト";
         case ILLUSIONER -> "イリュージョナー";
         case IRON_GOLEM -> "アイアンゴーレム";
         case MOOSHROOM -> "ムーシュルーム";
         case SKELETON_HORSE -> "スケルトンホース";
         case SNOW_GOLEM -> "スノウゴーレム";
         case STRIDER -> "ストライダー";
         case TADPOLE -> "オタマジャクシ";
         case VILLAGER -> "村人";
         case WANDERING_TRADER -> "行商人";
         case ZOMBIE_HORSE -> "ゾンビホース";
         case ZOMBIE_VILLAGER -> "村人ゾンビ";
         default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
      };
   }

   private ItemStack statusItem(Material material, String name, List<String> lore, boolean glint) {
      ItemStack item = this.named(material, name, lore);
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(glint);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack advancementItem(Advancement advancement, Material icon, boolean done) {
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();
      ItemStack item = new ItemStack(done ? icon : Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = item.getItemMeta();
      Component title = (Component)(display == null ? Component.text(advancement.getKey().getKey()) : display.title());
      meta.displayName(title.color(done ? this.advancementFrameColor(frame) : NamedTextColor.DARK_GRAY));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.text(done ? "達成済" : "未達成", done ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
      if (display != null && display.description() != null) {
         lore.add(display.description().color(done ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));
      }

      meta.lore(lore);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      meta.setEnchantmentGlintOverride(done);
      item.setItemMeta(meta);
      return item;
   }

   private NamedTextColor advancementFrameColor(Frame frame) {
      return switch (frame) {
         case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
         case GOAL -> NamedTextColor.GOLD;
         default -> NamedTextColor.GREEN;
      };
   }

   private String formatPlayTime(int totalMinutes) {
      int hours = totalMinutes / 60;
      int minutes = totalMinutes % 60;
      return hours + "時間" + minutes + "分";
   }

   private int countCompletedAdvancements(Player player) {
      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      int count = 0;

      for (Advancement advancement : this.trackableAdvancements()) {
         if (this.shouldTrackAdvancement(advancement) && completed.contains(advancement.getKey().toString())) {
            count++;
         }
      }

      return count;
   }

   private int countTrackableAdvancements() {
      return this.trackableAdvancements().size();
   }

   private List<Advancement> trackableAdvancements() {
      List<Advancement> advancements = new ArrayList<>();
      Iterator<Advancement> iterator = Bukkit.advancementIterator();

      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (this.shouldTrackAdvancement(advancement)) {
            advancements.add(advancement);
         }
      }

      advancements.sort((first, second) -> {
         int difficulty = Integer.compare(this.advancementDifficulty(first), this.advancementDifficulty(second));
         if (difficulty != 0) {
            return difficulty;
         }

         int category = Integer.compare(this.advancementCategoryOrder(first), this.advancementCategoryOrder(second));
         if (category != 0) {
            return category;
         }

         int path = Integer.compare(this.advancementPathOrder(first), this.advancementPathOrder(second));
         if (path != 0) {
            return path;
         }

         int frame = Integer.compare(this.advancementFrameOrder(first), this.advancementFrameOrder(second));
         return frame != 0 ? frame : first.getKey().toString().compareToIgnoreCase(second.getKey().toString());
      });
      return advancements;
   }

   private int advancementFrameOrder(Advancement advancement) {
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();

      return switch (frame) {
         case CHALLENGE -> 2;
         case GOAL -> 1;
         case TASK -> 0;
         default -> throw new MatchException(null, null);
      };
   }

   private int advancementCategoryOrder(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String category = key.contains("/") ? key.substring(0, key.indexOf(47)) : key;

      return switch (category) {
         case "story" -> 0;
         case "nether" -> 1;
         case "end" -> 2;
         case "adventure" -> 3;
         case "husbandry" -> 4;
         default -> 9;
      };
   }

   private int advancementPathOrder(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String category = key.contains("/") ? key.substring(0, key.indexOf(47)) : key;
      String path = key.contains("/") ? key.substring(key.indexOf(47) + 1) : key;

      List<String> ordered = switch (category) {
         case "story" -> List.of(
            "root",
            "mine_stone",
            "upgrade_tools",
            "smelt_iron",
            "obtain_armor",
            "lava_bucket",
            "iron_tools",
            "deflect_arrow",
            "form_obsidian",
            "mine_diamond",
            "enter_the_nether",
            "shiny_gear",
            "enchant_item",
            "cure_zombie_villager",
            "follow_ender_eye",
            "enter_the_end"
         );
         case "nether" -> List.of(
            "root",
            "return_to_sender",
            "find_bastion",
            "obtain_ancient_debris",
            "fast_travel",
            "find_fortress",
            "obtain_crying_obsidian",
            "distract_piglin",
            "ride_strider",
            "uneasy_alliance",
            "loot_bastion",
            "use_lodestone",
            "netherite_armor",
            "get_wither_skull",
            "obtain_blaze_rod",
            "charge_respawn_anchor",
            "ride_strider_in_overworld_lava",
            "explore_nether",
            "summon_wither",
            "brew_potion",
            "create_beacon",
            "all_potions",
            "create_full_beacon",
            "all_effects"
         );
         case "end" -> List.of(
            "root", "kill_dragon", "dragon_egg", "enter_end_gateway", "respawn_dragon", "dragon_breath", "find_end_city", "elytra", "levitate"
         );
         case "adventure" -> List.of(
            "root",
            "voluntary_exile",
            "spyglass_at_parrot",
            "kill_a_mob",
            "trade",
            "trim_with_any_armor_pattern",
            "honey_block_slide",
            "ol_betsy",
            "lightning_rod_with_villager_no_fire",
            "fall_from_world_height",
            "avoid_vibration",
            "sleep_in_bed",
            "hero_of_the_village",
            "spyglass_at_ghast",
            "throw_trident",
            "shoot_arrow",
            "kill_all_mobs",
            "totem_of_undying",
            "summon_iron_golem",
            "trade_at_world_height",
            "two_birds_one_arrow",
            "whos_the_pillager_now",
            "arbalistic",
            "adventuring_time",
            "play_jukebox_in_meadows",
            "walk_on_powder_snow_with_leather_boots",
            "spyglass_at_dragon",
            "very_very_frightening",
            "sniper_duel",
            "bullseye"
         );
         case "husbandry" -> List.of(
            "root",
            "safely_harvest_honey",
            "breed_an_animal",
            "ride_a_boat_with_a_goat",
            "tame_an_animal",
            "make_a_sign_glow",
            "fishy_business",
            "silk_touch_nest",
            "plant_seed",
            "wax_on",
            "bred_all_animals",
            "allay_deliver_item_to_player",
            "complete_catalogue",
            "tactical_fishing",
            "balanced_diet",
            "obtain_netherite_hoe",
            "axolotl_in_a_bucket",
            "wax_off",
            "kill_axolotl_target",
            "frogspawn",
            "froglights",
            "allay_deliver_cake_to_note_block",
            "leash_all_frog_variants",
            "feed_snifflet",
            "plant_any_sniffer_seed"
         );
         default -> List.of("root");
      };
      int index = ordered.indexOf(path);
      if (index >= 0) {
         return index;
      } else {
         return path.equals("root") ? 0 : 1000 + this.advancementDifficulty(advancement);
      }
   }

   private int advancementDifficulty(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String path = key.contains("/") ? key.substring(key.indexOf(47) + 1) : key;
      List<String> ordered = List.of(
         "root",
         "mine_stone",
         "upgrade_tools",
         "smelt_iron",
         "obtain_armor",
         "iron_tools",
         "deflect_arrow",
         "lava_bucket",
         "form_obsidian",
         "mine_diamond",
         "shiny_gear",
         "enchant_item",
         "enter_the_nether",
         "follow_ender_eye",
         "enter_the_end",
         "kill_a_mob",
         "shoot_arrow",
         "ol_betsy",
         "trade",
         "sleep_in_bed",
         "plant_seed",
         "breed_an_animal",
         "tame_an_animal",
         "fishy_business",
         "safely_harvest_honey",
         "wax_on",
         "wax_off",
         "silk_touch_nest",
         "make_a_sign_glow",
         "ride_a_boat_with_a_goat",
         "voluntary_exile",
         "honey_block_slide",
         "throw_trident",
         "totem_of_undying",
         "summon_iron_golem",
         "cure_zombie_villager",
         "return_to_sender",
         "find_fortress",
         "obtain_blaze_rod",
         "brew_potion",
         "distract_piglin",
         "obtain_crying_obsidian",
         "ride_strider",
         "use_lodestone",
         "find_bastion",
         "loot_bastion",
         "obtain_ancient_debris",
         "kill_dragon",
         "dragon_egg",
         "enter_end_gateway",
         "dragon_breath",
         "find_end_city",
         "elytra",
         "respawn_dragon",
         "spyglass_at_parrot",
         "spyglass_at_ghast",
         "lightning_rod_with_villager_no_fire",
         "walk_on_powder_snow_with_leather_boots",
         "play_jukebox_in_meadows",
         "avoid_vibration",
         "fall_from_world_height",
         "trade_at_world_height",
         "hero_of_the_village",
         "whos_the_pillager_now",
         "two_birds_one_arrow",
         "sniper_duel",
         "bullseye",
         "arbalistic",
         "very_very_frightening",
         "kill_all_mobs",
         "adventuring_time",
         "tactical_fishing",
         "axolotl_in_a_bucket",
         "kill_axolotl_target",
         "frogspawn",
         "froglights",
         "allay_deliver_item_to_player",
         "allay_deliver_cake_to_note_block",
         "complete_catalogue",
         "bred_all_animals",
         "balanced_diet",
         "obtain_netherite_hoe",
         "feed_snifflet",
         "plant_any_sniffer_seed",
         "leash_all_frog_variants",
         "fast_travel",
         "uneasy_alliance",
         "get_wither_skull",
         "summon_wither",
         "create_beacon",
         "create_full_beacon",
         "netherite_armor",
         "charge_respawn_anchor",
         "ride_strider_in_overworld_lava",
         "explore_nether",
         "all_potions",
         "all_effects",
         "levitate"
      );
      int explicit = ordered.indexOf(path);
      if (explicit >= 0) {
         return explicit;
      }

      int score = key.split("/").length * 10 + key.length();
      if (key.contains("root")) {
         score -= 100;
      }

      for (String token : List.of("obtain", "mine", "smelt", "breed", "tame", "trade", "kill", "summon", "all", "complete", "netherite", "elytra")) {
         if (key.contains(token)) {
            score += switch (token) {
               case "obtain", "mine", "smelt" -> 5;
               case "breed", "tame", "trade" -> 15;
               case "kill", "summon" -> 25;
               case "all", "complete" -> 60;
               case "netherite", "elytra" -> 80;
               default -> 0;
            };
         }
      }

      return score;
   }

   String formatNumber(int value) {
      return String.format(Locale.US, "%,d", value);
   }

   private String formatDateTime(long millis) {
      return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
   }

   private String safePlayerName(OfflinePlayer player) {
      return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
   }

   private boolean isSafeConfigKey(String key) {
      return key != null && SAFE_CONFIG_KEY_PATTERN.matcher(key).matches();
   }

   private void sendInvalidConfigKeyMessage(CommandSender sender, String label) {
      sender.sendMessage("§c" + label + " は英数字、ハイフン、アンダースコアのみで1-32文字にしてください。");
   }

   private OfflinePlayer resolveKnownPlayer(CommandSender sender, String name) {
      if (name != null && !name.isBlank() && name.length() <= 16) {
         OfflinePlayer player = Bukkit.getOfflinePlayer(name);
         if (!player.isOnline() && !player.hasPlayedBefore()) {
            sender.sendMessage("§cプレイヤーが見つかりません: " + name);
            return null;
         } else {
            return player;
         }
      } else {
         sender.sendMessage("§cプレイヤー名が不正です。");
         return null;
      }
   }

   void openTeleportUi(Player player) {
      Inventory inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.DROPPER, Component.text("§5Mifron Teleporter"));
      inventory.setItem(0, this.actionItem(Material.GRASS_BLOCK, "§a中央広場", List.of("§7初期スポーンへ移動"), "teleport", "hub"));
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers != null) {
         int slot = 1;

         for (String key : servers.getKeys(false)) {
            if (!this.isSafeConfigKey(key)) {
               this.getLogger().warning("Ignoring unsafe server key in config.yml: " + key);
            } else {
               if (slot >= 9) {
                  break;
               }

               inventory.setItem(
                  slot++, this.actionItem(this.serverIconMaterial("servers." + key), "§d" + key, List.of("§7クリックで移動"), "teleport", "servers." + key)
               );
            }
         }
      }

      this.fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   void openServerPortalTargetUi(Player player, String portalKey) {
      Inventory inventory = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.DROPPER, Component.text("§5Mifron Teleporter"));
      inventory.setItem(0, this.named(Material.ENDER_EYE, "§dポータル移動先設定", List.of("§7このポータルに触れた時の移動先を選択")));
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers != null) {
         int slot = 1;

         for (String key : servers.getKeys(false)) {
            if (!this.isSafeConfigKey(key)) {
               this.getLogger().warning("Ignoring unsafe server key in config.yml: " + key);
            } else {
               if (slot >= 9) {
                  break;
               }

               inventory.setItem(
                  slot++,
                  this.actionItem(
                     this.serverIconMaterial("servers." + key),
                     "§d" + key,
                     List.of("§7クリックでこのポータルの移動先に設定"),
                     "server_portal_bind",
                     portalKey + "|servers." + key
                  )
               );
            }
         }
      }

      this.fillEmptyGuiSlots(inventory);
      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Teleporter", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleTeleporterUiItem(player, item))) {
         return;
      }
      player.openInventory(inventory);
   }

   private void fillEmptyGuiSlots(Inventory inventory) {
      ItemStack filler = this.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of());

      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack item = inventory.getItem(slot);
         if (item == null || item.getType() == Material.AIR) {
            inventory.setItem(slot, filler.clone());
         }
      }
   }

   private Material serverIconMaterial(String path) {
      String configured = this.getConfig().getString(path + ".icon", "ender_pearl");
      Material material = configured == null ? null : Material.matchMaterial(configured.toUpperCase(Locale.ROOT));
      return material != null && material != Material.AIR && material.isItem() ? material : Material.ENDER_PEARL;
   }

   private Material parseServerIcon(CommandSender sender, String raw) {
      Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
      if (material != null && material != Material.AIR && material.isItem()) {
         return material;
      }

      sender.sendMessage("§cアイコン素材が見つからないか、アイテムとして使えません: " + raw);
      sender.sendMessage("§7例: /mf setserver minigame diamond_sword");
      return null;
   }

   private List<String> serverIconSuggestions(String prefix) {
      String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
      List<String> preferred = List.of(
         "diamond_sword",
         "bow",
         "crossbow",
         "trident",
         "mace",
         "iron_spear",
         "grass_block",
         "compass",
         "ender_pearl",
         "ender_eye",
         "nether_star",
         "emerald",
         "diamond",
         "gold_ingot",
         "iron_pickaxe",
         "elytra"
      );
      List<String> suggestions = new ArrayList<>();

      for (String value : preferred) {
         if (value.startsWith(normalized)) {
            suggestions.add(value);
         }
      }

      if (suggestions.size() >= 20) {
         return suggestions;
      }

      for (Material material : Material.values()) {
         if (material != Material.AIR && material.isItem()) {
            String key = material.name().toLowerCase(Locale.ROOT);
            if (key.startsWith(normalized) && !suggestions.contains(key)) {
               suggestions.add(key);
               if (suggestions.size() >= 20) {
                  break;
               }
            }
         }
      }

      return suggestions;
   }

   private ItemStack named(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.lore(lore.stream().map(Component::text).toList());
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack actionItem(Material material, String name, List<String> lore, String action, String target) {
      ItemStack item = this.named(material, name, lore);
      ItemMeta meta = item.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.uiActionKey, PersistentDataType.STRING, action);
      if (target != null) {
         container.set(this.uiTargetKey, PersistentDataType.STRING, target);
      }

      item.setItemMeta(meta);
      return item;
   }

   private String getUiAction(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? (String)item.getItemMeta().getPersistentDataContainer().get(this.uiActionKey, PersistentDataType.STRING)
         : null;
   }

   private UUID getUiTarget(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         String raw = this.getUiTargetString(item);
         if (raw == null) {
            return null;
         }

         try {
            return UUID.fromString(raw);
         } catch (IllegalArgumentException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private String getUiTargetString(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? (String)item.getItemMeta().getPersistentDataContainer().get(this.uiTargetKey, PersistentDataType.STRING)
         : null;
   }

   private void handleTeleporterUiItem(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      String target = this.getUiTargetString(clicked);
      if ("teleport".equals(action) && target != null) {
         this.playUiClickSound(player);
         this.teleportToConfigLocation(player, target);
         player.closeInventory();
      } else if ("server_portal_bind".equals(action) && target != null) {
         String[] parts = target.split("\\|", 2);
         if (parts.length == 2) {
            this.serverPortalFeature.setServerPortalTarget(parts[0], parts[1]);
            this.playUiClickSound(player);
            player.sendMessage("§aサーバーポータルの移動先を設定しました。");
            player.closeInventory();
         }
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (this.isBarrelShopInventory(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               this.buyBarrelOffer(player, event.getCurrentItem(), event.getSlot(), event.getView().getTopInventory());
            }
         } else if (event.getClickedInventory() != null) {
            String title = this.inventoryTitle(event.getView().title());
            if ("§3Mifron Friends".equals(title)) {
               event.setCancelled(true);
               this.handleFriendUiClick(player, event.getCurrentItem());
            } else if ("§2Mifron Status".equals(title)) {
               event.setCancelled(true);
               this.handleStatusUiClick(player, event.getCurrentItem());
            } else if (QUEST_UI_TITLE.equals(title)) {
               event.setCancelled(true);
               this.handleQuestUiClick(player, event.getCurrentItem());
            } else if ("§5Mifron Teleporter".equals(title)) {
               event.setCancelled(true);
               this.handleTeleporterUiItem(player, event.getCurrentItem());
            } else {
               if ("§6Mifron Merchant".equals(title)) {
                  if (event.getClickedInventory() != event.getView().getTopInventory()) {
                     return;
                  }

                  event.setCancelled(true);
                  if (this.isMerchantOffer(event.getCurrentItem())) {
                     this.buyMerchantOffer(
                        player, event.getCurrentItem(), event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT
                     );
                  } else {
                     this.handleMerchantNavigation(player, event.getCurrentItem());
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (this.isBarrelShopInventory(event.getView().getTopInventory())) {
         event.setCancelled(true);
      } else if ("§6Mifron Merchant".equals(this.inventoryTitle(event.getView().title()))) {
         int topSize = event.getView().getTopInventory().getSize();

         for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onInventoryMoveItem(InventoryMoveItemEvent event) {
      if (this.isBarrelShopInventory(event.getSource()) || this.isBarrelShopInventory(event.getDestination())) {
         event.setCancelled(true);
      }
   }

   private boolean isBarrelShopInventory(Inventory inventory) {
      return inventory != null && inventory.getHolder() instanceof Barrel barrel && this.isBarrelShop(barrel.getBlock());
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player && "§6Mifron Merchant".equals(this.inventoryTitle(event.getView().title()))) {
         this.activeMerchantPages.remove(player.getUniqueId());
         UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
         if (merchantId != null && !this.activeMerchantViews.containsValue(merchantId)) {
            Entity entity = this.findEntity(merchantId);
            if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
               villager.setAI(true);
               villager.setInvulnerable(false);
            }
         }
      }
   }

   private String inventoryTitle(Component title) {
      return PlainTextComponentSerializer.plainText().serialize(title);
   }

   private Entity findEntity(UUID entityId) {
      for (World world : Bukkit.getWorlds()) {
         Entity entity = world.getEntity(entityId);
         if (entity != null) {
            return entity;
         }
      }

      return null;
   }

   private void handleFriendUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action != null) {
         this.playUiClickSound(player);
         UUID targetId = this.getUiTarget(clicked);
         switch (action) {
            case "friend_search":
               this.pendingFriendSearch.put(player.getUniqueId(), "");
               player.closeInventory();
               player.sendMessage("§e検索するユーザー名をチャットに入力してください。空入力の代わりに clear で解除します。");
               break;
            case "friend_request":
               if (targetId != null) {
                  this.sendFriendRequest(player, Bukkit.getOfflinePlayer(targetId));
                  this.openFriendUi(player);
               }
               break;
            case "friend_accept":
               if (targetId != null) {
                  this.acceptFriendRequest(player, Bukkit.getOfflinePlayer(targetId));
                  this.openFriendUi(player);
               }
               break;
            case "friend_remove":
               if (targetId != null) {
                  this.removeFriend(player, Bukkit.getOfflinePlayer(targetId));
                  this.openFriendUi(player);
               }
               break;
            case "friend_chat_open":
               if (targetId != null) {
                  this.activeFriendChatTarget.put(player.getUniqueId(), targetId);
                  this.openFriendUi(player);
               }
               break;
            case "friend_chat_input":
               if (targetId != null) {
                  this.pendingFriendChatInput.put(player.getUniqueId(), targetId);
                  player.closeInventory();
                  player.sendMessage("§b" + this.safePlayerName(Bukkit.getOfflinePlayer(targetId)) + " へ送る本文をチャットに入力してください。");
               }
               break;
            case "friend_chat_send":
               if (targetId != null) {
                  this.sendFriendChatDraft(player, Bukkit.getOfflinePlayer(targetId));
                  this.openFriendUi(player);
               }
               break;
            case "friend_chat_close":
               this.activeFriendChatTarget.remove(player.getUniqueId());
               this.friendChatDrafts.remove(player.getUniqueId());
               this.openFriendUi(player);
               break;
            case "status_tab_progress":
               this.openStatusUi(player, "progress:0");
               break;
            case "status_tab_reincarnation":
               this.openStatusUi(player, "reincarnation");
               break;
            case "status_tab_titles":
               this.openStatusUi(player, "titles:0");
               break;
            case "status_tab_kills":
               this.openStatusUi(player, "kills:0");
               break;
            case "friend_status_detail":
               this.openDetailedStatusUi(player);
         }
      }
   }

   private void handleStatusUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action != null) {
         this.playUiClickSound(player);
         switch (action) {
            case "friend_status_back":
               this.openFriendUi(player);
               break;
            case "status_tab_progress":
               this.openStatusUi(player, "progress:0");
               break;
            case "status_tab_reincarnation":
               this.openStatusUi(player, "reincarnation");
               break;
            case "status_tab_titles":
               this.openStatusUi(player, "titles:0");
               break;
            case "status_tab_kills":
               this.openStatusUi(player, "kills:0");
               break;
            case "progress_page":
               this.openStatusUi(player, "progress:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
               break;
            case "titles_page":
               this.openStatusUi(player, "titles:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
               break;
            case "kills_page":
               this.openStatusUi(player, "kills:" + this.parsePositiveInt(this.getUiTargetString(clicked), 0));
               break;
            case "reincarnate_now":
               this.tryReincarnate(player, null);
               this.openStatusUi(player, "reincarnation");
               break;
            case "select_title":
               String title = this.getUiTargetString(clicked);
               if (title != null && this.canUseTitle(player, title)) {
                  this.getPlayerSection(player.getUniqueId()).set("selected-title", title);
                  this.queueDataSave();
                  this.refreshPlayerName(player);
                  player.sendMessage("§a称号を選択しました: " + title);
                  this.openStatusUi(player, "titles:0");
               }
               break;
            case "clear_title":
               this.getPlayerSection(player.getUniqueId()).set("selected-title", null);
               this.queueDataSave();
               this.refreshPlayerName(player);
               player.sendMessage("§a称号を外しました。");
               this.openStatusUi(player, "titles:0");
         }
      }
   }

   private void handleQuestUiClick(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      if (action == null) {
         return;
      }
      this.playUiClickSound(player);
      switch (action) {
         case "quest_close":
            player.closeInventory();
            break;
         case "quest_home":
            this.openQuestUi(player, "categories");
            break;
         case "quest_category":
            this.openQuestUi(player, this.getUiTargetString(clicked));
            break;
         case "quest_claim":
            String questId = this.getUiTargetString(clicked);
            if (questId != null && this.questService.claim(player, questId)) {
               QuestDefinition definition = this.questService.definition(questId);
               this.openQuestUi(player, definition == null ? "categories" : definition.type().key());
            }
      }
   }

   BedrockUiFeature bedrockUiFeature() {
      return this.bedrockUiFeature;
   }

   void teleportToConfigLocation(Player player, String path) {
      Location location = this.readLocation(path);
      if (location == null) {
         player.sendMessage("§c移動先が未設定です: " + path);
      } else {
         player.teleport(location);
         this.playTeleportSound(player);
         player.sendMessage("§a移動しました。");
      }
   }

   Location readLocation(String path) {
      String worldName = this.getConfig().getString(path + ".world");
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      return world == null
         ? null
         : new Location(
            world,
            this.getConfig().getDouble(path + ".x"),
            this.getConfig().getDouble(path + ".y"),
            this.getConfig().getDouble(path + ".z"),
            (float)this.getConfig().getDouble(path + ".yaw"),
            (float)this.getConfig().getDouble(path + ".pitch")
         );
   }

   private void handleServerOrderCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return;
      }
      if (args.length < 3) {
         sender.sendMessage("§e/mf serverorder <server-id> <position>");
         sender.sendMessage("§7例: /mf serverorder survival 1");
         return;
      }

      String key = args[1];
      String path = "servers." + key;
      if (!this.getConfig().isConfigurationSection(path)) {
         sender.sendMessage("§cサーバーが見つかりません: " + key);
         return;
      }

      int requested;
      try {
         requested = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
         sender.sendMessage("§cposition は1以上の整数で指定してください。");
         return;
      }

      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers == null || servers.getKeys(false).isEmpty()) {
         sender.sendMessage("§c登録済みサーバーがありません。");
         return;
      }

      List<String> keys = new ArrayList<>(servers.getKeys(false));
      Map<String, Integer> originalIndex = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) {
         originalIndex.put(keys.get(i), i);
      }
      keys.sort((a, b) -> {
         int ao = this.getConfig().getInt("servers." + a + ".order", originalIndex.get(a) + 1);
         int bo = this.getConfig().getInt("servers." + b + ".order", originalIndex.get(b) + 1);
         int order = Integer.compare(ao, bo);
         return order != 0 ? order : Integer.compare(originalIndex.get(a), originalIndex.get(b));
      });

      keys.remove(key);
      int position = Math.max(1, Math.min(requested, keys.size() + 1));
      keys.add(position - 1, key);
      for (int i = 0; i < keys.size(); i++) {
         this.getConfig().set("servers." + keys.get(i) + ".order", i + 1);
      }
      this.saveConfig();

      sender.sendMessage("§aテレポート先の表示順を変更しました: " + key + " → " + position + "番目");
      sender.sendMessage("§7現在の順番: " + String.join(" → ", keys));
      if (sender instanceof Player player) {
         player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
      }
   }

   private void handleServerIconCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return;
      }
      if (args.length < 3) {
         sender.sendMessage("§e/mf servericon <server-id> <material>");
         sender.sendMessage("§7例: /mf servericon survival grass_block");
         return;
      }

      String key = args[1];
      String path = "servers." + key;
      if (!this.getConfig().isConfigurationSection(path)) {
         sender.sendMessage("§cサーバーが見つかりません: " + key);
         return;
      }

      Material icon = Material.matchMaterial(args[2]);
      if (icon == null || !icon.isItem() || icon == Material.AIR) {
         sender.sendMessage("§c有効なアイテムMaterialを指定してください: " + args[2]);
         return;
      }

      this.getConfig().set(path + ".icon", icon.name().toLowerCase(Locale.ROOT));
      this.saveConfig();
      sender.sendMessage("§aテレポート先のアイコンを変更しました: " + key + " → " + icon.name().toLowerCase(Locale.ROOT));
      if (sender instanceof Player player) {
         player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.35F);
      }
   }

   private void applyWorldSpawnLocations() {
      ConfigurationSection spawns = this.getConfig().getConfigurationSection("world-rules.spawn");
      if (spawns != null) {
         for (String key : spawns.getKeys(false)) {
            String path = "world-rules.spawn." + key;
            String worldName = this.getConfig().getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world != null) {
               Location spawn = new Location(
                  world,
                  this.getConfig().getDouble(path + ".x", 0.0),
                  this.getConfig().getDouble(path + ".y", 0.0),
                  this.getConfig().getDouble(path + ".z", 0.0),
                  (float)this.getConfig().getDouble(path + ".yaw", 0.0),
                  (float)this.getConfig().getDouble(path + ".pitch", 0.0)
               );
               world.setSpawnLocation(spawn);
            }
         }
      }
   }

   private void normalizeSpawnLocationsToOrigin() {
      this.setLocationCoordinatesToOrigin("hub");
      this.normalizeLocationSection("world-rules.spawn");
      this.normalizeLocationSection("servers");
      this.normalizeLocationSection("warning-servers");
      this.saveConfig();
   }

   private void normalizeLocationSection(String sectionPath) {
      ConfigurationSection section = this.getConfig().getConfigurationSection(sectionPath);
      if (section != null) {
         for (String key : section.getKeys(false)) {
            String path = sectionPath + "." + key;
            if (!"survival".equalsIgnoreCase(this.getConfig().getString(path + ".world"))) {
               this.setLocationCoordinatesToOrigin(path);
            }
         }
      }
   }

   private void setLocationCoordinatesToOrigin(String path) {
      if (this.getConfig().contains(path + ".world")) {
         this.getConfig().set(path + ".x", 0.0);
         this.getConfig().set(path + ".y", 0.0);
         this.getConfig().set(path + ".z", 0.0);
         this.getConfig().set(path + ".yaw", 0.0);
         this.getConfig().set(path + ".pitch", 0.0);
      }
   }

   private void applyFixedSpawnLocation(String worldName, double x, double y, double z) {
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world != null) {
         world.setSpawnLocation(new Location(world, x, y, z, 0.0F, 0.0F));
      }
   }

   private void configureSurvivalSpawnLocation() {
      this.getConfig().set("world-rules.spawn.survival.world", "survival");
      this.getConfig().set("world-rules.spawn.survival.x", 0.0);
      this.getConfig().set("world-rules.spawn.survival.y", 101.0);
      this.getConfig().set("world-rules.spawn.survival.z", 0.0);
      this.getConfig().set("world-rules.spawn.survival.yaw", 0.0);
      this.getConfig().set("world-rules.spawn.survival.pitch", 0.0);
      this.getConfig().set("servers.survival.world", "survival");
      this.getConfig().set("servers.survival.x", 0.0);
      this.getConfig().set("servers.survival.y", 101.0);
      this.getConfig().set("servers.survival.z", 0.0);
      this.getConfig().set("servers.survival.yaw", 0.0);
      this.getConfig().set("servers.survival.pitch", 0.0);
      this.setIfMissing("servers.survival.icon", "grass_block");
      this.applyFixedSpawnLocation("survival", 0.0, 101.0, 0.0);
      this.saveConfig();
   }

   private void applyConfiguredSpawnLocation(String primaryPath, String fallbackPath, String defaultWorld, double defaultX, double defaultY, double defaultZ) {
      String path = this.getConfig().contains(primaryPath + ".world") ? primaryPath : fallbackPath;
      String worldName = this.getConfig().getString(path + ".world", defaultWorld);
      World world = worldName == null ? null : Bukkit.getWorld(worldName);
      if (world != null) {
         Location spawn = new Location(
            world,
            this.getConfig().getDouble(path + ".x", defaultX),
            this.getConfig().getDouble(path + ".y", defaultY),
            this.getConfig().getDouble(path + ".z", defaultZ),
            (float)this.getConfig().getDouble(path + ".yaw", 0.0),
            (float)this.getConfig().getDouble(path + ".pitch", 0.0)
         );
         world.setSpawnLocation(spawn);
      }
   }

   private void migrateDefaultHubLocation() {
      if ("world".equalsIgnoreCase(this.getConfig().getString("hub.world", "world"))) {
         boolean oldDefault = Math.abs(this.getConfig().getDouble("hub.x") - 0.5) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("hub.y") - 64.0) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("hub.z") - 0.5) < 1.0E-4;
         boolean lowOriginSpawn = Math.abs(this.getConfig().getDouble("hub.x")) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("hub.z")) < 1.0E-4
            && (Math.abs(this.getConfig().getDouble("hub.y") - 60.0) < 1.0E-4 || Math.abs(this.getConfig().getDouble("hub.y") - 64.0) < 1.0E-4);
         boolean highOriginSpawn = Math.abs(this.getConfig().getDouble("hub.x")) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("hub.y") - 300.0) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("hub.z")) < 1.0E-4;
         boolean missing = !this.getConfig().contains("hub.x") || !this.getConfig().contains("hub.y") || !this.getConfig().contains("hub.z");
         if (oldDefault || lowOriginSpawn || highOriginSpawn || missing) {
            this.getConfig().set("hub.world", "world");
            this.getConfig().set("hub.x", 0.0);
            this.getConfig().set("hub.y", 0.0);
            this.getConfig().set("hub.z", 0.0);
            this.getConfig().set("hub.yaw", 0.0);
            this.getConfig().set("hub.pitch", 0.0);
            this.setIfMissing("world-rules.spawn.main.world", "world");
            this.setIfMissing("world-rules.spawn.main.x", 0.0);
            this.setIfMissing("world-rules.spawn.main.y", 0.0);
            this.setIfMissing("world-rules.spawn.main.z", 0.0);
            this.setIfMissing("world-rules.spawn.main.yaw", 0.0);
            this.setIfMissing("world-rules.spawn.main.pitch", 0.0);
            this.saveConfig();
         }
      }
   }

   private void migrateDefaultMinigameLocation() {
      if ("minigame".equalsIgnoreCase(this.getConfig().getString("servers.minigame.world", "minigame"))) {
         boolean oldDefault = Math.abs(this.getConfig().getDouble("servers.minigame.x") - 0.5) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("servers.minigame.y") - 64.0) < 1.0E-4
            && Math.abs(this.getConfig().getDouble("servers.minigame.z") - 0.5) < 1.0E-4;
         boolean missing = !this.getConfig().contains("servers.minigame.x")
            || !this.getConfig().contains("servers.minigame.y")
            || !this.getConfig().contains("servers.minigame.z");
         if (oldDefault || missing) {
            this.getConfig().set("servers.minigame.world", "minigame");
            this.getConfig().set("servers.minigame.x", 0.0);
            this.getConfig().set("servers.minigame.y", 0.0);
            this.getConfig().set("servers.minigame.z", 0.0);
            this.getConfig().set("servers.minigame.yaw", 0.0);
            this.getConfig().set("servers.minigame.pitch", 0.0);
            this.setIfMissing("world-rules.spawn.minigame.world", "minigame");
            this.setIfMissing("world-rules.spawn.minigame.x", 0.0);
            this.setIfMissing("world-rules.spawn.minigame.y", 0.0);
            this.setIfMissing("world-rules.spawn.minigame.z", 0.0);
            this.setIfMissing("world-rules.spawn.minigame.yaw", 0.0);
            this.setIfMissing("world-rules.spawn.minigame.pitch", 0.0);
            this.saveConfig();
         }
      }
   }

   private void setIfMissing(String path, Object value) {
      if (!this.getConfig().contains(path)) {
         this.getConfig().set(path, value);
      }
   }

   private void writeLocation(String path, Location location) {
      this.getConfig().set(path + ".world", location.getWorld().getName());
      this.getConfig().set(path + ".x", location.getX());
      this.getConfig().set(path + ".y", location.getY());
      this.getConfig().set(path + ".z", location.getZ());
      this.getConfig().set(path + ".yaw", location.getYaw());
      this.getConfig().set(path + ".pitch", location.getPitch());
      this.saveConfig();
   }

   @EventHandler
   public void onChat(AsyncChatEvent event) {
      Player sender = event.getPlayer();
      String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
      if (this.pendingFriendSearch.containsKey(sender.getUniqueId())) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> {
            String plainMessage = this.sanitizeTextInput(rawMessage, 32);
            this.pendingFriendSearch.remove(sender.getUniqueId());
            if (!plainMessage.equalsIgnoreCase("clear") && !plainMessage.isBlank()) {
               this.friendSearchFilters.put(sender.getUniqueId(), plainMessage);
            } else {
               this.friendSearchFilters.remove(sender.getUniqueId());
            }

            this.openFriendUi(sender);
         });
      } else if (this.pendingFriendChatInput.containsKey(sender.getUniqueId())) {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> {
            UUID chatTarget = this.pendingFriendChatInput.remove(sender.getUniqueId());
            if (chatTarget != null) {
               String plainMessage = this.sanitizeTextInput(rawMessage, 256);
               if (!plainMessage.isBlank()) {
                  this.activeFriendChatTarget.put(sender.getUniqueId(), chatTarget);
                  this.friendChatDrafts.put(sender.getUniqueId(), plainMessage);
               }

               this.openFriendUi(sender);
            }
         });
      } else {
         event.setCancelled(true);
         Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§7通常チャットはDiscordでご利用ください。サーバーからのシステム通知は引き続き表示されます。"));
      }
   }

   @EventHandler
   public void onAdvancement(PlayerAdvancementDoneEvent event) {
      Advancement advancement = event.getAdvancement();
      if (this.shouldTrackAdvancement(advancement)) {
         String fullKey = advancement.getKey().toString();
         Player player = event.getPlayer();
         Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
         completed.add(fullKey);
         this.getPlayerSection(player.getUniqueId()).set("completed-advancements", new ArrayList<>(completed));
         this.notifyUnlockedTitles(player, completed);
         Set<String> rewarded = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("rewarded-advancements"));
         if (!rewarded.add(fullKey)) {
            this.queueDataSave();
         } else {
            this.getPlayerSection(player.getUniqueId()).set("rewarded-advancements", new ArrayList<>(rewarded));
            String key = advancement.getKey().getKey();
            AdvancementDisplay display = advancement.getDisplay();
            Frame frame = display == null ? Frame.TASK : display.frame();
            int reward = this.advancementReward(frame, "emeralds");
            int bonus = this.advancementBonus(frame);
            ConfigurationSection special = this.getConfig().getConfigurationSection("advancement-unlocks." + key);
            if (special != null) {
               reward = special.getInt("emeralds", reward);
               this.applyUnlocks(player, special);
            }

            double multiplier = Math.max(0.0D, Math.min(10.0D, this.getConfig().getDouble("advancement-rewards.multiplier", 5.0D)));
            reward = (int)Math.min(2000000000L, Math.max(0L, Math.round(reward * multiplier)));

            int paidReward = this.applyIncomeBonus(player.getUniqueId(), reward);
            this.depositEmeralds(player.getUniqueId(), paidReward);
            this.updateAdvancementBonus(player.getUniqueId(), completed);
            player.sendMessage("§a進捗報酬: +" + this.formatNumber(paidReward) + "MP");
            this.checkAllAdvancementsCompleted(player);
            this.refreshPlayerName(player);
         }
      }
   }

   private int advancementReward(Frame frame, String field) {
      String path = switch (frame) {
         case CHALLENGE -> "advancement-rewards.challenge.";
         case GOAL -> "advancement-rewards.goal.";
         default -> "advancement-rewards.default.";
      };

      int fallback = switch (frame) {
         case CHALLENGE -> 100;
         case GOAL -> 50;
         default -> 10;
      };
      return this.getConfig().getInt(path + field, fallback);
   }

   private int advancementBonus(Frame frame) {
      return 0;
   }

   private void syncAdvancementState(Player player) {
      if (player.isOnline()) {
         ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
         Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
         Set<String> rewarded = new HashSet<>(section.getStringList("rewarded-advancements"));
         boolean changed = false;
         Iterator<Advancement> iterator = Bukkit.advancementIterator();

         while (iterator.hasNext()) {
            Advancement advancement = iterator.next();
            if (this.shouldTrackAdvancement(advancement)) {
               String key = advancement.getKey().toString();
               AdvancementProgress progress = player.getAdvancementProgress(advancement);
               if (progress.isDone()) {
                  changed |= completed.add(key);
                  changed |= rewarded.add(key);
               } else if (completed.contains(key)) {
                  changed |= rewarded.add(key);

                  for (String criterion : new ArrayList<String>(progress.getRemainingCriteria())) {
                     progress.awardCriteria(criterion);
                  }

                  changed = true;
               }
            }
         }

         if (changed) {
            section.set("completed-advancements", new ArrayList<>(completed));
            section.set("rewarded-advancements", new ArrayList<>(rewarded));
         }

         this.updateAdvancementBonus(player.getUniqueId(), completed);
         this.queueDataSave();
         this.checkAllAdvancementsCompleted(player);
         this.notifyUnlockedTitles(player, completed);
         this.refreshPlayerName(player);
      }
   }

   private boolean shouldTrackAdvancement(Advancement advancement) {
      String key = advancement.getKey().getKey();
      return !key.startsWith("recipes/");
   }

   private void checkAllAdvancementsCompleted(Player player) {
      int total = this.countTrackableAdvancements();
      if (total > 0 && this.countCompletedAdvancements(player) >= total) {
         ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
         if (!section.getBoolean("all-advancements-rewarded", false)) {
            int paidReward = this.applyIncomeBonus(player.getUniqueId(), 10000);
            this.depositEmeralds(player.getUniqueId(), paidReward);
            this.updateAdvancementBonus(player.getUniqueId(), new HashSet<>(section.getStringList("completed-advancements")));
            section.set("all-advancements-rewarded", true);
            this.queueDataSave();
            player.sendMessage("§6全進捗達成報酬: +" + this.formatNumber(paidReward) + "MP / 転生タブから転生できます。");
         }
      }
   }

   private void applyUnlocks(Player player, ConfigurationSection section) {
      if (section.contains("shop-discount")) {
         this.getPlayerSection(player.getUniqueId())
            .set("shop-discount", Math.max(this.getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0), section.getInt("shop-discount")));
      }

      for (String listKey : List.of("unlocks", "skins", "pets", "ffa-classes")) {
         List<String> values = section.getStringList(listKey);
         if (!values.isEmpty()) {
            Set<String> current = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList(listKey));
            current.addAll(values);
            this.getPlayerSection(player.getUniqueId()).set(listKey, new ArrayList<>(current));
         }
      }

      this.queueDataSave();
   }

   private void handleLoginReward(Player player) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      String today = LocalDate.now(ZoneId.systemDefault()).toString();
      if (!today.equals(section.getString("last-login-reward"))) {
         LocalDate last = null;
         String lastValue = section.getString("last-login-reward");
         if (lastValue != null) {
            last = LocalDate.parse(lastValue);
         }

         int streak = last != null && last.plusDays(1L).toString().equals(today) ? this.safeAdd(section.getInt("login-streak", 0), 1) : 1;
         int total = this.safeAdd(section.getInt("total-logins", 0), 1);
         section.set("last-login-reward", today);
         section.set("login-streak", streak);
         section.set("total-logins", total);
         int reward = this.applyIncomeBonus(player.getUniqueId(), 10 + streak);
         if (total % 10 == 0) {
            reward = this.safeAdd(reward, this.safeMultiply(100, total / 10));
         }

         this.depositEmeralds(player.getUniqueId(), reward);
         player.sendMessage("§aログイン報酬: +" + this.formatNumber(reward) + "MP");
         this.queueDataSave();
      }
   }

   private void grantPlaytimeRewards() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
         int minutes = this.safeAdd(section.getInt("session-minutes", 0), 1);
         section.set("session-minutes", minutes);
         int totalMinutes = this.safeAdd(section.getInt("total-minutes", 0), 1);
         section.set("total-minutes", totalMinutes);
         if (minutes % 10 == 0) {
            int sessionRewards = section.getInt("session-playtime-rewards", 0);
            if (sessionRewards < 20) {
               int reward = this.applyIncomeBonus(player.getUniqueId(), 10);
               if (totalMinutes % 6000 == 0) {
                  reward = this.safeAdd(reward, this.safeMultiply(100, totalMinutes / 6000));
               }

               section.set("session-playtime-rewards", sessionRewards + 1);
               this.depositEmeralds(player.getUniqueId(), reward);
               player.sendMessage("§a滞在報酬: +" + this.formatNumber(reward) + "MP");
            }
         }
      }

      this.queueDataSave();
   }

   private void routeByWarningLevel(Player player) {
      int warning = this.getPlayerSection(player.getUniqueId()).getInt("warning-level", 0);
      if (warning >= 4) {
         player.kick(Component.text("BAN: 警戒値4に到達しています。"));
      } else {
         String path = switch (warning) {
            case 1 -> "warning-servers.caution";
            case 2 -> "warning-servers.detention";
            case 3 -> "warning-servers.imprisonment";
            default -> "warning-servers.basic";
         };
         Location location = this.readLocation(path);
         if (location != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> player.teleport(location), 5L);
         }
      }
   }

   int getEmeralds(UUID uuid) {
      int emeralds = this.getPlayerSection(uuid).getInt("emeralds", 0);
      return Math.max(0, Math.min(2000000000, emeralds));
   }

   void depositEmeralds(UUID uuid, int amount) {
      this.depositEmeralds(uuid, amount, true);
   }

   void refundEmeralds(UUID uuid, int amount) {
      if (uuid != null && amount > 0) {
         ConfigurationSection section = this.getPlayerSection(uuid);
         int before = this.getEmeralds(uuid);
         int after = this.safeAdd(before, amount);
         section.set("emeralds", after);
         this.trackEconomyAnalytics(uuid, "mp_earned", Math.max(0, after - before), after, "refund");
         this.queueDataSave();
      }
   }

   private void depositEmeralds(UUID uuid, int amount, boolean persist) {
      if (amount > 0) {
         ConfigurationSection section = this.getPlayerSection(uuid);
         int added = Math.min(amount, 2000000000);
         int before = this.getEmeralds(uuid);
         int after = this.safeAdd(before, added);
         section.set("emeralds", after);
         this.trackEconomyAnalytics(uuid, "mp_earned", Math.max(0, after - before), after, "unclassified");
         section.set("total-earned-emeralds", this.safeAdd(section.getInt("total-earned-emeralds", 0), added));
         if (!section.getBoolean("analytics.first-mp-earned-recorded", false)) {
            section.set("analytics.first-mp-earned-pending", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
               this.flushPendingFirstMpEvent(player);
            }
         }
         if (!section.getBoolean("analytics.first-reward-recorded", false)) {
            section.set("analytics.first-reward-recorded", true);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
               this.trackAnalytics(player, "first_reward", "first-reward:" + uuid);
            }
         }
         if (persist) {
            this.queueDataSave();
         }
      }
   }

   boolean withdrawEmeralds(UUID uuid, int amount) {
      return this.withdrawEmeralds(uuid, amount, true);
   }

   private boolean withdrawEmeralds(UUID uuid, int amount, boolean persist) {
      if (amount <= 0) {
         return false;
      }

      ConfigurationSection section = this.getPlayerSection(uuid);
      int current = this.getEmeralds(uuid);
      if (current < amount) {
         return false;
      }

      section.set("emeralds", current - amount);
      this.trackEconomyAnalytics(uuid, "mp_spent", amount, current - amount, "unclassified");
      if (persist) {
         this.queueDataSave();
      }
      return true;
   }

   private int safeAdd(int current, int amount) {
      long result = (long)Math.max(0, current) + Math.max(0, amount);
      return (int)Math.min(2000000000L, result);
   }

   private int safeMultiply(int left, int right) {
      long result = (long)Math.max(0, left) * Math.max(0, right);
      return (int)Math.min(2000000000L, result);
   }

   int applyIncomeBonus(UUID uuid, int base) {
      if (base <= 0) {
         return 0;
      }

      int bonus = this.getTotalIncomeBonus(uuid);
      long reward = base + (long)base * Math.max(0, bonus) / 100L;
      return (int)Math.min(2000000000L, reward);
   }

   private int getMfl(UUID uuid) {
      ConfigurationSection section = this.getPlayerSection(uuid);
      long score = (long)Math.max(0, section.getInt("total-blocks-broken", 0))
         + Math.max(0L, section.getInt("total-blocks-placed", 0))
         + (long)Math.max(0, section.getInt("total-trades", 0)) * 5L
         + Math.max(0L, section.getInt("total-minutes", 0))
         + (long)Math.max(0, section.getInt("total-play-count", 0)) * 10L
         + (long)Math.max(0, section.getInt("total-mob-kills", 0)) * 3L
         + (long)section.getStringList("completed-advancements").size() * 50L;
      return (int)Math.max(1L, Math.min(2000000000L, score / 100L + 1L));
   }

   private String getMflRank(UUID uuid) {
      int mfl = this.getMfl(uuid);
      if (mfl >= 100) {
         return "S";
      } else if (mfl >= 60) {
         return "A";
      } else if (mfl >= 30) {
         return "B";
      } else {
         return mfl >= 10 ? "C" : "D";
      }
   }

   private int getAdvancementBonus(UUID uuid) {
      ConfigurationSection section = this.getPlayerSection(uuid);
      Set<String> completed = new HashSet<>(section.getStringList("completed-advancements"));
      return !completed.isEmpty() ? this.calculateAdvancementBonus(completed) : 0;
   }

   int getReincarnationBonus(UUID uuid) {
      return this.getPlayerSection(uuid).getInt("reincarnation-bonus-percent", 0);
   }

   private int getTotalIncomeBonus(UUID uuid) {
      return this.getReincarnationBonus(uuid);
   }

   private int updateAdvancementBonus(UUID uuid, Set<String> completed) {
      ConfigurationSection section = this.getPlayerSection(uuid);
      int bonus = this.calculateAdvancementBonus(completed);
      section.set("advancement-bonus-percent", bonus);
      section.set("income-bonus-percent", null);
      this.queueDataSave();
      return bonus;
   }

   private int calculateAdvancementBonus(Set<String> completed) {
      return 0;
   }

   private void addAdvancementBonus(UUID uuid, int percent) {
      this.updateAdvancementBonus(uuid, new HashSet<>(this.getPlayerSection(uuid).getStringList("completed-advancements")));
   }

   private void addReincarnationBonus(UUID uuid, int percent) {
      ConfigurationSection section = this.getPlayerSection(uuid);
      section.set("reincarnation-bonus-percent", this.safeAdd(this.getReincarnationBonus(uuid), percent));
      this.queueDataSave();
   }

   void addPlayerStat(UUID uuid, String key, int amount) {
      this.addPlayerStat(uuid, key, amount, true);
   }

   private void addPlayerStat(UUID uuid, String key, int amount, boolean persist) {
      if (amount > 0) {
         ConfigurationSection section = this.getPlayerSection(uuid);
         section.set(key, this.safeAdd(section.getInt(key, 0), amount));
         this.questService.recordStat(uuid, key, amount);
         if (persist) {
            this.queueDataSave();
         }
      }
   }

   private void refreshPlayerName(Player player) {
      String title = this.selectedTitle(player);
      Component name = this.titlePrefix(player).append(Component.text(player.getName()));
      Component tabName = name.append(Component.text(" MFL " + this.getMfl(player.getUniqueId()), NamedTextColor.AQUA));
      player.displayName(name);
      player.playerListName(tabName);
      player.customName(name);
      player.setCustomNameVisible(!title.isBlank());
   }

   private Component titlePrefix(Player player) {
      String title = this.selectedTitle(player);
      return title.isBlank() ? Component.empty() : Component.text("[" + title + "] ", NamedTextColor.GOLD);
   }

   private String selectedTitle(Player player) {
      String title = this.getPlayerSection(player.getUniqueId()).getString("selected-title", "");
      if (title != null && !title.isBlank() && this.canUseTitle(player, title)) {
         return title;
      }

      if (title != null && !title.isBlank()) {
         this.getPlayerSection(player.getUniqueId()).set("selected-title", null);
         this.queueDataSave();
      }

      return "";
   }

   private boolean canUseTitle(Player player, String title) {
      Mifron.TitleDefinition definition = this.titleDefinitions().get(title);
      if (definition == null) {
         return false;
      }

      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      return this.hasTitle(completed, definition);
   }

   private Map<String, Mifron.TitleDefinition> titleDefinitions() {
      Map<String, Mifron.TitleDefinition> definitions = new LinkedHashMap<>(TITLE_DEFINITIONS);
      ConfigurationSection section = this.getConfig().getConfigurationSection("titles");
      if (section == null) {
         return definitions;
      }

      for (String key : section.getKeys(false)) {
         String displayName = section.getString(key + ".display-name", key);
         if (displayName != null && !displayName.isBlank()) {
            Material icon = Material.matchMaterial(section.getString(key + ".icon", "name_tag"));
            List<String> requirements = section.getStringList(key + ".required-advancements");
            definitions.put(displayName, new Mifron.TitleDefinition(icon == null ? Material.NAME_TAG : icon, requirements));
         }
      }

      return definitions;
   }

   private void resetStatusData(UUID uuid) {
      ConfigurationSection section = this.getPlayerSection(uuid);

      for (String key : List.of(
         "emeralds",
         "total-earned-emeralds",
         "income-bonus-percent",
         "advancement-bonus-percent",
         "reincarnation-bonus-percent",
         "reincarnations",
         "pending-advancement-reset",
         "session-minutes",
         "session-playtime-rewards",
         "total-minutes",
         "total-play-count",
         "login-streak",
         "total-logins",
         "last-login-reward",
         "completed-advancements",
         "rewarded-advancements",
         "all-advancements-rewarded",
         "shop-discount",
         "unlocks",
         "skins",
         "pets",
         "ffa-classes",
         "total-mob-kills",
         "killed-mobs",
         "unlocked-titles",
         "selected-title",
         "total-trades",
         "total-blocks-broken",
         "total-blocks-placed"
      )) {
         section.set(key, null);
      }

      section.set("pending-advancement-reset", true);
      this.queueDataSave();
   }

   private ConfigurationSection getPlayerSection(UUID uuid) {
      String path = "players." + uuid;
      ConfigurationSection section = this.data.getConfigurationSection(path);
      return section != null ? section : this.data.createSection(path);
   }

   private Set<UUID> getUuidSet(UUID owner, String key) {
      List<String> raw = this.getPlayerSection(owner).getStringList(key);
      Set<UUID> result = new HashSet<>();

      for (String value : raw) {
         try {
            result.add(UUID.fromString(value));
         } catch (IllegalArgumentException var8) {
         }
      }

      return result;
   }

   boolean areFriends(UUID first, UUID second) {
      return first != null && second != null && !first.equals(second)
         ? this.getUuidSet(first, "friends").contains(second) || this.getUuidSet(second, "friends").contains(first)
         : false;
   }

   private void setUuidSet(UUID owner, String key, Set<UUID> values) {
      this.getPlayerSection(owner).set(key, values.stream().map(UUID::toString).toList());
      this.queueDataSave();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      try {
         if (("mifron".equalsIgnoreCase(command.getName()) || "mf".equalsIgnoreCase(command.getName()))
            && args.length > 0 && ("link".equalsIgnoreCase(args[0]) || "discord".equalsIgnoreCase(args[0]))) {
            if (!(sender instanceof Player player)) {
               sender.sendMessage("§cこのコマンドはゲーム内プレイヤー専用です。");
               return true;
            }
            return this.minoruBridgeFeature.handleCommand(player, args);
         }
         if ("friend".equalsIgnoreCase(command.getName())) {
            return this.handleFriendCommand(sender, args);
         } else if ("status".equalsIgnoreCase(command.getName())) {
            return this.handleStatusCommand(sender, args);
         } else {
            return "tutorial".equalsIgnoreCase(command.getName()) ? this.handleTutorialCommand(sender) : this.handleMifronCommand(sender, args);
         }
      } catch (Throwable e) {
         this.getLogger().severe("Command failed: /" + label + " " + String.join(" ", args));
         e.printStackTrace();
         sender.sendMessage("§cコマンド実行中にエラーが発生しました。詳細はサーバーコンソールを確認してください。");
         return true;
      }
   }

   private boolean handleMifronCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         sender.sendMessage(
            "§e/mifron check|list|tp|text|ffa|structure|build|proposal|gamerules|info|reload|kit|balance|pay|merchant|minigame|athletic|quest|mp|regen|chunk|status|tutorial|shelfshop|shopwand|slotwand|jumppadwand|serverwand|sethub|setserver|serverorder|servericon|delserver|warning"
         );
         return true;
      } else if (!(sender instanceof Player player)
         && !List.of("warning", "mp", "em", "emerald", "regen", "reload", "info", "list", "gamerules", "text", "ffa", "structure", "proposal", "shelfshop")
            .contains(args[0].toLowerCase(Locale.ROOT))) {
         sender.sendMessage("Player only.");
         return true;
      } else {
         switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check":
               if (this.hasPermission(sender, "mifron.command.chunk")) {
                  this.handleChunkCommand((Player)sender);
               }
               break;
            case "list":
               this.handleListCommand(sender);
               break;
            case "tp":
               this.handleWorldTpCommand((Player)sender, args);
               break;
            case "text":
               this.textDisplayFeature.handleCommand(sender, args);
               break;
            case "ffa":
               this.ffaManager.handleCommand(sender, args);
               break;
            case "structure":
               this.structureManager.handleCommand(sender, args);
               break;
            case "build":
               this.buildWorldManager.handleCommand(sender, args);
               break;
            case "proposal":
               this.proposalManager.handleCommand(sender, args);
               break;
            case "gamerules":
               this.handleGamerulesCommand(sender, args);
               break;
            case "info":
               this.handleInfoCommand(sender);
               break;
            case "reload":
               this.handleReloadCommand(sender);
               break;
            case "kit":
               this.giveInitialItems((Player)sender);
               sender.sendMessage("§a初期配布物を確認しました。");
               break;
            case "balance":
               sender.sendMessage("§a所持MP: " + this.formatNumber(this.getEmeralds(((Player)sender).getUniqueId())));
               break;
            case "pay":
               this.handlePayCommand((Player)sender, args);
               break;
            case "merchant":
            case "marchant":
               this.handleMerchantCommand((Player)sender, args);
               break;
            case "minigame":
               this.handleMinigameCommand((Player)sender, args);
               break;
            case "athletic":
               if (!this.athleticManager.handleCommand((Player)sender, args)) {
                  this.handleAthleticCommand((Player)sender, args);
               }
               break;
            case "quest":
               this.handleQuestCommand(sender, args);
               break;
            case "mp":
            case "em":
            case "emerald":
               this.handleEmeraldCommand(sender, args);
               break;
            case "regen":
               this.handleRegenCommand(sender, args);
               break;
            case "chunk":
               if (this.hasPermission(sender, "mifron.command.chunk")) {
                  this.handleChunkCommand((Player)sender);
               }
               break;
            case "status":
               if (this.hasPermission(sender, "mifron.command.status")) {
                  this.handleMifronStatusCommand((Player)sender, args);
               }
               break;
            case "tutorial":
               this.handleTutorialCommand(sender);
               break;
            case "shelfshop":
               this.handleShelfShopCommand(sender, args);
               break;
            case "shopwand":
               if (!sender.hasPermission("mifron.shop.admin") && !sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               ItemStack wand;
               if (args.length == 1) {
                  wand = this.createShopWand();
               } else {
                  if (args.length < 2) {
                     sender.sendMessage("§c/mf shopwand <shelf|barrel|frame>");
                     return true;
                  }

                  ShopWandType type = ShopWandType.fromKey(args[1]);
                  if (type == null) {
                     sender.sendMessage("§ctype は shelf / barrel / frame のいずれかです。");
                     return true;
                  }

                  if (type == ShopWandType.FRAME) {
                     sender.sendMessage("§cframe ショップは未実装です。額縁は既存のオークション専用です。");
                     return true;
                  }

                  wand = this.createShopWand(type);
               }

               Map<Integer, ItemStack> leftovers = ((Player)sender).getInventory().addItem(new ItemStack[]{wand});
               if (!leftovers.isEmpty()) {
                  sender.sendMessage("§cインベントリに空きがありません。");
                  return true;
               }

               sender.sendMessage("§aショップワンドを入手しました。");
               break;
            case "jumppadwand":
               if (!sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               int verticalPower = args.length >= 2 ? this.parsePositiveInt(args[1], -1) : 5;
               int horizontalPower = args.length >= 3 ? this.parsePositiveInt(args[2], -1) : verticalPower;
               if (verticalPower < 1 || verticalPower > 100 || horizontalPower < 1 || horizontalPower > 100) {
                  sender.sendMessage("§c/mf jumppadwand <縦1-100> [横1-100]");
                  return true;
               }

               Map<Integer, ItemStack> jumpPadLeftovers = ((Player)sender)
                  .getInventory()
                  .addItem(new ItemStack[]{this.createJumpPadWand(verticalPower, horizontalPower)});
               if (!jumpPadLeftovers.isEmpty()) {
                  sender.sendMessage("§cインベントリに空きがありません。");
                  return true;
               }

               sender.sendMessage("§aジャンプパッドワンドを入手しました。縦: " + verticalPower + " / 横: " + horizontalPower);
               break;
            case "slotwand":
               if (!sender.hasPermission("mifron.shop.admin") && !sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               ItemStack slotWand;
               if (args.length == 1) {
                  slotWand = this.createShopWand(ShopWandType.SLOT_NORMAL);
               } else {
                  if (args.length < 2) {
                     sender.sendMessage("§c/mf slotwand <easy|normal|hard|expert>");
                     return true;
                  }

                  ShopWandType type = ShopWandType.fromKey("slot_" + args[1].toLowerCase(Locale.ROOT));
                  if (type == null || !type.isSlotWand()) {
                     sender.sendMessage("§c難易度は easy / normal / hard / expert のいずれかです。");
                     return true;
                  }

                  slotWand = this.createShopWand(type);
               }

               Map<Integer, ItemStack> slotLeftovers = ((Player)sender).getInventory().addItem(new ItemStack[]{slotWand});
               if (!slotLeftovers.isEmpty()) {
                  sender.sendMessage("§cインベントリに空きがありません。");
                  return true;
               }

               sender.sendMessage("§aスロットワンドを入手しました。");
               break;
            case "serverwand":
               if (!sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               Map<Integer, ItemStack> serverLeftovers = ((Player)sender).getInventory().addItem(new ItemStack[]{this.serverPortalFeature.createServerWand()});
               if (!serverLeftovers.isEmpty()) {
                  sender.sendMessage("§cインベントリに空きがありません。");
                  return true;
               }

               sender.sendMessage("§aサーバーワンドを入手しました。");
               break;
            case "sethub":
               if (!sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               this.writeLocation("hub", ((Player)sender).getLocation());
               sender.sendMessage("§a中央広場を設定しました。");
               break;
            case "setserver":
               if (!sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               if (args.length < 2) {
                  sender.sendMessage("§c/mifron setserver <name> [icon]");
                  return true;
               }

               if (!this.isSafeConfigKey(args[1])) {
                  this.sendInvalidConfigKeyMessage(sender, "サーバー名");
                  return true;
               }

               Material icon = null;
               if (args.length >= 3) {
                  icon = this.parseServerIcon(sender, args[2]);
                  if (icon == null) {
                     return true;
                  }
               }

               this.writeLocation("servers." + args[1], ((Player)sender).getLocation());
               if (icon != null) {
                  this.getConfig().set("servers." + args[1] + ".icon", icon.name().toLowerCase(Locale.ROOT));
                  this.saveConfig();
               }

               sender.sendMessage("§aサーバー移動先を設定しました: " + args[1] + (icon == null ? "" : "§7 / icon: " + icon.name().toLowerCase(Locale.ROOT)));
               break;
            case "serverorder":
            case "servermove":
               this.handleServerOrderCommand(sender, args);
               break;
            case "servericon":
            case "setservericon":
               this.handleServerIconCommand(sender, args);
               break;
            case "delserver":
            case "removeserver":
               if (!sender.hasPermission("mifron.admin")) {
                  sender.sendMessage("§c権限がありません。");
                  return true;
               }

               if (args.length < 2) {
                  sender.sendMessage("§c/mifron delserver <name>");
                  return true;
               }

               if (!this.isSafeConfigKey(args[1])) {
                  this.sendInvalidConfigKeyMessage(sender, "サーバー名");
                  return true;
               }

               String path = "servers." + args[1];
               if (!this.getConfig().contains(path)) {
                  sender.sendMessage("§cサーバー移動先が見つかりません: " + args[1]);
                  return true;
               }

               this.getConfig().set(path, null);
               this.saveConfig();
               sender.sendMessage("§aサーバー移動先を削除しました: " + args[1]);
               break;
            case "warning":
               this.handleWarningCommand(sender, args);
               break;
            default:
               sender.sendMessage(
                  "§e/mifron check|list|tp|text|ffa|structure|proposal|gamerules|info|reload|kit|balance|pay|merchant|minigame|athletic|quest|mp|regen|chunk|status|tutorial|shelfshop|shopwand|slotwand|jumppadwand|serverwand|sethub|setserver|delserver|warning"
               );
         }

         return true;
      }
   }

   private boolean handleTutorialCommand(CommandSender sender) {
      if (sender instanceof Player player) {
         this.startTutorial(player, true);
         return true;
      } else {
         sender.sendMessage("Player only.");
         return true;
      }
   }

   private void handleRegenCommand(CommandSender sender, String[] args) {
      this.chunkProtectionFeature.handleRegenCommand(sender, args);
   }

   boolean hasPermission(CommandSender sender, String permission) {
      if (!sender.hasPermission(permission) && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return false;
      } else {
         return true;
      }
   }

   private void handleListCommand(CommandSender sender) {
      List<String> worlds = Bukkit.getWorlds().stream().<String>map(WorldInfo::getName).sorted().toList();
      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      List<String> serverKeys = servers == null ? Collections.emptyList() : servers.getKeys(false).stream().sorted().toList();
      sender.sendMessage("§aWorlds: " + String.join(", ", worlds));
      sender.sendMessage("§aConfigured servers: " + (serverKeys.isEmpty() ? "(none)" : String.join(", ", serverKeys)));
   }

   private void handleWorldTpCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("§c/mifron tp <worldKey>");
      } else {
         String key = args[1];
         if (this.getConfig().contains("servers." + key)) {
            this.teleportToConfigLocation(player, "servers." + key);
         } else {
            World world = Bukkit.getWorld(key);
            if (world == null) {
               player.sendMessage("§c移動先が見つかりません: " + key);
            } else {
               player.teleport(world.getSpawnLocation());
               this.playTeleportSound(player);
               player.sendMessage("§a" + world.getName() + " のスポーンへ移動しました。");
            }
         }
      }
   }

   private void handleGamerulesCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return;
      }

      if (args.length >= 2) {
         World world = Bukkit.getWorld(args[1]);
         if (world == null) {
            sender.sendMessage("§cワールドが見つかりません: " + args[1]);
            return;
         }

         this.worldRulesFeature.apply(world);
         sender.sendMessage("§aゲームルールを適用しました: " + world.getName());
         return;
      }

      this.worldRulesFeature.apply();
      sender.sendMessage("§a全ワールドへMifronゲームルールを適用しました。");
   }

   private void handleInfoCommand(CommandSender sender) {
      sender.sendMessage("§aMifron " + this.getDescription().getVersion());
      sender.sendMessage("§7Commands: /mifron, /mf");
      sender.sendMessage("§7/mv はMultiverse-Core専用です。Mifronは登録しません。");
   }

   private void handleReloadCommand(CommandSender sender) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else {
         this.reloadConfig();
         this.economyPriceTable.load();
         this.questService.load();
         this.loadShopPrices();
         this.applyEconomyPriceTable();
         this.rebuildShelfShopCatalog();
         this.syncShelfShopDisplays();
         this.structureManager.load();
         this.proposalManager.load();
         this.ffaManager.load();
         sender.sendMessage("§aMifron設定、価格表、クエスト定義を再読込しました。");
      }
   }

   private void handleChunkCommand(Player player) {
      this.chunkProtectionFeature.handleChunkCommand(player);
   }

   private void handleMifronStatusCommand(Player player, String[] args) {
      if (args.length < 2 || !"reset".equalsIgnoreCase(args[1])) {
         ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
         player.sendMessage("§aMifronステータス");
         player.sendMessage("§7MFL: " + this.getMfl(player.getUniqueId()) + " / ランク: " + this.getMflRank(player.getUniqueId()));
         player.sendMessage("§7所持MP: " + this.formatNumber(this.getEmeralds(player.getUniqueId())) + "MP");
         player.sendMessage("§7転生ボーナス: +" + this.getReincarnationBonus(player.getUniqueId()) + "%");
         player.sendMessage("§7総プレイ時間: " + this.formatPlayTime(section.getInt("total-minutes", 0)));
         player.sendMessage("§eリセット: /mifron status reset");
      } else if (!player.hasPermission("mifron.admin")) {
         player.sendMessage("§cステータスリセットは管理者のみ実行できます。");
      } else {
         this.resetStatusData(player.getUniqueId());
         player.sendMessage("§a自分のMifronステータスをリセットしました。");
      }
   }

   private void handleQuestCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else if (args.length >= 5 && "progress".equalsIgnoreCase(args[1])) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[2]);
         if (target != null && target.isOnline() && target.getPlayer() != null) {
            int amount = this.parsePositiveInt(args[4], -1);
            if (amount < 0) {
               sender.sendMessage("§camount は0以上の数字にしてください。");
            } else {
               this.questService.setQuestProgress(target.getPlayer(), args[3].toUpperCase(Locale.ROOT), amount);
            }
         } else {
            sender.sendMessage("§cオンラインのプレイヤーを指定してください。");
         }
      } else {
         sender.sendMessage("§c/mifron quest progress <player> <questId> <amount>");
      }
   }

   private void handleAthleticCommand(Player player, String[] args) {
      if (this.hasPermission(player, "mifron.reward.grant")) {
         if (args.length >= 3 && "complete".equalsIgnoreCase(args[1])) {
            String difficulty = args[2].toLowerCase(Locale.ROOT);

            int base = switch (difficulty) {
               case "easy", "イージー" -> 5;
               case "normal", "ノーマル" -> 10;
               case "hard", "ハード" -> 50;
               case "hardcore", "ハードコア" -> 100;
               default -> -1;
            };
            if (base < 0) {
               player.sendMessage("§c難易度は easy, normal, hard, hardcore のいずれかです。");
            } else {
               int misses = args.length >= 4 ? this.parsePositiveInt(args[3], 0) : 0;
               int reward = this.applyIncomeBonus(player.getUniqueId(), Math.max(0, base - misses));
               this.depositEmeralds(player.getUniqueId(), reward);
               this.addPlayerStat(player.getUniqueId(), "athletic-clears", 1);
               if ("hardcore".equals(difficulty) || "ハードコア".equals(difficulty)) {
                  this.recordQuestProgress(player, "hardcore_athletic", 1);
               }
               player.sendMessage("§aアスレチック報酬: +" + this.formatNumber(reward) + "MP");
            }
         } else {
            player.sendMessage("§c/mifron athletic complete <easy|normal|hard|hardcore> [misses]");
         }
      }
   }

   private void handleMinigameCommand(Player player, String[] args) {
      if (args.length < 2) {
         player.sendMessage("§c/mifron minigame play|win|unlock <name> <amount>");
      } else {
         switch (args[1].toLowerCase(Locale.ROOT)) {
            case "play":
               if (!this.hasPermission(player, "mifron.reward.grant")) {
                  return;
               }

               int reward = this.applyIncomeBonus(player.getUniqueId(), 10);
               this.depositEmeralds(player.getUniqueId(), reward);
               this.addPlayerStat(player.getUniqueId(), "minigame-plays", 1);
               player.sendMessage("§aミニゲーム参加報酬: +" + this.formatNumber(reward) + "MP");
               break;
            case "win":
               if (!this.hasPermission(player, "mifron.reward.grant")) {
                  return;
               }

               int winReward = this.applyIncomeBonus(player.getUniqueId(), 10);
               this.depositEmeralds(player.getUniqueId(), winReward);
               this.addPlayerStat(player.getUniqueId(), "minigame-wins", 1);
               this.recordQuestProgress(player, "minigame_champion", 1);
               player.sendMessage("§aミニゲーム勝利報酬: +" + this.formatNumber(winReward) + "MP");
               break;
            case "unlock":
               if (args.length < 4) {
                  player.sendMessage("§c/mifron minigame unlock <name> <amount>");
                  return;
               }

               int amount = this.parsePositiveInt(args[3], -1);
               String key = args[2].toLowerCase(Locale.ROOT);
               if (!this.isSafeConfigKey(key)) {
                  this.sendInvalidConfigKeyMessage(player, "ミニゲーム名");
                  return;
               }

               if (amount <= 0 || !this.withdrawEmeralds(player.getUniqueId(), amount)) {
                  player.sendMessage("§c納品できません。");
                  return;
               }

               String path = "minigames." + key + ".donated";
               int donated = this.safeAdd(this.data.getInt(path, 0), amount);
               this.data.set(path, donated);
               this.recordQuestProgress(player, "community_donations", amount);
               this.recordQuestProgress(player, "community_participation", 1);
               this.recordQuestProgress(player, "server_unlock_contribution", amount);
               int required = this.getConfig().getInt("minigame-unlocks." + key + ".required-emeralds", 0);
               if (required > 0 && donated >= required) {
                  this.data.set("minigames." + key + ".unlocked", true);
               }

               this.saveData();
               String suffix = required > 0 ? " / 必要: " + this.formatNumber(required) : "";
               String unlocked = this.data.getBoolean("minigames." + key + ".unlocked", false) ? " / 解放済" : "";
               player.sendMessage("§a" + key + " に " + this.formatNumber(amount) + "MP 納品しました。累計: " + this.formatNumber(donated) + suffix + unlocked);
               break;
            default:
               player.sendMessage("§c/mifron minigame play|win|unlock <name> <amount>");
         }
      }
   }

   private void handleEmeraldCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else if (args.length >= 4 && List.of("give", "grant", "add").contains(args[1].toLowerCase(Locale.ROOT))) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[2]);
         if (target != null) {
            int amount = this.parsePositiveInt(args[3], -1);
            if (amount <= 0) {
               sender.sendMessage("§c配布MPは1以上の数字にしてください。");
            } else {
               this.depositEmeralds(target.getUniqueId(), amount);
               sender.sendMessage("§a" + this.safePlayerName(target) + " に " + this.formatNumber(amount) + "MP を配布しました。");
               if (target.isOnline() && target.getPlayer() != null) {
                  target.getPlayer().sendMessage("§a管理者から " + this.formatNumber(amount) + "MP が配布されました。");
               }
            }
         }
      } else {
         sender.sendMessage("§c/mifron mp give <player> <amount>");
      }
   }

   private void handlePayCommand(Player player, String[] args) {
      if (args.length < 3) {
         player.sendMessage("§c/mifron pay <player> <amount>");
      } else {
         OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
         if (target != null) {
            if (target.getUniqueId().equals(player.getUniqueId())) {
               player.sendMessage("§c自分には支払えません。");
            } else {
               int amount = this.parsePositiveInt(args[2], -1);
               if (amount > 0 && this.withdrawEmeralds(player.getUniqueId(), amount)) {
                  this.depositEmeralds(target.getUniqueId(), amount);
                  player.sendMessage("§a" + this.safePlayerName(target) + " に " + this.formatNumber(amount) + "MP 支払いました。");
               } else {
                  player.sendMessage("§c支払いできません。");
               }
            }
         }
      }
   }

   private void handleMerchantCommand(Player player, String[] args) {
      if (!player.hasPermission("mifron.admin")) {
         player.sendMessage("§c権限がありません。");
      } else if (args.length < 2) {
         player.sendMessage("§c/mifron merchant spawn|reroll|clear");
      } else {
         switch (args[1].toLowerCase(Locale.ROOT)) {
            case "spawn":
               if (this.spawnMerchant(player.getLocation())) {
                  player.sendMessage("§aMifron商人をスポーンしました。");
               } else {
                  player.sendMessage("§c中央広場にはMifron商人をスポーンできません。");
               }
               break;
            case "reroll":
               int count = 0;

               for (World world : Bukkit.getWorlds()) {
                  for (Entity entity : world.getEntities()) {
                     if (entity instanceof AbstractVillager villager && this.isMifronMerchant(entity)) {
                        this.rerollMerchant(villager);
                        entity.getPersistentDataContainer().set(this.merchantSpawnKey, PersistentDataType.LONG, System.currentTimeMillis());
                        entity.getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
                        count++;
                     }
                  }
               }

               player.sendMessage("§a商人の販売品を再抽選しました: " + count + "体");
               break;
            case "clear":
               int clearedCount = 0;

               for (World world : Bukkit.getWorlds()) {
                  for (Entity entity : world.getEntities()) {
                     if (this.isMifronMerchant(entity)) {
                        entity.remove();
                        clearedCount++;
                     }
                  }
               }

               player.sendMessage("§aMifron商人を削除しました: " + clearedCount + "体");
               break;
            default:
               player.sendMessage("§c/mifron merchant spawn|reroll|clear");
         }
      }
   }

   private void handleWarningCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else if (args.length < 3) {
         sender.sendMessage("§c/mifron warning <player> <0-4>");
      } else {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[1]);
         if (target != null) {
            int level = Math.min(4, this.parsePositiveInt(args[2], 0));
            this.getPlayerSection(target.getUniqueId()).set("warning-level", level);
            this.saveData();
            sender.sendMessage("§a" + this.safePlayerName(target) + " の警戒値を " + level + " にしました。");
            if (target.isOnline()) {
               this.routeByWarningLevel(target.getPlayer());
            }
         }
      }
   }

   private void sendFriendRequest(Player player, OfflinePlayer target) {
      if (target.getUniqueId().equals(player.getUniqueId())) {
         player.sendMessage("§c自分には申請できません。");
      } else if (this.getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) {
         player.sendMessage("§eすでにフレンドです。");
      } else {
         Set<UUID> requests = this.getUuidSet(target.getUniqueId(), "requests");
         if (requests.size() >= 100) {
            player.sendMessage("§c相手のフレンド申請が上限に達しています。");
         } else if (!requests.add(player.getUniqueId())) {
            player.sendMessage("§eすでに申請済みです。");
         } else {
            this.setUuidSet(target.getUniqueId(), "requests", requests);
            player.sendMessage("§aフレンド申請を送信しました: " + this.safePlayerName(target));
            if (target.isOnline()) {
               target.getPlayer().sendMessage("§e" + player.getName() + " からフレンド申請が届きました。");
            }
         }
      }
   }

   private void acceptFriendRequest(Player player, OfflinePlayer requester) {
      Set<UUID> requests = this.getUuidSet(player.getUniqueId(), "requests");
      if (!requests.remove(requester.getUniqueId())) {
         player.sendMessage("§c申請が見つかりません。");
      } else {
         this.setUuidSet(player.getUniqueId(), "requests", requests);
         Set<UUID> playerFriends = this.getUuidSet(player.getUniqueId(), "friends");
         Set<UUID> requesterFriends = this.getUuidSet(requester.getUniqueId(), "friends");
         playerFriends.add(requester.getUniqueId());
         requesterFriends.add(player.getUniqueId());
         this.setUuidSet(player.getUniqueId(), "friends", playerFriends);
         this.setUuidSet(requester.getUniqueId(), "friends", requesterFriends);
         player.sendMessage("§a" + this.safePlayerName(requester) + " とフレンドになりました。");
         if (requester.isOnline()) {
            requester.getPlayer().sendMessage("§a" + player.getName() + " がフレンド申請を承認しました。");
         }
      }
   }

   private void removeFriend(Player player, OfflinePlayer target) {
      Set<UUID> playerFriends = this.getUuidSet(player.getUniqueId(), "friends");
      Set<UUID> targetFriends = this.getUuidSet(target.getUniqueId(), "friends");
      playerFriends.remove(target.getUniqueId());
      targetFriends.remove(player.getUniqueId());
      this.setUuidSet(player.getUniqueId(), "friends", playerFriends);
      this.setUuidSet(target.getUniqueId(), "friends", targetFriends);
      player.sendMessage("§aフレンドを解除しました: " + this.safePlayerName(target));
   }

   private void sendFriendChatDraft(Player player, OfflinePlayer target) {
      String message = this.friendChatDrafts.getOrDefault(player.getUniqueId(), "").trim();
      if (message.isBlank()) {
         player.sendMessage("§c本文が未入力です。");
      } else {
         this.sendFriendChat(player, target, message);
         this.friendChatDrafts.remove(player.getUniqueId());
      }
   }

   private void sendFriendChat(Player player, OfflinePlayer target, String message) {
      message = this.sanitizeTextInput(message, 256);
      if (message.isBlank()) {
         player.sendMessage("§c本文が未入力です。");
      } else if (!this.getUuidSet(player.getUniqueId(), "friends").contains(target.getUniqueId())) {
         player.sendMessage("§cフレンドではありません。");
      } else {
         player.sendMessage("§b[Friend -> " + this.safePlayerName(target) + "] " + message);
         if (target.isOnline()) {
            target.getPlayer().sendMessage("§b[Friend <- " + player.getName() + "] " + message);
         } else {
            List<String> notifications = this.getPlayerSection(target.getUniqueId()).getStringList("offline-messages");
            notifications.add(this.safePlayerName(player) + ": " + message);

            while (notifications.size() > 50) {
               notifications.remove(0);
            }

            this.getPlayerSection(target.getUniqueId()).set("offline-messages", notifications);
            this.saveData();
         }
      }
   }

   private boolean handleFriendCommand(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.openFriendUi(player);
            return true;
         }

         switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add":
               if (args.length < 2) {
                  player.sendMessage("§c/friend add <player>");
                  return true;
               }

               OfflinePlayer target = this.resolveKnownPlayer(player, args[1]);
               if (target != null) {
                  this.sendFriendRequest(player, target);
               }
               break;
            case "accept":
               if (args.length < 2) {
                  player.sendMessage("§c/friend accept <player>");
                  return true;
               }

               OfflinePlayer acceptTarget = this.resolveKnownPlayer(player, args[1]);
               if (acceptTarget != null) {
                  this.acceptFriendRequest(player, acceptTarget);
               }
               break;
            case "remove":
               if (args.length < 2) {
                  player.sendMessage("§c/friend remove <player>");
                  return true;
               }

               OfflinePlayer removeTarget = this.resolveKnownPlayer(player, args[1]);
               if (removeTarget != null) {
                  this.removeFriend(player, removeTarget);
               }
               break;
            case "chat":
               if (args.length < 3) {
                  player.sendMessage("§c/friend chat <player> <message>");
                  return true;
               }

               OfflinePlayer chatTarget = this.resolveKnownPlayer(player, args[1]);
               if (chatTarget != null) {
                  String message = this.sanitizeTextInput(String.join(" ", List.of(args).subList(2, args.length)), 256);
                  this.sendFriendChat(player, chatTarget, message);
               }
               break;
            default:
               player.sendMessage("§e/friend add|accept|remove|chat");
         }

         return true;
      } else {
         sender.sendMessage("Player only.");
         return true;
      }
   }

   private boolean handleStatusCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return true;
      }

      if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
         OfflinePlayer target = this.resolveKnownPlayer(sender, args[0]);
         if (target == null) {
            return true;
         }

         this.resetStatusData(target.getUniqueId());
         sender.sendMessage("§a" + this.safePlayerName(target) + " のステータスをリセットしました。");
         if (target.isOnline() && target.getPlayer() != null) {
            this.resetAdvancements(target.getPlayer());
            this.getPlayerSection(target.getUniqueId()).set("pending-advancement-reset", null);
            this.saveData();
            target.getPlayer().sendMessage("§eステータスがリセットされました。");
         }

         return true;
      } else {
         sender.sendMessage("§c/status <player> reset");
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && this.isMifronRootCommand(command)) {
         return List.of(
            "check",
            "list",
            "tp",
            "text",
            "ffa",
            "structure",
            "build",
            "proposal",
            "gamerules",
            "info",
            "reload",
            "kit",
            "balance",
            "pay",
            "merchant",
            "marchant",
            "minigame",
            "athletic",
            "quest",
            "mp",
            "regen",
            "chunk",
            "status",
            "tutorial",
            "shelfshop",
            "shopwand",
            "slotwand",
            "jumppadwand",
            "serverwand",
            "sethub",
            "setserver",
            "delserver",
            "warning"
         );
      }

      if (args.length >= 2 && this.isMifronRootCommand(command) && "text".equalsIgnoreCase(args[0])) {
         return this.textDisplayFeature.tabComplete(args);
      }

      if (args.length >= 2 && this.isMifronRootCommand(command) && "ffa".equalsIgnoreCase(args[0])) {
         return this.ffaManager.tabComplete(args, sender);
      }

      if (args.length >= 2 && this.isMifronRootCommand(command) && "structure".equalsIgnoreCase(args[0])) {
         return this.structureManager.tabComplete(args);
      }

      if (args.length >= 2 && this.isMifronRootCommand(command) && "build".equalsIgnoreCase(args[0])) {
         return this.buildWorldManager.tabComplete(args);
      }

      if (args.length >= 2 && this.isMifronRootCommand(command) && "proposal".equalsIgnoreCase(args[0])) {
         return this.proposalManager.tabComplete(args);
      }

      if (args.length == 2 && this.isMifronRootCommand(command) && "shopwand".equalsIgnoreCase(args[0])) {
         return List.of("shelf", "barrel", "frame");
      }

      if (args.length == 2 && this.isMifronRootCommand(command) && "shelfshop".equalsIgnoreCase(args[0])) {
         return List.of("clearall", "clear", "reorder", "renumber", "resetstock", "reset");
      }

      if (args.length == 2 && this.isMifronRootCommand(command) && "slotwand".equalsIgnoreCase(args[0])) {
         return List.of("easy", "normal", "hard", "expert");
      }

      if ((args.length == 2 || args.length == 3) && this.isMifronRootCommand(command) && "jumppadwand".equalsIgnoreCase(args[0])) {
         return List.of("1", "5", "10", "25", "50", "75", "100");
      }

      if (args.length == 2 && this.isMifronRootCommand(command) && "tp".equalsIgnoreCase(args[0])) {
         ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
         List<String> values = new ArrayList<>();
         if (servers != null) {
            values.addAll(servers.getKeys(false));
         }

         values.addAll(Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
         return values;
      } else {
         if (args.length == 3 && this.isMifronRootCommand(command) && "setserver".equalsIgnoreCase(args[0])) {
            return this.serverIconSuggestions(args[2]);
         }

         if (args.length == 2 && this.isMifronRootCommand(command) && "gamerules".equalsIgnoreCase(args[0])) {
            return Bukkit.getWorlds().stream().<String>map(WorldInfo::getName).toList();
         }

         if (args.length == 2 && this.isMifronRootCommand(command) && "regen".equalsIgnoreCase(args[0])) {
            return List.of("allow", "deny", "list", "0", "1", "2", "4", "force");
         }

         if (args.length == 3 && this.isMifronRootCommand(command) && "regen".equalsIgnoreCase(args[0]) && "force".equalsIgnoreCase(args[1])) {
            return List.of("0", "1", "2", "4");
         }

         if (args.length == 2 && this.isMifronRootCommand(command) && "status".equalsIgnoreCase(args[0])) {
            return List.of("reset");
         }

         if (args.length == 2 && this.isMifronRootCommand(command) && "minigame".equalsIgnoreCase(args[0])) {
            return List.of("play", "win", "unlock");
         }

         if (args.length >= 2 && this.isMifronRootCommand(command) && "athletic".equalsIgnoreCase(args[0])) {
            return this.athleticManager.tabComplete(args);
         }

         if (args.length == 2 && this.isMifronRootCommand(command) && "quest".equalsIgnoreCase(args[0])) {
            return List.of("progress");
         }

         if (args.length != 2 || !this.isMifronRootCommand(command) || !"merchant".equalsIgnoreCase(args[0]) && !"marchant".equalsIgnoreCase(args[0])) {
            if (args.length != 2
               || !this.isMifronRootCommand(command)
               || !"mp".equalsIgnoreCase(args[0]) && !"em".equalsIgnoreCase(args[0]) && !"emerald".equalsIgnoreCase(args[0])) {
               if (args.length != 2
                  || !this.isMifronRootCommand(command)
                  || !"delserver".equalsIgnoreCase(args[0]) && !"removeserver".equalsIgnoreCase(args[0])) {
                  if (args.length == 1 && "friend".equalsIgnoreCase(command.getName())) {
                     return List.of("add", "accept", "remove", "chat");
                  } else {
                     return args.length == 2 && "status".equalsIgnoreCase(command.getName()) ? List.of("reset") : Collections.emptyList();
                  }
               } else {
                  ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
                  return servers == null ? Collections.emptyList() : new ArrayList<>(servers.getKeys(false));
               }
            } else {
               return List.of("give");
            }
         } else {
            return List.of("spawn", "reroll", "clear");
         }
      }
   }

   private boolean isMifronRootCommand(Command command) {
      return "mifron".equalsIgnoreCase(command.getName()) || "mf".equalsIgnoreCase(command.getName());
   }

   private record BarrelShopConfig(String tier, int weight) {
   }

   static final class ChatColor {
      static final String DARK_AQUA = "§3";
      static final String DARK_GREEN = "§2";
      static final String DARK_PURPLE = "§5";
      static final String GOLD = "§6";
      static final String GREEN = "§a";
      static final String GRAY = "§7";
      static final String AQUA = "§b";
      static final String LIGHT_PURPLE = "§d";
      static final String YELLOW = "§e";
      static final String RED = "§c";
      static final String BLUE = "§9";
      static final String WHITE = "§f";
      static final String DARK_GRAY = "§8";

      private ChatColor() {
      }

      private static String stripColor(String input) {
         return input == null ? null : input.replaceAll("(?i)§[0-9A-FK-ORX]", "");
      }
   }

   private record JumpPadPower(int vertical, int horizontal) {
   }

   private static final class KillRewardWindow {
      private long startedAtMillis;
      private int count;

      private KillRewardWindow(long startedAtMillis) {
         this.startedAtMillis = startedAtMillis;
      }
   }

   private record MerchantOffer(Material material, int amount, String rarity, int price) {
   }

   private record MerchantSale(int quantity, int totalPrice) {
   }

   private record ShelfShopOffer(Material material, int amount, int price) {
   }

   private record ShopPasteSnapshot(
      World world,
      Map<String, String> unregisteredShelfSignatures,
      Set<String> registeredShelfSignatures,
      Map<String, String> unregisteredBarrelSignatures,
      Set<String> registeredBarrelSignatures
   ) {
   }

   private record TitleDefinition(Material icon, List<String> requiredAdvancements) {
   }
}
