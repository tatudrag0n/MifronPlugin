package org.server.mifron;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Player-to-player secure trades. MP moves only at dual-confirm execution;
 * items are returned to their owners on any other exit path.
 */
public final class TradeService {
   private final Mifron plugin;
   private final Map<String, TradeSession> sessions = new HashMap<>();
   private final Map<Inventory, TradeSession> views = new HashMap<>();

   public TradeService(Mifron plugin) {
      this.plugin = plugin;
   }

   public int maxAmount() {
      return Math.max(1, this.plugin.getConfig().getInt("trade.max-amount", 1000000));
   }

   public TradeSession sessionOf(UUID uuid) {
      if (uuid == null) return null;
      for (TradeSession session : this.sessions.values()) {
         if ((session.state == TradeSession.State.REQUESTED || session.state == TradeSession.State.OPEN) && session.involves(uuid)) {
            return session;
         }
      }
      return null;
   }

   public TradeSession sessionOf(Inventory view) {
      return view == null ? null : this.views.get(view);
   }

   private Player player(UUID uuid) {
      return uuid == null ? null : Bukkit.getPlayer(uuid);
   }

   private String name(UUID uuid) {
      Player online = this.player(uuid);
      return online == null ? uuid.toString().substring(0, 8) : online.getName();
   }

   /** /trade <player>: request a trade. */
   public void request(Player from, Player to) {
      if (from == null || to == null) return;
      if (from.equals(to)) {
         from.sendMessage("§c自分とは取引できません。");
         return;
      }
      if (this.sessionOf(from.getUniqueId()) != null) {
         from.sendMessage("§c進行中の取引があります。/trade cancel で中止してください。");
         return;
      }
      if (this.sessionOf(to.getUniqueId()) != null) {
         from.sendMessage("§c相手は取引中です。");
         return;
      }
      TradeSession session = new TradeSession(from.getUniqueId(), to.getUniqueId());
      session.requestedAt = System.currentTimeMillis();
      session.expiresAt = System.currentTimeMillis() + TradeRules.REQUEST_EXPIRY_MILLIS;
      this.sessions.put(TradeRules.sessionKey(from.getUniqueId(), to.getUniqueId()), session);
      from.sendMessage("§a" + to.getName() + "に取引を申し込みました。（60秒有効）");
      to.sendMessage("§e" + from.getName() + "から取引の申込があります。/trade accept で承諾、/trade deny で拒否。");
   }

   /** /trade accept: open the shared trade UI. */
   public void accept(Player player) {
      TradeSession session = this.sessionOf(player == null ? null : player.getUniqueId());
      if (session == null || session.state != TradeSession.State.REQUESTED) {
         if (player != null) player.sendMessage("§c承諾できる申込がありません。");
         return;
      }
      if (session.isExpired(System.currentTimeMillis())) {
         this.finish(session, TradeSession.State.CANCELLED, "§c申込の期限が切れました。");
         return;
      }
      if (session.b.equals(player.getUniqueId()) || session.a.equals(player.getUniqueId())) {
         Player a = this.player(session.a);
         Player b = this.player(session.b);
         if (a == null || b == null) {
            this.finish(session, TradeSession.State.CANCELLED, "§c相手がオフラインのため中止しました。");
            return;
         }
         session.state = TradeSession.State.OPEN;
         session.expiresAt = System.currentTimeMillis() + TradeRules.SESSION_TIMEOUT_MILLIS;
         session.view = this.createView(session);
         this.views.put(session.view, session);
         a.openInventory(session.view);
         b.openInventory(session.view);
         a.sendMessage("§a取引を開始しました。提示を変更すると双方の確定がリセットされます。");
         b.sendMessage("§a取引を開始しました。提示を変更すると双方の確定がリセットされます。");
      }
   }

   /** /trade deny: refuse a request. */
   public void deny(Player player) {
      TradeSession session = this.sessionOf(player == null ? null : player.getUniqueId());
      if (session == null || session.state != TradeSession.State.REQUESTED) {
         if (player != null) player.sendMessage("§c拒否できる申込がありません。");
         return;
      }
      this.finish(session, TradeSession.State.CANCELLED, "§c取引は拒否されました。");
   }

   /** /trade cancel: abort and return everything. */
   public void cancel(Player player, String reason) {
      TradeSession session = this.sessionOf(player == null ? null : player.getUniqueId());
      if (session == null) {
         if (player != null) player.sendMessage("§c進行中の取引がありません。");
         return;
      }
      this.finish(session, TradeSession.State.CANCELLED, reason == null ? "§c取引を中止しました。" : reason);
   }

   /** /trade money <amount>: set your MP offer. */
   public void setMoney(Player player, int amount) {
      TradeSession session = this.sessionOf(player == null ? null : player.getUniqueId());
      if (session == null || session.state != TradeSession.State.OPEN) {
         if (player != null) player.sendMessage("§c取引中にのみ金額を設定できます。");
         return;
      }
      if (amount < 0 || (amount > 0 && !TradeRules.isValidMoneyOffer(amount, this.maxAmount()))) {
         player.sendMessage("§c金額は0〜" + this.plugin.formatNumber(this.maxAmount()) + "MPの範囲で指定してください。");
         return;
      }
      if (session.isA(player.getUniqueId())) session.moneyA = amount;
      else session.moneyB = amount;
      session.resetConfirms();
      this.refresh(session);
      player.sendMessage("§a提示MPを" + this.plugin.formatNumber(amount) + "MPに設定しました。");
   }

   /** Toggle your confirmation. Executes when both sides confirm. */
   public void toggleConfirm(Player player) {
      TradeSession session = this.sessionOf(player == null ? null : player.getUniqueId());
      if (session == null || session.state != TradeSession.State.OPEN || session.view == null) return;
      if (session.isA(player.getUniqueId())) session.confirmedA = !session.confirmedA;
      else session.confirmedB = !session.confirmedB;
      if (session.bothConfirmed()) this.execute(session);
      else this.refresh(session);
   }

   /** Called by the UI when an offer changes: reset confirms and repaint. */
   public void onOfferChanged(TradeSession session) {
      if (session == null || session.state != TradeSession.State.OPEN) return;
      session.resetConfirms();
      this.refresh(session);
   }

   /** Inventory was closed without execution: return items. */
   public void onViewClosed(Inventory view) {
      TradeSession session = this.sessionOf(view);
      if (session == null || session.state != TradeSession.State.OPEN) return;
      this.finish(session, TradeSession.State.CANCELLED, "§c取引画面が閉じられたため中止し、提示を返却しました。");
   }

   /** A participant quit: return items. */
   public void onQuit(UUID uuid) {
      TradeSession session = this.sessionOf(uuid);
      if (session == null) return;
      this.finish(session, TradeSession.State.CANCELLED, "§c参加者が退出したため中止し、提示を返却しました。");
   }

   private void execute(TradeSession session) {
      Player a = this.player(session.a);
      Player b = this.player(session.b);
      if (a == null || b == null) {
         this.finish(session, TradeSession.State.CANCELLED, "§c相手がオフラインのため中止しました。");
         return;
      }
      int balanceA = this.plugin.getEmeralds(session.a);
      int balanceB = this.plugin.getEmeralds(session.b);
      if (!EconomyManager.canTransferExact(balanceA, balanceB, session.moneyA)
         || !EconomyManager.canTransferExact(balanceB, balanceA, session.moneyB)) {
         session.resetConfirms();
         this.refresh(session);
         a.sendMessage("§cMP残高が不足しているため成立しませんでした。");
         b.sendMessage("§cMP残高が不足しているため成立しませんでした。");
         return;
      }
      // Move MP first so any failure below still leaves consistent balances.
      if (session.moneyA > 0) this.plugin.economyManager.transfer(session.a, session.b, session.moneyA);
      if (session.moneyB > 0) this.plugin.economyManager.transfer(session.b, session.a, session.moneyB);
      this.moveOffers(session, true);
      this.moveOffers(session, false);
      session.state = TradeSession.State.DONE;
      this.close(session);
      String summary = "§a取引成立！ (提示MP: " + this.plugin.formatNumber(session.moneyA) + " ↔ " + this.plugin.formatNumber(session.moneyB) + ")";
      a.sendMessage(summary);
      b.sendMessage(summary);
      this.plugin.queueDataSave();
   }

   /** Moves A's offered items to B (first=true) or B's to A. */
   private void moveOffers(TradeSession session, boolean aToB) {
      if (session.view == null) return;
      Player receiver = this.player(aToB ? session.b : session.a);
      int[] slots = aToB ? TradeRules.A_OFFERS : TradeRules.B_OFFERS;
      for (int slot : slots) {
         ItemStack stack = session.view.getItem(slot);
         if (stack == null || stack.getType().isAir()) continue;
         session.view.setItem(slot, null);
         if (receiver != null) {
            Map<Integer, ItemStack> leftover = receiver.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
               receiver.getWorld().dropItemNaturally(receiver.getLocation(), drop);
            }
         }
      }
   }

   private void finish(TradeSession session, TradeSession.State end, String message) {
      // Return offered items to their owners before closing.
      if (session.view != null) {
         this.returnOffers(session, true);
         this.returnOffers(session, false);
      }
      session.state = end;
      this.close(session);
      Player a = this.player(session.a);
      Player b = this.player(session.b);
      if (message != null) {
         if (a != null) a.sendMessage(message);
         if (b != null) b.sendMessage(message);
      }
   }

   private void returnOffers(TradeSession session, boolean ownA) {
      Player owner = this.player(ownA ? session.a : session.b);
      int[] slots = ownA ? TradeRules.A_OFFERS : TradeRules.B_OFFERS;
      for (int slot : slots) {
         ItemStack stack = session.view.getItem(slot);
         if (stack == null || stack.getType().isAir()) continue;
         session.view.setItem(slot, null);
         if (owner != null) {
            Map<Integer, ItemStack> leftover = owner.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
               owner.getWorld().dropItemNaturally(owner.getLocation(), drop);
            }
         }
      }
   }

   private void close(TradeSession session) {
      this.sessions.remove(TradeRules.sessionKey(session.a, session.b));
      if (session.view != null) {
         this.views.remove(session.view);
         Player a = this.player(session.a);
         Player b = this.player(session.b);
         // Closing triggers onViewClosed; session is already removed so it no-ops.
         if (a != null && a.getOpenInventory().getTopInventory().equals(session.view)) a.closeInventory();
         if (b != null && b.getOpenInventory().getTopInventory().equals(session.view)) b.closeInventory();
         session.view = null;
      }
   }

   private Inventory createView(TradeSession session) {
      Inventory view = Bukkit.createInventory(null, TradeRules.SIZE,
         Component.text("§6取引: " + this.name(session.a) + " ↔ " + this.name(session.b)));
      this.refreshInto(session, view);
      return view;
   }

   void refresh(TradeSession session) {
      if (session == null || session.view == null) return;
      this.refreshInto(session, session.view);
   }

   private void refreshInto(TradeSession session, Inventory view) {
      ItemStack divider = this.plugin.named(Material.GRAY_STAINED_GLASS_PANE, " ", java.util.List.of());
      for (int slot : TradeRules.DIVIDER) {
         if (view.getItem(slot) == null || view.getItem(slot).getType() == Material.GRAY_STAINED_GLASS_PANE) {
            view.setItem(slot, divider);
         }
      }
      for (int slot = 36; slot < TradeRules.SIZE; slot++) {
         if (slot == TradeRules.A_MONEY || slot == TradeRules.B_MONEY
            || slot == TradeRules.A_CONFIRM || slot == TradeRules.B_CONFIRM
            || slot == TradeRules.STATUS || slot == TradeRules.CANCEL) continue;
         if (view.getItem(slot) == null) view.setItem(slot, this.plugin.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", java.util.List.of()));
      }
      view.setItem(TradeRules.A_MONEY, this.moneyIcon(session, true));
      view.setItem(TradeRules.B_MONEY, this.moneyIcon(session, false));
      view.setItem(TradeRules.A_CONFIRM, this.confirmIcon(session, true));
      view.setItem(TradeRules.B_CONFIRM, this.confirmIcon(session, false));
      view.setItem(TradeRules.STATUS, this.statusIcon(session));
      view.setItem(TradeRules.CANCEL, this.plugin.actionItem(Material.BARRIER, ChatColor.RED + "中止",
         java.util.List.of(ChatColor.GRAY + "クリック: 取引を中止"), "trade_cancel", null));
   }

   private ItemStack moneyIcon(TradeSession session, boolean forA) {
      int amount = forA ? session.moneyA : session.moneyB;
      String who = this.name(forA ? session.a : session.b);
      return this.plugin.actionItem(Material.EMERALD, ChatColor.GREEN + who + " の提示MP",
         java.util.List.of(ChatColor.GOLD + this.plugin.formatNumber(amount) + " MP",
            ChatColor.GRAY + "/trade money <額> で変更"), "trade_money", null);
   }

   private ItemStack confirmIcon(TradeSession session, boolean forA) {
      boolean confirmed = forA ? session.confirmedA : session.confirmedB;
      String who = this.name(forA ? session.a : session.b);
      Material icon = confirmed ? Material.LIME_WOOL : Material.RED_WOOL;
      ChatColor color = confirmed ? ChatColor.GREEN : ChatColor.RED;
      return this.plugin.named(icon, color + who + (confirmed ? " 確定済み" : " 未確定"),
         java.util.List.of(ChatColor.GRAY + "クリック: " + (confirmed ? "確定を取り消す" : "確定する")));
   }

   private ItemStack statusIcon(TradeSession session) {
      boolean ready = session.bothConfirmed();
      return this.plugin.named(ready ? Material.TOTEM_OF_UNDYING : Material.CLOCK,
         (ready ? ChatColor.GREEN : ChatColor.YELLOW) + (ready ? "成立！" : "双方の確定待ち"),
         java.util.List.of(ChatColor.GRAY + "提示変更で確定リセット"));
   }
}
