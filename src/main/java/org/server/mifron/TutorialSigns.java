package org.server.mifron;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rebuilds the tutorial-world guide holograms from code.
 *
 * The signs used to live only in the world save and drifted away from the
 * real feature set (jobs/gear/scratch were removed). Defining them here makes
 * the text part of the build: a deploy or restart refreshes the guide.
 *
 * Only displays within {@link #SIGN_RADIUS} of a known sign position are
 * removed, so unrelated text displays in the tutorial world are preserved.
 */
final class TutorialSigns {
   private TutorialSigns() {
   }

   private static final double SIGN_RADIUS = 2.0;
   private static final NamespacedKey SIGN_KEY = new NamespacedKey("mifron", "tutorial_sign");

   private record Line(String text, NamedTextColor color, boolean bold) {
      static Line gold(String text) { return new Line(text, NamedTextColor.GOLD, false); }
      static Line goldBold(String text) { return new Line(text, NamedTextColor.GOLD, true); }
      static Line white(String text) { return new Line(text, NamedTextColor.WHITE, false); }
      static Line gray(String text) { return new Line(text, NamedTextColor.GRAY, false); }
      static Line aqua(String text) { return new Line(text, NamedTextColor.AQUA, true); }
   }

   private record Sign(int x, int z, List<Line> lines) {
   }

   private static final List<Sign> SIGNS = List.of(
      new Sign(-3, 17, List.of(
         Line.goldBold("ようこそ Mifron"),
         Line.white("通貨はMP。稼いで使う"),
         Line.white("この道を北へ進むと城"),
         Line.gray("7段階で基本を体験"))),
      new Sign(2, 17, List.of(
         Line.gold("[1] メニュー"),
         Line.white("メニュー(ネザースター)を右クリック"),
         Line.white("項目は左クリックで選ぶ"),
         Line.gray("SHOPはSurvivalのみ"))),
      new Sign(-3, 13, List.of(
         Line.gold("[2] ゾンビを3体討伐"),
         Line.white("道の上の金ブロックのゾンビ"),
         Line.white("左クリックで攻撃"),
         Line.gray("1体30MP / 倒すと復活"))),
      new Sign(2, 13, List.of(
         Line.goldBold("MPの稼ぎ方"),
         Line.white("Survivalでは狩り/採掘/釣り"),
         Line.white("売買・クエスト・投票も"),
         Line.gray("目標達成でもボーナス"))),
      new Sign(-3, -3, List.of(
         Line.gold("[3] Eliteを討伐"),
         Line.white("北の金ブロックの紫ネーム"),
         Line.white("HP3倍で硬いがドロップ増"),
         Line.gray("金リンゴ/ダイヤ/本 +100MP"))),
      new Sign(2, -3, List.of(
         Line.goldBold("レリックの入手"),
         Line.white("素材の入手/クラフト時に"),
         Line.white("ごく低確率で変化"),
         Line.gray("Elite討伐でも2%ドロップ"))),
      new Sign(-3, -19, List.of(
         Line.gold("[4] レリックを使う"),
         Line.white("配布のバニーホップを右クリック"),
         Line.white("前方へ強くダッシュ"),
         Line.gray("レリックは売買不可"))),
      new Sign(2, -19, List.of(
         Line.gold("レリックの例"),
         Line.white("ウサギの足→バニーホップ"),
         Line.white("持つだけで効果が出るものも"),
         Line.gray("説明はアイテムの説明文に"))),
      new Sign(-3, -23, List.of(
         Line.gold("[5] クエスト"),
         Line.white("メニュー→クエスト"),
         Line.white("デイリー/ウィークリーを確認"),
         Line.gray("達成でMP報酬"))),
      new Sign(2, -23, List.of(
         Line.goldBold("目標チェックリスト"),
         Line.white("メニュー→目標"),
         Line.white("次にやることを確認"),
         Line.gray("達成で1件300MP"))),
      new Sign(-3, -40, List.of(
         Line.gold("[6] 金床で強化"),
         Line.white("配布の本を剣に合成"),
         Line.white("結果を左クリックで取る"),
         Line.gray("レリック以外を持って開く"))),
      new Sign(2, -40, List.of(
         Line.goldBold("高度な金床"),
         Line.white("バニラ上限を超えて強化"),
         Line.white("MPとXPを消費"),
         Line.gray("結果の説明文に必要量"))),
      new Sign(-3, -52, List.of(
         Line.gold("[7] 卒業"),
         Line.white("キープ前の金ブロックを"),
         Line.white("踏むか右クリック"),
         Line.gray("Survivalへ出発"))),
      new Sign(2, -52, List.of(
         Line.goldBold("Survivalの注意"),
         Line.white("SHOPはSurvivalのみ"),
         Line.white("死亡でMP50%減"),
         Line.gray("詳しくは メニュー→ガイド"))),
      new Sign(62, 0, List.of(
         Line.aqua("提案・投票"),
         Line.white("/mf quest propose"),
         Line.white("/mf vote で賛成投票"),
         Line.gray("詳しくは メニュー→ガイド")))
   );

   static void rebuild(Mifron plugin, World world) {
      if (world == null || !plugin.getConfig().getBoolean("tutorial.rebuild-signs", true)) {
         return;
      }

      // Remove the previous generation. Only the known positions are touched.
      for (Sign sign : SIGNS) {
         Location center = new Location(world, sign.x() + 0.5, -59.85, sign.z() + 0.5);
         world.getChunkAt(sign.x() >> 4, sign.z() >> 4).load();
         for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, SIGN_RADIUS, 3.0, SIGN_RADIUS)) {
            if (entity instanceof TextDisplay display) display.remove();
         }
      }

      for (Sign sign : SIGNS) {
         world.getChunkAt(sign.x() >> 4, sign.z() >> 4).load();
         for (int i = 0; i < sign.lines().size(); i++) {
            Line line = sign.lines().get(i);
            Location location = new Location(world,
               sign.x() + 0.5,
               -59.85 + (3 - i) * 0.42,
               sign.z() + 0.5);
            world.spawn(location, TextDisplay.class, display -> {
               Component text = Component.text(line.text(), line.color());
               if (line.bold()) text = text.decorate(TextDecoration.BOLD);
               display.text(text);
               display.setBillboard(Display.Billboard.FIXED);
               display.setRotation(0.0F, 0.0F);
               display.setShadowed(true);
               display.setSeeThrough(false);
               display.setDefaultBackground(false);
               display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
               display.setTransformation(new Transformation(
                  new Vector3f(), new Quaternionf(), new Vector3f(1.2F, 1.2F, 1.2F), new Quaternionf()));
               display.getPersistentDataContainer().set(SIGN_KEY, PersistentDataType.STRING, "v2");
               // Rebuilt on every start; never persist a second copy.
               display.setPersistent(false);
            });
         }
      }
      plugin.getLogger().info("Rebuilt " + SIGNS.size() + " tutorial guide signs.");
   }
}
