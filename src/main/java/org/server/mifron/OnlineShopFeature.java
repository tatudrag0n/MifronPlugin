package org.server.mifron;

import java.util.ArrayList;
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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class OnlineShopFeature implements Listener {
   static final String TITLE_GENERAL = "§bMifron OnlineShop";
   static final String TITLE_SPECIAL = "§dMifron OnlineShop / 特殊";
   private final Mifron plugin;
   private final NamespacedKey accessKey;
   private final NamespacedKey productKey;
   private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

   OnlineShopFeature(Mifron plugin) {
      this.plugin = plugin;
      this.accessKey = new NamespacedKey(plugin, "item");
      this.productKey = new NamespacedKey(plugin, "online_shop_product");
   }

   @EventHandler(ignoreCancelled = true)
   public void onUse(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (item != null && item.hasItemMeta()
         && "online_shop".equals(item.getItemMeta().getPersistentDataContainer().get(this.accessKey, PersistentDataType.STRING))) {
         event.setCancelled(true);
         event.getPlayer().openInventory(this.createInventory(event.getPlayer(), "general"));
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      String title = event.getView().getTitle();
      if (!TITLE_GENERAL.equals(title) && !TITLE_SPECIAL.equals(title)
         && !title.equals(this.plugin.getConfig().getString("online-shop.title", TITLE_GENERAL))) {
         return;
      }
      event.setCancelled(true);
      if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
         return;
      }
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) {
         return;
      }
      String action = clicked.getItemMeta().getPersistentDataContainer().get(this.productKey, PersistentDataType.STRING);
      if (action == null) {
         return;
      }
      if ("tab:general".equals(action)) {
         player.openInventory(this.createInventory(player, "general"));
         return;
      }
      if ("tab:special".equals(action)) {
         player.openInventory(this.createInventory(player, "special"));
         return;
      }
      this.purchase(player, action);
   }

   private Inventory createInventory(Player player, String category) {
      boolean special = "special".equalsIgnoreCase(category);
      Inventory inventory = Bukkit.createInventory(player, 54, special ? TITLE_SPECIAL : TITLE_GENERAL);
      inventory.setItem(45, this.tab(Material.CHEST, "§a一般タブ", "tab:general"));
      inventory.setItem(46, this.tab(Material.NETHER_STAR, "§d特殊アイテムタブ", "tab:special"));
      var section = this.plugin.getConfig().getConfigurationSection("online-shop.items");
      if (section == null) {
         return inventory;
      }
      int slot = 0;
      for (String id : section.getKeys(false)) {
         if (slot >= 45) break;
         String itemCategory = section.getString(id + ".category", "general");
         boolean itemSpecial = "special".equalsIgnoreCase(itemCategory) || "shop".equalsIgnoreCase(itemCategory);
         if (itemSpecial != special) continue;
         String materialName = section.getString(id + ".material", "PAPER");
         Material material = Material.matchMaterial(materialName);
         if (material == null) material = Material.PAPER;
         ItemStack icon = new ItemStack(material);
         ItemMeta meta = icon.getItemMeta();
         meta.setDisplayName(color(section.getString(id + ".display-name", id)));
         int price = Math.max(0, section.getInt(id + ".price", 0));
         String rarity = section.getString(id + ".rarity", "COMMON");
         long remaining = this.remainingSeconds(player, id);
         List<String> lore = new ArrayList<>();
         lore.add("§7カテゴリ: " + itemCategory);
         lore.add("§e価格: " + price + " MP");
         lore.add("§bレア度: " + rarity);
         lore.add(remaining > 0 ? "§cクールダウン残り " + remaining + " 秒" : "§a購入可能");
         lore.add("§8販売のみ / 買取なし");
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
         inventory.setItem(slot++, icon);
      }
      return inventory;
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
      String path = "online-shop.items." + id;
      if (!this.plugin.getConfig().contains(path)) return;
      long remaining = this.remainingSeconds(player, id);
      if (remaining > 0L) {
         player.sendMessage(ChatColor.RED + "購入クールダウン中です。残り " + remaining + " 秒");
         return;
      }
      int price = Math.max(0, this.plugin.getConfig().getInt(path + ".price", 0));
      ItemStack product = this.plugin.createOnlineShopProduct(id);
      if (product == null) return;
      if (!this.plugin.canReceiveOnlineShopProduct(player, product)) {
         player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MP が足りません。必要 MP: " + price);
         return;
      }
      this.plugin.grantOnlineShopProduct(player, product);
      long cooldown = Math.max(0L, this.plugin.getConfig().getLong(path + ".cooldown-seconds", 0L)) * 1000L;
      long until = System.currentTimeMillis() + cooldown;
      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, until);
      this.plugin.data().set("online-shop-cooldowns." + player.getUniqueId() + "." + id, until);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "OnlineShop で購入しました：" + id);
   }

   private static String color(String value) {
      return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
   }
}
