from pathlib import Path

p = Path('src/main/java/org/server/mifron/FfaFieldItemManager.java')
s = p.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'{label}: target not found')
    s = s.replace(old, new, 1)

replace_once(
    'import org.bukkit.World;\n',
    'import org.bukkit.World;\nimport org.bukkit.WeatherType;\n',
    'WeatherType import',
)

replace_once(
'''               int durationTicks = seconds * 20;
               world.setStorm(true);
               world.setWeatherDuration(durationTicks);
               world.setThundering(false);
               world.setThunderDuration(durationTicks);
            }
            this.startRainVisuals();

            for (Player player : this.ffaPlayers()) {''',
'''               int durationTicks = seconds * 20;
               // Clear any forced-clear timer before starting the event. This keeps the
               // server-side world weather in a genuine storm state for the full event.
               world.setClearWeatherDuration(0);
               world.setStorm(true);
               world.setWeatherDuration(durationTicks);
               world.setThundering(false);
               world.setThunderDuration(durationTicks);
            }
            this.startRainVisuals();

            for (Player player : this.ffaPlayers()) {
               // Explicitly sync DOWNFALL to each FFA client. This fixes clients that
               // remain visually clear even though the FFA world is storming.
               player.setPlayerWeather(WeatherType.DOWNFALL);''',
    'rain start sync',
)

replace_once(
'''   private void stopRainWeather() {
      this.stopRainVisuals();
      World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();''',
'''   private void stopRainWeather() {
      this.stopRainVisuals();
      for (Player player : this.ffaPlayers()) {
         // Return the client to the real world weather when the event ends.
         player.resetPlayerWeather();
      }
      World world = this.ffa.center() == null ? null : this.ffa.center().getWorld();''',
    'rain stop sync',
)

replace_once(
'''   void applyActiveEventGear(Player player) {
      if (this.activeEvents.containsValue("one_shot_bow")) {''',
'''   void applyActiveEventGear(Player player) {
      if (this.activeEvents.containsValue("rain")) {
         // Players joining FFA during an active rain event must receive the same
         // client weather state as players who were present when it started.
         player.setPlayerWeather(WeatherType.DOWNFALL);
      }

      if (this.activeEvents.containsValue("one_shot_bow")) {''',
    'join rain sync',
)

p.write_text(s, encoding='utf-8', newline='\n')
