package org.server.mifron;

/** Jobs a player can hold. Single-select, switchable with a cooldown. */
enum JobType {
   HUNTER("hunter", "狩人", "モブ討伐の報酬が増加する"),
   FARMER("farmer", "農民", "作物収穫でMPがもらえる"),
   ENCHANTER("enchanter", "付呪師", "高度な金床のMPコストが割引される"),
   MERCHANT("merchant", "商人", "ショップ売却額が増加する"),
   FISHER("fisher", "釣り人", "釣りでMPがもらえる"),
   MINER("miner", "鉱夫", "採掘でMPがもらえる"),
   ADVENTURER("adventurer", "冒険者", "未踏破チャンクの初回訪問でMPがもらえる"),
   NONE("none", "無職", "ボーナスなし");

   private final String key;
   private final String displayName;
   private final String description;

   JobType(String key, String displayName, String description) {
      this.key = key;
      this.displayName = displayName;
      this.description = description;
   }

   String key() {
      return this.key;
   }

   String displayName() {
      return this.displayName;
   }

   String description() {
      return this.description;
   }

   static JobType fromKey(String raw) {
      if (raw == null || raw.isBlank()) return null;
      String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
      for (JobType type : values()) {
         if (type.key.equals(normalized)) return type;
      }
      return null;
   }

   /** The action this job earns its income bonus from, or null for NONE. */
   JobAction incomeAction() {
      return switch (this) {
         case HUNTER -> JobAction.HUNT;
         case FARMER -> JobAction.HARVEST;
         case ENCHANTER -> JobAction.ENCHANT;
         case MERCHANT -> JobAction.SELL;
         case FISHER -> JobAction.FISH;
         case MINER -> JobAction.MINE;
         case ADVENTURER -> JobAction.EXPLORE;
         case NONE -> null;
      };
   }
}
