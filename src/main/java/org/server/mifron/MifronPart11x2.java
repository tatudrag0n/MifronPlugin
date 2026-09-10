package org.server.mifron;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

abstract class MifronPart11x2 extends MifronPart11x1 {
   protected void fillStatusTab(Player player, Inventory inventory) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      inventory.setItem(10, this.mifron().named(Material.EXPERIENCE_BOTTLE, "\u00a7bMFL", List.of("\u00a77" + this.mifron().getMfl(player.getUniqueId()))));
      inventory.setItem(11, this.mifron().named(Material.EMERALD, "\u00a7a\u6240\u6301MP", List.of("\u00a77" + this.mifron().formatNumber(this.mifron().getEmeralds(player.getUniqueId())) + "MP")));
      inventory.setItem(12, this.mifron().named(Material.EMERALD_BLOCK, "\u00a7a\u7dcf\u7372\u5f97MP", List.of("\u00a77" + this.mifron().formatNumber(section.getInt("total-earned-emeralds", 0)) + "MP")));
      inventory.setItem(13, this.mifron().named(Material.CLOCK, "\u00a7e\u7dcf\u30d7\u30ec\u30a4\u6642\u9593", List.of("\u00a77" + this.mifron().formatPlayTime(section.getInt("total-minutes", 0)))));
      inventory.setItem(19, this.mifron().named(Material.IRON_SWORD, "\u00a7c\u7dcf\u30e2\u30d6\u8a0e\u4f10\u6570", List.of("\u00a77" + section.getInt("total-mob-kills", 0))));
   }

   protected void fillProgressTab(Player player, Inventory inventory, int page) {
      Set<String> completed = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      List<Advancement> advancements = this.mifron().trackableAdvancements();
      int pageSize = 27;
      int maxPage = Math.max(0, (advancements.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      inventory.setItem(45, this.mifron().named(Material.WRITABLE_BOOK, "\u00a7d\u9054\u6210\u9032\u6357\u6570", List.of("\u00a77" + this.mifron().countCompletedAdvancements(player) + "/" + advancements.size())));
      inventory.setItem(49, this.mifron().named(Material.PAPER, "\u00a7e\u30da\u30fc\u30b8", List.of("\u00a77" + (safePage + 1) + "/" + (maxPage + 1))));
      if (safePage > 0) inventory.setItem(48, this.mifron().actionItem(Material.ARROW, "\u00a7f\u524d\u306e\u30da\u30fc\u30b8", List.of(), "progress_page", String.valueOf(safePage - 1)));
      if (safePage < maxPage) inventory.setItem(50, this.mifron().actionItem(Material.ARROW, "\u00a7f\u6b21\u306e\u30da\u30fc\u30b8", List.of(), "progress_page", String.valueOf(safePage + 1)));
      int slot = 9;
      int from = safePage * pageSize;
      for (Advancement advancement : advancements.subList(from, Math.min(advancements.size(), from + pageSize))) {
         AdvancementDisplay display = advancement.getDisplay();
         boolean done = completed.contains(advancement.getKey().toString());
         Material icon = display != null && display.icon() != null ? display.icon().getType() : Material.PAPER;
         inventory.setItem(slot++, this.mifron().advancementItem(advancement, icon, done));
      }
   }

   protected int tabPage(String tab, String prefix) {
      String marker = prefix + ":";
      return tab == null || !tab.startsWith(marker) ? 0 : this.mifron().parsePositiveInt(tab.substring(marker.length()), 0);
   }

   protected void fillReincarnationTab(Player player, Inventory inventory) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      int next = section.getInt("reincarnations", 0) + 1;
      inventory.setItem(20, this.mifron().named(Material.EXPERIENCE_BOTTLE, "\u00a7b\u8ee2\u751f\u56de\u6570", List.of("\u00a77" + section.getInt("reincarnations", 0))));
      inventory.setItem(22, this.mifron().actionItem(Material.NETHER_STAR, "\u00a7d\u8ee2\u751f\u3059\u308b", List.of("\u00a77" + this.mifron().formatNumber(this.mifron().safeMultiply(10000, next)) + "MP"), "reincarnate_now", null));
      inventory.setItem(24, this.mifron().named(Material.GOLD_INGOT, "\u00a76\u8ee2\u751f\u30dc\u30fc\u30ca\u30b9", List.of("\u00a77+" + this.mifron().getReincarnationBonus(player.getUniqueId()) + "%")));
   }

   protected void fillTitlesTab(Player player, Inventory inventory, int page) {
      Set<String> completed = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      String selected = this.mifron().selectedTitle(player);
      List<Entry<String, TitleDefinition>> titles = this.titleDefinitions().entrySet().stream().sorted(Entry.comparingByKey()).toList();
      int pageSize = 27;
      int maxPage = Math.max(0, (titles.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      inventory.setItem(46, this.mifron().actionItem(Material.BARRIER, "\u00a7f\u79f0\u53f7\u306a\u3057", List.of(), "clear_title", null));
      int slot = 9;
      int from = safePage * pageSize;
      for (Entry<String, TitleDefinition> entry : titles.subList(from, Math.min(titles.size(), from + pageSize))) {
         boolean unlocked = this.hasTitle(completed, entry.getValue());
         inventory.setItem(slot++, this.mifron().actionItem(entry.getValue().icon(), (unlocked ? "\u00a7a" : "\u00a78") + (unlocked ? entry.getKey() : "???"), List.of(), unlocked ? "select_title" : "locked_title", entry.getKey()));
      }
   }

   protected boolean hasTitle(Set<String> completed, TitleDefinition definition) {
      return !definition.requiredAdvancements().isEmpty()
         ? definition.requiredAdvancements().stream().allMatch(requirement -> this.mifron().matchesTitleRequirement(completed, requirement))
         : completed.size() >= this.mifron().countTrackableAdvancements() && this.mifron().countTrackableAdvancements() > 0;
   }
}
