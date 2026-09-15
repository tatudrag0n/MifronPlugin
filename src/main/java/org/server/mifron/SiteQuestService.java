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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;

/**
 * Reads the live quest list that the Minoru bot publishes to
 * {@code /api/quests} on mifron.mct-official.com. The result is cached in
 * memory and refreshed at most once per configured interval so opening the
 * quest book never spams the site API.
 */
final class SiteQuestService {
   private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*)");
   private static final Pattern MP_REWARD = Pattern.compile("(?i)MP\\s*[:：]?\\s*([0-9][0-9,]*)");
   private final Mifron plugin;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build();
   private volatile List<SiteQuestService.SiteQuest> cache = List.of();
   private volatile long lastAttempt = 0L;
   private volatile boolean fetching = false;

   SiteQuestService(Mifron plugin) {
      this.plugin = plugin;
   }

   record SiteQuest(
      String id,
      String name,
      String description,
      String condition,
      String reward,
      String type,
      String difficulty,
      List<String> conditionTypes,
      List<String> rewardTypes,
      String status,
      List<String> dependencies
   ) {
      /**
       * Maps the website contract onto the internal quest engine. The site
       * exposes machine-readable {@code conditionTypes}/{@code rewardTypes} plus
       * a human condition string; the required count is the number written in
       * the condition and the MP amount is written in the reward string. When a
       * condition cannot be mapped the quest stays display-only rather than
       * inventing a tracking rule.
       */
      QuestDefinition toDefinition() {
         QuestType questType = this.questType();
         String progressKey = progressKey(this.conditionTypes);
         if (progressKey == null) {
            return null;
         }
         int required = Math.max(1, firstNumber(this.condition, 1));
         int rewardMp = this.rewardMp();
         return new QuestDefinition(
            this.id,
            questType,
            this.name,
            "",
            this.condition == null ? "" : this.condition,
            rewardMp,
            this.description == null ? "" : this.description,
            true,
            "",
            Material.PAPER,
            progressKey,
            required,
            ""
         );
      }

      private QuestType questType() {
         String value = this.type == null ? "" : this.type.toLowerCase(Locale.ROOT);
         return switch (value) {
            case "daily" -> QuestType.DAILY;
            case "weekly" -> QuestType.WEEKLY;
            case "monthly" -> QuestType.MONTHLY;
            case "periodic" -> QuestType.PERIODIC;
            case "hidden" -> QuestType.HIDDEN;
            case "special" -> QuestType.SPECIAL;
            default -> QuestType.ONE_SHOT;
         };
      }

      private int rewardMp() {
         if (this.reward == null || this.reward.isBlank()) {
            return 0;
         }
         if (this.rewardTypes != null && !this.rewardTypes.isEmpty()
            && this.rewardTypes.stream().noneMatch(type -> "mp".equalsIgnoreCase(type))) {
            return 0;
         }
         Matcher matcher = MP_REWARD.matcher(this.reward);
         if (matcher.find()) {
            return Math.max(0, parseInt(matcher.group(1)));
         }
         return Math.max(0, firstNumber(this.reward, 0));
      }

      private static String progressKey(List<String> conditionTypes) {
         if (conditionTypes == null) {
            return null;
         }
         for (String conditionType : conditionTypes) {
            if (conditionType == null) {
               continue;
            }
            String key = switch (conditionType.toLowerCase(Locale.ROOT)) {
               case "item_obtain" -> "items_obtained";
               case "mob_kill" -> "hostile_kills";
               case "block_break" -> "mining_blocks";
               case "block_place" -> "building_blocks";
               case "move" -> "exploration_chunks";
               case "mp_gain" -> "mp_gained";
               case "advancement" -> "advancements";
               case "trade" -> "trades";
               default -> null;
            };
            if (key != null) {
               return key;
            }
         }
         return null;
      }

      private static int firstNumber(String text, int fallback) {
         if (text == null) {
            return fallback;
         }
         Matcher matcher = NUMBER.matcher(text);
         return matcher.find() ? parseInt(matcher.group(1)) : fallback;
      }

      private static int parseInt(String value) {
         try {
            return Integer.parseInt(value.replace(",", ""));
         } catch (NumberFormatException error) {
            return 0;
         }
      }
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

   private long retryMillis() {
      long seconds = Math.max(10L, this.plugin.getConfig().getLong("quests.site-sync.retry-seconds", 60L));
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

   /** Site quests that can be tracked by the internal engine. */
   List<QuestDefinition> mappedDefinitions() {
      List<QuestDefinition> result = new ArrayList<>();
      for (SiteQuest quest : this.cache) {
         QuestDefinition definition = quest.toDefinition();
         if (definition != null) {
            result.add(definition);
         }
      }
      return result;
   }

   void refreshIfStale() {
      if (!this.enabled() || this.fetching) return;
      long now = System.currentTimeMillis();
      if (now - this.lastAttempt < this.cacheMillis()) return;
      this.lastAttempt = now;
      this.fetching = true;
      try {
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
                   // parse() returns null for a body that is not a JSON array
                   // (error envelope, HTML, empty). Never let such a response
                   // replace the last good cache with an empty quest list.
                   if (parsed == null) {
                      this.plugin.getLogger().warning("Site quest sync returned an unexpected body; keeping cached quests.");
                      this.retrySooner();
                   } else {
                      this.cache = parsed;
                      this.plugin.getLogger().info("Site quest sync loaded " + parsed.size() + " quests.");
                      // Register on the main thread as soon as a good sync lands,
                      // so progress tracks before the player ever opens the UI.
                      Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.questService.registerSiteQuests(this.mappedDefinitions()));
                   }
                } else {
                  this.plugin.getLogger().warning("Site quest sync returned HTTP " + response.statusCode() + ".");
                  this.retrySooner();
               }
            } catch (Exception error) {
               this.plugin.getLogger().warning("Site quest sync failed: " + error.getMessage());
               this.retrySooner();
            } finally {
               this.fetching = false;
            }
         });
      } catch (Exception rejected) {
         // Scheduler refused the task (plugin disabling); reset so a later
         // call can retry instead of the flag being stuck true forever.
         this.fetching = false;
      }
   }

   private void retrySooner() {
      long now = System.currentTimeMillis();
      this.lastAttempt = now - this.cacheMillis() + this.retryMillis();
   }

   void invalidate() {
      this.lastAttempt = 0L;
   }

   /**
    * @return the parsed quests, or {@code null} when the payload is not a JSON
    *     array (so a malformed/HTML/error response can never wipe the cache).
    *     A valid empty array returns an empty list.
    */
   private static List<SiteQuestService.SiteQuest> parse(String body) {
      List<SiteQuestService.SiteQuest> result = new ArrayList<>();
      if (body == null || body.isBlank()) return null;
      JsonElement root = JsonParser.parseString(body);
      if (!root.isJsonArray()) return null;
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
               string(object, "difficulty"),
               stringList(object, "conditionTypes"),
               stringList(object, "rewardTypes"),
               string(object, "status"),
               stringList(object, "dependencies")
            )
         );
      }
      return result;
   }

   private static String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value == null || value.isJsonNull() ? null : value.getAsString();
   }

   private static List<String> stringList(JsonObject object, String key) {
      List<String> result = new ArrayList<>();
      JsonElement value = object.get(key);
      if (value == null || value.isJsonNull() || !value.isJsonArray()) {
         return result;
      }
      for (JsonElement element : value.getAsJsonArray()) {
         if (!element.isJsonNull()) {
            result.add(element.getAsString());
         }
      }
      return result;
   }
}
