package org.server.mifron;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart2x1 extends MifronPart2 {
   protected void trackFirstAction(Player player) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      if (section.getBoolean("analytics.first-action-recorded", false)) return;
      section.set("analytics.first-action-recorded", true);
      this.mifron().trackAnalytics(player, "first_action", "first-action:" + player.getUniqueId());
      this.queueDataSave();
   }

   void saveData() {
      if (this.data == null || this.dataFile == null) return;
      File parent = this.dataFile.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         this.getLogger().severe("Could not create data folder: " + parent.getAbsolutePath());
         return;
      }
      File tempFile = new File(parent == null ? new File(".") : parent, this.dataFile.getName() + ".tmp");
      try {
         this.data.save(tempFile);
         try { Files.move(tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
         catch (AtomicMoveNotSupportedException e) { Files.move(tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING); }
      } catch (IOException e) {
         this.getLogger().severe("Could not save data.yml: " + e.getMessage());
         if (tempFile.exists() && !tempFile.delete()) this.getLogger().warning("Could not delete temp data file");
      }
   }

   FileConfiguration data() { return this.data; }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.mifron().startPlayerSession(player);
      ConfigurationSection session = this.mifron().getPlayerSection(player.getUniqueId());
      String sessionId = player.getUniqueId() + ":" + System.currentTimeMillis();
      session.set("analytics.session-id", sessionId);
      session.set("analytics.session-start", System.currentTimeMillis());
      boolean firstJoin = !player.hasPlayedBefore();
      if (firstJoin) session.set("tutorial.auto-pending", true);
      this.minoruBridgeFeature.sendAnalyticsEvent(player, firstJoin ? "first_join" : "return_join", sessionId, "join:" + player.getUniqueId());
      this.minoruBridgeFeature.sendAnalyticsEvent(player, "play_session_start", sessionId, "session-start:" + sessionId);
      this.flushPendingFirstMpEvent(player);
      this.queueDataSave();
      this.mifron().giveInitialItems(player);
      this.mifron().applyPendingAdvancementReset(player);
      this.mifron().handleLoginReward(player);
      this.mifron().routeByWarningLevel(player);
      this.mifron().refreshPlayerName(player);
      Bukkit.getScheduler().runTaskLater(this, () -> this.mifron().syncAdvancementState(player), 20L);
      if (this.mifron().isFirstJoin(player)) Bukkit.getScheduler().runTaskLater(this, () -> this.mifron().startTutorial(player, false), 40L);
   }

   ItemStack createOnlineShopProduct(String id) {
      if ("shelf_shop_wand".equalsIgnoreCase(id)) return this.utilityItemsFeature.createShopWand(ShopWandType.SHELF);
      if ("barrel_shop_wand".equalsIgnoreCase(id)) return this.utilityItemsFeature.createShopWand(ShopWandType.BARREL);
      if ("sell-shelf".equalsIgnoreCase(id) || "sell-shelf-shop".equalsIgnoreCase(id)) return this.createShopBlockItem("SELL_SHELF", Material.OAK_SHELF, "\u00a7a\u8ca9\u58f2\u68da\u30b7\u30e7\u30c3\u30d7");
      if ("buy-shelf".equalsIgnoreCase(id) || "buy-shelf-shop".equalsIgnoreCase(id)) return this.createShopBlockItem("BUY_SHELF", Material.SPRUCE_SHELF, "\u00a76\u8cb7\u53d6\u68da\u30b7\u30e7\u30c3\u30d7");
      if ("sell-barrel".equalsIgnoreCase(id) || "sell-barrel-shop".equalsIgnoreCase(id)) return this.createShopBlockItem("SELL_BARREL", Material.BARREL, "\u00a79\u8ca9\u58f2\u6a3d\u30b7\u30e7\u30c3\u30d7");
      if ("buy-barrel".equalsIgnoreCase(id) || "buy-barrel-shop".equalsIgnoreCase(id)) return this.createShopBlockItem("BUY_BARREL", Material.HOPPER, "\u00a7c\u8cb7\u53d6\u6a3d\u30b7\u30e7\u30c3\u30d7");
      Material material = Material.matchMaterial(this.getConfig().getString("online-shop.items." + id + ".material", "PAPER"));
      return material == null ? null : new ItemStack(material);
   }

   protected ItemStack createShopBlockItem(String shopType, Material material, String displayName) {
      ItemStack item = new ItemStack(material);
      var meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(displayName);
         meta.getPersistentDataContainer().set(new NamespacedKey(this, "shop_type"), PersistentDataType.STRING, shopType);
         item.setItemMeta(meta);
      }
      return item;
   }

   public boolean canPlaceShopBlock(Player player) { return this.shopBlockFeature == null || this.shopBlockFeature.canPlaceShopBlock(player); }
   public void recordShopBlockPlacement(Player player) { if (this.shopBlockFeature != null) this.shopBlockFeature.recordShopBlockPlacement(player); }
   void grantOnlineShopProduct(Player player, ItemStack item) { if (player != null && item != null) player.getInventory().addItem(item); }
   boolean canReceiveOnlineShopProduct(Player player, ItemStack item) { return player != null && item != null && this.mifron().inventorySpaceFor(player, item.getType()) >= item.getAmount(); }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getPlayer().getWorld() != null && "survival".equalsIgnoreCase(event.getPlayer().getWorld().getName())) {
         World survival = Bukkit.getWorld(this.getConfig().getString("survival-dimensions.overworld", "survival"));
         if (survival != null) event.setRespawnLocation(new Location(survival, 0.5D, 100.0D, 0.5D, 0.0F, 0.0F));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerBanCommand(PlayerCommandPreprocessEvent event) {
      String target = this.banTarget(event.getMessage());
      if (target != null && event.getPlayer().hasPermission("mifron.admin")) this.scheduleCoreProtectRollback(target);
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onConsoleBanCommand(ServerCommandEvent event) {
      String target = this.banTarget("/" + event.getCommand());
      if (target != null) this.scheduleCoreProtectRollback(target);
   }

   protected String banTarget(String command) {
      if (command == null) return null;
      String[] parts = command.trim().replaceFirst("^/", "").split("\\s+");
      if (parts.length < 2 || !(parts[0].equalsIgnoreCase("ban") || parts[0].equalsIgnoreCase("minecraft:ban"))) return null;
      return parts[1].replaceAll("[^A-Za-z0-9_\\-]", "");
   }

   protected void scheduleCoreProtectRollback(String playerName) {
      if (!this.getConfig().getBoolean("ban-rollback.enabled", true) || playerName.isBlank() || Bukkit.getPluginManager().getPlugin("CoreProtect") == null) return;
      String time = this.getConfig().getString("ban-rollback.time", "3650d");
      Bukkit.getScheduler().runTask(this, () -> {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "co rollback u:" + playerName + " t:" + time);
         this.getLogger().info("CoreProtect rollback requested for banned player " + playerName);
      });
   }
}
