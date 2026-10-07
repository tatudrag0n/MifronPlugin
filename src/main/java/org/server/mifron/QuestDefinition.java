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
   String job
) {
   boolean requiresJob() {
      return this.job != null && !this.job.isBlank();
   }
   boolean isCompletionQuest() {
      return this.progressKey.endsWith("_complete");
   }

   boolean isSpecial() {
      return this.type == QuestType.SPECIAL;
   }
}
