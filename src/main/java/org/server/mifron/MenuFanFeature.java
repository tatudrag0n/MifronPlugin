package org.server.mifron;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * In-world fan menu. Right-clicking the menu item spreads the entries in an
 * arc in front of the player's eyes; left-clicking an entry runs the same
 * action the old chest menu ran (complex screens still open their chest UI).
 */
final class MenuFanFeature implements Listener {
   private final Mifron plugin;
   private final Map<UUID, Session> sessions = new HashMap<>();
   private final Map<UUID, Session> byInteraction = new HashMap<>();

   private static final class Session {
      final UUID owner;
      final Location origin;
      final long expiresAt;
      final double closeDistance;
      final List<Entity> entities = new ArrayList<>();
      final List<ItemStack> actions = new ArrayList<>();
      final Map<UUID, Integer> targets = new HashMap<>();
      BukkitTask task;

      Session(UUID owner, Location origin, long expiresAt, double closeDistance) {
         this.owner = owner;
         this.origin = origin;
         this.expiresAt = expiresAt;
         this.closeDistance = closeDistance;
      }
   }

   MenuFanFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   /** Fan angle layout in degrees, centered on the view direction. */
   static double[] fanAngles(int count, double spreadDegrees) {
      if (count <= 0) return new double[0];
      if (count == 1) return new double[] {0.0};
      double[] angles = new double[count];
      double step = spreadDegrees / (count - 1);
      for (int i = 0; i < count; i++) angles[i] = -spreadDegrees / 2.0 + step * i;
      return angles;
   }

   /** Rotates the horizontal part of a vector around the Y axis. */
   static Vector rotateY(Vector vector, double degrees) {
      double radians = Math.toRadians(degrees);
      double cos = Math.cos(radians);
      double sin = Math.sin(radians);
      return new Vector(
         vector.getX() * cos - vector.getZ() * sin,
         vector.getY(),
         vector.getX() * sin + vector.getZ() * cos
      );
   }

   void open(Player player) {
      if (player == null) return;
      this.close(player.getUniqueId());
      this.plugin.starterGoalsFeature.mark(player, "menu");
      Location eye = player.getEyeLocation();
      Vector forward = eye.getDirection().normalize();
      double radius = Math.max(1.0, this.plugin.getConfig().getDouble("menu.fan-radius", 2.3));
      double spread = Math.max(10.0, this.plugin.getConfig().getDouble("menu.fan-spread-degrees", 130.0));
      double labelScale = Math.max(0.1, this.plugin.getConfig().getDouble("menu.fan-label-scale", 0.9));
      long timeoutMillis = Math.max(1, this.plugin.getConfig().getInt("menu.fan-timeout-seconds", 20)) * 1000L;
      double closeDistance = Math.max(2.0, this.plugin.getConfig().getDouble("menu.fan-close-distance", 6.0));
      List<ItemStack> actions = this.buildActions(player);
      List<String> labels = this.buildLabels(player);
      Session session = new Session(player.getUniqueId(), eye.clone(), System.currentTimeMillis() + timeoutMillis, closeDistance);
      int count = Math.min(actions.size(), labels.size());
      double[] angles = fanAngles(count, spread);
      for (int i = 0; i < count; i++) {
         Vector direction = rotateY(forward, angles[i]).normalize();
         Location position = eye.clone().add(direction.multiply(radius));
         World world = position.getWorld();
         if (world == null) continue;
         ItemStack icon = actions.get(i).clone();
         icon.setAmount(1);
         Location labelPosition = position.clone().add(0.0, 0.45, 0.0);
         String labelText = labels.get(i);
         int index = i;
         ItemDisplay display = world.spawn(position, ItemDisplay.class, entity -> {
            entity.setItemStack(icon);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.3f, 1.3f, 1.3f), new Quaternionf()));
         });
         TextDisplay label = world.spawn(labelPosition, TextDisplay.class, entity -> {
            entity.text(Component.text(labelText));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            float scale = (float) labelScale;
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
         });
         Interaction hit = world.spawn(position, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.0f);
            entity.setResponsive(true);
            entity.setPersistent(false);
         });
         session.entities.add(display);
         session.entities.add(label);
         session.entities.add(hit);
         session.actions.add(actions.get(index));
         session.targets.put(hit.getUniqueId(), index);
         this.byInteraction.put(hit.getUniqueId(), session);
      }
      if (session.entities.isEmpty()) return;
      this.sessions.put(player.getUniqueId(), session);
      session.task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.tick(player.getUniqueId()), 10L, 10L);
      this.plugin.tutorialFeature.onMenuOpen(player);
   }

   private List<ItemStack> buildActions(Player player) {
      JobType job = this.plugin.jobService.typeOf(player);
      int walletMp = this.plugin.getEmeralds(player.getUniqueId());
      List<ItemStack> actions = new ArrayList<>();
      actions.add(this.plugin.actionItem(Material.IRON_DOOR, ChatColor.AQUA + "SHOP", List.of(ChatColor.GRAY + "クリック: ショップを開く（Survivalのみ）"), "menu_shop", null));
      actions.add(this.plugin.actionItem(Material.BUNDLE, ChatColor.GREEN + "ウォレット", List.of(ChatColor.GOLD + "所持MP: " + this.plugin.formatNumber(walletMp) + " MP"), "menu_wallet", null));
      actions.add(this.plugin.actionItem(Material.NETHER_STAR, ChatColor.GOLD + "ステータス", List.of(ChatColor.GRAY + "クリック: ステータス UI"), "menu_status", null));
      actions.add(this.plugin.actionItem(Material.KNOWLEDGE_BOOK, ChatColor.AQUA + "クエスト", List.of(ChatColor.GRAY + "クリック: クエスト（ステータス内）"), "menu_quests", null));
      actions.add(this.plugin.actionItem(Material.WOODEN_HOE, ChatColor.GOLD + "ジョブ: " + job.displayName() + " Lv." + this.plugin.jobService.jobLevel(player.getUniqueId()), List.of(ChatColor.GRAY + "クリック: ジョブを選んで変更"), "menu_job", null));
      actions.add(this.plugin.utilityItemsFeature.gearMenuIcon(player));
      actions.add(this.plugin.actionItem(Material.CHEST, ChatColor.GOLD + "取引", List.of(ChatColor.GRAY + "クリック: 相手を選んで取引申込"), "menu_trade", null));
      actions.add(this.plugin.actionItem(Material.PAPER, ChatColor.AQUA + "\u6295\u7968", List.of(ChatColor.GRAY + "\u30af\u30ea\u30c3\u30af: \u6295\u7968\u30b5\u30a4\u30c8\u3092\u958b\u304f", ChatColor.GRAY + "1\u65e51\u56de\u306e\u6295\u7968\u3067MP\u304c\u3082\u3089\u3048\u307e\u3059"), "menu_vote", null));
      actions.add(this.plugin.actionItem(Material.CLOCK, ChatColor.YELLOW + "\u30ed\u30b0\u30dc", List.of(ChatColor.GRAY + "\u30af\u30ea\u30c3\u30af: \u9023\u7d9a\u30ed\u30b0\u30a4\u30f3\u72b6\u6cc1\u3092\u78ba\u8a8d"), "menu_login_bonus", null));
      actions.add(this.plugin.actionItem(Material.WRITABLE_BOOK, ChatColor.GREEN + "\u76ee\u6a19", List.of(ChatColor.GRAY + "\u30af\u30ea\u30c3\u30af: \u306f\u3058\u3081\u3066\u306e\u76ee\u6a19\u30c1\u30a7\u30c3\u30af\u30ea\u30b9\u30c8"), "menu_goals", null));
      actions.add(this.plugin.actionItem(Material.BOOK, ChatColor.YELLOW + "チュートリアル", List.of(ChatColor.GRAY + "クリック: 基本操作を再表示"), "menu_tutorial", null));
      return actions;
   }

   private List<String> buildLabels(Player player) {
      JobType job = this.plugin.jobService.typeOf(player);
      return List.of("SHOP", "ウォレット", "ステータス", "クエスト", "ジョブ: " + job.displayName() + " Lv." + this.plugin.jobService.jobLevel(player.getUniqueId()), "ギア", "取引", "\u6295\u7968", "\u30ed\u30b0\u30dc", "\u76ee\u6a19", "チュートリアル");
   }

   private void tick(UUID owner) {
      Session session = this.sessions.get(owner);
      if (session == null) return;
      Player player = Bukkit.getPlayer(owner);
      if (player == null || !player.isOnline()) {
         this.close(owner);
         return;
      }
      if (!player.getWorld().equals(session.origin.getWorld())
         || player.getLocation().distanceSquared(session.origin) > session.closeDistance * session.closeDistance) {
         this.close(owner);
         return;
      }
      if (System.currentTimeMillis() > session.expiresAt) {
         this.close(owner);
         return;
      }
      // Fallback detection: the attack packet may not surface as an event on
      // every server build, so poll the interaction's recorded last attack.
      for (Map.Entry<UUID, Integer> entry : session.targets.entrySet()) {
         if (!(Bukkit.getEntity(entry.getKey()) instanceof Interaction hit)) continue;
         Interaction.PreviousInteraction previous = hit.getLastAttack();
         if (previous == null) continue;
         Object attacker = previous.getPlayer();
         UUID attackerId = attacker instanceof Player player2 ? player2.getUniqueId() : attacker instanceof UUID id ? id : null;
         if (attackerId != null && attackerId.equals(owner)) {
            this.select(player, entry.getValue());
            return;
         }
      }
   }

   private void select(Player player, Integer index) {
      if (player == null || index == null) return;
      Session session = this.sessions.get(player.getUniqueId());
      if (session == null || index < 0 || index >= session.actions.size()) return;
      ItemStack action = session.actions.get(index);
      this.close(player.getUniqueId());
      this.plugin.handleMenuUiClick(player, action);
   }

   private void close(UUID owner) {
      Session session = this.sessions.remove(owner);
      if (session == null) return;
      if (session.task != null) session.task.cancel();
      for (Entity entity : session.entities) {
         if (entity == null) continue;
         this.byInteraction.remove(entity.getUniqueId());
         if (entity.isValid()) entity.remove();
      }
   }

   void closeAll() {
      for (UUID owner : List.copyOf(this.sessions.keySet())) this.close(owner);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
   public void onAttack(PrePlayerAttackEntityEvent event) {
      if (!(event.getAttacked() instanceof Interaction hit)) return;
      Session session = this.byInteraction.get(hit.getUniqueId());
      if (session == null) return;
      event.setCancelled(true);
      Integer index = session.targets.get(hit.getUniqueId());
      Player player = event.getPlayer();
      if (index == null || player == null || !player.getUniqueId().equals(session.owner)) return;
      this.select(player, index);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
   public void onInteract(PlayerInteractEntityEvent event) {
      if (event.getRightClicked() instanceof Interaction hit && this.byInteraction.containsKey(hit.getUniqueId())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.close(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      this.close(event.getPlayer().getUniqueId());
   }
}
