package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart13x1 extends MifronPart13 {
   protected Material serverIconMaterial(String path) {
      Material material = Material.matchMaterial(this.getConfig().getString(path + ".icon", "ender_pearl"));
      return material != null && material != Material.AIR && material.isItem() ? material : Material.ENDER_PEARL;
   }

   protected Material parseServerIcon(CommandSender sender, String raw) {
      Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
      if (material != null && material != Material.AIR && material.isItem()) return material;
      sender.sendMessage("\u00a7c\u30a2\u30a4\u30b3\u30f3\u7d20\u6750\u304c\u4f7f\u3048\u307e\u305b\u3093: " + raw);
      return null;
   }

   protected List<String> serverIconSuggestions(String prefix) {
      String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
      List<String> suggestions = new ArrayList<>();
      for (Material material : Material.values()) {
         if (material == Material.AIR || !material.isItem()) continue;
         String key = material.name().toLowerCase(Locale.ROOT);
         if (key.startsWith(normalized)) { suggestions.add(key); if (suggestions.size() >= 20) break; }
      }
      return suggestions;
   }

   protected ItemStack named(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.lore(lore.stream().map(Component::text).toList());
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      item.setItemMeta(meta);
      return item;
   }

   protected ItemStack actionItem(Material material, String name, List<String> lore, String action, String target) {
      ItemStack item = this.mifron().named(material, name, lore);
      ItemMeta meta = item.getItemMeta();
      PersistentDataContainer container = meta.getPersistentDataContainer();
      container.set(this.uiActionKey, PersistentDataType.STRING, action);
      if (target != null) container.set(this.uiTargetKey, PersistentDataType.STRING, target);
      item.setItemMeta(meta);
      return item;
   }

   protected String getUiAction(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(this.uiActionKey, PersistentDataType.STRING) : null;
   }

   protected UUID getUiTarget(ItemStack item) {
      String raw = this.getUiTargetString(item);
      if (raw == null) return null;
      try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
   }

   protected String getUiTargetString(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(this.uiTargetKey, PersistentDataType.STRING) : null;
   }

   protected void handleTeleporterUiItem(Player player, ItemStack clicked) {
      String action = this.mifron().getUiAction(clicked);
      String target = this.getUiTargetString(clicked);
      if ("teleport".equals(action) && target != null) { this.playUiClickSound(player); this.mifron().teleportToConfigLocation(player, target); player.closeInventory(); }
      else if ("server_portal_bind".equals(action) && target != null) {
         String[] parts = target.split("\\|", 2);
         if (parts.length == 2) { this.serverPortalFeature.setServerPortalTarget(parts[0], parts[1]); this.playUiClickSound(player); player.sendMessage("\u00a7a\u30dd\u30fc\u30bf\u30eb\u306e\u79fb\u52d5\u5148\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002"); player.closeInventory(); }
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (this.isBarrelShopInventory(event.getView().getTopInventory())) {
         event.setCancelled(true);
         if (event.getClickedInventory() == event.getView().getTopInventory()) this.buyBarrelOffer(player, event.getCurrentItem(), event.getSlot(), event.getView().getTopInventory());
         return;
      }
      if (event.getClickedInventory() == null) return;
      String title = this.mifron().inventoryTitle(event.getView().title());
      if ("\u00a73Mifron Friends".equals(title)) { event.setCancelled(true); this.mifron().handleFriendUiClick(player, event.getCurrentItem()); }
      else if ("\u00a72Mifron Status".equals(title)) { event.setCancelled(true); this.mifron().handleStatusUiClick(player, event.getCurrentItem()); }
      else if (QUEST_UI_TITLE.equals(title)) { event.setCancelled(true); this.mifron().handleQuestUiClick(player, event.getCurrentItem()); }
      else if ("\u00a75Mifron Teleporter".equals(title)) { event.setCancelled(true); this.handleTeleporterUiItem(player, event.getCurrentItem()); }
      else if ("\u00a76Mifron Merchant".equals(title)) {
         if (event.getClickedInventory() != event.getView().getTopInventory()) return;
         event.setCancelled(true);
         if (this.isMerchantOffer(event.getCurrentItem())) this.buyMerchantOffer(player, event.getCurrentItem(), event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT);
         else this.handleMerchantNavigation(player, event.getCurrentItem());
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (this.isBarrelShopInventory(event.getView().getTopInventory())) { event.setCancelled(true); return; }
      if (!"\u00a76Mifron Merchant".equals(this.mifron().inventoryTitle(event.getView().title()))) return;
      int topSize = event.getView().getTopInventory().getSize();
      for (int rawSlot : event.getRawSlots()) if (rawSlot < topSize) { event.setCancelled(true); return; }
   }

   @EventHandler
   public void onInventoryMoveItem(InventoryMoveItemEvent event) {
      if (this.isBarrelShopInventory(event.getSource()) || this.isBarrelShopInventory(event.getDestination())) event.setCancelled(true);
   }

   protected boolean isBarrelShopInventory(Inventory inventory) {
      return inventory != null && inventory.getHolder() instanceof Barrel barrel && this.mifron().isBarrelShop(barrel.getBlock());
   }
}
