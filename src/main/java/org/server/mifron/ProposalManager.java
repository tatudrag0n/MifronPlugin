package org.server.mifron;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ProposalManager {
   private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
   private final Mifron plugin;
   private File file;
   private YamlConfiguration data;

   ProposalManager(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.file = new File(this.plugin.getDataFolder(), "proposals.yml");
      if (!this.file.exists()) {
         this.data = new YamlConfiguration();
         this.data.set("proposals.pending", new LinkedHashMap());
         this.data.set("proposals.approved", new LinkedHashMap());
         this.data.set("proposals.rejected", new LinkedHashMap());
         this.save();
      } else {
         this.data = YamlConfiguration.loadConfiguration(this.file);
      }
   }

   boolean handleCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.admin")) {
         sender.sendMessage(ChatColor.RED + "権限がありません。");
         return true;
      }

      if (args.length < 2) {
         sender.sendMessage(ChatColor.YELLOW + "/mf proposal list|review|approve|forceapprove|reject|reload");
         return true;
      }

      this.ensureLoaded();

      return switch (args[1].toLowerCase(Locale.ROOT)) {
         case "list" -> this.list(sender);
         case "review" -> this.review(sender, args);
         case "approve" -> this.approve(sender, args);
         case "forceapprove", "force-approve" -> this.forceApprove(sender, args);
         case "reject" -> this.reject(sender, args);
         case "reload" -> {
            this.load();
            sender.sendMessage(ChatColor.GREEN + "Proposal を再読み込みしました。");
            yield true;
         }
         default -> {
            sender.sendMessage(ChatColor.YELLOW + "/mf proposal list|review|approve|forceapprove|reject|reload");
            yield true;
         }
      };
   }

   List<String> tabComplete(String[] args) {
      if (args.length == 2) {
         return List.of("list", "review", "approve", "forceapprove", "reject", "reload");
      } else if (args.length == 3 && List.of("review", "approve", "forceapprove", "force-approve", "reject").contains(args[1].toLowerCase(Locale.ROOT))) {
         ConfigurationSection pending = this.data == null ? null : this.data.getConfigurationSection("proposals.pending");
         return pending == null ? List.of() : new ArrayList<>(pending.getKeys(false));
      } else {
         return List.of();
      }
   }

   private boolean list(CommandSender sender) {
      ConfigurationSection pending = this.data.getConfigurationSection("proposals.pending");
      if (pending != null && !pending.getKeys(false).isEmpty()) {
         sender.sendMessage(ChatColor.GOLD + "Pending proposals:");

         for (String id : pending.getKeys(false).stream().sorted().toList()) {
            sender.sendMessage(
               ChatColor.YELLOW
                  + id
                  + ChatColor.GRAY
                  + " - "
                  + this.data.getString("proposals.pending." + id + ".type", "unknown")
                  + " / "
                  + this.data.getString("proposals.pending." + id + ".name", "(no name)")
                  + " / votes="
                  + this.data.getInt("proposals.pending." + id + ".votes", 0)
            );
         }

         return true;
      } else {
         sender.sendMessage(ChatColor.YELLOW + "pending proposal はありません。");
         return true;
      }
   }

   private boolean review(CommandSender sender, String[] args) {
      ConfigurationSection section = this.pending(sender, args);
      if (section == null) {
         return true;
      }

      String id = args[2];
      sender.sendMessage(ChatColor.GOLD + "Proposal: " + id);
      sender.sendMessage(ChatColor.GRAY + "type: " + section.getString("type", "unknown"));
      sender.sendMessage(ChatColor.GRAY + "name: " + section.getString("name", "(no name)"));
      sender.sendMessage(ChatColor.GRAY + "description: " + section.getString("description", ""));
      sender.sendMessage(ChatColor.GRAY + "source: " + section.getString("source", ""));
      sender.sendMessage(ChatColor.GRAY + "discord-user-id: " + section.getString("discord-user-id", ""));
      sender.sendMessage(ChatColor.GRAY + "votes: " + section.getInt("votes", 0));
      return true;
   }

   private boolean approve(CommandSender sender, String[] args) {
      ConfigurationSection section = this.pending(sender, args);
      if (section == null) {
         return true;
      }

      String id = args[2];
      if (!this.applyProposal(sender, id, section)) {
         return true;
      }

      this.move("proposals.pending." + id, "proposals.approved." + id);
      this.data.set("proposals.approved." + id + ".approved-at", System.currentTimeMillis());
      this.save();
      this.plugin.saveConfig();
      sender.sendMessage(ChatColor.GREEN + "Proposal を承認し、設定へ反映しました: " + id);
      return true;
   }

   private boolean forceApprove(CommandSender sender, String[] args) {
      ConfigurationSection section = this.pending(sender, args);
      if (section == null) {
         return true;
      }
      String id = args[2];
      if (!this.applyProposal(sender, id, section)) {
         return true;
      }
      this.move("proposals.pending." + id, "proposals.approved." + id);
      String path = "proposals.approved." + id;
      this.data.set(path + ".approved-at", System.currentTimeMillis());
      this.data.set(path + ".forced-approval", true);
      this.data.set(path + ".forced-by", sender.getName());
      this.data.set(path + ".forced-reason", args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "管理者による強制承認");
      this.save();
      this.plugin.saveConfig();
      sender.sendMessage(ChatColor.GREEN + "Proposalを強制承認し、設定へ反映しました: " + id);
      return true;
   }

   private boolean reject(CommandSender sender, String[] args) {
      ConfigurationSection section = this.pending(sender, args);
      if (section == null) {
         return true;
      }

      String id = args[2];
      this.move("proposals.pending." + id, "proposals.rejected." + id);
      this.data.set("proposals.rejected." + id + ".rejected-at", System.currentTimeMillis());
      this.save();
      sender.sendMessage(ChatColor.YELLOW + "Proposal を却下しました: " + id);
      return true;
   }

   private ConfigurationSection pending(CommandSender sender, String[] args) {
      if (args.length < 3) {
         sender.sendMessage(ChatColor.RED + "/mf proposal " + args[1] + " <id>");
         return null;
      }

      ConfigurationSection section = this.data.getConfigurationSection("proposals.pending." + args[2]);
      if (section == null) {
         sender.sendMessage(ChatColor.RED + "pending proposal が見つかりません: " + args[2]);
      }

      return section;
   }

   private boolean applyProposal(CommandSender sender, String proposalId, ConfigurationSection section) {
      String type = section.getString("type", "").toLowerCase(Locale.ROOT).replace("-", "_");

      return switch (type) {
         case "title" -> this.applyTitle(sender, proposalId, section);
         case "custom_item", "customitem", "item" -> this.applyCustomItem(sender, proposalId, section);
         case "shop_item", "shopitem" -> {
            sender.sendMessage(ChatColor.RED + "shop_item proposal は廃止されました。ショップ価格表で管理してください。");
            yield false;
         }
         case "quest" -> this.applyQuest(sender, proposalId, section);
         default -> {
            sender.sendMessage(ChatColor.RED + "未対応の proposal type です: " + type);
            yield false;
         }
      };
   }

   private boolean applyTitle(CommandSender sender, String proposalId, ConfigurationSection section) {
      String id = this.safeId(section.getString("id", proposalId));
      String displayName = section.getString("display-name", section.getString("name", id));
      if (displayName != null && !displayName.isBlank()) {
         String path = "titles." + id;
         this.plugin.getConfig().set(path + ".display-name", displayName);
         this.plugin.getConfig().set(path + ".description", section.getString("description", ""));
         this.plugin.getConfig().set(path + ".icon", section.getString("icon", "name_tag"));
         if (section.contains("required-advancements")) {
            this.plugin.getConfig().set(path + ".required-advancements", section.getStringList("required-advancements"));
         }

         ConfigurationSection condition = section.getConfigurationSection("condition");
         if (condition != null) {
            for (Entry<String, Object> entry : condition.getValues(true).entrySet()) {
               this.plugin.getConfig().set(path + ".condition." + entry.getKey(), entry.getValue());
            }
         }

         return true;
      } else {
         sender.sendMessage(ChatColor.RED + "title proposal に name がありません。");
         return false;
      }
   }

   private boolean applyCustomItem(CommandSender sender, String proposalId, ConfigurationSection section) {
      String id = this.safeId(section.getString("id", proposalId));
      String materialName = section.getString("material", "paper");
      Material material = Material.matchMaterial(materialName);
      if (material != null && material.isItem()) {
         String path = "custom-items." + id;
         this.plugin.getConfig().set(path + ".material", material.name().toLowerCase(Locale.ROOT));
         String baseMaterial = section.getString("base-material", material.name());
         Material base = Material.matchMaterial(baseMaterial);
         if (base == null || !base.isItem()) {
            sender.sendMessage(ChatColor.RED + "custom item の base-material が不正です: " + baseMaterial);
            return false;
         }
         double conversionChance = Math.max(0.0, Math.min(0.01, section.getDouble("conversion-chance", 0.001)));
         this.plugin.getConfig().set(path + ".base-material", base.name().toLowerCase(Locale.ROOT));
         this.plugin.getConfig().set(path + ".conversion-chance", conversionChance);
         this.plugin.getConfig().set(path + ".conversion-enabled", section.getBoolean("conversion-enabled", true));
         this.plugin.getConfig().set(path + ".display-name", section.getString("display-name", section.getString("name", id)));
         this.plugin.getConfig().set(path + ".lore", section.getStringList("lore"));
         this.plugin.getConfig().set(path + ".glint", section.getBoolean("glint", false));
         this.plugin.getConfig().set(path + ".unbreakable", section.getBoolean("unbreakable", false));
         this.plugin.getConfig().set(path + ".source", section.getString("source", "proposal"));
         return true;
      } else {
         sender.sendMessage(ChatColor.RED + "custom item の material が不正です: " + materialName);
         return false;
      }
   }


   String submitQuest(org.bukkit.entity.Player player, QuestProposalFeature.Draft draft) {
      this.ensureLoaded();
      String id = this.safeId("q_" + player.getName() + "_" + Long.toString(System.currentTimeMillis(), 36));
      String path = "proposals.pending." + id;
      this.data.set(path + ".type", "quest");
      this.data.set(path + ".name", draft.name);
      this.data.set(path + ".quest-type", draft.type);
      this.data.set(path + ".condition", draft.condition);
      this.data.set(path + ".progress-key", draft.progressKey);
      this.data.set(path + ".required", draft.required);
      this.data.set(path + ".base-reward-mp", draft.reward);
      this.data.set(path + ".depends-on", draft.dependsOn);
      this.data.set(path + ".title-reward", draft.title);
      this.data.set(path + ".advancement", draft.advancement);
      this.data.set(path + ".source", player.getUniqueId().toString());
      this.data.set(path + ".author", player.getName());
      this.data.set(path + ".submitted-at", System.currentTimeMillis());
      this.save();
      return id;
   }

   private boolean applyQuest(CommandSender sender, String proposalId, ConfigurationSection section) {
      String questId = this.safeId(section.getString("id", proposalId));
      File questFile = new File(this.plugin.getDataFolder(), "quests.yml");
      YamlConfiguration quests = questFile.exists() ? YamlConfiguration.loadConfiguration(questFile) : new YamlConfiguration();
      String path = "quests." + questId;
      quests.set(path + ".type", section.getString("quest-type", "one_shot"));
      quests.set(path + ".name", section.getString("name", questId));
      quests.set(path + ".condition", section.getString("condition", ""));
      quests.set(path + ".base-reward-mp", Math.max(0, section.getInt("base-reward-mp", 10)));
      quests.set(path + ".progress-key", section.getString("progress-key", "manual"));
      quests.set(path + ".required", Math.max(1, section.getInt("required", 1)));
      quests.set(path + ".depends-on", section.getString("depends-on", ""));
      quests.set(path + ".title-reward", section.getString("title-reward", ""));
      quests.set(path + ".icon", "PAPER");
      quests.set(path + ".display", "player-proposal");
      try {
         quests.save(questFile);
      } catch (IOException e) {
         sender.sendMessage(ChatColor.RED + "quests.yml を保存できませんでした。");
         return false;
      }
      String title = section.getString("title-reward", "");
      if (title != null && !title.isBlank()) {
         this.plugin.getConfig().set("titles." + questId + ".display-name", title);
         this.plugin.getConfig().set("titles." + questId + ".description", "クエスト提案から追加");
      }
      this.plugin.getQuestService().load();
      sender.sendMessage(ChatColor.GREEN + "クエストを追加しました: " + questId);
      return true;
   }

   private void move(String source, String target) {
      ConfigurationSection section = this.data.getConfigurationSection(source);
      if (section != null) {
         for (Entry<String, Object> entry : section.getValues(true).entrySet()) {
            this.data.set(target + "." + entry.getKey(), entry.getValue());
         }
      }

      this.data.set(source, null);
   }

   private String safeId(String raw) {
      String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
      if (normalized.isBlank()) {
         normalized = "proposal";
      }

      return SAFE_ID.matcher(normalized).matches() ? normalized : normalized.substring(0, Math.min(64, normalized.length()));
   }

   private void ensureLoaded() {
      if (this.data == null) {
         this.load();
      }
   }

   private void save() {
      try {
         if (this.file.getParentFile() != null && !this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         this.data.save(this.file);
      } catch (IOException e) {
         this.plugin.getLogger().severe("Could not save proposals.yml: " + e.getMessage());
      }
   }
}
