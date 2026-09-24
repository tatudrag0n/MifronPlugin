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
      var section = this.mifron().getPlayerSection(player.getUniqueId());
      Set<String> killed = new HashSet<>(section.getStringList("killed-mobs"));
      Set<String> eliteKilled = new HashSet<>(section.getStringList("elite-killed-mobs"));
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
         boolean elite = eliteKilled.contains(type.name());
         int count = section.getInt("mob-kill-counts." + type.name(), 0);
         inventory.setItem(slot++, this.mifron().statusItem(done && egg != null ? egg : Material.GRAY_STAINED_GLASS_PANE, (done ? "\u00a7a" : "\u00a78") + this.mobDisplayName(type),
            List.of("\u00a77\u8a0e\u4f10\u6570: " + count, elite ? "\u00a76\u30a8\u30ea\u30fc\u30c8: \u8a0e\u4f10\u6e08" : "\u00a78\u30a8\u30ea\u30fc\u30c8: \u672a\u8a0e\u4f10"), done));
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

   /** "追加" link at each status tab end: future content lives on the official site. */
   protected void fillStatusMore(Inventory inventory, String tab) {
      String base = tab == null ? "progress" : tab.split(":")[0];
      inventory.setItem(44, this.mifron().actionItem(Material.WRITABLE_BOOK, "\u00a7e\u8ffd\u52a0",
         List.of("\u00a77\u4eca\u5f8c\u306e\u30b3\u30f3\u30c6\u30f3\u30c4\u306f公式サイトで確認"), "status_more", base));
   }

   protected String officialSiteUrl() {
      return this.mifron().getConfig().getString("official-site-url", "https://mifron.mct-official.com/");
   }

   /** Rare-item collection tab: discovered vs undiscovered SpecialType finds. */
   protected void fillCollectionTab(Player player, Inventory inventory) {
      Set<String> found = new HashSet<>(this.mifron().getPlayerSection(player.getUniqueId()).getStringList("rare-collection"));
      SpecialItemsFeature.SpecialType[] types = SpecialItemsFeature.SpecialType.values();
      inventory.setItem(45, this.mifron().named(Material.ENDER_CHEST, "\u00a7d\u30ec\u30a2\u56f3\u9451",
         List.of("\u00a77" + found.size() + "/" + types.length)));
      int slot = 10;
      for (SpecialItemsFeature.SpecialType type : types) {
         boolean has = found.contains(type.id);
         inventory.setItem(slot++, this.mifron().statusItem(has ? type.baseMaterial : Material.GRAY_STAINED_GLASS_PANE,
            (has ? "\u00a7a" : "\u00a78") + (has ? type.displayName : "???"),
            List.of(has ? "\u00a77" + type.description : "\u00a77\u672a\u767a\u898b"), has));
      }
   }

   /** Gears tab: equipped state with a shortcut into the gear UI. */
   protected void fillGearsTab(Player player, Inventory inventory) {
      java.util.List<String> equipped = this.mifron().utilityItemsFeature.equippedGears(player);
      java.util.List<String> unlocked = this.mifron().utilityItemsFeature.unlockedGears(player);
      inventory.setItem(45, this.mifron().named(Material.IRON_CHESTPLATE, "\u00a7b\u30ae\u30a2",
         List.of("\u00a77\u88c5\u5099\u4e2d: " + equipped.size() + "/" + UtilityItemsFeature.MAX_EQUIPPED_GEARS)));
      inventory.setItem(49, this.mifron().actionItem(Material.CHEST, "\u00a7a\u30ae\u30a2\u88c5\u5099\u753b\u9762\u3092\u958b\u304f", List.of(), "menu_gear", null));
      int slot = 10;
      for (UtilityItemsFeature.GearDefinition gear : UtilityItemsFeature.GEARS.values()) {
         boolean has = unlocked.contains(gear.id());
         boolean on = has && equipped.contains(gear.id());
         inventory.setItem(slot++, this.mifron().statusItem(has ? gear.icon() : Material.GRAY_STAINED_GLASS_PANE,
            (on ? "\u00a7a" : has ? "\u00a7e" : "\u00a78") + gear.name(),
            List.of(on ? "\u00a7a\u88c5\u5099\u4e2d" : has ? "\u00a77\u672a\u88c5\u5099" : "\u00a77\u672a\u89e3\u653e: " + gear.unlockCost() + " MP"), has));
      }
   }

   protected String mobDisplayName(EntityType type) {
      String japanese = MOB_JAPANESE_NAMES.get(type.name());
      return japanese != null ? japanese : type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
   }
}
