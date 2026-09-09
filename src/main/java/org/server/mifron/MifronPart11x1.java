package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

abstract class MifronPart11x1 extends MifronPart11 {
   protected void openStatusUi(Player player, String tab) {
      this.syncAdvancementState(player);
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text("\u00a72Mifron Status"));
      this.fillStatusTabs(inventory, tab);
      if (tab != null && tab.startsWith("kills")) this.fillKillsTab(player, inventory, this.tabPage(tab, "kills"));
      else if (tab != null && tab.startsWith("titles")) this.fillTitlesTab(player, inventory, this.tabPage(tab, "titles"));
      else if ("reincarnation".equals(tab)) this.fillReincarnationTab(player, inventory);
      else this.fillProgressTab(player, inventory, this.tabPage(tab == null ? "progress" : tab, "progress"));
      inventory.setItem(53, this.actionItem(Material.ARROW, "\u00a7f\u623b\u308b", List.of(), "friend_status_back", null));
      this.fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected void fillStatusTabs(Inventory inventory, String activeTab) {
      String normalizedTab = this.activeStatusTab(activeTab);
      inventory.setItem(0, switch (normalizedTab) {
         case "reincarnation" -> this.named(Material.NETHER_STAR, "\u00a76\u8ee2\u751f", List.of());
         case "titles" -> this.named(Material.NAME_TAG, "\u00a76\u79f0\u53f7", List.of());
         case "kills" -> this.named(Material.ZOMBIE_SPAWN_EGG, "\u00a76\u8a0e\u4f10", List.of());
         default -> this.named(Material.BOOK, "\u00a76\u9032\u6357", List.of());
      });
   }

   protected String activeStatusTab(String activeTab) {
      if (activeTab == null) return "progress";
      if (activeTab.startsWith("progress")) return "progress";
      if (activeTab.startsWith("titles")) return "titles";
      if (activeTab.startsWith("kills")) return "kills";
      return "reincarnation".equals(activeTab) ? "reincarnation" : "progress";
   }

   protected void openQuestUi(Player player, String tab) {
      this.questService.ensurePeriods(player);
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text(QUEST_UI_TITLE));
      if (tab == null || "categories".equals(tab)) this.fillQuestCategoryTab(inventory);
      else this.fillQuestListTab(player, inventory, this.questTypeFromTab(tab));
      inventory.setItem(53, this.actionItem(Material.BARRIER, "\u00a7c\u9589\u3058\u308b", List.of(), "quest_close", null));
      this.fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected void fillQuestCategoryTab(Inventory inventory) {
      inventory.setItem(20, this.actionItem(Material.CLOCK, "\u00a7b\u30c7\u30a4\u30ea\u30fc", List.of(), "quest_category", QuestType.DAILY.key()));
      inventory.setItem(21, this.actionItem(Material.WRITABLE_BOOK, "\u00a7b\u30a6\u30a3\u30fc\u30af\u30ea\u30fc", List.of(), "quest_category", QuestType.WEEKLY.key()));
      inventory.setItem(23, this.actionItem(Material.MAP, "\u00a7b\u30de\u30f3\u30b9\u30ea\u30fc", List.of(), "quest_category", QuestType.MONTHLY.key()));
      inventory.setItem(24, this.actionItem(Material.NETHER_STAR, "\u00a7d\u30b9\u30da\u30b7\u30e3\u30eb", List.of(), "quest_category", QuestType.SPECIAL.key()));
   }

   protected void fillQuestListTab(Player player, Inventory inventory, QuestType type) {
      this.questService.ensurePeriods(player);
      inventory.setItem(45, this.named(Material.PAPER, "\u00a7e" + type.label() + "\u30af\u30a8\u30b9\u30c8", List.of("\u00a77" + this.questService.remainingTime(type))));
      inventory.setItem(48, this.actionItem(Material.ARROW, "\u00a7f\u30ab\u30c6\u30b4\u30ea\u306b\u623b\u308b", List.of(), "quest_home", null));
      int slot = 18;
      for (QuestDefinition definition : this.questService.visibleQuests(player, type)) {
         if (slot >= 45) break;
         inventory.setItem(slot++, this.questItem(player, definition));
      }
   }

   protected ItemStack questItem(Player player, QuestDefinition definition) {
      if (!this.questService.isUnlocked(player, definition)) {
         return this.actionItem(Material.GRAY_STAINED_GLASS_PANE, "\u00a78\uff1f\uff1f\uff1f", List.of(), "locked_quest", definition.id());
      }
      int progress = this.questService.progress(player, definition);
      boolean completed = this.questService.isCompleted(player, definition);
      boolean claimed = this.questService.isClaimed(player, definition);
      List<String> lore = new ArrayList<>();
      lore.add("\u00a77" + definition.condition());
      lore.add("\u00a77" + Math.min(progress, definition.required()) + " / " + definition.required());
      lore.add("\u00a77MP: " + this.formatNumber(this.questService.effectiveReward(player, definition)));
      ItemStack item = this.actionItem(claimed ? Material.LIME_STAINED_GLASS_PANE : definition.icon(), (claimed ? "\u00a78" : completed ? "\u00a76" : "\u00a7f") + definition.name(), lore, "quest_claim", definition.id());
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(completed && !claimed);
      item.setItemMeta(meta);
      return item;
   }

   protected QuestType questTypeFromTab(String tab) {
      String key = tab.startsWith("quests:") ? tab.substring("quests:".length()).toLowerCase(Locale.ROOT) : tab.toLowerCase(Locale.ROOT);
      for (QuestType type : QuestType.values()) if (type.key().equals(key)) return type;
      return QuestType.DAILY;
   }
}
