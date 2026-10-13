package org.server.mifron;

import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart3x1 extends MifronPart3 {
   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
      Player player = event.getPlayer();
      this.trackFirstAction(player);
      if (this.compassFeature.handleCompassClick(event)) return;
      if (this.isShopWand(item)) { this.mifron().handleShopWandClick(event); return; }
      if (this.isMifronItem(item, "jump_pad_wand")) { this.mifron().handleJumpPadWandClick(event); return; }
      if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.mifron().isShelf(event.getClickedBlock().getType()) && this.mifron().isShelfShop(event.getClickedBlock())) {
         // Never transact on an already-cancelled interact (protection etc.):
         // with ignoreCancelled=false this handler would otherwise charge twice
         // when another listener also processes the click.
         if (event.isCancelled()) return;
         event.setCancelled(true);
         long now = System.currentTimeMillis();
         if (now < this.shelfShopTransactionUntil.getOrDefault(player.getUniqueId(), 0L)) return;
         this.shelfShopTransactionUntil.put(player.getUniqueId(), now + 90L);
         if (this.isMifronItem(item, "emerald_bundle")) this.mifron().tryShopPayment(player, event.getClickedBlock());
         else this.mifron().tryShopSell(player, event.getClickedBlock(), item);
         return;
      }
   }

   @EventHandler
   public void onPickupItem(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player)) return;
      ItemStack stack = event.getItem().getItemStack();
      this.mifron().tryConvertProposedItem(event, stack);
      this.mifron().recordAcquiredItem(player, stack.getType());
   }
}
