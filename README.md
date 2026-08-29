# Mifron Plugin

Paper 1.21向けのMifronサーバー統合プラグインです。実装に基づくFFA以外の詳細仕様は [`docs/non-ffa-code-spec.md`](docs/non-ffa-code-spec.md) を参照してください。

## コマンド

- メイン: `/mifron`、短縮: `/mf`
- プレイヤー向け: `/mf balance`、`/mf pay <player> <amount>`、`/mf build enter|exit`、`/mf athletic ranking <name> [monthly|alltime]`
- `/mv` はMifronでは登録せず、Multiverse-Core専用です。
- 管理系サブコマンドは原則 `mifron.admin` または個別権限が必要です。

## プレイヤー向け主要仕様

- ウォレットは左クリックでMP残高を確認し、棚ショップ・スロット・オークションでは持って右クリックします。アイテム収納には使えません。
- オークション入札時はMPを一時預かりし、他人に最高額を更新された場合は即時返金します。終了時に落札品と売上を自動配送します。
- 通常のエメラルドはMPへ変換されません。FFA外で死亡すると所持MPの50%を失います。
- テレポーターは右クリックで候補を展開し、表示アイテムを左クリックすると移動します。
- 棚ショップの商品は見本で、在庫は同じ素材について全棚で共有されます。ウォレットで購入し、棚の商品と同じ通常アイテムを持って右クリックすると1個売却します。
- 樽ショップは既定で27枠、先頭3枠が掘り出し物枠です。購入した枠だけ新しい商品へ入れ替わります。
- SurvivalではTNTと溶岩が無効です。ショップ化ブロックと承認済み建築は通常破壊できません。

## 設定

`src/main/resources/config.yml` には現行コードが参照する設定だけを置きます。

- `build-world`: プレイヤー別Buildワールド、ワールド境界、初期足場。WorldEdit権限は滞在中だけ付与されます。
- `athletic.defaults`: クリア、自己ベスト、歴代1位、月間順位の報酬。
- `world-rules`: 全ワールドのKeepInventory、PvP、固定昼、スポーン位置。
- `regen.allowed-chunks`: 警告と再生成候補の許可リスト。ただし現行Paper APIではチャンク再生成処理自体が無効です。
- `regen.nation-chunks` / `public-facility-chunks` / `staff-excluded-chunks`: 中央範囲以外の保護チャンク。
- `barrel-shop`: 商品枠と掘り出し物枠。
- `auction`: 通常・スニーク時の入札加算額。手数料設定はありません。
- `minoru-bridge`: ローカルAPIの有効化、待受、共有シークレット。`serverSecret` は旧設定からのフォールバックです。

`backupBeforeRegen`、`customOreGeneration`、`vanillaOreMode` は、現行コードでは再生成が実行されないため削除しました。

## 保護の実装範囲

Vanillaの `spawn-protection` はカスタムショップ操作より先にイベントを止める可能性があるため、推奨値は `0` です。

```properties
spawn-protection=0
```

Mifronの保護チャンクでは、通常のブロック破壊・設置、扉・ボタン・コンテナ・額縁・防具立て・看板・ホッパー移送と、爆発・ピストン・液体・延焼を制限します。管理者権限は保護を迂回できます。ショップ化ブロックとSurvivalへ設置した承認済み建築は別処理でも破壊・設置から保護されます。

## 現在未完了の機能

- Proposalはゲーム内作成・投票に対応せず、外部から `proposals.yml` へ入った提案を管理者が審査する機能です。
- `/mf regen` は現行Paper APIで再生成できないため失敗します。

## 保存データ

- `data.yml`: UUID、名前、MP、ステータス、フレンド、ショップ、オークション、各種進捗。
- `quests.yml`: クエスト定義。デイリー・ウィークリーは各期間5件、マンスリーは固定表示、スペシャルは条件解放です。
- `structures.yml`、`text-displays.yml`、`ffa-stats.yml`: 管理コンテンツ、設置記録、FFA戦績。
- `proposals.yml`: 外部から投入された提案と審査結果。
- `inventory-groups/*.dat`: Survival系と通常系のインベントリ、装備、オフハンド、選択スロット、XP、満腹度、隠し満腹度。
- `minoru-bridge.yml`: API取引の重複防止状態。

これらはサーバー固有データのため公開リポジトリへ追加しないでください。

## FFA

現行実装は18キットです。詳細は [`docs/ffa-kits-field-items-manual-test.md`](docs/ffa-kits-field-items-manual-test.md) を参照してください。

## 法的注意

MifronはMojangまたはMicrosoftの公式・公認サービスではありません。MPやゲーム内報酬を現実通貨や換金可能な価値と交換せず、収益化時はMinecraft EULAとUsage Guidelinesに従ってください。
