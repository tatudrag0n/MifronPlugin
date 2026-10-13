package org.server.mifron;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Post-tutorial starter checklist. Goals complete either from explicit marks
 * (menu opened, sold an item, protected a chunk, claimed a quest) or from
 * existing lifetime counters (job picked, blocks mined). Each goal pays a
 * small MP reward once, claimed from the UI.
 */
final class StarterGoalsFeature {
   static final String UI_TITLE = "\u00a7aMifron Starter Goals";

   record Goal(String id, Material icon, String name, String description) {}

   static final List<Goal> GOALS = List.of(
      new Goal("menu", Material.NETHER_STAR, "\u30e1\u30cb\u30e5\u30fc\u3092\u958b\u304f", "\u30e1\u30cb\u30e5\u30fc\u30a2\u30a4\u30c6\u30e0\u3092\u53f3\u30af\u30ea\u30c3\u30af"),
      new Goal("job", Material.WOODEN_HOE, "\u30b8\u30e7\u30d6\u3092\u9078\u3076", "\u30e1\u30cb\u30e5\u30fc\u306e\u300c\u30b8\u30e7\u30d6\u300d\u304b\u3089\u9078\u629e"),
      new Goal("mine", Material.IRON_PICKAXE, "\u30d6\u30ed\u30c3\u30af\u309264\u500b\u63a1\u6398", "Survival\u3067\u6398\u3063\u3066\u307f\u3088\u3046"),
      new Goal("sell", Material.EMERALD, "\u30a2\u30a4\u30c6\u30e0\u3092\u58f2\u5374", "\u8cb7\u53d6\u68da\u306b\u30a2\u30a4\u30c6\u30e0\u3092\u6301\u3063\u3066\u53f3\u30af\u30ea\u30c3\u30af"),
      new Goal("protect", Material.SHIELD, "\u30c1\u30e3\u30f3\u30af\u4fdd\u8b77\u3092\u63d0\u6848", "/mf protect chunk"),
      new Goal("quest", Material.KNOWLEDGE_BOOK, "\u30af\u30a8\u30b9\u30c8\u5831\u916c\u3092\u53d7\u3051\u53d6\u308b", "\u30af\u30a8\u30b9\u30c8\u9054\u6210\u5f8c\u306b\u53d7\u53d6")
   );

   private final Mifron plugin;

   StarterGoalsFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   /** Records a goal as done (idempotent). */
   void mark(Player player, String goalId) {
      if (player == null || goalId == null) return;
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      if (section.getBoolean("starter-goals.done." + goalId, false)) return;
      section.set("starter-goals.done." + goalId, true);
      this.plugin.queueDataSave();
   }

   boolean isComplete(Player player, Goal goal) {
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      if (section.getBoolean("starter-goals.done." + goal.id(), false)) return true;
      return switch (goal.id()) {
         case "job" -> !"none".equalsIgnoreCase(section.getString("job.type", "none"));
         case "mine" -> StarterGoalsRules.miningComplete(section.getInt("total-blocks-broken", 0));
         default -> false;
      };
   }

   boolean isClaimed(Player player, Goal goal) {
      return this.plugin.getPlayerSection(player.getUniqueId()).getBoolean("starter-goals.claimed." + goal.id(), false);
   }

   int rewardMp() {
      return Math.max(0, this.plugin.getConfig().getInt("starter-goals.reward-mp", 300));
   }

   void open(Player player) {
      org.bukkit.inventory.Inventory inventory = Bukkit.createInventory(player, 27, Component.text(UI_TITLE));
      int slot = 10;
      for (Goal goal : GOALS) {
         boolean claimed = this.isClaimed(player, goal);
         StarterGoalsRules.GoalState state = StarterGoalsRules.state(this.isComplete(player, goal), claimed);
         List<String> lore = new java.util.ArrayList<>();
         lore.add(ChatColor.GRAY + goal.description());
         Material icon = goal.icon();
         String nameColor = ChatColor.WHITE.toString();
         switch (state) {
            case CLAIMED -> {
               lore.add(ChatColor.GREEN + "\u53d7\u53d6\u6e08\u307f");
               icon = Material.LIME_DYE;
               nameColor = ChatColor.GREEN.toString();
            }
            case CLAIMABLE -> {
               lore.add(ChatColor.YELLOW + "\u30af\u30ea\u30c3\u30af: +" + this.plugin.formatNumber(this.rewardMp()) + "MP \u3092\u53d7\u3051\u53d6\u308b");
               icon = Material.EMERALD;
               nameColor = ChatColor.YELLOW.toString();
            }
            case LOCKED -> lore.add(ChatColor.GRAY + "\u672a\u9054\u6210");
         }
         String action = state == StarterGoalsRules.GoalState.CLAIMABLE ? "goal_claim" : "goal_info";
         inventory.setItem(slot++, this.plugin.actionItem(icon, nameColor + goal.name(), lore, action, goal.id()));
      }
      inventory.setItem(26, this.plugin.actionItem(Material.OAK_DOOR, "\u00a7f\u30e1\u30cb\u30e5\u30fc\u306b\u623b\u308b", List.of(), "menu_back", null));
      for (int i = 0; i < inventory.getSize(); i++) {
         if (inventory.getItem(i) == null) inventory.setItem(i, this.plugin.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of()));
      }
      player.openInventory(inventory);
   }

   void claim(Player player, String goalId) {
      if (player == null || goalId == null) return;
      Goal goal = GOALS.stream().filter(candidate -> candidate.id().equals(goalId)).findFirst().orElse(null);
      if (goal == null) return;
      if (!this.isComplete(player, goal) || this.isClaimed(player, goal)) {
         this.open(player);
         return;
      }
      int reward = this.plugin.applyIncomeBonus(player.getUniqueId(), this.rewardMp());
      this.plugin.depositEmeralds(player.getUniqueId(), reward);
      this.plugin.getPlayerSection(player.getUniqueId()).set("starter-goals.claimed." + goal.id(), true);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "\u76ee\u6a19\u9054\u6210\u30dc\u30fc\u30ca\u30b9: +" + this.plugin.formatNumber(reward) + "MP");
      this.open(player);
   }
}
