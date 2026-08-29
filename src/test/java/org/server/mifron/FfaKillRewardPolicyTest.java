package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FfaKillRewardPolicyTest {
   @Test
   void repeatsOnlyWhenTheSameTargetIsKilledWithinTheConfiguredWindow() {
      UUID firstVictim = UUID.randomUUID();
      UUID secondVictim = UUID.randomUUID();
      FfaManager.KillRewardState first = FfaManager.nextKillRewardState(null, firstVictim, 1_000L, 600_000L);
      FfaManager.KillRewardState repeated = FfaManager.nextKillRewardState(first, firstVictim, 2_000L, 600_000L);
      FfaManager.KillRewardState different = FfaManager.nextKillRewardState(repeated, secondVictim, 3_000L, 600_000L);
      FfaManager.KillRewardState expired = FfaManager.nextKillRewardState(repeated, firstVictim, 602_001L, 600_000L);

      assertEquals(0, first.repeats());
      assertEquals(1, repeated.repeats());
      assertEquals(0, different.repeats());
      assertEquals(0, expired.repeats());
   }

   @Test
   void repeatCountIsCappedAtTheZeroRewardThreshold() {
      UUID victim = UUID.randomUUID();
      FfaManager.KillRewardState state = new FfaManager.KillRewardState(victim, 7, 1_000L);

      FfaManager.KillRewardState next = FfaManager.nextKillRewardState(state, victim, 2_000L, 600_000L);

      assertEquals(7, next.repeats());
   }
}
