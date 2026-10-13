package org.server.mifron;

import java.time.LocalTime;

/** Pure event schedule math: window, pre-announce and countdown checks. */
final class EventRules {
   private EventRules() {
   }

   static int minuteOfDay(LocalTime time) {
      return time.getHour() * 60 + time.getMinute();
   }

   /** True when now is inside [start, start+duration). */
   static boolean withinWindow(LocalTime now, LocalTime start, int durationMinutes) {
      if (now == null || start == null) return false;
      int nowMinute = minuteOfDay(now);
      int startMinute = minuteOfDay(start);
      return nowMinute >= startMinute && nowMinute < startMinute + Math.max(1, durationMinutes);
   }

   /** True when now is inside [start-before, start). */
   static boolean inAnnounceWindow(LocalTime now, LocalTime start, int beforeMinutes) {
      if (now == null || start == null || beforeMinutes <= 0) return false;
      int nowMinute = minuteOfDay(now);
      int startMinute = minuteOfDay(start);
      return nowMinute >= startMinute - beforeMinutes && nowMinute < startMinute;
   }

   /** Minutes remaining until start (0 once start has passed). */
   static int minutesUntil(LocalTime now, LocalTime start) {
      if (now == null || start == null) return 0;
      return Math.max(0, minuteOfDay(start) - minuteOfDay(now));
   }
}
