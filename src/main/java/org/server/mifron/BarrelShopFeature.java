package org.server.mifron;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Shelf;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Barrel-backed shops. A shelf placed on top of an EMPTY barrel becomes a shop.
 *   SELL shelf: stock = items in the barrel; a customer clicks the shelf to buy one.
 *   BUY  shelf: a customer holding the item clicks the shelf; the item goes into the barrel.
 * The shelf and barrel belong to the creator; nobody else can open or break them.
 */
public final class BarrelShopFeature implements Listener {
   static final int DEFAULT_PRICE = 1000;
   static final int SHELF_STOCK_LIMIT = 1;

   private final Mifron plugin;
   final NamespacedKey shopTypeKey;
   final NamespacedKey ownerKey;
   final NamespacedKey priceKey;
   final NamespacedKey itemKey;

   public BarrelShopFeature(Mifron plugin) {
      this.plugin = plugin;
      this.shopTypeKey = new NamespacedKey(plugin, "barrel_shop_type");
      this.ownerKey = new NamespacedKey(plugin, "barrel_shop_owner");
      this.priceKey = new NamespacedKey(plugin, "barrel_shop_price");
      this.itemKey = new NamespacedKey(plugin, "barrel_shop_item");
   }

   // ---- item factory ----

   public ItemStack createSellShelf() { return this.createShelf("SELL", Material.OAK_SHELF, "§a販売棚ショップ"); }
   public ItemStack createBuyShelf() { return this.createShelf("BUY", Material.SPRUCE_SHELF, "§6買取棚ショップ"); }

   private ItemStack createShelf(String type, Material material, String name) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(name);
      meta.setLore(java.util.List.of(
         "§7空の樽の上に置くと店になる。",
         "§7作成後は作成者以外は触れない。",
         "§7価格: §e" + DEFAULT_PRICE + " MP §7/ 在庫: §e" + SHELF_STOCK_LIMIT));
      meta.getPersistentDataContainer().set(this.shopTypeKey, PersistentDataType.STRING, type);
      item.setItemMeta(meta);
      return item;
   }

   private String itemType(ItemStack item) {
      if (item == null || !item.hasItemMeta()) return null;
      return item.getItemMeta().getPersistentDataContainer().get(this.shopTypeKey, PersistentDataType.STRING);
   }

   // ---- creation: shelf on top of an empty barrel ----

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      String type = this.itemType(event.getItemInHand());
      if (type == null) return;
      Player player = event.getPlayer();
      Block shelf = event.getBlockPlaced();
      Block below = shelf.getRelative(BlockFace.DOWN);
      if (!(below.getState() instanceof Barrel barrel)) {
         event.setCancelled(true);
         player.sendMessage(Component.text("販売棚・買取棚は空の樽の上に置いてください。", NamedTextColor.RED));
         return;
      }
      if (!barrel.getInventory().isEmpty()) {
         event.setCancelled(true);
         player.sendMessage(Component.text("樽は空にしてから置いてください。", NamedTextColor.RED));
         return;
      }
      this.tagShelf(shelf, player.getUniqueId(), type);
      this.tagBarrel(below, player.getUniqueId());
      this.refreshShelfDisplay(shelf);
      player.sendMessage(Component.text(("SELL".equals(type) ? "販売" : "買取") + "棚ショップを作成しました。樽の中身が在庫/買取品になります。", NamedTextColor.GREEN));
   }

   private void tagShelf(Block shelf, UUID owner, String type) {
      if (!(shelf.getState() instanceof TileState state)) return;
      PersistentDataContainer pdc = state.getPersistentDataContainer();
      pdc.set(this.shopTypeKey, PersistentDataType.STRING, type);
      pdc.set(this.ownerKey, PersistentDataType.STRING, owner.toString());
      pdc.set(this.priceKey, PersistentDataType.INTEGER, DEFAULT_PRICE);
      state.update(true, false);
   }

   private void tagBarrel(Block barrel, UUID owner) {
      if (!(barrel.getState() instanceof TileState state)) return;
      state.getPersistentDataContainer().set(this.ownerKey, PersistentDataType.STRING, owner.toString());
      state.update(true, false);
   }

   // ---- shop lookup ----

   /** The shelf shop at this block, or null. */
   public String shelfType(Block block) {
      if (block == null || !(block.getState() instanceof TileState state)) return null;
      return state.getPersistentDataContainer().get(this.shopTypeKey, PersistentDataType.STRING);
   }

   public UUID ownerOf(Block block) {
      if (block == null || !(block.getState() instanceof TileState state)) return null;
      String raw = state.getPersistentDataContainer().get(this.ownerKey, PersistentDataType.STRING);
      try { return raw == null ? null : UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
   }

   public boolean isOwnerOrAdmin(Player player, UUID owner) {
      if (player.hasPermission("mifron.admin") || player.hasPermission("mifron.shop.admin")) return true;
      return owner != null && owner.equals(player.getUniqueId());
   }

   /** Barrel directly under a shelf shop, or null. */
   public Block barrelUnder(Block shelf) {
      Block below = shelf.getRelative(BlockFace.DOWN);
      return below.getState() instanceof Barrel ? below : null;
   }

   // ---- protection: only the owner may open or break the barrel and shelf ----

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      Block block = event.getBlock();
      if (!this.isProtected(block)) return;
      UUID owner = this.ownerOf(block);
      if (!this.isOwnerOrAdmin(event.getPlayer(), owner)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(Component.text("この店は作成者以外は壊せません。", NamedTextColor.RED));
      }
   }

   /** A shelf shop, or a barrel that sits under a shelf shop. */
   private boolean isProtected(Block block) {
      if (this.shelfType(block) != null) return true;
      if (block.getState() instanceof Barrel) {
         Block above = block.getRelative(BlockFace.UP);
         return this.shelfType(above) != null;
      }
      return false;
   }



   /** Mirrors each section's first item onto the shelf's 3 display slots (left / middle / right). */
   public void refreshShelfDisplay(Block shelfBlock) {
      if (!(shelfBlock.getState() instanceof Shelf shelf)) return;
      Block barrel = this.barrelUnder(shelfBlock);
      if (barrel == null || !(barrel.getState() instanceof Barrel state)) return;
      Inventory shelfInv = shelf.getInventory();
      for (int section = 0; section < SECTIONS; section++) {
         ItemStack item = sectionStock(state.getInventory(), section);
         ItemStack view = item == null ? null : item.clone();
         if (view != null) view.setAmount(1);
         if (section < shelfInv.getSize()) shelfInv.setItem(section, view);
      }
      shelf.update(true, false);
   }


   // ---- keep the shelf display in sync with the barrel contents ----

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
   public void onBarrelClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
      this.refreshIfShopBarrel(event.getInventory().getHolder());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
   public void onBarrelClick(org.bukkit.event.inventory.InventoryClickEvent event) {
      org.bukkit.inventory.InventoryHolder holder = event.getView().getTopInventory().getHolder();
      if (holder == null) return;
      org.bukkit.Bukkit.getScheduler().runTask(this.plugin, () -> this.refreshIfShopBarrel(holder));
   }

   private void refreshIfShopBarrel(org.bukkit.inventory.InventoryHolder holder) {
      if (!(holder instanceof Barrel barrel)) return;
      Block barrelBlock = barrel.getBlock();
      Block above = barrelBlock.getRelative(BlockFace.UP);
      if (this.shelfType(above) != null) this.refreshShelfDisplay(above);
   }

   // ---- sections: barrel 27 slots = 3 sections x 9 slots, mapped to the shelf's left/middle/right ----

   static final int SECTIONS = 3;

   /** Barrel slots (0-based) per section: each row of 9 is cut into 3 + 3 + 3. */
   static final int[][] SECTION_SLOTS = {
      {0, 1, 2, 9, 10, 11, 18, 19, 20},     // left
      {3, 4, 5, 12, 13, 14, 21, 22, 23},    // middle
      {6, 7, 8, 15, 16, 17, 24, 25, 26},    // right
   };

   /** Section 0 (left), 1 (middle), 2 (right) of the shelf the player is looking at, or -1. */
   int sectionAt(Player player, Block shelf) {
      org.bukkit.util.RayTraceResult hit = player.rayTraceBlocks(5.0);
      if (hit == null || hit.getHitBlock() == null || !hit.getHitBlock().equals(shelf) || hit.getHitPosition() == null) {
         return -1;
      }
      org.bukkit.block.data.BlockData data = shelf.getBlockData();
      BlockFace facing = data instanceof org.bukkit.block.data.Directional directional ? directional.getFacing() : BlockFace.NORTH;
      double x = hit.getHitPosition().getX() - shelf.getX();
      double z = hit.getHitPosition().getZ() - shelf.getZ();
      // Local coordinate along the shelf's width, left -> right as seen from the front.
      // Viewer stands on the facing side. Left as seen by the viewer: NORTH -> +X, SOUTH -> -X, EAST -> +Z, WEST -> -Z.
      double local = switch (facing) {
         case NORTH -> 1.0 - x;
         case SOUTH -> x;
         case EAST -> z;
         case WEST -> 1.0 - z;
         default -> 1.0 - x;
      };
      local = Math.max(0.0, Math.min(0.999999, local));
      return Math.min(SECTIONS - 1, (int) Math.floor(local * SECTIONS));
   }

   /** First stock stack inside section {@code section} of the barrel, or null. */
   static ItemStack sectionStock(Inventory inv, int section) {
      for (int i : SECTION_SLOTS[section]) {
         ItemStack item = inv.getItem(i);
         if (item != null && !item.getType().isAir() && item.getAmount() > 0) return item;
      }
      return null;
   }

   /** Item count inside section {@code section} of the barrel. */
   static int sectionCount(Inventory inv, int section) {
      int total = 0;
      for (int i : SECTION_SLOTS[section]) {
         ItemStack item = inv.getItem(i);
         if (item != null && !item.getType().isAir()) total += item.getAmount();
      }
      return total;
   }

   /** First empty slot in section {@code section}, or -1. */
   static int sectionEmptySlot(Inventory inv, int section) {
      for (int i : SECTION_SLOTS[section]) {
         ItemStack item = inv.getItem(i);
         if (item == null || item.getType().isAir()) return i;
      }
      return -1;
   }

   // ---- trading ----

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onInteract(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND) return;
      Block block = event.getClickedBlock();
      if (block == null) return;
      Player player = event.getPlayer();

      // Barrel that belongs to a shop: owner-only access.
      if (block.getState() instanceof Barrel && this.isShopBarrel(block)) {
         if (!this.isOwnerOrAdmin(player, this.ownerOf(block))) {
            event.setCancelled(true);
            player.sendMessage(Component.text("この樽は店の在庫です。作成者のみ開けます。", NamedTextColor.RED));
         }
         return;
      }

      String type = this.shelfType(block);
      if (type == null || !(block.getState() instanceof Shelf)) return;
      if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
      event.setCancelled(true);

      UUID owner = this.ownerOf(block);
      if (this.isOwnerOrAdmin(player, owner)) {
         player.sendMessage(Component.text("自分の店です。樽の左・中・右それぞれ9マスが、棚の左・中・右に対応します。", NamedTextColor.GRAY));
         return;
      }
      Block barrel = this.barrelUnder(block);
      if (barrel == null) {
         player.sendMessage(Component.text("樽が見つかりません。", NamedTextColor.RED));
         return;
      }
      int section = this.sectionAt(player, block);
      if (section < 0) return;
      if ("SELL".equals(type)) {
         this.sell(player, block, barrel, section);
      } else {
         this.buy(player, block, barrel, section);
      }
   }

   private boolean isShopBarrel(Block barrel) {
      Block above = barrel.getRelative(BlockFace.UP);
      return this.shelfType(above) != null;
   }

   /** Customer buys one item from the barrel stock. */
   private void sell(Player player, Block shelf, Block barrel, int section) {
      Barrel state = (Barrel) barrel.getState();
      Inventory inv = state.getInventory();
      ItemStack stock = sectionStock(inv, section);
      if (stock == null) {
         player.sendMessage(Component.text("売り切れです。", NamedTextColor.RED));
         return;
      }
      int price = this.priceOf(shelf);
      if (!this.plugin.withdrawEmeralds(player.getUniqueId(), price)) {
         player.sendMessage(Component.text("MPが足りません。必要MP: " + price, NamedTextColor.RED));
         return;
      }
      ItemStack give = stock.clone();
      give.setAmount(1);
      if (!player.getInventory().addItem(give).isEmpty()) {
         this.plugin.depositEmeralds(player.getUniqueId(), price);
         player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
         return;
      }
      stock.setAmount(stock.getAmount() - 1);
      state.update(true, false);
      this.refreshShelfDisplay(shelf);
      player.sendMessage(Component.text(stock.getType().name() + " を " + price + " MP で購入しました。", NamedTextColor.GREEN));
   }

   /** Customer sells the held item; it is stored in the barrel and paid out. */
   private void buy(Player player, Block shelf, Block barrel, int section) {
      ItemStack hand = player.getInventory().getItemInMainHand();
      if (hand == null || hand.getType().isAir()) {
         player.sendMessage(Component.text("買取品を手に持ってクリックしてください。", NamedTextColor.YELLOW));
         return;
      }
      int price = this.priceOf(shelf);
      Barrel state = (Barrel) barrel.getState();
      Inventory inv = state.getInventory();
      ItemStack one = hand.clone();
      one.setAmount(1);
      int slot = sectionEmptySlot(inv, section);
      if (slot < 0) {
         player.sendMessage(Component.text("この区画に空きがありません。", NamedTextColor.RED));
         return;
      }
      inv.setItem(slot, one);
      hand.setAmount(hand.getAmount() - 1);
      state.update(true, false);
      this.refreshShelfDisplay(shelf);
      this.plugin.depositEmeralds(player.getUniqueId(), price);
      player.sendMessage(Component.text(one.getType().name() + " を " + price + " MP で買い取りました。", NamedTextColor.GOLD));
   }

   private int priceOf(Block shelf) {
      if (!(shelf.getState() instanceof TileState state)) return DEFAULT_PRICE;
      Integer value = state.getPersistentDataContainer().get(this.priceKey, PersistentDataType.INTEGER);
      return value == null ? DEFAULT_PRICE : Math.max(1, value);
   }

   static ItemStack firstNonEmpty(Inventory inv) {
      for (ItemStack item : inv.getContents()) {
         if (item != null && !item.getType().isAir() && item.getAmount() > 0) return item;
      }
      return null;
   }

   // ---- hover: show remaining stock while looking at a sell shelf ----

   /** Starts the look-at ticker (every 10 ticks). */
   public void startHoverTask() {
      org.bukkit.Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            org.bukkit.util.RayTraceResult hit = player.rayTraceBlocks(5.0);
            if (hit == null || hit.getHitBlock() == null) continue;
            Block block = hit.getHitBlock();
            if (!"SELL".equals(this.shelfType(block))) continue;
            Block barrel = this.barrelUnder(block);
            if (barrel == null || !(barrel.getState() instanceof Barrel state)) continue;
            this.refreshShelfDisplay(block);
            int section = this.sectionAt(player, block);
            if (section < 0) continue;
            int stock = sectionCount(state.getInventory(), section);
            int price = this.priceOf(block);
            String text = stock > 0
               ? "販売中: 在庫 " + stock + " / " + price + " MP"
               : "売り切れ";
            player.sendActionBar(Component.text(text, stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
         }
      }, 20L, 10L);
   }

   /** Total item count inside the barrel. */
   static int stockOf(Block barrel) {
      if (!(barrel.getState() instanceof Barrel state)) return 0;
      int total = 0;
      for (ItemStack item : state.getInventory().getContents()) {
         if (item != null && !item.getType().isAir()) total += item.getAmount();
      }
      return total;
   }
}
