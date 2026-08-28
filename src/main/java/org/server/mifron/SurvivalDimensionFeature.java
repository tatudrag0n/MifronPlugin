package org.server.mifron;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Keeps Survival's Nether and End separate from the main world's dimensions.
 *
 * <p>This listener only handles normal Bukkit portal events originating in the
 * configured Survival dimension group. Mifron's server-wand portals are
 * handled by {@link ServerPortalFeature} and remain untouched.</p>
 */
final class SurvivalDimensionFeature implements Listener {
   private static final String CONFIG_ROOT = "survival-dimensions";
   private static final String OVERWORLD_PATH = CONFIG_ROOT + ".overworld";
   private static final String NETHER_PATH = CONFIG_ROOT + ".nether";
   private static final String END_PATH = CONFIG_ROOT + ".end";

   private final Mifron plugin;

   SurvivalDimensionFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   void ensureWorlds() {
      if (!this.plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", true)) {
         this.plugin.getLogger().info("Survival dimensions are disabled by configuration.");
         return;
      }

      World survival = this.world(OVERWORLD_PATH, "survival");
      if (survival == null) {
         this.plugin.getLogger().severe("Configured Survival overworld is missing; custom portal routing is disabled.");
         return;
      }

      long seed = survival.getSeed();
      this.ensureWorld(NETHER_PATH, "survival_nether", World.Environment.NETHER, seed);
      this.ensureWorld(END_PATH, "survival_the_end", World.Environment.THE_END, seed);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onPlayerPortal(PlayerPortalEvent event) {
      if (!this.plugin.getConfig().getBoolean(CONFIG_ROOT + ".enabled", true)) {
         return;
      }

      Location from = event.getFrom();
      if (from == null || from.getWorld() == null) {
         return;
      }

      World survival = this.world(OVERWORLD_PATH, "survival");
      World nether = this.world(NETHER_PATH, "survival_nether");
      World end = this.world(END_PATH, "survival_the_end");
      if (survival == null || nether == null || end == null) {
         this.cancelWithoutFallback(event, "Survival用ディメンションの準備が完了していないため、ポータルを利用できません。");
         return;
      }

      World source = from.getWorld();
      TeleportCause cause = event.getCause();
      if (source.equals(survival) && cause == TeleportCause.NETHER_PORTAL) {
         this.route(event, this.scaled(from, nether, 1.0D / 8.0D));
      } else if (source.equals(nether) && cause == TeleportCause.NETHER_PORTAL) {
         this.route(event, this.scaled(from, survival, 8.0D));
      } else if (source.equals(survival) && cause == TeleportCause.END_PORTAL) {
         this.route(event, this.endEntry(event, end));
      } else if (source.equals(end) && cause == TeleportCause.END_PORTAL) {
         this.route(event, survival.getSpawnLocation());
      }
   }

   private void ensureWorld(String path, String defaultName, World.Environment environment, long seed) {
      String name = this.plugin.getConfig().getString(path, defaultName);
      if (name == null || name.isBlank()) {
         this.plugin.getLogger().severe("Empty world name configured at " + path + "; custom portal routing is disabled for this dimension.");
         return;
      }

      World existing = Bukkit.getWorld(name);
      if (existing != null) {
         if (existing.getEnvironment() != environment) {
            this.plugin.getLogger().severe("World " + name + " has environment " + existing.getEnvironment()
               + ", expected " + environment + "; it will not be used for Survival portals.");
         } else {
            this.plugin.getLogger().info("Survival " + environment.name().toLowerCase(Locale.ROOT) + " ready: " + name);
         }
         return;
      }

      try {
         WorldCreator creator = new WorldCreator(name)
            .environment(environment)
            .type(WorldType.NORMAL)
            .generateStructures(true)
            .seed(seed);
         World created = Bukkit.createWorld(creator);
         if (created == null || created.getEnvironment() != environment) {
            this.plugin.getLogger().severe("Failed to create Survival dimension " + name + " with environment " + environment + ".");
         } else {
            this.plugin.getLogger().info("Created Survival " + environment.name().toLowerCase(Locale.ROOT) + ": " + name);
         }
      } catch (Throwable error) {
         this.plugin.getLogger().severe("Failed to create Survival dimension " + name + ": " + error.getMessage());
      }
   }

   private void route(PlayerPortalEvent event, Location destination) {
      if (destination == null || destination.getWorld() == null) {
         this.cancelWithoutFallback(event, "移動先のワールドが見つからないため、ポータルを利用できません。");
         return;
      }
      event.setTo(destination);
   }

   private Location scaled(Location from, World target, double factor) {
      return new Location(target, from.getX() * factor, from.getY(), from.getZ() * factor, from.getYaw(), from.getPitch());
   }

   private Location endEntry(PlayerPortalEvent event, World end) {
      Location current = event.getTo();
      if (current == null) {
         current = end.getSpawnLocation();
      }
      return new Location(end, current.getX(), current.getY(), current.getZ(), current.getYaw(), current.getPitch());
   }

   private World world(String path, String fallback) {
      String name = this.plugin.getConfig().getString(path, fallback);
      return name == null || name.isBlank() ? null : Bukkit.getWorld(name);
   }

   private void cancelWithoutFallback(PlayerPortalEvent event, String message) {
      event.setCancelled(true);
      Player player = event.getPlayer();
      player.sendMessage(ChatColor.RED + message);
   }
}
