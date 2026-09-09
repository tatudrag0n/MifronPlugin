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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Shelf;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

public class ShopBlockFeature implements Listener {
   static final String CONFIG_TITLE = "\u00a76\u30b7\u30e7\u30c3\u30d7\u4fa1\u683c\u8a2d\u5b9a";
   private static final int MAX_PLACEMENTS_PER_MINUTE = 10;
   private final Mifron plugin;
   private final NamespacedKey keyShopType;
   private final NamespacedKey keyOwnerUuid;
   private final NamespacedKey keyPlacementTime;
   private final NamespacedKey keyLastActivity;
   private final NamespacedKey keyAction;
   private final NamespacedKey[] keyPrices;
   private final Map<UUID, List<Long>> playerShopPlacementTimes = new HashMap<>();
   private final Map<UUID, EditSession> editing = new HashMap<>();

   public ShopBlockFeature(Mifron plugin) {
      this.plugin = plugin;
      this.keyShopType = new NamespacedKey(plugin, "shop_type");
      this.keyOwnerUuid = new NamespacedKey(plugin, "shop_owner");
      this.keyPlacementTime = new NamespacedKey(plugin, "shop_placement_time");
      this.keyLastActivity = new NamespacedKey(plugin, "shop_last_activity");
      this.keyAction = new NamespacedKey(plugin, "shop_ui_action");
      this.keyPrices = new NamespacedKey[] {
         new NamespacedKey(plugin, "shop_price_0"),
         new NamespacedKey(plugin, "shop_price_1"),
         new NamespacedKey(plugin, "shop_price_2")
      };
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      PersistentDataContainer pdc = this.blockData(event.getBlock());
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) return;
      if (!this.isOwnerOrAdmin(event.getPlayer(), pdc)) {
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
         player.sendMessage(ChatColor.RED + "\u30b7\u30e7\u30c3\u30d7\u306f1\u5206\u3042\u305f\u308a" + MAX_PLACEMENTS_PER_MINUTE + "\u500b\u307e\u3067\u8a2d\u7f6e\u3067\u304d\u307e\u3059\u3002");
         return;
      }
      if (!this.placeShopBlock(player, event.getBlockPlaced(), shopType)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "\u30b7\u30e7\u30c3\u30d7\u60c5\u5831\u3092\u4fdd\u5b58\u3067\u304d\u307e\u305b\u3093\u3002");
         return;
      }
      player.sendMessage(ChatColor.GREEN + "\u30b7\u30e7\u30c3\u30d7\u3092\u8a2d\u7f6e\u3057\u307e\u3057\u305f\u3002");
      player.sendMessage(ChatColor.GRAY + "\u9673\u5217\u306f\u30d0\u30cb\u30e9\u3069\u304a\u308a\u68da\u306b\u30a2\u30a4\u30c6\u30e0\u3092\u7f6e\u3044\u3066\u304f\u3060\u3055\u3044\u3002");
      player.sendMessage(ChatColor.GRAY + "\u4fa1\u683c\u306f\u30b9\u30cb\u30fc\u30af\uff0b\u53f3\u30af\u30ea\u30c3\u30af\u3067\u8a2d\u5b9a\u3057\u307e\u3059\u3002");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND) return;
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
      Block block = event.getClickedBlock();
      if (block == null || !this.isShopBlockType(block.getType())) return;
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) return;
      Player player = event.getPlayer();
      String shopType = pdc.get(this.keyShopType, PersistentDataType.STRING);
      boolean owner = this.isOwnerOrAdmin(player, pdc);
      boolean shelf = shopType != null && shopType.contains("SHELF");
      ItemStack hand = event.getItem();
      int slot = this.selectedSlot(player, block);
      if (this.isInactive(block) && !owner) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "\u3053\u306e\u30b7\u30e7\u30c3\u30d7\u306f1\u304b\u6708\u53d6\u5f15\u304c\u306a\u304f\u9589\u9396\u3055\u308c\u3066\u3044\u307e\u3059\u3002");
         return;
      }
      if (owner && player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         event.setCancelled(true);
         this.openPriceConfig(player, block, slot);
         return;
      }
      if (shopType != null && shopType.contains("BARREL")) {
         event.setCancelled(true);
         this.handleBarrelTrade(player, block, shopType, hand);
         return;
      }
      if (!shelf) return;
      if (owner && !this.isWallet(hand)) return;
      event.setCancelled(true);
      this.handleShelfTrade(player, block, shopType, hand, slot);
   }

   public boolean placeShopBlock(Player player, Block block, String shopType) {
      if (!(block.getState() instanceof TileState state)) return false;
      PersistentDataContainer pdc = state.getPersistentDataContainer();
      pdc.set(this.keyShopType, PersistentDataType.STRING, shopType);
      pdc.set(this.keyOwnerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
      pdc.set(this.keyPlacementTime, PersistentDataType.LONG, System.currentTimeMillis());
      pdc.set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      for (NamespacedKey key : this.keyPrices) pdc.set(key, PersistentDataType.INTEGER, 10);
      if (!state.update(true, false)) return false;
      this.recordShopBlockPlacement(player);
      return true;
   }

   public boolean canPlaceShopBlock(Player player) {
      long now = System.currentTimeMillis();
      List<Long> times = this.playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
      times.removeIf(time -> time < now - 60000L);
      return times.size() < MAX_PLACEMENTS_PER_MINUTE;
   }

   public void recordShopBlockPlacement(Player player) {
      this.playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(System.currentTimeMillis());
   }

   private PersistentDataContainer blockData(Block block) {
      return block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
   }

   private boolean isShopBlockType(Material type) {
      return type.name().endsWith("_SHELF") || type == Material.BARREL || type == Material.HOPPER;
   }

   private String shopTypeOf(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return null;
      return item.getItemMeta().getPersistentDataContainer().get(this.keyShopType, PersistentDataType.STRING);
   }

   private boolean isOwnerOrAdmin(Player player, PersistentDataContainer pdc) {
      if (player.hasPermission("mifron.admin") || player.hasPermission("mifron.shop.admin")) return true;
      String owner = pdc.get(this.keyOwnerUuid, PersistentDataType.STRING);
      return owner != null && owner.equals(player.getUniqueId().toString());
   }

   private boolean isWallet(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return false;
      return "emerald_bundle".equals(item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this.plugin, "item"), PersistentDataType.STRING));
   }

   private boolean isSpecial(ItemStack item) {
      return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this.plugin, "special_item_id"), PersistentDataType.STRING);
   }

   boolean isInactive(Block block) {
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null) return false;
      long last = pdc.getOrDefault(this.keyLastActivity, PersistentDataType.LONG, 0L);
      long days = Math.max(1L, this.plugin.getConfig().getLong("shops.inactivity-days", 30L));
      return last > 0L && System.currentTimeMillis() - last > days * 86400000L;
   }

   void touch(Block block) {
      if (!(block.getState() instanceof TileState state)) return;
      state.getPersistentDataContainer().set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      state.update(true, false);
   }

   private record EditSession(Block block, int slot) {}

   @EventHandler public void onClick(InventoryClickEvent event) {}
   @EventHandler public void onClose(InventoryCloseEvent event) {}
   @EventHandler public void onHopperMove(InventoryMoveItemEvent event) {}
   private void openPriceConfig(Player player, Block block, int slot) { player.sendMessage(ChatColor.YELLOW + "\u4fa1\u683c\u8a2d\u5b9a: \u67a0" + (slot + 1)); }
   private void handleShelfTrade(Player player, Block block, String shopType, ItemStack hand, int slot) {}
   private void handleBarrelTrade(Player player, Block block, String shopType, ItemStack hand) {}
   private int selectedSlot(Player player, Block shelf) { return 0; }
}
