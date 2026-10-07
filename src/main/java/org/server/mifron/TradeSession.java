package org.server.mifron;

import java.util.UUID;

/** Mutable state of one player-to-player trade. Main-thread only. */
final class TradeSession {
   enum State {
      REQUESTED,
      OPEN,
      DONE,
      CANCELLED
   }

   final UUID a;
   final UUID b;
   State state = State.REQUESTED;
   long requestedAt = System.currentTimeMillis();
   long expiresAt = System.currentTimeMillis() + TradeRules.SESSION_TIMEOUT_MILLIS;
   int moneyA;
   int moneyB;
   boolean confirmedA;
   boolean confirmedB;
   org.bukkit.inventory.Inventory view;

   TradeSession(UUID a, UUID b) {
      this.a = a;
      this.b = b;
   }

   boolean involves(UUID uuid) {
      return uuid != null && (uuid.equals(this.a) || uuid.equals(this.b));
   }

   boolean isA(UUID uuid) {
      return uuid != null && uuid.equals(this.a);
   }

   UUID other(UUID uuid) {
      if (uuid == null) return null;
      if (uuid.equals(this.a)) return this.b;
      if (uuid.equals(this.b)) return this.a;
      return null;
   }

   boolean bothConfirmed() {
      return this.confirmedA && this.confirmedB;
   }

   void resetConfirms() {
      this.confirmedA = false;
      this.confirmedB = false;
   }

   boolean isExpired(long now) {
      return now > this.expiresAt;
   }
}
