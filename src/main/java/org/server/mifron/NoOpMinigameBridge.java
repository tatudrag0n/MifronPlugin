package org.server.mifron;

import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Fallback used when the Minigame module is absent from the build. */
public final class NoOpMinigameBridge implements MinigameBridge {
   @Override
   public String name() {
      return "none";
   }

   @Override
   public boolean available() {
      return false;
   }

   @Override
   public void loadAll() {
   }

   @Override
   public void shutdownAll() {
   }

   @Override
   public void reloadAll() {
   }

   @Override
   public void registerListeners() {
   }

   @Override
   public boolean handleFfaCommand(CommandSender sender, String[] args) {
      sender.sendMessage("§cMinigameモジュールが無効です。");
      return true;
   }

   @Override
   public boolean handleAthleticCommand(Player player, String[] args) {
      player.sendMessage("§cMinigameモジュールが無効です。");
      return true;
   }

   @Override
   public List<String> tabCompleteFfa(String[] args, CommandSender sender) {
      return Collections.emptyList();
   }

   @Override
   public Location arenaCenter() {
      return null;
   }

   @Override
   public void sendToSpawn(Player player) {
   }

   @Override
   public boolean isPlaying(Player player) {
      return false;
   }

   @Override
   public boolean isFfaWorld(World world) {
      return false;
   }

   @Override
   public boolean isControlItem(ItemStack item) {
      return false;
   }

   @Override
   public boolean isMachine(Block block) {
      return false;
   }

   @Override
   public boolean isSlotMachine(Block block) {
      return false;
   }

   @Override
   public boolean registerMachine(Block block, String difficultyName) {
      return false;
   }

   @Override
   public boolean registerMachineOrRecord(Block block, String difficultyName) {
      return false;
   }

   @Override
   public void unregisterMachine(Block block) {
   }

   @Override
   public String describeSlotDifficulty(String difficultyName) {
      return null;
   }

   @Override
   public void openMinigameMenu(Player player) {
      player.sendMessage("§cMinigameモジュールが無効です。");
   }
}
