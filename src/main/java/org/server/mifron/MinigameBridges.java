package org.server.mifron;

/** Loads the optional Minigame module without a compile-time dependency. */
public final class MinigameBridges {
   private static final String MODULE_CLASS = "org.server.mifron.minigame.MinigameModule";

   private MinigameBridges() {
   }

   public static MinigameBridge load(Mifron plugin) {
      try {
         Class<?> type = Class.forName(MODULE_CLASS);
         Object module = type.getDeclaredConstructor(Mifron.class).newInstance(plugin);
         return (MinigameBridge) module;
      } catch (ReflectiveOperationException | LinkageError e) {
         plugin.getLogger().info("[mifron] Minigame module not present; running core-only.");
         return new NoOpMinigameBridge();
      }
   }
}
