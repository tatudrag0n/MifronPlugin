package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArtisanAnvilRulesTest {
   @Test
   void serializesAndParsesWorldAndCoordinates() {
      String key = ArtisanAnvilRules.serialize("world", 12, 64, -30);
      assertEquals("world;12;64;-30", key);
      ArtisanAnvilRules.Anchor anchor = ArtisanAnvilRules.parse(key);
      assertNotNull(anchor);
      assertEquals("world", anchor.world());
      assertEquals(12, anchor.x());
      assertEquals(64, anchor.y());
      assertEquals(-30, anchor.z());
   }

   @Test
   void rejectsMalformedStoredKeys() {
      assertNull(ArtisanAnvilRules.parse(null));
      assertNull(ArtisanAnvilRules.parse(""));
      assertNull(ArtisanAnvilRules.parse("world;1;2"));
      assertNull(ArtisanAnvilRules.parse(";1;2;3"));
      assertNull(ArtisanAnvilRules.parse("world;a;2;3"));
   }

   @Test
   void acceptsEveryAnvilStageButNothingElse() {
      assertTrue(ArtisanAnvilRules.isAnvilBlockName("ANVIL"));
      assertTrue(ArtisanAnvilRules.isAnvilBlockName("CHIPPED_ANVIL"));
      assertTrue(ArtisanAnvilRules.isAnvilBlockName("DAMAGED_ANVIL"));
      assertTrue(ArtisanAnvilRules.isAnvilBlockName("anvil"));
      assertFalse(ArtisanAnvilRules.isAnvilBlockName("CRAFTING_TABLE"));
      assertFalse(ArtisanAnvilRules.isAnvilBlockName(null));
   }

   @Test
   void treatsZeroOrNegativeLimitAsUnlimited() {
      assertTrue(ArtisanAnvilRules.isUnderLimit(8, 0));
      assertTrue(ArtisanAnvilRules.isUnderLimit(8, -1));
      assertTrue(ArtisanAnvilRules.isUnderLimit(2, 3));
      assertTrue(ArtisanAnvilRules.isUnderLimit(0, 1));
      assertFalse(ArtisanAnvilRules.isUnderLimit(3, 3));
      assertFalse(ArtisanAnvilRules.isUnderLimit(4, 3));
   }

   @Test
   void parsesOwnerUuidsAndRejectsGarbage() {
      UUID owner = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
      assertEquals(owner, ArtisanAnvilRules.parseOwner(" 00000000-0000-0000-0000-0000000000ab "));
      assertNull(ArtisanAnvilRules.parseOwner(null));
      assertNull(ArtisanAnvilRules.parseOwner("   "));
      assertNull(ArtisanAnvilRules.parseOwner("not-a-uuid"));
   }

   @Test
   void onlyAllowsUseFromWithinReach() {
      assertTrue(ArtisanAnvilRules.isWithinUseRange(0.0));
      assertTrue(ArtisanAnvilRules.isWithinUseRange(ArtisanAnvilRules.USE_RANGE_SQUARED));
      assertFalse(ArtisanAnvilRules.isWithinUseRange(ArtisanAnvilRules.USE_RANGE_SQUARED + 1.0));
      assertFalse(ArtisanAnvilRules.isWithinUseRange(Double.NaN));
   }
}
