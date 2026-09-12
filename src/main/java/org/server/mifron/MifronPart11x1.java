package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

abstract class MifronPart11x1 extends MifronPart11 {
   protected void openStatusUi(Player player, String tab) {
      this.mifron().syncAdvancementState(player);
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text("\u00a72Mifron Status"));
      this.fillStatusTabs(inventory, tab);
      if (tab != null && tab.startsWith("kills")) this.mifron().fillKillsTab(player, inventory, this.mifron().tabPage(tab, "kills"));
      else if (tab != null && tab.startsWith("titles")) this.mifron().fillTitlesTab(player, inventory, this.mifron().tabPage(tab, "titles"));
      else if ("reincarnation".equals(tab)) this.mifron().fillReincarnationTab(player, inventory);
      else this.mifron().fillProgressTab(player, inventory, this.mifron().tabPage(tab == null ? "progress" : tab, "progress"));
      inventory.setItem(53, this.mifron().actionItem(Material.ARROW, "\u00a7f\u623b\u308b", List.of(), "friend_status_back", null));
      this.mifron().fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected void fillStatusTabs(Inventory inventory, String activeTab) {
      String normalizedTab = this.activeStatusTab(activeTab);
      inventory.setItem(0, switch (normalizedTab) {
         case "reincarnation" -> this.mifron().named(Material.NETHER_STAR, "\u00a76\u8ee2\u751f", List.of());
         case "titles" -> this.mifron().named(Material.NAME_TAG, "\u00a76\u79f0\u53f7", List.of());
         case "kills" -> this.mifron().named(Material.ZOMBIE_SPAWN_EGG, "\u00a76\u8a0e\u4f10", List.of());
         default -> this.mifron().named(Material.BOOK, "\u00a76\u9032\u6357", List.of());
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
      inventory.setItem(53, this.mifron().actionItem(Material.BARRIER, "\u00a7c\u9589\u3058\u308b", List.of(), "quest_close", null));
      this.mifron().fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected void fillQuestCategoryTab(Inventory inventory) {
      inventory.setItem(20, this.mifron().actionItem(Material.CLOCK, "\u00a7b\u30c7\u30a4\u30ea\u30fc", List.of(), "quest_category", QuestType.DAILY.key()));
      inventory.setItem(21, this.mifron().actionItem(Material.WRITABLE_BOOK, "\u00a7b\u30a6\u30a3\u30fc\u30af\u30ea\u30fc", List.of(), "quest_category", QuestType.WEEKLY.key()));
      inventory.setItem(23, this.mifron().actionItem(Material.MAP, "\u00a7b\u30de\u30f3\u30b9\u30ea\u30fc", List.of(), "quest_category", QuestType.MONTHLY.key()));
      inventory.setItem(24, this.mifron().actionItem(Material.NETHER_STAR, "\u00a7d\u30b9\u30da\u30b7\u30e3\u30eb", List.of(), "quest_category", QuestType.SPECIAL.key()));
   }

   protected void fillQuestListTab(Player player, Inventory inventory, QuestType type) {
      this.questService.ensurePeriods(player);
      inventory.setItem(45, this.mifron().named(Material.PAPER, "\u00a7e" + type.label() + "\u30af\u30a8\u30b9\u30c8", List.of("\u00a77" + this.questService.remainingTime(type))));
      inventory.setItem(48, this.mifron().actionItem(Material.ARROW, "\u00a7f\u30ab\u30c6\u30b4\u30ea\u306b\u623b\u308b", List.of(), "quest_home", null));
      int slot = 18;
      for (QuestDefinition definition : this.questService.visibleQuests(player, type)) {
         if (slot >= 45) break;
         inventory.setItem(slot++, this.questItem(player, definition));
      }
   }

   protected ItemStack questItem(Player player, QuestDefinition definition) {
      if (!this.questService.isUnlocked(player, definition)) {
         return this.mifron().actionItem(Material.GRAY_STAINED_GLASS_PANE, "\u00a78\uff1f\uff1f\uff1f", List.of(), "locked_quest", definition.id());
      }
      int progress = this.questService.progress(player, definition);
      boolean completed = this.questService.isCompleted(player, definition);
      boolean claimed = this.questService.isClaimed(player, definition);
      List<String> lore = new ArrayList<>();
      lore.add("\u00a77" + definition.condition());
      lore.add("\u00a77" + Math.min(progress, definition.required()) + " / " + definition.required());
      lore.add("\u00a77MP: " + this.mifron().formatNumber(this.questService.effectiveReward(player, definition)));
      ItemStack item = this.mifron().actionItem(claimed ? Material.LIME_STAINED_GLASS_PANE : definition.icon(), (claimed ? "\u00a78" : completed ? "\u00a76" : "\u00a7f") + definition.name(), lore, "quest_claim", definition.id());
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(completed && !claimed);
      item.setItemMeta(meta);
      return item;
   }

   protected void openProposalUi(Player player, int page) {
      List<String> ids = this.proposalManager.pendingIds();
      int pageSize = 45;
      int maxPage = Math.max(0, (ids.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      Inventory inventory = Bukkit.createInventory(player, 54, Component.text(PROPOSAL_UI_TITLE));
      int from = safePage * pageSize;
      for (int index = from; index < Math.min(ids.size(), from + pageSize); index++) {
         inventory.setItem(index - from, this.proposalItem(player, ids.get(index), safePage));
      }
      inventory.setItem(49, this.mifron().named(Material.PAPER, "\u00a7e\u63d0\u6848\u4e00\u89a7", List.of("\u00a77" + (safePage + 1) + "/" + (maxPage + 1), "\u00a77\u63d0\u6848\u6570: " + ids.size())));
      if (safePage > 0) inventory.setItem(48, this.mifron().actionItem(Material.ARROW, "\u00a7f\u524d\u306e\u30da\u30fc\u30b8", List.of(), "proposal_page", String.valueOf(safePage - 1)));
      if (safePage < maxPage) inventory.setItem(50, this.mifron().actionItem(Material.ARROW, "\u00a7f\u6b21\u306e\u30da\u30fc\u30b8", List.of(), "proposal_page", String.valueOf(safePage + 1)));
      inventory.setItem(53, this.mifron().actionItem(Material.BARRIER, "\u00a7c\u9589\u3058\u308b", List.of(), "proposal_close", null));
      this.mifron().fillEmptyGuiSlots(inventory);
      player.openInventory(inventory);
   }

   protected ItemStack proposalItem(Player player, String id, int page) {
      ConfigurationSection section = this.proposalManager.pendingProposal(id);
      if (section == null) return this.mifron().named(Material.PAPER, "\u00a77" + id, List.of());
      boolean voted = this.proposalManager.hasVoted(id, player.getUniqueId());
      List<String> lore = new ArrayList<>();
      lore.add("\u00a77\u7a2e\u5225: " + this.proposalTypeLabel(section.getString("type", "other")));
      lore.add("\u00a77\u6295\u7968\u6570: \u00a7f" + this.proposalManager.voteCount(id) + (voted ? " \u00a7a(\u6295\u7968\u6e08\u307f)" : ""));
      List<String> description = this.proposalLore(section.getString("description", ""), 34, 8);
      if (!description.isEmpty()) {
         lore.add("");
         lore.addAll(description);
      }
      lore.add("");
      lore.add(voted ? "\u00a7e\u30af\u30ea\u30c3\u30af\u3067\u6295\u7968\u3092\u53d6\u308a\u6d88\u3057" : "\u00a7a\u30af\u30ea\u30c3\u30af\u3067\u8cdb\u6210\u6295\u7968");
      ItemStack item = this.mifron().actionItem(voted ? Material.LIME_DYE : Material.PAPER, (voted ? "\u00a7a" : "\u00a7f") + section.getString("name", id), lore, "proposal_vote", id + "|" + page);
      ItemMeta meta = item.getItemMeta();
      meta.setEnchantmentGlintOverride(voted);
      item.setItemMeta(meta);
      return item;
   }

   protected String proposalTypeLabel(String type) {
      return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
         case "quest" -> "\u00a7a\u30af\u30a8\u30b9\u30c8\u63d0\u6848";
         case "bug" -> "\u00a7c\u30d0\u30b0\u5831\u544a";
         case "feature" -> "\u00a7b\u6a5f\u80fd\u63d0\u6848";
         case "title" -> "\u00a7d\u79f0\u53f7\u63d0\u6848";
         case "custom_item" -> "\u00a76\u30a2\u30a4\u30c6\u30e0\u63d0\u6848";
         default -> "\u00a77\u305d\u306e\u4ed6";
      };
   }

   protected List<String> proposalLore(String text, int width, int maxLines) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isBlank()) return lines;
      boolean truncated = false;
      for (String raw : text.split("\\R")) {
         String current = raw;
         while (current.length() > width) {
            if (lines.size() >= maxLines) { truncated = true; break; }
            lines.add(current.substring(0, width));
            current = current.substring(width);
         }
         if (lines.size() >= maxLines) { truncated = true; break; }
         lines.add(current);
      }
      if (truncated) {
         if (lines.size() >= maxLines) lines.set(maxLines - 1, "\u00a77\u2026");
         else lines.add("\u00a77\u2026");
      }
      return lines;
   }

   protected QuestType questTypeFromTab(String tab) {
      String key = tab.startsWith("quests:") ? tab.substring("quests:".length()).toLowerCase(Locale.ROOT) : tab.toLowerCase(Locale.ROOT);
      for (QuestType type : QuestType.values()) if (type.key().equals(key)) return type;
      return QuestType.DAILY;
   }
}
