package org.server.mifron;

import java.util.Locale;

enum ShopWandType {
   SHELF("shelf"),
   BARREL("barrel"),
   FRAME("frame"),
   SLOT_EASY("slot_easy"),
   SLOT_NORMAL("slot_normal"),
   SLOT_HARD("slot_hard"),
   SLOT_EXPERT("slot_expert");

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
      if (!normalized.startsWith("slot_") && (normalized.equals("easy") || normalized.equals("normal") || normalized.equals("hard") || normalized.equals("expert"))) {
         normalized = "slot_" + normalized;
      }
      if (normalized.equals("slot") || normalized.equals("slotwand") || normalized.equals("slot_wand")) {
         normalized = "slot_normal";
      }
      for (ShopWandType type : values()) {
         if (type.key.equals(normalized)) return type;
      }
      return null;
   }

   boolean isSlotWand() {
      return this == SLOT_EASY || this == SLOT_NORMAL || this == SLOT_HARD || this == SLOT_EXPERT;
   }

   SlotMachineManager.Difficulty getSlotDifficulty() {
      switch (this) {
         case SLOT_EASY: return SlotMachineManager.Difficulty.EASY;
         case SLOT_NORMAL: return SlotMachineManager.Difficulty.NORMAL;
         case SLOT_HARD: return SlotMachineManager.Difficulty.HARD;
         case SLOT_EXPERT: return SlotMachineManager.Difficulty.EXPERT;
         default: return null;
      }
   }
}
