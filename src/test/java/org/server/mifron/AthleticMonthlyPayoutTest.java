package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the monthly ranking double-payout: after a crash
 * mid-settlement, the resumed run must pay only the winners that are not yet
 * recorded as paid.
 */
class AthleticMonthlyPayoutTest {
   @Test
   void unpaidWinnersAreRankedSliceMinusPaid() {
      List<String> ranked = List.of("a", "b", "c", "d");
      assertEquals(List.of("a", "b", "c"), AthleticManager.unpaidMonthlyWinners(ranked, List.of(), 3));
      assertEquals(List.of("b", "c"), AthleticManager.unpaidMonthlyWinners(ranked, List.of("a"), 3));
      assertEquals(List.of(), AthleticManager.unpaidMonthlyWinners(ranked, List.of("a", "b", "c"), 3));
   }

   @Test
   void crashResumePaysOnlyRemainder() {
      // Winners a and b were paid and persisted before the crash.
      List<String> ranked = new ArrayList<>(List.of("a", "b", "c"));
      List<String> paid = new ArrayList<>(List.of("a", "b"));
      assertEquals(List.of("c"), AthleticManager.unpaidMonthlyWinners(ranked, paid, 3));
   }

   @Test
   void nullsAndLimitsAreSafe() {
      assertEquals(List.of(), AthleticManager.unpaidMonthlyWinners(null, List.of(), 3));
      assertEquals(List.of(), AthleticManager.unpaidMonthlyWinners(List.of("a"), List.of(), 0));
      List<String> withNull = new ArrayList<>();
      withNull.add(null);
      withNull.add("b");
      assertEquals(List.of("b"), AthleticManager.unpaidMonthlyWinners(withNull, null, 5));
   }
}
