package org.server.mifron;

/** Pure trade rules: layout, limits, and validation. */
final class TradeRules {
   private TradeRules() {
   }

   static final int SIZE = 54;
   static final int[] A_OFFERS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
   static final int[] B_OFFERS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
   static final int[] DIVIDER = {4, 13, 22, 31, 40};
   static final int A_MONEY = 39;
   static final int B_MONEY = 41;
   static final int A_CONFIRM = 47;
   static final int B_CONFIRM = 51;
   static final int STATUS = 49;
   static final int CANCEL = 45;

   static final long REQUEST_EXPIRY_MILLIS = 60_000L;
   static final long SESSION_TIMEOUT_MILLIS = 300_000L;

   static boolean isAOffer(int slot) {
      for (int s : A_OFFERS) if (s == slot) return true;
      return false;
   }

   static boolean isBOffer(int slot) {
      for (int s : B_OFFERS) if (s == slot) return true;
      return false;
   }

   /** Validates a money offer: positive and within the configured per-trade cap. */
   static boolean isValidMoneyOffer(int amount, int maxAmount) {
      return amount > 0 && amount <= Math.max(1, maxAmount);
   }

   static String sessionKey(java.util.UUID a, java.util.UUID b) {
      return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
   }
}
