package org.server.mifron;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Generates the main world as a fully flat plane with no structures or
 * natural features: a single bedrock floor at the world bottom (Y=-64) and
 * air above. Only used when creating a new main world; existing worlds are
 * never touched.
 */
final class MainFlatGenerator extends ChunkGenerator {
   /** Ground level: the lowest buildable Y of a standard -64..320 world. */
   static final int GROUND_Y = -64;

   @Override
   public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
      // Bedrock floor at the world minimum; everything else stays air.
      int minY = worldInfo.getMinHeight();
      chunkData.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
   }

   @Override
   public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
   }

   @Override
   public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
   }

   @Override
   public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
   }

   @Override
   public boolean shouldGenerateNoise() {
      return true;
   }

   @Override
   public boolean shouldGenerateSurface() {
      return false;
   }

   @Override
   public boolean shouldGenerateBedrock() {
      return false;
   }

   @Override
   public boolean shouldGenerateCaves() {
      return false;
   }

   @Override
   public boolean shouldGenerateDecorations() {
      return false;
   }

   @Override
   public boolean shouldGenerateMobs() {
      return false;
   }

   @Override
   public boolean shouldGenerateStructures() {
      return false;
   }

   @Override
   public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
      return new BiomeProvider() {
         @Override
         public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.PLAINS;
         }

         @Override
         public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.PLAINS);
         }
      };
   }

   @Override
   public boolean isParallelCapable() {
      return true;
   }
}
