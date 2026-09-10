package org.server.mifron;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

abstract class MifronPart12 extends MifronPart11x2 {
   protected boolean matchesTitleRequirement(Set<String> completed, String requirement) {
      for (String option : requirement.split("\\|")) {
         String key = option.trim();
         if (!key.isBlank() && (completed.contains(key) || completed.contains("minecraft:" + key))) return true;
      }
      return false;
   }

   protected void notifyUnlockedTitles(Player player, Set<String> completed) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      List<String> newlyUnlocked = new ArrayList<>();
      for (Entry<String, TitleDefinition> entry : this.titleDefinitions().entrySet()) {
         if (!notified.contains(entry.getKey()) && this.hasTitle(completed, entry.getValue())) {
            notified.add(entry.getKey());
            newlyUnlocked.add(entry.getKey());
         }
      }
      if (newlyUnlocked.isEmpty()) return;
      section.set("unlocked-titles", new ArrayList<>(notified));
      this.queueDataSave();
      newlyUnlocked.stream().sorted().forEach(title -> player.sendMessage("\u00a76\u79f0\u53f7\u3092\u7372\u5f97\u3057\u307e\u3057\u305f: \u00a7e" + title));
      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
   }

   public void unlockTitle(Player player, String title) {
      ConfigurationSection section = this.getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      if (notified.contains(title)) return;
      notified.add(title);
      section.set("unlocked-titles", new ArrayList<>(notified));
      this.queueDataSave();
      player.sendMessage("\u00a76\u79f0\u53f7\u3092\u7372\u5f97\u3057\u307e\u3057\u305f\uff1a\u00a7e" + title);
      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
   }

   protected void fillKillsTab(Player player, Inventory inventory, int page) {
      Set<String> killed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("killed-mobs"));
      List<EntityType> mobs = this.killableMobTypes();
      int pageSize = 27;
      int maxPage = Math.max(0, (mobs.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      inventory.setItem(45, this.named(Material.IRON_SWORD, "\u00a7c\u8a0e\u4f10\u6e08\u307fMob", List.of("\u00a77" + mobs.stream().filter(type -> killed.contains(type.name())).count() + "/" + mobs.size())));
      int slot = 9;
      int from = safePage * pageSize;
      for (EntityType type : mobs.subList(from, Math.min(mobs.size(), from + pageSize))) {
         Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
         boolean done = killed.contains(type.name());
         inventory.setItem(slot++, this.statusItem(done && egg != null ? egg : Material.GRAY_STAINED_GLASS_PANE, (done ? "\u00a7a" : "\u00a78") + this.mobDisplayName(type), List.of(done ? "\u00a7a\u8a0e\u4f10\u6e08" : "\u00a77\u672a\u8a0e\u4f10"), done));
      }
   }

   protected List<EntityType> killableMobTypes() {
      List<EntityType> mobs = new ArrayList<>();
      for (EntityType type : EntityType.values()) {
         if (type.isAlive() && type != EntityType.PLAYER && Material.matchMaterial(type.name() + "_SPAWN_EGG") != null) mobs.add(type);
      }
      mobs.sort((a, b) -> {
         int difficulty = Integer.compare(this.killDifficulty(a), this.killDifficulty(b));
         return difficulty != 0 ? difficulty : a.name().compareToIgnoreCase(b.name());
      });
      return mobs;
   }

   protected int killDifficulty(EntityType type) {
      return switch (type) {
         case CHICKEN, COW, PIG, SHEEP, RABBIT -> 1;
         case ZOMBIE, SKELETON, SPIDER, CREEPER -> 3;
         case BLAZE, WITCH, GUARDIAN -> 4;
         case ENDERMAN, WITHER_SKELETON, SHULKER -> 5;
         case WARDEN, WITHER, ENDER_DRAGON -> 6;
         default -> 3;
      };
   }

   protected String mobDisplayName(EntityType type) {
      return switch (type) {
         case ZOMBIE -> "\u30be\u30f3\u30d3"; case SKELETON -> "\u30b9\u30b1\u30eb\u30c8\u30f3"; case CREEPER -> "\u30af\u30ea\u30fc\u30d1\u30fc";
         case ENDERMAN -> "\u30a8\u30f3\u30c0\u30fc\u30de\u30f3"; case WITHER -> "\u30a6\u30a3\u30b6\u30fc"; case ENDER_DRAGON -> "\u30a8\u30f3\u30c0\u30fc\u30c9\u30e9\u30b4\u30f3";
         case WARDEN -> "\u30a6\u30a9\u30fc\u30c7\u30f3"; default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
      };
   }
}
