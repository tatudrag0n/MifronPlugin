package org.server.mifron;

import java.util.HashMap;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class ShopTradeService {
   private final Mifron plugin;
   private final ShopBlockStore store;
   private final NamespacedKey itemKey;
   private final NamespacedKey specialKey;

   ShopTradeService(Mifron plugin, ShopBlockStore store) {
      this.plugin = plugin;
      this.store = store;
      this.itemKey = new NamespacedKey(plugin, "item");
      this.specialKey = new NamespacedKey(plugin, "special_item_id");
   }

   boolean isWallet(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return false;
      return "emerald_bundle".equals(item.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING));
   }

   boolean isSpecial(ItemStack item) {
      return item != null && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(this.specialKey, PersistentDataType.STRING);
   }

   void tradeShelf(Player player, Block block, String shopType, ItemStack hand, int slot) {
      ItemStack displayed = this.store.displayedItem(block, slot);
      if (displayed == null) {
         player.sendMessage(ChatColor.YELLOW + "\u3053\u306e\u67a0\u306b\u306f\u5546\u54c1\u304c\u4e26\u3093\u3067\u3044\u307e\u305b\u3093\u3002");
         return;
      }
      if (this.isSpecial(displayed)) {
         player.sendMessage(ChatColor.RED + "\u7279\u6b8a\u30a2\u30a4\u30c6\u30e0\u306f\u58f2\u8cb7\u3067\u304d\u307e\u305b\u3093\u3002");
         return;
      }
      this.trade(player, block, "BUY_SHELF".equals(shopType), displayed, hand, this.store.slotPrice(block, slot));
   }

   void tradeBarrel(Player player, Block block, String shopType, ItemStack hand) {
      if ("BUY_BARREL".equals(shopType)) {
         Block below = block.getRelative(BlockFace.DOWN);
         if (below.getType() != Material.HOPPER || !(below.getState() instanceof Hopper hopper) || hopper.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "\u8cb7\u53d6\u6a3d\u306f\u7a7a\u304d\u306e\u3042\u308b\u30db\u30c3\u30d1\u30fc\u3092\u4e0b\u306b\u63a5\u7d9a\u3057\u3066\u304f\u3060\u3055\u3044\u3002");
            return;
         }
      }
      ItemStack displayed = null;
      if (block.getState() instanceof org.bukkit.block.Barrel barrel) {
         for (ItemStack item : barrel.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && !this.isSpecial(item) && !this.isWallet(item)) {
               displayed = item;
               break;
            }
         }
      }
      if (displayed == null) {
         player.sendMessage(ChatColor.YELLOW + "\u6a3d\u306b\u5546\u54c1\u304c\u5165\u3063\u3066\u3044\u307e\u305b\u3093\u3002");
         return;
      }
      this.trade(player, block, "BUY_BARREL".equals(shopType), displayed, hand, this.store.slotPrice(block, 0));
   }

   private void trade(Player player, Block block, boolean buyShop, ItemStack displayed, ItemStack hand, int price) {
      if (buyShop) {
         if (hand == null || hand.getType() != displayed.getType() || this.isSpecial(hand) || this.isWallet(hand)) {
            player.sendMessage(ChatColor.GOLD + "\u8cb7\u53d6: " + displayed.getType().name() + " = " + price + " MP");
            return;
         }
         hand.setAmount(hand.getAmount() - 1);
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         this.store.touch(block);
         player.sendMessage(ChatColor.GOLD + displayed.getType().name() + " \u3092 " + price + " MP \u3067\u58f2\u5374\u3057\u307e\u3057\u305f\u3002");
         return;
      }
      if (!this.isWallet(hand)) {
         player.sendMessage(ChatColor.YELLOW + "\u8ca9\u58f2: " + displayed.getType().name() + " = " + price + " MP");
         player.sendMessage(ChatColor.GRAY + "\u30a6\u30a9\u30ec\u30c3\u30c8\u3092\u6301\u3063\u3066\u53f3\u30af\u30ea\u30c3\u30af\u3067\u8cfc\u5165\u3057\u307e\u3059\u3002");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MP\u304c\u8db3\u308a\u307e\u305b\u3093\u3002\u5fc5\u8981: " + price);
         return;
      }
      ItemStack product = displayed.clone();
      product.setAmount(1);
      HashMap leftover = player.getInventory().addItem(product);
      if (!leftover.isEmpty()) {
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "\u30a4\u30f3\u30d9\u30f3\u30c8\u30ea\u306b\u7a7a\u304d\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      this.store.touch(block);
      player.sendMessage(ChatColor.GREEN + displayed.getType().name() + " \u3092 " + price + " MP \u3067\u8cfc\u5165\u3057\u307e\u3057\u305f\u3002");
   }
}
