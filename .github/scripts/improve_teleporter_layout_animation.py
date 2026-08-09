from pathlib import Path

p = Path('src/main/java/org/server/minerva/ServerPortalFeature.java')
s = p.read_text(encoding='utf-8')


def method_span(text, signature):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'missing method: {signature}')
    brace = text.find('{', start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f'unterminated method: {signature}')


def replace_method(text, signature, replacement):
    a, b = method_span(text, signature)
    return text[:a] + replacement + text[b:]

new_open = r'''   private void openDirectTeleporter(Player player) {
      List<TeleportDestination> destinations = this.teleporterDestinations();
      if (destinations.isEmpty()) {
         player.sendMessage(ChatColor.YELLOW + "テレポーターに利用できる移動先が設定されていません。");
         return;
      }

      this.closeWorldTeleporter(player);
      Location eye = player.getEyeLocation();
      Vector forward = eye.getDirection().normalize();
      Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
      if (right.lengthSquared() < 0.01) {
         right = new Vector(1.0, 0.0, 0.0);
      } else {
         right.normalize();
      }

      // Keep many destinations visible at once. Up to eight destinations fit on one row;
      // additional destinations wrap into a second/third row instead of wrapping every five.
      int columns = Math.min(8, Math.max(1, destinations.size()));
      int rows = (destinations.size() + columns - 1) / columns;
      double spacing = columns >= 7 ? 1.02 : 1.16;
      Location center = eye.clone().add(forward.clone().multiply(3.25)).add(0.0, -0.30, 0.0);
      Location origin = eye.clone().add(forward.clone().multiply(1.05)).add(0.0, -0.55, 0.0);
      List<UUID> spawned = new ArrayList<>();
      List<TeleporterAnimatedEntity> animated = new ArrayList<>();

      for (int i = 0; i < destinations.size(); i++) {
         TeleportDestination destination = destinations.get(i);
         int row = i / columns;
         int col = i % columns;
         int rowCount = Math.min(columns, destinations.size() - row * columns);
         double horizontal = (col - (rowCount - 1) / 2.0) * spacing;
         // A subtle curved row makes the selector feel less like a chest-grid floating in space.
         double curve = -0.055 * horizontal * horizontal;
         double vertical = (rows - 1) * 0.72 - row * 1.42 + curve;
         Location iconLocation = center.clone().add(right.clone().multiply(horizontal)).add(0.0, vertical, 0.0);
         int delay = Math.min(6, col);

         Interaction hitbox = (Interaction)player.getWorld().spawnEntity(origin, EntityType.INTERACTION);
         hitbox.setInteractionWidth(0.08F);
         hitbox.setInteractionHeight(0.08F);
         hitbox.setResponsive(true);
         hitbox.getPersistentDataContainer().set(this.teleporterOptionKey, PersistentDataType.STRING, destination.key());
         hitbox.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(hitbox.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(hitbox.getUniqueId(), origin.clone(), iconLocation.clone(), 0.92F, 1.12F, delay));

         ItemDisplay icon = (ItemDisplay)player.getWorld().spawnEntity(origin.clone().add(0.0, 0.18, 0.0), EntityType.ITEM_DISPLAY);
         icon.setItemStack(new ItemStack(this.teleporterIcon(destination)));
         icon.setBillboard(Display.Billboard.CENTER);
         icon.setViewRange(0.95F);
         icon.setShadowStrength(0.6F);
         icon.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(icon.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(icon.getUniqueId(), origin.clone().add(0.0, 0.18, 0.0), iconLocation.clone().add(0.0, 0.18, 0.0), 0.0F, 0.0F, delay));

         TextDisplay label = (TextDisplay)player.getWorld().spawnEntity(origin.clone().add(0.0, -0.48, 0.0), EntityType.TEXT_DISPLAY);
         label.text(Component.text(destination.name(), NamedTextColor.WHITE));
         label.setBillboard(Display.Billboard.CENTER);
         label.setSeeThrough(true);
         label.setShadowed(true);
         label.setViewRange(0.95F);
         label.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(label.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(label.getUniqueId(), origin.clone().add(0.0, -0.48, 0.0), iconLocation.clone().add(0.0, -0.48, 0.0), 0.0F, 0.0F, delay));
      }

      this.teleporterMenuEntities.put(player.getUniqueId(), spawned);
      this.animateWorldTeleporter(player, animated);
      player.sendActionBar(Component.text("移動したいアイテムを左クリック", NamedTextColor.LIGHT_PURPLE));
      player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.65F, 1.45F);
      player.spawnParticle(Particle.REVERSE_PORTAL, origin, 28, 0.22, 0.30, 0.22, 0.025);

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.closeWorldTeleporter(player), 20L * 12L);
   }'''

new_animate = r'''   private void animateWorldTeleporter(Player player, List<TeleporterAnimatedEntity> animated) {
      // Staggered "fan-out" animation: each destination launches from the teleporter,
      // rises on a short arc, then settles into place. This avoids the previous flat slide.
      final int travelTicks = 10;
      final int maxDelay = animated.stream().mapToInt(TeleporterAnimatedEntity::delayTicks).max().orElse(0);
      final int[] tick = new int[]{0};
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task -> {
         if (!player.isOnline() || !this.teleporterMenuEntities.containsKey(player.getUniqueId())) {
            task.cancel();
            return;
         }

         int globalTick = tick[0]++;
         boolean finished = true;
         for (TeleporterAnimatedEntity moving : animated) {
            double raw = (globalTick - moving.delayTicks()) / (double)travelTicks;
            if (raw < 0.0) {
               finished = false;
               continue;
            }
            raw = Math.min(1.0, raw);
            if (raw < 1.0) {
               finished = false;
            }

            Entity entity = Bukkit.getEntity(moving.entityId());
            if (entity == null) {
               continue;
            }

            // Fast launch, soft landing, with a small vertical arc.
            double eased = 1.0 - Math.pow(1.0 - raw, 3.0);
            double arc = Math.sin(Math.PI * raw) * 0.48;
            Location from = moving.from();
            Location to = moving.to();
            Location current = from.clone().add(
               (to.getX() - from.getX()) * eased,
               (to.getY() - from.getY()) * eased + arc,
               (to.getZ() - from.getZ()) * eased
            );
            entity.teleport(current);
            if (entity instanceof Interaction interaction) {
               if (raw >= 1.0) {
                  interaction.setInteractionWidth(moving.finalWidth());
                  interaction.setInteractionHeight(moving.finalHeight());
               } else {
                  interaction.setInteractionWidth(0.08F);
                  interaction.setInteractionHeight(0.08F);
               }
            }
         }

         if (globalTick <= maxDelay && globalTick % 2 == 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.28F, 1.05F + globalTick * 0.06F);
         }
         if (finished || globalTick > travelTicks + maxDelay + 2) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55F, 1.75F);
            player.spawnParticle(Particle.PORTAL, player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2.4)), 18, 0.8, 0.45, 0.25, 0.02);
            task.cancel();
         }
      }, 0L, 1L);
   }'''

s = replace_method(s, '   private void openDirectTeleporter(Player player)', new_open)
s = replace_method(s, '   private void animateWorldTeleporter(Player player, List<TeleporterAnimatedEntity> animated)', new_animate)
old_record = '   private record TeleporterAnimatedEntity(UUID entityId, Location from, Location to, float finalWidth, float finalHeight) {\n   }'
new_record = '   private record TeleporterAnimatedEntity(UUID entityId, Location from, Location to, float finalWidth, float finalHeight, int delayTicks) {\n   }'
if old_record not in s:
    raise SystemExit('missing TeleporterAnimatedEntity record')
s = s.replace(old_record, new_record, 1)

p.write_text(s, encoding='utf-8')
