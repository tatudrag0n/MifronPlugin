package org.server.mifron;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

final class MinoruBridgeFeature {
   private static final Pattern JSON_STRING = Pattern.compile("\\\"([A-Za-z0-9_]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
   private static final Pattern JSON_NUMBER = Pattern.compile("\\\"([A-Za-z0-9_]+)\\\"\\s*:\\s*(-?\\d+)");
   private static final long CODE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
   private static final int MAX_TRANSACTIONS = 5000;

   private final Mifron plugin;
   private final SecureRandom random = new SecureRandom();
   private final Map<String, LinkCode> linkCodes = new ConcurrentHashMap<>();
   private final java.io.File stateFile;
   private final YamlConfiguration state;
   private HttpServer server;
   private String secret;
   private String analyticsEndpoint;
   private String analyticsSecret;
   private final HttpClient analyticsClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

   MinoruBridgeFeature(Mifron plugin) {
      this.plugin = plugin;
      this.stateFile = new java.io.File(plugin.getDataFolder(), "minoru-bridge.yml");
      this.state = YamlConfiguration.loadConfiguration(this.stateFile);
   }

   void start() {
      if (!this.plugin.getConfig().getBoolean("minoru-bridge.enabled", true)) {
         this.plugin.getLogger().info("Minoru bridge API is disabled by config.");
         return;
      }
      this.secret = firstNonBlank(
         this.plugin.getConfig().getString("minoru-bridge.secret"),
         this.plugin.getConfig().getString("serverSecret")
      );
      this.analyticsEndpoint = this.plugin.getConfig().getString("analytics.endpoint", "http://127.0.0.1:8124/v1/analytics/events");
      this.analyticsSecret = firstNonBlank(this.plugin.getConfig().getString("analytics.secret"));
      if (this.secret == null || this.secret.isBlank() || "change-this".equalsIgnoreCase(this.secret) || "CHANGE_ME".equalsIgnoreCase(this.secret)) {
         this.plugin.getLogger().warning("Minoru bridge API disabled: configure minoru-bridge.secret (or serverSecret) with a strong shared secret.");
         return;
      }
      String bind = this.plugin.getConfig().getString("minoru-bridge.bind", "127.0.0.1");
      int port = Math.max(1, Math.min(65535, this.plugin.getConfig().getInt("minoru-bridge.port", 8123)));
      try {
         this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
         this.server.createContext("/health", this::handleHealth);
         this.server.createContext("/v1/link/verify", this::handleVerifyLink);
         this.server.createContext("/v1/player", this::handlePlayer);
         this.server.createContext("/v1/mp", this::handleMp);
         this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
         this.server.start();
         this.plugin.getLogger().info("Minoru bridge API listening on " + bind + ":" + port);
      } catch (IOException error) {
         this.plugin.getLogger().severe("Failed to start Minoru bridge API: " + error.getMessage());
      }
   }

   void sendAnalyticsEvent(Player player, String eventName, String sessionId, String dedupeKey) {
      this.sendAnalyticsEvent(player, eventName, sessionId, dedupeKey, null);
   }

   void sendAnalyticsEvent(Player player, String eventName, String sessionId, String dedupeKey, String metadataKey, String metadataValue) {
      if (player == null) return;
      String key = metadataKey == null ? "" : metadataKey.replaceAll("[^A-Za-z0-9_.-]", "");
      String value = metadataValue == null ? "" : metadataValue.replaceAll("[^A-Za-z0-9_.:-]", "_");
      if (key.isBlank()) {
         this.sendAnalyticsEvent(player, eventName, sessionId, dedupeKey, null);
      } else {
         this.sendAnalyticsEvent(player, eventName, sessionId, dedupeKey, ",\"" + key + "\":\"" + escape(value.substring(0, Math.min(64, value.length()))) + "\"");
      }
   }

   void sendFfaDamageAnalyticsEvent(Player player, String sessionId, String dedupeKey, String kit, double damage) {
      if (player == null || !Double.isFinite(damage) || damage <= 0.0) return;
      String safeKit = kit == null ? "unknown" : kit.replaceAll("[^A-Za-z0-9_.-]", "_");
      safeKit = safeKit.substring(0, Math.min(64, safeKit.length()));
      double safeDamage = Math.min(100000.0, damage);
      this.sendAnalyticsEvent(player, "ffa_damage", sessionId, dedupeKey,
         ",\"kit\":\"" + escape(safeKit) + "\",\"damage\":" + String.format(Locale.ROOT, "%.3f", safeDamage));
   }

   void sendEconomyAnalyticsEvent(Player player, String eventName, String sessionId, String dedupeKey, int amount, int balanceAfter, String reason) {
      if (player == null) return;
      this.sendEconomyAnalyticsEvent(player.getUniqueId(), player.getWorld().getName(), eventName, sessionId, dedupeKey, amount, balanceAfter, reason);
   }

   void sendEconomyAnalyticsEvent(UUID uuid, String eventName, String sessionId, String dedupeKey, int amount, int balanceAfter, String reason) {
      this.sendEconomyAnalyticsEvent(uuid, null, eventName, sessionId, dedupeKey, amount, balanceAfter, reason);
   }

   private void sendEconomyAnalyticsEvent(UUID uuid, String world, String eventName, String sessionId, String dedupeKey, int amount, int balanceAfter, String reason) {
      int safeAmount = Math.max(0, amount);
      int safeBalance = Math.max(0, balanceAfter);
      String safeReason = reason == null ? "unclassified" : reason.replaceAll("[^A-Za-z0-9_.:-]", "_");
      safeReason = safeReason.substring(0, Math.min(48, safeReason.length()));
      this.sendAnalyticsEvent(uuid, world, eventName, sessionId, dedupeKey,
         ",\"amount\":" + safeAmount + ",\"balanceAfter\":" + safeBalance + ",\"reason\":\"" + escape(safeReason) + "\"");
   }

   private void sendAnalyticsEvent(Player player, String eventName, String sessionId, String dedupeKey, String extraMetadata) {
      if (player == null) return;
      this.sendAnalyticsEvent(player.getUniqueId(), player.getWorld().getName(), eventName, sessionId, dedupeKey, extraMetadata);
   }

   private void sendAnalyticsEvent(UUID uuid, String world, String eventName, String sessionId, String dedupeKey, String extraMetadata) {
      if (uuid == null || this.analyticsSecret == null || this.analyticsSecret.isBlank() || this.analyticsEndpoint == null || this.analyticsEndpoint.isBlank()) return;
      String worldMetadata = world == null || world.isBlank() ? "" : "\"world\":\"" + escape(world) + "\"";
      String separator = worldMetadata.isEmpty() || extraMetadata == null || extraMetadata.isEmpty() ? "" : ",";
      String payload = "{\"eventName\":\"" + escape(eventName) + "\",\"minecraftUuid\":\"" + escape(uuid.toString()) + "\",\"sessionId\":\"" + escape(sessionId) + "\",\"source\":\"minecraft\",\"dedupeKey\":\"" + escape(dedupeKey) + "\",\"metadata\":{" + worldMetadata + separator + (extraMetadata == null ? "" : extraMetadata.substring(extraMetadata.startsWith(",") ? 1 : 0)) + "}}";
      try {
         HttpRequest request = HttpRequest.newBuilder(URI.create(this.analyticsEndpoint)).timeout(Duration.ofSeconds(3))
            .header("Authorization", "Bearer " + this.analyticsSecret).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
         this.analyticsClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally(error -> null);
      } catch (IllegalArgumentException ignored) {
         this.plugin.getLogger().warning("Invalid analytics endpoint; event skipped.");
      }
   }

   void stop() {
      if (this.server != null) {
         this.server.stop(1);
         this.server = null;
      }
      this.saveState();
   }

   boolean handleCommand(Player player, String[] args) {
      if (args.length == 0 || !("link".equalsIgnoreCase(args[0]) || "discord".equalsIgnoreCase(args[0]))) return false;
      if (this.server == null) {
         player.sendMessage("§cDiscord連携APIが無効です。管理者に連絡してください。");
         return true;
      }
      this.linkCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt < System.currentTimeMillis() || entry.getValue().uuid.equals(player.getUniqueId()));
      String code;
      do { code = String.format(Locale.ROOT, "%06d", this.random.nextInt(1_000_000)); } while (this.linkCodes.containsKey(code));
      long expiresAt = System.currentTimeMillis() + CODE_TTL_MILLIS;
      this.linkCodes.put(code, new LinkCode(player.getUniqueId(), player.getName(), expiresAt));
      player.sendMessage("§b[Mifron] §fDiscord連携コード: §e§l" + code);
      player.sendMessage("§7Discordの認証チャンネルへ、この6桁コードだけを送信してください。");
      player.sendMessage("§7認証チャンネルID: 1539107105477234738");
      player.sendMessage("§7有効期限: 10分 / 一度使用すると無効になります。");
      return true;
   }

   private void handleHealth(HttpExchange exchange) throws IOException {
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, jsonError("method_not_allowed")); return; }
      send(exchange, 200, "{\"ok\":true,\"service\":\"mifron-minoru-bridge\",\"version\":\"1.0\"}");
   }

   private void handleVerifyLink(HttpExchange exchange) throws IOException {
      if (!authorize(exchange)) return;
      if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, jsonError("method_not_allowed")); return; }
      Map<String, String> body = parseJson(readBody(exchange));
      String code = body.getOrDefault("code", "");
      // The six-digit code is already bound to the Minecraft UUID and name
      // when it is issued by /mf link.  Keep username validation for legacy
      // callers that provide it, but do not require Minoru to know the name
      // before it can complete the link.
      String username = body.getOrDefault("username", "");
      if (!code.matches("\\d{6}")) { send(exchange, 400, jsonError("invalid_code")); return; }
      LinkCode link = this.linkCodes.get(code);
      if (link == null) { send(exchange, 404, jsonError("invalid_code")); return; }
      if (link.expiresAt < System.currentTimeMillis()) {
         this.linkCodes.remove(code);
         send(exchange, 410, jsonError("expired_code"));
         return;
      }
      if (!username.isBlank() && !link.name.equalsIgnoreCase(username)) { send(exchange, 409, jsonError("name_mismatch")); return; }
      this.linkCodes.remove(code);
      send(exchange, 200, "{\"ok\":true,\"uuid\":\"" + link.uuid + "\",\"name\":\"" + escape(link.name) + "\"}");
   }

   private void handlePlayer(HttpExchange exchange) throws IOException {
      if (!authorize(exchange)) return;
      if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, jsonError("method_not_allowed")); return; }
      String prefix = "/v1/player/";
      String path = exchange.getRequestURI().getPath();
      if (!path.startsWith(prefix) || path.length() <= prefix.length()) { send(exchange, 400, jsonError("missing_player")); return; }
      String requested = java.net.URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
      try {
         OfflinePlayer player = onMain(() -> findKnownPlayer(requested));
         if (player == null || player.getName() == null) { send(exchange, 404, jsonError("not_found")); return; }
         send(exchange, 200, "{\"ok\":true,\"uuid\":\"" + player.getUniqueId() + "\",\"name\":\"" + escape(player.getName()) + "\"}");
      } catch (Exception error) {
         send(exchange, 500, jsonError("internal_error"));
      }
   }

   private void handleMp(HttpExchange exchange) throws IOException {
      if (!authorize(exchange)) return;
      String path = exchange.getRequestURI().getPath();
      try {
         if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith("/v1/mp/")) {
            UUID uuid = UUID.fromString(path.substring("/v1/mp/".length()));
            if (!onMain(() -> isKnown(uuid))) { send(exchange, 404, jsonError("player_not_found")); return; }
            int balance = onMain(() -> this.plugin.getEmeralds(uuid));
            send(exchange, 200, "{\"ok\":true,\"uuid\":\"" + uuid + "\",\"balance\":" + balance + "}");
            return;
         }
         if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && ("/v1/mp/change".equals(path) || "/v1/mp/set".equals(path))) {
            String raw = readBody(exchange);
            Map<String, String> body = parseJson(raw);
            UUID uuid = UUID.fromString(body.getOrDefault("uuid", ""));
            if (!onMain(() -> isKnown(uuid))) { send(exchange, 404, jsonError("player_not_found")); return; }
            String transactionId = body.getOrDefault("transactionId", "");
            if (transactionId.isBlank() || transactionId.length() > 200) { send(exchange, 400, jsonError("invalid_transaction")); return; }
            String key = hash(transactionId);
            boolean setMode = "/v1/mp/set".equals(path);
            int requested = "/v1/mp/change".equals(path) ? number(body, "amount") : number(body, "balance");
            Result result;
            boolean replay;
            String transactionError = null;
            synchronized (this.state) {
               if (this.state.contains("transactions." + key + ".balance")) {
                  int balance = this.state.getInt("transactions." + key + ".balance");
                  int applied = this.state.getInt("transactions." + key + ".applied");
                  int before = this.state.getInt("transactions." + key + ".before", Math.max(0, balance - applied));
                  int storedRequested = this.state.getInt("transactions." + key + ".requested", requested);
                  boolean storedSetMode = this.state.getBoolean("transactions." + key + ".setMode", setMode);
                  String storedUuid = this.state.getString("transactions." + key + ".uuid", "");
                  if (!transactionMatches(storedUuid, storedSetMode, storedRequested, uuid, setMode, requested)) {
                     result = null;
                     replay = false;
                     transactionError = "transaction_id_reused";
                  } else {
                     result = new Result(balance, applied, before, storedRequested, storedSetMode);
                     replay = true;
                  }
               } else {
                  // Keep the idempotency check, MP mutation, and durable record
                  // under one lock. Without this, two concurrent retries with
                  // the same transactionId could both change the balance.
                  result = onMain(() -> applyMp(uuid, requested, setMode));
                  this.state.set("transactions." + key + ".uuid", uuid.toString());
                  this.state.set("transactions." + key + ".before", result.before);
                  this.state.set("transactions." + key + ".requested", result.requested);
                  this.state.set("transactions." + key + ".setMode", result.setMode);
                  this.state.set("transactions." + key + ".balance", result.balance);
                  this.state.set("transactions." + key + ".applied", result.applied);
                  this.state.set("transactions." + key + ".source", "minoru-bridge");
                  this.state.set("transactions." + key + ".at", System.currentTimeMillis());
                  trimTransactions();
                  saveState();
                  replay = false;
               }
            }
            if (transactionError != null) { send(exchange, 409, jsonError(transactionError)); return; }
            send(exchange, 200, response(result.balance, result.applied, replay));
            return;
         }
         send(exchange, 404, jsonError("not_found"));
      } catch (IllegalArgumentException error) {
         send(exchange, 400, jsonError("invalid_request"));
      } catch (Exception error) {
         this.plugin.getLogger().warning("Minoru bridge request failed: " + error.getMessage());
         send(exchange, 500, jsonError("internal_error"));
      }
   }

   private Result applyMp(UUID uuid, int value, boolean setMode) {
      int current = this.plugin.getEmeralds(uuid);
      int target;
      if (setMode) target = Math.max(0, Math.min(2_000_000_000, value));
      else target = (int)Math.max(0L, Math.min(2_000_000_000L, (long)current + value));
      int applied = target - current;
      if (applied > 0) this.plugin.depositEmeralds(uuid, applied);
      else if (applied < 0) this.plugin.withdrawEmeralds(uuid, -applied);
      return new Result(this.plugin.getEmeralds(uuid), applied, current, value, setMode);
   }

   private boolean authorize(HttpExchange exchange) throws IOException {
      String header = exchange.getRequestHeaders().getFirst("Authorization");
      if (header == null || !constantTimeEquals(header, "Bearer " + this.secret)) {
         send(exchange, 401, jsonError("unauthorized"));
         return false;
      }
      return true;
   }

   private OfflinePlayer findKnownPlayer(String name) {
      for (Player online : Bukkit.getOnlinePlayers()) if (online.getName().equalsIgnoreCase(name)) return online;
      for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) return offline;
      return null;
   }

   private boolean isKnown(UUID uuid) {
      Player online = Bukkit.getPlayer(uuid);
      if (online != null) return true;
      OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
      return offline.hasPlayedBefore();
   }

   private <T> T onMain(java.util.concurrent.Callable<T> task) throws Exception {
      if (Bukkit.isPrimaryThread()) return task.call();
      return Bukkit.getScheduler().callSyncMethod(this.plugin, task).get(5, TimeUnit.SECONDS);
   }

   private void trimTransactions() {
      org.bukkit.configuration.ConfigurationSection section = this.state.getConfigurationSection("transactions");
      if (section == null || section.getKeys(false).size() <= MAX_TRANSACTIONS) return;
      List<String> keys = new ArrayList<>(section.getKeys(false));
      keys.sort(Comparator.comparingLong(k -> section.getLong(k + ".at", 0L)));
      for (int i = 0; i < keys.size() - MAX_TRANSACTIONS; i++) this.state.set("transactions." + keys.get(i), null);
   }

   private void saveState() {
      java.io.File tempFile = null;
      try {
         java.io.File parent = this.stateFile.getParentFile();
         if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create bridge state directory");
         }
         tempFile = new java.io.File(parent == null ? new java.io.File(".") : parent, this.stateFile.getName() + ".tmp");
         this.state.save(tempFile);
         try {
            Files.move(tempFile.toPath(), this.stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException error) {
            Files.move(tempFile.toPath(), this.stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException error) {
         this.plugin.getLogger().warning("Failed to save Minoru bridge state: " + error.getMessage());
         if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
            this.plugin.getLogger().warning("Failed to remove temporary Minoru bridge state file.");
         }
      }
   }

   private static String readBody(HttpExchange exchange) throws IOException {
      byte[] data = exchange.getRequestBody().readNBytes(16384);
      return new String(data, StandardCharsets.UTF_8);
   }

   private static Map<String, String> parseJson(String raw) {
      Map<String, String> out = new java.util.HashMap<>();
      Matcher strings = JSON_STRING.matcher(raw);
      while (strings.find()) out.put(strings.group(1), unescape(strings.group(2)));
      Matcher numbers = JSON_NUMBER.matcher(raw);
      while (numbers.find()) out.put(stringsafe(numbers.group(1)), numbers.group(2));
      return out;
   }

   private static String stringsafe(String s) { return s; }
   private static int number(Map<String, String> body, String key) { return Integer.parseInt(body.getOrDefault(key, "0")); }
   static boolean transactionMatches(String storedUuid, boolean storedSetMode, int storedRequested, UUID requestedUuid, boolean requestedSetMode, int requested) {
      return (storedUuid == null || storedUuid.isBlank() || storedUuid.equals(requestedUuid.toString()))
         && storedSetMode == requestedSetMode
         && storedRequested == requested;
   }
   private static String response(int balance, int applied, boolean duplicate) { return "{\"ok\":true,\"balance\":" + balance + ",\"applied\":" + applied + ",\"duplicate\":" + duplicate + "}"; }
   private static String jsonError(String code) { return "{\"ok\":false,\"error\":\"" + escape(code) + "\"}"; }
   private static String hash(String value) {
      try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
      catch (Exception error) { throw new IllegalStateException(error); }
   }
   private static String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return null; }
   private static boolean constantTimeEquals(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
   private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", ""); }
   private static String unescape(String value) { return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\"); }
   private static void send(HttpExchange exchange, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.sendResponseHeaders(status, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
   }

   private record LinkCode(UUID uuid, String name, long expiresAt) {}
   private record Result(int balance, int applied, int before, int requested, boolean setMode) {}
}
