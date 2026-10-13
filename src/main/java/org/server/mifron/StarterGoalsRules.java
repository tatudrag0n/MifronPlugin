package org.server.mifron;

/** Pure starter-goal rules: claim state and mining threshold checks. */
final class StarterGoalsRules {
   private StarterGoalsRules() {
   }

   enum GoalState { LOCKED, CLAIMABLE, CLAIMED }

   /** UI/claim state for one goal from its completion and claim flags. */
   static GoalState state(boolean complete, boolean claimed) {
      if (claimed) return GoalState.CLAIMED;
      return complete ? GoalState.CLAIMABLE : GoalState.LOCKED;
   }

   static final int MINE_TARGET = 64;

   /** Mining goal completion from the lifetime block-break counter. */
   static boolean miningComplete(int blocksBroken) {
      return blocksBroken >= MINE_TARGET;
   }
}
