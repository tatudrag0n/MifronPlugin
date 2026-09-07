# server-v2-overhaul 実装メモ

このブランチでは、要望リストのうちプラグイン側で安全に追加実装できる範囲を新規クラスとして追加しています。既存の巨大なコアファイル(`Mifron.java` 354KB, `FfaManager.java` 145KB 等)の内容は本環境から直接読み取れなかったため、誤破壊を避けて上書きせず、すべて追加ファイル方式で実装しました。

## 追加ファイルと有効化コード

`Mifron.java` の `onEnable()` に以下を追加:

```java
// 商人 買取専用UI (18種ランダム)
MerchantBuybackFeature.register(this);
// 既存の商人interactハンドラ内で、バニラ取引UIを開く呼び出しを次に置き換え:
// MerchantBuybackFeature.open(player);

// Survivalワールド リスポーン固定 (0,101,0)
SurvivalSpawnFeature.register(this, "world_survival"); // 実際のワールド名に置換

// Survival以外でのモブスポーン禁止
MobSpawnRestrictionFeature.register(this, "world_survival");

// /tell /msg /w 等の個人チャットコマンド禁止
PrivateChatBlocker.register(this);

// 特殊アイテム7種 (低確率変換・不可壊・取引不可)
SpecialItemsFeature.register(this);

// エリートモブ (0.2%変化・HP3倍・攻撃2倍・ドロップ&MP5倍)
EliteMobFeature.register(this);
```

## 個別クラスの補足

- `EnchantDisplayUtil.java`: 既存のロア生成コードで `EnchantDisplayUtil.format(japaneseName, level, maxLevel)` を呼ぶだけで「鋭さ X -MAX」表示が可能。差し込み箇所は `AdvancedAnvilFeature.java` 内のエンチャント名生成部分と思われますが、内容を直接確認できなかったため未実施。
- `BanRollbackFeature.java`: CoreProtect本体はサーバーへの別途インストールが必要なため、API呼び出し部分はコメントで明示したプレースホルダー。導入後に pom.xml へ provided 依存を追加し、コメントを実装に置き換えること。
- `MerchantBuybackFeature.java`: MP入金は既存エコノミーAPI (`Mifron.getEconomyService()` 等) への接続が必要(コメント参照)。

## このバッチで実装していない項目(要: 別途対応)

- ONLINE SHOP / 棚ショップ / 樽ショップの大規模UI改修(既存 `OnlineShopFeature.java` の内容確認が必要)
- Marketワールド削除・Survivalワールドでの棚ショップ許可(`WorldRulesFeature.java` / `InventoryGroupFeature.java` の既存実装確認が必要)
- 再生成システムの除外制化・月一更新
- クエスト・チュートリアル・提案サイト (`officialsites` リポジトリ側)
- DiscordBot-Minoru の認証通知
- CoreProtect / DiscordSRV / アンチチート導入、GCP VM監視、Tebex連携等のインフラ・外部サービス設定
- JAVA版専用ランチャー作成

これらは次バッチで既存ファイルの内容を取得しながら順次対応します。
