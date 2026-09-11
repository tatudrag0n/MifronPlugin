package org.server.mifron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class OnlineShopFeature implements Listener {
   static final String TITLE = "\u00a7bMifron OnlineShop";
   private static final int PAGE_SIZE = 36;
   private final Mifron plugin;
   private final NamespacedKey accessKey;
   private final NamespacedKey productKey;
   private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> pages = new HashMap<>();
   private final Map<UUID, Category> selected = new HashMap<>();
   private final EnumMap<Category, List<Material>> catalog = new EnumMap<>(Category.class);

   enum Category {
      BLOCKS(Material.BRICKS, "\u5efa\u6750"),
      ORES(Material.DIAMOND, "\u9271\u77f3"),
      COMBAT(Material.IRON_SWORD, "\u6226\u95d8"),
      TOOLS(Material.IRON_PICKAXE, "\u9053\u5177"),
      FOOD(Material.BREAD, "\u98df\u6599"),
      REDSTONE(Material.REDSTONE, "\u56de\u8def"),
      NATURE(Material.OAK_SAPLING, "\u81ea\u7136"),
      DECOR(Material.PAINTING, "\u88c5\u98fe"),
      MISC(Material.BUNDLE, "\u305d\u306e\u4ed6");
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
      for (Material material : Material.values()) {
         if (this.isPurchasable(material)) this.catalog.get(this.categoryOf(material)).add(material);
      }
      for (List<Material> list : this.catalog.values()) list.sort(Comparator.comparing(Material::name));
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Bukkit.getScheduler().runTask(this.plugin, () -> this.ensureAccessItem(event.getPlayer()));
   }

   private void ensureAccessItem(Player player) {
      PlayerInventory inventory = player.getInventory();
      boolean has = false;
      for (ItemStack item : inventory.getContents()) {
         if (!this.isAccessItem(item)) continue;
         has = true;
         if (item.getType() != Material.IRON_DOOR) item.setType(Material.IRON_DOOR);
      }
      ItemStack offhand = inventory.getItemInOffHand();
      if (this.isAccessItem(offhand)) {
         has = true;
         if (offhand.getType() != Material.IRON_DOOR) offhand.setType(Material.IRON_DOOR);
      }
      if (!has) inventory.addItem(this.createAccessItem());
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
      if (action.startsWith("cat:")) {
         try {
            this.selected.put(player.getUniqueId(), Category.valueOf(action.substring(4)));
            this.pages.put(player.getUniqueId(), 0);
         } catch (IllegalArgumentException ignored) { return; }
         player.openInventory(this.createInventory(player));
         return;
      }
      if ("page:prev".equals(action)) {
         this.pages.merge(player.getUniqueId(), -1, Integer::sum);
         player.openInventory(this.createInventory(player));
         return;
      }
      if ("page:next".equals(action)) {
         this.pages.merge(player.getUniqueId(), 1, Integer::sum);
         player.openInventory(this.createInventory(player));
         return;
      }
      this.purchase(player, action);
   }

   private Inventory createInventory(Player player) {
      Inventory inventory = Bukkit.createInventory(player, 54, TITLE);
      Category current = this.selected.getOrDefault(player.getUniqueId(), Category.BLOCKS);
      List<Material> items = this.catalog.getOrDefault(current, List.of());
      int totalPages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
      int page = Math.max(0, Math.min(totalPages - 1, this.pages.getOrDefault(player.getUniqueId(), 0)));
      this.pages.put(player.getUniqueId(), page);
      int start = page * PAGE_SIZE;
      int slot = 9;
      for (int i = start; i < Math.min(start + PAGE_SIZE, items.size()); i++) {
         inventory.setItem(slot++, this.catalogIcon(player, items.get(i)));
      }
      Category[] values = Category.values();
      for (int i = 0; i < values.length && i < 9; i++) {
         Category category = values[i];
         String name = (category == current ? "\u00a7a\u25b6 " : "\u00a77") + category.label;
         inventory.setItem(i, this.actionIcon(category.icon, name, "cat:" + category.name()));
      }
      inventory.setItem(45, this.actionIcon(Material.ARROW, "\u00a7e\u524d\u306e\u30da\u30fc\u30b8", "page:prev"));
      inventory.setItem(49, this.actionIcon(Material.PAPER, "\u00a7f" + current.label + "  " + (page + 1) + "/" + totalPages, "cat:" + current.name()));
      inventory.setItem(53, this.actionIcon(Material.ARROW, "\u00a7e\u6b21\u306e\u30da\u30fc\u30b8", "page:next"));
      return inventory;
   }

   private ItemStack catalogIcon(Player player, Material material) {
      String id = "item:" + material.name();
      ItemStack icon = new ItemStack(material);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
         int price = this.priceOf(material);
         long remaining = this.remainingSeconds(player, id);
         meta.setDisplayName("\u00a7f" + material.name());
         meta.setLore(List.of(
            "\u00a77" + this.categoryOf(material).label,
            "\u00a7e\u4fa1\u683c: " + price + " MP",
            remaining > 0 ? "\u00a7c\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u6b8b\u308a " + remaining + " \u79d2" : "\u00a7a\u30af\u30ea\u30c3\u30af\u3067\u8cfc\u5165",
            "\u00a78\u8ca9\u58f2\u306e\u307f"
         ));
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
      }
      return icon;
   }

   private ItemStack actionIcon(Material material, String name, String action) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, action);
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
      return Math.max(0L, (until - now + 999L) / 1000L);
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
      Material material = Material.matchMaterial(id.substring(5));
      if (material == null || !this.isPurchasable(material)) return;
      int price = this.priceOf(material);
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
      this.plugin.grantOnlineShopProduct(player, product);
      long until = System.currentTimeMillis() + this.cooldownOf(this.rarityOf(price)) * 1000L;
      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, until);
      this.plugin.data().set("online-shop-cooldowns." + player.getUniqueId() + "." + id, until);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "\u8cfc\u5165\u3057\u307e\u3057\u305f: " + material.name() + " (" + price + " MP)");
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
      String name = material.name();
      return !name.contains("COMMAND") && material != Material.BARRIER && material != Material.STRUCTURE_VOID
         && material != Material.STRUCTURE_BLOCK && material != Material.JIGSAW && material != Material.LIGHT
         && material != Material.DEBUG_STICK && material != Material.KNOWLEDGE_BOOK && material != Material.SPAWNER
         && material != Material.BEDROCK;
   }

   private Category categoryOf(Material material) {
      String name = material.name();
      if (contains(name, "ORE", "INGOT", "RAW_", "NUGGET", "ANCIENT_DEBRIS")
         || (contains(name, "DIAMOND", "EMERALD", "COAL", "LAPIS", "QUARTZ", "NETHERITE", "AMETHYST", "COPPER")
            && !contains(name, "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "HORSE", "DOOR", "BARS", "BLOCK"))) {
         if (!contains(name, "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) return Category.ORES;
      }
      if (contains(name, "SWORD", "BOW", "ARROW", "TRIDENT", "MACE", "SHIELD", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "CROSSBOW", "TOTEM", "SPECTRAL")) return Category.COMBAT;
      if (contains(name, "PICKAXE", "AXE", "SHOVEL", "HOE", "SHEARS", "FLINT_AND_STEEL", "FISHING_ROD", "BRUSH", "SPYGLASS", "COMPASS", "CLOCK", "LEAD", "NAME_TAG")) return Category.TOOLS;
      if (material.isEdible() || contains(name, "STEW", "SOUP", "BREAD", "CAKE", "COOKIE", "PUMPKIN_PIE", "HONEY_BOTTLE", "COOKED", "KELP")) return Category.FOOD;
      if (contains(name, "REDSTONE", "PISTON", "REPEATER", "COMPARATOR", "HOPPER", "DISPENSER", "DROPPER", "OBSERVER", "RAIL", "MINECART", "LEVER", "BUTTON", "PRESSURE", "TRIPWIRE", "SCULK_SENSOR", "CALIBRATED", "CRAFTER", "DAYLIGHT", "NOTE_BLOCK", "TARGET")) return Category.REDSTONE;
      if (contains(name, "SAPLING", "SEED", "WHEAT", "CARROT", "POTATO", "BEET", "COCOA", "SUGAR_CANE", "BAMBOO", "CACTUS", "CHORUS", "FUNGUS", "MUSHROOM", "TULIP", "ORCHID", "LILAC", "PEONY", "ROSE", "LEAVES", "VINE", "MOSS", "AZALEA", "MANGROVE", "PINK_PETALS")) return Category.NATURE;
      if (contains(name, "BANNER", "SIGN", "PAINTING", "ITEM_FRAME", "CANDLE", "LANTERN", "CAMPFIRE", "CARPET", "GLASS_PANE", "HEAD", "SKULL", "DECORATED", "ARMOR_STAND", "FLOWER_POT")) return Category.DECOR;
      if (material.isBlock()) return Category.BLOCKS;
      return Category.MISC;
   }

   private static boolean contains(String name, String... parts) {
      for (String part : parts) if (name.contains(part)) return true;
      return false;
   }

   private int priceOf(Material material) {
      int configured = this.plugin.shopSalePrices.getOrDefault(material.name(), 0);
      return configured > 0 ? configured : Math.max(1, this.plugin.getConfig().getInt("online-shop.fallback-price", 10));
   }

   private String rarityOf(int price) {
      if (price >= 2000) return "LEGENDARY";
      if (price >= 500) return "EPIC";
      if (price >= 150) return "RARE";
      if (price >= 40) return "UNCOMMON";
      return "COMMON";
   }

   private long cooldownOf(String rarity) {
      return switch (rarity == null ? "COMMON" : rarity.toUpperCase(Locale.ROOT)) {
         case "LEGENDARY" -> 600L;
         case "EPIC" -> 180L;
         case "RARE" -> 60L;
         case "UNCOMMON" -> 15L;
         default -> 0L;
      };
   }
}
