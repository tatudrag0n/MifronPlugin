from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)


def method_span(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"missing method: {signature}")
    brace = text.find('{', start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f"unterminated method: {signature}")


def replace_method(text: str, signature: str, replacement: str) -> str:
    a, b = method_span(text, signature)
    return text[:a] + replacement + text[b:]

# -----------------------------------------------------------------------------
# FfaManager: track the Bug Mania owner behind the vanilla INFESTED effect and
# adopt silverfish spawned with SpawnReason.POTION_EFFECT into bug_silverfish.
# -----------------------------------------------------------------------------
p = Path('src/main/java/org/server/minerva/FfaManager.java')
s = p.read_text(encoding='utf-8')

field_anchor = '   private final Map<UUID, BukkitTask> bugExpiryTasks = new HashMap<>();'
field_repl = field_anchor + '''\n   private final Map<UUID, UUID> infestedBugOwners = new HashMap<>();\n   private final Map<UUID, Long> infestedBugOwnerExpires = new HashMap<>();'''
s = replace_once(s, field_anchor, field_repl, 'Infested owner maps')

shutdown_anchor = '      this.removeAllBugMobs();'
shutdown_repl = shutdown_anchor + '''\n      this.infestedBugOwners.clear();\n      this.infestedBugOwnerExpires.clear();'''
s = replace_once(s, shutdown_anchor, shutdown_repl, 'Infested map shutdown cleanup')

old_spawn_signature = '   private void spawnBugSilverfish(Player owner, Location location)'
new_spawn = '''   private void spawnBugSilverfish(Player owner, Location location) {
      if (owner == null || location == null || location.getWorld() == null) {
         return;
      }
      Entity entity = location.getWorld().spawnEntity(location, EntityType.SILVERFISH);
      this.registerBugSilverfish(owner, entity);
   }

   private void registerBugSilverfish(Player owner, Entity entity) {
      if (owner == null || entity == null || entity.getType() != EntityType.SILVERFISH) {
         return;
      }

      int globalMax = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.BUG_MANIA, "max-global-silverfish"), 30));
      while (this.bugOwners.size() >= globalMax) {
         UUID first = this.bugOwners.keySet().stream().findFirst().orElse(null);
         if (first == null) {
            break;
         }
         this.removeBugEntity(first);
      }

      UUID ownerId = owner.getUniqueId();
      List<UUID> owned = this.bugMobs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
      int maxOwned = Math.max(1, this.plugin.getConfig().getInt(this.config.kitPath(FfaKit.BUG_MANIA, "max-owned-silverfish"), 6));
      while (owned.size() >= maxOwned) {
         this.removeBugEntity(owned.remove(0));
      }

      entity.getPersistentDataContainer().set(this.entityKindKey, PersistentDataType.STRING, "bug_silverfish");
      entity.getPersistentDataContainer().set(this.entityOwnerKey, PersistentDataType.STRING, ownerId.toString());
      if (entity instanceof LivingEntity living) {
         living.setCanPickupItems(false);
         living.setRemoveWhenFarAway(false);
      }

      if (!owned.contains(entity.getUniqueId())) {
         owned.add(entity.getUniqueId());
      }
      this.bugOwners.put(entity.getUniqueId(), ownerId);
      BukkitTask oldExpiry = this.bugExpiryTasks.remove(entity.getUniqueId());
      if (oldExpiry != null) {
         oldExpiry.cancel();
      }
      BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.removeBugEntity(entity.getUniqueId()), 400L);
      this.bugExpiryTasks.put(entity.getUniqueId(), task);
   }'''
s = replace_method(s, old_spawn_signature, new_spawn)

old_effect = '''               killer.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 1200, 0, false, false, true));'''
new_effect = '''               if (owner != null) {
                  this.infestedBugOwners.put(killer.getUniqueId(), owner);
                  this.infestedBugOwnerExpires.put(killer.getUniqueId(), System.currentTimeMillis() + 65000L);
               }
               killer.addPotionEffect(new PotionEffect(PotionEffectType.INFESTED, 1200, 0, false, false, true));'''
s = replace_once(s, old_effect, new_effect, 'remember Bug Mania source for INFESTED')

spawn_handler = '''   void handlePotionEffectSilverfishSpawn(Entity entity) {
      if (entity == null || entity.getType() != EntityType.SILVERFISH) {
         return;
      }
      String existingKind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if ("bug_silverfish".equals(existingKind)) {
         return;
      }

      long now = System.currentTimeMillis();
      Player afflicted = null;
      UUID bugOwnerId = null;
      double bestDistance = Double.MAX_VALUE;
      for (Player candidate : this.plugin.getServer().getOnlinePlayers()) {
         if (!this.isPlaying(candidate) || candidate.getWorld() != entity.getWorld() || !candidate.hasPotionEffect(PotionEffectType.INFESTED)) {
            continue;
         }
         UUID mappedOwner = this.infestedBugOwners.get(candidate.getUniqueId());
         long expires = this.infestedBugOwnerExpires.getOrDefault(candidate.getUniqueId(), 0L);
         if (mappedOwner == null || expires < now) {
            if (expires < now) {
               this.infestedBugOwners.remove(candidate.getUniqueId());
               this.infestedBugOwnerExpires.remove(candidate.getUniqueId());
            }
            continue;
         }
         double distance = candidate.getLocation().distanceSquared(entity.getLocation());
         if (distance <= 16.0 && distance < bestDistance) {
            afflicted = candidate;
            bugOwnerId = mappedOwner;
            bestDistance = distance;
         }
      }

      if (afflicted == null || bugOwnerId == null) {
         return;
      }
      Player owner = this.plugin.getServer().getPlayer(bugOwnerId);
      if (owner == null || !this.isPlaying(owner) || !this.isBugMania(owner)) {
         return;
      }

      this.registerBugSilverfish(owner, entity);
   }

'''
insert_anchor = '   void handleBugSilverfishBlockChange(EntityChangeBlockEvent event) {'
if 'void handlePotionEffectSilverfishSpawn(Entity entity)' not in s:
    if insert_anchor not in s:
        raise SystemExit('missing potion-effect spawn handler insertion point')
    s = s.replace(insert_anchor, spawn_handler + insert_anchor, 1)

p.write_text(s, encoding='utf-8', newline='\n')

# -----------------------------------------------------------------------------
# FfaListener: Paper 26.1.2 exposes POTION_EFFECT as the exact spawn reason for
# creatures created by effects such as INFESTED. Route only those silverfish.
# -----------------------------------------------------------------------------
p = Path('src/main/java/org/server/minerva/FfaListener.java')
s = p.read_text(encoding='utf-8')
if 'import org.bukkit.event.entity.CreatureSpawnEvent;' not in s:
    s = s.replace('import org.bukkit.event.entity.EntityChangeBlockEvent;\n', 'import org.bukkit.event.entity.CreatureSpawnEvent;\nimport org.bukkit.event.entity.EntityChangeBlockEvent;\n', 1)

listener = '''   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCreatureSpawn(CreatureSpawnEvent event) {
      if (event.getEntityType() == org.bukkit.entity.EntityType.SILVERFISH
         && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.POTION_EFFECT) {
         this.ffa.handlePotionEffectSilverfishSpawn(event.getEntity());
      }
   }

'''
listener_anchor = '   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n   public void onKitStandClick(PlayerInteractAtEntityEvent event) {'
if 'public void onCreatureSpawn(CreatureSpawnEvent event)' not in s:
    if listener_anchor not in s:
        raise SystemExit('missing FfaListener insertion point')
    s = s.replace(listener_anchor, listener + listener_anchor, 1)
p.write_text(s, encoding='utf-8', newline='\n')

# -----------------------------------------------------------------------------
# FfaFieldItemManager: real weather remains enabled, but also render explicit
# rain particles around FFA players so dry biomes still visibly look rainy.
# -----------------------------------------------------------------------------
p = Path('src/main/java/org/server/minerva/FfaFieldItemManager.java')
s = p.read_text(encoding='utf-8')
if 'import org.bukkit.Particle;' not in s:
    s = s.replace('import org.bukkit.NamespacedKey;\n', 'import org.bukkit.NamespacedKey;\nimport org.bukkit.Particle;\n', 1)

weather_field_anchor = '   private BukkitTask spawnTask;'
weather_field_repl = weather_field_anchor + '\n   private BukkitTask rainVisualTask;'
s = replace_once(s, weather_field_anchor, weather_field_repl, 'rain visual task field')

shutdown_weather_anchor = '''      if (this.spawnTask != null) {
         this.spawnTask.cancel();
         this.spawnTask = null;
      }
'''
shutdown_weather_repl = shutdown_weather_anchor + '''
      this.stopRainVisuals();
'''
s = replace_once(s, shutdown_weather_anchor, shutdown_weather_repl, 'rain visual shutdown')

old_previous_event = '''         if (oldType != null) {
            this.removeEventItems(oldType);
         }'''
new_previous_event = '''         if (oldType != null) {
            this.removeEventItems(oldType);
            if ("rain".equals(oldType)) {
               this.stopRainWeather();
            }
         }'''
s = replace_once(s, old_previous_event, new_previous_event, 'stop replaced rain event')

rain_apply_anchor = '''               world.setThunderDuration(durationTicks);
            }

            for (Player player : this.ffaPlayers()) {'''
rain_apply_repl = '''               world.setThunderDuration(durationTicks);
            }
            this.startRainVisuals();

            for (Player player : this.ffaPlayers()) {'''
s = replace_once(s, rain_apply_anchor, rain_apply_repl, 'start rain visuals')

old_end_rain = '''      if ("rain".equals(type)) {
         World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();
         if (world != null) {
            world.setStorm(false);
            world.setWeatherDuration(0);
            world.setThundering(false);
            world.setThunderDuration(0);
            world.setClearWeatherDuration(20);
         }
      }'''
new_end_rain = '''      if ("rain".equals(type)) {
         this.stopRainWeather();
      }'''
s = replace_once(s, old_end_rain, new_end_rain, 'end rain helper')

weather_helpers = '''   private void startRainVisuals() {
      this.stopRainVisuals();
      this.rainVisualTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
         for (Player player : this.ffaPlayers()) {
            Location above = player.getLocation().clone().add(0.0, 5.5, 0.0);
            // RAIN supplies the familiar rain/splash visual; FALLING_WATER makes
            // the shower visible even in biomes where vanilla precipitation is hidden.
            player.spawnParticle(Particle.RAIN, above, 40, 5.5, 4.0, 5.5, 0.12);
            player.spawnParticle(Particle.FALLING_WATER, above, 24, 5.0, 3.2, 5.0, 0.08);
         }
      }, 0L, 4L);
   }

   private void stopRainVisuals() {
      if (this.rainVisualTask != null) {
         this.rainVisualTask.cancel();
         this.rainVisualTask = null;
      }
   }

   private void stopRainWeather() {
      this.stopRainVisuals();
      World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();
      if (world != null) {
         world.setStorm(false);
         world.setWeatherDuration(0);
         world.setThundering(false);
         world.setThunderDuration(0);
         world.setClearWeatherDuration(20);
      }
   }

'''
helper_anchor = '   void applyActiveEventGear(Player player) {'
if 'private void startRainVisuals()' not in s:
    if helper_anchor not in s:
        raise SystemExit('missing rain helper insertion point')
    s = s.replace(helper_anchor, weather_helpers + helper_anchor, 1)

p.write_text(s, encoding='utf-8', newline='\n')
print('patched INFESTED silverfish ownership and rain visuals')
