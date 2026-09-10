package org.server.mifron;

import java.time.LocalDate;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

abstract class MifronPart1x2 extends MifronPart1x1 {
   protected void applyServerMotd() {
      String motd = this.getConfig().getString("server-motd", "Mifron - Community Minecraft Server");
      if (motd == null || motd.isBlank()) motd = "Mifron - Community Minecraft Server";
      this.getServer().setMotd(motd);
      this.getLogger().info("Server MOTD set to Mifron.");
   }

   protected void runStartupStep(String name, Runnable step) {
      try { step.run(); }
      catch (Throwable e) { this.getLogger().severe("Startup step failed: " + name); e.printStackTrace(); }
   }

   public void onDisable() {
      this.cancelScheduledAutoShutdown("the plugin is disabling");
      this.minoruBridgeFeature.stop();
      try { this.auctionFeature.shutdown(); } catch (Throwable e) { this.getLogger().severe("Failed to disable auction settlement cleanly."); e.printStackTrace(); }
      try { this.ffaManager.shutdown(); } catch (Throwable e) { this.getLogger().severe("Failed to disable FFA cleanly."); e.printStackTrace(); }
      try { this.athleticManager.shutdown(); } catch (Throwable e) { this.getLogger().severe("Failed to disable athletic cleanly."); e.printStackTrace(); }
      try { this.buildWorldManager.shutdown(); } catch (Throwable e) { this.getLogger().severe("Failed to disable build worlds cleanly."); e.printStackTrace(); }
      try { this.textDisplayFeature.disable(); } catch (Throwable e) { this.getLogger().severe("Failed to disable text displays cleanly."); e.printStackTrace(); }
      this.mifron().saveData();
      InventoryGroupFeature.shutdown(this);
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      if (this.getConfig().getBoolean("auto-shutdown.enabled", false)) this.cancelScheduledAutoShutdown("a player joined");
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.shopWandActionUntil.remove(player.getUniqueId());
      ConfigurationSection session = this.mifron().getPlayerSection(player.getUniqueId());
      String sessionId = session.getString("analytics.session-id", "");
      this.minoruBridgeFeature.sendAnalyticsEvent(player, "play_session_end", sessionId, "session-end:" + (sessionId.isBlank() ? player.getUniqueId() + ":" + System.currentTimeMillis() : sessionId));
      if (this.getConfig().getBoolean("auto-shutdown.enabled", false)) {
         long remaining = Bukkit.getOnlinePlayers().stream().filter(online -> !online.getUniqueId().equals(event.getPlayer().getUniqueId())).count();
         if (remaining == 0L) this.scheduleAutoShutdown();
      }
   }

   @EventHandler
   public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
      Player player = event.getPlayer();
      String worldName = player.getWorld().getName();
      String survivalWorld = this.getConfig().getString("servers.survival.world", "survival");
      String minigameWorld = this.getConfig().getString("servers.minigame.world", "minigame");
      if (worldName.equalsIgnoreCase(survivalWorld) || worldName.equalsIgnoreCase("survival"))
         this.mifron().trackAnalytics(player, "survival_join", "survival:" + player.getUniqueId() + ":" + LocalDate.now());
      else if (worldName.equalsIgnoreCase(minigameWorld) || worldName.equalsIgnoreCase("minigame"))
         this.mifron().trackAnalytics(player, "minigame_join", "minigame:" + player.getUniqueId() + ":" + LocalDate.now());
   }

   protected void scheduleAutoShutdown() {
      int delay = Math.max(1, this.getConfig().getInt("auto-shutdown.delay-seconds", 600));
      synchronized (this.shutdownLock) {
         if (this.scheduledShutdownTask != null) this.scheduledShutdownTask.cancel();
         this.scheduledShutdownTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            synchronized (this.shutdownLock) {
               if (Bukkit.getOnlinePlayers().isEmpty()) this.performAutoShutdown(delay);
               else this.getLogger().info("Players returned before auto-shutdown; aborting.");
               this.scheduledShutdownTask = null;
            }
         }, 20L * delay);
         this.getLogger().info("Scheduled auto-shutdown in " + delay + " seconds.");
      }
   }

   protected void cancelScheduledAutoShutdown(String reason) {
      synchronized (this.shutdownLock) {
         if (this.scheduledShutdownTask != null) {
            this.scheduledShutdownTask.cancel();
            this.scheduledShutdownTask = null;
            this.getLogger().info("Cancelled scheduled auto-shutdown because " + reason + ".");
         }
      }
   }

   protected void performAutoShutdown(int delaySeconds) {
      this.getLogger().info("No players online for " + delaySeconds + "s; stopping host VM.");
      try {
         this.mifron().saveData();
         Bukkit.savePlayers();
         for (World world : Bukkit.getWorlds()) world.save();
      } catch (Throwable e) {
         this.getLogger().warning("Failed to save before VM stop: " + e.getMessage());
      }
      Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
         try {
            List<String> command = this.mifron().resolveVmStopCommand();
            this.getLogger().info("Executing VM stop command: " + String.join(" ", command));
            int exitCode = this.mifron().runProcess(command);
            this.getLogger().info("VM stop command exited with code " + exitCode + ".");
            if (exitCode != 0) this.getLogger().severe("VM stop command failed with exit code " + exitCode + ".");
         } catch (Exception e) {
            this.getLogger().severe("Failed to stop VM: " + e.getMessage());
            e.printStackTrace();
         }
      });
   }
}
