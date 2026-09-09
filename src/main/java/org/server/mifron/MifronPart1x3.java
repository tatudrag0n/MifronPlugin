package org.server.mifron;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.YamlConfiguration;

abstract class MifronPart1x3 extends MifronPart1x2 {
   protected List<String> resolveVmStopCommand() throws IOException {
      String provider = this.getConfig().getString("auto-shutdown.provider", "gcloud");
      if (provider != null && provider.equalsIgnoreCase("command")) {
         String command = this.getConfig().getString("auto-shutdown.vm-stop-command", "sudo shutdown -h now");
         if (command == null || command.isBlank()) throw new IOException("auto-shutdown.vm-stop-command is empty");
         String normalized = command.trim().replaceAll("\\s+", " ");
         if (!normalized.matches("(?i)(sudo )?(shutdown -h now|poweroff)")) {
            throw new IOException("auto-shutdown.vm-stop-command is not an allowed stop command");
         }
         if (normalized.toLowerCase(Locale.ROOT).startsWith("sudo ")) {
            return normalized.equalsIgnoreCase("sudo poweroff") ? List.of("sudo", "poweroff") : List.of("sudo", "shutdown", "-h", "now");
         }
         return normalized.equalsIgnoreCase("poweroff") ? List.of("poweroff") : List.of("shutdown", "-h", "now");
      }
      String instance = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.instance"), readGceMetadata("instance/name"));
      String zonePath = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.zone"), readGceMetadata("instance/zone"));
      String zone = zoneFromMetadataPath(zonePath);
      String project = firstNonBlank(this.getConfig().getString("auto-shutdown.gcloud.project"), readGceMetadata("project/project-id"));
      if (instance == null || instance.isBlank()) throw new IOException("Could not resolve GCE instance name");
      if (zone == null || zone.isBlank()) throw new IOException("Could not resolve GCE zone");
      List<String> command = new ArrayList<>(List.of("gcloud", "compute", "instances", "stop", instance.trim(), "--zone", zone.trim()));
      if (project != null && !project.isBlank()) {
         command.add("--project");
         command.add(project.trim());
      }
      command.add("--quiet");
      return command;
   }

   protected static String zoneFromMetadataPath(String zonePathOrName) {
      if (zonePathOrName == null || zonePathOrName.isBlank()) return null;
      String value = zonePathOrName.trim();
      int slash = value.lastIndexOf('/');
      return slash >= 0 ? value.substring(slash + 1) : value;
   }

   protected static String firstNonBlank(String... values) {
      if (values == null) return null;
      for (String value : values) {
         if (value != null && !value.isBlank()) return value.trim();
      }
      return null;
   }

   protected static String readGceMetadata(String path) {
      try {
         URI uri = URI.create("http://metadata.google.internal/computeMetadata/v1/" + path);
         HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3L)).header("Metadata-Flavor", "Google").GET().build();
         HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).build()
            .send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() != 200) return null;
         String body = response.body();
         return body == null ? null : body.trim();
      } catch (Exception e) {
         return null;
      }
   }

   protected int runProcess(List<String> command) throws IOException, InterruptedException {
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
         String output = reader.lines().collect(Collectors.joining("\n"));
         if (!output.isBlank()) this.getLogger().info("VM stop command output:\n" + output);
      }
      return process.waitFor();
   }

   protected void loadData() {
      this.dataFile = new File(this.getDataFolder(), "data.yml");
      if (!this.dataFile.exists()) this.saveResource("data.yml", false);
      this.data = YamlConfiguration.loadConfiguration(this.dataFile);
   }
}
