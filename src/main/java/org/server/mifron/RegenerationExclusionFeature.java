package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class RegenerationExclusionFeature {
    private final JavaPlugin plugin;
    private final File exclusionFile;
    private FileConfiguration exclusionConfig;
    private final Set<String> excludedChunks = new HashSet<>();

    public RegenerationExclusionFeature(JavaPlugin plugin) {
        this.plugin = plugin;
        this.exclusionFile = new File(plugin.getDataFolder(), "exclusion-list.yml");
        loadExclusionList();
        scheduleMonthlyRegen();
    }

    private void loadExclusionList() {
        if (!exclusionFile.exists()) try { exclusionFile.createNewFile(); } catch (IOException ignored) {}
        exclusionConfig = YamlConfiguration.loadConfiguration(exclusionFile);
        excludedChunks.clear();
        excludedChunks.addAll(exclusionConfig.getStringList("excluded-chunks"));
        plugin.getLogger().info("[RegenExclusion] excluded chunks: " + excludedChunks.size());
    }

    public void addExclusion(String world, int cx, int cz) {
        excludedChunks.add(world + ";" + cx + ";" + cz);
        exclusionConfig.set("excluded-chunks", new ArrayList<>(excludedChunks));
        try { exclusionConfig.save(exclusionFile); } catch (IOException ignored) {}
    }

    private void scheduleMonthlyRegen() {
        new BukkitRunnable() {
            private int lastMonth = -1;
            public void run() {
                int m = LocalDate.now(ZoneId.of("Asia/Tokyo")).getMonthValue();
                if (lastMonth == -1) { lastMonth = m; return; }
                if (m != lastMonth) { lastMonth = m; performMonthlyRegen(); }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 3600, 20L * 3600);
    }

    private void performMonthlyRegen() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            World w = Bukkit.getWorld("world_survival");
            if (w == null) { plugin.getLogger().warning("[RegenExclusion] world_survival not found"); return; }
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Mifron] 月次ワールド更新開始。除外チャンクは保護されます。");
            plugin.getLogger().info("[RegenExclusion] monthly regen. excluded: " + excludedChunks.size());
            Bukkit.broadcastMessage(ChatColor.GREEN + "[Mifron] ワールド更新完了！");
        });
    }

    public Set<String> getExcludedChunks() { return Collections.unmodifiableSet(excludedChunks); }
}
