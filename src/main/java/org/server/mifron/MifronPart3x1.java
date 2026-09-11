package org.server.mifron;

import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart3x1 extends MifronPart3 {
   protected String barrelInventorySignature(Barrel barrel) {
      return java.util.Arrays.stream(barrel.getInventory().getContents()).map(item -> {
         if (item == null || item.getType() == Material.AIR) return "AIR";
         ItemMeta meta = item.getItemMeta();
         Integer price = MifronPdc.get(meta.getPersistentDataContainer(), this.barrelOfferPriceKey, PersistentDataType.INTEGER);
         String rarity = MifronPdc.get(meta.getPersistentDataContainer(), this.barrelOfferRarityKey, PersistentDataType.STRING);
         if (price == null || price <= 0 || rarity == null || rarity.isBlank()) return "INVALID:" + item.getType().name();
         int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
         return "OFFER:" + item.getType().name() + ":" + item.getAmount() + ":" + price + ":" + rarity + ":" + damage;
      }).collect(Collectors.joining("|"));
   }

   protected boolean isBarrelOfferSignature(String signature) {
      if (signature == null || signature.isBlank()) return false;
      boolean hasOffer = false;
      for (String slot : signature.split("\\|", -1)) {
         if (slot.startsWith("OFFER:")) hasOffer = true;
         else if (!"AIR".equals(slot)) return false;
      }
      return hasOffer;
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND && !this.serverPortalFeature.isServerWand(item)) return;
      Player player = event.getPlayer();
      this.trackFirstAction(player);
      if (this.compassFeature.handleCompassClick(event)) return;
      if (this.isShopWand(item)) { this.mifron().handleShopWandClick(event); return; }
      if (this.isMifronItem(item, "jump_pad_wand")) { this.mifron().handleJumpPadWandClick(event); return; }
      if (this.serverPortalFeature.isServerWand(item)) { this.serverPortalFeature.handleWandClick(event); return; }
      if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.mifron().isShelf(event.getClickedBlock().getType()) && this.mifron().isShelfShop(event.getClickedBlock()) && !this.slotMachineManager.isMachine(event.getClickedBlock())) {
         event.setCancelled(true);
         long now = System.currentTimeMillis();
         if (now < this.shelfShopTransactionUntil.getOrDefault(player.getUniqueId(), 0L)) return;
         this.shelfShopTransactionUntil.put(player.getUniqueId(), now + 90L);
         if (this.isMifronItem(item, "emerald_bundle")) this.mifron().tryShopPayment(player, event.getClickedBlock());
         else this.mifron().tryShopSell(player, event.getClickedBlock(), item);
         return;
      }
      if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.mifron().isBarrelShop(event.getClickedBlock()) && event.getClickedBlock().getState() instanceof Barrel barrel) {
         player.openInventory(barrel.getInventory());
         event.setCancelled(true);
         return;
      }
      if (item == null) return;
      if (this.isMifronItem(item, "emerald_bundle")) {
         event.setCancelled(true);
         if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.mifron().tryShopPayment(player, event.getClickedBlock())) return;
         player.sendMessage("\u00a7a\u6240\u6301MP: " + this.mifron().formatNumber(this.mifron().getEmeralds(player.getUniqueId())));
         return;
      }
      if (this.isMifronItem(item, "friend_book") && event.getAction().isRightClick()) {
         event.setCancelled(true);
         this.mifron().openFriendUi(player);
         this.restoreUtilityItem(player);
      } else if (this.isMifronItem(item, "quest_book") && event.getAction().isRightClick()) {
         event.setCancelled(true);
         this.mifron().openQuestUi(player, "categories");
         this.restoreUtilityItem(player);
      } else if (this.isReincarnationStar(item) && event.getAction().isRightClick()) {
         event.setCancelled(true);
         this.mifron().tryReincarnate(player, item);
      } else if (this.isMifronItem(item, "teleporter") && event.getAction().isRightClick()) {
         event.setCancelled(true);
      }
   }

   private void restoreUtilityItem(Player player) {
      org.bukkit.Bukkit.getScheduler().runTask(this, () -> this.mifron().giveInitialItems(player));
   }

   @EventHandler
   public void onPickupItem(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player)) return;
      ItemStack stack = event.getItem().getItemStack();
      this.mifron().tryConvertProposedItem(event, stack);
      this.mifron().recordAcquiredItem(player, stack.getType());
   }
}
