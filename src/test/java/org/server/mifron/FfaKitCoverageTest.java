package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Configuration-level regression coverage for the FFA selector.
 *
 * This does not replace a live combat smoke test, but it prevents a kit from
 * being added to the enum/website and silently omitted from production config.
 */
final class FfaKitCoverageTest {
   private static final Pattern ENABLED_KIT = Pattern.compile("^\\s{6}- \\\"?([a-z0-9_]+)\\\"?\\s*$");

   @Test
   void allDeclaredKitsHaveUniqueKeys() {
      Set<String> keys = new HashSet<>();
      for (FfaKit kit : FfaKit.values()) {
         assertTrue(keys.add(kit.key()), "duplicate FFA kit key: " + kit.key());
      }
      assertEquals(18, keys.size(), "unexpected FFA kit count; update the coverage test and config together");
   }

   @Test
   void everyDeclaredKitIsEnabledInBundledProductionConfig() throws IOException {
      Path config = Path.of("src/main/resources/config.yml");
      assertTrue(Files.isRegularFile(config), "production config is missing");
      List<String> lines = Files.readAllLines(config);
      int enabledLine = -1;
      boolean inKits = false;
      for (int i = 0; i < lines.size(); i++) {
         String trimmed = lines.get(i).trim();
         if (trimmed.equals("kits:")) {
            inKits = true;
         } else if (inKits && trimmed.equals("enabled:")) {
            enabledLine = i;
            break;
         }
      }
      assertTrue(enabledLine >= 0, "ffa.kits.enabled list is missing");

      Set<String> configured = new HashSet<>();
      for (int i = enabledLine + 1; i < lines.size(); i++) {
         Matcher match = ENABLED_KIT.matcher(lines.get(i));
         if (match.matches()) {
            configured.add(match.group(1));
         } else if (!lines.get(i).isBlank() && !lines.get(i).trim().startsWith("#")) {
            break;
         }
      }

      for (FfaKit kit : FfaKit.values()) {
         assertFalse(configured.isEmpty(), "ffa.kits.enabled was parsed as empty");
         assertTrue(configured.contains(kit.key()), "kit missing from ffa.kits.enabled: " + kit.key());
      }
   }
}
