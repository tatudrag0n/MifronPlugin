package org.server.mifron;

enum QuestType {
   DAILY("daily", "デイリー"),
   WEEKLY("weekly", "ウィークリー"),
   MONTHLY("monthly", "マンスリー"),
   SPECIAL("special", "スペシャル"),
   ONE_SHOT("one_shot", "単発"),
   HIDDEN("hidden", "隠し");

   private final String key;
   private final String label;

   QuestType(String key, String label) {
      this.key = key;
      this.label = label;
   }

   String key() {
      return this.key;
   }

   String label() {
      return this.label;
   }

   static QuestType fromLabel(String label) {
      for (QuestType type : values()) {
         if (type.label.equals(label) || type.key.equalsIgnoreCase(label)) {
            return type;
         }
      }
      return SPECIAL;
   }
}
