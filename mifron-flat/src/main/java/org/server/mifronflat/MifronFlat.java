package org.server.mifronflat;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Exposes the flat bedrock generator for the default (main) world.
 *
 * <p>The main plugin loads at POSTWORLD, which is too late: Bukkit resolves
 * the default world's generator before that (and errors out). This micro
 * plugin therefore loads at STARTUP and does nothing else.
 *
 * <p>Usage in {@code bukkit.yml}:
 * <pre>
 * worlds:
 *   main:
 *     generator: MifronFlat:flat
 * </pre>
 */
public final class MifronFlat extends JavaPlugin {
   @Override
   public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
      if ("flat".equalsIgnoreCase(id)) return new FlatBedrockGenerator();
      if ("main".equalsIgnoreCase(worldName)) return new FlatBedrockGenerator();
      return super.getDefaultWorldGenerator(worldName, id);
   }
}
