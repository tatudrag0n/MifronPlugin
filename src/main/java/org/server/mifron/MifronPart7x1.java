package org.server.mifron;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart7x1 extends MifronPart7 {

   protected void normalizeMerchants() {
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (entity instanceof AbstractVillager villager && this.mifron().isMifronMerchant(entity)) {
               if (!"survival".equalsIgnoreCase(world.getName())) entity.remove();
               else {
                  villager.setAI(true);
                  villager.setInvulnerable(false);
                  // All merchants are rare merchants: convert legacy types and
                  // rebuild their offers as special-only 1+1.
                  String type = villager.getPersistentDataContainer().get(this.merchantTypeKey, org.bukkit.persistence.PersistentDataType.STRING);
                  if (!"rare".equals(type)) {
                     villager.getPersistentDataContainer().set(this.merchantTypeKey, org.bukkit.persistence.PersistentDataType.STRING, "rare");
                     this.mifron().rerollMerchant(villager);
                  } else {
                     this.mifron().purgeSpecialMerchantOffers(villager.getUniqueId(), type);
                  }
               }
            }
         }
      }
   }

   protected ItemStack createShopPurchasedItem(Material material, int amount) { return new ItemStack(material, amount); }
   protected int applyShopDiscount(Player player, int price) {
      int discount = Math.max(0, Math.min(95, this.mifron().getPlayerSection(player.getUniqueId()).getInt("shop-discount", 0)));
      return (int) Math.max(1L, Math.min(2000000000L, Math.max(1, price) * (100L - discount) / 100L));
   }
}
