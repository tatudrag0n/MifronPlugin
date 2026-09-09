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
   static final String CONFIG_TITLE = "§6ショップ価格設定";
   private static final int MAX_PLACEMENTS_PER_MINUTE = 10;
   private final Mifron plugin;
   private final NamespacedKey keyShopType;
   private final NamespacedKey keyOwnerUuid;
   private final NamespacedKey keyPlacementTime;
   private final NamespacedKey keyLastActivity;
   private final NamespacedKey keyPriceSlot;
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
      this.keyPriceSlot = new NamespacedKey(plugin, "shop_price_slot");
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
      if (pdc == null || !pdc.has(this.keyShopType, PersistentDataType.STRING)) {
         return;
      }
      if (!this.isOwnerOrAdmin(event.getPlayer(), pdc)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "このショップは作成者以外破壊できません。");
      }
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
      player.sendMessage(ChatColor.GREEN + "ショップを設置しました。");
      player.sendMessage(ChatColor.GRAY + "陳列はバニラどおり棚にアイテムを置いてください。");
      player.sendMessage(ChatColor.GRAY + "価格はスニーク＋右クリックで設定します。");
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND) {
         return;
      }
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

      Player player = event.getPlayer();
      String shopType = pdc.get(this.keyShopType, PersistentDataType.STRING);
      boolean owner = this.isOwnerOrAdmin(player, pdc);
      boolean shelf = shopType != null && shopType.contains("SHELF");
      ItemStack hand = event.getItem();
      int slot = this.selectedSlot(player, block);

      if (this.isInactive(block) && !owner) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.RED + "このショップは1か月取引がなく閉鎖されています。");
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

      if (!shelf) {
         return;
      }

      // Owner uses vanilla placement / removal. Plugin does not touch that.
      if (owner && !this.isWallet(hand)) {
         return;
      }

      event.setCancelled(true);
      this.handleShelfTrade(player, block, shopType, hand, slot);
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player) || !CONFIG_TITLE.equals(event.getView().getTitle())) {
         return;
      }
      event.setCancelled(true);
      EditSession session = this.editing.get(player.getUniqueId());
      if (session == null || session.block() == null) {
         player.closeInventory();
         return;
      }
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) {
         return;
      }
      String action = clicked.getItemMeta().getPersistentDataContainer().get(this.keyAction, PersistentDataType.STRING);
      if (action == null) {
         return;
      }
      int price = this.slotPrice(session.block(), session.slot());
      switch (action) {
         case "minus100" -> this.setSlotPrice(session.block(), session.slot(), Math.max(1, price - 100));
         case "minus10" -> this.setSlotPrice(session.block(), session.slot(), Math.max(1, price - 10));
         case "minus1" -> this.setSlotPrice(session.block(), session.slot(), Math.max(1, price - 1));
         case "plus1" -> this.setSlotPrice(session.block(), session.slot(), Math.min(1_000_000, price + 1));
         case "plus10" -> this.setSlotPrice(session.block(), session.slot(), Math.min(1_000_000, price + 10));
         case "plus100" -> this.setSlotPrice(session.block(), session.slot(), Math.min(1_000_000, price + 100));
         case "close" -> {
            player.closeInventory();
            return;
         }
         default -> {
            return;
         }
      }
      this.openPriceConfig(player, session.block(), session.slot());
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (CONFIG_TITLE.equals(event.getView().getTitle())) {
         this.editing.remove(event.getPlayer().getUniqueId());
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onHopperMove(InventoryMoveItemEvent event) {
      if (!(event.getDestination().getHolder() instanceof org.bukkit.block.Barrel barrel)) {
         return;
      }
      PersistentDataContainer pdc = this.blockData(barrel.getBlock());
      if (pdc == null || !"SELL_BARREL".equals(pdc.get(this.keyShopType, PersistentDataType.STRING))) {
         return;
      }
      ItemStack moving = event.getItem();
      if (moving == null || this.isSpecial(moving) || this.isWallet(moving)) {
         event.setCancelled(true);
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
      pdc.set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      for (NamespacedKey key : this.keyPrices) {
         pdc.set(key, PersistentDataType.INTEGER, 10);
      }
      if (!state.update(true, false)) {
         return false;
      }
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

   private void openPriceConfig(Player player, Block block, int slot) {
      this.editing.put(player.getUniqueId(), new EditSession(block, slot));
      Inventory inventory = Bukkit.createInventory(player, 27, CONFIG_TITLE);
      ItemStack displayed = this.displayedItem(block, slot);
      int price = this.slotPrice(block, slot);
      inventory.setItem(4, this.named(Material.OAK_SIGN, "§e枠" + (slot + 1) + " の価格",
         List.of("§7陳列は棚を直接クリックして変更", "§7ここでは価格だけ変えます")));
      inventory.setItem(10, this.action(Material.RED_CONCRETE, "§c-100 MP", "minus100"));
      inventory.setItem(11, this.action(Material.ORANGE_CONCRETE, "§c-10 MP", "minus10"));
      inventory.setItem(12, this.action(Material.YELLOW_CONCRETE, "§c-1 MP", "minus1"));
      ItemStack preview = displayed == null ? new ItemStack(Material.ITEM_FRAME) : displayed.clone();
      preview.setAmount(1);
      ItemMeta meta = preview.getItemMeta();
      meta.setDisplayName(displayed == null ? "§7この枠は空です" : "§a陳列中: " + displayed.getType().name());
      meta.setLore(List.of("§e価格: " + price + " MP", "§8バニラの棚スロット " + (slot + 1)));
      preview.setItemMeta(meta);
      inventory.setItem(13, preview);
      inventory.setItem(14, this.action(Material.LIME_CONCRETE, "§a+1 MP", "plus1"));
      inventory.setItem(15, this.action(Material.GREEN_CONCRETE, "§a+10 MP", "plus10"));
      inventory.setItem(16, this.action(Material.CYAN_CONCRETE, "§a+100 MP", "plus100"));
      inventory.setItem(22, this.action(Material.BARRIER, "§c閉じる", "close"));
      player.openInventory(inventory);
   }

   private void handleShelfTrade(Player player, Block block, String shopType, ItemStack hand, int slot) {
      ItemStack displayed = this.displayedItem(block, slot);
      if (displayed == null) {
         player.sendMessage(ChatColor.YELLOW + "この枠には商品が並んでいません。");
         return;
      }
      if (this.isSpecial(displayed)) {
         player.sendMessage(ChatColor.RED + "特殊アイテムは売買できません。");
         return;
      }
      int price = this.slotPrice(block, slot);
      boolean buyShop = "BUY_SHELF".equals(shopType);
      if (buyShop) {
         if (hand == null || hand.getType() != displayed.getType() || this.isSpecial(hand) || this.isWallet(hand)) {
            player.sendMessage(ChatColor.GOLD + "買取: " + displayed.getType().name() + " = " + price + " MP");
            return;
         }
         hand.setAmount(hand.getAmount() - 1);
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         this.touch(block);
         player.sendMessage(ChatColor.GOLD + displayed.getType().name() + " を " + price + " MP で売却しました。");
         return;
      }
      if (!this.isWallet(hand)) {
         player.sendMessage(ChatColor.YELLOW + "販売: " + displayed.getType().name() + " = " + price + " MP");
         player.sendMessage(ChatColor.GRAY + "ウォレットを持って右クリックで購入します。");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MPが足りません。必要: " + price);
         return;
      }
      ItemStack product = displayed.clone();
      product.setAmount(1);
      HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(product);
      if (!leftover.isEmpty()) {
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
         return;
      }
      this.touch(block);
      player.sendMessage(ChatColor.GREEN + displayed.getType().name() + " を " + price + " MP で購入しました。");
   }

   private void handleBarrelTrade(Player player, Block block, String shopType, ItemStack hand) {
      if ("BUY_BARREL".equals(shopType)) {
         Block below = block.getRelative(BlockFace.DOWN);
         if (below.getType() != Material.HOPPER || !(below.getState() instanceof Hopper hopper) || hopper.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "買取樽は空きのあるホッパーを下に接続してください。");
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
         player.sendMessage(ChatColor.YELLOW + "樽に商品が入っていません。");
         return;
      }
      int price = this.slotPrice(block, 0);
      boolean buy = "BUY_BARREL".equals(shopType);
      if (buy) {
         if (hand == null || hand.getType() != displayed.getType() || this.isSpecial(hand)) {
            player.sendMessage(ChatColor.GOLD + "買取: " + displayed.getType().name() + " = " + price + " MP");
            return;
         }
         hand.setAmount(hand.getAmount() - 1);
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         this.touch(block);
         player.sendMessage(ChatColor.GOLD + displayed.getType().name() + " を " + price + " MP で売却しました。");
         return;
      }
      if (!this.isWallet(hand)) {
         player.sendMessage(ChatColor.YELLOW + "販売: " + displayed.getType().name() + " = " + price + " MP");
         return;
      }
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(ChatColor.RED + "MPが足りません。必要: " + price);
         return;
      }
      ItemStack product = displayed.clone();
      product.setAmount(1);
      if (!player.getInventory().addItem(product).isEmpty()) {
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
         return;
      }
      this.touch(block);
      player.sendMessage(ChatColor.GREEN + displayed.getType().name() + " を " + price + " MP で購入しました。");
   }

   private ItemStack displayedItem(Block block, int slot) {
      if (!(block.getState() instanceof Shelf shelf)) {
         return null;
      }
      ItemStack[] contents = shelf.getSnapshotInventory().getContents();
      if (slot >= 0 && slot < contents.length && contents[slot] != null && contents[slot].getType() != Material.AIR) {
         return contents[slot];
      }
      return null;
   }

   private int selectedSlot(Player player, Block shelf) {
      if (!(shelf.getState() instanceof Shelf shelfState)) {
         return 0;
      }
      int size = Math.max(1, Math.min(3, shelfState.getInventory().getSize()));
      RayTraceResult result = player.rayTraceBlocks(5.0);
      if (result == null || result.getHitBlock() == null || !result.getHitBlock().equals(shelf) || result.getHitPosition() == null) {
         return 0;
      }
      BlockFace facing = shelf.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.NORTH;
      double local = switch (facing) {
         case NORTH -> result.getHitPosition().getX() - shelf.getX();
         case SOUTH -> 1.0 - (result.getHitPosition().getX() - shelf.getX());
         case EAST -> result.getHitPosition().getZ() - shelf.getZ();
         case WEST -> 1.0 - (result.getHitPosition().getZ() - shelf.getZ());
         default -> result.getHitPosition().getX() - shelf.getX();
      };
      local = Math.max(0.0, Math.min(0.999999, local));
      int slot = (int) Math.floor(local * size);
      return Math.max(0, Math.min(size - 1, slot));
   }

   private int slotPrice(Block block, int slot) {
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null) {
         return 10;
      }
      int index = Math.max(0, Math.min(this.keyPrices.length - 1, slot));
      return Math.max(1, pdc.getOrDefault(this.keyPrices[index], PersistentDataType.INTEGER, 10));
   }

   private void setSlotPrice(Block block, int slot, int price) {
      if (!(block.getState() instanceof TileState state)) {
         return;
      }
      int index = Math.max(0, Math.min(this.keyPrices.length - 1, slot));
      state.getPersistentDataContainer().set(this.keyPrices[index], PersistentDataType.INTEGER, Math.max(1, price));
      state.update(true, false);
   }

   void touch(Block block) {
      if (!(block.getState() instanceof TileState state)) {
         return;
      }
      state.getPersistentDataContainer().set(this.keyLastActivity, PersistentDataType.LONG, System.currentTimeMillis());
      state.update(true, false);
   }

   boolean isInactive(Block block) {
      PersistentDataContainer pdc = this.blockData(block);
      if (pdc == null) {
         return false;
      }
      long last = pdc.getOrDefault(this.keyLastActivity, PersistentDataType.LONG, 0L);
      long days = Math.max(1L, this.plugin.getConfig().getLong("shops.inactivity-days", 30L));
      return last > 0L && System.currentTimeMillis() - last > days * 24L * 60L * 60L * 1000L;
   }

   private boolean isOwnerOrAdmin(Player player, PersistentDataContainer pdc) {
      if (player.hasPermission("mifron.admin") || player.hasPermission("mifron.shop.admin")) {
         return true;
      }
      String owner = pdc.get(this.keyOwnerUuid, PersistentDataType.STRING);
      return owner != null && owner.equals(player.getUniqueId().toString());
   }

   private String shopTypeOf(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return null;
      }
      return item.getItemMeta().getPersistentDataContainer().get(this.keyShopType, PersistentDataType.STRING);
   }

   private PersistentDataContainer blockData(Block block) {
      return block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
   }

   private boolean isShopBlockType(Material type) {
      return type.name().endsWith("_SHELF") || type == Material.BARREL || type == Material.HOPPER;
   }

   private boolean isWallet(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return false;
      }
      return "emerald_bundle".equals(item.getItemMeta().getPersistentDataContainer()
         .get(new NamespacedKey(this.plugin, "item"), PersistentDataType.STRING));
   }

   private boolean isSpecial(ItemStack item) {
      return item != null && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this.plugin, "special_item_id"), PersistentDataType.STRING);
   }

   private ItemStack action(Material material, String name, String action) {
      ItemStack item = this.named(material, name, List.of());
      ItemMeta meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(this.keyAction, PersistentDataType.STRING, action);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack named(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      if (!lore.isEmpty()) {
         meta.setLore(lore);
      }
      item.setItemMeta(meta);
      return item;
   }

   private record EditSession(Block block, int slot) {
   }
}
