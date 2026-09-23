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
         // New main worlds generate as a fully flat bedrock plane at Y=-64
         // with no structures (MainFlatGenerator). Existing worlds are never
         // modified here.
         world = Bukkit.createWorld(new org.bukkit.WorldCreator(name).environment(Environment.NORMAL).generator(new MainFlatGenerator()));
      }
      if (world == null || border == null) return;
      world.getWorldBorder().setCenter(border.getDouble("center-x", 0.0D), border.getDouble("center-z", 0.0D));
      world.getWorldBorder().setSize(Math.max(1.0D, border.getDouble("size", 500.0D)));
   }

   /**
    * {@code /mf main approve [all|<player>] [<player>]}: approves pending
    * main-world blocks (formal save, then fixed). Admin only.
    */
   protected boolean handleMainCommand(org.bukkit.command.CommandSender sender, String[] args) {
      String sub = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
      // Player submissions: schematic file + coordinates for admin placement.
      if ("submit".equals(sub)) {
         if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ実行できます。");
            return true;
         }
         if (args.length < 6) {
            sender.sendMessage("§c使い方: /mf main submit <schematic> <x> <y> <z>");
            sender.sendMessage("§7schematicは plugins/WorldEdit/schematics 内のファイル名");
            return true;
         }
         double x, y, z;
         try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
         } catch (NumberFormatException bad) {
            sender.sendMessage("§c座標は数値で指定してください。");
            return true;
         }
         int id = this.mainWorldFeature.submitBuild(player.getUniqueId(), args[2], x, y, z);
         if (id == -1) sender.sendMessage("§cファイル名が不正です。");
         else if (id == -2) sender.sendMessage("§cそのschematicが見つかりません。先にファイルを配置してください。");
         else if (id == -3) sender.sendMessage("§cY座標は-64〜320の範囲で指定してください。");
         else sender.sendMessage("§a建築申請 #" + id + " を受け付けました。承認後に管理者が設置します。");
         return true;
      }
      if ("submissions".equals(sub)) {
         boolean admin = sender.hasPermission("mifron.admin");
         String viewer = sender instanceof Player player ? player.getUniqueId().toString() : null;
         java.util.List<String> lines = this.mainWorldFeature.submissionLines(!admin, admin ? null : viewer);
         if (lines.isEmpty()) sender.sendMessage("§7申請はありません。");
         else for (String line : lines) sender.sendMessage("§e" + line);
         return true;
      }
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
         return true;
      }
      if ("approve-sub".equals(sub)) {
         if (args.length < 3) {
            sender.sendMessage("§c使い方: /mf main approve-sub <id>");
            return true;
         }
         try {
            sender.sendMessage("§a" + this.mainWorldFeature.approveSubmission(Integer.parseInt(args[2])));
         } catch (NumberFormatException bad) {
            sender.sendMessage("§cIDは数値で指定してください。");
         }
         return true;
      }
      if ("reject-sub".equals(sub)) {
         if (args.length < 3) {
            sender.sendMessage("§c使い方: /mf main reject-sub <id>");
            return true;
         }
         try {
            sender.sendMessage("§a" + this.mainWorldFeature.rejectSubmission(Integer.parseInt(args[2])));
         } catch (NumberFormatException bad) {
            sender.sendMessage("§cIDは数値で指定してください。");
         }
         return true;
      }
      if (args.length >= 2 && "approve".equalsIgnoreCase(args[1])) {
         String target = null;
         String targetName = null;
         if (args.length >= 3 && !"all".equalsIgnoreCase(args[2])) {
            targetName = args[2];
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline == null || offline.getUniqueId() == null) {
               sender.sendMessage("§cプレイヤーが見つかりません: " + targetName);
               return true;
            }
            target = offline.getUniqueId().toString();
         }
         int approved = this.mainWorldFeature.approvePending(target);
         if (targetName != null) sender.sendMessage("§a" + targetName + " の承認待ちブロック " + approved + " 件を承認しました。");
         else sender.sendMessage("§a承認待ちブロック " + approved + " 件を承認しました。");
         return true;
      }
      int pending = this.mainWorldFeature.pendingCount();
      sender.sendMessage("§amainワールド承認待ち: " + pending + " 件");
      sender.sendMessage("§7/mf main approve [all|<player>]");
      sender.sendMessage("§7/mf main submit <schematic> <x> <y> <z> / submissions / approve-sub <id> / reject-sub <id>");
      return true;
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
      String merchantType = villager.getPersistentDataContainer().get(this.merchantTypeKey, PersistentDataType.STRING);
      List<MerchantOffer> sellOffers = this.readMerchantOffers(villager.getUniqueId(), "sell");
      List<MerchantOffer> buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      if (sellOffers.isEmpty() || buyOffers.isEmpty()) {
         this.rerollMerchant(villager);
         sellOffers = this.readMerchantOffers(villager.getUniqueId(), "sell");
         buyOffers = this.readMerchantOffers(villager.getUniqueId(), "buy");
      }
      // Rare merchant: exactly 1 sell slot + 1 buy slot, special items only.
      if ("rare".equals(merchantType)) {
         this.activeMerchantPages.put(player.getUniqueId(), 1);
         Inventory rare = Bukkit.createInventory(player, 27, Component.text(MERCHANT_UI_TITLE));
         rare.setItem(10, this.mifron().named(Material.GOLD_INGOT, "\u00a76\u8ca9\u58f2\u67a0", List.of("\u00a77\u4e0b\u306e\u5546\u54c1\u3092\u8cfc\u5165")));
         if (!sellOffers.isEmpty()) rare.setItem(11, this.mifron().createMerchantOfferIcon(villager, sellOffers.get(0), "sell"));
         rare.setItem(14, this.mifron().named(Material.RED_STAINED_GLASS_PANE, "\u00a7c\u8cb7\u53d6\u67a0", List.of("\u00a77\u4e0b\u306e\u5546\u54c1\u3092\u58f2\u5374")));
         if (!buyOffers.isEmpty()) rare.setItem(15, this.mifron().createMerchantOfferIcon(villager, buyOffers.get(0), "buy"));
         player.openInventory(rare);
         return;
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
