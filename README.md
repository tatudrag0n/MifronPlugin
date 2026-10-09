# Mifron Plugin

Paper 26.1.2向けのMifronサーバー統合プラグインです。実装に基づく詳細仕様は [`docs/non-ffa-code-spec.md`](docs/non-ffa-code-spec.md) と [`docs/SPECIFICATION.md`](docs/SPECIFICATION.md) を参照してください。

## ハイライト

- **ジョブシステム** — 7職(狩人・農民・付呪師・商人・釣り人・鉱夫・冒険者)+無職から好きな職を選択。対応行動の所得が増え、職固有の特典(付呪師のエンチャ上限突破、狩人のElite討伐確定報酬、鉱夫の溶岩半減など)が使えます。ジョブ限定クエストJ01-J07も毎日選べます。
- **安全なプレイヤー間取引** — `/trade` で相手と向かい合い、専用UIでアイテムとMPを同時交換。提示を変えると確定がリセットされる詐欺防止設計で、途中退出でも提示は全額返却されます。メニューの「取引」から相手選択だけで申込完了。
- **`/travel` ファストトラベル** — hub・survival・mainへ即時移動。冒険者ジョブなら無料・クールダウン10分。
- **転生・称号・図鑑** — 全進捗達成後の転生、称号21種以上、レア収集の図鑑UI。
- **Discord連携・認証・投票** — MinoruブリッジAPIによるWeb側とのMP連携、提案・投票パネル。

## コマンド

- メイン: `/mifron`、短縮: `/mf`
- プレイヤー向け: `/mf balance`、`/mf pay <player> <amount>`、`/mf tutorial [restart|skip]`、`/mf vote`、`/mf menu`、`/mf quest`、`/mf job [hunter|farmer|enchanter|merchant|fisher|miner|adventurer|none]`、`/mf protect chunk`、`/mf build enter|exit`、`/trade <player>|accept|deny|cancel|money <額>`、`/travel <hub|survival|main>`
- `/mv` はMifronでは登録せず、Multiverse-Core専用です。
- 管理系サブコマンドは原則 `mifron.admin` または個別権限が必要です。

## プレイヤー向け主要仕様

- ウォレットはメニューでクリックするとMP残高を確認でき、棚ショップ・オークションでは持って右クリックで使います。アイテム収納には使えません。
- オークション入札時はMPを一時預かりし、他人に最高額を更新された場合は即時返金します。終了時に落札品と売上を自動配送します。
- 通常のエメラルドはMPへ変換されません。死亡すると所持MPの50%を失います。
- 棚ショップの商品は見本で、在庫は同じ素材について全棚で共有されます。ウォレットで購入し、棚の商品と同じ通常アイテムを持って右クリックすると1個売却します。
- 樽ショップは既定で27枠、先頭3枠が掘り出し物枠です。購入した枠だけ新しい商品へ入れ替わります。
- SurvivalではTNTと溶岩が無効です。ショップ化ブロックと承認済み建築は通常破壊できません。

## ジョブ

7職(狩人/農民/付呪師/商人/釣り人/鉱夫/冒険者)+無職の単一選択・切替式です。

- `/mf job` で確認、`/mf job <名>` の2段階確定で変更。切替クールダウンは `jobs.change-cooldown-hours`(既定24h)。
- 対応行動に所得ボーナス(Hunter討伐+20%、Merchant売却+10%)、職限定の微少報酬(Farmer/Fisher/Miner/Adventurer)。
- 固有特典:Enchanterはエンチャ上限突破+5と超過MP倍率半減、HunterはElite討伐でダイヤ確定+討伐証、Farmerは完熟収穫+1確定、Merchantは購入割引+10%、Fisherは宝釣り、Minerは深層鉱石追加ドロップ+溶岩半減、Adventurerは `/travel` 無料・CD短縮。
- ジョブ限定デイリークエストJ01-J07は該当職のみ表示・進行します。
## 安全取引(/trade)

- `/trade <player>` で申込、相手の `/trade accept` で共有UIを開きます。メニューの「取引」から相手選択UIでも申込できます。
- 提示枠(各16枠)+提示MPを設定し、双方が確定すると同時に交換されます。提示を変更すると双方の確定がリセットされます。
- 閉鎖・退出・タイムアウト時は提示を返却します。1回の提示MP上限は `trade.max-amount`(既定100万MP)です。

## 設定

`src/main/resources/config.yml` には現行コードが参照する設定だけを置きます。

- `jobs`: ボーナス率、職限定報酬、割引、切替クールダウン、ファストトラベル費用。
- `trade`: 取引のMP上限。
- `build-world`: プレイヤー別Buildワールド、ワールド境界、初期足場。WorldEdit権限は滞在中だけ付与されます。
- `world-rules`: 全ワールドのKeepInventory、PvP、固定昼、スポーン位置。
- `regen.excluded-chunks`: 自然再生成から除外するSurvivalチャンクの除外リスト(`regen.allowed-chunks` は現行コードでは参照されません)。`/mf regen allow` で除外解除、`deny` で除外登録、`list` で除外一覧を確認し、`/mf regen [radius]` は Survival・ロード済み・保護外・除外外のチャンクだけを再生成します。
- `regen.nation-chunks` / `public-facility-chunks` / `staff-excluded-chunks`: 中央範囲以外の保護チャンク。
- `barrel-shop`: 商品枠と掘り出し物枠。
- `auction`: 通常・スニーク時の入札加算額。手数料設定はありません。
- `minoru-bridge`: ローカルAPIの有効化、待受、共有シークレット。`serverSecret` は旧設定からのフォールバックです。

再生成は破壊的操作のため、半径は最大8です。未ロードのチャンクは自動スキップします。実行前にバックアップを取得してください。

## 保護の実装範囲

Vanillaの `spawn-protection` はカスタムショップ操作より先にイベントを止める可能性があるため、推奨値は `0` です。

```properties
spawn-protection=0
```

Mifronの保護チャンクでは、扉・ボタン・コンテナ・額縁・防具立て・看板・ホッパー移送と、爆発・ピストン・液体・延焼を制限します。通常のブロック破壊・設置は現行の中央チャンク保護では止めていません。ショップ化ブロックとSurvivalへ設置した承認済み建築は別処理で破壊・設置から保護されます。

## 現在未完了の機能

- Proposalへの投票はゲーム内で可能です(`/mf vote` でGUIを開き賛成投票)。建築の提案提出(`/mf structure submit`)やクエスト提案フローもゲーム内対応で、審査(`list` / `review` / `approve` / `reject`)は管理者専用です。外部から `proposals.yml` へ入った提案も審査対象です。
- `/mf regen` は管理者権限が必要です。`allow` は除外解除、`deny` は除外登録、`list` は除外一覧、半径指定で強制再生成します。再生成対象は Survival のロード済み・保護外・除外外チャンクだけです。

## 保存データ

- `data.yml`: UUID、名前、MP、ステータス、フレンド、ジョブ、ショップ、オークション、各種進捗。
- `quests.yml`: クエスト定義。デイリー・ウィークリーは各期間5件、マンスリーは固定表示、スペシャルは条件解放、ジョブクエストは `job` 指定職のみ表示です。
- `structures.yml`、`text-displays.yml`: 管理コンテンツと設置記録。
- `proposals.yml`: 外部から投入された提案と審査結果。
- `inventory-groups/*.dat`: Survival系と通常系のインベントリ、装備、オフハンド、選択スロット、XP、満腹度、隠し満腹度。
- `minoru-bridge.yml`: API取引の重複防止状態。

これらはサーバー固有データのため公開リポジトリへ追加しないでください。

## 法的注意

MifronはMojangまたはMicrosoftの公式・公認サービスではありません。MPやゲーム内報酬を現実通貨や換金可能な価値と交換せず、収益化時はMinecraft EULAとUsage Guidelinesに従ってください。
