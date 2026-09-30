package org.server.mifron;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Core-side boundary to the optional Minigame module
 * ({@code org.server.mifron.minigame}). Core code must only touch Minigame
 * through this interface so the minigame package can be excluded from the
 * build or extracted into a separate plugin later.
 *
 * <p>Data keys for slots, athletic, FFA and player minigame stats
 * are unchanged so existing player data keeps working.
 */
public interface MinigameBridge {
   /** Implementation id: "minigame" when the module is present, "none" otherwise. */
   String name();

   /** True when the real Minigame module backs this bridge. */
   boolean available();

   void loadAll();

   void shutdownAll();

   void reloadAll();

   void registerListeners();

   boolean handleFfaCommand(CommandSender sender, String[] args);

   boolean handleAthleticCommand(Player player, String[] args);

   List<String> tabCompleteFfa(String[] args, CommandSender sender);

   Location arenaCenter();

   void sendToSpawn(Player player);

   boolean isPlaying(Player player);

   boolean isFfaWorld(World world);

   boolean isControlItem(ItemStack item);

   boolean isMachine(Block block);

   boolean isSlotMachine(Block block);

   /** Registers a slot machine; difficultyName is EASY/NORMAL/HARD/EXPERT. */
   boolean registerMachine(Block block, String difficultyName);

   /** Same as {@link #registerMachine} but records the entry even when the
    * shelf look cannot be applied (mirrors the old slot-wand fallback). */
   boolean registerMachineOrRecord(Block block, String difficultyName);

   void unregisterMachine(Block block);

   /** Human-readable difficulty label, or null when unknown. */
   String describeSlotDifficulty(String difficultyName);

   void openMinigameMenu(Player player);
}
