package org.server.mifron;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for the placed ancient-anvil relic block.
 *
 * <p>The Bukkit {@link AdvancedAnvilFeature} keeps only the wiring. Everything
 * that decides "is this block an anvil", "which stored key is this location"
 * and "may this player place another one" lives here so it can be tested
 * without a running server.</p>
 */
final class ArtisanAnvilRules {
   /**
    * How far a player may be from a tracked anvil and still use it (8 blocks,
    * squared). Only used as a fallback when the container does not expose the
    * block location of the open anvil.
    */
   static final double USE_RANGE_SQUARED = 64.0;
   private static final Set<String> ANVIL_BLOCKS = Set.of("ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL");

   private ArtisanAnvilRules() {
   }

   /** A stored anvil location, split into the world name and block coordinates. */
   record Anchor(String world, int x, int y, int z) {
   }

   /**
    * Vanilla anvils degrade in place, so a placed relic anvil is tracked by
    * location and every degradation stage still counts as the same anvil.
    */
   static boolean isAnvilBlockName(String materialName) {
      if (materialName == null) return false;
      return ANVIL_BLOCKS.contains(materialName.toUpperCase(Locale.ROOT));
   }

   static String serialize(String world, int x, int y, int z) {
      return world + ";" + x + ";" + y + ";" + z;
   }

   /** Returns null for anything that is not a complete stored key. */
   static Anchor parse(String serialized) {
      if (serialized == null) return null;
      String[] parts = serialized.split(";");
      if (parts.length != 4 || parts[0].isEmpty()) return null;
      try {
         return new Anchor(parts[0], Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   /** Returns null for a missing or malformed owner entry instead of failing the load. */
   static UUID parseOwner(String stored) {
      if (stored == null || stored.isBlank()) return null;
      try {
         return UUID.fromString(stored.trim());
      } catch (IllegalArgumentException ignored) {
         return null;
      }
   }

   /** A limit of zero or less means "no limit". */
   static boolean isUnderLimit(int placedCount, int limit) {
      if (limit <= 0) return true;
      return Math.max(0, placedCount) < limit;
   }

   static boolean isWithinUseRange(double distanceSquared) {
      return !Double.isNaN(distanceSquared) && distanceSquared <= USE_RANGE_SQUARED;
   }
}
