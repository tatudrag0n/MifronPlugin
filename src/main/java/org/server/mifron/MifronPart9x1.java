package org.server.mifron;

import io.papermc.paper.event.player.PlayerTradeEvent;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart9x1 extends MifronPart9 {
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (this.mifron().isCentralPlazaLocation(event.getLocation())) { event.setCancelled(true); return; }
      if (!"survival".equalsIgnoreCase(event.getLocation().getWorld().getName()) && switch (event.getSpawnReason()) {
         case NATURAL, CHUNK_GEN, REINFORCEMENTS, PATROL, RAID, VILLAGE_INVASION -> true;
         default -> false;
      }) event.setCancelled(true);
   }

   protected void applyMainWorldBorder() {
      World world = Bukkit.getWorld(this.getConfig().getString("main-world.name", "world"));
      ConfigurationSection border = this.getConfig().getConfigurationSection("main-world.border");
      if (world == null) {
         String name = this.getConfig().getString("main-world.name", "world");
         world = Bukkit.createWorld(new org.bukkit.WorldCreator(name).environment(Environment.NORMAL).generator(new OceanWorldGenerator()));
      }
      if (world == null || border == null) return;
      world.getWorldBorder().setCenter(border.getDouble("center-x", 0.0D), border.getDouble("center-z", 0.0D));
      world.getWorldBorder().setSize(Math.max(1.0D, border.getDouble("size", 500.0D)));
   }

   protected boolean isCentralPlazaLocation(Location location) {
      return location != null && this.protectionService.isSpawnProtected(location);
   }
   boolean isStructureProtectedLocation(Location location) { return this.protectionService.isProtected(location); }
   boolean canBuild(Player player, Location location) { return this.protectionService.canBuild(player, location); }
   protected boolean isMifronMerchant(Entity entity) {
      return Boolean.TRUE.equals(entity.getPersistentDataContainer().get(this.merchantKey, PersistentDataType.BOOLEAN));
   }

   @EventHandler
   public void onMerchantDamage(EntityDamageEvent event) {
      if (!this.mifron().isMifronMerchant(event.getEntity())) return;
      event.getEntity().setInvulnerable(false);
      if (!this.isPlayerCausedDamage(event)) { event.setCancelled(true); event.setDamage(0.0); }
   }

   protected boolean isPlayerCausedDamage(EntityDamageEvent event) {
      if (!(event instanceof EntityDamageByEntityEvent entityDamage)) return false;
      Entity damager = entityDamage.getDamager();
      if (damager instanceof Player) return true;
      return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
   }

   @EventHandler
   public void onPlayerTrade(PlayerTradeEvent event) {
      this.mifron().addPlayerStat(event.getPlayer().getUniqueId(), "total-trades", 1);
      if (this.mifron().isMifronMerchant(event.getVillager())) {
         event.getVillager().getPersistentDataContainer().set(this.merchantTradedKey, PersistentDataType.BOOLEAN, true);
      }
   }

   @EventHandler
   public void onMerchantInteract(PlayerInteractEntityEvent event) {
      if (event.getRightClicked() instanceof AbstractVillager villager && this.mifron().isMifronMerchant(villager)) {
         event.setCancelled(true);
         villager.setAI(false);
         villager.setInvulnerable(false);
         this.openMerchantUi(event.getPlayer(), villager);
      }
   }

   protected void openMerchantUi(Player player, AbstractVillager villager) {
      this.activeMerchantViews.put(player.getUniqueId(), villager.getUniqueId());
      this.renderMerchantUi(player, villager);
   }

   protected void renderMerchantUi(Player player, AbstractVillager villager) {
      List<MerchantOffer> sellOffers = this.readMerchantOffers(villager.getUniqueId(), "sell");
      List<MerchantOffer> buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      if (sellOffers.isEmpty() || buyOffers.isEmpty()) {
         this.rerollMerchant(villager);
         buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      }
      this.activeMerchantPages.put(player.getUniqueId(), 1);
      Inventory inventory = Bukkit.createInventory(player, 27, Component.text(MERCHANT_UI_TITLE));
      inventory.setItem(18, this.mifron().named(Material.RED_STAINED_GLASS_PANE, "\u00a7c\u8cb7\u53d6\u5c02\u7528", List.of("\u00a77\u30a2\u30a4\u30c6\u30e0\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u5546\u4eba\u306b\u58f2\u5374")));
      for (int i = 0; i < Math.min(18, buyOffers.size()); i++) {
         inventory.setItem(i, this.mifron().createMerchantOfferIcon(villager, buyOffers.get(i), "buy"));
      }
      inventory.setItem(26, this.mifron().named(Material.BARRIER, "\u00a77\u8cb7\u53d6\u5c02\u7528", List.of("\u00a77\u8ca9\u58f2\u6a5f\u80fd\u306f\u3042\u308a\u307e\u305b\u3093\u3002")));
      player.openInventory(inventory);
   }

   protected ItemStack createMerchantNavigationIcon(Material material, String name, String action, UUID merchantId) {
      ItemStack item = this.mifron().named(material, name, List.of("\u00a77\u30af\u30ea\u30c3\u30af\u3067\u5207\u308a\u66ff\u3048"));
      ItemMeta meta = item.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.uiActionKey, PersistentDataType.STRING, action);
      container.set(this.uiTargetKey, PersistentDataType.STRING, merchantId.toString());
      item.setItemMeta(meta);
      return item;
   }

   protected void handleMerchantNavigation(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      if (!"merchant_sell".equals(action) && !"merchant_buy".equals(action)) return;
      UUID merchantId = this.mifron().getUiTarget(clicked);
      if (merchantId == null) return;
      Entity entity = this.mifron().findEntity(merchantId);
      if (!(entity instanceof AbstractVillager villager) || !this.mifron().isMifronMerchant(entity)) {
         player.sendMessage("\u00a7c\u5546\u4eba\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093\u3002");
         player.closeInventory();
         return;
      }
      this.activeMerchantPages.put(player.getUniqueId(), "merchant_buy".equals(action) ? 1 : 0);
      this.renderMerchantUi(player, villager);
   }
}
