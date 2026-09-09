package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

final class EnchantMaxDisplayFeature implements Listener {
   private static final String MARKER = ChatColor.DARK_GRAY + "-MAX";
   private final Mifron plugin;

   EnchantMaxDisplayFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEnchant(EnchantItemEvent event) {
      plugin.getServer().getScheduler().runTask(plugin, () -> this.apply(event.getItem()));
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onAnvil(PrepareAnvilEvent event) {
      this.apply(event.getResult());
   }

   void apply(ItemStack item) {
      if (item == null || !item.hasItemMeta()) {
         return;
      }
      ItemMeta meta = item.getItemMeta();
      Map<Enchantment, Integer> enchants = meta instanceof EnchantmentStorageMeta stored
         ? stored.getStoredEnchants()
         : meta.getEnchants();
      if (enchants.isEmpty()) {
         return;
      }
      List<String> extra = new ArrayList<>();
      for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
         int vanillaMax = entry.getKey().getMaxLevel();
         if (entry.getValue() >= vanillaMax) {
            extra.add(ChatColor.LIGHT_PURPLE + displayName(entry.getKey()) + " " + toRoman(entry.getValue()) + " " + MARKER);
         }
      }
      if (extra.isEmpty()) {
         return;
      }
      List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
      lore.removeIf(line -> line != null && line.contains("-MAX"));
      lore.addAll(0, extra);
      meta.setLore(lore);
      item.setItemMeta(meta);
   }

   private static String displayName(Enchantment enchantment) {
      String key = enchantment.getKey().getKey().replace('_', ' ');
      if (key.isEmpty()) {
         return "Enchantment";
      }
      return Character.toUpperCase(key.charAt(0)) + key.substring(1);
   }

   private static String toRoman(int value) {
      int[] nums = {100, 90, 50, 40, 10, 9, 5, 4, 1};
      String[] romans = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
      StringBuilder out = new StringBuilder();
      int remaining = Math.max(1, value);
      for (int i = 0; i < nums.length; i++) {
         while (remaining >= nums[i]) {
            out.append(romans[i]);
            remaining -= nums[i];
         }
      }
      return out.toString();
   }
}
