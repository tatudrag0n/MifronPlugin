package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

final class AuctionFeature implements Listener {
   private static final int ESCROW_VERSION = 1;
   private final Mifron plugin;
   private final EconomyPriceTable priceTable;
   private BukkitTask settlementTask;

   AuctionFeature(Mifron plugin, EconomyPriceTable priceTable) {
      this.plugin = plugin;
      this.priceTable = priceTable;
   }

   void start() {
      if (this.settlementTask != null) {
         this.settlementTask.cancel();
      }
      this.settlementTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::settleExpiredAuctions, 20L, 600L);
   }

   void shutdown() {
      if (this.settlementTask != null) {
         this.settlementTask.cancel();
         this.settlementTask = null;
      }
   }

   boolean isAuctionFrame(Entity entity) {
      return entity instanceof ItemFrame && this.plugin.data().contains(this.auctionPath(entity));
   }

   boolean isAuctionInteractionItem(ItemStack item) {
      return this.plugin.isShopWand(item) || this.plugin.isMifronItem(item, "emerald_bundle");
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFrameInteract(PlayerInteractEntityEvent event) {
      if (event.getHand() == EquipmentSlot.HAND && event.getRightClicked() instanceof ItemFrame frame) {
         Player player = event.getPlayer();
         ItemStack item = player.getInventory().getItemInMainHand();
         if (this.plugin.isShopWand(item)) {
            if (this.plugin.isLegacyShopWand(item)) {
               this.createAuction(player, frame);
            } else {
               player.sendMessage(ChatColor.RED + "額縁ショップは未実装です。額縁は既存のオークション専用です。");
            }
            event.setCancelled(true);
         } else if (this.isAuctionFrame(frame)) {
            event.setCancelled(true);
            this.ensureAuctionMetadata(frame);
            if (this.settleIfExpired(frame)) {
               player.sendMessage(ChatColor.YELLOW + "オークションは終了しました。");
            } else if (this.plugin.isMifronItem(item, "emerald_bundle")) {
               this.bid(player, frame, player.isSneaking());
            } else {
               this.showAuctionInfo(player, frame);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFrameDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof ItemFrame frame && event.getDamager() instanceof Player player && this.isAuctionFrame(frame)) {
         event.setCancelled(true);
         this.ensureAuctionMetadata(frame);
         if (this.settleIfExpired(frame)) {
            player.sendMessage(ChatColor.YELLOW + "オークションは終了しました。");
         } else if (this.plugin.isLegacyShopWand(player.getInventory().getItemInMainHand())) {
            this.removeAuction(player, frame);
         } else {
            this.showAuctionInfo(player, frame);
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.deliverPendingItems(event.getPlayer());
      this.deliverPendingMessages(event.getPlayer());
   }

   private void createAuction(Player player, ItemFrame frame) {
      if (this.isAuctionFrame(frame)) {
         this.ensureAuctionMetadata(frame);
         this.showAuctionInfo(player, frame);
      } else {
         ItemStack item = frame.getItem();
         if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "額縁に出品アイテムを入れてください。");
         } else if (!this.priceTable.isAuctionAllowed(item.getType())) {
            player.sendMessage(ChatColor.RED + "このアイテムはオークション出品できません。");
         } else {
            long now = System.currentTimeMillis();
            String path = this.auctionPath(frame);
            this.plugin.data().set(path + ".owner", player.getUniqueId().toString());
            this.plugin.data().set(path + ".item", item.getType().name());
            this.plugin.data().set(path + ".item-stack", item.clone());
            this.plugin.data().set(path + ".created-at", now);
            this.plugin.data().set(path + ".ends-at", this.safeEndTime(now));
            this.plugin.data().set(path + ".escrow-version", ESCROW_VERSION);
            this.plugin.saveData();
            this.plugin.recordQuestProgress(player, "auctions", 1);
            player.sendMessage(ChatColor.GREEN + "額縁をオークション化しました。終了まで " + this.formatRemaining(this.endsAt(path) - now) + "。ウォレットで右クリックすると入札できます。");
         }
      }
   }

   private void removeAuction(Player player, ItemFrame frame) {
      String path = this.auctionPath(frame);
      String owner = this.plugin.data().getString(path + ".owner", "");
      boolean allowed = player.hasPermission("mifron.auction.admin") || player.hasPermission("mifron.admin") || owner.equals(player.getUniqueId().toString());
      if (!allowed) {
         player.sendMessage(ChatColor.RED + "このオークションを解除できるのは出品者または管理者のみです。");
         return;
      }
      int refunded = this.refundEscrow(path, null, "オークションが取り消されたため、入札MPを返金しました。");
      this.plugin.data().set(path, null);
      this.plugin.saveData();
      player.sendMessage(ChatColor.YELLOW + "オークションを解除しました。" + (refunded > 0 ? "預かりMPは返金済みです。" : ""));
   }

   private void bid(Player player, ItemFrame frame, boolean largeStep) {
      String path = this.auctionPath(frame);
      String owner = this.plugin.data().getString(path + ".owner", "");
      if (owner.equals(player.getUniqueId().toString())) {
         player.sendMessage(ChatColor.RED + "自分のオークションには入札できません。");
         return;
      }
      int step = this.plugin.getConfig().getInt(largeStep ? "auction.sneak-bid-step" : "auction.bid-step", largeStep ? 1000 : 100);
      step = Math.max(1, step);
      int highest = this.safeCurrency(this.plugin.data().getInt(path + ".highest-amount", 0));
      int ownBid = this.safeCurrency(this.plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0));
      int nextBid = this.safeAdd(Math.max(highest, ownBid), step);
      if (nextBid <= highest) {
         player.sendMessage(ChatColor.RED + "最高入札額がMP上限に達しています。");
         return;
      }
      int ownEscrow = this.safeCurrency(this.plugin.data().getInt(path + ".escrow." + player.getUniqueId(), 0));
      long requiredValue = (long)nextBid - ownEscrow;
      if (requiredValue <= 0L || requiredValue > 2000000000L || !this.plugin.withdrawEmeralds(player.getUniqueId(), (int)requiredValue)) {
         int required = requiredValue > 2000000000L ? 2000000000 : (int)Math.max(0L, requiredValue);
         player.sendMessage(ChatColor.RED + "入札に必要なMPが不足しています。必要: " + this.formatNumber(required) + "MP");
         return;
      }
      String previousBidder = this.plugin.data().getString(path + ".highest-bidder", "");
      if (!previousBidder.isBlank() && !previousBidder.equals(player.getUniqueId().toString())) {
         this.refundEscrowBidder(path, previousBidder, "最高入札が更新されたため、入札MPを返金しました。");
      }
      this.plugin.data().set(path + ".bids." + player.getUniqueId(), nextBid);
      this.plugin.data().set(path + ".escrow." + player.getUniqueId(), nextBid);
      this.plugin.data().set(path + ".highest-bidder", player.getUniqueId().toString());
      this.plugin.data().set(path + ".highest-amount", nextBid);
      this.plugin.saveData();
      player.sendMessage(ChatColor.GREEN + "最高額で入札しました: " + this.formatNumber(nextBid) + "MP");
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.4F);
      this.showAuctionInfo(player, frame);
   }

   private void showAuctionInfo(Player player, ItemFrame frame) {
      String path = this.auctionPath(frame);
      int highest = this.plugin.data().getInt(path + ".highest-amount", 0);
      int own = this.plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0);
      ItemStack item = frame.getItem();
      String itemName = item != null && item.getType() != Material.AIR ? item.getType().name() : this.plugin.data().getString(path + ".item", "なし");
      player.sendMessage(ChatColor.GOLD + "オークション: " + itemName + ChatColor.GRAY + " / 最高入札: " + this.formatNumber(highest) + "MP / 自分: " + this.formatNumber(own) + "MP / 残り: " + this.formatRemaining(this.endsAt(path) - System.currentTimeMillis()));
   }

   private void settleExpiredAuctions() {
      ConfigurationSection frames = this.plugin.data().getConfigurationSection("auctions.frames");
      if (frames == null) {
         return;
      }
      for (String key : new ArrayList<>(frames.getKeys(false))) {
         UUID frameId = this.parseUuid(key);
         Entity entity = frameId == null ? null : this.plugin.getServer().getEntity(frameId);
         if (entity instanceof ItemFrame frame) {
            this.ensureAuctionMetadata(frame);
            this.settleIfExpired(frame);
         }
      }
   }

   private boolean settleIfExpired(ItemFrame frame) {
      String path = this.auctionPath(frame);
      if (System.currentTimeMillis() < this.endsAt(path)) {
         return false;
      }
      this.settleAuction(frame, path);
      return true;
   }

   private void settleAuction(ItemFrame frame, String path) {
      String auctionId = frame.getUniqueId().toString();
      UUID owner = this.parseUuid(this.plugin.data().getString(path + ".owner", ""));
      UUID winner = this.parseUuid(this.plugin.data().getString(path + ".highest-bidder", ""));
      int highest = this.plugin.data().getInt(path + ".highest-amount", 0);
      int escrow = winner == null ? 0 : this.plugin.data().getInt(path + ".escrow." + winner, 0);
      ItemStack item = this.plugin.data().getItemStack(path + ".item-stack");
      if ((item == null || item.getType() == Material.AIR) && frame.getItem().getType() != Material.AIR) {
         item = frame.getItem().clone();
      }
      if (winner == null || highest <= 0) {
         this.refundEscrow(path, null, "オークション終了に伴い、入札MPを返金しました。");
         this.plugin.data().set(path, null);
         this.notifyPlayer(owner, "入札がなかったため、オークションは終了しました。商品は額縁に残っています。");
      } else if (owner == null) {
         // Never complete a sale when the persisted seller identity is
         // invalid. Otherwise a damaged legacy record could transfer the
         // item to the winner without crediting a seller.
         this.refundEscrow(path, null, "出品者情報を確認できなかったため、オークションを取消しました。");
         this.plugin.data().set(path, null);
         this.notifyPlayer(winner, "出品者情報を確認できなかったため、オークションを取消しました。入札MPは返金済みです。");
      } else if (escrow < highest || item == null || item.getType() == Material.AIR) {
         this.refundEscrow(path, null, "オークションを精算できなかったため、入札MPを返金しました。");
         this.plugin.data().set(path, null);
         this.notifyPlayer(owner, "商品または預かりMPを確認できなかったため、オークションを取消しました。");
      } else {
         this.refundEscrow(path, winner, "オークション終了に伴い、入札MPを返金しました。");
         this.plugin.data().set(path + ".escrow." + winner, null);
         if (owner != null) {
            this.plugin.depositEmeralds(owner, highest);
         }
         frame.setItem(new ItemStack(Material.AIR));
         this.deliverItem(winner, auctionId, item);
         this.plugin.data().set(path, null);
         this.notifyPlayer(owner, "オークションが成立し、" + this.formatNumber(highest) + "MPを受け取りました。");
         this.notifyPlayer(winner, "オークションを" + this.formatNumber(highest) + "MPで落札しました。商品を配送しました。");
      }
      this.plugin.saveData();
   }

   private int refundEscrow(String path, UUID except, String message) {
      ConfigurationSection escrow = this.plugin.data().getConfigurationSection(path + ".escrow");
      if (escrow == null) {
         return 0;
      }
      int refunded = 0;
      for (String key : new ArrayList<>(escrow.getKeys(false))) {
         UUID bidder = this.parseUuid(key);
         if (bidder != null && !bidder.equals(except)) {
            int amount = Math.max(0, escrow.getInt(key, 0));
            if (amount > 0) {
               this.plugin.refundEmeralds(bidder, amount);
               this.notifyPlayer(bidder, message + " +" + this.formatNumber(amount) + "MP");
               refunded = this.safeAdd(refunded, amount);
            }
            this.plugin.data().set(path + ".escrow." + key, null);
         }
      }
      return refunded;
   }

   private void refundEscrowBidder(String path, String bidderKey, String message) {
      UUID bidder = this.parseUuid(bidderKey);
      int amount = bidder == null ? 0 : this.plugin.data().getInt(path + ".escrow." + bidderKey, 0);
      if (bidder != null && amount > 0) {
         this.plugin.refundEmeralds(bidder, amount);
         this.notifyPlayer(bidder, message + " +" + this.formatNumber(amount) + "MP");
      }
      this.plugin.data().set(path + ".escrow." + bidderKey, null);
   }

   private void deliverItem(UUID winner, String auctionId, ItemStack item) {
      Player player = this.plugin.getServer().getPlayer(winner);
      if (player == null || !player.isOnline()) {
         String path = "auctions.pending-items." + winner + "." + auctionId;
         this.plugin.data().set(path + ".item", item.clone());
         this.plugin.data().set(path + ".created-at", System.currentTimeMillis());
         return;
      }
      for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
         player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
   }

   private void deliverPendingItems(Player player) {
      String path = "auctions.pending-items." + player.getUniqueId();
      ConfigurationSection pending = this.plugin.data().getConfigurationSection(path);
      if (pending == null) {
         return;
      }
      int delivered = 0;
      for (String key : new ArrayList<>(pending.getKeys(false))) {
         ItemStack item = this.plugin.data().getItemStack(path + "." + key + ".item");
         if (item != null && item.getType() != Material.AIR) {
            for (ItemStack leftover : player.getInventory().addItem(item.clone()).values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            delivered++;
         }
         this.plugin.data().set(path + "." + key, null);
      }
      this.plugin.data().set(path, null);
      this.plugin.saveData();
      if (delivered > 0) {
         player.sendMessage(ChatColor.GREEN + "オークション落札品を" + delivered + "件受け取りました。");
      }
   }

   private void notifyPlayer(UUID uuid, String message) {
      if (uuid == null || message == null || message.isBlank()) {
         return;
      }
      Player player = this.plugin.getServer().getPlayer(uuid);
      if (player != null && player.isOnline()) {
         player.sendMessage(ChatColor.GOLD + "[オークション] " + ChatColor.GRAY + message);
         return;
      }
      String path = "auctions.pending-messages." + uuid;
      List<String> messages = new ArrayList<>(this.plugin.data().getStringList(path));
      messages.add(message);
      if (messages.size() > 20) {
         messages = new ArrayList<>(messages.subList(messages.size() - 20, messages.size()));
      }
      this.plugin.data().set(path, messages);
   }

   private void deliverPendingMessages(Player player) {
      String path = "auctions.pending-messages." + player.getUniqueId();
      List<String> messages = this.plugin.data().getStringList(path);
      if (messages.isEmpty()) {
         return;
      }
      for (String message : messages) {
         player.sendMessage(ChatColor.GOLD + "[オークション] " + ChatColor.GRAY + message);
      }
      this.plugin.data().set(path, null);
      this.plugin.saveData();
   }

   private void ensureAuctionMetadata(ItemFrame frame) {
      String path = this.auctionPath(frame);
      boolean changed = false;
      if (this.plugin.data().getInt(path + ".escrow-version", 0) < ESCROW_VERSION) {
         this.plugin.data().set(path + ".bids", null);
         this.plugin.data().set(path + ".escrow", null);
         this.plugin.data().set(path + ".highest-bidder", null);
         this.plugin.data().set(path + ".highest-amount", 0);
         this.plugin.data().set(path + ".escrow-version", ESCROW_VERSION);
         changed = true;
      }
      if (!this.plugin.data().contains(path + ".item-stack") && frame.getItem().getType() != Material.AIR) {
         this.plugin.data().set(path + ".item-stack", frame.getItem().clone());
         changed = true;
      }
      if (this.plugin.data().getLong(path + ".ends-at", 0L) <= 0L) {
         this.plugin.data().set(path + ".ends-at", this.safeEndTime(System.currentTimeMillis()));
         changed = true;
      }
      if (changed) {
         this.plugin.saveData();
      }
   }

   private long safeEndTime(long start) {
      long durationMinutes = Math.min(5256000L, Math.max(1L, this.plugin.getConfig().getLong("auction.duration-minutes", 1440L)));
      long durationMillis = durationMinutes * 60000L;
      return start > Long.MAX_VALUE - durationMillis ? Long.MAX_VALUE : start + durationMillis;
   }

   private long endsAt(String path) {
      return this.plugin.data().getLong(path + ".ends-at", Long.MAX_VALUE);
   }

   private String formatRemaining(long millis) {
      long seconds = Math.max(0L, millis / 1000L);
      long days = seconds / 86400L;
      long hours = seconds % 86400L / 3600L;
      long minutes = seconds % 3600L / 60L;
      if (days > 0L) {
         return days + "日" + hours + "時間";
      }
      return hours > 0L ? hours + "時間" + minutes + "分" : Math.max(1L, minutes) + "分";
   }

   private UUID parseUuid(String value) {
      try {
         return value == null || value.isBlank() ? null : UUID.fromString(value);
      } catch (IllegalArgumentException ignored) {
         return null;
      }
   }

   private String auctionPath(Entity entity) {
      return "auctions.frames." + entity.getUniqueId();
   }

   private int safeAdd(int first, int second) {
      return (int)Math.min(2000000000L, (long)Math.max(0, first) + Math.max(0, second));
   }

   private int safeCurrency(int value) {
      return Math.max(0, Math.min(2000000000, value));
   }

   private String formatNumber(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }
}
