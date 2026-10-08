package org.server.mifron;

import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Click/close/quit handling for the shared trade UI. */
final class TradeFeature implements Listener {
   private final Mifron plugin;

   TradeFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   private TradeService service() {
      return this.plugin.tradeService;
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player)) return;
      if (this.service().sessionOf(event.getInventory()) != null) event.setCancelled(true);
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      this.service().onViewClosed(event.getInventory());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent event) {
      this.service().onQuit(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (TradeService.SELECT_TITLE.equals(event.getView().getTitle())) {
         event.setCancelled(true);
         if (event.getRawSlot() >= 0 && event.getInventory().equals(event.getClickedInventory())) {
            this.service().onPlayerSelectClick(player, event.getCurrentItem());
         }
         return;
      }
      TradeSession session = this.service().sessionOf(event.getInventory());
      if (session == null || session.state != TradeSession.State.OPEN) return;
      // Whitelist only: plain left/right on own areas, shift-left moves, buttons.
      event.setCancelled(true);
      UUID uuid = player.getUniqueId();
      boolean ownA = session.isA(uuid);
      if (!session.involves(uuid)) return;
      int slot = event.getRawSlot();
      boolean top = slot >= 0 && slot < TradeRules.SIZE && event.getInventory().equals(event.getClickedInventory());

      if (top && slot == TradeRules.CANCEL) {
         this.service().cancel(player, "§c取引を中止しました。提示を返却します。");
         return;
      }
      if (top && slot == (ownA ? TradeRules.A_CONFIRM : TradeRules.B_CONFIRM)) {
         ClickType click = event.getClick();
         if (click.isLeftClick() || click.isRightClick()) this.service().toggleConfirm(player);
         return;
      }

      int[] ownSlots = ownA ? TradeRules.A_OFFERS : TradeRules.B_OFFERS;
      boolean ownSlot = false;
      for (int s : ownSlots) if (s == slot && top) ownSlot = true;

      ClickType click = event.getClick();
      if (ownSlot && (click.isLeftClick() || click.isRightClick())) {
         this.handleOfferClick(session, slot, event.getCursor(), player, event);
         return;
      }
      if (click == ClickType.SHIFT_LEFT && event.getCursor() != null && event.getCursor().getType().isAir()) {
         if (top && ownSlot) this.shiftOut(session, slot, player);
         else if (!top && event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            this.shiftIn(session, ownA, event.getCurrentItem(), player, event);
         }
      }
   }

   private void handleOfferClick(TradeSession session, int slot, ItemStack cursor, Player player, InventoryClickEvent event) {
      Inventory view = session.view;
      ItemStack current = view.getItem(slot);
      boolean cursorEmpty = cursor == null || cursor.getType().isAir();
      boolean slotEmpty = current == null || current.getType().isAir();
      if (cursorEmpty && !slotEmpty) {
         player.setItemOnCursor(current);
         view.setItem(slot, null);
         this.service().onOfferChanged(session);
      } else if (!cursorEmpty && slotEmpty) {
         view.setItem(slot, cursor);
         player.setItemOnCursor(null);
         this.service().onOfferChanged(session);
      }
      // Swap and all other combinations stay cancelled (no partial merges).
   }

   private void shiftOut(TradeSession session, int slot, Player player) {
      ItemStack current = session.view.getItem(slot);
      if (current == null || current.getType().isAir()) return;
      Map<Integer, ItemStack> leftover = player.getInventory().addItem(current);
      if (leftover.isEmpty()) session.view.setItem(slot, null);
      else {
         ItemStack rest = leftover.values().iterator().next();
         session.view.setItem(slot, rest.getAmount() <= 0 ? null : rest);
      }
      this.service().onOfferChanged(session);
   }

   private void shiftIn(TradeSession session, boolean ownA, ItemStack current, Player player, InventoryClickEvent event) {
      if (current == null || current.getType().isAir()) return;
      int[] ownSlots = ownA ? TradeRules.A_OFFERS : TradeRules.B_OFFERS;
      for (int slot : ownSlots) {
         ItemStack existing = session.view.getItem(slot);
         if (existing == null || existing.getType().isAir()) {
            session.view.setItem(slot, current);
            event.getClickedInventory().setItem(event.getSlot(), null);
            this.service().onOfferChanged(session);
            return;
         }
      }
      player.sendMessage("§c提示枠がいっぱいです。");
   }
}
