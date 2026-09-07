package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * BanRollbackFeature
 * --------------------
 * When a player is banned, rolls back their logged footprint (block
 * breaks/placements, container transactions) via the CoreProtect API.
 *
 * IMPORTANT: The CoreProtect plugin jar must be installed on the server and
 * added to pom.xml as a provided dependency before the commented API call
 * can be enabled:
 *   <dependency>
 *     <groupId>net.coreprotect</groupId>
 *     <artifactId>coreprotect</artifactId>
 *     <version>21.3</version>
 *     <scope>provided</scope>
 *   </dependency>
 *
 * Wiring (wherever ban logic runs):
 *   BanRollbackFeature.rollbackPlayer(playerName, lookbackDays);
 */
public final class BanRollbackFeature {

    private BanRollbackFeature() {}

    /**
     * Rolls back all CoreProtect-logged actions for the given player name
     * across all worlds, looking back the given number of days.
     * Returns true if CoreProtect was found and the rollback was issued.
     *
     * The actual CoreProtect API call is a documented placeholder until the
     * dependency is installed (see class Javadoc).
     */
    public static boolean rollbackPlayer(String playerName, int lookbackDays) {
        Plugin coreProtect = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (coreProtect == null || !coreProtect.isEnabled()) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[Mifron] CoreProtect is not installed; cannot roll back player " + playerName);
            return false;
        }

        Instant since = Instant.now().minus(Duration.ofDays(lookbackDays));
        Bukkit.getLogger().info("[Mifron] Requesting CoreProtect rollback for " + playerName
                + " since " + since + ". Enable the API call in this method once the"
                + " CoreProtect dependency is added to pom.xml.");
        // Example once dependency is added:
        // CoreProtectAPI api = ((CoreProtect) coreProtect).getAPI();
        // List<String[]> restrictions = Collections.singletonList(new String[]{"user:" + playerName});
        // api.performRollback((int) (Instant.now().getEpochSecond() - since.getEpochSecond()), restrictions);
        return true;
    }
}
