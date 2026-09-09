package org.server.mifron;

import org.bukkit.NamespacedKey;

final class ShopKeys {
   final NamespacedKey shopType;
   final NamespacedKey owner;
   final NamespacedKey placed;
   final NamespacedKey activity;
   final NamespacedKey action;
   final NamespacedKey[] prices;

   ShopKeys(Mifron plugin) {
      this.shopType = new NamespacedKey(plugin, "shop_type");
      this.owner = new NamespacedKey(plugin, "shop_owner");
      this.placed = new NamespacedKey(plugin, "shop_placement_time");
      this.activity = new NamespacedKey(plugin, "shop_last_activity");
      this.action = new NamespacedKey(plugin, "shop_ui_action");
      this.prices = new NamespacedKey[] {
         new NamespacedKey(plugin, "shop_price_0"),
         new NamespacedKey(plugin, "shop_price_1"),
         new NamespacedKey(plugin, "shop_price_2")
      };
   }
}
