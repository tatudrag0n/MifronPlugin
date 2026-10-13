package org.server.mifron;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Invite (referral) system: veterans hand a 6-digit code to new players.
 * When the invited player graduates the tutorial, both sides receive MP.
 * Anti-abuse: only players who joined within {@code referral.eligible-days}
 * can be referred, codes expire, one redemption per player, and the inviter
 * has a weekly reward cap.
 */
final class ReferralService {
   private static final SecureRandom RANDOM = new SecureRandom();

   private final Mifron plugin;

   ReferralService(Mifron plugin) {
      this.plugin = plugin;
   }

   void handleCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         sender.sendMessage("Player only.");
         return;
      }
      if (args.length >= 2) {
         this.redeem(player, args[1]);
         return;
      }
      this.showCode(player);
   }

   private long codeTtlMillis() {
      long hours = Math.max(1L, this.plugin.getConfig().getLong("referral.code-ttl-hours", 48L));
      return hours * 3_600_000L;
   }

   private void showCode(Player player) {
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      long now = System.currentTimeMillis();
      String code = section.getString("referral.code");
      long created = section.getLong("referral.code-created", 0L);
      if (code == null || !ReferralRules.codeValid(created, now, this.codeTtlMillis())) {
         code = this.uniqueCode(now);
         section.set("referral.code", code);
         section.set("referral.code-created", now);
         this.plugin.queueDataSave();
      }
      player.sendMessage(ChatColor.GOLD + "\u7d39\u4ecb\u30b3\u30fc\u30c9: " + ChatColor.YELLOW + code);
      player.sendMessage(ChatColor.GRAY + "\u3053\u306e\u30b3\u30fc\u30c9\u3092\u65b0\u898f\u30d7\u30ec\u30a4\u30e4\u30fc\u306b\u4f1d\u3048\u3066\u304f\u3060\u3055\u3044\u3002\u76f8\u624b\u304c\u30c1\u30e5\u30fc\u30c8\u30ea\u30a2\u30eb\u3092\u5352\u696d\u3059\u308b\u3068\u304a\u4e92\u3044\u306b\u7d39\u4ecb\u5831\u916c\u304c\u3082\u3089\u3048\u307e\u3059\u3002");
      player.sendMessage(ChatColor.GRAY + "/mf refer <code> \u3067\u76f8\u624b\u306e\u30b3\u30fc\u30c9\u3092\u5165\u529b\u3059\u308b\u3068\u767b\u9332\u3067\u304d\u307e\u3059\u3002");
   }

   private void redeem(Player player, String codeInput) {
      String code = codeInput == null ? "" : codeInput.trim();
      long now = System.currentTimeMillis();
      UUID inviterId = code.matches("\\d{6}") ? this.findInviter(code, now) : null;
      if (inviterId == null) {
         player.sendMessage(ChatColor.RED + "\u7d39\u4ecb\u30b3\u30fc\u30c9\u304c\u7121\u52b9\u304b\u671f\u9650\u5207\u308c\u3067\u3059\u3002");
         return;
      }
      if (inviterId.equals(player.getUniqueId())) {
         player.sendMessage(ChatColor.RED + "\u81ea\u5206\u306e\u30b3\u30fc\u30c9\u306f\u4f7f\u3048\u307e\u305b\u3093\u3002");
         return;
      }
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      if (section.getString("referral.invited-by") != null) {
         player.sendMessage(ChatColor.YELLOW + "\u3059\u3067\u306b\u7d39\u4ecb\u3092\u53d7\u3051\u3066\u3044\u307e\u3059\u3002");
         return;
      }
      int eligibleDays = this.plugin.getConfig().getInt("referral.eligible-days", 7);
      if (!ReferralRules.eligible(section.getLong("first-seen", 0L), now, eligibleDays)) {
         player.sendMessage(ChatColor.RED + "\u3053\u306e\u5236\u5ea6\u306f\u65b0\u898f\u30d7\u30ec\u30a4\u30e4\u30fc(\u53c2\u52a0" + eligibleDays + "\u65e5\u4ee5\u5185)\u306e\u307f\u5bf9\u8c61\u3067\u3059\u3002");
         return;
      }
      section.set("referral.invited-by", inviterId.toString());
      section.set("referral.inviter-name", this.plugin.safePlayerName(Bukkit.getOfflinePlayer(inviterId)));
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GREEN + "\u7d39\u4ecb\u3092\u767b\u9332\u3057\u307e\u3057\u305f\u3002\u30c1\u30e5\u30fc\u30c8\u30ea\u30a2\u30eb\u5352\u696d\u6642\u306b\u7d39\u4ecb\u8005\u3068\u3042\u306a\u305f\u306b\u5831\u916c\u304c\u5165\u308a\u307e\u3059\u3002");
      Player inviter = Bukkit.getPlayer(inviterId);
      if (inviter != null) {
         inviter.sendMessage(ChatColor.GREEN + player.getName() + "\u304c\u3042\u306a\u305f\u306e\u7d39\u4ecb\u30b3\u30fc\u30c9\u3067\u767b\u9332\u3057\u307e\u3057\u305f\u3002");
      }
   }

   /** Pays both sides exactly once when the invited player graduates. */
   void onTutorialGraduated(Player player) {
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      String inviterRaw = section.getString("referral.invited-by");
      if (inviterRaw == null || section.getBoolean("referral.rewarded", false)) return;
      UUID inviterId;
      try {
         inviterId = UUID.fromString(inviterRaw);
      } catch (IllegalArgumentException e) {
         return;
      }
      section.set("referral.rewarded", true);
      int inviteeBase = Math.max(0, this.plugin.getConfig().getInt("referral.reward-mp-invitee", 3000));
      int inviterBase = Math.max(0, this.plugin.getConfig().getInt("referral.reward-mp-inviter", 3000));
      String inviterName = section.getString("referral.inviter-name", this.plugin.safePlayerName(Bukkit.getOfflinePlayer(inviterId)));
      if (inviteeBase > 0) {
         int paid = this.plugin.applyIncomeBonus(player.getUniqueId(), inviteeBase);
         this.plugin.depositEmeralds(player.getUniqueId(), paid);
         player.sendMessage(ChatColor.GOLD + "\u7d39\u4ecb\u5831\u916c: +" + this.plugin.formatNumber(paid) + "MP (" + inviterName + " \u3055\u3093)");
      }
      if (inviterBase > 0) this.payInviter(player, inviterId, inviterBase);
      this.plugin.queueDataSave();
   }

   private void payInviter(Player invitee, UUID inviterId, int inviterBase) {
      ConfigurationSection inviterSection = this.plugin.getPlayerSection(inviterId);
      String week = ReferralRules.weekKey(LocalDate.now(ZoneId.systemDefault()));
      int rewarded = week.equals(inviterSection.getString("referral.rewarded-week")) ? inviterSection.getInt("referral.rewarded-count", 0) : 0;
      int cap = this.plugin.getConfig().getInt("referral.weekly-cap", 5);
      Player inviter = Bukkit.getPlayer(inviterId);
      if (!ReferralRules.canRewardInviter(rewarded, cap)) {
         if (inviter != null) inviter.sendMessage(ChatColor.YELLOW + "\u7d39\u4ecb\u4e0a\u9650(\u9031" + cap + "\u4ef6)\u306b\u9054\u3057\u305f\u305f\u3081\u3001\u4eca\u56de\u306f\u7d39\u4ecb\u8005\u306e\u5831\u916c\u306f\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      int paid = this.plugin.applyIncomeBonus(inviterId, inviterBase);
      this.plugin.depositEmeralds(inviterId, paid);
      inviterSection.set("referral.rewarded-week", week);
      inviterSection.set("referral.rewarded-count", rewarded + 1);
      String notice = "\u7d39\u4ecb\u5831\u916c: +" + this.plugin.formatNumber(paid) + "MP (\u7d39\u4ecb\u3057\u305f " + invitee.getName() + " \u3055\u3093\u304c\u5352\u696d)";
      if (inviter != null) inviter.sendMessage(ChatColor.GOLD + notice);
      else inviterSection.set("referral.pending-notice", notice);
   }

   /** Delivers a deferred referral reward notice on next login. */
   void flushPendingNotice(Player player) {
      ConfigurationSection section = this.plugin.getPlayerSection(player.getUniqueId());
      String notice = section.getString("referral.pending-notice");
      if (notice == null || notice.isBlank()) return;
      section.set("referral.pending-notice", null);
      this.plugin.queueDataSave();
      player.sendMessage(ChatColor.GOLD + notice);
   }

   private UUID findInviter(String code, long now) {
      ConfigurationSection players = this.plugin.data().getConfigurationSection("players");
      if (players == null) return null;
      long ttl = this.codeTtlMillis();
      for (String key : players.getKeys(false)) {
         ConfigurationSection section = players.getConfigurationSection(key);
         if (section == null || !code.equals(section.getString("referral.code"))) continue;
         if (!ReferralRules.codeValid(section.getLong("referral.code-created", 0L), now, ttl)) return null;
         try {
            return UUID.fromString(key);
         } catch (IllegalArgumentException ignored) {
            return null;
         }
      }
      return null;
   }

   private String uniqueCode(long now) {
      for (int attempt = 0; attempt < 50; attempt++) {
         String candidate = String.format("%06d", RANDOM.nextInt(1_000_000));
         if (this.findInviter(candidate, now) == null) return candidate;
      }
      // Fall back to a time-derived code if the random space is saturated.
      return String.format("%06d", (int) (now % 1_000_000L));
   }
}
