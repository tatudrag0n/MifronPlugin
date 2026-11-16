package org.server.mifron;

import java.util.Locale;

/**
 * イベント「ラグナロク」の純粋な計算（開催秒数の丸め・残り時間の表示・ボスバー進捗）。
 *
 * <p>Bukkit に触らないので、サーバー無しの単体テストで直接検証できる。
 */
final class RagnarokRules {
   /** コマンドで秒数を省略したときの開催時間（秒）。 */
   static final int DEFAULT_DURATION_SECONDS = 300;
   /** 短すぎる開催を防ぐ下限（秒）。 */
   static final int MIN_DURATION_SECONDS = 10;
   /** 事故で長時間エリート化が残らないようにする上限（秒）。 */
   static final int MAX_DURATION_SECONDS = 3600;

   private RagnarokRules() {
   }

   /** 希望秒数を [min, max] に丸める。壊れた設定（min > max, 0 以下）でも 1 秒以上を返す。 */
   static int clampDuration(int seconds, int min, int max) {
      int lower = Math.max(1, min);
      int upper = Math.max(lower, max);
      return Math.max(lower, Math.min(upper, seconds));
   }

   /** until が now より未来なら開催中。 */
   static boolean isActive(long nowMillis, long untilMillis) {
      return untilMillis > nowMillis;
   }

   /** 残りミリ秒。終了後は 0 を返す（負にならない）。 */
   static long remainingMillis(long nowMillis, long untilMillis) {
      return Math.max(0L, untilMillis - nowMillis);
   }

   /** 残り時間を MM:SS 表示にする。 */
   static String formatRemaining(long remainingMillis) {
      long totalSeconds = Math.max(0L, remainingMillis) / 1000L;
      return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
   }

   /** ボスバーの進捗（1.0 = 開始直後, 0.0 = 終了）。 */
   static double progress(long remainingMillis, long totalMillis) {
      if (totalMillis <= 0L) return 0.0D;
      double ratio = (double) Math.max(0L, remainingMillis) / (double) totalMillis;
      return Math.max(0.0D, Math.min(1.0D, ratio));
   }
}
