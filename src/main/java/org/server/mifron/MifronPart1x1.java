package org.server.mifron;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

abstract class MifronPart1x1 extends MifronPart1 {
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
      this.getConfig().addDefault("survival-dimensions.enabled", true);
      this.getConfig().addDefault("survival-dimensions.overworld", "survival");
      this.getConfig().addDefault("survival-dimensions.nether", "survival_nether");
      this.getConfig().addDefault("survival-dimensions.end", "survival_the_end");
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      this.applyServerMotd();
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
      this.runStartupStep("register shop block events", () -> Bukkit.getPluginManager().registerEvents(this.shopBlockFeature, this));
      this.runStartupStep("register quest proposal events", () -> Bukkit.getPluginManager().registerEvents(this.questProposalFeature, this));
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
      this.runStartupStep("apply main world border", this::applyMainWorldBorder);
      this.runStartupStep("register online shop", () -> {
         this.onlineShopFeature = new OnlineShopFeature(this);
         Bukkit.getPluginManager().registerEvents(this.onlineShopFeature, this);
      });
      this.runStartupStep("apply world spawn locations", this::applyWorldSpawnLocations);
      this.runStartupStep("normalize merchants", this::normalizeMerchants);
      Bukkit.getScheduler().runTaskTimer(this, this.worldRulesFeature::enforceFixedDayWorlds, 1L, 100L);
      Bukkit.getScheduler().runTaskTimer(this, this::grantPlaytimeRewards, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this::tickMerchants, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this::tickShelfShopActionBars, 10L, 10L);
      Bukkit.getScheduler().runTaskTimer(this, this::cleanupInactiveShops, 20L, 20L * 60L * 60L * 24L);
      if (this.getConfig().getBoolean("regen.monthly-enabled", true)) {
         long days = Math.max(1L, Math.min(365L, this.getConfig().getLong("regen.interval-days", 30L)));
         long period = days * 20L * 60L * 60L * 24L;
         Bukkit.getScheduler().runTaskTimer(this, this.chunkProtectionFeature::runMonthlyMaintenance, period, period);
      }
      InventoryGroupFeature.install(this);
   }
}
