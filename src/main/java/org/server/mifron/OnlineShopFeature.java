package org.server.mifron;

import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class OnlineShopFeature implements Listener {
   static final String TITLE_GENERAL = "\u00a7bMifron OnlineShop";
   static final String TITLE_SPECIAL = "\u00a7dMifron OnlineShop / \u7279\u6b8a";
   private static final int PAGE_SIZE = 45;
   private final Mifron plugin;
   private final NamespacedKey accessKey;
   private final NamespacedKey productKey;
   private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> generalPages = new HashMap<>();
   private List<Material> catalog = List.of();

   OnlineShopFeature(Mifron plugin) {
      this.plugin = plugin;
      this.accessKey = new NamespacedKey(plugin, "item");
      this.productKey = new NamespacedKey(plugin, "online_shop_product");
      this.rebuildCatalog();
   }

   void rebuildCatalog() {
      List<Material> items = new ArrayList<>();
      for (Material material : Material.values()) {
         if (this.isPurchasable(material)) items.add(material);
      }
      items.sort(Comparator.comparing(Material::name));
      this.catalog = List.copyOf(items);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      boolean has = false;
      for (ItemStack item : player.getInventory().getContents()) {
         if (item == null || !item.hasItemMeta()) continue;
         if ("online_shop".equals(item.getItemMeta().getPersistentDataContainer().get(this.accessKey, PersistentDataType.STRING))) {
            has = true;
            if (item.getType() != Material.IRON_DOOR) item.setType(Material.IRON_DOOR);
         }
      }
      if (!has) player.getInventory().addItem(this.createAccessItem());
   }

   ItemStack createAccessItem() {
      ItemStack item = new ItemStack(Material.IRON_DOOR);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(ChatColor.AQUA + "OnlineShop");
      meta.setLore(List.of(ChatColor.GRAY + "\u53f3\u30af\u30ea\u30c3\u30af: \u5546\u54c1\u4e00\u89a7\u3092\u958b\u304f"));
      meta.getPersistentDataContainer().set(this.accessKey, PersistentDataType.STRING, "online_shop");
      item.setItemMeta(meta);
      return item;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onUse(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (item == null || !item.hasItemMeta()) return;
      if (!"online_shop".equals(item.getItemMeta().getPersistentDataContainer().get(this.accessKey, PersistentDataType.STRING))) return;
      event.setCancelled(true);
      if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
      event.getPlayer().openInventory(this.createInventory(event.getPlayer(), "general"));
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      String title = event.getView().getTitle();
      if (!TITLE_GENERAL.equals(title) && !TITLE_SPECIAL.equals(title)
         && !title.equals(this.plugin.getConfig().getString("online-shop.title", TITLE_GENERAL))) return;
      event.setCancelled(true);
      if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) return;
      String action = clicked.getItemMeta().getPersistentDataContainer().get(this.productKey, PersistentDataType.STRING);
      if (action == null) return;
      if ("tab:general".equals(action)) { player.openInventory(this.createInventory(player, "general")); return; }
      if ("tab:special".equals(action)) { player.openInventory(this.createInventory(player, "special")); return; }
      if ("page:prev".equals(action)) { this.generalPages.merge(player.getUniqueId(), -1, Integer::sum); player.openInventory(this.createInventory(player, "general")); return; }
      if ("page:next".equals(action)) { this.generalPages.merge(player.getUniqueId(), 1, Integer::sum); player.openInventory(this.createInventory(player, "general")); return; }
      this.purchase(player, action);
   }

   private Inventory createInventory(Player player, String category) {
      boolean special = "special".equalsIgnoreCase(category);
      Inventory inventory = Bukkit.createInventory(player, 54, special ? TITLE_SPECIAL : TITLE_GENERAL);
      inventory.setItem(45, this.tab(Material.CHEST, "\u00a7a\u4e00\u822c\u30bf\u30d6", "tab:general"));
      inventory.setItem(46, this.tab(Material.NETHER_STAR, "\u00a7d\u7279\u6b8a\u30a2\u30a4\u30c6\u30e0\u30bf\u30d6", "tab:special"));
      if (special) { this.fillConfigured(player, inventory); return inventory; }
      if (this.catalog.isEmpty()) this.rebuildCatalog();
      int pages = Math.max(1, (this.catalog.size() + PAGE_SIZE - 1) / PAGE_SIZE);
      int page = Math.max(0, Math.min(pages - 1, this.generalPages.getOrDefault(player.getUniqueId(), 0)));
      this.generalPages.put(player.getUniqueId(), page);
      int start = page * PAGE_SIZE;
      int slot = 0;
      for (int i = start; i < Math.min(start + PAGE_SIZE, this.catalog.size()); i++) {
         inventory.setItem(slot++, this.catalogIcon(player, this.catalog.get(i)));
      }
      inventory.setItem(48, this.tab(Material.ARROW, "\u00a7e\u524d\u306e\u30da\u30fc\u30b8", "page:prev"));
      inventory.setItem(49, this.tab(Material.PAPER, "\u00a7f" + (page + 1) + " / " + pages, "tab:general"));
      inventory.setItem(50, this.tab(Material.ARROW, "\u00a7e\u6b21\u306e\u30da\u30fc\u30b8", "page:next"));
      return inventory;
   }

   private void fillConfigured(Player player, Inventory inventory) {
      var section = this.plugin.getConfig().getConfigurationSection("online-shop.items");
      if (section == null) return;
      int slot = 0;
      for (String id : section.getKeys(false)) {
         if (slot >= PAGE_SIZE) break;
         Material material = Material.matchMaterial(section.getString(id + ".material", "PAPER"));
         if (material == null) material = Material.PAPER;
         ItemStack icon = new ItemStack(material);
         ItemMeta meta = icon.getItemMeta();
         int price = Math.max(0, section.getInt(id + ".price", 0));
         long remaining = this.remainingSeconds(player, id);
         meta.setDisplayName(color(section.getString(id + ".display-name", id)));
         meta.setLore(List.of(
            "\u00a77\u30ab\u30c6\u30b4\u30ea: " + section.getString(id + ".category", "special"),
            "\u00a7e\u4fa1\u683c: " + price + " MP",
            "\u00a7b\u30ec\u30a2\u5ea6: " + section.getString(id + ".rarity", "COMMON"),
            remaining > 0 ? "\u00a7c\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u6b8b\u308a " + remaining + " \u79d2" : "\u00a7a\u8cfc\u5165\u53ef\u80fd",
            "\u00a78\u8ca9\u58f2\u306e\u307f / \u8cb7\u53d6\u306a\u3057"
         ));
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
         inventory.setItem(slot++, icon);
      }
   }

   private ItemStack catalogIcon(Player player, Material material) {
      String id = "item:" + material.name();
      ItemStack icon = new ItemStack(material);
      ItemMeta meta = icon.getItemMeta();
      if (meta != null) {
         int price = this.priceOf(material);
         String rarity = this.rarityOf(price);
         long remaining = this.remainingSeconds(player, id);
         meta.setDisplayName("\u00a7f" + material.name());
         meta.setLore(List.of(
            "\u00a77\u30ab\u30c6\u30b4\u30ea: general",
            "\u00a7e\u4fa1\u683c: " + price + " MP",
            "\u00a7b\u30ec\u30a2\u5ea6: " + rarity,
            remaining > 0 ? "\u00a7c\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u6b8b\u308a " + remaining + " \u79d2" : "\u00a7a\u8cfc\u5165\u53ef\u80fd",
            "\u00a78\u8ca9\u58f2\u306e\u307f / \u8cb7\u53d6\u306a\u3057"
         ));
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
      }
      return icon;
   }

   private ItemStack tab(Material material, String name, String action) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, action);
      item.setItemMeta(meta);
      return item;
   }

   private long remainingSeconds(Player player, String id) {
      long now = System.currentTimeMillis();
      String cooldownPath = "online-shop-cooldowns." + player.getUniqueId() + "." + id;
      long until = Math.max(
         this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).getOrDefault(id, 0L),
         this.plugin.data().getLong(cooldownPath, 0L)
      );
      return Math.max(0L, (until - now + 999L) / 1000L);
   }

   private void purchase(Player player, String id) {
      if (!this.plugin.getConfig().getBoolean("online-shop.enabled", true)) return;
      long remaining = this.remainingSeconds(player, id);
      if (remaining > 0L) { player.sendMessage(ChatColor.RED + "\u8cfc\u5165\u30af\u30fc\u30eb\u30c0\u30a6\u30f3\u4e2d\u3067\u3059\u3002\u6b8b\u308a " + remaining + " \u79d2"); return; }
      int price;
      long cooldownSeconds;
      if (id.startsWith("item:")) {
         Material material = Material.matchMaterial(id.substring(5));
         if (material == null || !this.isPurchasable(material)) return;
         price = this.priceOf(material);
         cooldownSeconds = this.cooldownOf(this.rarityOf(price));
      } else {
         String path = "online-shop.items." + id;
         if (!this.plugin.getConfig().contains(path)) return;
         price = Math.max(0, this.plugin.getConfig().getInt(path + ".price", 0));
         cooldownSeconds = Math.max(0L, this.plugin.getConfig().getLong(path + ".cooldown-seconds", this.cooldownOf(this.plugin.getConfig().getString(path + ".rarity", "COMMON"))));
      }
      ItemStack product = this.plugin.createOnlineShopProduct(id);
      if (product == null) return;
      if (!this.plugin.canReceiveOnlineShopProduct(player, product)) { player.sendMessage(ChatColor.RED + "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); return; }
      if (price > 0 && !this.plugin.withdrawEmeralds(player.getUniqueId(), price)) { player.sendMessage(ChatColor.RED + "MP \u304c\u8db3\u308a\u307e\u305b\u3093\u3002\u5fc5\u8981 MP: " + price); return; }
      this.plugin.grantOnlineShopProduct(player, product);
      long until = System.currentTimeMillis() + cooldownSeconds * 1000L;
      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, until);
      this.plugin.data().set("online-shop-cooldowns." + player.getUniqueId() + "." + id, until);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "OnlineShop \u3067\u8cfc\u5165\u3057\u307e\u3057\u305f\uff1a" + product.getType().name());
   }

   private boolean isPurchasable(Material material) {
      if (material == null || !material.isItem() || material.isAir()) return false;
      String name = material.name();
      return !name.contains("COMMAND")
         && material != Material.BARRIER
         && material != Material.STRUCTURE_VOID
         && material != Material.STRUCTURE_BLOCK
         && material != Material.JIGSAW
         && material != Material.LIGHT
         && material != Material.DEBUG_STICK
         && material != Material.KNOWLEDGE_BOOK
         && material != Material.SPAWNER
         && material != Material.BEDROCK;
   }

   private int priceOf(Material material) {
      int configured = this.plugin.shopSalePrices.getOrDefault(material.name(), 0);
      if (configured > 0) return configured;
      return Math.max(1, this.plugin.getConfig().getInt("online-shop.fallback-price", 10));
   }

   private String rarityOf(int price) {
      if (price >= 2000) return "LEGENDARY";
      if (price >= 500) return "EPIC";
      if (price >= 150) return "RARE";
      if (price >= 40) return "UNCOMMON";
      return "COMMON";
   }

   private long cooldownOf(String rarity) {
      String key = rarity == null ? "COMMON" : rarity.toUpperCase(Locale.ROOT);
      return switch (key) {
         case "LEGENDARY" -> 600L;
         case "EPIC" -> 180L;
         case "RARE" -> 60L;
         case "UNCOMMON" -> 15L;
         default -> 0L;
      };
   }

   private static String color(String value) {
      return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
   }
}
