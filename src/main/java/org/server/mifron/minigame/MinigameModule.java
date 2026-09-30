package org.server.mifron.minigame;

import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.server.mifron.Mifron;
import org.server.mifron.MinigameBridge;

/**
 * Real {@link MinigameBridge} backed by the in-tree Minigame module.
 * Owns the FFA / Athletic / Slot managers so core never references them.
 */
public final class MinigameModule implements MinigameBridge {
   private final Mifron plugin;
   private final FfaManager ffaManager;
   private final FfaListener ffaListener;
   private final SlotMachineManager slotMachineManager;
   private final AthleticManager athleticManager;

   public MinigameModule(Mifron plugin) {
      this.plugin = plugin;
      this.ffaManager = new FfaManager(plugin);
      this.ffaListener = new FfaListener(plugin, this.ffaManager);
      this.slotMachineManager = new SlotMachineManager(plugin);
      this.athleticManager = new AthleticManager(plugin);
   }

   @Override
   public String name() {
      return "minigame";
   }

   @Override
   public boolean available() {
      return true;
   }

   @Override
   public void loadAll() {
      this.ffaManager.load();
      this.athleticManager.load();
   }

   @Override
   public void shutdownAll() {
      this.ffaManager.shutdown();
      this.athleticManager.shutdown();
   }

   @Override
   public void reloadAll() {
      this.ffaManager.load();
      this.athleticManager.load();
   }

   @Override
   public void registerListeners() {
      Bukkit.getPluginManager().registerEvents(this.ffaListener, this.plugin);
      Bukkit.getPluginManager().registerEvents(this.slotMachineManager, this.plugin);
      Bukkit.getPluginManager().registerEvents(this.athleticManager, this.plugin);
   }

   @Override
   public boolean handleFfaCommand(CommandSender sender, String[] args) {
      return this.ffaManager.handleCommand(sender, args);
   }

   @Override
   public boolean handleAthleticCommand(Player player, String[] args) {
      return this.athleticManager.handleCommand(player, args);
   }

   @Override
   public List<String> tabCompleteFfa(String[] args, CommandSender sender) {
      List<String> out = this.ffaManager.tabComplete(args, sender);
      return out == null ? Collections.emptyList() : out;
   }

   @Override
   public Location arenaCenter() {
      return this.ffaManager.arenaCenter();
   }

   @Override
   public void sendToSpawn(Player player) {
      this.athleticManager.sendToSpawn(player);
   }

   @Override
   public boolean isPlaying(Player player) {
      return this.ffaManager.isPlaying(player);
   }

   @Override
   public boolean isFfaWorld(World world) {
      return this.ffaManager.isFfaWorld(world);
   }

   @Override
   public boolean isControlItem(ItemStack item) {
      return this.athleticManager.isControlItem(item);
   }

   @Override
   public boolean isMachine(Block block) {
      return this.slotMachineManager.isMachine(block);
   }

   @Override
   public boolean isSlotMachine(Block block) {
      return this.slotMachineManager.isSlotMachine(block);
   }

   @Override
   public boolean registerMachine(Block block, String difficultyName) {
      SlotMachineManager.Difficulty difficulty = parseDifficulty(difficultyName);
      if (difficulty == null) return false;
      return this.slotMachineManager.registerMachine(block, difficulty);
   }

   @Override
   public boolean registerMachineOrRecord(Block block, String difficultyName) {
      SlotMachineManager.Difficulty difficulty = parseDifficulty(difficultyName);
      if (block == null || difficulty == null) return false;
      if (this.slotMachineManager.registerMachine(block, difficulty)) return true;
      String path = "slot-machines." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
      this.plugin.data().set(path + ".difficulty", difficulty.name());
      this.plugin.data().set(path + ".created-at", System.currentTimeMillis());
      this.plugin.queueDataSave();
      return true;
   }

   @Override
   public void unregisterMachine(Block block) {
      this.slotMachineManager.unregisterMachine(block);
   }

   @Override
   public String describeSlotDifficulty(String difficultyName) {
      SlotMachineManager.Difficulty difficulty = parseDifficulty(difficultyName);
      if (difficulty == null) return "不明";
      switch (difficulty) {
         case EASY:
            return ChatColor.GREEN + "イージー";
         case NORMAL:
            return ChatColor.YELLOW + "ノーマル";
         case HARD:
            return ChatColor.RED + "ハード";
         case EXPERT:
            return "" + ChatColor.DARK_RED + ChatColor.BOLD + "エキスパート";
         default:
            return "不明";
      }
   }

   @Override
   public void openMinigameMenu(Player player) {
      Inventory inventory = Bukkit.createInventory(player, 27, Component.text("§dMifron Minigame"));
      inventory.setItem(11, this.plugin.actionItem(Material.DIAMOND_SWORD, ChatColor.RED + "FFA",
         List.of(ChatColor.GRAY + "クリック: FFAアリーナへ移動", ChatColor.GRAY + "現地の防具立てをクリックで参加"), "minigame_go", "ffa"));
      inventory.setItem(13, this.plugin.actionItem(Material.LEATHER_BOOTS, ChatColor.GREEN + "アスレチック",
         List.of(ChatColor.GRAY + "クリック: アスレ開始地点へ移動"), "minigame_go", "athletic"));
      inventory.setItem(15, this.plugin.actionItem(Material.GOLD_INGOT, ChatColor.GOLD + "スロット",
         List.of(ChatColor.GRAY + "棚＋スロットワンドで設置", ChatColor.GRAY + "ウォレットを持って右クリックで開始"), "minigame_go", "slots"));
      for (int i = 0; i < inventory.getSize(); i++) {
         if (inventory.getItem(i) == null) inventory.setItem(i, this.plugin.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of()));
      }
      player.openInventory(inventory);
   }

   private static SlotMachineManager.Difficulty parseDifficulty(String difficultyName) {
      if (difficultyName == null) return null;
      try {
         return SlotMachineManager.Difficulty.valueOf(difficultyName.toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
