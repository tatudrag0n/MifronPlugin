package org.server.mifron;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.advancement.AdvancementDisplay.Frame;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

abstract class MifronPart12x1 extends MifronPart12 {
   protected ItemStack statusItem(Material material, String name, List<String> lore, boolean glint) {
      ItemStack item = this.mifron().named(material, name, lore);
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(glint);
      item.setItemMeta(meta);
      return item;
   }

   protected ItemStack advancementItem(Advancement advancement, Material icon, boolean done) {
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();
      ItemStack item = new ItemStack(done ? icon : Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = item.getItemMeta();
      Component title = display == null ? Component.text(advancement.getKey().getKey()) : display.title();
      meta.displayName(title.color(done ? this.advancementFrameColor(frame) : NamedTextColor.DARK_GRAY));
      List<Component> lore = new ArrayList<>();
      lore.add(Component.text(done ? "\u9054\u6210\u6e08" : "\u672a\u9054\u6210", done ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
      if (display != null && display.description() != null) {
         lore.add(display.description().color(done ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));
      }
      meta.lore(lore);
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      meta.setEnchantmentGlintOverride(done);
      item.setItemMeta(meta);
      return item;
   }

   protected NamedTextColor advancementFrameColor(Frame frame) {
      return switch (frame) {
         case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
         case GOAL -> NamedTextColor.GOLD;
         default -> NamedTextColor.GREEN;
      };
   }

   protected String formatPlayTime(int totalMinutes) {
      return (totalMinutes / 60) + "\u6642\u9593" + (totalMinutes % 60) + "\u5206";
   }

   protected int countCompletedAdvancements(Player player) {
      Set<String> completed = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      int count = 0;
      for (Advancement advancement : this.mifron().trackableAdvancements()) {
         if (this.mifron().shouldTrackAdvancement(advancement) && completed.contains(advancement.getKey().toString())) count++;
      }
      return count;
   }

   protected int countTrackableAdvancements() {
      return this.mifron().trackableAdvancements().size();
   }

   protected List<Advancement> trackableAdvancements() {
      List<Advancement> advancements = new ArrayList<>();
      Iterator<Advancement> iterator = Bukkit.advancementIterator();
      while (iterator.hasNext()) {
         Advancement advancement = iterator.next();
         if (this.mifron().shouldTrackAdvancement(advancement)) advancements.add(advancement);
      }
      advancements.sort((first, second) -> {
         int difficulty = Integer.compare(this.mifron().advancementDifficulty(first), this.mifron().advancementDifficulty(second));
         if (difficulty != 0) return difficulty;
         int category = Integer.compare(this.advancementCategoryOrder(first), this.advancementCategoryOrder(second));
         if (category != 0) return category;
         int path = Integer.compare(this.mifron().advancementPathOrder(first), this.mifron().advancementPathOrder(second));
         if (path != 0) return path;
         int frame = Integer.compare(this.advancementFrameOrder(first), this.advancementFrameOrder(second));
         return frame != 0 ? frame : first.getKey().toString().compareToIgnoreCase(second.getKey().toString());
      });
      return advancements;
   }

   protected int advancementFrameOrder(Advancement advancement) {
      AdvancementDisplay display = advancement.getDisplay();
      Frame frame = display == null ? Frame.TASK : display.frame();
      return switch (frame) {
         case CHALLENGE -> 2;
         case GOAL -> 1;
         case TASK -> 0;
      };
   }

   protected int advancementCategoryOrder(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String category = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
      return switch (category) {
         case "story" -> 0;
         case "nether" -> 1;
         case "end" -> 2;
         case "adventure" -> 3;
         case "husbandry" -> 4;
         default -> 9;
      };
   }
}
