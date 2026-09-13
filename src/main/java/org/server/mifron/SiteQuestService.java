package org.server.mifron;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;

/**
 * Reads the live quest list that the Minoru bot publishes to
 * {@code /api/quests} on mifron.mct-official.com. The result is cached in
 * memory and refreshed at most once per configured interval so opening the
 * quest book never spams the site API.
 */
final class SiteQuestService {
   private final Mifron plugin;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build();
   private volatile List<SiteQuestService.SiteQuest> cache = List.of();
   private volatile long lastAttempt = 0L;
   private volatile boolean fetching = false;

   SiteQuestService(Mifron plugin) {
      this.plugin = plugin;
   }

   record SiteQuest(String id, String name, String description, String condition, String reward, String type, String difficulty) {
   }

   boolean enabled() {
      return this.plugin.getConfig().getBoolean("quests.site-sync.enabled", true);
   }

   private String url() {
      return this.plugin.getConfig().getString("quests.site-sync.url", "https://mifron.mct-official.com/api/quests");
   }

   private long cacheMillis() {
      long seconds = Math.max(30L, this.plugin.getConfig().getLong("quests.site-sync.cache-seconds", 600L));
      return seconds * 1000L;
   }

   /**
    * Cached view of the last successful sync. Triggers a background refresh
    * when the cache has expired so callers never block the server thread.
    */
   List<SiteQuestService.SiteQuest> quests() {
      this.refreshIfStale();
      return this.cache;
   }

   void refreshIfStale() {
      if (!this.enabled() || this.fetching) return;
      long now = System.currentTimeMillis();
      if (now - this.lastAttempt < this.cacheMillis()) return;
      this.lastAttempt = now;
      this.fetching = true;
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(this.url()))
               .timeout(Duration.ofSeconds(8L))
               .header("Accept", "application/json")
               .GET()
               .build();
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
               List<SiteQuestService.SiteQuest> parsed = parse(response.body());
               this.cache = parsed;
               this.plugin.getLogger().info("Site quest sync loaded " + parsed.size() + " quests.");
            } else {
               this.plugin.getLogger().warning("Site quest sync returned HTTP " + response.statusCode() + ".");
            }
         } catch (Exception error) {
            this.plugin.getLogger().warning("Site quest sync failed: " + error.getMessage());
         } finally {
            this.fetching = false;
         }
      });
   }

   void invalidate() {
      this.lastAttempt = 0L;
   }

   private static List<SiteQuestService.SiteQuest> parse(String body) {
      List<SiteQuestService.SiteQuest> result = new ArrayList<>();
      if (body == null || body.isBlank()) return result;
      JsonElement root = JsonParser.parseString(body);
      if (!root.isJsonArray()) return result;
      JsonArray array = root.getAsJsonArray();
      for (JsonElement element : array) {
         if (!element.isJsonObject()) continue;
         JsonObject object = element.getAsJsonObject();
         String id = string(object, "id");
         String name = string(object, "name");
         if (id == null || id.isBlank() || name == null || name.isBlank()) continue;
         result.add(
            new SiteQuestService.SiteQuest(
               id,
               name,
               string(object, "description"),
               string(object, "condition"),
               string(object, "reward"),
               string(object, "type"),
               string(object, "difficulty")
            )
         );
      }
      return result;
   }

   private static String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value == null || value.isJsonNull() ? null : value.getAsString();
   }
}
