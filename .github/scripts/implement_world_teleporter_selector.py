from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing patch target: {label}')
    return text.replace(old, new, 1)


def method_span(text, signature):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'missing method: {signature}')
    brace = text.find('{', start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == '{': depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f'unterminated method: {signature}')


def replace_method(text, signature, replacement):
    a, b = method_span(text, signature)
    return text[:a] + replacement + text[b:]

p = Path('src/main/java/org/server/minerva/ServerPortalFeature.java')
s = p.read_text(encoding='utf-8')

# imports
if 'org.bukkit.entity.Interaction;' not in s:
    s = s.replace('import org.bukkit.entity.EntityType;\n', 'import org.bukkit.entity.EntityType;\nimport org.bukkit.entity.Interaction;\nimport org.bukkit.entity.ItemDisplay;\n', 1)
if 'org.bukkit.event.entity.EntityDamageByEntityEvent;' not in s:
    s = s.replace('import org.bukkit.event.Listener;\n', 'import org.bukkit.event.Listener;\nimport org.bukkit.event.entity.EntityDamageByEntityEvent;\n', 1)
if 'org.bukkit.event.player.PlayerInteractEntityEvent;' not in s:
    s = s.replace('import org.bukkit.event.player.PlayerInteractEvent;\n', 'import org.bukkit.event.player.PlayerInteractEvent;\nimport org.bukkit.event.player.PlayerInteractEntityEvent;\n', 1)

# keys + entity tracking
s = replace_once(
    s,
    '   private final NamespacedKey frameLabelKey;\n',
    '   private final NamespacedKey frameLabelKey;\n   private final NamespacedKey teleporterOptionKey;\n   private final NamespacedKey teleporterOwnerKey;\n',
    'selector keys fields'
)
s = replace_once(
    s,
    '   private final Map<UUID, Long> teleporterSelectionExpires = new ConcurrentHashMap<>();\n',
    '   private final Map<UUID, Long> teleporterSelectionExpires = new ConcurrentHashMap<>();\n   private final Map<UUID, List<UUID>> teleporterMenuEntities = new ConcurrentHashMap<>();\n',
    'selector tracking field'
)
s = replace_once(
    s,
    '      this.frameLabelKey = new NamespacedKey(plugin, "teleporter_frame_label");\n',
    '      this.frameLabelKey = new NamespacedKey(plugin, "teleporter_frame_label");\n      this.teleporterOptionKey = new NamespacedKey(plugin, "teleporter_option");\n      this.teleporterOwnerKey = new NamespacedKey(plugin, "teleporter_owner");\n',
    'selector key init'
)

# replace direct teleporter opening with world-space icons for every platform
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

      int columns = Math.min(5, Math.max(1, destinations.size()));
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

      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.closeWorldTeleporter(player), 20L * 12L);
   }'''
s = replace_method(s, '   private void openDirectTeleporter(Player player)', new_open)

# Old left-click cycling becomes selector attack handler. Keep item left-click cancelled if menu absent.
new_left = r'''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterLeftClick(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isLeftClick() || !this.isTeleporter(event.getItem())) {
         return;
      }
      if (this.teleporterMenuEntities.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }'''
s = replace_method(s, '   public void onTeleporterLeftClick(PlayerInteractEvent event)', new_left)

# insert world selector event/helper methods before useTeleporterFrame
anchor = '   private void useTeleporterFrame(Player player, Block frame) {'
if 'public void onWorldTeleporterHit(EntityDamageByEntityEvent event)' not in s:
    helpers = r'''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onWorldTeleporterHit(EntityDamageByEntityEvent event) {
      if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof Interaction interaction)) {
         return;
      }
      String owner = interaction.getPersistentDataContainer().get(this.teleporterOwnerKey, PersistentDataType.STRING);
      String key = interaction.getPersistentDataContainer().get(this.teleporterOptionKey, PersistentDataType.STRING);
      if (owner == null || key == null || !owner.equals(player.getUniqueId().toString())) {
         return;
      }

      event.setCancelled(true);
      TeleportDestination destination = this.teleporterDestinations().stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
      if (destination == null) {
         player.sendMessage(ChatColor.RED + "この移動先は現在利用できません。");
         this.closeWorldTeleporter(player);
         return;
      }
      this.closeWorldTeleporter(player);
      this.teleportDirect(player, destination);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onWorldTeleporterRightClick(PlayerInteractEntityEvent event) {
      if (!(event.getRightClicked() instanceof Interaction interaction)) {
         return;
      }
      String owner = interaction.getPersistentDataContainer().get(this.teleporterOwnerKey, PersistentDataType.STRING);
      if (owner != null && owner.equals(event.getPlayer().getUniqueId().toString())) {
         event.setCancelled(true);
         event.getPlayer().sendActionBar(Component.text("左クリックでテレポート", NamedTextColor.GRAY));
      }
   }

   private void closeWorldTeleporter(Player player) {
      if (player == null) {
         return;
      }
      List<UUID> ids = this.teleporterMenuEntities.remove(player.getUniqueId());
      if (ids == null) {
         return;
      }
      for (UUID id : ids) {
         for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
               entity.remove();
               break;
            }
         }
      }
   }

   private Material teleporterIcon(TeleportDestination destination) {
      String key = destination.key().toLowerCase();
      if (key.contains("survival")) return Material.GRASS_BLOCK;
      if (key.contains("ffa") || key.contains("pvp")) return Material.IRON_SWORD;
      if (key.contains("athletic") || key.contains("parkour")) return Material.FEATHER;
      if (key.contains("mini")) return Material.SLIME_BALL;
      if (key.contains("mod")) return Material.REDSTONE;
      if (key.contains("hub") || key.contains("lobby")) return Material.NETHER_STAR;
      return Material.ENDER_PEARL;
   }

'''
    s = replace_once(s, anchor, helpers + anchor, 'world selector helpers')

# cleanup on quit
quit_anchor = '      this.teleporterSelectionExpires.remove(uuid);\n'
if 'this.closeWorldTeleporter(event.getPlayer());' not in s:
    s = replace_once(s, quit_anchor, quit_anchor + '      this.closeWorldTeleporter(event.getPlayer());\n', 'quit menu cleanup')

# Utility item lore: new operation hint
up = Path('src/main/java/org/server/minerva/UtilityItemsFeature.java')
us = up.read_text(encoding='utf-8')
us = us.replace('ChatColor.GRAY + "右クリック: テレポート先を選択",', 'ChatColor.GRAY + "右クリック: 目の前に移動先を表示",\n               ChatColor.GRAY + "表示されたアイテムを左クリック: テレポート",', 1)
up.write_text(us, encoding='utf-8')

p.write_text(s, encoding='utf-8')
