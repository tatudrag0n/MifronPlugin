package org.server.mifron;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class UtilityItemsFeature implements Listener {
   private static final Set<String> INITIAL_ITEM_IDS = Set.of("emerald_bundle", "friend_book", "quest_book", "teleporter", "online_shop");
   private static final Set<String> FIXED_ITEM_IDS = Set.of(
      "emerald_bundle", "friend_book", "quest_book", "teleporter", "shelf_shop_wand", "shop_wand", "slot_wand", "server_wand", "jump_pad_wand", "jump_block"
   );
   private static final int MAX_JUMP_PAD_POWER = 100;
   private static final long UTILITY_USE_DEBOUNCE_MILLIS = 150L;
   private final Mifron plugin;
   private final Map<UUID, Long> lastUtilityUse = new ConcurrentHashMap<>();
   private final NamespacedKey mifronItemKey;
   private final NamespacedKey shopWandTypeKey;
   private final NamespacedKey jumpPadPowerKey;
   private final NamespacedKey jumpPadVerticalPowerKey;
   private final NamespacedKey jumpPadHorizontalPowerKey;

   UtilityItemsFeature(Mifron plugin) {
      this.plugin = plugin;
      this.mifronItemKey = new NamespacedKey(plugin, "item");
      this.shopWandTypeKey = new NamespacedKey(plugin, "shop_wand_type");
      this.jumpPadPowerKey = new NamespacedKey(plugin, "jump_pad_power");
      this.jumpPadVerticalPowerKey = new NamespacedKey(plugin, "jump_pad_vertical_power");
      this.jumpPadHorizontalPowerKey = new NamespacedKey(plugin, "jump_pad_horizontal_power");
   }

   void giveInitialItems(Player player) {
      this.removeMifronItems(player, "hub_compass");
      this.updateOrGiveMifronItem(player, "online_shop", this.createMifronItem(
         Material.IRON_DOOR, "online_shop", ChatColor.AQUA + "OnlineShop", List.of(ChatColor.GRAY + "右クリック: 商品一覧を開く（Survivalのみ）")
      ));
      this.updateOrGiveMifronItem(
         player,
         "emerald_bundle",
         this.createMifronItem(
            Material.BUNDLE, "emerald_bundle", ChatColor.GREEN + "ウォレット", List.of(ChatColor.GRAY + "右クリック: MP残高確認", ChatColor.GRAY + "棚ショップ・スロットに右クリック: 使用", ChatColor.GRAY + "アイテム収納不可")
         )
      );
      this.updateOrGiveMifronItem(player, "friend_book", this.createStatusBook());
      this.updateOrGiveMifronItem(player, "quest_book", this.createQuestBook());
      this.updateOrGiveMifronItem(
         player,
         "teleporter",
         this.createMifronItem(
            Material.ENDER_EYE,
            "teleporter",
            ChatColor.LIGHT_PURPLE + "テレポーター",
            List.of(
               ChatColor.GRAY + "右クリック: 目の前に移動先を表示",
               ChatColor.GRAY + "表示されたアイテムを左クリック: テレポート",
               ChatColor.GRAY + "エンドポータルフレームに使用: フレームの登録地点へ移動",
               ChatColor.DARK_GRAY + "投げることはできません"
            )
         )
      );
   }

   ItemStack createShopWand() {
      return this.createMifronItem(
         Material.BLAZE_ROD,
         "shelf_shop_wand",
         ChatColor.GOLD + "ショップワンド",
         List.of(
            ChatColor.GRAY + "棚を右クリック: 番号順の順番配置",
            ChatColor.GRAY + "棚をShift+右クリック: オフハンドの商品を指定枠へ配置",
            ChatColor.GRAY + "指定配置でShift+左クリック: 選択枠を空欄化",
            ChatColor.GRAY + "左クリック: ショップ化を解除",
            ChatColor.DARK_GRAY + "棚商品の在庫は全棚で共有されます。"
         )
      );
   }

   ItemStack createShopWand(ShopWandType type) {
      if (type.isSlotWand()) {
         SlotMachineManager.Difficulty difficulty = type.getSlotDifficulty();
         String diffName = difficulty != null ? difficulty.name() : "";
         return this.createMifronItem(
            Material.BLAZE_ROD,
            "slot_wand",
            ChatColor.GOLD + "スロットワンド [" + diffName + "]",
            List.of(
               ChatColor.GRAY + "難易度: " + this.getDifficultyDisplayName(difficulty),
               ChatColor.GRAY + "右クリック: 棚をスロットマシン化",
               ChatColor.GRAY + "ウォレットを持って棚を右クリックで回転"
            ),
            meta -> {
               PersistentDataContainer container = meta.getPersistentDataContainer();
               container.set(this.shopWandTypeKey, PersistentDataType.STRING, type.key());
            }
         );
      } else {
         return this.createMifronItem(
            Material.BLAZE_ROD,
            "shop_wand",
            ChatColor.GOLD + "ショップワンド",
            type == ShopWandType.SHELF
               ? List.of(
                  ChatColor.GRAY + "右クリック: 番号順の順番配置",
                  ChatColor.GRAY + "Shift+右クリック: オフハンドの商品を指定枠へ配置",
                  ChatColor.GRAY + "Shift+左クリック: 指定枠を空欄化",
                  ChatColor.GRAY + "左クリック: ショップ化を解除"
               )
               : List.of(ChatColor.GRAY + "種類: " + type.key(), ChatColor.GRAY + "右クリック: 対応ブロックをショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除"),
            meta -> {
               PersistentDataContainer container = meta.getPersistentDataContainer();
               container.set(this.shopWandTypeKey, PersistentDataType.STRING, type.key());
            }
         );
      }
   }

   private String getDifficultyDisplayName(SlotMachineManager.Difficulty difficulty) {
      if (difficulty == null) return "不明";
      switch (difficulty) {
         case EASY: return ChatColor.GREEN + "イージー";
         case NORMAL: return ChatColor.YELLOW + "ノーマル";
         case HARD: return ChatColor.RED + "ハード";
         case EXPERT: return "" + ChatColor.DARK_RED + ChatColor.BOLD + "エキスパート";
         default: return "不明";
      }
   }

   ItemStack createJumpPadWand(int verticalPower, int horizontalPower) {
      int safeVerticalPower = this.clampJumpPadPower(verticalPower);
      int safeHorizontalPower = this.clampJumpPadPower(horizontalPower);
      return this.createMifronItem(
         Material.FEATHER,
         "jump_pad_wand",
         ChatColor.AQUA + "ジャンプパッドワンド",
         List.of(
            ChatColor.GRAY + "縦の強さ: " + safeVerticalPower,
            ChatColor.GRAY + "横の強さ: " + safeHorizontalPower,
            ChatColor.GRAY + "右クリック: ブロックをジャンプパッド化",
            ChatColor.GRAY + "左クリック: ジャンプパッドを解除"
         ),
         meta -> {
            meta.getPersistentDataContainer().set(this.jumpPadVerticalPowerKey, PersistentDataType.INTEGER, safeVerticalPower);
            meta.getPersistentDataContainer().set(this.jumpPadHorizontalPowerKey, PersistentDataType.INTEGER, safeHorizontalPower);
         }
      );
   }

   ItemStack createJumpBlock(int verticalPower, int horizontalPower) {
      int safeVerticalPower = this.clampJumpPadPower(verticalPower);
      int safeHorizontalPower = this.clampJumpPadPower(horizontalPower);
      return this.createMifronItem(
         Material.CHISELED_TUFF,
         "jump_block",
         ChatColor.AQUA + "ジャンプブロック",
         List.of(
            ChatColor.GRAY + "縦の強さ: " + safeVerticalPower,
            ChatColor.GRAY + "横の強さ: " + safeHorizontalPower,
            ChatColor.GRAY + "設置するとジャンプ台になります",
            ChatColor.GRAY + "縦横: 0〜100"
         ),
         meta -> {
            meta.getPersistentDataContainer().set(this.jumpPadVerticalPowerKey, PersistentDataType.INTEGER, safeVerticalPower);
            meta.getPersistentDataContainer().set(this.jumpPadHorizontalPowerKey, PersistentDataType.INTEGER, safeHorizontalPower);
         }
      );
   }

   private boolean isJumpPowerItem(ItemStack item) {
      return this.isMifronItem(item, "jump_pad_wand") || this.isMifronItem(item, "jump_block");
   }

   int getJumpPadVerticalPower(ItemStack item) {
      if (!this.isJumpPowerItem(item)) return 5;
      PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
      Integer power = (Integer)container.get(this.jumpPadVerticalPowerKey, PersistentDataType.INTEGER);
      if (power != null) return this.clampJumpPadPower(power);
      Integer oldPower = (Integer)container.get(this.jumpPadPowerKey, PersistentDataType.INTEGER);
      return oldPower == null ? 5 : this.oldPowerToNewPower(oldPower);
   }

   int getJumpPadHorizontalPower(ItemStack item) {
      if (!this.isJumpPowerItem(item)) return 5;
      PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
      Integer power = (Integer)container.get(this.jumpPadHorizontalPowerKey, PersistentDataType.INTEGER);
      if (power != null) return this.clampJumpPadPower(power);
      Integer oldPower = (Integer)container.get(this.jumpPadPowerKey, PersistentDataType.INTEGER);
      return oldPower == null ? 5 : this.oldPowerToNewPower(oldPower);
   }

   boolean hasMifronItem(Player player, String id) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMifronItem(item, id)) return true;
      }
      // getContents() excludes the off hand and armour: without these, a
      // parked item is "missing" and gets duplicated on re-issue.
      if (this.isMifronItem(player.getInventory().getItemInOffHand(), id)) return true;
      for (ItemStack item : player.getInventory().getArmorContents()) {
         if (this.isMifronItem(item, id)) return true;
      }
      return false;
   }

   boolean isMifronItem(ItemStack item, String id) {
      return id.equals(this.getMifronItemId(item));
   }

   boolean isShopWand(ItemStack item) {
      String id = this.getMifronItemId(item);
      return "shop_wand".equals(id) || "shelf_shop_wand".equals(id) || "slot_wand".equals(id);
   }

   boolean isLegacyShopWand(ItemStack item) {
      return this.isMifronItem(item, "shelf_shop_wand");
   }

   ShopWandType getShopWandType(ItemStack item) {
      if (this.isShopWand(item) && item != null && item.hasItemMeta()) {
         String raw = (String)MifronPdc.get(item.getItemMeta().getPersistentDataContainer(), this.shopWandTypeKey, PersistentDataType.STRING);
         return ShopWandType.fromKey(raw);
      }
      return null;
   }

   String getMifronItemId(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
         return (String)MifronPdc.get(container, this.mifronItemKey, PersistentDataType.STRING);
      }
      return null;
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (player == null) return;

      // Resolve the item for the hand that fired this event. Paper does not
      // always populate getItem() for both hands, so fall back to the inventory.
      ItemStack item = event.getItem();
      if (item == null || item.getType() == Material.AIR) {
         EquipmentSlot hand = event.getHand();
         item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
      }
      String id = this.getMifronItemId(item);
      if (id == null) return;

      // When both hands hold a Mifron item, only the main hand may act. This
      // prevents the same utility item from opening twice from one click.
      if (event.getHand() == EquipmentSlot.OFF_HAND
         && this.getMifronItemId(player.getInventory().getItemInMainHand()) != null) {
         return;
      }

      // A block interaction that another listener already cancelled is a shop
      // or protected-block transaction; do not run the item action twice.
      if (event.isCancelled() && event.getAction() == Action.RIGHT_CLICK_BLOCK) return;

      // A single right-click can deliver the event more than once (dual-hand
      // dispatch and block/air pass), so debounce the wallet exactly like the
      // other utility items. Without this the balance was printed twice.
      long now = System.currentTimeMillis();
      Long last = this.lastUtilityUse.get(player.getUniqueId());
      if (last != null && now - last < UTILITY_USE_DEBOUNCE_MILLIS) {
         event.setCancelled(true);
         return;
      }

      if ("emerald_bundle".equals(id)) {
         // Slot machines start on wallet right-click, but their handler runs
         // at HIGH with ignoreCancelled=true: cancelling here first would
         // make spins impossible, so hands off machine blocks entirely.
         if (event.getAction().isRightClick() && event.getClickedBlock() != null
            && this.plugin.slotMachineManager.isSlotMachine(event.getClickedBlock())) return;
         event.setCancelled(true);
         event.setUseItemInHand(Event.Result.DENY);
         this.lastUtilityUse.put(player.getUniqueId(), now);
         if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.plugin.tryShopPayment(player, event.getClickedBlock())) return;
         player.sendMessage(ChatColor.GREEN + "所持MP: " + this.plugin.formatNumber(this.plugin.getEmeralds(player.getUniqueId())));
         return;
      }

      boolean clicked = event.getAction().isRightClick() || event.getAction().isLeftClick();
      if ("friend_book".equals(id) && clicked) {
         event.setCancelled(true);
         event.setUseItemInHand(Event.Result.DENY);
         // Deny the interacted block too: right-clicking a container with the
         // book would otherwise let the client predict-open the vanilla
         // window, desyncing window ids so every later click is silently
         // dropped server-side (ghost GUI: predicted pickup, no navigation).
         event.setUseInteractedBlock(Event.Result.DENY);
         this.lastUtilityUse.put(player.getUniqueId(), now);
         this.plugin.openFriendUi(player);
         this.scheduleRestore(player);
      } else if ("quest_book".equals(id) && clicked) {
         event.setCancelled(true);
         event.setUseItemInHand(Event.Result.DENY);
         event.setUseInteractedBlock(Event.Result.DENY);
         this.lastUtilityUse.put(player.getUniqueId(), now);
         this.plugin.openQuestUi(player, "categories");
         this.scheduleRestore(player);
      } else if ("teleporter".equals(id) && event.getAction().isRightClick()) {
         event.setCancelled(true);
         this.lastUtilityUse.put(player.getUniqueId(), now);
      } else if (this.plugin.isReincarnationStar(item) && event.getAction().isRightClick()) {
         event.setCancelled(true);
         this.lastUtilityUse.put(player.getUniqueId(), now);
         this.plugin.tryReincarnate(player, item);
      }
   }

   private void scheduleRestore(Player player) {
      Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.giveInitialItems(player));
   }

   @EventHandler(ignoreCancelled = true)
   public void onDropItem(PlayerDropItemEvent event) {
      ItemStack stack = event.getItemDrop().getItemStack();
      if (this.isInitialMifronItem(stack) || this.isFixedMifronUtilityItem(stack)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.YELLOW + "Mifronの固定アイテムは捨てられません。");
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastUtilityUse.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(ignoreCancelled = true)
   public void onInventoryClick(InventoryClickEvent event) {
      if (!this.isMifronItem(event.getCursor(), "emerald_bundle")) return;
      ItemStack current = event.getCurrentItem();
      if (current == null || current.getType() == Material.AIR) return;
      if (this.isMifronItem(current, "emerald_bundle")) return;
      event.setCancelled(true);
      if (event.getWhoClicked() instanceof Player player) player.sendMessage(ChatColor.YELLOW + "ウォレットにはアイテムを収納できません。");
   }

   /**
    * Mifron fixed items (wallet/status/quest books/teleporter/wands/jump block)
    * move freely inside the player's own inventory (pick up, place, swap,
    * hotbar keys, off-hand swaps, drags, double-click collect), but must never
    * reach an external container (Chest/Barrel/Hopper/crafting grid, ...).
    * Only the paths crossing into the top container are cancelled.
    */
   @EventHandler(ignoreCancelled = true)
   public void onFixedItemStoreClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (!isExternalContainerView(player, event.getView())) return;
      ClickType click = event.getClick();
      ItemStack clicked = event.getCurrentItem();
      ItemStack cursor = event.getCursor();
      boolean hotbarIsFixed = false;
      if (click == ClickType.NUMBER_KEY) {
         hotbarIsFixed = this.isFixedMifronUtilityItem(player.getInventory().getItem(event.getHotbarButton()));
      }
      boolean offhandIsFixed = false;
      if (click == ClickType.SWAP_OFFHAND) {
         offhandIsFixed = this.isFixedMifronUtilityItem(player.getInventory().getItemInOffHand());
      }
      boolean clickedInPlayer = event.getClickedInventory() != null && event.getClickedInventory() == player.getInventory();
      boolean clickedOutside = event.getClickedInventory() == null;
      if (shouldCancelFixedItemStore(click, this.isFixedMifronUtilityItem(clicked), this.isFixedMifronUtilityItem(cursor),
         hotbarIsFixed, offhandIsFixed, clickedInPlayer, clickedOutside)) {
         event.setCancelled(true);
         player.sendMessage(ChatColor.YELLOW + "Mifronの固定アイテムはチェストなどに収納できません。");
      }
   }

   /**
    * A fixed item on the cursor would drop to the ground when the inventory
    * closes. Pull it back into the inventory instead (leftovers, only when
    * the inventory is completely full, drop at the player's feet).
    */
   @EventHandler(ignoreCancelled = true)
   public void onFixedItemClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) return;
      ItemStack cursor = player.getItemOnCursor();
      if (!this.isFixedMifronUtilityItem(cursor)) return;
      player.setItemOnCursor(null);
      for (ItemStack leftover : player.getInventory().addItem(cursor).values()) {
         player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onFixedItemDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      if (!this.isFixedMifronUtilityItem(event.getOldCursor())) return;
      if (!isExternalContainerView(player, event.getView())) return;
      int topSize = event.getView().getTopInventory().getSize();
      for (int rawSlot : event.getRawSlots()) {
         if (rawSlot < topSize) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.YELLOW + "Mifronの固定アイテムはチェストなどに収納できません。");
            return;
         }
      }
   }

   private static boolean isExternalContainerView(Player player, InventoryView view) {
      if (player == null || view == null || view.getTopInventory() == null) return false;
      // The player's own inventory/crafting view is never an external store.
      // Custom Mifron GUIs use the viewing player as holder but are still
      // external surfaces for fixed items, so only PlayerInventory is exempt.
      return !(view.getTopInventory().getHolder() instanceof org.bukkit.inventory.PlayerInventory);
   }

   /**
    * Pure decision for the fixed-item store guard, extracted for regression
    * tests. {@code topIsExternal} is assumed true by callers.
    */
   static boolean shouldCancelFixedItemStore(ClickType click, boolean currentIsFixed, boolean cursorIsFixed,
      boolean hotbarIsFixed, boolean offhandIsFixed, boolean clickedInPlayerInventory, boolean clickedOutside) {
      // Clicks outside the window drop the cursor stack; dropping is handled
      // by the drop guard, but cancelling here is harmless defense in depth.
      if (clickedOutside) return cursorIsFixed;
      if (clickedInPlayerInventory) {
         // Inside the player's own inventory everything is allowed except a
         // shift-click on a fixed item, which would move it out to the top.
         return (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) && currentIsFixed;
      }
      // Clicks inside the external top container: only paths carrying a fixed
      // item inward are blocked. Anything else (including picking a legacy
      // fixed item back out) stays allowed.
      if (cursorIsFixed) return true;
      if (click == ClickType.NUMBER_KEY && hotbarIsFixed) return true;
      return click == ClickType.SWAP_OFFHAND && offhandIsFixed;
   }

   @EventHandler(ignoreCancelled = true)
   public void onFixedItemHopperMove(InventoryMoveItemEvent event) {
      if (this.isFixedMifronUtilityItem(event.getItem())) event.setCancelled(true);
   }

   private void giveMifronItemIfMissing(Player player, String id, ItemStack item) {
      if (!this.hasMifronItem(player, id)) {
         Map leftovers = player.getInventory().addItem(new ItemStack[]{item});
         if (!leftovers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "インベントリに空きがないため、初期アイテムを配布できませんでした: " + id);
         }
      }
   }

   private void updateOrGiveMifronItem(Player player, String id, ItemStack template) {
      boolean found = false;
      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMifronItem(item, id)) {
            if (item.getType() != template.getType()) item.setType(template.getType());
            ItemMeta meta = item.getItemMeta();
            ItemMeta templateMeta = template.getItemMeta();
            meta.displayName(templateMeta.displayName());
            meta.lore(templateMeta.lore());
            if (meta instanceof BookMeta bookMeta && templateMeta instanceof BookMeta templateBookMeta) {
               bookMeta.setTitle(templateBookMeta.getTitle());
               bookMeta.setAuthor(templateBookMeta.getAuthor());
               bookMeta.pages(templateBookMeta.pages());
            }
            item.setItemMeta(meta);
            found = true;
         }
      }
      if (!found) this.giveMifronItemIfMissing(player, id, template);
   }

   private void removeMifronItems(Player player, String id) {
      ItemStack[] contents = player.getInventory().getContents();
      for (int slot = 0; slot < contents.length; slot++) {
         if (this.isMifronItem(contents[slot], id)) player.getInventory().setItem(slot, null);
      }
   }

   private ItemStack createStatusBook() {
      return this.createMifronItem(Material.NETHER_STAR, "friend_book", ChatColor.GOLD + "ステータス", List.of(ChatColor.GRAY + "右クリック: ステータス UI"));
   }

   private ItemStack createQuestBook() {
      return this.createMifronItem(Material.KNOWLEDGE_BOOK, "quest_book", ChatColor.AQUA + "クエスト", List.of(ChatColor.GRAY + "右クリック: クエスト UI"));
   }

   private ItemStack createMifronItem(Material material, String id, String name, List<String> lore) {
      return this.createMifronItem(material, id, name, lore, null);
   }

   private ItemStack createMifronItem(Material material, String id, String name, List<String> lore, Consumer<ItemMeta> customizer) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.lore(lore.stream().map(Component::text).toList());
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      meta.getPersistentDataContainer().set(this.mifronItemKey, PersistentDataType.STRING, id);
      if (customizer != null) customizer.accept(meta);
      item.setItemMeta(meta);
      return item;
   }

   private boolean isFixedMifronUtilityItem(ItemStack item) {
      String id = this.getMifronItemId(item);
      return id != null && FIXED_ITEM_IDS.contains(id);
   }

   /**
    * Fixed and initial utility items survive death: they are pulled out of the
    * death drops and re-issued on respawn instead of dropping on the ground.
    */
   boolean isDeathProtectedItem(ItemStack item) {
      return this.isFixedMifronUtilityItem(item) || this.isInitialMifronItem(item);
   }

   private boolean isInitialMifronItem(ItemStack item) {
      String id = this.getMifronItemId(item);
      return id != null && INITIAL_ITEM_IDS.contains(id);
   }

   private int clampJumpPadPower(int power) {
      return Math.max(0, Math.min(100, power));
   }

   private int oldPowerToNewPower(int power) {
      return this.clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2);
   }
}
