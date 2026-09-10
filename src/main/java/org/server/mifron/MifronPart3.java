package org.server.mifron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Shelf;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

abstract class MifronPart3 extends MifronPart2x2 {
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onWorldEditPasteCommand(PlayerCommandPreprocessEvent event) {
      String command = event.getMessage().trim().toLowerCase(Locale.ROOT);
      if (!command.equals("//paste") && !command.startsWith("//paste ") && !command.equals("/worldedit:paste") && !command.startsWith("/worldedit:paste ")) return;
      Player player = event.getPlayer();
      if (!this.mifron().canCreateShop(player)) return;
      ShopPasteSnapshot before = this.snapshotWorldEditShops(player.getWorld());
      Bukkit.getScheduler().runTaskLater(this, () -> this.registerPastedShops(player, before), 2L);
   }

   protected ShopPasteSnapshot snapshotWorldEditShops(World world) {
      Map<String, String> unregisteredShelves = new HashMap<>();
      Set<String> registeredShelfSignatures = new HashSet<>();
      Map<String, String> unregisteredBarrels = new HashMap<>();
      Set<String> registeredBarrelSignatures = new HashSet<>();
      List<Material> catalog = this.mifron().shelfShopCatalogMaterials();
      for (int start = 0; start < catalog.size(); start += SHELF_SHOP_OFFER_SLOTS) {
         registeredShelfSignatures.add(this.shelfOfferSignature(catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS))));
      }
      for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
         for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Shelf shelf) {
               Block block = shelf.getBlock();
               String signature = this.shelfInventorySignature(shelf);
               if (this.mifron().isShelfShop(block)) { if (!this.isEmptyShelfSignature(signature)) registeredShelfSignatures.add(signature); }
               else if (!this.slotMachineManager.isMachine(block)) unregisteredShelves.put(this.shopPositionKey(block), signature);
            } else if (state instanceof Barrel barrel) {
               Block block = barrel.getBlock();
               String signature = this.mifron().barrelInventorySignature(barrel);
               if (this.mifron().isBarrelShop(block)) { if (this.mifron().isBarrelOfferSignature(signature)) registeredBarrelSignatures.add(signature); }
               else unregisteredBarrels.put(this.shopPositionKey(block), signature);
            }
         }
      }
      return new ShopPasteSnapshot(world, Map.copyOf(unregisteredShelves), Set.copyOf(registeredShelfSignatures), Map.copyOf(unregisteredBarrels), Set.copyOf(registeredBarrelSignatures));
   }

   protected void registerPastedShops(Player player, ShopPasteSnapshot before) {
      World world = before.world();
      if (world == null) return;
      List<Block> pastedShelves = new ArrayList<>();
      List<Block> pastedBarrels = new ArrayList<>();
      for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
         for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Shelf shelf) {
               Block block = shelf.getBlock();
               if (this.mifron().isShelfShop(block) || this.slotMachineManager.isMachine(block)) continue;
               String signature = this.shelfInventorySignature(shelf);
               String previous = before.unregisteredShelfSignatures().get(this.shopPositionKey(block));
               if (!Objects.equals(previous, signature) && !this.isEmptyShelfSignature(signature) && before.registeredShelfSignatures().contains(signature)) pastedShelves.add(block);
            } else if (state instanceof Barrel barrel) {
               Block block = barrel.getBlock();
               if (this.mifron().isBarrelShop(block)) continue;
               String signature = this.mifron().barrelInventorySignature(barrel);
               String previous = before.unregisteredBarrelSignatures().get(this.shopPositionKey(block));
               if (!Objects.equals(previous, signature) && this.mifron().isBarrelOfferSignature(signature) && before.registeredBarrelSignatures().contains(signature)) pastedBarrels.add(block);
            }
         }
      }
      if (pastedShelves.isEmpty() && pastedBarrels.isEmpty()) return;
      BlockFace facing = player.getFacing();
      pastedShelves.sort((a, b) -> this.comparePastedShelves(a, b, facing));
      for (Block block : pastedShelves) { this.mifron().configureSequentialShelfShop(block); this.mifron().setShopOwner(block, player.getUniqueId()); }
      for (Block block : pastedBarrels) { this.mifron().setBarrelShopMeta(block); this.mifron().setBarrelShop(block, true); this.mifron().setShopOwner(block, player.getUniqueId()); }
      this.queueDataSave();
      if (player.isOnline()) player.sendMessage("\u00a7aWorldEdit\u3067\u8cbc\u308a\u4ed8\u3051\u305f\u30b7\u30e7\u30c3\u30d7\u3092\u81ea\u52d5\u767b\u9332\u3057\u307e\u3057\u305f\u3002");
   }

   protected int comparePastedShelves(Block a, Block b, BlockFace facing) {
      int row = Integer.compare(b.getY(), a.getY());
      if (row != 0) return row;
      int horizontal = switch (facing) {
         case SOUTH -> Integer.compare(b.getX(), a.getX());
         case EAST -> Integer.compare(a.getZ(), b.getZ());
         case WEST -> Integer.compare(b.getZ(), a.getZ());
         default -> Integer.compare(a.getX(), b.getX());
      };
      if (horizontal != 0) return horizontal;
      return this.shopPositionKey(a).compareTo(this.shopPositionKey(b));
   }

   protected String shelfInventorySignature(Shelf shelf) {
      return java.util.Arrays.stream(shelf.getSnapshotInventory().getContents())
         .map(item -> item == null || item.getType() == Material.AIR ? "AIR" : item.getType().name())
         .collect(Collectors.joining(","));
   }

   protected String shelfOfferSignature(List<Material> materials) {
      List<String> slots = new ArrayList<>();
      for (int slot = 0; slot < SHELF_SHOP_OFFER_SLOTS; slot++) {
         Material material = slot < materials.size() ? materials.get(slot) : Material.AIR;
         slots.add(material == null || material == Material.AIR ? "AIR" : material.name());
      }
      return String.join(",", slots);
   }

   protected boolean isEmptyShelfSignature(String signature) {
      return signature == null || signature.isBlank() || java.util.Arrays.stream(signature.split(",", -1)).allMatch("AIR"::equals);
   }

   protected String shopPositionKey(Block block) {
      return block.getX() + "_" + block.getY() + "_" + block.getZ();
   }
}
