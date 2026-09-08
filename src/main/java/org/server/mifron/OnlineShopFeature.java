package org.server.mifron;

import java.util.HashMap;
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
         event.getPlayer().openInventory(this.createInventory(event.getPlayer()));
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)
         || !event.getView().getTitle().equals(this.plugin.getConfig().getString("online-shop.title", ""))) return;
      event.setCancelled(true);
      if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) return;
      String id = clicked.getItemMeta().getPersistentDataContainer().get(this.productKey, PersistentDataType.STRING);
      if (id == null) return;
      this.purchase(player, id);
   }

   private Inventory createInventory(Player player) {
      String title = this.plugin.getConfig().getString("online-shop.title", "");
      Inventory inventory = Bukkit.createInventory(player, 27, title);
      var section = this.plugin.getConfig().getConfigurationSection("online-shop.items");
      if (section == null) return inventory;
      int slot = 0;
      for (String id : section.getKeys(false)) {
         if (slot >= inventory.getSize()) break;
         String materialName = section.getString(id + ".material", "PAPER");
         Material material = Material.matchMaterial(materialName);
         if (material == null) material = Material.PAPER;
         ItemStack icon = new ItemStack(material);
         ItemMeta meta = icon.getItemMeta();
         meta.displayName(ComponentCompat.text(section.getString(id + ".display-name", id)));
         int price = Math.max(0, section.getInt(id + ".price", 0));
         String rarity = section.getString(id + ".rarity", "COMMON");
         String category = section.getString(id + ".category", "general");
         int cooldown = Math.max(0, section.getInt(id + ".cooldown-seconds", 0));
         meta.lore(java.util.List.of(ComponentCompat.text("" + category), ComponentCompat.text("" + price + "MP"), ComponentCompat.text("" + rarity), ComponentCompat.text("" + cooldown + ""), ComponentCompat.text("")));
         meta.getPersistentDataContainer().set(this.productKey, PersistentDataType.STRING, id);
         icon.setItemMeta(meta);
         inventory.setItem(slot++, icon);
      }
      return inventory;
   }

   private void purchase(Player player, String id) {
      if (!this.plugin.getConfig().getBoolean("online-shop.enabled", true)) return;
      String path = "online-shop.items." + id;
      if (!this.plugin.getConfig().contains(path)) return;
      long now = System.currentTimeMillis();
      String cooldownPath = "online-shop-cooldowns." + player.getUniqueId() + "." + id;
      long until = Math.max(
         this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).getOrDefault(id, 0L),
         this.plugin.data().getLong(cooldownPath, 0L)
      );
      if (until > now) {
         player.sendMessage(ChatColor.RED + "");
         return;
      }
      int price = Math.max(0, this.plugin.getConfig().getInt(path + ".price", 0));
      ItemStack product = this.plugin.createOnlineShopProduct(id);
      if (product == null) return;
      if (!this.plugin.canReceiveOnlineShopProduct(player, product)) {
         player.sendMessage(ChatColor.RED + "");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "");
         return;
      }
      this.plugin.grantOnlineShopProduct(player, product);
      long cooldown = Math.max(0L, this.plugin.getConfig().getLong(path + ".cooldown-seconds", 0L)) * 1000L;
      this.cooldowns.get(player.getUniqueId()).put(id, now + cooldown);
      this.plugin.data().set(cooldownPath, now + cooldown);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "");
   }

   private static final class ComponentCompat {
      private static net.kyori.adventure.text.Component text(String value) {
         return net.kyori.adventure.text.Component.text(value == null ? "" : value);
      }
   }
}
