package org.server.mifron;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Periodic event infrastructure: a config-driven weekly schedule with
 * pre-start/start/end broadcasts, manual {@code /mf event start|stop}, and
 * an {@link #isActive(String)} gate that time-locked quests use through
 * {@code QuestDefinition#event}.
 */
final class EventService {
   record ScheduledEvent(String id, String name, DayOfWeek day, LocalTime time, int durationMinutes, int announceBeforeMinutes, boolean enabled) {}

   private final Mifron plugin;
   private final Map<String, ScheduledEvent> events = new LinkedHashMap<>();
   private final Set<String> announced = new LinkedHashSet<>();

   EventService(Mifron plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.events.clear();
      this.announced.clear();
      ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("events");
      if (section == null) return;
      for (String rawId : section.getKeys(false)) {
         ConfigurationSection entry = section.getConfigurationSection(rawId);
         if (entry == null) continue;
         LocalTime time = parseTime(entry.getString("time", ""));
         if (time == null) {
            this.plugin.getLogger().warning("Event '" + rawId + "' has an invalid time; skipping.");
            continue;
         }
         String id = rawId.toLowerCase(Locale.ROOT);
         this.events.put(id, new ScheduledEvent(
            id,
            entry.getString("name", rawId),
            parseDay(entry.getString("day", "DAILY")),
            time,
            Math.max(1, entry.getInt("duration-minutes", 60)),
            Math.max(0, entry.getInt("announce-minutes-before", 15)),
            entry.getBoolean("enabled", false)));
      }
      this.plugin.getLogger().info("Loaded " + this.events.size() + " events: " + String.join(", ", this.events.keySet()));
   }

   static LocalTime parseTime(String raw) {
      if (raw == null) return null;
      String[] parts = raw.trim().split(":");
      if (parts.length != 2) return null;
      try {
         int hour = Integer.parseInt(parts[0].trim());
         int minute = Integer.parseInt(parts[1].trim());
         if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
         return LocalTime.of(hour, minute);
      } catch (NumberFormatException e) {
         return null;
      }
   }

   static DayOfWeek parseDay(String raw) {
      if (raw == null) return null;
      String value = raw.trim().toUpperCase(Locale.ROOT);
      if (value.isBlank() || "DAILY".equals(value) || "ANY".equals(value)) return null;
      try {
         return DayOfWeek.valueOf(value);
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   /** True when the event is running (schedule window or manual start). */
   boolean isActive(String eventId) {
      if (eventId == null || eventId.isBlank()) return false;
      String key = eventId.toLowerCase(Locale.ROOT);
      if (this.manualActive(key)) return true;
      ScheduledEvent event = this.events.get(key);
      if (event == null || !event.enabled()) return false;
      DayOfWeek today = LocalDate.now().getDayOfWeek();
      if (event.day() != null && event.day() != today) return false;
      return EventRules.withinWindow(LocalTime.now(), event.time(), event.durationMinutes());
   }

   private boolean manualActive(String key) {
      FileConfiguration data = this.plugin.data();
      if (data == null) return false;
      return key.equalsIgnoreCase(data.getString("events.manual-active"))
         && data.getLong("events.manual-until", 0L) > System.currentTimeMillis();
   }

   /** Called by the minute task: broadcasts schedule announcements once each. */
   void tick() {
      LocalDate today = LocalDate.now();
      LocalTime now = LocalTime.now();
      for (ScheduledEvent event : this.events.values()) {
         if (!event.enabled()) continue;
         if (event.day() != null && event.day() != today.getDayOfWeek()) continue;
         String key = event.id() + "@" + today + "T" + event.time();
         if (EventRules.inAnnounceWindow(now, event.time(), event.announceBeforeMinutes())
            && this.announced.add(key + ":pre")) {
            int minutes = EventRules.minutesUntil(now, event.time());
            Bukkit.broadcastMessage(ChatColor.GOLD + "[\u30a4\u30d9\u30f3\u30c8] " + ChatColor.YELLOW + event.name()
               + ChatColor.WHITE + " \u304c" + minutes + "\u5206\u5f8c\u306b\u958b\u59cb!");
         }
         if (EventRules.withinWindow(now, event.time(), event.durationMinutes()) && this.announced.add(key + ":start")) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[\u30a4\u30d9\u30f3\u30c8] " + ChatColor.YELLOW + event.name()
               + ChatColor.WHITE + " \u304c\u59cb\u307e\u308a\u307e\u3057\u305f!");
         }
         LocalTime end = event.time().plusMinutes(event.durationMinutes());
         if (!now.isBefore(end) && now.isBefore(end.plusMinutes(2)) && this.announced.add(key + ":end")) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[\u30a4\u30d9\u30f3\u30c8] " + ChatColor.YELLOW + event.name()
               + ChatColor.WHITE + " \u304c\u7d42\u4e86\u3057\u307e\u3057\u305f\u3002\u53c2\u52a0\u3042\u308a\u304c\u3068\u3046!");
         }
      }
   }

   void handleCommand(CommandSender sender, String[] args) {
      if (!this.plugin.hasPermission(sender, "mifron.admin")) {
         sender.sendMessage(ChatColor.RED + "\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      if (args.length < 2) {
         sender.sendMessage(ChatColor.YELLOW + "/mf event list|start <id> [\u5206]|stop");
         return;
      }
      switch (args[1].toLowerCase(Locale.ROOT)) {
         case "list" -> {
            if (this.events.isEmpty()) {
               sender.sendMessage(ChatColor.YELLOW + "\u8a2d\u5b9a\u6e08\u307f\u30a4\u30d9\u30f3\u30c8: " + ChatColor.GRAY + "(\u306a\u3057)");
               return;
            }
            sender.sendMessage(ChatColor.YELLOW + "\u8a2d\u5b9a\u6e08\u307f\u30a4\u30d9\u30f3\u30c8:");
            for (ScheduledEvent event : this.events.values()) {
               StringBuilder line = new StringBuilder(ChatColor.YELLOW + " - " + event.id() + ChatColor.GRAY + " (" + event.name() + ") ");
               line.append(event.enabled() ? "\u6709\u52b9" : "\u7121\u52b9");
               if (event.day() != null) line.append(" ").append(event.day()).append(" ").append(event.time());
               if (this.isActive(event.id())) line.append(ChatColor.GREEN).append(" \u958b\u50ac\u4e2d");
               sender.sendMessage(line.toString());
            }
         }
         case "start" -> {
            if (args.length < 3) {
               sender.sendMessage(ChatColor.YELLOW + "/mf event start <id> [\u5206]");
               return;
            }
            ScheduledEvent event = this.events.get(args[2].toLowerCase(Locale.ROOT));
            if (event == null) {
               sender.sendMessage(ChatColor.RED + "\u672a\u767b\u9332\u306e\u30a4\u30d9\u30f3\u30c8: " + args[2]);
               return;
            }
            int minutes = args.length >= 4 ? this.plugin.parsePositiveInt(args[3], event.durationMinutes()) : event.durationMinutes();
            if (minutes <= 0) minutes = event.durationMinutes();
            this.startManual(event, minutes);
            sender.sendMessage(ChatColor.GREEN + "\u30a4\u30d9\u30f3\u30c8\u3092\u958b\u59cb: " + event.name() + " (" + minutes + "\u5206)");
         }
         case "stop" -> this.stopManual(sender);
         default -> sender.sendMessage(ChatColor.YELLOW + "/mf event list|start <id> [\u5206]|stop");
      }
   }

   private void startManual(ScheduledEvent event, int minutes) {
      FileConfiguration data = this.plugin.data();
      data.set("events.manual-active", event.id());
      data.set("events.manual-until", System.currentTimeMillis() + minutes * 60_000L);
      this.plugin.saveData();
      Bukkit.broadcastMessage(ChatColor.GOLD + "[\u30a4\u30d9\u30f3\u30c8] " + ChatColor.YELLOW + event.name()
         + ChatColor.WHITE + " \u304c\u59cb\u307e\u308a\u307e\u3057\u305f!");
   }

   private void stopManual(CommandSender sender) {
      FileConfiguration data = this.plugin.data();
      String active = data.getString("events.manual-active");
      if (active == null) {
         sender.sendMessage(ChatColor.YELLOW + "\u73fe\u5728\u30a2\u30af\u30c6\u30a3\u30d6\u306a\u30a4\u30d9\u30f3\u30c8\u306f\u3042\u308a\u307e\u305b\u3093\u3002");
         return;
      }
      ScheduledEvent event = this.events.get(active.toLowerCase(Locale.ROOT));
      data.set("events.manual-active", null);
      data.set("events.manual-until", 0L);
      this.plugin.saveData();
      String name = event == null ? active : event.name();
      Bukkit.broadcastMessage(ChatColor.GOLD + "[\u30a4\u30d9\u30f3\u30c8] " + ChatColor.YELLOW + name
         + ChatColor.WHITE + " \u304c\u7d42\u4e86\u3057\u307e\u3057\u305f\u3002\u53c2\u52a0\u3042\u308a\u304c\u3068\u3046!");
      sender.sendMessage(ChatColor.GREEN + "\u30a4\u30d9\u30f3\u30c8\u3092\u505c\u6b62: " + name);
   }
}
