package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ShopBlockFeature implements Listener {
   private static final int MAX_PLACEMENTS_PER_MINUTE = 10;
   private final ShopBlockStore store;
   private final ShopPriceMenu prices;
   private final ShopTradeService trades;
   private final Map<UUID, List<Long>> placements = new HashMap<>();

   public ShopBlockFeature(Mifron plugin) {
      this.store = new ShopBlockStore(plugin);
      this.prices = new ShopPriceMenu(this.store);
      this.trades = new ShopTradeService(plugin, this.store);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      if (!this.store.isShop(event.getBlock())) return;
      if (!this.store.isOwnerOrAdmin(event.getPlayer(), event.getBlock())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "\u3053\u306e\u30b7\u30e7\u30c3\u30d7\u306f\u4f5c\u6210\u8005\u4ee5\u5916\u7834\u58ca\u3067\u304d\u307e\u305b\u3093\u3002");
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      String shopType = this.shopTypeOf(event.getItemInHand());
      if (shopType == null) return;
      Player player = event.getPlayer();
      if (!this.canPlaceShopBlock(player)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "\u30b7\u30e7\u30c3\u30d7\u306f1\u5206\u3042\u305f\u308a" + MAX_PLACEMENTS_PER_MINUTE + "\u500b\u307e\u3067\u3067\u3059\u3002");
         return;
      }
      if (!this.placeShopBlock(player, event.getBlockPlaced(), shopType)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "\u30b7\u30e7\u30c3\u30d7\u60c5\u5831\u3092\u4fdd\u5b58\u3067\u304d\u307e\u305b\u3093\u3002");
         return;
      }
      player.sendMessage(ChatColor.GREEN + "\u30b7\u30e7\u30c3\u30d7\u3092\u8a2d\u7f6e\u3057\u307e\u3057\u305f\u3002\u9673\u5217\u306f\u30d0\u30cb\u30e9\u3001\u4fa1\u683c\u306f\u30b9\u30cb\u30fc\u30af\u53f3\u30af\u30ea\u30c3\u30af\u3067\u3059\u3002");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND) return;
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
      Block block = event.getClickedBlock();
      if (block == null || !this.isShopBlockType(block.getType()) || !this.store.isShop(block)) return;
      Player player = event.getPlayer();
      String shopType = this.store.shopType(block);
      boolean owner = this.store.isOwnerOrAdmin(player, block);
      ItemStack hand = event.getItem();
      int slot = this.store.selectedSlot(player, block);
      if (this.store.isInactive(block) && !owner) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "\u3053\u306e\u30b7\u30e7\u30c3\u30d7\u306f1\u304b\u6708\u53d6\u5f15\u304c\u306a\u304f\u9589\u9396\u3055\u308c\u3066\u3044\u307e\u3059\u3002");
         return;
      }
      if (owner && player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         event.setCancelled(true);
         this.prices.open(player, block, slot);
         return;
      }
      if (shopType.contains("BARREL")) {
         event.setCancelled(true);
         this.trades.tradeBarrel(player, block, shopType, hand);
         return;
      }
      if (!shopType.contains("SHELF")) return;
      if (owner && !this.trades.isWallet(hand)) return;
      event.setCancelled(true);
      this.trades.tradeShelf(player, block, shopType, hand, slot);
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      this.prices.onClick(event);
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      this.prices.onClose(event);
   }

   @EventHandler(ignoreCancelled = true)
   public void onHopperMove(InventoryMoveItemEvent event) {
      if (!(event.getDestination().getHolder() instanceof org.bukkit.block.Barrel barrel)) return;
      if (!this.store.isShop(barrel.getBlock()) || !"SELL_BARREL".equals(this.store.shopType(barrel.getBlock()))) return;
      ItemStack moving = event.getItem();
      if (moving == null || this.trades.isSpecial(moving) || this.trades.isWallet(moving)) event.setCancelled(true);
   }

   public boolean placeShopBlock(Player player, Block block, String shopType) {
      if (!this.store.tagNewShop(player, block, shopType)) return false;
      this.recordShopBlockPlacement(player);
      return true;
   }

   public boolean canPlaceShopBlock(Player player) {
      long now = System.currentTimeMillis();
      List<Long> times = this.placements.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
      times.removeIf(time -> time < now - 60000L);
      return times.size() < MAX_PLACEMENTS_PER_MINUTE;
   }

   public void recordShopBlockPlacement(Player player) {
      this.placements.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(System.currentTimeMillis());
   }

   private String shopTypeOf(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return null;
      return item.getItemMeta().getPersistentDataContainer().get(this.store.keys.shopType, PersistentDataType.STRING);
   }

   private boolean isShopBlockType(Material type) {
      return type.name().endsWith("_SHELF") || type == Material.BARREL || type == Material.HOPPER;
   }
}
