package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Test-server operation: whitelist enforcement plus an automatic restart
 * every 6 hours with a 5-minute advance chat warning.
 *
 * <p>Fully config-gated ({@code test-server.*}) and inert on production
 * unless explicitly enabled, so the same jar runs on both servers.
 */
final class TestServerFeature implements Listener {
   static final long RESTART_PERIOD_TICKS = 6L * 60L * 60L * 20L;
   static final long WARN_BEFORE_TICKS = 5L * 60L * 20L;

   private final Mifron plugin;
   private BukkitTask warnTask;
   private BukkitTask restartTask;

   TestServerFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.plugin.getConfig().addDefault("test-server.enabled", false);
      this.plugin.getConfig().addDefault("test-server.enforce-whitelist", true);
      this.plugin.getConfig().addDefault("test-server.auto-restart", true);
      this.plugin.getConfig().addDefault("test-server.restart-period-hours", 6);
      this.plugin.getConfig().addDefault("test-server.warn-minutes-before", 5);
      this.plugin.getConfig().options().copyDefaults(true);
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("test-server.enabled", false);
   }

   void start() {
      this.stop();
      if (!this.enabled()) return;
      if (this.plugin.getConfig().getBoolean("test-server.enforce-whitelist", true)) {
         Bukkit.setWhitelist(true);
         Bukkit.reloadWhitelist();
      }
      if (!this.plugin.getConfig().getBoolean("test-server.auto-restart", true)) return;
      long periodHours = Math.max(1L, Math.min(72L,
         this.plugin.getConfig().getLong("test-server.restart-period-hours", 6L)));
      long periodTicks = periodHours * 60L * 60L * 20L;
      long warnTicks = Math.max(1L, Math.min(60L,
         this.plugin.getConfig().getLong("test-server.warn-minutes-before", 5L)))
         * 60L * 20L;
      long warnDelay = Math.max(0L, periodTicks - warnTicks);
      this.warnTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (!this.enabled()) return;
         String message = "§e[テストサーバー] " + (warnTicks / 1200L)
            + "分後に自動再起動します。安全な場所へ移動してください。";
         Bukkit.broadcastMessage(message);
         for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§e自動再起動まであと" + (warnTicks / 1200L) + "分", "§7安全な場所へ移動してください",
               10, 100, 20);
         }
      }, warnDelay, periodTicks);
      this.restartTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (!this.enabled()) return;
         Bukkit.broadcastMessage("§c[テストサーバー] 自動再起動を開始します。");
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            try {
               Bukkit.restart();
            } catch (Throwable restartFailed) {
               this.plugin.getLogger().warning("Bukkit.restart() failed, shutting down: " + restartFailed.getMessage());
               Bukkit.shutdown();
            }
         }, 100L);
      }, periodTicks, periodTicks);
      this.plugin.getLogger().info("TestServerFeature active: restart every " + periodHours
         + "h with " + (warnTicks / 1200L) + "min warning.");
   }

   void stop() {
      if (this.warnTask != null) {
         this.warnTask.cancel();
         this.warnTask = null;
      }
      if (this.restartTask != null) {
         this.restartTask.cancel();
         this.restartTask = null;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (!this.enabled()) return;
      if (!this.plugin.getConfig().getBoolean("test-server.enforce-whitelist", true)) return;
      if (!Bukkit.getWhitelistedPlayers().contains(event.getPlayer())
         && !event.getPlayer().isOp()) {
         // Whitelist is enforced by the server itself; this is a reminder.
         event.getPlayer().sendMessage("§eここはホワイトリスト制のテストサーバーです。");
      }
   }
}
