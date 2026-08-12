package org.server.minerva;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

final class ServerPortalFeature implements Listener {
   private final Minerva plugin;
   private final NamespacedKey minervaItemKey;
   private final NamespacedKey frameLabelKey;
   private final NamespacedKey teleporterOptionKey;
   private final NamespacedKey teleporterOwnerKey;
   private final Map<UUID, Long> portalUseCooldowns = new ConcurrentHashMap<>();
   private final Map<UUID, Location> pendingCoordinateTargets = new ConcurrentHashMap<>();
   private final Map<UUID, String> pendingFrameRenameKeys = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> teleporterSelections = new ConcurrentHashMap<>();
   private final Map<UUID, Long> teleporterSelectionExpires = new ConcurrentHashMap<>();
   private final Map<UUID, List<UUID>> teleporterMenuEntities = new ConcurrentHashMap<>();

   ServerPortalFeature(Minerva plugin) {
      this.plugin = plugin;
      this.minervaItemKey = new NamespacedKey(plugin, "item");
      this.frameLabelKey = new NamespacedKey(plugin, "teleporter_frame_label");
      this.teleporterOptionKey = new NamespacedKey(plugin, "teleporter_option");
      this.teleporterOwnerKey = new NamespacedKey(plugin, "teleporter_owner");
   }

   ItemStack createServerWand() {
      ItemStack item = new ItemStack(Material.BLAZE_ROD);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(ChatColor.LIGHT_PURPLE + "サーバーワンド"));
      meta.lore(
         List.of(
            Component.text(ChatColor.GRAY + "Shift+右クリック: 現在地を移動先として記憶"),
            Component.text(ChatColor.GRAY + "エンドポータルフレームを右クリック: 記憶した移動先を登録"),
            Component.text(ChatColor.GRAY + "移動先未記憶でフレーム右クリック: 移動先UI"),
            Component.text(ChatColor.GRAY + "Shift+左クリック: フレームの表示名を変更"),
            Component.text(ChatColor.GRAY + "左クリック: 設定解除 / ポータル削除")
         )
      );
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      meta.getPersistentDataContainer().set(this.minervaItemKey, PersistentDataType.STRING, "server_wand");
      item.setItemMeta(meta);
      return item;
   }

   boolean isServerWand(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? "server_wand".equals(item.getItemMeta().getPersistentDataContainer().get(this.minervaItemKey, PersistentDataType.STRING))
         : false;
   }

   void handleWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("minerva.admin")) {
         player.sendMessage(ChatColor.RED + "権限がありません。");
         event.setCancelled(true);
      } else {
         Block clicked = event.getClickedBlock();
         if (event.getAction().isRightClick()
            && player.isSneaking()
            && (clicked == null || clicked.getType() != Material.END_PORTAL_FRAME)) {
            Location remembered = player.getLocation().clone();
            this.pendingCoordinateTargets.put(player.getUniqueId(), remembered);
            player.sendMessage(ChatColor.GREEN + "移動先を記憶しました: " + this.formatLocation(remembered));
            player.sendMessage(ChatColor.GRAY + "次に、登録したいエンドポータルフレームを右クリックしてください。");
            event.setCancelled(true);
            return;
         }

         Block block = this.resolveWandTargetBlock(event);
         if (block == null) {
            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");
            event.setCancelled(true);
         } else if (block.getType() == Material.END_PORTAL_FRAME) {
            if (event.getAction().isRightClick()) {
               Location remembered = this.pendingCoordinateTargets.remove(player.getUniqueId());
               if (remembered != null) {
                  this.setCoordinateTarget(block, remembered);
                  player.sendMessage(ChatColor.GREEN + "記憶した移動先をこのフレームに登録しました: " + this.formatLocation(remembered));
               } else {
                  this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
                  player.sendMessage(ChatColor.LIGHT_PURPLE + "移動先を選択してください。座標登録は、移動先でShift+右クリック → このフレームを右クリックです。");
               }
            } else if (event.getAction().isLeftClick()) {
               if (player.isSneaking()) {
                  if (!this.hasFrameTarget(block)) {
                     player.sendMessage(ChatColor.YELLOW + "先にこのフレームへ移動先を登録してください。");
                  } else {
                     this.pendingFrameRenameKeys.put(player.getUniqueId(), this.blockKey(block));
                     player.sendMessage(ChatColor.LIGHT_PURPLE + "テレポート先の表示名をチャットに入力してください。");
                     player.sendMessage(ChatColor.GRAY + "reset で自動名に戻す / cancel で中止");
                  }
               } else {
                  this.clearPortalTarget(block);
                  player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");
               }
            }
            event.setCancelled(true);
         } else if (event.getAction().isRightClick()) {
            if (this.isServerPortal(block)) {
               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
               event.setCancelled(true);
            } else {
               block.setType(Material.NETHER_PORTAL, false);
               this.applyServerPortalFacing(block, player);
               this.setServerPortal(block, true);
               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
               player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。移動先サーバーを選択してください。");
               event.setCancelled(true);
            }
         } else {
            if (event.getAction().isLeftClick()) {
               if (!this.isServerPortal(block)) {
                  player.sendMessage(ChatColor.YELLOW + "このブロックはサーバーポータルではありません。");
                  event.setCancelled(true);
                  return;
               }

               this.setServerPortal(block, false);
               block.setType(Material.AIR, false);
               player.sendMessage(ChatColor.GREEN + "サーバーポータルを削除しました。");
               event.setCancelled(true);
            }
         }
      }
   }

   private Block resolveWandTargetBlock(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block clicked = event.getClickedBlock();
      if (clicked != null) {
         if (this.isServerPortal(clicked)) {
            return clicked;
         }

         if (event.getAction().isRightClick()) {
            return clicked;
         }
      }

      Block lookedPortal = this.lookedAtServerPortal(player);
      if (lookedPortal != null) {
         return lookedPortal;
      }

      if (clicked != null) {
         Block nearbyClickedPortal = this.nearbyServerPortal(clicked);
         if (nearbyClickedPortal != null) {
            return nearbyClickedPortal;
         }
      }

      Block nearbyPlayerPortal = this.nearestServerPortal(player.getLocation());
      if (nearbyPlayerPortal != null) {
         return nearbyPlayerPortal;
      } else {
         Block registeredNearby = this.nearestRegisteredServerPortal(player.getLocation(), 6.0);
         if (registeredNearby != null) {
            return registeredNearby;
         } else {
            return clicked != null ? clicked : null;
         }
      }
   }

   boolean isServerPortal(Block block) {
      return block != null && block.getType() == Material.NETHER_PORTAL && this.plugin.data().getStringList("server-portals").contains(this.blockKey(block));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterUse(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
         return;
      }

      ItemStack item = event.getItem();
      if (!this.isTeleporter(item)) {
         return;
      }

      // The Minerva Ender Eye is a UI item, never a throwable vanilla eye.
      event.setCancelled(true);
      Player player = event.getPlayer();
      Block frame = event.getClickedBlock();
      if (frame != null && frame.getType() == Material.END_PORTAL_FRAME) {
         this.useTeleporterFrame(player, frame);
         return;
      }

      this.openDirectTeleporter(player);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterLeftClick(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isLeftClick() || !this.isTeleporter(event.getItem())) {
         return;
      }
      if (this.teleporterMenuEntities.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
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
   }

   private void useTeleporterFrame(Player player, Block frame) {
      Location coordinateTarget = this.coordinateTarget(frame);
      String target = this.serverPortalTarget(frame);
      if (coordinateTarget == null && (target == null || target.isBlank())) {
         player.sendMessage(ChatColor.YELLOW + "このエンドポータルフレームには移動先が設定されていません。");
         return;
      }

      this.clearTeleporterSelection(player);
      this.setFrameEye(frame, true);
      player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8F, 1.1F);
      player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 22, 0.45, 0.65, 0.45, 0.08);
      if (coordinateTarget != null) {
         player.teleport(coordinateTarget);
         player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.05F);
         player.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 30, 0.55, 0.75, 0.55, 0.05);
      } else {
         this.plugin.teleportToConfigLocation(player, target);
      }

      // The inserted eye is only a short visual cue and never remains in the frame.
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.setFrameEye(frame, false));
   }

   private void openDirectTeleporter(Player player) {
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

      // Arrange destinations as a HORIZONTAL arc around the player's view.
      // The old vertical parabola pushed the outer options down and made them hard to aim at.
      // Keep the arc shallow: outer options move slightly closer to the player instead of vertically away.
      int columns = Math.min(8, Math.max(1, destinations.size()));
      int rows = (destinations.size() + columns - 1) / columns;
      double spacing = columns >= 7 ? 0.90 : 1.05;
      Location center = eye.clone().add(forward.clone().multiply(3.05)).add(0.0, -0.28, 0.0);
      Location origin = eye.clone().add(forward.clone().multiply(1.05)).add(0.0, -0.38, 0.0);
      List<UUID> spawned = new ArrayList<>();
      List<TeleporterAnimatedEntity> animated = new ArrayList<>();

      for (int i = 0; i < destinations.size(); i++) {
         TeleportDestination destination = destinations.get(i);
         int row = i / columns;
         int col = i % columns;
         int rowCount = Math.min(columns, destinations.size() - row * columns);
         double horizontal = (col - (rowCount - 1) / 2.0) * spacing;

         // Horizontal semicircle/fan: the center sits furthest forward and the left/right edges
         // bend toward the player. Vertical placement stays almost flat for easy clicking.
         double maxHalfWidth = Math.max(spacing, (rowCount - 1) * spacing / 2.0);
         double normalized = Math.min(1.0, Math.abs(horizontal) / maxHalfWidth);
         double depthTowardPlayer = 1.10 * normalized * normalized;
         // Raise the outer destinations slightly to make the curve visible and keep the
         // middle destination aligned with the player's aim point.
         double vertical = (rows - 1) * 0.64 - row * 1.28 + 0.38 * normalized * normalized;
         Location iconLocation = center.clone()
            .add(right.clone().multiply(horizontal))
            .add(forward.clone().multiply(-depthTowardPlayer))
            .add(0.0, vertical, 0.0);
         int delay = Math.min(6, Math.min(col, rowCount - 1 - col));

         Interaction hitbox = (Interaction)player.getWorld().spawnEntity(origin, EntityType.INTERACTION);
         hitbox.setInteractionWidth(0.08F);
         hitbox.setInteractionHeight(0.08F);
         hitbox.setResponsive(true);
         hitbox.getPersistentDataContainer().set(this.teleporterOptionKey, PersistentDataType.STRING, destination.key());
         hitbox.getPersistentDataContainer().set(this.teleporterOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
         spawned.add(hitbox.getUniqueId());
         animated.add(new TeleporterAnimatedEntity(hitbox.getUniqueId(), origin.clone(), iconLocation.clone(), 1.18F, 1.38F, delay));

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
   }

   private void animateWorldTeleporter(Player player, List<TeleporterAnimatedEntity> animated) {
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
   }

   private void cycleJavaTeleporter(Player player, int delta) {
      List<TeleportDestination> destinations = this.teleporterDestinations();
      if (destinations.isEmpty()) {
         player.sendMessage(ChatColor.YELLOW + "テレポーターに利用できる移動先が設定されていません。");
         return;
      }

      long now = System.currentTimeMillis();
      boolean active = this.teleporterSelectionExpires.getOrDefault(player.getUniqueId(), 0L) > now;
      int current = active ? this.teleporterSelections.getOrDefault(player.getUniqueId(), 0) : 0;
      int next = Math.floorMod(current + delta, destinations.size());
      this.teleporterSelections.put(player.getUniqueId(), next);
      this.teleporterSelectionExpires.put(player.getUniqueId(), now + 12000L);
      this.showJavaTeleporterSelection(player, destinations);
   }

   private void showJavaTeleporterSelection(Player player, List<TeleportDestination> destinations) {
      int index = Math.max(0, Math.min(this.teleporterSelections.getOrDefault(player.getUniqueId(), 0), destinations.size() - 1));
      TeleportDestination destination = destinations.get(index);
      String subtitle = ChatColor.DARK_GRAY + "◀  " + ChatColor.AQUA + "[" + (index + 1) + "/" + destinations.size() + "] " + ChatColor.WHITE + destination.name() + ChatColor.DARK_GRAY + "  ▶";
      player.sendTitle(ChatColor.LIGHT_PURPLE + "◆ TELEPORTER ◆", subtitle, 0, 38, 6);
      player.sendActionBar(Component.text("◀ 左クリック   |   Shift+右クリック: 移動   |   右クリック ▶", NamedTextColor.GRAY));
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55F, 1.25F);
      player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.45, 0.3, 0.02);
   }

   private List<TeleportDestination> teleporterDestinations() {
      ConfigurationSection servers = this.plugin.getConfig().getConfigurationSection("servers");
      if (servers == null) {
         return List.of();
      }

      List<TeleportDestination> destinations = new ArrayList<>();
      for (String key : servers.getKeys(false)) {
         String path = "servers." + key;
         Location location = this.plugin.readLocation(path);
         if (location == null || location.getWorld() == null) {
            continue;
         }
         String name = this.plugin.getConfig().getString(path + ".display-name", "");
         if (name == null || name.isBlank()) {
            name = this.plugin.getConfig().getString(path + ".name", key);
         }
         if (name == null || name.isBlank()) {
            name = key;
         }
         destinations.add(new TeleportDestination(key, name, location));
      }
      Map<String, Integer> fallbackOrder = new ConcurrentHashMap<>();
      int fallbackIndex = 1;
      for (String key : servers.getKeys(false)) {
         fallbackOrder.put(key, fallbackIndex++);
      }
      destinations.sort((a, b) -> {
         int ao = this.plugin.getConfig().getInt("servers." + a.key() + ".order", fallbackOrder.getOrDefault(a.key(), Integer.MAX_VALUE));
         int bo = this.plugin.getConfig().getInt("servers." + b.key() + ".order", fallbackOrder.getOrDefault(b.key(), Integer.MAX_VALUE));
         int order = Integer.compare(ao, bo);
         return order != 0 ? order : a.key().compareToIgnoreCase(b.key());
      });
      return destinations;
   }

   private void teleportDirect(Player player, TeleportDestination destination) {
      if (destination == null || destination.location() == null || destination.location().getWorld() == null) {
         player.sendMessage(ChatColor.RED + "移動先を読み込めませんでした。");
         return;
      }

      this.clearTeleporterSelection(player);
      Location before = player.getLocation();
      player.spawnParticle(Particle.PORTAL, before.clone().add(0.0, 1.0, 0.0), 28, 0.5, 0.7, 0.5, 0.08);
      player.playSound(before, Sound.BLOCK_PORTAL_TRIGGER, 0.45F, 1.35F);
      if (player.teleport(destination.location())) {
         player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.85F, 1.05F);
         player.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 34, 0.55, 0.75, 0.55, 0.05);
         player.sendActionBar(Component.text("移動先: " + destination.name(), NamedTextColor.LIGHT_PURPLE));
      } else {
         player.sendMessage(ChatColor.RED + "テレポートに失敗しました。");
      }
   }

   private void clearTeleporterSelection(Player player) {
      this.teleporterSelections.remove(player.getUniqueId());
      this.teleporterSelectionExpires.remove(player.getUniqueId());
   }

   private boolean hasFrameTarget(Block frame) {
      return this.coordinateTarget(frame) != null || !this.serverPortalTarget(frame).isBlank();
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onFrameRenameChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      String key = this.pendingFrameRenameKeys.remove(player.getUniqueId());
      if (key == null) {
         return;
      }

      event.setCancelled(true);
      String input = PlainTextComponentSerializer.plainText().serialize(event.message());
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         Block frame = this.blockFromKey(key);
         if (frame == null || frame.getType() != Material.END_PORTAL_FRAME) {
            player.sendMessage(ChatColor.RED + "対象のエンドポータルフレームが見つかりませんでした。");
            return;
         }

         String normalized = input == null ? "" : input.trim();
         if (normalized.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "表示名の変更を中止しました。");
            return;
         }

         if (normalized.equalsIgnoreCase("reset") || normalized.equalsIgnoreCase("auto")) {
            this.setFrameDisplayName(frame, null);
            player.sendMessage(ChatColor.GREEN + "表示名を自動名に戻しました。");
            return;
         }

         String clean = this.sanitizeFrameName(normalized);
         if (clean.isBlank()) {
            player.sendMessage(ChatColor.RED + "表示名が空です。もう一度Shift+左クリックから設定してください。");
            return;
         }
         this.setFrameDisplayName(frame, clean);
         player.sendMessage(ChatColor.GREEN + "テレポート先の表示名を「" + clean + "」に変更しました。");
      });
   }

   @EventHandler
   public void onTeleporterPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.pendingCoordinateTargets.remove(uuid);
      this.pendingFrameRenameKeys.remove(uuid);
      this.teleporterSelections.remove(uuid);
      this.teleporterSelectionExpires.remove(uuid);
      this.closeWorldTeleporter(event.getPlayer());
   }

   private String sanitizeFrameName(String raw) {
      if (raw == null) {
         return "";
      }
      String clean = ChatColor.stripColor(raw.replace("§", ""));
      if (clean == null) {
         clean = raw.replace("§", "");
      }
      clean = clean.replaceAll("\\p{Cntrl}", "").trim();
      return clean.length() > 40 ? clean.substring(0, 40) : clean;
   }

   private boolean isTeleporter(ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && "teleporter".equals(item.getItemMeta().getPersistentDataContainer().get(this.minervaItemKey, PersistentDataType.STRING));
   }

   private void setFrameEye(Block frame, boolean eye) {
      if (frame != null && frame.getType() == Material.END_PORTAL_FRAME && frame.getBlockData() instanceof EndPortalFrame data) {
         data.setEye(eye);
         frame.setBlockData(data, false);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onServerPortalTeleport(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.NETHER_PORTAL) {
         if (this.nearestServerPortal(event.getFrom()) != null) {
            event.setCancelled(true);
            this.tryUseServerPortal(event.getPlayer(), true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onServerPortalMove(PlayerMoveEvent event) {
      if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
         this.tryUseServerPortal(event.getPlayer(), true);
      }
   }

   private boolean tryUseServerPortal(Player player, boolean notifyIfUnset) {
      long now = System.currentTimeMillis();
      long nextAllowed = this.portalUseCooldowns.getOrDefault(player.getUniqueId(), 0L);
      if (nextAllowed > now) {
         return false;
      }

      Block portal = this.nearestServerPortal(player.getLocation());
      if (portal == null) {
         return false;
      }

      String target = this.serverPortalTarget(portal);
      if (target != null && !target.isBlank()) {
         this.portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
         this.plugin.teleportToConfigLocation(player, target);
         return true;
      }

      this.portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
      if (notifyIfUnset) {
         player.sendMessage(ChatColor.YELLOW + "このサーバーポータルの移動先が未設定です。");
      }

      return true;
   }

   private void applyServerPortalFacing(Block block, Player player) {
      if (block.getBlockData() instanceof Orientable orientable) {
         double var8 = player.getLocation().getX() - (block.getX() + 0.5);
         double dz = player.getLocation().getZ() - (block.getZ() + 0.5);
         orientable.setAxis(Math.abs(var8) > Math.abs(dz) ? Axis.Z : Axis.X);
         block.setBlockData(orientable, false);
      }
   }

   private boolean isNearServerPortal(Location location) {
      return this.nearestServerPortal(location) != null;
   }

   private Block nearestServerPortal(Location location) {
      return location != null && location.getWorld() != null ? this.nearbyServerPortal(location.getBlock()) : null;
   }

   private Block nearbyServerPortal(Block base) {
      if (base == null) {
         return null;
      }

      for (int x = -1; x <= 1; x++) {
         for (int y = -2; y <= 2; y++) {
            for (int z = -1; z <= 1; z++) {
               Block candidate = base.getRelative(x, y, z);
               if (this.isServerPortal(candidate)) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }

   private Block lookedAtServerPortal(Player player) {
      Location cursor = player.getEyeLocation();
      Vector step = cursor.getDirection().normalize().multiply(0.25);

      for (int i = 0; i < 40; i++) {
         cursor.add(step);
         Block block = cursor.getBlock();
         if (this.isServerPortal(block)) {
            return block;
         }

         Block nearby = this.nearbyServerPortal(block);
         if (nearby != null && nearby.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 2.25) {
            return nearby;
         }

         for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block relative = block.getRelative(face);
            if (this.isServerPortal(relative) && relative.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 1.25) {
               return relative;
            }
         }
      }

      return null;
   }

   private Block nearestRegisteredServerPortal(Location origin, double radius) {
      if (origin != null && origin.getWorld() != null) {
         double maxDistanceSquared = radius * radius;
         Block nearest = null;
         double nearestDistanceSquared = Double.MAX_VALUE;

         for (String key : this.plugin.data().getStringList("server-portals")) {
            Block block = this.blockFromKey(key);
            if (block != null && block.getWorld() != null && block.getWorld().equals(origin.getWorld()) && block.getType() == Material.NETHER_PORTAL) {
               double distanceSquared = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin);
               if (distanceSquared <= maxDistanceSquared && distanceSquared < nearestDistanceSquared) {
                  nearest = block;
                  nearestDistanceSquared = distanceSquared;
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private void setServerPortal(Block block, boolean enabled) {
      List<String> portals = new ArrayList<>(this.plugin.data().getStringList("server-portals"));
      Set<String> keys = this.portalClusterKeys(block);
      if (enabled) {
         for (String key : keys) {
            if (!portals.contains(key)) {
               portals.add(key);
            }
         }
      } else {
         portals.removeAll(keys);

         for (String key : keys) {
            this.plugin.data().set(this.serverPortalTargetPath(key), null);
         }
      }

      this.plugin.data().set("server-portals", portals);
      this.plugin.saveData();
   }

   void setServerPortalTarget(String portalKey, String targetPath) {
      if (portalKey != null && !portalKey.isBlank() && targetPath != null && !targetPath.isBlank()) {
         Block block = this.blockFromKey(portalKey);
         if (block == null) {
            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);
            this.plugin.data().set(this.coordinateTargetPath(portalKey), null);
            this.plugin.saveData();
         } else if (block.getType() == Material.END_PORTAL_FRAME) {
            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);
            this.plugin.data().set(this.coordinateTargetPath(portalKey), null);
            this.plugin.saveData();
            this.refreshFrameLabel(block);
         } else {
            for (String key : this.portalClusterKeys(block)) {
               this.plugin.data().set(this.serverPortalTargetPath(key), targetPath);
            }

            this.plugin.saveData();
         }
      }
   }

   private void setCoordinateTarget(Block block, Location location) {
      if (block == null || block.getType() != Material.END_PORTAL_FRAME || location == null || location.getWorld() == null) {
         return;
      }

      String key = this.blockKey(block);
      String path = this.coordinateTargetPath(key);
      this.plugin.data().set(this.serverPortalTargetPath(key), null);
      this.plugin.data().set(path + ".world", location.getWorld().getName());
      this.plugin.data().set(path + ".x", location.getX());
      this.plugin.data().set(path + ".y", location.getY());
      this.plugin.data().set(path + ".z", location.getZ());
      this.plugin.data().set(path + ".yaw", location.getYaw());
      this.plugin.data().set(path + ".pitch", location.getPitch());
      this.plugin.saveData();
      this.refreshFrameLabel(block);
   }

   private Location coordinateTarget(Block block) {
      if (block == null) {
         return null;
      }

      String path = this.coordinateTargetPath(this.blockKey(block));
      String worldName = this.plugin.data().getString(path + ".world", "");
      if (worldName.isBlank()) {
         return null;
      }

      World world = this.plugin.getServer().getWorld(worldName);
      if (world == null) {
         return null;
      }

      return new Location(
         world,
         this.plugin.data().getDouble(path + ".x"),
         this.plugin.data().getDouble(path + ".y"),
         this.plugin.data().getDouble(path + ".z"),
         (float)this.plugin.data().getDouble(path + ".yaw"),
         (float)this.plugin.data().getDouble(path + ".pitch")
      );
   }

   private String coordinateTargetPath(String key) {
      return "server-portal-coordinate-targets." + key;
   }

   private String frameDisplayNamePath(String key) {
      return "server-portal-display-names." + key;
   }

   private String frameDisplayName(Block frame) {
      return frame == null ? "" : this.plugin.data().getString(this.frameDisplayNamePath(this.blockKey(frame)), "");
   }

   private void setFrameDisplayName(Block frame, String name) {
      if (frame == null) {
         return;
      }
      String clean = name == null ? "" : this.sanitizeFrameName(name);
      this.plugin.data().set(this.frameDisplayNamePath(this.blockKey(frame)), clean.isBlank() ? null : clean);
      this.plugin.saveData();
      this.refreshFrameLabel(frame);
   }

   private void clearPortalTarget(Block block) {
      if (block != null) {
         String key = this.blockKey(block);
         this.plugin.data().set(this.serverPortalTargetPath(key), null);
         this.plugin.data().set(this.coordinateTargetPath(key), null);
         this.plugin.data().set(this.frameDisplayNamePath(key), null);
         this.plugin.saveData();
         this.setFrameEye(block, false);
         this.refreshFrameLabel(block);
      }
   }

   private String serverPortalTarget(Block block) {
      return this.plugin.data().getString(this.serverPortalTargetPath(this.blockKey(block)), "");
   }

   private String serverPortalTargetPath(String key) {
      return "server-portal-targets." + key;
   }

   private void refreshFrameLabel(Block frame) {
      if (frame == null || frame.getWorld() == null) {
         return;
      }

      String key = this.blockKey(frame);
      for (Entity entity : frame.getWorld().getNearbyEntities(frame.getLocation().add(0.5, 1.5, 0.5), 2.0, 2.0, 2.0)) {
         if (entity instanceof TextDisplay display
            && key.equals(display.getPersistentDataContainer().get(this.frameLabelKey, PersistentDataType.STRING))) {
            display.remove();
         }
      }

      Location direct = this.coordinateTarget(frame);
      String named = this.serverPortalTarget(frame);
      if (direct == null && (named == null || named.isBlank())) {
         return;
      }

      String detail = direct != null ? this.formatLocation(direct) : this.namedTargetLabel(named);
      String custom = this.frameDisplayName(frame);
      Component label = Component.text("▶ ", NamedTextColor.LIGHT_PURPLE);
      if (custom != null && !custom.isBlank()) {
         label = label.append(Component.text(custom, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text(detail, NamedTextColor.GRAY));
      } else {
         label = label.append(Component.text(detail, NamedTextColor.WHITE));
      }

      TextDisplay display = (TextDisplay)frame.getWorld().spawnEntity(frame.getLocation().add(0.5, 1.45, 0.5), EntityType.TEXT_DISPLAY);
      display.text(label);
      display.setBillboard(Display.Billboard.CENTER);
      display.setSeeThrough(true);
      display.setShadowed(true);
      display.setPersistent(true);
      display.setLineWidth(240);
      display.getPersistentDataContainer().set(this.frameLabelKey, PersistentDataType.STRING, key);
   }

   private String namedTargetLabel(String targetPath) {
      if (targetPath == null || targetPath.isBlank()) {
         return "未設定";
      }

      String worldName = this.plugin.getConfig().getString(targetPath + ".world", "");
      if (!worldName.isBlank()) {
         double x = this.plugin.getConfig().getDouble(targetPath + ".x");
         double y = this.plugin.getConfig().getDouble(targetPath + ".y");
         double z = this.plugin.getConfig().getDouble(targetPath + ".z");
         return worldName + "  " + this.formatCoordinates(x, y, z);
      }

      int dot = targetPath.lastIndexOf('.');
      return dot >= 0 ? targetPath.substring(dot + 1) : targetPath;
   }

   private String formatLocation(Location location) {
      return location.getWorld().getName() + "  " + this.formatCoordinates(location.getX(), location.getY(), location.getZ());
   }

   private String formatCoordinates(double x, double y, double z) {
      return "X:" + Math.round(x) + " Y:" + Math.round(y) + " Z:" + Math.round(z);
   }

   private Set<String> portalClusterKeys(Block origin) {
      Set<String> keys = new HashSet<>();
      this.collectPortalCluster(origin, keys, new HashSet<>());
      return keys;
   }

   private void collectPortalCluster(Block block, Set<String> keys, Set<String> visited) {
      if (block != null && block.getType() == Material.NETHER_PORTAL && visited.add(this.blockKey(block))) {
         keys.add(this.blockKey(block));

         for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            this.collectPortalCluster(block.getRelative(face), keys, visited);
         }
      }
   }

   private String blockKey(Block block) {
      return block.getWorld().getUID() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
   }

   private Block blockFromKey(String key) {
      String[] parts = key.split(",");
      if (parts.length != 4) {
         return null;
      }

      try {
         UUID worldId = UUID.fromString(parts[0]);
         World world = this.plugin.getServer().getWorld(worldId);
         return world == null ? null : world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private record TeleportDestination(String key, String name, Location location) {
   }

   private record TeleporterAnimatedEntity(UUID entityId, Location from, Location to, float finalWidth, float finalHeight, int delayTicks) {
   }
}
