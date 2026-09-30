package org.server.mifron;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Experiential tutorial world: short explain -&gt; real action -&gt; next goal.
 * All progress lives in data.yml (players.&lt;uuid&gt;.tutorial.*) so re-logs
 * and restarts never break it. Stage rewards are one-time (rewarded flags).
 * The tutorial world uses the NORMAL inventory group, so demo items can never
 * leak into Survival.
 */
public final class TutorialFeature implements Listener {
   private final Mifron plugin;

   static final int FINAL_STAGE = 10;

   private static final String[] INTRO = {
      "§6Mifronチュートリアルへようこそ。遊びながら基本を覚えよう。",
      "§7各エリアで短い説明→実際の操作→次の目的の順に進む。",
      "§7進み具合は保存される。/tutorial でいつでも再開できる。",
   };

   private static final String[] STAGE_TEXT = {
      "",
      "§e[1/10] メニュー: §7配布アイテムを右クリックでメニューを開こう。",
      "§e[2/10] 討伐: §7北のMob小屋でゾンビを3体倒そう。",
      "§e[3/10] 売却: §7ドロップ品を持ってデモ棚に右クリックで売却しよう。",
      "§e[4/10] 購入: §7ウォレットを持ってデモ棚を右クリックで1個買おう。",
      "§e[5/10] 探索: §7東のデッキ（Checkpoint）まで歩こう。",
      "§e[6/10] Elite: §7南のElite小屋の elite を倒そう。紫の名前が目印。",
      "§e[7/10] Rare: §7配布スクラッチを右クリックで削ろう。",
      "§e[8/10] Gear: §7メニューからギア画面を開こう。",
      "§e[9/10] 金床: §7高度な金床で合成結果を取ろう。",
      "§e[10/10] 卒業: §7金のボタンを押して通常Survivalへ出発しよう。",
   };

   private static final int[] STAGE_REWARD = {0, 50, 150, 100, 100, 150, 300, 150, 100, 150, 500};

   public TutorialFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   public void ensureWorld() {
      String name = this.worldName();
      if (Bukkit.getWorld(name) != null) return;
      WorldCreator creator = new WorldCreator(name).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false);
      World world = Bukkit.createWorld(creator);
      if (world == null) {
         this.plugin.getLogger().warning("[mifron] Tutorial world could not be created.");
         return;
      }
      world.setGameRule(GameRule.KEEP_INVENTORY, true);
      world.setGameRule(GameRule.PVP, false);
      world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
      world.setDifficulty(org.bukkit.Difficulty.NORMAL);
      world.setSpawnLocation(this.spawn(world));
      this.plugin.getLogger().info("[mifron] Tutorial world ready: " + name);
   }

   public boolean enabled() {
      return this.plugin.getConfig().getBoolean("tutorial.enabled", true);
   }

   public String worldName() {
      return this.plugin.getConfig().getString("tutorial.world", "tutorial");
   }

   public boolean isTutorialWorld(World world) {
      return world != null && world.getName().equalsIgnoreCase(this.worldName());
   }

   public Location spawn(World world) {
      if (world == null) world = Bukkit.getWorld(this.worldName());
      if (world == null) return null;
      double x = this.plugin.getConfig().getDouble("tutorial.spawn.x", 0.5);
      double y = this.plugin.getConfig().getDouble("tutorial.spawn.y", 65.0);
      double z = this.plugin.getConfig().getDouble("tutorial.spawn.z", 0.5);
      return new Location(world, x, y, z, 0.0F, 0.0F);
   }

   public int stageOf(Player player) {
      return this.plugin.getPlayerSection(player.getUniqueId()).getInt("tutorial.stage", 0);
   }

   public boolean completed(Player player) {
      return this.plugin.getPlayerSection(player.getUniqueId()).getBoolean("tutorial.completed", false);
   }

   /** Teleports the player to the tutorial world and shows the current stage. */
   public void resume(Player player) {
      if (!this.enabled()) {
         player.sendMessage("§cチュートリアルは現在無効です。");
         return;
      }
      if (this.completed(player)) {
         player.sendMessage("§aチュートリアルは完了済みです。");
         return;
      }
      Location spawn = this.spawn(null);
      if (spawn == null || spawn.getWorld() == null) {
         player.sendMessage("§cチュートリアルワールドがありません。管理者に連絡してください。");
         return;
      }
      int stage = Math.max(1, Math.min(this.stageOf(player), FINAL_STAGE));
      this.plugin.getPlayerSection(player.getUniqueId()).set("tutorial.stage", stage);
      this.plugin.queueDataSave();
      player.teleport(spawn);
      this.onStageStart(player, stage);
   }

   /** Forfeits remaining rewards and graduates immediately (anti-stuck). */
   public void skip(Player player) {
      if (this.completed(player)) {
         player.sendMessage("§aチュートリアルは完了済みです。");
         return;
      }
      this.plugin.getPlayerSection(player.getUniqueId()).set("tutorial.completed", true);
      this.plugin.getPlayerSection(player.getUniqueId()).set("tutorial.skipped", true);
      this.plugin.queueDataSave();
      this.sendToSurvival(player);
      player.sendMessage("§eチュートリアルをスキップした（残り報酬はなし）。");
   }

   private void advance(Player player) {
      UUID uuid = player.getUniqueId();
      int stage = this.stageOf(player);
      if (stage < 1 || stage > FINAL_STAGE) return;
      var section = this.plugin.getPlayerSection(uuid);
      section.set("tutorial.stages." + stage + ".done", true);
      int reward = stage < STAGE_REWARD.length ? STAGE_REWARD[stage] : 0;
      if (reward > 0 && !section.getBoolean("tutorial.rewarded." + stage, false)) {
         this.plugin.depositEmeralds(uuid, reward);
         section.set("tutorial.rewarded." + stage, true);
         player.sendMessage("§b報酬: +" + this.plugin.formatNumber(reward) + "MP");
      }
      int next = stage + 1;
      section.set("tutorial.stage", Math.min(next, FINAL_STAGE));
      this.plugin.queueDataSave();
      player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
      if (next > FINAL_STAGE) return;
      this.onStageStart(player, next);
   }

   private void onStageStart(Player player, int stage) {
      if (stage < 1 || stage > FINAL_STAGE) return;
      for (String line : STAGE_TEXT[stage].split("\n")) player.sendMessage(line);
      // Stage 7 hands out its demo item so nobody can get stuck waiting for luck.
      if (stage == 7) {
         org.bukkit.inventory.ItemStack scratch =
            this.plugin.specialItemsFeature.createSpecialItem(
               org.server.mifron.SpecialItemsFeature.SpecialType.SCRATCH);
         if (scratch != null) {
            for (ItemStack leftover : player.getInventory().addItem(scratch).values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage("§7スクラッチを受け取った。右クリックで削ろう。");
         }
      }
   }

   private void sendToSurvival(Player player) {
      Location hub = this.plugin.readLocation("world-rules.spawn.main");
      if (hub == null || hub.getWorld() == null) hub = this.plugin.readLocation("hub");
      if (hub == null || hub.getWorld() == null) {
         World survival = Bukkit.getWorld("survival");
         if (survival == null) return;
         hub = survival.getSpawnLocation();
      }
      player.teleport(hub);
   }

   // ---- detection entry points (called from existing handlers) ----

   public void onMenuOpen(Player player) {
      if (this.stageOf(player) == 1 && this.inTutorial(player)) this.advance(player);
   }

   public void onGearOpen(Player player) {
      if (this.stageOf(player) == 8 && this.inTutorial(player)) this.advance(player);
   }

   public void onShopSell(Player player) {
      if (this.stageOf(player) == 3 && this.inTutorial(player)) this.advance(player);
   }

   public void onShopBuy(Player player) {
      if (this.stageOf(player) == 4 && this.inTutorial(player)) this.advance(player);
   }

   public void onSpecialUse(Player player) {
      if (this.stageOf(player) == 7 && this.inTutorial(player)) this.advance(player);
   }

   public void onAnvilUse(Player player) {
      if (this.stageOf(player) == 9 && this.inTutorial(player)) this.advance(player);
   }

   private boolean inTutorial(Player player) {
      return this.enabled() && !this.completed(player) && this.isTutorialWorld(player.getWorld());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (!this.enabled() || this.completed(player)) return;
      if (this.stageOf(player) > 0 && !this.isTutorialWorld(player.getWorld())) {
         player.sendMessage("§eチュートリアル未完了（" + this.stageOf(player) + "/10）。/tutorial で再開できる。");
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMobKill(EntityDeathEvent event) {
      LivingEntity entity = event.getEntity();
      Player killer = entity.getKiller();
      if (killer == null || !this.isTutorialWorld(entity.getWorld())) return;
      if (!(entity instanceof Monster)) return;
      int stage = this.stageOf(killer);
      if (this.completed(killer)) return;
      if (stage == 2) {
         var section = this.plugin.getPlayerSection(killer.getUniqueId());
         int kills = section.getInt("tutorial.kills", 0) + 1;
         section.set("tutorial.kills", kills);
         this.plugin.queueDataSave();
         if (kills >= 3) {
            section.set("tutorial.kills", 0);
            killer.sendMessage("§a3体討伐！");
            this.advance(killer);
         } else {
            killer.sendMessage("§7討伐 " + kills + "/3");
         }
      } else if (stage == 6) {
         if (this.plugin.eliteMobFeature.isElite(entity)) {
            killer.sendMessage("§dElite討伐！");
            this.advance(killer);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      Player player = event.getPlayer();
      if (!this.inTutorial(player) || event.getTo() == null) return;
      int stage = this.stageOf(player);
      if (stage == 0) {
         // First arrival completes the entrance stage instantly.
         this.plugin.getPlayerSection(player.getUniqueId()).set("tutorial.stage", 1);
         this.plugin.queueDataSave();
         for (String line : INTRO) player.sendMessage(line);
         this.onStageStart(player, 1);
         return;
      }
      if (stage == 5) {
         Location to = event.getTo();
         double cx = this.plugin.getConfig().getDouble("tutorial.checkpoint.x", 60.5);
         double cy = this.plugin.getConfig().getDouble("tutorial.checkpoint.y", 65.0);
         double cz = this.plugin.getConfig().getDouble("tutorial.checkpoint.z", 0.5);
         double r = this.plugin.getConfig().getDouble("tutorial.checkpoint.radius", 4.0);
         if (to.getWorld() != null && to.getWorld().getName().equalsIgnoreCase(this.worldName())
            && Math.abs(to.getX() - cx) <= r && Math.abs(to.getY() - cy) <= r + 3.0 && Math.abs(to.getZ() - cz) <= r) {
            player.sendMessage("§aチェックポイント到達！");
            this.advance(player);
         }
         return;
      }
      if (stage == FINAL_STAGE) {
         // Graduation button: gold block in front of the gate.
         Block block = event.getTo().getBlock().getRelative(0, -1, 0);
         Location gate = this.graduationGate(event.getTo().getWorld());
         if (gate != null && block.getWorld().getName().equalsIgnoreCase(this.worldName())
            && block.getX() == gate.getBlockX() && block.getY() == gate.getBlockY() && block.getZ() == gate.getBlockZ()) {
            this.graduate(player);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
   public void onGraduationPress(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;
      Player player = event.getPlayer();
      if (!this.inTutorial(player) || this.stageOf(player) != FINAL_STAGE) return;
      Block clicked = event.getClickedBlock();
      Location gate = this.graduationGate(player.getWorld());
      if (clicked == null || gate == null) return;
      if (clicked.getX() == gate.getBlockX() && clicked.getY() == gate.getBlockY() && clicked.getZ() == gate.getBlockZ()) {
         event.setCancelled(true);
         this.graduate(player);
      }
   }

   private Location graduationGate(World world) {
      if (world == null || !this.isTutorialWorld(world)) return null;
      double x = this.plugin.getConfig().getDouble("tutorial.gate.x", 0.0);
      double y = this.plugin.getConfig().getDouble("tutorial.gate.y", 65.0);
      double z = this.plugin.getConfig().getDouble("tutorial.gate.z", 20.0);
      return new Location(world, x, y, z);
   }

   private void graduate(Player player) {
      if (this.completed(player)) return;
      this.plugin.getPlayerSection(player.getUniqueId()).set("tutorial.completed", true);
      // Graduation bonus goes through the same one-time path as stage rewards.
      var section = this.plugin.getPlayerSection(player.getUniqueId());
      if (!section.getBoolean("tutorial.rewarded.graduate", false)) {
         this.plugin.depositEmeralds(player.getUniqueId(), 500);
         section.set("tutorial.rewarded.graduate", true);
      }
      this.plugin.queueDataSave();
      this.plugin.checkStatTitles(player);
      player.sendMessage("§6チュートリアル完了！通常Survivalへ出発。");
      player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
      this.sendToSurvival(player);
   }
}
