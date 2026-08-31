package org.server.mifron;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

final class FfaKitStandManager {
   private static final String TAG = "mifron_ffa_kit_stand";
   private static final String SELECTOR_TAG = "mifron_ffa_kit_selector";
   private final Mifron plugin;
   private final FfaConfig config;
   private final NamespacedKey kitKey;
   private final NamespacedKey selectorKey;

   FfaKitStandManager(Mifron plugin, FfaConfig config) {
      this.plugin = plugin;
      this.config = config;
      this.kitKey = new NamespacedKey(plugin, "ffa_kit");
      this.selectorKey = new NamespacedKey(plugin, "ffa_kit_selector");
   }

   int createKitStands() {
      Location base = this.config.kitSelection();
      if (base == null) {
         base = this.config.center();
      }

      if (base == null) {
         return -1;
      }

      this.removeKitStands();
      Location location = base.clone();
      location.setYaw(base.getYaw());
      ArmorStand stand = (ArmorStand)location.getWorld().spawn(location, ArmorStand.class);
      hideSelectorLabel(stand);
      stand.setInvulnerable(true);
      stand.setGravity(false);
      stand.setArms(true);
      stand.setBasePlate(false);
      stand.setPersistent(true);
      stand.addScoreboardTag("mifron_ffa_kit_stand");
      stand.addScoreboardTag("mifron_ffa_kit_selector");
      stand.getPersistentDataContainer().set(this.selectorKey, PersistentDataType.BOOLEAN, true);
      FfaKit selected = this.selectedKit();
      selected.applyStandEquipment(stand.getEquipment(), this.config, this.plugin);
      this.saveStandLocation(location, selected);
      this.plugin.saveConfig();
      return 1;
   }

   int removeKitStands() {
      int removed = 0;

      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (this.isKitSelector(entity) || this.legacyKitFromEntity(entity) != null) {
               entity.remove();
               removed++;
            }
         }
      }

      return removed;
   }

   boolean isKitSelector(Entity entity) {
      if (!(entity instanceof ArmorStand)) {
         return false;
      } else {
         return Boolean.TRUE.equals(MifronPdc.get(entity.getPersistentDataContainer(), this.selectorKey, PersistentDataType.BOOLEAN))
            ? true
            : entity.getScoreboardTags().contains("mifron_ffa_kit_selector")
               || entity.getScoreboardTags().contains("mifron_ffa_kit_stand")
               || this.legacyKitFromEntity(entity) != null;
      }
   }

   void applySelectedKit(FfaKit kit) {
      this.plugin.getConfig().set("ffa.stands.selected-kit", kit.key());

      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (this.isKitSelector(entity) && entity instanceof ArmorStand stand) {
               hideSelectorLabel(stand);
               kit.applyStandEquipment(stand.getEquipment(), this.config, this.plugin);
            }
         }
      }

      this.plugin.saveConfig();
   }

   void hideSelectorLabels() {
      for (World world : this.plugin.getServer().getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (this.isKitSelector(entity) && entity instanceof ArmorStand stand) {
               hideSelectorLabel(stand);
            }
         }
      }
   }

   private static void hideSelectorLabel(ArmorStand stand) {
      stand.customName(null);
      stand.setCustomNameVisible(false);
   }

   FfaKit selectedKit() {
      FfaKit kit = FfaKit.fromKey(this.plugin.getConfig().getString("ffa.stands.selected-kit", FfaKit.SWORD.key()));
      return kit != null && kit.isActive(this.config) ? kit : FfaKit.SWORD;
   }

   private FfaKit legacyKitFromEntity(Entity entity) {
      if (!(entity instanceof ArmorStand)) {
         return null;
      }

      String kit = (String)MifronPdc.get(entity.getPersistentDataContainer(), this.kitKey, PersistentDataType.STRING);
      if (kit == null && entity.getScoreboardTags().contains("mifron_ffa_kit_stand")) {
         kit = entity.getScoreboardTags()
            .stream()
            .filter(tag -> tag.startsWith("mifron_ffa_kit="))
            .map(tag -> tag.substring("mifron_ffa_kit=".length()))
            .findFirst()
            .orElse(null);
      }

      return FfaKit.fromKey(kit);
   }

   void playClick(Location location) {
      if (location != null && location.getWorld() != null) {
         location.getWorld().playSound(location, Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
      }
   }

   private void saveStandLocation(Location location, FfaKit selected) {
      FileConfiguration file = this.plugin.getConfig();
      String path = "ffa.stands.selector.";
      file.set(path + "world", location.getWorld().getName());
      file.set(path + "x", location.getX());
      file.set(path + "y", location.getY());
      file.set(path + "z", location.getZ());
      file.set(path + "yaw", location.getYaw());
      file.set(path + "pitch", location.getPitch());
      file.set(path + "selected-kit", selected.key());
   }
}
