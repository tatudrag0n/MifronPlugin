package org.server.mifron;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Single-select jobs. Selection and change timestamps live in
 * data.yml (players.&lt;uuid&gt;.job.*). Income bonuses are read from
 * config (jobs.bonus.&lt;action&gt; percent) so they can be tuned live.
 */
public final class JobService {
   private final Mifron plugin;
   /** Pending job changes awaiting confirmation: uuid -> [jobKey, requestedAt]. */
   private final Map<UUID, long[]> pendingChanges = new ConcurrentHashMap<>();

   static final long PENDING_EXPIRY_MILLIS = 60_000L;

   public JobService(Mifron plugin) {
      this.plugin = plugin;
   }

   public JobType typeOf(UUID uuid) {
      if (uuid == null) return JobType.NONE;
      String key = this.plugin.getPlayerSection(uuid).getString("job.type", "none");
      JobType type = JobType.fromKey(key);
      return type == null ? JobType.NONE : type;
   }

   public JobType typeOf(Player player) {
      return player == null ? JobType.NONE : this.typeOf(player.getUniqueId());
   }

   /** Bonus percent for an action (0 when the player's job does not cover it). */
   public double bonusPercent(UUID uuid, JobAction action) {
      if (uuid == null || action == null) return 0.0;
      if (this.typeOf(uuid).incomeAction() != action) return 0.0;
      return Math.max(0.0, this.plugin.getConfig().getDouble("jobs.bonus." + action.key(), 0.0));
   }

   /** Applies the player's job bonus for an action to an amount. */
   public int applyJobBonus(UUID uuid, JobAction action, int amount) {
      return JobRules.applyBonus(amount, this.bonusPercent(uuid, action));
   }

   /** Discounted anvil MP cost for enchanters. */
   public int anvilCost(Player player, int baseCost) {
      if (player == null) return baseCost;
      double discount = this.typeOf(player) == JobType.ENCHANTER
         ? Math.max(0.0, this.plugin.getConfig().getDouble("jobs.enchant-discount-percent", 20.0))
         : 0.0;
      return JobRules.applyDiscount(baseCost, discount);
   }

   /** Base micro-reward for faucet-less actions, paid only to the matching job. */
   public int baseReward(JobAction action) {
      if (action == null) return 0;
      return Math.max(0, this.plugin.getConfig().getInt("jobs.base." + action.key(), 0));
   }

   /** Pays the job-only micro-reward when the player's job matches the action. */
   public void payJobReward(Player player, JobAction action) {
      if (player == null || action == null) return;
      if (this.typeOf(player).incomeAction() != action) return;
      int base = this.baseReward(action);
      if (base <= 0) return;
      this.plugin.depositEmeralds(player.getUniqueId(), base);
   }

   public long changeCooldownMillis() {
      double hours = Math.max(0.0, this.plugin.getConfig().getDouble("jobs.change-cooldown-hours", 24.0));
      return (long) (hours * 3_600_000L);
   }

   public long changeCooldownRemaining(UUID uuid) {
      if (uuid == null) return 0;
      long changedAt = this.plugin.getPlayerSection(uuid).getLong("job.changed-at", 0L);
      return JobRules.cooldownRemainingMillis(changedAt, System.currentTimeMillis(), this.changeCooldownMillis());
   }

   /**
    * Requests a job change. First call stages the request and returns false;
    * a second identical call within 60s applies it (unless cooled down).
    */
   public boolean requestChange(Player player, JobType target) {
      if (player == null || target == null) return false;
      UUID uuid = player.getUniqueId();
      if (this.typeOf(uuid) == target) {
         player.sendMessage("§e現在のジョブは" + target.displayName() + "です。");
         return true;
      }
      long remaining = this.changeCooldownRemaining(uuid);
      if (remaining > 0) {
         player.sendMessage("§cジョブ変更のクールダウン中です。残り: " + this.formatDuration(remaining));
         return true;
      }
      long now = System.currentTimeMillis();
      long[] pending = this.pendingChanges.get(uuid);
      if (pending != null && pending[0] == target.ordinal() && now - pending[1] < PENDING_EXPIRY_MILLIS) {
         this.pendingChanges.remove(uuid);
         this.plugin.getPlayerSection(uuid).set("job.type", target.key());
         this.plugin.getPlayerSection(uuid).set("job.changed-at", now);
         this.plugin.queueDataSave();
         player.sendMessage("§aジョブを" + target.displayName() + "に変更しました。");
         return true;
      }
      this.pendingChanges.put(uuid, new long[] {target.ordinal(), now});
      player.sendMessage("§e" + target.displayName() + "に変更しますか？60秒以内にもう一度実行すると確定します。");
      return true;
   }

   String formatDuration(long millis) {
      long hours = millis / 3_600_000L;
      long minutes = (millis % 3_600_000L) / 60_000L;
      if (hours > 0) return hours + "時間" + minutes + "分";
      if (minutes > 0) return minutes + "分";
      return Math.max(1L, millis / 1000L) + "秒";
   }
}
