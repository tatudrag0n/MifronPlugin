package org.server.mifron;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

abstract class MifronPart1x1 extends MifronPart1 {
   /**
    * Registers every @EventHandler declared anywhere in the Mifron
    * inheritance chain. Bukkit's registerEvents(listener) is only guaranteed
    * to inspect the runtime class itself; Mifron declares zero handlers
    * directly (they all live in MifronPart* superclasses), so a server build
    * that skips hierarchy traversal would silently leave every one of them
    * dead — exactly the "GUIs open but no click works, no errors" failure.
    * Walking the hierarchy here makes registration version-proof.
    */
   private void registerMifronSelfEvents() {
      int registered = 0;
      for (Class<?> cursor = this.getClass(); cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
         for (Method method : cursor.getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
               this.getLogger().warning("Skipping invalid event handler: " + cursor.getSimpleName() + "#" + method.getName());
               continue;
            }
            Class<? extends Event> eventClass = params[0].asSubclass(Event.class);
            EventPriority priority = annotation.priority();
            boolean ignoreCancelled = annotation.ignoreCancelled();
            method.setAccessible(true);
            Bukkit.getPluginManager().registerEvent(eventClass, this, priority,
               (listener, event) -> {
                  if (!eventClass.isInstance(event)) return;
                  try {
                     method.invoke(this, event);
                  } catch (InvocationTargetException e) {
                     throw new EventException(e.getCause());
                  } catch (Throwable t) {
                     throw new EventException(t);
                  }
               }, this, ignoreCancelled);
            registered++;
         }
      }
      this.getLogger().info("Registered " + registered + " Mifron self event handlers across the class hierarchy.");
   }

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
      this.getConfig().addDefault("quests.site-sync.enabled", true);
      this.getConfig().addDefault("quests.site-sync.url", "https://mifron.mct-official.com/api/quests");
      this.getConfig().addDefault("quests.site-sync.cache-seconds", 600L);
      this.getConfig().addDefault("chunk-protection.enabled", true);
      this.getConfig().addDefault("chunk-protection.discord-channel-id", "1538131813497700352");
      this.getConfig().addDefault("chunk-protection.warn-days-before", 7);
      this.getConfig().addDefault("chunk-protection.min-base-blocks", 8);
      this.getConfig().addDefault("chunk-protection.reject-protect-days", 90);
      this.getConfig().addDefault("chunk-protection.approved-protect-days", 0);
      this.getConfig().addDefault("chunk-protection.scan-interval-minutes", 60);
      this.getConfig().addDefault("advanced-enchanting.enabled", true);
      this.getConfig().addDefault("advanced-enchanting.allowed-worlds", List.of("survival"));
      this.getConfig().addDefault("survival-dimensions.enabled", true);
      this.getConfig().addDefault("survival-dimensions.overworld", "survival");
      this.getConfig().addDefault("survival-dimensions.nether", "survival_nether");
      this.getConfig().addDefault("survival-dimensions.end", "survival_the_end");
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      this.mifron().applyServerMotd();
      this.mifron().runStartupStep("migrate barrel shop offer slots", this.mifron()::migrateBarrelShopOfferSlots);
      this.mifron().runStartupStep("migrate hub location", this.mifron()::migrateDefaultHubLocation);
      this.mifron().runStartupStep("migrate minigame location", this.mifron()::migrateDefaultMinigameLocation);
      this.mifron().runStartupStep("configure survival spawn location", this.mifron()::configureSurvivalSpawnLocation);
      this.mifron().runStartupStep("normalize spawn locations to origin", this.mifron()::normalizeSpawnLocationsToOrigin);
      this.mifron().runStartupStep("load economy price table", this.economyPriceTable::load);
      this.mifron().runStartupStep("load quest definitions", this.questService::load);
      this.mifron().runStartupStep("refresh site quest sync", this.siteQuestService::refreshIfStale);
      this.mifron().runStartupStep("load shop prices", this.mifron()::loadShopPrices);
      this.mifron().runStartupStep("apply economy price table", this.mifron()::applyEconomyPriceTable);
      this.mifron().runStartupStep("cache shelf shop catalog", this.mifron()::rebuildShelfShopCatalog);
      this.mifron().loadData();
      this.mifron().runStartupStep("ensure Survival dimensions", this.survivalDimensionFeature::ensureWorlds);
      this.mifron().runStartupStep("load build worlds", this.buildWorldManager::load);
      this.mifron().runStartupStep("start Minoru bridge API", this.minoruBridgeFeature::start);
      this.mifron().runStartupStep("sync shelf shop displays", this.mifron()::syncShelfShopDisplays);
      this.mifron().runStartupStep("load structures", this.structureManager::load);
      this.mifron().runStartupStep("load proposals", this.proposalManager::load);
      this.mifron().runStartupStep("load text displays", this.textDisplayFeature::load);
      this.mifron().runStartupStep("load FFA", this.ffaManager::load);
      this.mifron().runStartupStep("load athletic", this.athleticManager::load);
      this.mifron().runStartupStep("start auction settlement", this.auctionFeature::start);
      this.mifron().runStartupStep("register Mifron events", this::registerMifronSelfEvents);
      this.mifron().runStartupStep("register chunk protection events", () -> Bukkit.getPluginManager().registerEvents(this.chunkProtectionFeature, this));
      this.mifron().runStartupStep("register protected interaction events", () -> Bukkit.getPluginManager().registerEvents(this.protectedInteractionListener, this));
      this.mifron().runStartupStep("register quest progress events", () -> Bukkit.getPluginManager().registerEvents(this.questProgressListener, this));
      this.mifron().runStartupStep("register auction events", () -> Bukkit.getPluginManager().registerEvents(this.auctionFeature, this));
      this.mifron().runStartupStep("register structure events", () -> Bukkit.getPluginManager().registerEvents(this.structureManager, this));
      this.mifron().runStartupStep("register build world events", () -> Bukkit.getPluginManager().registerEvents(this.buildWorldManager, this));
      this.mifron().runStartupStep("register FFA events", () -> Bukkit.getPluginManager().registerEvents(this.ffaListener, this));
      this.mifron().runStartupStep("register text display events", () -> Bukkit.getPluginManager().registerEvents(this.textDisplayFeature, this));
      this.mifron().runStartupStep("register server portal events", () -> Bukkit.getPluginManager().registerEvents(this.serverPortalFeature, this));
      this.mifron().runStartupStep("register Survival dimension portal events", () -> Bukkit.getPluginManager().registerEvents(this.survivalDimensionFeature, this));
      this.mifron().runStartupStep("register slot machine events", () -> Bukkit.getPluginManager().registerEvents(this.slotMachineManager, this));
      this.mifron().runStartupStep("register athletic events", () -> Bukkit.getPluginManager().registerEvents(this.athleticManager, this));
      this.mifron().runStartupStep("register compass events", () -> Bukkit.getPluginManager().registerEvents(this.compassFeature, this));
      this.mifron().runStartupStep("register utility item events", () -> Bukkit.getPluginManager().registerEvents(this.utilityItemsFeature, this));
      this.mifron().runStartupStep("register advanced anvil events", () -> Bukkit.getPluginManager().registerEvents(this.advancedAnvilFeature, this));
      this.mifron().runStartupStep("register shop block events", () -> Bukkit.getPluginManager().registerEvents(this.shopBlockFeature, this));
      this.mifron().runStartupStep("register quest proposal events", () -> Bukkit.getPluginManager().registerEvents(this.questProposalFeature, this));
      this.mifron().runStartupStep("register world policy events", () -> Bukkit.getPluginManager().registerEvents(this.worldPolicyFeature, this));
      this.mifron().runStartupStep("register special item events", () -> Bukkit.getPluginManager().registerEvents(this.specialItemsFeature, this));
      this.mifron().runStartupStep("register elite mob events", () -> Bukkit.getPluginManager().registerEvents(this.eliteMobFeature, this));
      this.mifron().runStartupStep("register store item events", () -> Bukkit.getPluginManager().registerEvents(this.storeItemFeature, this));
      this.mifron().runStartupStep("register enchant max display events", () -> Bukkit.getPluginManager().registerEvents(this.enchantMaxDisplayFeature, this));
      this.mifron().runStartupStep("register friend chat events", () -> Bukkit.getPluginManager().registerEvents(this.friendChatService, this));
      if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
         this.bedrockUiFeature = new BedrockUiFeature((Mifron) this);
         this.getLogger().info("Bedrock mobile Forms UI enabled through Geyser.");
      }
      this.bindPluginCommand("mifron");
      this.bindPluginCommand("mf");
      this.bindPluginCommand("friend");
      this.bindPluginCommand("status");
      this.bindPluginCommand("tutorial");
      this.bindPluginCommand("mshop");
      this.mifron().runStartupStep("apply world rules", this.worldRulesFeature::apply);
      this.mifron().runStartupStep("apply main world border", this.mifron()::applyMainWorldBorder);
      this.mifron().runStartupStep("register online shop", () -> {
         this.onlineShopFeature = new OnlineShopFeature((Mifron) this);
         Bukkit.getPluginManager().registerEvents(this.onlineShopFeature, this);
      });
      this.mifron().runStartupStep("apply world policy", this.worldPolicyFeature::apply);
      this.mifron().runStartupStep("apply world spawn locations", this.mifron()::applyWorldSpawnLocations);
      this.mifron().runStartupStep("normalize merchants", this.mifron()::normalizeMerchants);
      Bukkit.getScheduler().runTaskTimer(this, this.worldRulesFeature::enforceFixedDayWorlds, 1L, 100L);
      Bukkit.getScheduler().runTaskTimer(this, this.mifron()::grantPlaytimeRewards, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this.mifron()::tickMerchants, 1200L, 1200L);
      Bukkit.getScheduler().runTaskTimer(this, this.mifron()::tickShelfShopActionBars, 10L, 10L);
      Bukkit.getScheduler().runTaskTimer(this, this.mifron()::cleanupInactiveShops, 20L, 20L * 60L * 60L * 24L);
      if (this.getConfig().getBoolean("regen.monthly-enabled", true)) {
         long days = Math.max(1L, Math.min(365L, this.getConfig().getLong("regen.interval-days", 30L)));
         long period = days * 20L * 60L * 60L * 24L;
         Bukkit.getScheduler().runTaskTimer(this, this.chunkProtectionFeature::runMonthlyMaintenance, period, period);
         this.mifron().data().set("regen.next-run-at", System.currentTimeMillis() + period * 50L);
      }
      this.mifron().runStartupStep("schedule chunk protection scan", this.chunkProtectionFeature::scheduleScan);
      this.mifron().runStartupStep("start jump pad task", this.mifron()::startJumpPadTask);
      InventoryGroupFeature.install(this);
   }
}
