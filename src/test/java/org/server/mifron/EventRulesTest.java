package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class EventRulesTest {
   private static final LocalTime START = LocalTime.of(20, 0);

   @Test
   void windowCoversStartInclusiveEndExclusive() {
      assertFalse(EventRules.withinWindow(LocalTime.of(19, 59), START, 60));
      assertTrue(EventRules.withinWindow(LocalTime.of(20, 0), START, 60));
      assertTrue(EventRules.withinWindow(LocalTime.of(20, 59), START, 60));
      assertFalse(EventRules.withinWindow(LocalTime.of(21, 0), START, 60));
   }

   @Test
   void announceWindowEndsAtStart() {
      assertFalse(EventRules.inAnnounceWindow(LocalTime.of(19, 44), START, 15));
      assertTrue(EventRules.inAnnounceWindow(LocalTime.of(19, 45), START, 15));
      assertTrue(EventRules.inAnnounceWindow(LocalTime.of(19, 59), START, 15));
      assertFalse(EventRules.inAnnounceWindow(LocalTime.of(20, 0), START, 15));
      assertFalse(EventRules.inAnnounceWindow(LocalTime.of(19, 59), START, 0));
   }

   @Test
   void minutesUntilCountsDown() {
      assertEquals(15, EventRules.minutesUntil(LocalTime.of(19, 45), START));
      assertEquals(0, EventRules.minutesUntil(START, START));
      assertEquals(0, EventRules.minutesUntil(LocalTime.of(21, 0), START));
   }

   @Test
   void timeAndDayParsing() {
      assertEquals(LocalTime.of(20, 0), EventService.parseTime("20:00"));
      assertEquals(LocalTime.of(9, 5), EventService.parseTime("09:05"));
      assertNull(EventService.parseTime("24:00"));
      assertNull(EventService.parseTime("20"));
      assertNull(EventService.parseTime("abc"));
      assertNull(EventService.parseTime(null));
      assertEquals(DayOfWeek.SATURDAY, EventService.parseDay("saturday"));
      assertNull(EventService.parseDay("DAILY"));
      assertNull(EventService.parseDay("noday"));
   }
}
