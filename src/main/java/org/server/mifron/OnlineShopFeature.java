package org.server.mifron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class OnlineShopFeature implements Listener {
   static final String TITLE = "\u00a7bMifron OnlineShop";
   private static final int PAGE_SIZE = 36;
   private static final int PAGE_SIZE_PER_ROW = 8;
   private final Mifron plugin;
   private final NamespacedKey accessKey;
   private final NamespacedKey productKey;
   private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
   private final Map<String, Long> configuredCooldowns = new HashMap<>();
   private final Map<String, String> configuredRarities = new HashMap<>();
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
      this.rebuildConfiguredCooldowns();
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
      if (action.startsWith("cat:")) return; // Row header; not clickable.
      this.purchase(player, action);
   }

   private Inventory createInventory(Player player) {
      Inventory inventory = Bukkit.createInventory(player, 54, TITLE);
      // Single-screen layout: one labeled row per category, no tab navigation.
      // Each category shows its first PAGE_SIZE_PER_ROW items; the full catalog
      // remains purchasable via the item ids even if a row overflows.
      int slot = 0;
      List<Material> all = new ArrayList<>();
      for (Category category : Category.values()) {
         List<Material> items = this.catalog.getOrDefault(category, List.of());
         if (items.isEmpty()) continue;
         if (slot + 9 > 54) break;
         inventory.setItem(slot, this.actionIcon(category.icon, "\u00a76" + category.label, "cat:" + category.name()));
         int shown = Math.min(items.size(), PAGE_SIZE_PER_ROW);
         for (int i = 0; i < shown && slot + 1 + i < 54; i++) {
            inventory.setItem(slot + 1 + i, this.catalogIcon(player, items.get(i)));
            all.add(items.get(i));
         }
         slot += 9;
      }
      this.applyCooldownOverlay(player, all);
      return inventory;
   }

   private void applyCooldownOverlay(Player player, List<Material> materials) {
      // The vanilla item cooldown is the single source of truth and must
      // survive closing/reopening the GUI. Earlier code zeroed it from
      // InventoryCloseEvent, which ran right after createInventory set it and
      // made the cooldown animation disappear.
      for (Material material : materials) {
         long remaining = this.remainingSeconds(player, "item:" + material.name());
         player.setCooldown(material, remaining <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining * 20L));
      }
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
      if (!this.plugin.grantOnlineShopProduct(player, product)) {
         // Delivery failed after payment; refund atomically rather than
         // silently dropping the item and keeping the MP.
         if (price > 0) this.plugin.refundEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "\u30a2\u30a4\u30c6\u30e0\u3092\u6e21\u305b\u307e\u305b\u3093\u3067\u3057\u305f\u3002MP\u3092\u8fd4\u5374\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      long cooldownSeconds = this.cooldownSecondsFor(material);
      long until = System.currentTimeMillis() + cooldownSeconds * 1000L;
      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, until);
      this.plugin.data().set("online-shop-cooldowns." + player.getUniqueId() + "." + id, until);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "\u8cfc\u5165\u3057\u307e\u3057\u305f: " + material.name() + " (" + price + " MP)");
      player.openInventory(this.createInventory(player));
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
    * explicit value still yields a sane cooldown.
    */
   private long cooldownSecondsFor(Material material) {
      Long explicit = this.configuredCooldowns.get(material.name());
      if (explicit != null) return explicit;
      String rarity = this.configuredRarities.get(material.name());
      if (rarity == null || rarity.isBlank()) rarity = this.rarityOf(this.priceOf(material));
      return OnlineShopRules.cooldownSeconds(rarity);
   }
}
