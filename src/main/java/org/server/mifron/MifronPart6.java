package org.server.mifron;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.shelf.Shelf;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

abstract class MifronPart6 extends MifronPart5x3 {
   protected void syncShelfShopDisplays() {
      ConfigurationSection worlds = this.data.getConfigurationSection("shelf-shops");
      if (worlds == null) return;
      int synced = 0;
      for (String worldId : worlds.getKeys(false)) {
         World world = this.worldFromId(worldId);
         if (world == null) continue;
         ConfigurationSection offers = worlds.getConfigurationSection(worldId);
         if (offers == null) continue;
         for (String coordinates : offers.getKeys(false)) {
            Block block = this.blockFromCoordinates(world, coordinates);
            if (block == null || !this.isShelfShop(block)) continue;
            List<Material> materials = this.shelfShopRandomOffers(block);
            if (materials.isEmpty()) this.clearShelfShopDisplay(block);
            else if (this.displayShelfShopOffers(block, materials)) synced++;
         }
      }
      if (synced > 0) this.getLogger().info("Synced " + synced + " shelf shop display items.");
   }

   protected World worldFromId(String worldId) {
      try { return Bukkit.getWorld(UUID.fromString(worldId)); }
      catch (IllegalArgumentException e) { return null; }
   }

   protected Block blockFromCoordinates(World world, String coordinates) {
      String[] parts = coordinates.split("_", 3);
      if (parts.length != 3) return null;
      try { return world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
      catch (NumberFormatException e) { return null; }
   }

   protected boolean displayShelfShopOffers(Block block, List<Material> materials) {
      if (block == null || materials == null || !(block.getState() instanceof Shelf shelfState)) return false;
      Inventory inventory = shelfState.getInventory();
      inventory.clear();
      int slots = Math.min(SHELF_SHOP_OFFER_SLOTS, Math.min(inventory.getSize(), materials.size()));
      for (int slot = 0; slot < slots; slot++) {
         Material material = materials.get(slot);
         if (material != null && material != Material.AIR) inventory.setItem(slot, new ItemStack(material, 1));
      }
      return true;
   }

   protected void clearShelfShopDisplay(Block block) {
      if (block != null && block.getState() instanceof Shelf shelfState) shelfState.getInventory().clear();
   }

   protected String shelfShopOfferPath(Block block) {
      return "shelf-shop-offers." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   protected int selectedShelfSlot(Player player, Block shelf) {
      if (player == null || shelf == null) return 0;
      RayTraceResult result = player.rayTraceBlocks(5.0);
      if (result == null || result.getHitBlock() == null || !result.getHitBlock().equals(shelf)) return 0;
      double local = this.shelfSlotAxisPosition(shelf, result.getHitPosition().getX(), result.getHitPosition().getZ());
      if (local < 0.3333333333333333) return 2;
      return local < 0.6666666666666666 ? 1 : 0;
   }

   protected double shelfSlotAxisPosition(Block shelf, double hitX, double hitZ) {
      BlockFace facing = this.shelfFacing(shelf);
      double local = switch (facing) {
         case NORTH -> hitX - shelf.getX();
         case SOUTH -> 1.0 - (hitX - shelf.getX());
         case EAST -> hitZ - shelf.getZ();
         case WEST -> 1.0 - (hitZ - shelf.getZ());
         default -> hitX - shelf.getX();
      };
      return Math.max(0.0, Math.min(0.999999, local));
   }

   protected BlockFace shelfFacing(Block shelf) {
      return shelf.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.NORTH;
   }

   protected Material shelfShopMaterial(Block shelf, int selectedSlot) {
      if (shelf.getState() instanceof Shelf shelfState) {
         ItemStack[] contents = shelfState.getSnapshotInventory().getContents();
         if (selectedSlot >= 0 && selectedSlot < contents.length) {
            ItemStack selected = contents[selectedSlot];
            if (selected != null && selected.getType() != Material.AIR) return selected.getType();
         }
         for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) return item.getType();
         }
      }
      Location center = shelf.getLocation().add(0.5, 0.5, 0.5);
      return shelf.getWorld().getNearbyEntities(center, 1.25, 1.25, 1.25).stream()
         .filter(ItemFrame.class::isInstance).map(ItemFrame.class::cast).map(ItemFrame::getItem)
         .filter(itemx -> itemx != null && itemx.getType() != Material.AIR).map(ItemStack::getType).findFirst().orElse(null);
   }

   protected boolean isShelf(Material material) {
      String name = material.name();
      return name.endsWith("_SHELF") || name.equals("CHISELED_BOOKSHELF");
   }

   boolean isShelfShop(Block block) {
      if (block == null) return false;
      if (this.slotMachineManager != null && this.slotMachineManager.isMachine(block)) return false;
      String path = this.shelfShopPath(block);
      return this.data.getBoolean(path, false) || this.data.getBoolean(path + ".enabled", false);
   }

   boolean setShelfShop(Block block, boolean enabled) {
      String path = this.shelfShopPath(block);
      boolean existed = this.isShelfShop(block);
      if (enabled) { this.configureSequentialShelfShop(block); return existed; }
      this.data.set(path, null);
      this.clearShelfShopDisplay(block);
      this.clearShopOwner(block);
      this.clearShelfShopRandomOffer(block);
      this.data.set(this.shopLastActivityPath(block), null);
      this.renumberSequentialShelfShops();
      return existed;
   }

   protected String shelfShopPath(Block block) {
      return "shelf-shops." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }

   protected boolean canCreateShop(Player player) {
      return player.hasPermission("mifron.shop.admin") || player.hasPermission("mifron.admin");
   }

   protected boolean canManageShop(Player player, Block block) {
      if (this.canCreateShop(player)) return true;
      return this.data.getString(this.shopOwnerPath(block), "").equals(player.getUniqueId().toString());
   }

   protected void setShopOwner(Block block, UUID owner) {
      this.data.set(this.shopOwnerPath(block), owner.toString());
      this.queueDataSave();
   }

   protected void clearShopOwner(Block block) { this.data.set(this.shopOwnerPath(block), null); }

   protected String shopOwnerPath(Block block) {
      return "shop-owners." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }
}
