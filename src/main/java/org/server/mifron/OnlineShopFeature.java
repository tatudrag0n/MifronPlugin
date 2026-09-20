package org.server.mifron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

final class OnlineShopFeature implements Listener {
   static final String TITLE = "\u00a7bMifron OnlineShop";
   // Left column (slots 0/9/18/27/36) holds the 5 genre tabs, products fill
   // columns 1-5 of rows 0-4 (25 slots per page); the bottom row navigates.
   private static final int PAGE_SIZE = 25;
   private static final int[] TAB_SLOTS = {0, 9, 18, 27, 36};
   private static final int PREV_SLOT = 45;
   private static final int MENU_SLOT = 48;
   private static final int PAGE_SLOT = 49;
   private static final int NEXT_SLOT = 53;
   private final Mifron plugin;
   private final NamespacedKey accessKey;
   private final NamespacedKey productKey;
   private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> pages = new HashMap<>();
   private final Map<UUID, Category> selected = new HashMap<>();
   private final Map<String, Long> configuredCooldowns = new HashMap<>();
   private final Map<String, String> configuredRarities = new HashMap<>();
   // Materials that currently carry a shop-imposed vanilla cooldown overlay.
   // The overlay is a shop-GUI visual; it must be cleared when the GUI closes
   // so bought food/pearls/arrows stay usable.
   private final Map<UUID, Set<Material>> overlaid = new HashMap<>();
   private final EnumMap<Category, List<ShopProduct>> catalog = new EnumMap<>(Category.class);
   private final Map<String, ShopProduct> productIndex = new HashMap<>();

   /**
    * One sellable product. Plain items have an empty variant; enchanted books
    * use {@code ENCHANT:LEVEL} (e.g. {@code SHARPNESS:5}) and potions use the
    * potion type name (e.g. {@code STRONG_HEALING}).
    */
   record ShopProduct(Material material, String variant, String label, int price) {
      String id() {
         return "item:" + material.name() + (variant.isEmpty() ? "" : "#" + variant);
      }
   }

   /** Five shop genres, tabbed in the left GUI column. */
   enum Category {
      BUILD(Material.BRICKS, "\u5efa\u7bc9"),
      MATERIAL(Material.DIAMOND, "\u7d20\u6750"),
      EQUIP(Material.IRON_SWORD, "\u88c5\u5099"),
      FOOD(Material.BREAD, "\u98df\u6599\u30fb\u30dd\u30fc\u30b7\u30e7\u30f3"),
      SPECIAL(Material.ENCHANTED_BOOK, "\u7279\u6b8a");
      final Material icon;
      final String label;
      Category(Material icon, String label) { this.icon = icon; this.label = label; }
   }

   OnlineShopFeature(Mifron plugin) {
      this.plugin = plugin;
      this.accessKey = new NamespacedKey(plugin, "item");
      this.productKey = new NamespacedKey(plugin, "online_shop_product");
      this.rebuildCatalog();
   }

   void rebuildCatalog() {
      for (Category category : Category.values()) this.catalog.put(category, new ArrayList<>());
      this.productIndex.clear();
      for (Material material : Material.values()) {
         if (!this.isPurchasable(material) || isVariantMaterial(material)) continue;
         this.addProduct(new ShopProduct(material, "", material.name(), this.priceOf(material)));
      }
      this.addEnchantedBooks();
      this.addPotions();
      for (List<ShopProduct> list : this.catalog.values()) list.sort(Comparator.comparingInt(ShopProduct::price).thenComparing(ShopProduct::id));
      this.rebuildConfiguredCooldowns();
      // Register every product in stock so monthly restock covers the table.
      try {
         for (String id : this.productIndex.keySet()) this.plugin.shopStockService.stockOf(id);
      } catch (Throwable ignored) {
      }
   }

   private void addProduct(ShopProduct product) {
      this.catalog.get(this.categoryOf(product.material())).add(product);
      this.productIndex.put(product.id(), product);
   }

   /** Variant materials are expanded per-effect below, not sold as blanks. */
   static boolean isVariantMaterial(Material material) {
      return material == Material.ENCHANTED_BOOK
         || material == Material.POTION
         || material == Material.SPLASH_POTION
         || material == Material.LINGERING_POTION
         || material == Material.TIPPED_ARROW;
   }

   private void addEnchantedBooks() {
      for (Enchantment enchantment : Registry.ENCHANTMENT) {
         String key = enchantment.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
         for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
            int price = OnlineShopRules.enchantedBookPrice(level, enchantment.isTreasure());
            String label = OnlineShopRules.prettyVariantName(enchantment.getKey().getKey())
               + " " + OnlineShopRules.romanLevel(level);
            this.addProduct(new ShopProduct(Material.ENCHANTED_BOOK, key + ":" + level, label, price));
         }
      }
   }

   private void addPotions() {
      Material[] containers = {Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW};
      for (Material container : containers) {
         for (PotionType type : PotionType.values()) {
            if ("UNCRAFTABLE".equals(type.name())) continue;
            boolean strongOrLong = type.name().startsWith("STRONG_") || type.name().startsWith("LONG_");
            int price = OnlineShopRules.potionPrice(container.name(), strongOrLong);
            String label = OnlineShopRules.prettyVariantName(type.name());
            this.addProduct(new ShopProduct(container, type.name(), label, price));
         }
      }
   }

   private void rebuildConfiguredCooldowns() {
      this.configuredCooldowns.clear();
      this.configuredRarities.clear();
      for (String section : List.of("online-shop.items", "online-shop-new.items")) {
         var root = this.plugin.getConfig().getConfigurationSection(section);
         if (root == null) continue;
         for (String key : root.getKeys(false)) {
            String materialName = root.getString(key + ".material", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null) continue;
            if (root.contains(key + ".cooldown-seconds")) {
               this.configuredCooldowns.put(material.name(), Math.max(0L, root.getLong(key + ".cooldown-seconds")));
            }
            String rarity = root.getString(key + ".rarity", "");
            if (!rarity.isBlank()) this.configuredRarities.put(material.name(), rarity);
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Bukkit.getScheduler().runTask(this.plugin, () -> this.ensureAccessItem(event.getPlayer()));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.cooldowns.remove(event.getPlayer().getUniqueId());
      this.pages.remove(event.getPlayer().getUniqueId());
      this.selected.remove(event.getPlayer().getUniqueId());
      this.overlaid.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) return;
      if (!TITLE.equals(event.getView().getTitle())) return;
      // The close event also fires when purchase() reopens the GUI, right
      // after createInventory applied a fresh overlay. Defer by one tick: if
      // the shop is open again, keep tracking; otherwise clear the overlay so
      // bought food/pearls/arrows stay usable outside the shop.
      Set<Material> snapshot = new HashSet<>(this.overlaid.getOrDefault(player.getUniqueId(), Set.of()));
      if (snapshot.isEmpty()) return;
      UUID uuid = player.getUniqueId();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         Player online = Bukkit.getPlayer(uuid);
         if (online == null || !online.isOnline()) {
            this.overlaid.remove(uuid);
            return;
         }
         if (TITLE.equals(online.getOpenInventory().getTitle())) {
            this.overlaid.computeIfAbsent(uuid, ignored -> new HashSet<>()).addAll(snapshot);
            return;
         }
         for (Material material : snapshot) {
            if (online.getCooldown(material) > 0) online.setCooldown(material, 0);
         }
         this.overlaid.computeIfPresent(uuid, (ignored, set) -> {
            set.removeAll(snapshot);
            return set.isEmpty() ? null : set;
         });
      }, 1L);
   }

   private void ensureAccessItem(Player player) {
      // The standalone shop door is retired: the menu item owns shop access
      // now. Remove leftovers instead of handing new ones out.
      PlayerInventory inventory = player.getInventory();
      for (int slot = 0; slot < inventory.getContents().length; slot++) {
         if (this.isAccessItem(inventory.getContents()[slot])) inventory.setItem(slot, null);
      }
      if (this.isAccessItem(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
   }

   void openShop(Player player) {
      if (!this.inSurvivalWorld(player)) {
         player.sendMessage(ChatColor.RED + "OnlineShop\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u3048\u307e\u3059\u3002");
         return;
      }
      player.openInventory(this.createInventory(player));
   }

   ItemStack createAccessItem() {
      ItemStack item = new ItemStack(Material.IRON_DOOR);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(ChatColor.AQUA + "OnlineShop");
      meta.setLore(List.of(ChatColor.GRAY + "\u53f3\u30af\u30ea\u30c3\u30af: \u5546\u54c1\u4e00\u89a7\u3092\u958b\u304f", ChatColor.DARK_GRAY + "Survival\u30ef\u30fc\u30eb\u30c9\u306e\u307f"));
      meta.getPersistentDataContainer().set(this.accessKey, PersistentDataType.STRING, "online_shop");
      item.setItemMeta(meta);
      return item;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onUse(PlayerInteractEvent event) {
      if (!this.isAccessItem(event.getItem())) return;
      event.setCancelled(true);
      if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
      Player player = event.getPlayer();
      if (!this.inSurvivalWorld(player)) {
         player.sendMessage(ChatColor.RED + "OnlineShop\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u3048\u307e\u3059\u3002");
         return;
      }
      player.openInventory(this.createInventory(player));
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player)) return;
      // Product icons are real items: without this, dragging them out of the
      // virtual catalogue duplicates them into the player inventory for free.
      if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (!TITLE.equals(event.getView().getTitle())) return;
      event.setCancelled(true);
      if (!this.inSurvivalWorld(player)) {
         player.closeInventory();
         player.sendMessage(ChatColor.RED + "OnlineShop\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u3048\u307e\u3059\u3002");
         return;
      }
      if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) return;
      String action = clicked.getItemMeta().getPersistentDataContainer().get(this.productKey, PersistentDataType.STRING);
      if (action == null) return;
      if (action.equals("page:prev") || action.equals("page:next")) {
         this.changePage(player, action);
         return;
      }
      if (action.startsWith("cat:")) {
         try {
            this.selected.put(player.getUniqueId(), Category.valueOf(action.substring(4)));
         } catch (IllegalArgumentException e) {
            return;
         }
         this.pages.put(player.getUniqueId(), 0);
         player.openInventory(this.createInventory(player));
         return;
      }
      if (action.equals("menu_back")) {
         this.plugin.utilityItemsFeature.openMenuUi(player);
         return;
      }
      // Left click buys; right click sells one; shift+right-click sells all.
      org.bukkit.event.inventory.ClickType click = event.getClick();
      if (click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
         this.sell(player, action, true);
      } else if (click.isRightClick()) {
         this.sell(player, action, false);
      } else {
         this.purchase(player, action);
      }
   }

   private void changePage(Player player, String action) {
      int page = this.pages.getOrDefault(player.getUniqueId(), 0);
      if (action.equals("page:next")) page++;
      else page = Math.max(0, page - 1);
      this.pages.put(player.getUniqueId(), page);
      player.openInventory(this.createInventory(player));
   }

   /** Product slot for page index i: rows 0-4, columns 1-5. */
   static int productSlot(int index) {
      return (index / 5) * 9 + 1 + (index % 5);
   }

   private Inventory createInventory(Player player) {
      Category category = this.selected.getOrDefault(player.getUniqueId(), Category.BUILD);
      List<ShopProduct> list = new ArrayList<>(this.catalog.getOrDefault(category, List.of()));
      int maxPage = Math.max(0, (list.size() - 1) / PAGE_SIZE);
      int page = Math.max(0, Math.min(maxPage, this.pages.getOrDefault(player.getUniqueId(), 0)));
      this.pages.put(player.getUniqueId(), page);
      Inventory inventory = Bukkit.createInventory(player, 54, TITLE);
      Category[] tabs = Category.values();
      for (int i = 0; i < tabs.length && i < TAB_SLOTS.length; i++) {
         inventory.setItem(TAB_SLOTS[i], this.tabIcon(tabs[i], tabs[i] == category));
      }
      int start = page * PAGE_SIZE;
      List<ShopProduct> shown = new ArrayList<>();
      for (int i = 0; i < PAGE_SIZE && start + i < list.size(); i++) {
         ShopProduct product = list.get(start + i);
         inventory.setItem(productSlot(i), this.catalogIcon(player, product));
         shown.add(product);
      }
      if (page > 0) inventory.setItem(PREV_SLOT, this.actionIcon(Material.ARROW, "\u00a7e\u524d\u306e\u30da\u30fc\u30b8", "page:prev"));
      inventory.setItem(MENU_SLOT, this.actionIcon(Material.OAK_DOOR, "\u00a7f\u30e1\u30cb\u30e5\u30fc\u306b\u623b\u308b", "menu_back"));
      inventory.setItem(PAGE_SLOT, this.actionIcon(Material.PAPER, "\u00a7e\u30da\u30fc\u30b8 " + (page + 1) + " / " + (maxPage + 1), null));
      if (page < maxPage) inventory.setItem(NEXT_SLOT, this.actionIcon(Material.ARROW, "\u00a7e\u6b21\u306e\u30da\u30fc\u30b8", "page:next"));
      this.applyCooldownOverlay(player, shown);
      return inventory;
   }

   private ItemStack tabIcon(Category tab, boolean active) {
      ItemStack item = this.actionIcon(tab.icon, (active ? "\u00a76\u25c6 " : "\u00a7f") + tab.label, "cat:" + tab.name());
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setLore(List.of(active ? "\u00a7e\u9078\u629e\u4e2d" : "\u00a77\u30af\u30ea\u30c3\u30af\u3067\u8868\u793a"));
         item.setItemMeta(meta);
      }
      return item;
   }

   /**
    * Variant goods (enchanted books, potions, ...) share one vanilla
    * {@link Material} cooldown, so a vanilla overlay would cool down every
    * sibling product when only one was bought. Those stay lore-only; plain
    * single-material goods keep the vanilla animation.
    */
   static boolean sharesVanillaCooldown(Material material) {
      return material == Material.ENCHANTED_BOOK
         || material == Material.POTION
         || material == Material.SPLASH_POTION
         || material == Material.LINGERING_POTION
         || material == Material.TIPPED_ARROW;
   }

   private void applyCooldownOverlay(Player player, List<ShopProduct> products) {
      // The vanilla item cooldown is the single source of truth and must
      // survive closing/reopening the GUI. Earlier code zeroed it from
      // InventoryCloseEvent, which ran right after createInventory set it and
      // made the cooldown animation disappear.
      for (ShopProduct product : products) {
         Material material = product.material();
         if (sharesVanillaCooldown(material)) continue;
         long remaining = this.remainingSeconds(player, product.id());
         // Only overlay an active shop cooldown. Writing a zero would wipe a
         // genuine vanilla cooldown (ender pearl and friends) for no reason.
         if (!shouldOverlayCooldown(remaining)) continue;
         player.setCooldown(material, OnlineShopRules.cooldownTicks(remaining));
         this.overlaid.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(material);
      }
   }

   /**
    * The overlay exists to visualise an active shop cooldown. With no shop
    * cooldown left, vanilla cooldowns must be left untouched.
    */
   static boolean shouldOverlayCooldown(long remainingSeconds) {
      return remainingSeconds > 0;
   }

   /** Displayed and charged prices share one basis (stock-adjusted). */
   int effectivePrice(ShopProduct product) {
      int effective = this.plugin.shopStockService.salePriceNow(product.id(), product.price());
      // Deep slate emerald ore stays the most valuable shop good even when
      // other goods spike on low stock: clamp everything else below its
      // current effective price (same basis for display and settlement).
      if (product.material() != Material.DEEPSLATE_EMERALD_ORE) {
         ShopProduct deep = this.productIndex.get("item:" + Material.DEEPSLATE_EMERALD_ORE.name());
         if (deep != null) {
            effective = Math.min(effective,
               this.plugin.shopStockService.salePriceNow(deep.id(), deep.price()));
         }
      }
      return Math.max(1, effective);
   }

   int effectiveBuyPrice(ShopProduct product) {
      // Floor 1 MP: low stock swings must never zero-out a buyback.
      return Math.max(1, this.plugin.shopStockService.buyPriceNow(product.id(), this.buyBaseOf(product)));
   }

   /**
    * Buy-back base: table buy price when set, otherwise half of sale.
    * Every catalog product is sellable; the floor is 1 MP so no product is
    * ever "buyback excluded" by pricing.
    */
   private int buyBaseOf(ShopProduct product) {
      int table = this.plugin.pricingService.buyPrice(product.material());
      if (table > 0) return table;
      return Math.max(1, product.price() / 2);
   }

   private ItemStack catalogIcon(Player player, ShopProduct product) {
      String id = product.id();
      ItemStack icon = this.displayStack(product);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
         int price = this.effectivePrice(product);
         int buy = this.effectiveBuyPrice(product);
         int stock = this.plugin.shopStockService.stockOf(id);
         long remaining = this.remainingSeconds(player, id);
         List<String> lore = new ArrayList<>(List.of(
            "\u00a77" + this.categoryOf(product.material()).label,
            "\u00a7e\u4fa1\u683c: " + price + " MP" + (stock <= 0 ? " \u00a7c(SOLD OUT)" : ""),
            "\u00a7b\u8cb7\u53d6: " + buy + " MP",
            "\u00a77\u5728\u5eab: " + stock
         ));
         lore.add(remaining > 0 ? "\u00a7c\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u6b8b\u308a " + remaining + " \u79d2" : "\u00a7a\u5de6\u30af\u30ea\u30c3\u30af\u3067\u8cfc\u5165");
         lore.add("\u00a76\u53f3\u30af\u30ea\u30c3\u30af\u30671\u500b\u58f2\u5374 / Shift+\u53f3\u4e00\u62ec\u58f2\u5374");
         meta.setLore(lore);
         meta.setDisplayName("\u00a7f" + product.label());
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
      }
      return icon;
   }

   /** GUI icon that previews the exact variant (enchanted book / potion type). */
   private ItemStack displayStack(ShopProduct product) {
      if (product.variant().isEmpty()) return new ItemStack(product.material());
      ItemStack stack = this.plugin.createOnlineShopProduct(product.id());
      return stack == null ? new ItemStack(product.material()) : stack;
   }

   private ItemStack actionIcon(Material material, String name, String action) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (action != null) meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, action);
      item.setItemMeta(meta);
      return item;
   }

   private long remainingSeconds(Player player, String id) {
      long now = System.currentTimeMillis();
      String path = "online-shop-cooldowns." + player.getUniqueId() + "." + id;
      long until = Math.max(
         this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).getOrDefault(id, 0L),
         this.plugin.data().getLong(path, 0L)
      );
      return OnlineShopRules.remainingSeconds(now, until);
   }

   private void purchase(Player player, String id) {
      if (!this.plugin.getConfig().getBoolean("online-shop.enabled", true) || !id.startsWith("item:")) return;
      if (!this.inSurvivalWorld(player)) {
         player.sendMessage(ChatColor.RED + "OnlineShop\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u3048\u307e\u3059\u3002");
         return;
      }
      long remaining = this.remainingSeconds(player, id);
      if (remaining > 0L) {
         player.sendMessage(ChatColor.RED + "\u8cfc\u5165\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u4e2d\u3067\u3059\u3002\u6b8b\u308a " + remaining + " \u79d2");
         return;
      }
      ShopProduct listed = this.productIndex.get(id);
      if (listed == null) return;
      Material material = listed.material();
      // Displayed price and charged price share the stock-adjusted basis.
      int price = this.effectivePrice(listed);
      if (this.plugin.shopStockService.stockOf(id) <= 0) {
         player.sendMessage(ChatColor.RED + "売り切れです。入荷をお待ちください。");
         return;
      }
      ItemStack product = this.plugin.createOnlineShopProduct(id);
      if (product == null) return;
      if (!this.plugin.canReceiveOnlineShopProduct(player, product)) {
         player.sendMessage(ChatColor.RED + "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (price > 0 && !this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MP \u304c\u8db3\u308a\u307e\u305b\u3093\u3002\u5fc5\u8981 MP: " + price);
         return;
      }
      boolean delivered;
      try {
         delivered = this.plugin.grantOnlineShopProduct(player, product);
      } catch (RuntimeException error) {
         // A thrown delivery would otherwise leave the MP withdrawn with no
         // item; treat it as a failure so the buyer is always refunded.
         this.plugin.getLogger().warning("Online shop delivery failed for " + player.getName() + ": " + error.getMessage());
         delivered = false;
      }
      if (!delivered) {
         // Delivery failed after payment; refund atomically rather than
         // silently dropping the item and keeping the MP.
         if (price > 0) this.plugin.refundEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "\u30a2\u30a4\u30c6\u30e0\u3092\u6e21\u305b\u307e\u305b\u3093\u3067\u3057\u305f\u3002MP\u3092\u8fd4\u5374\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      // Cooldown tiers follow the stable base price, not the stock swing.
      long cooldownSeconds = this.cooldownSecondsFor(material, listed.price());
      long until = OnlineShopRules.cooldownDeadline(System.currentTimeMillis(), cooldownSeconds);
      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, until);
      this.plugin.data().set("online-shop-cooldowns." + player.getUniqueId() + "." + id, until);
      this.plugin.shopStockService.onPurchase(id);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "\u8cfc\u5165\u3057\u307e\u3057\u305f: " + listed.label() + " (" + price + " MP)");
      player.openInventory(this.createInventory(player));
   }

   /**
    * Buy-back: right click sells one unit, shift+right-click sells everything
    * matching. No cooldown on sales. Each unit is priced live (stock moves per
    * unit), so bulk sales never freeze the first unit's price.
    */
   private void sell(Player player, String id, boolean bulk) {
      if (!this.plugin.getConfig().getBoolean("online-shop.enabled", true) || !id.startsWith("item:")) return;
      if (!this.inSurvivalWorld(player)) {
         player.sendMessage(ChatColor.RED + "OnlineShop\u306fSurvival\u30ef\u30fc\u30eb\u30c9\u3067\u306e\u307f\u4f7f\u3048\u307e\u3059\u3002");
         return;
      }
      ShopProduct listed = this.productIndex.get(id);
      if (listed == null) return;
      int capacity = bulk ? Integer.MAX_VALUE : 1;
      int sold = 0;
      long gained = 0L;
      // Single-threaded server tick: inventory mutation + stock + payout stay
      // consistent by construction; amounts are validated non-negative.
      for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (!this.matchesProduct(player, stack, listed)) continue;
         while (stack.getAmount() > 0 && sold < capacity) {
            int unitPrice = Math.max(1, this.effectiveBuyPrice(listed));
            stack.setAmount(stack.getAmount() - 1);
            sold++;
            gained = Math.min(2_000_000_000L, gained + unitPrice);
            this.plugin.shopStockService.onSale(id, 1);
         }
         if (stack.getAmount() <= 0) player.getInventory().setItem(slot, null);
         if (sold >= capacity) break;
      }
      if (sold <= 0) {
         player.sendMessage(ChatColor.RED + "売却できるアイテムを持っていません。");
         return;
      }
      player.updateInventory();
      this.plugin.depositEmeralds(player.getUniqueId(), (int) Math.min(2_000_000_000L, gained));
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "売却しました: " + listed.label() + " x" + sold + " (" + gained + " MP)");
      player.openInventory(this.createInventory(player));
   }

   /** True when the stack is a sellable unit of the product. */
   private boolean matchesProduct(Player player, ItemStack stack, ShopProduct product) {
      if (stack == null || stack.getType() != product.material() || stack.getAmount() <= 0) return false;
      // Mifron fixed/utility items must never be sellable for MP.
      try {
         if (this.plugin.utilityItemsFeature.getMifronItemId(stack) != null) return false;
      } catch (Throwable ignored) {
      }
      if (product.variant().isEmpty()) return true;
      // Variant goods match only the exact variant (enchant+level / potion).
      ItemStack canonical = this.plugin.createOnlineShopProduct(product.id());
      if (canonical == null) return false;
      if (product.material() == Material.ENCHANTED_BOOK) {
         if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta have)
            || !(canonical.getItemMeta() instanceof EnchantmentStorageMeta want)) return false;
         return have.getStoredEnchants().equals(want.getStoredEnchants());
      }
      if (stack.getItemMeta() instanceof PotionMeta have && canonical.getItemMeta() instanceof PotionMeta want) {
         return have.getBasePotionType() == want.getBasePotionType();
      }
      return false;
   }

   private boolean inSurvivalWorld(Player player) {
      if (player.getWorld() == null) return false;
      String world = player.getWorld().getName();
      String survival = this.plugin.getConfig().getString("survival-dimensions.overworld", "survival");
      return world.equalsIgnoreCase(survival) || "survival".equalsIgnoreCase(world);
   }

   private boolean isAccessItem(ItemStack item) {
      return item != null && item.hasItemMeta()
         && "online_shop".equals(item.getItemMeta().getPersistentDataContainer().get(this.accessKey, PersistentDataType.STRING));
   }

   private boolean isPurchasable(Material material) {
      if (material == null || !material.isItem() || material.isAir()) return false;
      // Rare-merchant exclusives never enter the normal shop table.
      if (RareMerchantItems.isSpecial(material)) return false;
      String name = material.name();
      if (name.startsWith("LEGACY_") || name.startsWith("INFESTED_")) return false;
      return !name.contains("COMMAND") && material != Material.BARRIER && material != Material.STRUCTURE_VOID
         && material != Material.STRUCTURE_BLOCK && material != Material.JIGSAW && material != Material.LIGHT
         && material != Material.DEBUG_STICK && material != Material.KNOWLEDGE_BOOK && material != Material.SPAWNER
         && material != Material.TRIAL_SPAWNER
         && material != Material.BEDROCK && material != Material.VAULT && material != Material.DRAGON_EGG
         && material != Material.TEST_BLOCK && material != Material.TEST_INSTANCE_BLOCK
         && material != Material.REINFORCED_DEEPSLATE && material != Material.PETRIFIED_OAK_SLAB;
   }

   private Category categoryOf(Material material) {
      String name = material.name();
      // Equipment first: gear names overlap ore keywords (diamond/emerald).
      if (contains(name, "SWORD", "BOW", "ARROW", "TRIDENT", "MACE", "BREEZE_ROD", "WIND_CHARGE", "SHIELD", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "CROSSBOW", "TOTEM", "SPECTRAL", "TURTLE")) return Category.EQUIP;
      if (contains(name, "PICKAXE", "AXE", "SHOVEL", "HOE", "SHEARS", "FLINT_AND_STEEL", "FISHING_ROD", "BRUSH", "SPYGLASS", "COMPASS", "CLOCK", "LEAD", "NAME_TAG", "MACE", "HEAVY_CORE")) return Category.EQUIP;
      if (material == Material.ENCHANTED_BOOK) return Category.SPECIAL;
      if (material == Material.POTION || material == Material.SPLASH_POTION
         || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW
         || material == Material.OMINOUS_BOTTLE) return Category.FOOD;
      if (material.isEdible() || contains(name, "STEW", "SOUP", "BREAD", "CAKE", "COOKIE", "PUMPKIN_PIE", "HONEY_BOTTLE", "COOKED", "KELP", "MILK", "POTION")) return Category.FOOD;
      if (contains(name, "ORE", "INGOT", "RAW_", "NUGGET", "ANCIENT_DEBRIS", "DIAMOND", "EMERALD", "COAL", "LAPIS", "QUARTZ", "NETHERITE", "AMETHYST", "COPPER", "IRON", "GOLD", "REDSTONE")) return Category.MATERIAL;
      if (contains(name, "REDSTONE", "PISTON", "REPEATER", "COMPARATOR", "HOPPER", "DISPENSER", "DROPPER", "OBSERVER", "RAIL", "MINECART", "LEVER", "BUTTON", "PRESSURE", "TRIPWIRE", "SCULK_SENSOR", "CALIBRATED", "CRAFTER", "DAYLIGHT", "NOTE_BLOCK", "TARGET")) return Category.MATERIAL;
      if (contains(name, "SAPLING", "SEED", "WHEAT", "CARROT", "POTATO", "BEET", "COCOA", "SUGAR", "BAMBOO", "CACTUS", "CHORUS", "FUNGUS", "MUSHROOM", "TULIP", "ORCHID", "LILAC", "PEONY", "ROSE", "LEAVES", "VINE", "MOSS", "AZALEA", "MANGROVE", "PINK_PETALS", "KELP", "LOG", "PLANKS", "WOOL")) return Category.MATERIAL;
      if (contains(name, "BANNER", "SIGN", "PAINTING", "ITEM_FRAME", "CANDLE", "LANTERN", "CAMPFIRE", "CARPET", "GLASS", "DECORATED", "ARMOR_STAND", "FLOWER_POT", "SHULKER_BOX", "BED", "DOOR", "FENCE", "STAIRS", "SLAB", "WALL", "BRICK", "TILE", "TERRACOTTA", "CONCRETE", "GLAZED")) return Category.BUILD;
      if (material.isBlock()) return Category.BUILD;
      return Category.SPECIAL;
   }

   private static boolean contains(String name, String... parts) {
      for (String part : parts) if (name.contains(part)) return true;
      return false;
   }

   private int priceOf(Material material) {
      return OnlineShopRules.price(this.plugin, material);
   }

   private String rarityOf(int price) {
      if (price >= 2000) return "LEGENDARY";
      if (price >= 500) return "EPIC";
      if (price >= 150) return "RARE";
      if (price >= 40) return "UNCOMMON";
      return "COMMON";
   }

   /**
    * Explicit per-item {@code cooldown-seconds} config is authoritative. Rarity
    * (configured, else derived from price) is only a fallback so a missing
    * explicit value still yields a sane cooldown. Quick consumables (food,
    * arrows, thrown projectiles, ...) are capped low so bought items stay
    * usable right away.
    */
   private long cooldownSecondsFor(Material material, int price) {
      Long explicit = this.configuredCooldowns.get(material.name());
      long seconds;
      if (explicit != null) seconds = explicit;
      else {
         String rarity = this.configuredRarities.get(material.name());
         if (rarity == null || rarity.isBlank()) rarity = this.rarityOf(price);
         seconds = OnlineShopRules.cooldownSeconds(rarity);
      }
      if (OnlineShopRules.isQuickConsumable(material.name(), material.isEdible())) {
         long cap = Math.max(0L, this.plugin.getConfig().getLong(
            "online-shop.consumable-cooldown-cap-seconds",
            OnlineShopRules.QUICK_CONSUMABLE_CAP_DEFAULT_SECONDS));
         seconds = OnlineShopRules.applyConsumableCap(seconds, cap);
      }
      return seconds;
   }
}
