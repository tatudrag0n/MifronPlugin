package org.server.mifron;

/** Income-relevant player actions that jobs can boost. */
enum JobAction {
   HUNT("hunt"),
   HARVEST("harvest"),
   ENCHANT("enchant"),
   SELL("sell"),
   FISH("fish"),
   MINE("mine"),
   EXPLORE("explore");

   private final String key;

   JobAction(String key) {
      this.key = key;
   }

   String key() {
      return this.key;
   }

   static JobAction fromKey(String raw) {
      if (raw == null || raw.isBlank()) return null;
      String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
      for (JobAction action : values()) {
         if (action.key.equals(normalized)) return action;
      }
      return null;
   }
}
