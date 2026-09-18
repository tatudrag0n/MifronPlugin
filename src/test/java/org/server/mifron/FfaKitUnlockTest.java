package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The paid-kit set and default prices must stay exactly as designed:
 * premium kits cost MP once, basic kits stay free.
 */
class FfaKitUnlockTest {
   private static final Set<FfaKit> PAID = EnumSet.of(
      FfaKit.NECROMANCER, FfaKit.ASSASSIN, FfaKit.SNIPER, FfaKit.WIZARD,
      FfaKit.VAMPIRE, FfaKit.CRUSHER, FfaKit.BUG_MANIA);

   @Test
   void paidKitsCostTenThousand() {
      for (FfaKit kit : PAID) {
         assertEquals(10000, kit.defaultUnlockPrice(), kit.key());
      }
   }

   @Test
   void basicKitsAreFree() {
      for (FfaKit kit : FfaKit.values()) {
         if (!PAID.contains(kit)) {
            assertEquals(0, kit.defaultUnlockPrice(), kit.key());
         }
      }
      assertTrue(FfaKit.values().length >= PAID.size());
   }
}
