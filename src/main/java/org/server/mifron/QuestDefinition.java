package org.server.mifron;

import org.bukkit.Material;

record QuestDefinition(
   String id,
   QuestType type,
   String name,
   String reset,
   String condition,
   int baseReward,
   String display,
   boolean reincarnationBonus,
   String repeatLimit,
   Material icon,
   String progressKey,
   int required,
   String intent,
   String event
) {
   boolean hasEvent() {
      return this.event != null && !this.event.isBlank();
   }
   boolean isCompletionQuest() {
      return this.progressKey.endsWith("_complete");
   }

   boolean isSpecial() {
      return this.type == QuestType.SPECIAL;
   }
}
