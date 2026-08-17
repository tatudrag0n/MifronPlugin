from pathlib import Path

p = Path('src/main/java/org/server/mifron/ServerPortalFeature.java')
s = p.read_text(encoding='utf-8')

old = '''   private Material teleporterIcon(TeleportDestination destination) {
      String key = destination.key().toLowerCase();
      if (key.contains("survival")) return Material.GRASS_BLOCK;
      if (key.contains("ffa") || key.contains("pvp")) return Material.IRON_SWORD;
      if (key.contains("athletic") || key.contains("parkour")) return Material.FEATHER;
      if (key.contains("mini")) return Material.SLIME_BALL;
      if (key.contains("mod")) return Material.REDSTONE;
      if (key.contains("hub") || key.contains("lobby")) return Material.NETHER_STAR;
      return Material.ENDER_PEARL;
   }'''
new = '''   private Material teleporterIcon(TeleportDestination destination) {
      String configured = this.plugin.getConfig().getString("servers." + destination.key() + ".icon", "");
      if (configured != null && !configured.isBlank()) {
         Material material = Material.matchMaterial(configured.trim());
         if (material != null && material.isItem()) {
            return material;
         }
      }

      String key = destination.key().toLowerCase();
      if (key.contains("survival")) return Material.GRASS_BLOCK;
      if (key.contains("ffa") || key.contains("pvp")) return Material.IRON_SWORD;
      if (key.contains("athletic") || key.contains("parkour")) return Material.FEATHER;
      if (key.contains("mini")) return Material.SLIME_BALL;
      if (key.contains("mod")) return Material.REDSTONE;
      if (key.contains("hub") || key.contains("lobby")) return Material.NETHER_STAR;
      return Material.ENDER_PEARL;
   }'''
if old not in s:
    raise SystemExit('teleporterIcon target missing')
s = s.replace(old, new, 1)

old_loop = '''      int columns = Math.min(5, Math.max(1, destinations.size()));
      int rows = (destinations.size() + columns - 1) / columns;
      Location center = eye.clone().add(forward.clone().multiply(3.0)).add(0.0, -0.35, 0.0);
      List<UUID> spawned = new ArrayList<>();

      for (int i = 0; i < destinations.size(); i++) {
         TeleportDestination destination = destinations.get(i);
         int row = i / columns;
         int col = i % columns;
         int rowCount = Math.min(columns, destinations.size() - row * columns);
         double horizontal = (col - (rowCount - 1) / 2.0) * 1.15;
         double vertical = (rows - 1) * 0.65 - row * 1.3;
         Location iconLocation = center.clone().add(right.clone().multiply(horizontal)).add(0.0, vertical, 0.0);

         Interaction hitbox = (Interaction)player.getWorld().spawnEntity(iconLocation, EntityType.INTERACTION);
         hitbox.setInteractionWidth(0.95F);
         hitbox.setInteractionHeight(1.15F);
         hitbox.setResponsive(true);
         hitbox.getPersistentDataContainer().set(this.teleporterOptionKey, PersistentDataType.STRING, destination.key());
         hitbox.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(hitbox.getUniqueId());

         ItemDisplay icon = (ItemDisplay)player.getWorld().spawnEntity(iconLocation.clone().add(0.0, 0.18, 0.0), EntityType.ITEM_DISPLAY);
         icon.setItemStack(new ItemStack(this.teleporterIcon(destination)));
         icon.setBillboard(Display.Billboard.CENTER);
         icon.setViewRange(0.8F);
         icon.setShadowStrength(0.6F);
         icon.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(icon.getUniqueId());

         TextDisplay label = (TextDisplay)player.getWorld().spawnEntity(iconLocation.clone().add(0.0, -0.48, 0.0), EntityType.TEXT_DISPLAY);
         label.text(Component.text(destination.name(), NamedTextColor.WHITE));
         label.setBillboard(Display.Billboard.CENTER);
         label.setSeeThrough(true);
         label.setShadowed(true);
         label.setViewRange(0.8F);
         label.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(label.getUniqueId());
      }

      this.teleporterMenuEntities.put(player.getUniqueId(), spawned);
      player.sendActionBar(Component.text("移動したいアイテムを左クリック", NamedTextColor.LIGHT_PURPLE));
      player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.65F, 1.35F);
      player.spawnParticle(Particle.PORTAL, center, 24, 1.5, 0.8, 0.25, 0.02);

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.closeWorldTeleporter(player), 20L * 12L);'''
new_loop = '''      int columns = Math.min(5, Math.max(1, destinations.size()));
      int rows = (destinations.size() + columns - 1) / columns;
      Location center = eye.clone().add(forward.clone().multiply(3.0)).add(0.0, -0.35, 0.0);
      Location origin = eye.clone().add(forward.clone().multiply(1.15)).add(0.0, -0.2, 0.0);
      List<UUID> spawned = new ArrayList<>();
      List<TeleporterAnimatedEntity> animated = new ArrayList<>();

      for (int i = 0; i < destinations.size(); i++) {
         TeleportDestination destination = destinations.get(i);
         int row = i / columns;
         int col = i % columns;
         int rowCount = Math.min(columns, destinations.size() - row * columns);
         double horizontal = (col - (rowCount - 1) / 2.0) * 1.15;
         double vertical = (rows - 1) * 0.65 - row * 1.3;
         Location iconLocation = center.clone().add(right.clone().multiply(horizontal)).add(0.0, vertical, 0.0);

         Interaction hitbox = (Interaction)player.getWorld().spawnEntity(origin, EntityType.INTERACTION);
         hitbox.setInteractionWidth(0.15F);
         hitbox.setInteractionHeight(0.15F);
         hitbox.setResponsive(true);
         hitbox.getPersistentDataContainer().set(this.teleporterOptionKey, PersistentDataType.STRING, destination.key());
         hitbox.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(hitbox.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(hitbox.getUniqueId(), origin.clone(), iconLocation.clone(), 0.95F, 1.15F));

         ItemDisplay icon = (ItemDisplay)player.getWorld().spawnEntity(origin.clone().add(0.0, 0.18, 0.0), EntityType.ITEM_DISPLAY);
         icon.setItemStack(new ItemStack(this.teleporterIcon(destination)));
         icon.setBillboard(Display.Billboard.CENTER);
         icon.setViewRange(0.8F);
         icon.setShadowStrength(0.6F);
         icon.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(icon.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(icon.getUniqueId(), origin.clone().add(0.0, 0.18, 0.0), iconLocation.clone().add(0.0, 0.18, 0.0), 0.0F, 0.0F));

         TextDisplay label = (TextDisplay)player.getWorld().spawnEntity(origin.clone().add(0.0, -0.48, 0.0), EntityType.TEXT_DISPLAY);
         label.text(Component.text(destination.name(), NamedTextColor.WHITE));
         label.setBillboard(Display.Billboard.CENTER);
         label.setSeeThrough(true);
         label.setShadowed(true);
         label.setViewRange(0.8F);
         label.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(label.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(label.getUniqueId(), origin.clone().add(0.0, -0.48, 0.0), iconLocation.clone().add(0.0, -0.48, 0.0), 0.0F, 0.0F));
      }

      this.teleporterMenuEntities.put(player.getUniqueId(), spawned);
      this.animateWorldTeleporter(player, animated);
      player.sendActionBar(Component.text("移動したいアイテムを左クリック", NamedTextColor.LIGHT_PURPLE));
      player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.65F, 1.35F);
      player.spawnParticle(Particle.PORTAL, origin, 20, 0.35, 0.45, 0.35, 0.03);

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.closeWorldTeleporter(player), 20L * 12L);'''
if old_loop not in s:
    raise SystemExit('open selector loop target missing')
s = s.replace(old_loop, new_loop, 1)

anchor = '''   private void cycleJavaTeleporter(Player player, int delta) {'''
helper = '''   private void animateWorldTeleporter(Player player, List<TeleporterAnimatedEntity> animated) {
      final int animationTicks = 9;
      final int[] tick = new int[]{0};
      this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task -> {
         if (!player.isOnline() || !this.teleporterMenuEntities.containsKey(player.getUniqueId())) {
            task.cancel();
            return;
         }

         tick[0]++;
         double raw = Math.min(1.0, tick[0] / (double)animationTicks);
         double eased = 1.0 - Math.pow(1.0 - raw, 3.0);
         for (TeleporterAnimatedEntity moving : animated) {
            Entity entity = Bukkit.getEntity(moving.entityId());
            if (entity == null) {
               continue;
            }
            Location from = moving.from();
            Location to = moving.to();
            Location current = from.clone().add(
               (to.getX() - from.getX()) * eased,
               (to.getY() - from.getY()) * eased,
               (to.getZ() - from.getZ()) * eased
            );
            entity.teleport(current);
            if (entity instanceof Interaction interaction && raw >= 1.0) {
               interaction.setInteractionWidth(moving.finalWidth());
               interaction.setInteractionHeight(moving.finalHeight());
            }
         }

         if (tick[0] == 2 || tick[0] == 5 || tick[0] == 8) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 0.9F + tick[0] * 0.07F);
         }
         if (raw >= 1.0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F);
            task.cancel();
         }
      }, 0L, 1L);
   }

'''
if anchor not in s:
    raise SystemExit('animation helper anchor missing')
s = s.replace(anchor, helper + anchor, 1)

record_old = '''   private record TeleportDestination(String key, String name, Location location) {
   }
}'''
record_new = '''   private record TeleportDestination(String key, String name, Location location) {
   }

   private record TeleporterAnimatedEntity(UUID entityId, Location from, Location to, float finalWidth, float finalHeight) {
   }
}'''
if record_old not in s:
    raise SystemExit('record target missing')
s = s.replace(record_old, record_new, 1)

p.write_text(s, encoding='utf-8')
