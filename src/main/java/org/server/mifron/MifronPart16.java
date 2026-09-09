package org.server.mifron;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

abstract class MifronPart16 extends MifronPart15 {
   protected void addPlayerStat(UUID uuid, String key, int amount, boolean persist) {
      if (amount > 0) {
         ConfigurationSection section = this.getPlayerSection(uuid);
         section.set(key, this.safeAdd(section.getInt(key, 0), amount));
         this.questService.recordStat(uuid, key, amount);
         if (persist) this.queueDataSave();
      }
   }

   protected void refreshPlayerName(Player player) {
      String title = this.selectedTitle(player);
      Component name = this.titlePrefix(player).append(Component.text(player.getName()));
      Component tabName = name.append(Component.text(" MFL " + this.getMfl(player.getUniqueId()), NamedTextColor.AQUA));
      player.displayName(name);
      player.playerListName(tabName);
      player.customName(name);
      player.setCustomNameVisible(!title.isBlank());
   }

   protected Component titlePrefix(Player player) {
      String title = this.selectedTitle(player);
      return title.isBlank() ? Component.empty() : Component.text("[" + title + "] ", NamedTextColor.GOLD);
   }

   protected String selectedTitle(Player player) {
      String title = this.getPlayerSection(player.getUniqueId()).getString("selected-title", "");
      if (title != null && !title.isBlank() && this.canUseTitle(player, title)) return title;
      if (title != null && !title.isBlank()) {
         this.getPlayerSection(player.getUniqueId()).set("selected-title", null);
         this.queueDataSave();
      }
      return "";
   }

   protected boolean canUseTitle(Player player, String title) {
      TitleDefinition definition = this.titleDefinitions().get(title);
      if (definition == null) return false;
      Set<String> completed = new HashSet<>(this.getPlayerSection(player.getUniqueId()).getStringList("completed-advancements"));
      return this.hasTitle(completed, definition);
   }

   protected Map<String, TitleDefinition> titleDefinitions() {
      Map<String, TitleDefinition> definitions = new LinkedHashMap<>(TITLE_DEFINITIONS);
      ConfigurationSection section = this.getConfig().getConfigurationSection("titles");
      if (section == null) return definitions;
      for (String key : section.getKeys(false)) {
         String displayName = section.getString(key + ".display-name", key);
         if (displayName != null && !displayName.isBlank()) {
            Material icon = Material.matchMaterial(section.getString(key + ".icon", "name_tag"));
            List<String> requirements = section.getStringList(key + ".required-advancements");
            definitions.put(displayName, new TitleDefinition(icon == null ? Material.NAME_TAG : icon, requirements));
         }
      }
      return definitions;
   }

   protected void resetStatusData(UUID uuid) {
      ConfigurationSection section = this.getPlayerSection(uuid);
      for (String key : List.of(
         "emeralds", "total-earned-emeralds", "income-bonus-percent", "advancement-bonus-percent",
         "reincarnation-bonus-percent", "reincarnations", "pending-advancement-reset", "session-minutes",
         "session-playtime-rewards", "total-minutes", "total-play-count", "login-streak", "total-logins",
         "last-login-reward", "completed-advancements", "rewarded-advancements", "all-advancements-rewarded",
         "shop-discount", "unlocks", "skins", "pets", "ffa-classes", "total-mob-kills", "killed-mobs",
         "unlocked-titles", "selected-title", "total-trades", "total-blocks-broken", "total-blocks-placed"
      )) {
         section.set(key, null);
      }
      section.set("pending-advancement-reset", true);
      this.queueDataSave();
   }

   protected ConfigurationSection getPlayerSection(UUID uuid) {
      String path = "players." + uuid;
      ConfigurationSection section = this.data.getConfigurationSection(path);
      return section != null ? section : this.data.createSection(path);
   }

   protected Set<UUID> getUuidSet(UUID owner, String key) {
      List<String> raw = this.getPlayerSection(owner).getStringList(key);
      Set<UUID> result = new HashSet<>();
      for (String value : raw) {
         try { result.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) {}
      }
      return result;
   }

   boolean areFriends(UUID first, UUID second) {
      return first != null && second != null && !first.equals(second)
         && (this.getUuidSet(first, "friends").contains(second) || this.getUuidSet(second, "friends").contains(first));
   }

   protected void setUuidSet(UUID owner, String key, Set<UUID> values) {
      this.getPlayerSection(owner).set(key, values.stream().map(UUID::toString).toList());
      this.queueDataSave();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      try {
         if ("friend".equalsIgnoreCase(command.getName())) return this.handleFriendCommand(sender, args);
         if ("status".equalsIgnoreCase(command.getName())) return this.handleStatusCommand(sender, args);
         if ("tutorial".equalsIgnoreCase(command.getName())) return this.handleTutorialCommand(sender);
         return this.handleMifronCommand(sender, args);
      } catch (Throwable e) {
         this.getLogger().severe("Command failed: /" + label + " " + String.join(" ", args));
         e.printStackTrace();
         sender.sendMessage("\u00a7c\u30b3\u30de\u30f3\u30c9\u5b9f\u884c\u4e2d\u306b\u30a8\u30e9\u30fc\u304c\u767a\u751f\u3057\u307e\u3057\u305f\u3002");
         return true;
      }
   }
}
