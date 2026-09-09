package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ShopBlockFeature implements Listener {
   private static final int MAX_PLACEMENTS_PER_MINUTE = 10;
   private final Mifron plugin;
   private final NamespacedKey keyShopType;
   private final NamespacedKey keyOwnerUuid;
   private final NamespacedKey keyPlacementTime;
   private final Map<UUID, List<Long>> playerShopPlacementTimes = new HashMap<>();

   public ShopBlockFeature(Mifron plugin) {
      this.plugin = plugin;
      this.keyShopType = new NamespacedKey(plugin, "shop_type");
      this.keyOwnerUuid = new NamespacedKey(plugin, "shop_owner");
      this.keyPlacementTime = new NamespacedKey(plugin, "shop_placement_time");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      String shopType = this.shopTypeOf(event.getItemInHand());
      if (shopType == null) {
         return;
      }

      Player player = event.getPlayer();
      if (!this.canPlaceShopBlock(player)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "ショップブロックは1分あたり" + MAX_PLACEMENTS_PER_MINUTE + "個まで設置できます。");
         return;
      }

      if (!this.placeShopBlock(player, event.getBlockPlaced(), shopType)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "このブロックにはショップ情報を保存できません。");
         return;
      }

      player.sendMessage(ChatColor.GREEN + "ショップブロックを設置しました: " + shopType);
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
         return;
      }

      Block block = event.getClickedBlock();
      if (block == null || !this.isShopBlockType(block.getType())) {
         return;
      }

      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) {
         return;
      }

      String shopType = pdc.get(this.keyShopType, PersistentDataType.STRING);
      if (shopType == null) {
         return;
      }

      event.setCancelled(true);
      if (shopType.equals("SELL_SHELF") || shopType.equals("BUY_SHELF")) {
         this.handleShelfShop(event.getPlayer(), shopType);
      } else if (shopType.equals("SELL_BARREL") || shopType.equals("BUY_BARREL")) {
         this.handleBarrelShop(event.getPlayer(), block, shopType);
      }
   }

   public boolean placeShopBlock(Player player, Block block, String shopType) {
      if (!(block.getState() instanceof TileState state)) {
         return false;
      }

      PersistentDataContainer pdc = state.getPersistentDataContainer();
      pdc.set(this.keyShopType, PersistentDataType.STRING, shopType);
      pdc.set(this.keyOwnerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
      pdc.set(this.keyPlacementTime, PersistentDataType.LONG, System.currentTimeMillis());
      if (!state.update(true, false)) {
         return false;
      }

      this.recordShopBlockPlacement(player);
      return true;
   }

   public boolean canPlaceShopBlock(Player player) {
      long now = System.currentTimeMillis();
      long cutoff = now - 60_000L;
      List<Long> times = this.playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
      times.removeIf(time -> time < cutoff);
      return times.size() < MAX_PLACEMENTS_PER_MINUTE;
   }

   public void recordShopBlockPlacement(Player player) {
      this.playerShopPlacementTimes
         .computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>())
         .add(System.currentTimeMillis());
   }

   private String shopTypeOf(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return null;
      }
      ItemMeta meta = item.getItemMeta();
      return meta == null ? null : meta.getPersistentDataContainer().get(this.keyShopType, PersistentDataType.STRING);
   }

   private PersistentDataContainer blockData(Block block) {
      return block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
   }

   private boolean isShopBlockType(Material type) {
      return type == Material.OAK_SHELF
         || type == Material.SPRUCE_SHELF
         || type == Material.BIRCH_SHELF
         || type == Material.JUNGLE_SHELF
         || type == Material.ACACIA_SHELF
         || type == Material.DARK_OAK_SHELF
         || type == Material.MANGROVE_SHELF
         || type == Material.CHERRY_SHELF
         || type == Material.BAMBOO_SHELF
         || type == Material.CRIMSON_SHELF
         || type == Material.WARPED_SHELF
         || type == Material.BARREL
         || type == Material.HOPPER;
   }

   private void handleShelfShop(Player player, String shopType) {
      if ("SELL_SHELF".equals(shopType)) {
         player.sendMessage(ChatColor.GREEN + "販売棚ショップです。既存の棚ショップ操作で売買できます。");
      } else {
         player.sendMessage(ChatColor.GOLD + "買取棚ショップです。既存の棚ショップ操作で売買できます。");
      }
   }

   private void handleBarrelShop(Player player, Block block, String shopType) {
      if ("SELL_BARREL".equals(shopType)) {
         player.sendMessage(ChatColor.BLUE + "販売樽ショップです。既存の樽ショップ操作で購入できます。");
         return;
      }

      Block below = block.getRelative(BlockFace.DOWN);
      if (below.getType() != Material.HOPPER || !(below.getState() instanceof Hopper hopper)) {
         player.sendMessage(ChatColor.RED + "買取樽ショップには下にホッパーが必要です。");
         return;
      }

      Inventory hopperInv = hopper.getInventory();
      if (hopperInv.firstEmpty() == -1) {
         player.sendMessage(ChatColor.RED + "ホッパーがいっぱいです。買取できません。");
         return;
      }

      player.sendMessage(ChatColor.GOLD + "買取樽ショップです。既存の樽ショップ操作で売却できます。");
   }
}
