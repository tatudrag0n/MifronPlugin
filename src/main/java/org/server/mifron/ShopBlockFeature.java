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
import org.bukkit.event.block.BlockBreakEvent;
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
   private final NamespacedKey keyLastActivity;
   private final Map<UUID, List<Long>> playerShopPlacementTimes = new HashMap<>();

   public ShopBlockFeature(Mifron plugin) {
      this.plugin = plugin;
      this.keyShopType = new NamespacedKey(plugin, "shop_type");
      this.keyOwnerUuid = new NamespacedKey(plugin, "shop_owner");
      this.keyPlacementTime = new NamespacedKey(plugin, "shop_placement_time");
      this.keyLastActivity = new NamespacedKey(plugin, "shop_last_activity");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      PersistentDataContainer pdc = this.blockData(event.getBlock());
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) return;
      Player player = event.getPlayer();
      String owner = pdc.get(this.keyOwnerUuid, PersistentDataType.STRING);
      boolean admin = player.hasPermission("mifron.admin") || player.hasPermission("mifron.shop.admin");
      if (!admin && (owner == null || !owner.equals(player.getUniqueId().toString()))) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "このショップは作成者以外破壊できません。");
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      String shopType = this.shopTypeOf(event.getItemInHand());
      if (shopType == null) return;
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
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
      Block block = event.getClickedBlock();
      if (block == null || !this.isShopBlockType(block.getType())) return;
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) return;
      String shopType = pdc.get(this.keyShopType, PersistentDataType.STRING);
      if (shopType == null) return;
      event.setCancelled(true);
      if (shopType.equals("SELL_SHELF") || shopType.equals("BUY_SHELF")) {
         this.handleShelfShop(event.getPlayer(), shopType);
      } else if (shopType.equals("SELL_BARREL") || shopType.equals("BUY_BARREL")) {
         this.handleBarrelShop(event.getPlayer(), block, shopType);
      }
   }

   public boolean placeShopBlock(Player player, Block block, String shopType) {
      if (!(block.getState() instanceof TileState state)) return false;
      PersistentDataContainer pdc = state.getPersistentDataContainer();
      pdc.set(this.keyShopType, PersistentDataType.STRING, shopType);
      pdc.set(this.keyOwnerUuid, PersistentDataType.STRING, player.getUniqueId().toString());
      pdc.set(this.keyPlacementTime, PersistentDataType.LONG, System.currentTimeMillis());
      pdc.set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      if (!state.update(true, false)) return false;
      this.recordShopBlockPlacement(player);
      return true;
   }

   public boolean canPlaceShopBlock(Player player) {
      long now = System.currentTimeMillis();
      List<Long> times = this.playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
      times.removeIf(time -> time < now - 60_000L);
      return times.size() < MAX_PLACEMENTS_PER_MINUTE;
   }

   public void recordShopBlockPlacement(Player player) {
      this.playerShopPlacementTimes.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(System.currentTimeMillis());
   }

   private String shopTypeOf(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return null;
      ItemMeta meta = item.getItemMeta();
      return meta == null ? null : meta.getPersistentDataContainer().get(this.keyShopType, PersistentDataType.STRING);
   }

   private PersistentDataContainer blockData(Block block) {
      return block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
   }

   private boolean isShopBlockType(Material type) {
      String name = type.name();
      return name.endsWith("_SHELF") || type == Material.BARREL || type == Material.HOPPER;
   }

   void touch(Block block) {
      if (!(block.getState() instanceof TileState state)) return;
      state.getPersistentDataContainer().set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      state.update(true, false);
   }

   boolean isInactive(Block block) {
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null) return false;
      long last = pdc.getOrDefault(this.keyLastActivity, PersistentDataType.LONG, 0L);
      long days = Math.max(1L, this.plugin.getConfig().getLong("shops.inactivity-days", 30L));
      return last > 0L && System.currentTimeMillis() - last > days * 24L * 60L * 60L * 1000L;
   }

   private void handleShelfShop(Player player, String shopType) {
      if ("SELL_SHELF".equals(shopType)) {
         player.sendMessage(ChatColor.GREEN + "販売棚です。陳列アイテムと価格は作成者が設定します。");
      } else {
         player.sendMessage(ChatColor.GOLD + "買取棚です。対象アイテムと買取価格は作成者が設定します。");
      }
   }

   private void handleBarrelShop(Player player, Block block, String shopType) {
      if ("SELL_BARREL".equals(shopType)) {
         player.sendMessage(ChatColor.BLUE + "販売樽ショップです。");
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
      player.sendMessage(ChatColor.GOLD + "買取樽ショップです。");
   }
}
