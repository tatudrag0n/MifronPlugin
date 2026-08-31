package org.server.mifron;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Adds configurable, Survival-world-only anvil combining while retaining the
 * vanilla prior-work penalty and XP progression. This feature deliberately
 * does not change shop valuation; shop code continues to price by material.
 */
final class AdvancedAnvilFeature implements Listener {
   private static final Set<String> DEFAULT_EXCLUDED_IDS = Set.of(
      "emerald_bundle", "friend_book", "quest_book", "teleporter",
      "shelf_shop_wand", "shop_wand", "slot_wand", "server_wand",
      "jump_pad_wand", "hub_compass"
   );
   private final Mifron plugin;
   private final NamespacedKey currentItemKey;
   private final NamespacedKey currentFfaItemKey;
   private final NamespacedKey legacyFfaItemKey;
   private final NamespacedKey currentFieldItemKey;
   private final NamespacedKey legacyFieldItemKey;
   private final NamespacedKey advancedEnchantDisplayKey;
   private final NamespacedKey advancedEnchantLoreCountKey;
   private final NamespacedKey advancedCostDisplayKey;
   private final Map<Inventory, PendingResult> pendingResults = new WeakHashMap<>();

   AdvancedAnvilFeature(Mifron plugin) {
      this.plugin = plugin;
      this.currentItemKey = new NamespacedKey(plugin, "item");
      this.currentFfaItemKey = new NamespacedKey(plugin, "ffa_item");
      this.legacyFfaItemKey = new NamespacedKey("minerva", "ffa_item");
      this.currentFieldItemKey = new NamespacedKey(plugin, "ffa_field_item");
      this.legacyFieldItemKey = new NamespacedKey("minerva", "ffa_field_item");
      this.advancedEnchantDisplayKey = new NamespacedKey(plugin, "advanced_enchant_display");
      this.advancedEnchantLoreCountKey = new NamespacedKey(plugin, "advanced_enchant_lore_count");
      this.advancedCostDisplayKey = new NamespacedKey(plugin, "advanced_anvil_cost_display");
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onPrepareAnvil(PrepareAnvilEvent event) {
      AnvilInventory inventory = event.getInventory();
      this.pendingResults.remove(inventory);

      HumanEntity viewer = event.getView().getPlayer();
      if (!(viewer instanceof Player player) || !this.isFeatureAllowed(player.getWorld())) {
         return;
      }
      this.configureMaximumRepairCost(event.getView());

      ItemStack left = inventory.getItem(0);
      ItemStack right = inventory.getItem(1);
      if (this.isEmpty(left) || this.isEmpty(right)) {
         return;
      }

      if (this.isExcluded(left) || this.isExcluded(right)) {
         if (!this.enchantments(right).isEmpty()) {
            event.setResult(null);
         }
         return;
      }
      if (!this.isTargetMaterial(left.getType())) {
         return;
      }

      Map<Enchantment, Integer> incoming = this.enchantments(right);
      if (incoming.isEmpty()) {
         return;
      }

      ItemStack vanillaResult = event.getResult();
      // Do not use the vanilla result as the data source. Paper can copy the
      // right-hand book into that result before this listener runs, and a
      // high-level/legacy book can then retain both direct and stored forms of
      // one enchantment. Rebuild from the left input so every merge has one
      // deterministic representation.
      ItemStack result = this.createNormalizedResult(left, right);
      this.resetResultEnchantments(result, left);
      int changed = this.mergeEnchantments(result, left, incoming);
      if (changed == 0) {
         return;
      }

      int vanillaCost = Math.max(0, event.getView().getRepairCost());
      int totalCost = Math.max(vanillaCost, this.fallbackCost(left, right, incoming, result));
      totalCost = Math.min(totalCost, this.maximumRepairCost());
      boolean exceedsVanillaLevel = this.hasEnchantmentAboveVanillaMaximum(result);
      int xpCost = exceedsVanillaLevel ? 0 : totalCost;
      int mpCost = exceedsVanillaLevel ? this.mpCost(totalCost, result, left) : 0;
      event.getView().setRepairCost(xpCost);
      this.configureMaximumRepairCost(event.getView());
      if (vanillaResult == null) {
         this.applyRepairPenalty(result, left, right);
      }
      this.applyAnvilDisplay(result, xpCost, mpCost);
      event.setResult(result);
      this.pendingResults.put(inventory, new PendingResult(result.clone(), xpCost, mpCost));
   }

   /**
    * Creates the result container with the correct metadata type before any
    * enchantments are written. In particular, changing BOOK to ENCHANTED_BOOK
    * after cloning can leave a regular ItemMeta behind on some server builds.
    * That produces one direct and one stored copy of the same enchantment and
    * makes the next merge appear to be stuck at the old level.
    */
   private ItemStack createNormalizedResult(ItemStack left, ItemStack right) {
      // The normal anvil order is item+book, but accepting book+item as well
      // makes the feature deterministic for both input directions. Whenever
      // the left input is a book, the result must be an enchanted book so the
      // merged levels are stored rather than written as direct item enchants.
      if (!this.isBookInput(left)) {
         return left.clone();
      }

      // Always create the result as a real ENCHANTED_BOOK and require
      // EnchantmentStorageMeta. A plain BOOK or a stale ItemMeta converted
      // from an older build can otherwise receive a direct enchantment in
      // addition to its stored enchantment. That produces duplicate tooltip
      // lines and makes a later V+V merge appear to stop at the old level.
      ItemStack result = new ItemStack(Material.ENCHANTED_BOOK, left.getAmount());
      ItemMeta converted = null;
      ItemMeta sourceMeta = left.getItemMeta();
      if (sourceMeta != null) {
         ItemMeta candidate = Bukkit.getItemFactory().asMetaFor(sourceMeta, Material.ENCHANTED_BOOK);
         if (candidate instanceof EnchantmentStorageMeta) {
            converted = candidate;
         }
      }
      if (converted == null) {
         ItemMeta fallback = result.getItemMeta();
         if (fallback instanceof EnchantmentStorageMeta) {
            converted = fallback;
         }
      }
      if (converted != null) {
         result.setItemMeta(converted);
      }
      return result;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onTakeResult(InventoryClickEvent event) {
      if (event.getRawSlot() < 0 || event.getRawSlot() != event.getView().getTopInventory().getSize() - 1) {
         return;
      }

      Inventory top = event.getView().getTopInventory();
      PendingResult pending = this.pendingResults.get(top);
      if (pending == null || !(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      // Restrict the custom path to the ordinary empty-cursor click. This
      // avoids charging MP when a shift-click or unusual cursor transaction is
      // rejected later by the container implementation.
      if (event.getClick() != ClickType.LEFT || !this.isEmpty(event.getCursor())) {
         event.setCancelled(true);
         player.sendMessage(Component.text("高度な金床合成は、空のカーソルで結果を左クリックしてください。", NamedTextColor.YELLOW));
         return;
      }

      if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < pending.xpCost()) {
         event.setCancelled(true);
         player.sendMessage(Component.text("経験値レベルが足りません。必要レベル: " + pending.xpCost(), NamedTextColor.RED));
         return;
      }

      if (pending.mpCost() > 0 && this.plugin.getEmeralds(player.getUniqueId()) < pending.mpCost()) {
         event.setCancelled(true);
         player.sendMessage(Component.text("MPが足りません。必要MP: " + pending.mpCost(), NamedTextColor.RED));
         return;
      }

      // Consume the prepared result before charging. InventoryClickEvent can
      // be re-entered by rapid/double clicks before the next tick callback
      // runs; keeping the pending entry would allow the same result to be
      // charged and taken twice. A failed vanilla transaction is refunded in
      // the callback below, while the next prepare event can recreate it.
      if (this.pendingResults.remove(top) == null) {
         event.setCancelled(true);
         player.sendMessage(Component.text("この合成結果はすでに処理されています。もう一度お試しください。", NamedTextColor.YELLOW));
         return;
      }

      if (pending.mpCost() > 0 && !this.plugin.withdrawEmeralds(player.getUniqueId(), pending.mpCost())) {
         event.setCancelled(true);
         player.sendMessage(Component.text("MPの支払いに失敗しました。もう一度お試しください。", NamedTextColor.RED));
         return;
      }

      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (!player.isOnline()) {
            if (pending.mpCost() > 0) {
               this.plugin.depositEmeralds(player.getUniqueId(), pending.mpCost());
            }
            return;
         }

         ItemStack cursor = player.getItemOnCursor();
         if (!this.isSameResult(cursor, pending.result())) {
            // The vanilla container did not complete the transaction (for
            // example because another listener cancelled it). Refund MP.
            if (pending.mpCost() > 0) {
               this.plugin.depositEmeralds(player.getUniqueId(), pending.mpCost());
            }
            player.sendMessage(Component.text("合成が完了しなかったため、MPを返却しました。", NamedTextColor.YELLOW));
            return;
         }
         this.removeTransientCostDisplay(cursor);
      });
   }

   /**
    * Displays costs in the result tooltip. Vanilla only exposes an XP cost in
    * the anvil UI, so the MP part is deliberately shown as item lore and is
    * removed after the item is successfully taken from the anvil.
    */
   private void applyAnvilDisplay(ItemStack result, int xpCost, int mpCost) {
      ItemMeta meta = result.getItemMeta();
      if (meta == null) {
         return;
      }

      List<Component> lore = new ArrayList<>();
      if (meta.lore() != null) {
         lore.addAll(meta.lore());
      }
      this.removeTransientCostLore(meta, lore);
      this.removeGeneratedEnchantLore(meta, lore);
      // Older builds rendered the enchantment tooltip as custom lore and, on
      // enchanted books, could leave both a direct and a stored enchantment.
      // Remove only the old translatable Minecraft lines; normal player lore
      // is intentionally preserved.
      if (this.isBookInput(result)) {
         lore.removeIf(this::isLegacyMinecraftEnchantmentLore);
      }

      int generatedEnchantLines = 0;
      if (this.hasEnchantmentAboveVanillaMaximum(result)) {
         meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
         if (meta instanceof EnchantmentStorageMeta) {
            meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
         }
         List<Map.Entry<Enchantment, Integer>> displayEntries = new ArrayList<>(this.enchantments(result).entrySet());
         displayEntries.sort((first, second) -> first.getKey().getKey().toString()
            .compareTo(second.getKey().getKey().toString()));
         for (Map.Entry<Enchantment, Integer> entry : displayEntries) {
            lore.add(this.enchantmentDisplay(entry.getKey(), entry.getValue()));
            generatedEnchantLines++;
         }
         meta.getPersistentDataContainer().set(this.advancedEnchantDisplayKey,
            org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
         meta.getPersistentDataContainer().set(this.advancedEnchantLoreCountKey,
            org.bukkit.persistence.PersistentDataType.INTEGER, generatedEnchantLines);
      }

      lore.add(Component.text("必要XP: " + xpCost + " / 必要MP: " + mpCost));
      meta.getPersistentDataContainer().set(this.advancedCostDisplayKey,
         org.bukkit.persistence.PersistentDataType.BYTE, (byte)1);
      meta.lore(lore);
      result.setItemMeta(meta);
   }

   private Component enchantmentDisplay(Enchantment enchantment, int level) {
      String key = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
      return Component.translatable("enchantment.minecraft." + key)
         .append(Component.text(" " + AdvancedAnvilRules.toRoman(level)));
   }

   private String toRoman(int level) {
      return AdvancedAnvilRules.toRoman(level);
   }

   private void removeGeneratedEnchantLore(ItemMeta meta, List<Component> lore) {
      Integer count = meta.getPersistentDataContainer().get(this.advancedEnchantLoreCountKey,
         org.bukkit.persistence.PersistentDataType.INTEGER);
      if (count != null) {
         for (int index = 0; index < count && !lore.isEmpty(); index++) {
            lore.remove(lore.size() - 1);
         }
         meta.getPersistentDataContainer().remove(this.advancedEnchantLoreCountKey);
         meta.getPersistentDataContainer().remove(this.advancedEnchantDisplayKey);
      }
   }

   private void removeTransientCostLore(ItemMeta meta, List<Component> lore) {
      Byte marker = meta.getPersistentDataContainer().get(this.advancedCostDisplayKey,
         org.bukkit.persistence.PersistentDataType.BYTE);
      if (marker != null && marker != 0 && !lore.isEmpty()) {
         lore.remove(lore.size() - 1);
         meta.getPersistentDataContainer().remove(this.advancedCostDisplayKey);
      }
   }

   private boolean isLegacyMinecraftEnchantmentLore(Component component) {
      String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
         .serialize(component).trim().toLowerCase(Locale.ROOT);
      if (plain.startsWith("enchantment.minecraft.")
         || "minecraft:enchanted_book".equals(plain)
         || "item.minecraft.enchanted_book".equals(plain)) {
         return true;
      }
      if (component instanceof net.kyori.adventure.text.TranslatableComponent translatable) {
         String key = translatable.key().toLowerCase(Locale.ROOT);
         if (key.startsWith("enchantment.minecraft.")
            || "minecraft:enchanted_book".equals(key)
            || "item.minecraft.enchanted_book".equals(key)) {
            return true;
         }
      }
      for (Component child : component.children()) {
         if (this.isLegacyMinecraftEnchantmentLore(child)) {
            return true;
         }
      }
      return false;
   }

   private void removeTransientCostDisplay(ItemStack item) {
      if (this.isEmpty(item) || !item.hasItemMeta()) {
         return;
      }
      ItemMeta meta = item.getItemMeta();
      Byte marker = meta.getPersistentDataContainer().get(this.advancedCostDisplayKey,
         org.bukkit.persistence.PersistentDataType.BYTE);
      if (marker == null || marker == 0) {
         return;
      }
      List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
      if (!lore.isEmpty()) {
         lore.remove(lore.size() - 1);
      }
      meta.getPersistentDataContainer().remove(this.advancedCostDisplayKey);
      meta.lore(lore);
      item.setItemMeta(meta);
   }

   private void configureMaximumRepairCost(AnvilView view) {
      view.setMaximumRepairCost(this.maximumRepairCost());
   }

   private boolean isFeatureAllowed(World world) {
      if (!this.plugin.getConfig().getBoolean("advanced-enchanting.enabled", true) || world == null) {
         return false;
      }

      for (String configured : this.plugin.getConfig().getStringList("advanced-enchanting.allowed-worlds")) {
         if (configured != null && configured.trim().equalsIgnoreCase(world.getName())) {
            return true;
         }
      }
      return false;
   }

   private int maximumRepairCost() {
      int configured = this.plugin.getConfig().getInt("advanced-enchanting.maximum-level-cost", 1000000);
      return Math.max(40, Math.min(Integer.MAX_VALUE, configured));
   }

   private int maximumEnchantmentLevel() {
      int configured = this.plugin.getConfig().getInt("advanced-enchanting.max-enchantment-level", 20);
      return Math.max(1, Math.min(255, configured));
   }

   private int mpCost(int xpCost, ItemStack result, ItemStack left) {
      int perLevel = Math.max(0, this.plugin.getConfig().getInt("advanced-enchanting.mp-cost-per-level", 1));
      int multiplier = 1;
      Map<Enchantment, Integer> before = this.enchantments(left);
      for (Map.Entry<Enchantment, Integer> entry : this.enchantments(result).entrySet()) {
         int oldLevel = before.getOrDefault(entry.getKey(), 0);
         if (entry.getValue() > oldLevel && entry.getValue() > entry.getKey().getMaxLevel()) {
            multiplier = Math.max(multiplier, this.mpCostMultiplier(entry.getKey()));
         }
      }
      return AdvancedAnvilRules.mpCost(xpCost, perLevel, multiplier);
   }

   private int enchantmentLevelLimit(Enchantment enchantment) {
      int global = this.maximumEnchantmentLevel();
      if (enchantment == null) return global;
      String key = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
      int configured = this.plugin.getConfig().getInt("advanced-enchanting.enchantment-level-limits." + key, global);
      return Math.max(1, Math.min(global, configured));
   }

   private int mpCostMultiplier(Enchantment enchantment) {
      if (enchantment == null) return 1;
      String key = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
      return Math.max(1, this.plugin.getConfig().getInt("advanced-enchanting.mp-cost-multipliers." + key, 1));
   }

   private boolean hasEnchantmentAboveVanillaMaximum(ItemStack item) {
      if (this.isEmpty(item) || !item.hasItemMeta()) {
         return false;
      }

      ItemMeta meta = item.getItemMeta();
      for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
         if (entry.getValue() > entry.getKey().getMaxLevel()) {
            return true;
         }
      }
      if (meta instanceof EnchantmentStorageMeta stored) {
         for (Map.Entry<Enchantment, Integer> entry : stored.getStoredEnchants().entrySet()) {
            if (entry.getValue() > entry.getKey().getMaxLevel()) {
               return true;
            }
         }
      }
      return false;
   }

   private int mergeEnchantments(ItemStack result, ItemStack left, Map<Enchantment, Integer> incoming) {
      ItemMeta meta = result.getItemMeta();
      if (meta == null) {
         return 0;
      }

      int changed = 0;
      int maxLevel = this.maximumEnchantmentLevel();
      Map<Enchantment, Integer> existing = this.enchantments(left);
      Map<Enchantment, Integer> acceptedIncoming = new HashMap<>();
      for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
         if (entry.getKey() != null && !this.isBlockedEnchantment(entry.getKey())) {
            acceptedIncoming.put(entry.getKey(), entry.getValue());
         }
      }

      // Both item+item and item+book/book+book arrive here. The normalized
      // maps are merged once, then written to the correct metadata type. This
      // is deliberately independent of the vanilla result, which may contain
      // a second direct enchantment when the right input is a book.
      Map<Enchantment, Integer> merged = AdvancedAnvilRules.mergeEnchantments(existing, acceptedIncoming, maxLevel);
      for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
         Enchantment enchantment = entry.getKey();
         int existingLevel = existing.getOrDefault(enchantment, 0);
         int mergedLevel = Math.max(1, Math.min(Math.max(existingLevel, this.enchantmentLevelLimit(enchantment)), entry.getValue()));
         if (mergedLevel <= existingLevel) {
            continue;
         }

         if (meta instanceof EnchantmentStorageMeta stored) {
            stored.addStoredEnchant(enchantment, mergedLevel, true);
         } else {
            meta.addEnchant(enchantment, mergedLevel, true);
         }
         changed++;
      }

      if (changed > 0) {
         result.setItemMeta(meta);
      }
      return changed;
   }

   private void resetResultEnchantments(ItemStack result, ItemStack left) {
      ItemMeta meta = result.getItemMeta();
      if (meta == null) {
         return;
      }

      for (Enchantment enchantment : new HashSet<>(meta.getEnchants().keySet())) {
         meta.removeEnchant(enchantment);
      }
      if (meta instanceof EnchantmentStorageMeta stored) {
         // Books must have one canonical representation. Remove stale hide
         // flags left by the old renderer so normal-level books still show
         // their native enchantment tooltip after a subsequent merge.
         meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_STORED_ENCHANTS);
         for (Enchantment enchantment : new HashSet<>(stored.getStoredEnchants().keySet())) {
            stored.removeStoredEnchant(enchantment);
         }
      }

      for (Map.Entry<Enchantment, Integer> entry : this.enchantments(left).entrySet()) {
         int level = Math.max(1, Math.min(this.maximumEnchantmentLevel(), entry.getValue()));
         if (meta instanceof EnchantmentStorageMeta stored) {
            stored.addStoredEnchant(entry.getKey(), level, true);
         } else {
            meta.addEnchant(entry.getKey(), level, true);
         }
      }
      result.setItemMeta(meta);
   }

   private int fallbackCost(ItemStack left, ItemStack right, Map<Enchantment, Integer> incoming, ItemStack result) {
      int prior = Math.max(this.repairPenalty(left), this.repairPenalty(right));
      long cost = prior <= 0 ? 1L : (long)prior * 2L + 1L;
      ItemMeta resultMeta = result.getItemMeta();
      for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
         if (this.isBlockedEnchantment(entry.getKey())) {
            continue;
         }
         int before = this.enchantmentLevel(left, entry.getKey());
         int after = resultMeta == null ? entry.getValue() : resultMeta.getEnchantLevel(entry.getKey());
         if (resultMeta instanceof EnchantmentStorageMeta stored) {
            after = stored.getStoredEnchantLevel(entry.getKey());
         }
         cost += Math.max(1, after - before);
      }
      return (int)Math.min(Integer.MAX_VALUE, cost);
   }

   private int enchantmentLevel(ItemStack item, Enchantment enchantment) {
      if (this.isEmpty(item) || !item.hasItemMeta()) {
         return 0;
      }
      ItemMeta meta = item.getItemMeta();
      if (meta instanceof EnchantmentStorageMeta stored) {
         // Include the direct form only for compatibility with books created
         // by the earlier buggy implementation. New books use stored
         // enchantments exclusively.
         return Math.max(stored.getStoredEnchantLevel(enchantment), stored.getEnchantLevel(enchantment));
      }
      return meta.getEnchantLevel(enchantment);
   }

   private void applyRepairPenalty(ItemStack result, ItemStack left, ItemStack right) {
      if (!(result.getItemMeta() instanceof Repairable repairable)) {
         return;
      }

      int prior = Math.max(this.repairPenalty(left), this.repairPenalty(right));
      long penalty = prior <= 0 ? 0L : (long)prior * 2L + 1L;
      repairable.setRepairCost((int)Math.min(Integer.MAX_VALUE, penalty));
      result.setItemMeta(repairable);
   }

   private int repairPenalty(ItemStack item) {
      if (item == null || !item.hasItemMeta() || !(item.getItemMeta() instanceof Repairable repairable)) {
         return 0;
      }
      return Math.max(0, repairable.getRepairCost());
   }

   private Map<Enchantment, Integer> enchantments(ItemStack item) {
      if (this.isEmpty(item) || !item.hasItemMeta()) {
         return Map.of();
      }

      ItemMeta meta = item.getItemMeta();
      Map<Enchantment, Integer> result = new HashMap<>();
      for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
         result.merge(entry.getKey(), entry.getValue(), Math::max);
      }
      if (meta instanceof EnchantmentStorageMeta stored) {
         for (Map.Entry<Enchantment, Integer> entry : stored.getStoredEnchants().entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), Math::max);
         }
      }
      return result;
   }

   private boolean isBookInput(ItemStack item) {
      return !this.isEmpty(item)
         && (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK);
   }

   private boolean isExcluded(ItemStack item) {
      if (this.isEmpty(item) || !item.hasItemMeta()) {
         return false;
      }

      PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
      String id = MifronPdc.get(container, this.currentItemKey, org.bukkit.persistence.PersistentDataType.STRING);
      if (id != null) {
         Set<String> configured = new java.util.HashSet<>();
         for (String configuredId : this.plugin.getConfig().getStringList("advanced-enchanting.excluded-item-ids")) {
            if (configuredId != null && !configuredId.isBlank()) {
               configured.add(configuredId.trim().toLowerCase(Locale.ROOT));
            }
         }
         String normalizedId = id.toLowerCase(Locale.ROOT);
         if (configured.contains(normalizedId) || DEFAULT_EXCLUDED_IDS.contains(normalizedId)) {
            return true;
         }
      }

      if (this.hasBoolean(container, this.currentFfaItemKey, this.legacyFfaItemKey)
         || this.hasBoolean(container, this.currentFieldItemKey, this.legacyFieldItemKey)) {
         return true;
      }

      for (NamespacedKey key : container.getKeys()) {
         String namespace = key.getNamespace().toLowerCase(Locale.ROOT);
         if ("mifron".equals(namespace) || "minerva".equals(namespace)) {
            String keyName = key.getKey().toLowerCase(Locale.ROOT);
            if (keyName.startsWith("ui_") || keyName.contains("shop") || keyName.contains("wand")
               || keyName.contains("merchant") || keyName.contains("athletic") || keyName.contains("ffa_")
               || keyName.contains("offer") || keyName.contains("reincarnation") || "item".equals(keyName)) {
               return true;
            }
         }
      }
      return false;
   }

   private boolean hasBoolean(PersistentDataContainer container, NamespacedKey current, NamespacedKey legacy) {
      return container.has(current, org.bukkit.persistence.PersistentDataType.BOOLEAN)
         || container.has(legacy, org.bukkit.persistence.PersistentDataType.BOOLEAN);
   }

   private boolean isBlockedEnchantment(Enchantment enchantment) {
      String key = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
      for (String configured : this.plugin.getConfig().getStringList("advanced-enchanting.blocked-enchantments")) {
         if (configured != null && configured.trim().toLowerCase(Locale.ROOT).equals(key)) {
            return true;
         }
      }
      return false;
   }

   private boolean isTargetMaterial(Material material) {
      if (material == null || material == Material.AIR) {
         return false;
      }

      Set<String> blocked = this.normalizedMaterialNames("advanced-enchanting.blocked-materials");
      if (blocked.contains(material.name().toLowerCase(Locale.ROOT))) {
         return false;
      }
      Set<String> allowed = this.normalizedMaterialNames("advanced-enchanting.allowed-materials");
      return allowed.isEmpty() || allowed.contains(material.name().toLowerCase(Locale.ROOT));
   }

   private Set<String> normalizedMaterialNames(String path) {
      Set<String> names = new java.util.HashSet<>();
      for (String configured : this.plugin.getConfig().getStringList(path)) {
         if (configured != null && !configured.isBlank()) {
            Material material = Material.matchMaterial(configured.trim());
            if (material != null) {
               names.add(material.name().toLowerCase(Locale.ROOT));
            }
         }
      }
      return names;
   }

   private boolean isEmpty(ItemStack item) {
      return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
   }

   private boolean isSameResult(ItemStack actual, ItemStack expected) {
      return !this.isEmpty(actual) && expected != null && actual.isSimilar(expected);
   }

   private record PendingResult(ItemStack result, int xpCost, int mpCost) {
   }
}
