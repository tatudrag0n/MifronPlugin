package org.server.mifron;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Serves the Mifron relic resource pack over HTTP and pushes it to every player on join.
 *
 * <p>The pack is a plain zip produced by {@code tools/relic-textures/pack-resourcepack.ps1}.
 * It is read from disk once at startup; if the file is missing the feature stays inert and
 * players simply see the vanilla base material for relics.
 *
 * <p>When {@code resource-pack.host} is {@code auto} (the default) the public address is
 * discovered at runtime: the bound server address is used when it is a real address,
 * otherwise the machine's public IP is looked up. Players who join before discovery
 * finishes are re-tried after a short delay.
 */
public final class ResourcePackFeature implements Listener {

   /** Delay between a join and the pack push, in ticks. */
   private static final long JOIN_DELAY_TICKS = 20L;
   /** How many times a scheduled push waits for the public URL before giving up. */
   private static final int MAX_URL_WAIT_ATTEMPTS = 10;

   private final Mifron plugin;
   private HttpServer server;
   private Path packFile;
   private byte[] packBytes;
   private String packSha1;
   private int port;
   private String urlPath;
   private String configuredHost;
   private volatile String publicUrl;
   private volatile boolean enabled;

   public ResourcePackFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   /** Starts the HTTP server when a pack file exists. Safe to call once from onEnable. */
   public void start() {
      if (!this.plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
         this.plugin.getLogger().info("Resource pack delivery disabled by config.");
         return;
      }
      String configuredPath = this.plugin.getConfig()
         .getString("resource-pack.file", "resourcepack/mifron-relics.zip");
      Path candidate = this.plugin.getDataFolder().toPath().resolve(configuredPath).normalize();
      if (!Files.isRegularFile(candidate)) {
         // Fall back to a pack shipped next to the server working directory.
         candidate = Paths.get(configuredPath).toAbsolutePath().normalize();
      }
      if (!Files.isRegularFile(candidate)) {
         this.plugin.getLogger().warning("Resource pack not found: " + configuredPath
            + " (looked in " + this.plugin.getDataFolder().toPath().resolve(configuredPath)
            + " and " + Paths.get(configuredPath).toAbsolutePath() + ")"
            + " - relics will fall back to vanilla textures.");
         return;
      }
      try {
         this.packFile = candidate;
         this.packBytes = Files.readAllBytes(candidate);
         this.packSha1 = sha1Hex(this.packBytes);
         this.port = this.plugin.getConfig().getInt("resource-pack.port", 18026);
         this.urlPath = this.plugin.getConfig().getString("resource-pack.path", "/mifron-relics.zip");
         if (!this.urlPath.startsWith("/")) {
            this.urlPath = "/" + this.urlPath;
         }
         this.configuredHost = this.plugin.getConfig().getString("resource-pack.host", "auto");
         startHttpServer(this.urlPath);
         this.enabled = true;
         this.plugin.getLogger().info("Resource pack file loaded: " + this.packBytes.length
            + " bytes, sha1=" + this.packSha1 + ", listening on port " + this.port);
         resolvePublicUrlAsync();
      } catch (IOException ex) {
         this.plugin.getLogger().log(Level.WARNING, "Failed to start resource pack delivery", ex);
      }
   }

   private void startHttpServer(String contextPath) throws IOException {
      // Other plugins on this server grab free ports dynamically at startup (Dynmap and a
      // profiler both bind 81xx). Bind explicitly to the wildcard address and walk forward
      // a few ports when the configured one is taken, so we never collide silently.
      int requested = this.port;
      IOException lastFailure = null;
      for (int offset = 0; offset <= 10; offset++) {
         int candidate = requested + offset;
         try {
            HttpServer candidateServer = HttpServer.create(
               new InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), candidate), 0);
            candidateServer.createContext(contextPath, this::handlePackRequest);
            candidateServer.setExecutor(null);
            candidateServer.start();
            this.server = candidateServer;
            if (candidate != requested) {
               this.plugin.getLogger().warning("Resource pack port " + requested
                  + " was busy; using " + candidate + " instead.");
            }
            this.port = candidate;
            return;
         } catch (IOException ex) {
            lastFailure = ex;
         }
      }
      throw lastFailure != null ? lastFailure : new IOException("no free port found");
   }

   private void handlePackRequest(HttpExchange exchange) throws IOException {
      try {
         if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
         }
         exchange.getResponseHeaders().set("Content-Type", "application/zip");
         exchange.getResponseHeaders().set("Content-Length", Integer.toString(this.packBytes.length));
         exchange.sendResponseHeaders(200, this.packBytes.length);
         try (OutputStream out = exchange.getResponseBody()) {
            out.write(this.packBytes);
         }
      } finally {
         exchange.close();
      }
   }

   /** Resolves the address clients should use, without blocking the server thread. */
   private void resolvePublicUrlAsync() {
      String host = this.configuredHost;
      if (host != null && !host.isBlank() && !"auto".equalsIgnoreCase(host)) {
         this.publicUrl = buildUrl(host.trim());
         this.plugin.getLogger().info("Resource pack URL (configured): " + this.publicUrl);
         return;
      }
      Thread worker = new Thread(() -> {
         String resolved = detectServerAddress();
         this.publicUrl = buildUrl(resolved);
         this.plugin.getLogger().info("Resource pack URL (auto-detected): " + this.publicUrl);
      }, "mifron-resourcepack-url");
      worker.setDaemon(true);
      worker.start();
   }

   private String buildUrl(String host) {
      return "http://" + host + ":" + this.port + this.urlPath;
   }

   /** Bound address first, then the machine's public IP, then loopback. */
   private String detectServerAddress() {
      try {
         String bound = Bukkit.getIp();
         if (bound != null && !bound.isBlank() && !"0.0.0.0".equals(bound) && !"::".equals(bound)) {
            return bound;
         }
      } catch (Throwable ignored) {
         // Bukkit.getIp() is not available on every API level; fall through.
      }
      String detected = fetchPublicIp("https://api.ipify.org");
      if (detected == null) {
         detected = fetchPublicIp("https://ifconfig.me/ip");
      }
      return detected != null ? detected : "127.0.0.1";
   }

   private String fetchPublicIp(String endpoint) {
      HttpURLConnection connection = null;
      try {
         URL url = new URL(endpoint);
         connection = (HttpURLConnection) url.openConnection();
         connection.setConnectTimeout(3000);
         connection.setReadTimeout(3000);
         connection.setRequestProperty("User-Agent", "curl/8");
         if (connection.getResponseCode() != 200) {
            return null;
         }
         try (InputStream in = connection.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (body.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
               return body;
            }
         }
      } catch (IOException ex) {
         this.plugin.getLogger().fine("Public IP lookup failed for " + endpoint + ": " + ex.getMessage());
      } finally {
         if (connection != null) {
            connection.disconnect();
         }
      }
      return null;
   }

   /** Pushes the pack to a joining player. No-op when delivery is not configured. */
   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      if (!this.enabled) {
         return;
      }
      Player player = event.getPlayer();
      // Delay so the client is fully in the PLAY state before the pack is pushed.
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> applyTo(player), JOIN_DELAY_TICKS);
   }

   /** Sends the resource pack to one player, waiting briefly for URL discovery. */
   public void applyTo(Player player) {
      applyTo(player, 0);
   }

   private void applyTo(Player player, int attempt) {
      if (!this.enabled || player == null || !player.isOnline()) {
         return;
      }
      String url = this.publicUrl;
      if (url == null) {
         if (attempt < MAX_URL_WAIT_ATTEMPTS) {
            Bukkit.getScheduler().runTaskLater(this.plugin,
               () -> applyTo(player, attempt + 1), JOIN_DELAY_TICKS);
         } else {
            this.plugin.getLogger().warning("Resource pack URL unresolved; skipping push for "
               + player.getName());
         }
         return;
      }
      boolean required = this.plugin.getConfig().getBoolean("resource-pack.required", true);
      String prompt = this.plugin.getConfig().getString("resource-pack.prompt",
         "Mifron のレリックテクスチャを読み込みます");
      try {
         player.setResourcePack(url, this.packSha1, required,
            net.kyori.adventure.text.Component.text(prompt));
      } catch (LinkageError legacy) {
         // Older API without the Component overload.
         player.setResourcePack(url, this.packSha1, required);
      }
      this.plugin.getLogger().info("Sent resource pack to " + player.getName() + " -> " + url);
   }

   /** Re-sends the pack to everyone online (used by the admin command). */
   public int applyToAll() {
      if (!this.enabled) {
         return 0;
      }
      List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
      for (Player player : online) {
         applyTo(player);
      }
      return online.size();
   }

   /** Stops the HTTP server. */
   public void stop() {
      if (this.server != null) {
         this.server.stop(0);
         this.server = null;
      }
      this.enabled = false;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public String getPublicUrl() {
      return this.publicUrl;
   }

   public String getSha1() {
      return this.packSha1;
   }

   public Path getPackFile() {
      return this.packFile;
   }

   /** Lowercase hex SHA-1, the format Minecraft expects for resource pack verification. */
   static byte[] sha1(byte[] data) {
      try {
         return java.security.MessageDigest.getInstance("SHA-1").digest(data);
      } catch (java.security.NoSuchAlgorithmException ex) {
         throw new IllegalStateException("SHA-1 unavailable", ex);
      }
   }

   static String sha1Hex(byte[] data) {
      byte[] digest = sha1(data);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
         sb.append(Character.forDigit((b >> 4) & 0xF, 16));
         sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
   }
}
