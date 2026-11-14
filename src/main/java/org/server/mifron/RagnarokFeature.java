package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * イベント「ラグナロク」。開催中は全 MOB がエリート化する。
 *
 * <p>エリート化そのものは {@link EliteMobFeature} の公開 API
 * ({@link EliteMobFeature#elitizeNearest}) を呼ぶだけで、エリートの生成ロジックには
 * 一切手を入れない。ここが受け持つのは「いつ・どの MOB をエリート化するか」の制御と
 * プレイヤーへの通知だけ。
 *
 * <p>操作は {@code /mf ragnarok start [秒数]} / {@code /mf ragnarok stop} /
 * {@code /mf ragnarok status}。既存の {@code /mf} ディスパッチャを書き換えずに済むよう、
 * コマンド前処理イベントでこの 1 系統だけを横取りする。
 */
public class RagnarokFeature implements Listener {
   /** {@code /mf} の別名（plugin.yml の mifron / mf に対応）。 */
   private static final List<String> ROOT_COMMANDS = List.of("mf", "mifron");
   private static final String SUBCOMMAND = "ragnarok";
   /** elitizeNearest は近傍 1 体しか変換しないため、全員が変わるまで走査を繰り返す。 */
   private static final int MAX_CONVERT_PASSES = 20;
   /** 変換対象を拾う半径（ブロック）。対象自身が最寄りになる程度の狭さにする。 */
   private static final double CONVERT_RADIUS = 2.5D;

   private final Mifron plugin;
   /** 開催終了時刻（epoch millis）。0 なら未開催。 */
   private volatile long ragnarokUntil;
   /** 開催時間の合計（ボスバー進捗用）。 */
   private volatile long ragnarokTotalMillis;
   private BossBar bossBar;
   private BukkitTask bossBarTask;

   public RagnarokFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   /** ラグナロク開催中か。 */
   public boolean isActive() {
      return RagnarokRules.isActive(System.currentTimeMillis(), this.ragnarokUntil);
   }

   /** 残り時間（ミリ秒）。未開催なら 0。 */
   public long remainingMillis() {
      return RagnarokRules.remainingMillis(System.currentTimeMillis(), this.ragnarokUntil);
   }

   /**
    * ラグナロクを開始し、読み込み済みの MOB をその場でエリート化する。
    *
    * @param seconds 希望する開催秒数（0 以下なら設定値。設定の min/max で丸める）
    * @return エリート化した既存 MOB の体数。すでに開催中なら -1
    */
   public int start(int seconds) {
      if (this.isActive()) return -1;
      int duration = this.durationSeconds(seconds);
      this.ragnarokTotalMillis = duration * 1000L;
      this.ragnarokUntil = System.currentTimeMillis() + this.ragnarokTotalMillis;
      int converted = this.plugin.getConfig().getBoolean("ragnarok.convert-existing", true)
         ? this.convertExistingMobs()
         : 0;
      this.startBossBar();
      this.broadcast(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ ラグナロク ⚔ "
         + ChatColor.RED + "全MOBがエリート化した。" + ChatColor.GRAY
         + " (" + duration + "秒間 / 既存 " + converted + " 体)");
      this.plugin.getLogger().info("Ragnarok started: " + duration + "s, converted=" + converted);
      return converted;
   }

   /**
    * ラグナロクを即時終了する。
    *
    * @return 開催中だった場合のみ true
    */
   public boolean stop() {
      boolean wasActive = this.isActive();
      this.ragnarokUntil = 0L;
      this.ragnarokTotalMillis = 0L;
      this.hideBossBar();
      if (wasActive) {
         this.broadcast(ChatColor.GOLD + "ラグナロクは終息した。");
         this.plugin.getLogger().info("Ragnarok stopped by command.");
      }
      return wasActive;
   }

   /**
    * 読み込み済みの全 MOB をエリート化する。
    *
    * <p>{@code elitizeNearest} は指定位置の近傍 1 体しか変換しないので、
    * 対象が減らなくなるまで走査を繰り返す（{@link #MAX_CONVERT_PASSES} 回で打ち切り）。
    *
    * @return エリート化した体数
    */
   public int convertExistingMobs() {
      int converted = 0;
      for (World world : Bukkit.getWorlds()) {
         if (this.isExcludedWorld(world)) continue;
         List<Mob> targets = new ArrayList<>();
         for (LivingEntity living : world.getLivingEntities()) {
            if (!(living instanceof Mob mob) || !(mob instanceof Enemy)) continue;
            if (this.plugin.eliteMobFeature.isElite(mob)) continue;
            targets.add(mob);
         }
         int passes = 0;
         boolean progress = !targets.isEmpty();
         while (progress && passes++ < MAX_CONVERT_PASSES) {
            progress = false;
            for (Mob mob : targets) {
               if (mob.isDead() || !mob.isValid()) continue;
               if (this.plugin.eliteMobFeature.isElite(mob)) continue;
               if (this.elitizeAt(mob)) {
                  converted++;
                  progress = true;
               }
            }
         }
      }
      return converted;
   }

   /** 開催中に新しく湧いた MOB もエリート化する。 */
   @EventHandler(priority = EventPriority.MONITOR)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (!this.isActive()) return;
      if (!this.plugin.getConfig().getBoolean("ragnarok.elite-new-spawns", true)) return;
      if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) return;
      if (this.isExcludedWorld(mob.getWorld())) return;
      this.elitizeAt(mob);
   }

   /** 開催中に参加したプレイヤーにもボスバーを見せる。 */
   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      BossBar bar = this.bossBar;
      if (bar == null || !this.isActive()) return;
      if (this.isExcludedWorld(event.getPlayer().getWorld())) return;
      bar.addPlayer(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
      String message = event.getMessage();
      if (!isRagnarokCommand(message)) return;
      event.setCancelled(true);
      this.handleCommand(event.getPlayer(), message);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onServerCommand(ServerCommandEvent event) {
      String message = "/" + event.getCommand();
      if (!isRagnarokCommand(message)) return;
      event.setCancelled(true);
      this.handleCommand(event.getSender(), message);
   }

   // ---- コマンド処理 -------------------------------------------------------

   /** {@code /mf ragnarok ...} かどうか。 */
   static boolean isRagnarokCommand(String raw) {
      List<String> tokens = tokens(raw);
      if (tokens.size() < 2) return false;
      if (!ROOT_COMMANDS.contains(tokens.get(0).toLowerCase(Locale.ROOT))) return false;
      return SUBCOMMAND.equalsIgnoreCase(tokens.get(1));
   }

   private static List<String> tokens(String raw) {
      String trimmed = raw == null ? "" : raw.trim();
      while (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
      if (trimmed.isEmpty()) return List.of();
      return List.of(trimmed.split("\\s+"));
   }

   private void handleCommand(org.bukkit.command.CommandSender sender, String raw) {
      if (sender == null) return;
      if (!sender.isOp() && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage(ChatColor.RED + "ラグナロクの操作には管理者権限が必要です。");
         return;
      }
      List<String> tokens = tokens(raw);
      String action = tokens.size() >= 3 ? tokens.get(2).toLowerCase(Locale.ROOT) : "status";
      switch (action) {
         case "start" -> this.handleStart(sender, tokens);
         case "stop" -> this.handleStop(sender);
         case "status" -> this.handleStatus(sender);
         default -> sender.sendMessage(ChatColor.RED
            + "使い方: /mf ragnarok start [秒数] | /mf ragnarok stop | /mf ragnarok status");
      }
   }

   private void handleStart(org.bukkit.command.CommandSender sender, List<String> tokens) {
      if (this.isActive()) {
         sender.sendMessage(ChatColor.YELLOW + "ラグナロクはすでに開催中です。残り "
            + RagnarokRules.formatRemaining(this.remainingMillis()) + "。");
         return;
      }
      int requested = 0;
      if (tokens.size() >= 4) {
         try {
            requested = Integer.parseInt(tokens.get(3));
         } catch (NumberFormatException ignored) {
            sender.sendMessage(ChatColor.RED + "秒数は整数で指定してください: " + tokens.get(3));
            return;
         }
      }
      int converted = this.start(requested);
      sender.sendMessage(ChatColor.GREEN + "ラグナロク開始。残り "
         + RagnarokRules.formatRemaining(this.remainingMillis()) + " / 既存 " + converted + " 体をエリート化");
   }

   private void handleStop(org.bukkit.command.CommandSender sender) {
      boolean wasActive = this.stop();
      sender.sendMessage(wasActive
         ? ChatColor.GREEN + "ラグナロクを終了しました。"
         : ChatColor.GRAY + "ラグナロクは開催されていません。");
   }

   private void handleStatus(org.bukkit.command.CommandSender sender) {
      if (!this.isActive()) {
         sender.sendMessage(ChatColor.GRAY + "ラグナロクは開催されていません。");
         return;
      }
      sender.sendMessage(ChatColor.YELLOW + "ラグナロク開催中。残り "
         + RagnarokRules.formatRemaining(this.remainingMillis()) + "。");
   }

   // ---- 内部処理 -----------------------------------------------------------

   private boolean elitizeAt(Mob mob) {
      Location location = mob.getLocation();
      return this.plugin.eliteMobFeature.elitizeNearest(
         location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), CONVERT_RADIUS);
   }

   private int durationSeconds(int requested) {
      int fallback = this.plugin.getConfig().getInt("ragnarok.duration-seconds",
         RagnarokRules.DEFAULT_DURATION_SECONDS);
      int min = this.plugin.getConfig().getInt("ragnarok.min-duration-seconds",
         RagnarokRules.MIN_DURATION_SECONDS);
      int max = this.plugin.getConfig().getInt("ragnarok.max-duration-seconds",
         RagnarokRules.MAX_DURATION_SECONDS);
      int wanted = requested > 0 ? requested : fallback;
      return RagnarokRules.clampDuration(wanted, min, max);
   }

   private void startBossBar() {
      this.hideBossBar();
      if (!this.plugin.getConfig().getBoolean("ragnarok.boss-bar", true)) return;
      BossBar bar = Bukkit.createBossBar(this.bossBarTitle(this.remainingMillis()), BarColor.RED, BarStyle.SEGMENTED_20);
      bar.setProgress(1.0D);
      bar.setVisible(true);
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isExcludedWorld(player.getWorld())) continue;
         bar.addPlayer(player);
      }
      this.bossBar = bar;
      this.bossBarTask = this.plugin.getServer().getScheduler()
         .runTaskTimer(this.plugin, this::updateBossBar, 20L, 20L);
   }

   private void updateBossBar() {
      BossBar bar = this.bossBar;
      if (bar == null) {
         this.hideBossBar();
         return;
      }
      long remaining = this.remainingMillis();
      if (!this.isActive()) {
         this.finish();
         return;
      }
      bar.setTitle(this.bossBarTitle(remaining));
      bar.setProgress(RagnarokRules.progress(remaining, this.ragnarokTotalMillis));
   }

   /** 時間切れの終了処理。 */
   private void finish() {
      boolean wasActive = this.ragnarokUntil != 0L;
      this.ragnarokUntil = 0L;
      this.ragnarokTotalMillis = 0L;
      this.hideBossBar();
      if (wasActive) this.broadcast(ChatColor.GOLD + "ラグナロクは終息した。");
   }

   private void hideBossBar() {
      BukkitTask task = this.bossBarTask;
      this.bossBarTask = null;
      if (task != null) task.cancel();
      BossBar bar = this.bossBar;
      this.bossBar = null;
      if (bar == null) return;
      bar.removeAll();
      bar.setVisible(false);
   }

   private String bossBarTitle(long remaining) {
      return ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ ラグナロク ⚔ "
         + ChatColor.RED + "全MOBエリート化 " + ChatColor.WHITE
         + RagnarokRules.formatRemaining(remaining);
   }

   private void broadcast(String message) {
      if (!this.plugin.getConfig().getBoolean("ragnarok.broadcast", true)) return;
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isExcludedWorld(player.getWorld())) continue;
         player.sendMessage(message);
      }
      Bukkit.getConsoleSender().sendMessage(message);
   }

   /** hub ワールド（既定 world）と設定で除外したワールドはラグナロクの対象外。 */
   private boolean isExcludedWorld(World world) {
      if (world == null) return true;
      String hub = this.plugin.getConfig().getString("hub.world", "world");
      if (hub != null && !hub.isBlank() && hub.equalsIgnoreCase(world.getName())) return true;
      for (String name : this.plugin.getConfig().getStringList("ragnarok.excluded-worlds")) {
         if (name != null && name.equalsIgnoreCase(world.getName())) return true;
      }
      return false;
   }
}
