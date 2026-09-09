package org.server.mifron;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Shelf;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

final class ShopBlockStore {
   final ShopKeys keys;
   private final Mifron plugin;

   ShopBlockStore(Mifron plugin) {
      this.plugin = plugin;
      this.keys = new ShopKeys(plugin);
   }

   PersistentDataContainer data(Block block) {
      return block.getState() instanceof TileState state ? state.getPersistentDataContainer() : null;
   }

   boolean isShop(Block block) {
      PersistentDataContainer pdc = this.data(block);
      return pdc != null && pdc.has(this.keys.shopType, PersistentDataType.STRING);
   }

   String shopType(Block block) {
      PersistentDataContainer pdc = this.data(block);
      return pdc == null ? "" : pdc.getOrDefault(this.keys.shopType, PersistentDataType.STRING, "");
   }

   boolean isOwnerOrAdmin(Player player, Block block) {
      if (player.hasPermission("mifron.admin") || player.hasPermission("mifron.shop.admin")) {
         return true;
      }
      PersistentDataContainer pdc = this.data(block);
      String owner = pdc == null ? null : pdc.get(this.keys.owner, PersistentDataType.STRING);
      return owner != null && owner.equals(player.getUniqueId().toString());
   }

   boolean tagNewShop(Player player, Block block, String shopType) {
      if (!(block.getState() instanceof TileState state)) {
         return false;
      }
      PersistentDataContainer pdc = state.getPersistentDataContainer();
      pdc.set(this.keys.shopType, PersistentDataType.STRING, shopType);
      pdc.set(this.keys.owner, PersistentDataType.STRING, player.getUniqueId().toString());
      pdc.set(this.keys.placed, PersistentDataType.LONG, System.currentTimeMillis());
      pdc.set(this.keys.activity, PersistentDataType.LONG, System.currentTimeMillis());
      for (var key : this.keys.prices) {
         pdc.set(key, PersistentDataType.INTEGER, 10);
      }
      return state.update(true, false);
   }

   ItemStack displayedItem(Block block, int slot) {
      if (!(block.getState() instanceof Shelf shelf)) {
         return null;
      }
      ItemStack[] contents = shelf.getSnapshotInventory().getContents();
      if (slot >= 0 && slot < contents.length && contents[slot] != null && contents[slot].getType() != Material.AIR) {
         return contents[slot];
      }
      return null;
   }

   int selectedSlot(Player player, Block shelf) {
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
      return Math.max(0, Math.min(size - 1, (int) Math.floor(local * size)));
   }

   int slotPrice(Block block, int slot) {
      PersistentDataContainer pdc = this.data(block);
      if (pdc == null) {
         return 10;
      }
      int index = Math.max(0, Math.min(this.keys.prices.length - 1, slot));
      return Math.max(1, pdc.getOrDefault(this.keys.prices[index], PersistentDataType.INTEGER, 10));
   }

   void setSlotPrice(Block block, int slot, int price) {
      if (!(block.getState() instanceof TileState state)) {
         return;
      }
      int index = Math.max(0, Math.min(this.keys.prices.length - 1, slot));
      state.getPersistentDataContainer().set(this.keys.prices[index], PersistentDataType.INTEGER, Math.max(1, price));
      state.update(true, false);
   }

   void touch(Block block) {
      if (!(block.getState() instanceof TileState state)) {
         return;
      }
      state.getPersistentDataContainer().set(this.keys.activity, PersistentDataType.LONG, System.currentTimeMillis());
      state.update(true, false);
   }

   boolean isInactive(Block block) {
      PersistentDataContainer pdc = this.data(block);
      if (pdc == null) {
         return false;
      }
      long last = pdc.getOrDefault(this.keys.activity, PersistentDataType.LONG, 0L);
      long days = Math.max(1L, this.plugin.getConfig().getLong("shops.inactivity-days", 30L));
      return last > 0L && System.currentTimeMillis() - last > days * 86400000L;
   }
}
