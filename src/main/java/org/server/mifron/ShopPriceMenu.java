package org.server.mifron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class ShopPriceMenu {
   static final String TITLE = "\u00a76\u30b7\u30e7\u30c3\u30d7\u4fa1\u683c\u8a2d\u5b9a";
   private final ShopBlockStore store;
   private final Map<UUID, Session> editing = new HashMap<>();

   ShopPriceMenu(ShopBlockStore store) {
      this.store = store;
   }

   void open(Player player, Block block, int slot) {
      this.editing.put(player.getUniqueId(), new Session(block, slot));
      Inventory inventory = Bukkit.createInventory(player, 27, TITLE);
      ItemStack displayed = this.store.displayedItem(block, slot);
      int price = this.store.slotPrice(block, slot);
      inventory.setItem(4, this.named(Material.OAK_SIGN, "\u00a7e\u67a0" + (slot + 1) + " \u306e\u4fa1\u683c",
         List.of("\u00a77\u9673\u5217\u306f\u68da\u3092\u76f4\u63a5\u30af\u30ea\u30c3\u30af", "\u00a77\u3053\u3053\u3067\u306f\u4fa1\u683c\u3060\u3051\u5909\u3048\u307e\u3059")));
      inventory.setItem(10, this.action(Material.RED_CONCRETE, "\u00a7c-100 MP", "minus100"));
      inventory.setItem(11, this.action(Material.ORANGE_CONCRETE, "\u00a7c-10 MP", "minus10"));
      inventory.setItem(12, this.action(Material.YELLOW_CONCRETE, "\u00a7c-1 MP", "minus1"));
      ItemStack preview = displayed == null ? new ItemStack(Material.ITEM_FRAME) : displayed.clone();
      preview.setAmount(1);
      ItemMeta meta = preview.getItemMeta();
      meta.setDisplayName(displayed == null ? "\u00a77\u3053\u306e\u67a0\u306f\u7a7a\u3067\u3059" : "\u00a7a\u9673\u5217\u4e2d: " + displayed.getType().name());
      meta.setLore(List.of("\u00a7e\u4fa1\u683c: " + price + " MP"));
      preview.setItemMeta(meta);
      inventory.setItem(13, preview);
      inventory.setItem(14, this.action(Material.LIME_CONCRETE, "\u00a7a+1 MP", "plus1"));
      inventory.setItem(15, this.action(Material.GREEN_CONCRETE, "\u00a7a+10 MP", "plus10"));
      inventory.setItem(16, this.action(Material.CYAN_CONCRETE, "\u00a7a+100 MP", "plus100"));
      inventory.setItem(22, this.action(Material.BARRIER, "\u00a7c\u9589\u3058\u308b", "close"));
      player.openInventory(inventory);
   }

   void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player) || !TITLE.equals(event.getView().getTitle())) return;
      event.setCancelled(true);
      Session session = this.editing.get(player.getUniqueId());
      if (session == null) { player.closeInventory(); return; }
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) return;
      String action = clicked.getItemMeta().getPersistentDataContainer().get(this.store.keys.action, PersistentDataType.STRING);
      if (action == null) return;
      int price = this.store.slotPrice(session.block, session.slot);
      switch (action) {
         case "minus100" -> this.store.setSlotPrice(session.block, session.slot, Math.max(1, price - 100));
         case "minus10" -> this.store.setSlotPrice(session.block, session.slot, Math.max(1, price - 10));
         case "minus1" -> this.store.setSlotPrice(session.block, session.slot, Math.max(1, price - 1));
         case "plus1" -> this.store.setSlotPrice(session.block, session.slot, Math.min(1000000, price + 1));
         case "plus10" -> this.store.setSlotPrice(session.block, session.slot, Math.min(1000000, price + 10));
         case "plus100" -> this.store.setSlotPrice(session.block, session.slot, Math.min(1000000, price + 100));
         case "close" -> { player.closeInventory(); return; }
         default -> { return; }
      }
      this.open(player, session.block, session.slot);
   }

   void onClose(InventoryCloseEvent event) {
      if (TITLE.equals(event.getView().getTitle())) this.editing.remove(event.getPlayer().getUniqueId());
   }

   private ItemStack action(Material material, String name, String action) {
      ItemStack item = this.named(material, name, List.of());
      ItemMeta meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(this.store.keys.action, PersistentDataType.STRING, action);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack named(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (!lore.isEmpty()) meta.setLore(lore);
      item.setItemMeta(meta);
      return item;
   }

   private static final class Session {
      final Block block;
      final int slot;
      Session(Block block, int slot) { this.block = block; this.slot = slot; }
   }
}
