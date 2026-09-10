package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart9 extends MifronPart8x1 {
   protected Integer exactMaterialPrice(Material material) {
      return switch (material) {
         case DIRT, GRASS_BLOCK, SAND, GRAVEL, COBBLESTONE, STONE, NETHERRACK, END_STONE -> 1;
         case CLAY, BRICK, BRICKS -> 4;
         case COAL, CHARCOAL -> 4;
         case COAL_BLOCK -> 36;
         case COPPER_INGOT -> 4;
         case COPPER_BLOCK, WAXED_COPPER_BLOCK -> 36;
         case IRON_NUGGET -> 2;
         case IRON_INGOT -> 10;
         case IRON_BLOCK -> 90;
         case GOLD_NUGGET -> 3;
         case GOLD_INGOT -> 25;
         case GOLD_BLOCK -> 225;
         case REDSTONE -> 5;
         case REDSTONE_BLOCK -> 45;
         case LAPIS_LAZULI -> 8;
         case LAPIS_BLOCK -> 72;
         case QUARTZ -> 8;
         case QUARTZ_BLOCK -> 32;
         case AMETHYST_SHARD -> 8;
         case AMETHYST_BLOCK -> 32;
         case DIAMOND -> 100;
         case DIAMOND_BLOCK -> 900;
         case EMERALD -> 100;
         case EMERALD_BLOCK -> 900;
         case NETHERITE_SCRAP -> 250;
         case NETHERITE_INGOT -> 1100;
         case NETHERITE_BLOCK -> 9900;
         case OBSIDIAN -> 20;
         case CRYING_OBSIDIAN -> 35;
         case ANCIENT_DEBRIS -> 300;
         case BLAZE_ROD -> 20;
         case ENDER_PEARL -> 20;
         case GHAST_TEAR -> 50;
         case SLIME_BALL -> 10;
         case SHULKER_SHELL -> 75;
         case TOTEM_OF_UNDYING -> 250;
         case HEART_OF_THE_SEA -> 250;
         case TRIDENT -> 300;
         case ELYTRA -> 1500;
         case NETHER_STAR -> 2000;
         case GOLDEN_APPLE -> 80;
         case ENCHANTED_GOLDEN_APPLE -> 1500;
         case BEACON -> 2500;
         case CONDUIT -> 600;
         default -> null;
      };
   }

   protected int storageMaterialPrice(String name) {
      if (!name.endsWith("_BLOCK")) return !name.endsWith("_BUNDLE") && !name.endsWith("_BOX") ? 0 : 20;
      return switch (name.substring(0, name.length() - "_BLOCK".length())) {
         case "RAW_IRON" -> 72;
         case "RAW_GOLD" -> 180;
         case "RAW_COPPER" -> 27;
         case "HONEY" -> 36;
         case "SLIME" -> 90;
         default -> 0;
      };
   }

   protected int equipmentPrice(String name) {
      int material = this.equipmentMaterialBase(name);
      int units = this.equipmentUnits(name);
      if (material <= 0 || units <= 0) return 0;
      return material * units + (name.contains("NETHERITE") ? 200 : 0);
   }

   protected int equipmentMaterialBase(String name) {
      if (name.startsWith("WOODEN_")) return 2;
      if (name.startsWith("STONE_")) return 1;
      if (name.startsWith("LEATHER_")) return 8;
      if (name.startsWith("CHAINMAIL_")) return 12;
      if (name.startsWith("IRON_")) return 10;
      if (name.startsWith("GOLDEN_")) return 25;
      if (name.startsWith("COPPER_")) return 4;
      if (name.startsWith("DIAMOND_")) return 100;
      return name.startsWith("NETHERITE_") ? 1100 : 0;
   }

   protected int equipmentUnits(String name) {
      if (name.endsWith("_HELMET")) return 5;
      if (name.endsWith("_CHESTPLATE")) return 8;
      if (name.endsWith("_LEGGINGS")) return 7;
      if (name.endsWith("_BOOTS")) return 4;
      if (name.endsWith("_SWORD") || name.endsWith("_HOE") || name.endsWith("_SPEAR")) return 2;
      if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) return 3;
      if (name.endsWith("_SHOVEL")) return 1;
      return name.endsWith("_HORSE_ARMOR") ? 6 : 0;
   }

   protected int priceByContainedResource(String name) {
      if (name.contains("DIAMOND") || name.contains("EMERALD")) return 100;
      if (name.contains("GOLD")) return 25;
      if (name.contains("IRON")) return 10;
      if (name.contains("LAPIS") || name.contains("QUARTZ")) return 8;
      if (name.contains("REDSTONE")) return 5;
      if (name.contains("COPPER") || name.contains("COAL")) return 4;
      return 4;
   }

   protected void tickMerchants() {
      long now = System.currentTimeMillis();
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            if (!(entity instanceof AbstractVillager villager) || !this.mifron().isMifronMerchant(entity)) continue;
            villager.setInvulnerable(false);
            villager.setAI(!this.activeMerchantViews.containsValue(villager.getUniqueId()));
            PersistentDataContainer container = entity.getPersistentDataContainer();
            long spawnedAt = container.getOrDefault(this.merchantSpawnKey, PersistentDataType.LONG, now);
            if (now - spawnedAt < 3600000L) continue;
            boolean traded = Boolean.TRUE.equals(container.get(this.merchantTradedKey, PersistentDataType.BOOLEAN));
            if (!traded) entity.remove();
            else {
               this.rerollMerchant(villager);
               container.set(this.merchantSpawnKey, PersistentDataType.LONG, now);
               container.set(this.merchantTradedKey, PersistentDataType.BOOLEAN, false);
            }
         }
         this.trySpawnRandomMerchant(world);
      }
   }

   protected void trySpawnRandomMerchant(World world) {
      if (!"survival".equalsIgnoreCase(world.getName()) || world.getEnvironment() != Environment.NORMAL || world.getPlayers().isEmpty()) return;
      if (this.random.nextDouble() >= 0.005) return;
      Player anchor = world.getPlayers().get(this.random.nextInt(world.getPlayers().size()));
      Location base = anchor.getLocation().clone().add(this.random.nextInt(33) - 16, 0.0, this.random.nextInt(33) - 16);
      Location spawn = new Location(world, base.getBlockX() + 0.5, world.getHighestBlockYAt(base) + 1.0, base.getBlockZ() + 0.5);
      if (!this.mifron().isCentralPlazaLocation(spawn)) this.spawnMerchant(spawn);
   }
}
