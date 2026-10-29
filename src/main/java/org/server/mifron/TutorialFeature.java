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
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;

/**
 * Experiential tutorial world: short explain -&gt; real action -&gt; next goal.
 * All progress lives in data.yml (players.&lt;uuid&gt;.tutorial.*) so re-logs
 * and restarts never break it. Stage rewards are one-time (rewarded flags).
 * The tutorial world uses the NORMAL inventory group, so demo items can never
 * leak into Survival.
 */
public final class TutorialFeature implements Listener {
   private final Mifron plugin;

   static final int FINAL_STAGE = 7;

   private static final String[] INTRO = {
      "§6Mifronチュートリアルへようこそ。7段階で基本を一通り体験できる。",
      "§7各段階で「説明→操作→次の目的」の順に進む。進み具合は保存される。",
      "§7MPは討伐・進捗達成・スクラッチ・レリックなどで獲得できる。 tutorial 中もMPが入る。",
      "§7途中で止めても /tutorial で再開できる。",
   };

   private static final String[] STAGE_TEXT = {
      "",
      "§e[1/7] メニュー: §7配布アイテムを右クリックするとメニューが開く。SHOP・ウォレット・ステータス・クエスト・ジョブ・レリック・チュートリアルが並ぶ。",
      "§e[2/7] 討伐: §7南の金ブロックの上のゾンビを3体倒そう。1体倒すたびにMPがもらえる(30MP)。討伐数はステータスの討伐タブで見られる。",
      "§e[3/7] Elite: §7中央の北側の金ブロックの上の Elite を倒そう。紫の名前が目印。Elite は通常より硬く、ドロップが増える(リンゴ・ダイヤ・エンチャント本など)。討伐で100MPもらえる。",
      "§e[4/7] レリック: §7配布スクラッチを右クリックして削ろう。10%で500MP、90%で50MP。スクラッチは Elite 討伐時にも2%で出る。レリックは /mf relic <id> で入手できる。",
      "§e[5/7] レリック: §7/mf relic でレリックを入手し、持つだけで効果が発動するものがある。レリックは売買不可の特殊アイテム。",
      "§e[6/7] 金床: §7金床で本を素材に、道具へエンチャントを付けよう。結果を左クリックで取ると完了。高度な金床は MP を消費し、上限を超えて付けられる。",
      "§e[7/7] 卒業: §7金のボタンを押して通常 Survival へ出発しよう。レリックは /mf relic <id> で入手でき、持っているだけで効果が発動するものもある。",
   };

   private static final int[] STAGE_REWARD = {0, 50, 150, 300, 150, 100, 500};

   public TutorialFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   public void ensureWorld() {
      String name = this.worldName();
      if (Bukkit.getWorld(name) != null) {
         this.lockDaytime(Bukkit.getWorld(name));
         return;
      }
      WorldCreator creator = new WorldCreator(name).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false);
      World world = Bukkit.createWorld(creator);
      if (world == null) {
         this.plugin.getLogger().warning("[mifron] Tutorial world could not be created.");
         return;
      }
      world.setGameRule(GameRule.KEEP_INVENTORY, true);
      world.setGameRule(GameRule.PVP, false);
      world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
      world.setDifficulty(org.bukkit.Difficulty.NORMAL);
      world.setSpawnLocation(this.spawn(world));
      this.lockDaytime(world);
      this.plugin.getLogger().info("[mifron] Tutorial world ready: " + name);
   }

   /** Tutorial world is always daytime (no cycle, fixed noon-ish time). */
   void lockDaytime(World world) {
      if (world == null) return;
      world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
      world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
      world.setStorm(false);
      world.setThundering(false);
      world.setTime(1000L);
      world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
   }

   // ---- tutorial anchors: fixed, respawning zombie / elite on one gold block ----

   /** Kill anchor (south) and elite anchor (north-east). Mobs stand on top of a gold block. */
   private Location anchor(World world, String key) {
      String base = "tutorial." + key + ".";
      return new Location(world,
         this.plugin.getConfig().getDouble(base + "x", 0.5),
         this.plugin.getConfig().getDouble(base + "y", -60.0),
         this.plugin.getConfig().getDouble(base + "z", 0.5));
   }

   /** Keeps one fixed mob on each anchor; a killed mob is respawned within ~2 seconds. */
   public void startArenaTask() {
      new BukkitRunnable() {
         @Override public void run() {
            World world = Bukkit.getWorld(worldName());
            if (world == null || !enabled()) return;
            ensureAnchorMob(world, anchor(world, "kill-anchor"), false);
            ensureAnchorMob(world, anchor(world, "elite-anchor"), true);
         }
      }.runTaskTimer(this.plugin, 40L, 40L);
   }

   private void ensureAnchorMob(World world, Location loc, boolean elite) {
      loc.getChunk().load(true);
      for (Entity e : world.getNearbyEntities(loc, 1.0, 1.5, 1.0)) {
         if (e instanceof Monster) return;
      }
      // Face the player side (north-ward for the south kill mob, south-ward for the north elite mob).
      float yaw = elite ? 0.0F : 180.0F;
      Location spawnLoc = loc.clone();
      spawnLoc.setYaw(yaw);
      world.spawn(spawnLoc, Zombie.class, zombie -> {
         zombie.setAI(false);
         zombie.setPersistent(true);
         zombie.setRemoveWhenFarAway(false);
         zombie.setShouldBurnInDay(false);
         zombie.setCanPickupItems(false);
         zombie.setRotation(yaw, 0.0F);
      });
      if (elite) this.plugin.eliteMobFeature.elitizeNearest(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), 1.5);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onCombust(EntityCombustEvent event) {
      if (!this.isTutorialWorld(event.getEntity().getWorld())) return;
      if (event.getEntity() instanceof Monster) event.setCancelled(true);
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
         player.sendMessage("§aチュートリアルは完了済みです。受け直す場合は /tutorial restart を実行してください。");
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

   /** Restarts the tutorial from stage 1. Stage rewards are one-time and are not paid again. */
   public void restart(Player player) {
      if (!this.enabled()) {
         player.sendMessage("§cチュートリアルは現在無効です。");
         return;
      }
      Location spawn = this.spawn(null);
      if (spawn == null || spawn.getWorld() == null) {
         player.sendMessage("§cチュートリアルワールドがありません。管理者に連絡してください。");
         return;
      }
      var section = this.plugin.getPlayerSection(player.getUniqueId());
      section.set("tutorial.stage", 1);
      section.set("tutorial.kills", 0);
      section.set("tutorial.stages", null);
      section.set("tutorial.completed", false);
      section.set("tutorial.skipped", false);
      this.plugin.queueDataSave();
      player.teleport(spawn);
      player.sendMessage("§eチュートリアルを最初から受け直します（クリア済み報酬の再付与はありません）。");
      for (String line : INTRO) player.sendMessage(line);
      this.onStageStart(player, 1);
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

   private boolean hasSword(Player player) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (item != null && item.getType().name().endsWith("_SWORD")) return true;
      }
      return false;
   }

   private void onStageStart(Player player, int stage) {
      if (stage < 1 || stage > FINAL_STAGE) return;
      for (String line : STAGE_TEXT[stage].split("\n")) player.sendMessage(line);
      // Anvil stage: hand out a Sharpness VI book so the player has something to combine.
      if (stage == 6) {
         ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
         org.bukkit.inventory.meta.EnchantmentStorageMeta meta =
            (org.bukkit.inventory.meta.EnchantmentStorageMeta) book.getItemMeta();
         meta.addStoredEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 6, true);
         book.setItemMeta(meta);
         for (ItemStack leftover : player.getInventory().addItem(book).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }
         player.sendMessage("§7ダメージ増加VIの本を渡した。金床で剣に合成しよう。");
      }
      // Kill stage: the player needs a weapon (zombies are fixed in place, so the player fights).
      if (stage == 2 && !this.hasSword(player)) {
         for (ItemStack leftover : player.getInventory().addItem(new ItemStack(Material.IRON_SWORD)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
         }
         player.sendMessage("§7鉄の剣を渡した。攻撃は左クリック、倒すまで殴ろう。");
      }
      // Stage 7 hands out its demo item so nobody can get stuck waiting for luck.
      if (stage == 4) {
         org.bukkit.inventory.ItemStack scratch =
            this.plugin.relicFeature.createRelicItem(
               org.server.mifron.RelicFeature.RelicType.SCRATCH);
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

   public void onJobOpen(Player player) {
      if (this.stageOf(player) == 5 && this.inTutorial(player)) this.advance(player);
   }

   public void onSpecialUse(Player player) {
      if (this.stageOf(player) == 4 && this.inTutorial(player)) this.advance(player);
   }

   public void onAnvilUse(Player player) {
      if (this.stageOf(player) == 6 && this.inTutorial(player)) this.advance(player);
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
         this.plugin.depositEmeralds(killer.getUniqueId(), 30);
         killer.sendMessage("§a+30MP 討伐報酬");
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
      } else if (stage == 3) {
         if (this.plugin.eliteMobFeature.isElite(entity)) {
            this.plugin.depositEmeralds(killer.getUniqueId(), 100);
            killer.sendMessage("§dElite討伐！ +100MP");
            this.advance(killer);
         }
      } else {
         // Tutorial world kills also pay a small MP reward outside the two stages.
         this.plugin.depositEmeralds(killer.getUniqueId(), 15);
         killer.sendMessage("§a+15MP 討伐報酬");
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onAdvancement(PlayerAdvancementDoneEvent event) {
      Player player = event.getPlayer();
      String key = event.getAdvancement().getKey().getKey();
      if (key.startsWith("recipes/")) return;
      this.plugin.depositEmeralds(player.getUniqueId(), 50);
      player.sendMessage("§a+50MP 進捗達成: " + key);
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
      Bukkit.broadcastMessage("\u00a76[\u304a\u77e5\u3089\u305b] \u00a7e" + player.getName() + " \u00a7f\u304c\u30c1\u30e5\u30fc\u30c8\u30ea\u30a2\u30eb\u3092\u5352\u696d\u3057\u307e\u3057\u305f!");
      this.plugin.referralService.onTutorialGraduated(player);
      this.sendToSurvival(player);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plugin.starterGoalsFeature.open(player), 80L);
   }
}
