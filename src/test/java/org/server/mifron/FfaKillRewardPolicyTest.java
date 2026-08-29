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

   @Test
   void alternatingKillsAreSuppressedOnlyAfterBothDirectionsReachTheThreshold() {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();
      FfaManager.ReciprocalKillState state = null;
      for (int index = 0; index < 5; index++) {
         state = FfaManager.nextReciprocalKillState(state,
            index % 2 == 0 ? first : second,
            index % 2 == 0 ? second : first,
            1_000L + index, 180_000L);
      }
      assertEquals(false, FfaManager.isReciprocalFarm(state, 6));
      state = FfaManager.nextReciprocalKillState(state, second, first, 1_006L, 180_000L);
      assertEquals(true, FfaManager.isReciprocalFarm(state, 6));
      state = FfaManager.nextReciprocalKillState(state, first, second, 181_007L, 180_000L);
      assertEquals(false, FfaManager.isReciprocalFarm(state, 6));
   }

   @Test
   void killRewardWindowResetsAfterTheConfiguredWindow() {
      FfaManager.KillRewardWindowState state = new FfaManager.KillRewardWindowState(1_000L, 2_900);
      FfaManager.KillRewardWindowState same = FfaManager.nextKillRewardWindowState(state, 2_000L, 3_600_000L);
      FfaManager.KillRewardWindowState reset = FfaManager.nextKillRewardWindowState(state, 3_601_001L, 3_600_000L);

      assertEquals(2_900, same.credited());
      assertEquals(0, reset.credited());
      assertEquals(3_601_001L, reset.windowStartAt());
   }
}
