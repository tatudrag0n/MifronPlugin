package org.server.mifron;

import java.util.Locale;

enum ShopWandType {
   SHELF("shelf"),
   BARREL("barrel"),
   FRAME("frame");

   private final String key;

   ShopWandType(String key) {
      this.key = key;
   }

   String key() {
      return this.key;
   }

   static ShopWandType fromKey(String raw) {
      if (raw == null || raw.isBlank()) return null;
      String normalized = raw.toLowerCase(Locale.ROOT).replace('-', '_');
      for (ShopWandType type : values()) {
         if (type.key.equals(normalized)) return type;
      }
      return null;
   }
}
