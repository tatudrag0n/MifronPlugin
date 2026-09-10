package org.server.mifron;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

abstract class MifronBase extends JavaPlugin implements Listener, TabExecutor {
   protected static final String FRIEND_UI_TITLE = "\u00a73Mifron Friends";
   protected static final String FRIEND_STATUS_UI_TITLE = "\u00a72Mifron Status";
   protected static final String QUEST_UI_TITLE = "\u00a7bMifron Quests";
   protected static final String TELEPORT_UI_TITLE = "\u00a75Mifron Teleporter";
   protected static final String MERCHANT_UI_TITLE = "\u00a76Mifron Merchant";
   protected static final long MERCHANT_REROLL_MILLIS = 3600000L;
   protected static final long MERCHANT_TRANSACTION_COOLDOWN_MILLIS = 150L;
   protected static final long SHOP_WAND_ACTION_COOLDOWN_MILLIS = 400L;
   protected static final long JUMP_PAD_COOLDOWN_MILLIS = 650L;
   protected static final long JUMP_PAD_FALL_PROTECTION_MILLIS = 60000L;
   protected static final int MAX_JUMP_PAD_POWER = 100;
   protected static final int MAX_EMERALDS = 2000000000;
   protected static final int MAX_FRIEND_REQUESTS = 100;
   protected static final int MAX_OFFLINE_MESSAGES = 50;
   protected static final int MAX_FRIEND_MESSAGE_LENGTH = 256;
   protected static final int MAX_FRIEND_FILTER_LENGTH = 32;
   protected static final int MAX_SHOP_STACKS_PER_CLICK = 64;
   protected static final int BARREL_SHOP_OFFER_SLOTS = 27;
   protected static final int SHELF_SHOP_OFFER_SLOTS = 3;
   protected static final int MOB_REWARD_FARM_THRESHOLD_PER_HOUR = 100;
   protected static final Map<String, TitleDefinition> TITLE_DEFINITIONS = Map.ofEntries(
      Map.entry("sniper", new TitleDefinition(Material.BOW, List.of("adventure/sniper_duel", "adventure/bullseye"))),
      Map.entry("hunter", new TitleDefinition(Material.CROSSBOW, List.of("adventure/two_birds_one_arrow", "adventure/arbalistic"))),
      Map.entry("hero", new TitleDefinition(Material.DIAMOND_SWORD, List.of("adventure/kill_all_mobs"))),
      Map.entry("omniscient", new TitleDefinition(Material.KNOWLEDGE_BOOK, List.of()))
   );
   protected static final Set<Material> MERCHANT_EXCLUDED_ITEMS = Set.of(
      Material.AIR, Material.BARRIER, Material.BEDROCK, Material.COMMAND_BLOCK,
      Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
      Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.JIGSAW, Material.LIGHT,
      Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK, Material.SPAWNER, Material.DRAGON_EGG
   );
   protected static final Map<String, Integer> MOB_KILL_REWARDS = Map.ofEntries(
      Map.entry("BEE", 5), Map.entry("BLAZE", 25), Map.entry("BOGGED", 20), Map.entry("BREEZE", 25),
      Map.entry("CAVE_SPIDER", 15), Map.entry("CREAKING", 30), Map.entry("CREEPER", 12), Map.entry("DOLPHIN", 5),
      Map.entry("DROWNED", 10), Map.entry("ELDER_GUARDIAN", 80), Map.entry("ENDER_DRAGON", 900), Map.entry("ENDERMAN", 25),
      Map.entry("ENDERMITE", 4), Map.entry("EVOKER", 40), Map.entry("GHAST", 22), Map.entry("GOAT", 5),
      Map.entry("GUARDIAN", 18), Map.entry("HOGLIN", 25), Map.entry("HUSK", 10), Map.entry("LLAMA", 5),
      Map.entry("MAGMA_CUBE", 8), Map.entry("NAUTILUS", 40), Map.entry("PHANTOM", 15), Map.entry("PIGLIN", 15),
      Map.entry("PIGLIN_BRUTE", 45), Map.entry("PILLAGER", 20), Map.entry("POLAR_BEAR", 5), Map.entry("RAVAGER", 50),
      Map.entry("SHULKER", 35), Map.entry("SILVERFISH", 4), Map.entry("SKELETON", 10), Map.entry("SLIME", 6),
      Map.entry("SPIDER", 10), Map.entry("STRAY", 15), Map.entry("SULFUR_CUBE", 25), Map.entry("TRADER_LLAMA", 5),
      Map.entry("VEX", 30), Map.entry("VINDICATOR", 45), Map.entry("WARDEN", 250), Map.entry("WITCH", 25),
      Map.entry("WITHER", 650), Map.entry("WITHER_SKELETON", 30), Map.entry("ZOGLIN", 40), Map.entry("ZOMBIE", 10),
      Map.entry("ZOMBIE_VILLAGER", 10), Map.entry("ZOMBIFIED_PIGLIN", 8)
   );
   protected NamespacedKey mifronItemKey;
   protected NamespacedKey merchantKey;
   protected NamespacedKey merchantSpawnKey;
   protected NamespacedKey merchantTradedKey;
   protected NamespacedKey merchantTypeKey;
   protected NamespacedKey merchantOfferKey;
   protected NamespacedKey merchantOfferPriceKey;
   protected NamespacedKey merchantOfferMaterialKey;
   protected NamespacedKey merchantOfferAmountKey;
   protected NamespacedKey merchantOfferMerchantKey;
   protected NamespacedKey merchantOfferActionKey;
   protected NamespacedKey merchantOfferRarityKey;
   protected NamespacedKey barrelOfferPriceKey;
   protected NamespacedKey barrelOfferRarityKey;
   protected NamespacedKey ffaEntityKindKey;
   protected NamespacedKey reincarnationStarKey;
   protected NamespacedKey uiActionKey;
   protected NamespacedKey uiTargetKey;
   protected File dataFile;
   protected FileConfiguration data;
   protected final EconomyPriceTable economyPriceTable = new EconomyPriceTable((Mifron) this);
   protected final QuestService questService = new QuestService((Mifron) this);
   protected final ChunkProtectionFeature chunkProtectionFeature = new ChunkProtectionFeature((Mifron) this);
   protected final ProtectionService protectionService = new ProtectionService((Mifron) this, this.chunkProtectionFeature);
   protected final ServerPortalFeature serverPortalFeature = new ServerPortalFeature((Mifron) this);
   protected final SurvivalDimensionFeature survivalDimensionFeature = new SurvivalDimensionFeature((Mifron) this);
   protected final CompassFeature compassFeature = new CompassFeature((Mifron) this);
   protected final WorldRulesFeature worldRulesFeature = new WorldRulesFeature((Mifron) this);
   protected final UtilityItemsFeature utilityItemsFeature = new UtilityItemsFeature((Mifron) this);
   protected final AdvancedAnvilFeature advancedAnvilFeature = new AdvancedAnvilFeature((Mifron) this);
   protected BedrockUiFeature bedrockUiFeature;
   protected final TextDisplayFeature textDisplayFeature = new TextDisplayFeature((Mifron) this);
   protected final AuctionFeature auctionFeature = new AuctionFeature((Mifron) this, this.economyPriceTable);
   protected final BuildWorldManager buildWorldManager = new BuildWorldManager((Mifron) this);
   protected final StructureManager structureManager = new StructureManager((Mifron) this);
   protected final ProposalManager proposalManager = new ProposalManager((Mifron) this);
   protected final QuestProposalFeature questProposalFeature = new QuestProposalFeature((Mifron) this, this.proposalManager);
   protected final QuestProgressListener questProgressListener = new QuestProgressListener((Mifron) this, this.questService);
   protected final ProtectedInteractionListener protectedInteractionListener = new ProtectedInteractionListener((Mifron) this, this.protectionService);
   protected final FfaManager ffaManager = new FfaManager((Mifron) this);
   protected final FfaListener ffaListener = new FfaListener((Mifron) this, this.ffaManager);
   protected final SlotMachineManager slotMachineManager = new SlotMachineManager((Mifron) this);
   protected final AthleticManager athleticManager = new AthleticManager((Mifron) this);
   protected final MinoruBridgeFeature minoruBridgeFeature = new MinoruBridgeFeature((Mifron) this);
   protected final ShopBlockFeature shopBlockFeature = new ShopBlockFeature((Mifron) this);
   protected OnlineShopFeature onlineShopFeature;
   protected final Random random = new Random();
   protected final Map<String, Integer> shopSalePrices = new HashMap<>();
   protected final Map<String, Integer> shopBuyPrices = new HashMap<>();
   protected final Map<String, Integer> merchantBuyWeights = new HashMap<>();
   protected final Map<String, Integer> merchantSellWeights = new HashMap<>();
   protected final Map<String, BarrelShopConfig> barrelShopConfigs = new HashMap<>();
   protected final Map<UUID, String> friendSearchFilters = new ConcurrentHashMap<>();
   protected final Map<UUID, String> pendingFriendSearch = new ConcurrentHashMap<>();
   protected final Map<UUID, UUID> pendingFriendChatInput = new ConcurrentHashMap<>();
   protected final Map<UUID, UUID> activeFriendChatTarget = new ConcurrentHashMap<>();
   protected final Map<UUID, String> friendChatDrafts = new ConcurrentHashMap<>();
   protected final Set<UUID> merchantTransactions = ConcurrentHashMap.newKeySet();
   protected final Map<UUID, Long> lastMerchantTransaction = new ConcurrentHashMap<>();
   protected final Map<UUID, UUID> activeMerchantViews = new ConcurrentHashMap<>();
   protected final Map<UUID, Integer> activeMerchantPages = new ConcurrentHashMap<>();
   protected final Map<UUID, String> temporaryActionBarMessages = new ConcurrentHashMap<>();
   protected final Set<UUID> activeTutorials = ConcurrentHashMap.newKeySet();
   protected final Map<UUID, Long> temporaryActionBarUntil = new ConcurrentHashMap<>();
   protected final Map<UUID, Long> shelfShopTransactionUntil = new ConcurrentHashMap<>();
   protected final Map<UUID, Long> shopWandActionUntil = new ConcurrentHashMap<>();
   protected final Map<UUID, Map<String, KillRewardWindow>> mobRewardWindows = new ConcurrentHashMap<>();
   protected final Map<UUID, Long> lastJumpPadUse = new ConcurrentHashMap<>();
   protected final Map<UUID, Long> jumpPadFallProtectionUntil = new ConcurrentHashMap<>();
   protected BukkitTask scheduledShutdownTask;
   protected BukkitTask pendingDataSaveTask;
   protected List<Material> shelfShopCatalog = List.of();
   protected Map<Material, Integer> shelfShopCatalogNumbers = Map.of();
   protected boolean shelfShopCatalogReady;
   protected final Object shutdownLock = new Object();

   boolean isPlayerBuildWorld(Player player) {
      return player != null && this.buildWorldManager.isOwner(player, player.getWorld());
   }

   protected record BarrelShopConfig(String tier, int weight) {}
   static final class ChatColor {
      static final String DARK_AQUA = "\u00a73";
      static final String DARK_GREEN = "\u00a72";
      static final String DARK_PURPLE = "\u00a75";
      static final String GOLD = "\u00a76";
      static final String GREEN = "\u00a7a";
      static final String GRAY = "\u00a77";
      static final String AQUA = "\u00a7b";
      static final String LIGHT_PURPLE = "\u00a7d";
      static final String YELLOW = "\u00a7e";
      static final String RED = "\u00a7c";
      static final String BLUE = "\u00a79";
      static final String WHITE = "\u00a7f";
      static final String DARK_GRAY = "\u00a78";
      private ChatColor() {}
      private static String stripColor(String input) {
         return input == null ? null : input.replaceAll("(?i)\u00a7[0-9A-FK-ORX]", "");
      }
   }
   protected record JumpPadPower(int vertical, int horizontal) {}
   protected static final class KillRewardWindow {
      private long startedAtMillis;
      private int count;
      private KillRewardWindow(long startedAtMillis) { this.startedAtMillis = startedAtMillis; }
   }
   protected record MerchantOffer(Material material, int amount, String rarity, int price) {}
   protected record MerchantSale(int quantity, int totalPrice) {}
   protected record ShelfShopOffer(Material material, int amount, int price, String mode) {}
   protected record ShopPasteSnapshot(
      World world,
      Map<String, String> unregisteredShelfSignatures,
      Set<String> registeredShelfSignatures,
      Map<String, String> unregisteredBarrelSignatures,
      Set<String> registeredBarrelSignatures
   ) {}
   protected record TitleDefinition(Material icon, List<String> requiredAdvancements) {}
}
