package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player-facing handbook. The tutorial explains one action at a time; this
 * book is the reference players can keep after graduation and re-open with
 * {@code /mf guide} (or the menu's ガイド entry).
 */
final class PlayerGuide {
   private PlayerGuide() {
   }

   static final NamespacedKey GUIDE_KEY = new NamespacedKey("mifron", "guide_book");
   static final String CURRENT_VERSION = "v3";
   static final String TITLE = "Mifron はじめの一歩";

   private static final String[] PAGES = {
      "§1Mifron はじめの一歩\n\n"
         + "§0ようこそ！\n"
         + "この本は、最初に知っておきたいことをまとめたものです。\n\n"
         + "なくしたら §9/mf guide§0 でいつでも取り出せます。",
      "§1MP(通貨)\n\n"
         + "§0狩り・採掘・釣り・売買・クエスト・投票などで増えます。\n\n"
         + "§c死亡すると、所持MPの50%を失います。§0\n"
         + "無理をせず、保護や装備を整えてから出かけましょう。",
      "§1メニュー\n\n"
         + "§0「メニュー」(ネザースター)を右クリック。\n"
         + "開いた項目は左クリックで選びます。\n\n"
         + "SHOP/ウォレット/ステータス/クエスト/取引/投票/ログボ/目標/ガイド/チュートリアル。",
      "§1SHOP\n\n"
         + "§0メニュー→SHOPで商品一覧を開きます。\n\n"
         + "買うとMPが減り、売るとMPが増えます。\n"
         + "棚・樽ショップも同じMPで取引できます。",
      "§1クエストと目標\n\n"
         + "§0メニュー→クエストでデイリー/ウィークリーを確認。\n"
         + "達成するとMPがもらえます。\n\n"
         + "メニュー→目標は「次にやること」のチェックリスト。\n"
         + "1件達成するごとに300MPです。",
      "§1取引(/trade)\n\n"
         + "§0/trade <相手> で申込み、相手が /trade accept。\n"
         + "専用画面でアイテムとMPを同時に交換します。\n\n"
         + "双方が確定すると成立。提示を変えると確定はリセットされ、閉じると返却されます。",
      "§1クエストを提案する\n\n"
         + "§0/mf quest propose\n\n"
         + "種類→名前→条件→必要数→報酬→依存→称号の順に入力します。\n\n"
         + "採用するかは運営が審査します。気軽にアイデアを送ってください。",
      "§1提案に投票する\n\n"
         + "§0/mf vote で提案一覧を開き、賛成投票できます。\n"
         + "もう一度クリックすると取り消せます。\n\n"
         + "§8メニューの「投票」は外部の投票サイトで、提案投票とは別です。",
      "§1ログボ・投票・紹介\n\n"
         + "§0参加するだけで連続ログインボーナス。\n"
         + "メニュー→ログボで連続日数と次の節目を確認できます。\n"
         + "14日以上空くと復帰ボーナス。\n\n"
         + "投票サイトはメニュー→投票。\n"
         + "紹介は §9/mf refer§0。6桁コードを新規プレイヤーが\n"
         + "§9/mf refer <code>§0 で入力すると、相手の卒業時に双方へ報酬。\n"
         + "§8コードの有効期限は48時間、登録は参加7日以内。",
      "§1土地を保護する\n\n"
         + "§0/mf protect chunk を実行すると、そのチャンクの保護を提案できます。\n\n"
         + "管理者の承認後に保護されます。\n"
         + "自分の建築は早めに提案しておきましょう。",
      "§1チャットと困ったとき\n\n"
         + "§0ゲーム内は全体チャットのみです。\n"
         + "個人宛の連絡はDiscordへ。\n\n"
         + "困ったら /tutorial、/mf guide、Discordサポートへ。\n"
         + "§8MifronはMojang/Microsoftの公式サービスではありません。"
   };

   private static boolean isGuide(ItemStack item) {
      return item != null
         && item.getType() == Material.WRITTEN_BOOK
         && item.hasItemMeta()
         && item.getItemMeta().getPersistentDataContainer().has(GUIDE_KEY, PersistentDataType.STRING);
   }

   private static boolean isCurrent(ItemStack item) {
      return isGuide(item)
         && CURRENT_VERSION.equals(item.getItemMeta().getPersistentDataContainer().get(GUIDE_KEY, PersistentDataType.STRING));
   }

   static boolean hasGuide(Player player) {
      if (player == null) return false;
      for (ItemStack item : player.getInventory().getContents()) {
         if (isCurrent(item)) return true;
      }
      return false;
   }

   /** Remove outdated copies so the player does not keep stale instructions. */
   private static boolean removeOutdated(Player player) {
      ItemStack[] contents = player.getInventory().getContents();
      boolean removed = false;
      for (int slot = 0; slot < contents.length; slot++) {
         ItemStack item = contents[slot];
         if (isGuide(item) && !isCurrent(item)) {
            player.getInventory().setItem(slot, null);
            removed = true;
         }
      }
      return removed;
   }

   private static void addBook(Player player) {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      meta.setTitle(TITLE);
      meta.setAuthor("Mifron運営");
      meta.addPage(PAGES);
      meta.getPersistentDataContainer().set(GUIDE_KEY, PersistentDataType.STRING, CURRENT_VERSION);
      book.setItemMeta(meta);

      for (ItemStack leftover : player.getInventory().addItem(book).values()) {
         player.getWorld().dropItemNaturally(player.getLocation(), leftover);
      }
   }

   static void give(Mifron plugin, Player player) {
      if (player == null || !player.isOnline()) return;
      if (hasGuide(player)) {
         player.sendMessage(ChatColor.YELLOW + "遊び方ガイドはすでに持っています。");
         return;
      }

      boolean updated = removeOutdated(player);
      addBook(player);
      player.sendMessage(ChatColor.GREEN + (updated
         ? "遊び方ガイド「" + TITLE + "」を最新版に更新しました。"
         : "遊び方ガイド「" + TITLE + "」を配布しました。"));
      player.sendMessage(ChatColor.GRAY + "なくしたら /mf guide で取り出せます。");
   }

   /** Called on join: silently replace a book from an older release. */
   static boolean upgradeIfOutdated(Mifron plugin, Player player) {
      if (player == null || !player.isOnline()) return false;
      if (!removeOutdated(player)) return false;
      addBook(player);
      player.sendMessage(ChatColor.GREEN + "遊び方ガイド「" + TITLE + "」を最新版に更新しました。");
      return true;
   }
}
