package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies the resource pack SHA-1 helper. Minecraft compares the SHA-1 sent with
 * {@code setResourcePack} against the hash of the downloaded zip, so the format
 * (lowercase hex, 40 chars) has to be exact.
 */
class ResourcePackFeatureTest {

   @Test
   void sha1HexMatchesKnownDigest() {
      // SHA-1 of the ASCII bytes "abc"
      byte[] data = "abc".getBytes(StandardCharsets.US_ASCII);
      assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", ResourcePackFeature.sha1Hex(data));
   }

   @Test
   void sha1HexIsLowercaseAndFortyChars() {
      byte[] data = new byte[] {0x00, 0x0f, (byte) 0xf0, (byte) 0xff};
      String hex = ResourcePackFeature.sha1Hex(data);
      assertEquals(40, hex.length());
      assertEquals(hex.toLowerCase(), hex);
      assertTrue(hex.matches("[0-9a-f]{40}"));
   }

   @Test
   void sha1HexOfEmptyInputIsKnownValue() {
      assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709",
         ResourcePackFeature.sha1Hex(new byte[0]));
   }

   @Test
   void sha1ReturnsTwentyBytes() {
      assertNotNull(ResourcePackFeature.sha1("mifron".getBytes(StandardCharsets.UTF_8)));
      assertEquals(20, ResourcePackFeature.sha1("mifron".getBytes(StandardCharsets.UTF_8)).length);
   }
}
