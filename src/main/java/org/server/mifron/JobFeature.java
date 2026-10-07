package org.server.mifron;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Job combat/environment perks that need their own listener. */
final class JobFeature implements Listener {
   private final Mifron plugin;

   JobFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onLavaDamage(EntityDamageEvent event) {
      if (event.getCause() != EntityDamageEvent.DamageCause.LAVA) return;
      if (!(event.getEntity() instanceof Player player)) return;
      if (this.plugin.jobService.typeOf(player) != JobType.MINER) return;
      // Miner signature: halve lava damage (survival = effective mining uptime).
      event.setDamage(event.getDamage() / 2.0);
   }
}
