package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class QuestBookGuard implements Listener {
   private final Mifron plugin;
   private final NamespacedKey itemKey;

   QuestBookGuard(Mifron plugin) {
      this.plugin = plugin;
      this.itemKey = new NamespacedKey(plugin, "item");
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Bukkit.getScheduler().runTask(this.plugin, () -> this.convert(event.getPlayer()));
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onUse(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (!this.isUtilityBook(item)) return;
      if ("quest_book".equals(this.id(item)) && item.getType() != Material.BOOK) item.setType(Material.BOOK);
      event.setCancelled(true);
   }

   private void convert(Player player) {
      if (player == null) return;
      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isUtilityBook(item) && "quest_book".equals(this.id(item)) && item.getType() != Material.BOOK) {
            item.setType(Material.BOOK);
         }
      }
   }

   private boolean isUtilityBook(ItemStack item) {
      String id = this.id(item);
      return "quest_book".equals(id) || "friend_book".equals(id);
   }

   private String id(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return null;
      return item.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING);
   }
}
