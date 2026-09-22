package org.server.mifron;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

abstract class MifronPart12 extends MifronPart11x2 {
   protected boolean matchesTitleRequirement(Set<String> completed, String requirement) {
      for (String option : requirement.split("\\|")) {
         String key = option.trim();
         if (!key.isBlank() && (completed.contains(key) || completed.contains("minecraft:" + key))) return true;
      }
      return false;
   }

   protected void notifyUnlockedTitles(Player player, Set<String> completed) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      List<String> newlyUnlocked = new ArrayList<>();
      for (Entry<String, TitleDefinition> entry : this.mifron().titleDefinitions().entrySet()) {
         if (!notified.contains(entry.getKey()) && this.hasTitle(completed, entry.getValue())) {
            notified.add(entry.getKey());
            newlyUnlocked.add(entry.getKey());
         }
      }
      if (newlyUnlocked.isEmpty()) return;
      section.set("unlocked-titles", new ArrayList<>(notified));
      this.queueDataSave();
      newlyUnlocked.stream().sorted().forEach(title -> player.sendMessage("\u00a76\u79f0\u53f7\u3092\u7372\u5f97\u3057\u307e\u3057\u305f: \u00a7e" + title));
      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
   }

   public void unlockTitle(Player player, String title) {
      ConfigurationSection section = this.mifron().getPlayerSection(player.getUniqueId());
      Set<String> notified = new HashSet<>(section.getStringList("unlocked-titles"));
      if (notified.contains(title)) return;
      notified.add(title);
      section.set("unlocked-titles", new ArrayList<>(notified));
      this.queueDataSave();
      player.sendMessage("\u00a76\u79f0\u53f7\u3092\u7372\u5f97\u3057\u307e\u3057\u305f\uff1a\u00a7e" + title);
      player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
   }

   protected void fillKillsTab(Player player, Inventory inventory, int page) {
      Set<String> killed = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList("killed-mobs"));
      List<EntityType> mobs = this.killableMobTypes();
      int pageSize = 27;
      int maxPage = Math.max(0, (mobs.size() - 1) / pageSize);
      int safePage = Math.max(0, Math.min(page, maxPage));
      inventory.setItem(45, this.mifron().named(Material.IRON_SWORD, "\u00a7c\u8a0e\u4f10\u6e08\u307fMob", List.of("\u00a77" + mobs.stream().filter(type -> killed.contains(type.name())).count() + "/" + mobs.size())));
      inventory.setItem(49, this.mifron().named(Material.PAPER, "\u00a7e\u30da\u30fc\u30b8", List.of("\u00a77" + (safePage + 1) + "/" + (maxPage + 1))));
      if (safePage > 0) inventory.setItem(48, this.mifron().actionItem(Material.ARROW, "\u00a7f\u524d\u306e\u30da\u30fc\u30b8", List.of(), "kills_page", String.valueOf(safePage - 1)));
      if (safePage < maxPage) inventory.setItem(50, this.mifron().actionItem(Material.ARROW, "\u00a7f\u6b21\u306e\u30da\u30fc\u30b8", List.of(), "kills_page", String.valueOf(safePage + 1)));
      int slot = 9;
      int from = safePage * pageSize;
      for (EntityType type : mobs.subList(from, Math.min(mobs.size(), from + pageSize))) {
         Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
         boolean done = killed.contains(type.name());
         inventory.setItem(slot++, this.mifron().statusItem(done && egg != null ? egg : Material.GRAY_STAINED_GLASS_PANE, (done ? "\u00a7a" : "\u00a78") + this.mobDisplayName(type), List.of(done ? "\u00a7a\u8a0e\u4f10\u6e08" : "\u00a77\u672a\u8a0e\u4f10"), done));
      }
   }

   protected List<EntityType> killableMobTypes() {
      List<EntityType> mobs = new ArrayList<>();
      for (EntityType type : EntityType.values()) {
         if (type.isAlive() && type != EntityType.PLAYER && Material.matchMaterial(type.name() + "_SPAWN_EGG") != null) mobs.add(type);
      }
      mobs.sort((a, b) -> {
         int difficulty = Integer.compare(this.killDifficulty(a), this.killDifficulty(b));
         return difficulty != 0 ? difficulty : a.name().compareToIgnoreCase(b.name());
      });
      return mobs;
   }

   protected int killDifficulty(EntityType type) {
      return switch (type) {
         case CHICKEN, COW, PIG, SHEEP, RABBIT -> 1;
         case ZOMBIE, SKELETON, SPIDER, CREEPER -> 3;
         case BLAZE, WITCH, GUARDIAN -> 4;
         case ENDERMAN, WITHER_SKELETON, SHULKER -> 5;
         case WARDEN, WITHER, ENDER_DRAGON -> 6;
         default -> 3;
      };
   }

   /** Official Japanese mob names for the hunted-mob catalogue. */
   private static final Map<String, String> MOB_JAPANESE_NAMES = Map.ofEntries(
      Map.entry("ALLAY", "アレイ"), Map.entry("ARMADILLO", "アルマジロ"),
      Map.entry("AXOLOTL", "ウーパールーパー"), Map.entry("BAT", "コウモリ"),
      Map.entry("BEE", "ミツバチ"), Map.entry("BLAZE", "ブレイズ"),
      Map.entry("BOGGED", "ボグド"), Map.entry("BREEZE", "ブリーズ"),
      Map.entry("CAMEL", "ラクダ"), Map.entry("CAMEL_HUSK", "カメルハスク"),
      Map.entry("CAT", "ネコ"), Map.entry("CAVE_SPIDER", "洞窟グモ"),
      Map.entry("CHICKEN", "ニワトリ"), Map.entry("COD", "タラ"),
      Map.entry("COPPER_GOLEM", "銅ゴーレム"), Map.entry("COW", "ウシ"),
      Map.entry("CREAKING", "クリーキング"), Map.entry("CREEPER", "クリーパー"),
      Map.entry("DOLPHIN", "イルカ"), Map.entry("DONKEY", "ロバ"),
      Map.entry("DROWNED", "ドラウンド"), Map.entry("ELDER_GUARDIAN", "エルダーガーディアン"),
      Map.entry("ENDERMAN", "エンダーマン"), Map.entry("ENDERMITE", "エンダーマイト"),
      Map.entry("ENDER_DRAGON", "エンダードラゴン"), Map.entry("EVOKER", "エヴォーカー"),
      Map.entry("FOX", "キツネ"), Map.entry("FROG", "カエル"),
      Map.entry("GHAST", "ガスト"), Map.entry("GLOW_SQUID", "ヒカリイカ"),
      Map.entry("GOAT", "ヤギ"), Map.entry("GUARDIAN", "ガーディアン"),
      Map.entry("HAPPY_GHAST", "ハッピーガスト"), Map.entry("HOGLIN", "ホグリン"),
      Map.entry("HORSE", "ウマ"), Map.entry("HUSK", "ハスク"),
      Map.entry("IRON_GOLEM", "アイアンゴーレム"), Map.entry("LLAMA", "ラマ"),
      Map.entry("MAGMA_CUBE", "マグマキューブ"), Map.entry("MOOSHROOM", "ムーシュルーム"),
      Map.entry("MULE", "ラバ"), Map.entry("NAUTILUS", "ノーチラス"),
      Map.entry("OCELOT", "オセロット"), Map.entry("PANDA", "パンダ"),
      Map.entry("PARCHED", "パーチド"), Map.entry("PARROT", "オウム"),
      Map.entry("PHANTOM", "ファントム"), Map.entry("PIG", "ブタ"),
      Map.entry("PIGLIN", "ピグリン"), Map.entry("PIGLIN_BRUTE", "ピグリンブルート"),
      Map.entry("PILLAGER", "ピリジャー"), Map.entry("POLAR_BEAR", "シロクマ"),
      Map.entry("PUFFERFISH", "フグ"), Map.entry("RABBIT", "ウサギ"),
      Map.entry("RAVAGER", "ラヴェジャー"), Map.entry("SALMON", "サケ"),
      Map.entry("SHEEP", "ヒツジ"), Map.entry("SHULKER", "シュルカー"),
      Map.entry("SILVERFISH", "シルバーフィッシュ"), Map.entry("SKELETON", "スケルトン"),
      Map.entry("SKELETON_HORSE", "スケルトンホース"), Map.entry("SLIME", "スライム"),
      Map.entry("SNIFFER", "スニッファー"), Map.entry("SNOW_GOLEM", "スノーゴーレム"),
      Map.entry("SPIDER", "クモ"), Map.entry("SQUID", "イカ"),
      Map.entry("STRAY", "ストレイ"), Map.entry("STRIDER", "ストライダー"),
      Map.entry("TADPOLE", "オタマジャクシ"), Map.entry("TRADER_LLAMA", "行商ラマ"),
      Map.entry("TROPICAL_FISH", "熱帯魚"), Map.entry("TURTLE", "カメ"),
      Map.entry("VEX", "ヴェックス"), Map.entry("VILLAGER", "村人"),
      Map.entry("VINDICATOR", "ヴィンディケーター"), Map.entry("WANDERING_TRADER", "行商人"),
      Map.entry("WARDEN", "ウォーデン"), Map.entry("WITCH", "ウィッチ"),
      Map.entry("WITHER", "ウィザー"), Map.entry("WITHER_SKELETON", "ウィザースケルトン"),
      Map.entry("WOLF", "オオカミ"), Map.entry("ZOGLIN", "ゾグリン"),
      Map.entry("ZOMBIE", "ゾンビ"), Map.entry("ZOMBIE_HORSE", "ゾンビホース"),
      Map.entry("ZOMBIE_NAUTILUS", "ゾンビノーチラス"), Map.entry("ZOMBIE_VILLAGER", "村人ゾンビ"),
      Map.entry("ZOMBIFIED_PIGLIN", "ゾンビピグリン")
   );

   protected String mobDisplayName(EntityType type) {
      String japanese = MOB_JAPANESE_NAMES.get(type.name());
      return japanese != null ? japanese : type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
   }
}
