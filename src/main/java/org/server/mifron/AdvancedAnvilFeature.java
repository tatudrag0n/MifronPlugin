package org.server.mifron;

import java.util.HashMap;
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
   private final Map<Inventory, PendingResult> pendingResults = new WeakHashMap<>();

   AdvancedAnvilFeature(Mifron plugin) {
      this.plugin = plugin;
      this.currentItemKey = new NamespacedKey(plugin, "item");
      this.currentFfaItemKey = new NamespacedKey(plugin, "ffa_item");
      this.legacyFfaItemKey = new NamespacedKey("minerva", "ffa_item");
      this.currentFieldItemKey = new NamespacedKey(plugin, "ffa_field_item");
      this.legacyFieldItemKey = new NamespacedKey("minerva", "ffa_field_item");
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
      ItemStack result = vanillaResult == null ? left.clone() : vanillaResult.clone();
      int changed = this.mergeEnchantments(result, incoming);
      if (changed == 0) {
         return;
      }

      int vanillaCost = Math.max(0, event.getView().getRepairCost());
      int totalCost = Math.max(vanillaCost, this.fallbackCost(left, right, incoming, result));
      totalCost = Math.min(totalCost, this.maximumRepairCost());
      boolean exceedsVanillaLevel = this.hasEnchantmentAboveVanillaMaximum(result);
      int xpCost = exceedsVanillaLevel ? 0 : totalCost;
      int mpCost = exceedsVanillaLevel ? this.mpCost(totalCost) : 0;
      event.getView().setRepairCost(xpCost);
      this.configureMaximumRepairCost(event.getView());
      if (vanillaResult == null) {
         this.applyRepairPenalty(result, left, right);
      }
      event.setResult(result);
      this.pendingResults.put(inventory, new PendingResult(result.clone(), xpCost, mpCost));
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
         }
      });
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

   private int mpCost(int xpCost) {
      int perLevel = Math.max(0, this.plugin.getConfig().getInt("advanced-enchanting.mp-cost-per-level", 1));
      long result = (long)Math.max(0, xpCost) * perLevel;
      return (int)Math.min(2000000000L, result);
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

   private int mergeEnchantments(ItemStack result, Map<Enchantment, Integer> incoming) {
      ItemMeta meta = result.getItemMeta();
      if (meta == null) {
         return 0;
      }

      int changed = 0;
      int maxLevel = this.maximumEnchantmentLevel();
      for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
         Enchantment enchantment = entry.getKey();
         if (enchantment == null || this.isBlockedEnchantment(enchantment)) {
            continue;
         }

         int incomingLevel = Math.max(1, Math.min(maxLevel, entry.getValue()));
         int existingLevel = meta.getEnchantLevel(enchantment);
         int mergedLevel = existingLevel == incomingLevel
            ? existingLevel + 1
            : Math.max(existingLevel, incomingLevel);
         mergedLevel = Math.max(1, Math.min(maxLevel, mergedLevel));
         if (mergedLevel <= existingLevel) {
            continue;
         }

         meta.addEnchant(enchantment, mergedLevel, true);
         changed++;
      }

      if (changed > 0) {
         result.setItemMeta(meta);
      }
      return changed;
   }

   private int fallbackCost(ItemStack left, ItemStack right, Map<Enchantment, Integer> incoming, ItemStack result) {
      int prior = Math.max(this.repairPenalty(left), this.repairPenalty(right));
      long cost = prior <= 0 ? 1L : (long)prior * 2L + 1L;
      ItemMeta resultMeta = result.getItemMeta();
      for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
         if (this.isBlockedEnchantment(entry.getKey())) {
            continue;
         }
         int before = left.getItemMeta() == null ? 0 : left.getItemMeta().getEnchantLevel(entry.getKey());
         int after = resultMeta == null ? entry.getValue() : resultMeta.getEnchantLevel(entry.getKey());
         cost += Math.max(1, after - before);
      }
      return (int)Math.min(Integer.MAX_VALUE, cost);
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
      Map<Enchantment, Integer> result = new HashMap<>(meta.getEnchants());
      if (meta instanceof EnchantmentStorageMeta stored) {
         result.putAll(stored.getStoredEnchants());
      }
      return result;
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
