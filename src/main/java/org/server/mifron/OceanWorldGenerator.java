package org.server.mifron;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

/** Minimal ocean generator used only when the configured main world is absent. */
final class OceanWorldGenerator extends ChunkGenerator {
   @Override
   public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
      ChunkData data = this.createChunkData(world);
      data.setRegion(0, 0, 0, 16, 58, 16, Material.STONE);
      data.setRegion(0, 58, 0, 16, 62, 16, Material.SAND);
      data.setRegion(0, 62, 0, 16, 63, 16, Material.WATER);
      return data;
   }
}
