# MifronPlugin 仕様書

最終更新: 2026-09-23 / 対象: `origin/main` 最新
本プラグインは Paper 26.1.2 向けの総合サーバー管理プラグインである。
通貨単位は MP（内部的にはエメラルド残高として管理）。

## 1. ワールド構成

| ワールド | 用途 |
|---|---|
| main | 建築ワールド（岩盤平面・セミクリエ・承認制） |
| survival | メインサバイバル（TerraformGenerator） |
| athletic / minigame | アスレ・ミニゲーム（VoidGen） |
| creative / market | クリエ・市場 |
| 各 `_nether` / `_the_end` | 対応ディメンション |

Multiverse管理ワールドの実体は `main/dimensions/minecraft/<名前>/` 配下に格納される。
`main/` 削除時は必ず `main/dimensions/` を先に退避すること。

## 2. 経済（MP）

- 所持MPはプレイヤーデータに保存。メニューのウォレットにカーソルで表示、左クリックで残高確認。
- 通常のエメラルドアイテムはMPに自動変換されない。
- 送金: `/mf pay <player> <amount>`。
- 売却・クエスト・進捗・プレイ時間報酬等でMP取得。売却入金は `mp_gained` クエストにも計上。
- 決済は単一スレッドのティック内で完結し、二重支払い・MP増殖が起きない設計（購入失敗時は自動返金）。

## 3. SHOP（旧OnlineShop、Survival専用）

- 左列タブの5ジャンル：装飾 / 材料 / 道具類 / 食料 / その他（1ページ40件）。
- 装飾＝建築ブロック、材料＝クラフト素材（鉱石・ドロップ・染料・型・本等）、道具類＝武器防具・ツール・機能ブロック・レッドストーン、食料＝飲食＋ポーション、その他＝残り。
- 並び順は種類→基準価格→IDの固定順（在庫変動で並び替わらない）。
- 左クリック購入、右クリック1個売却、Shift+右クリック一括売却。売却にクールダウンなし。
- 購入クールダウンは商品ごと（lore表示）。エンチャ本・ポーション等のバリエーション商品はバニラCDを使わずloreのみ。
- エンチャ本価格はレベルごとに倍増（Lv1:X → Lv2:2X → Lv3:4X…、秘蔵2倍）。
- 全商品売却可（買取下限1MP）。バリエーション品の買取基準は売価の半分。
- 在庫制：購入で減少・売却で増加。低在庫で高騰、高在庫で下落（0.25〜4倍）。月初に各商品+5補充（月次冪等）。再起動保持。
- 深層エメラルド鉱石が最高値を維持するよう下限・クランプあり。
- SHOP表示中は自分のインベントリのアイテムをクリックすると該当商品ページへジャンプ。
- 購買音・売却音あり。特殊品（スポーンエッグ・束系・頭・トーテム等）はSHOP対象外。
- 購入・売却は `total-trades` クエストに計上（商人・樽・棚と同一基準）。

## 4. 商人

- 現在は全商人が金色「秘宝商人」。特殊品限定の販売1枠・買取1枠、超高額固定価格。
- 通常商人テーブルへの特殊品混入なし（分離）。
- スポーンは survival のランダムスポーン＋ `/mifron merchant spawn|spawnrare|reroll|clear`（管理者）。
- 商人売買も `total-trades`・ farming-submission・`mp_gained` に連携。

## 5. メニュー・テレポーター

- 配布アイテム右クリックでメニュー：SHOP / ウォレット / ステータス / クエスト / テレポーター / ギア。
- テレポーターで survival・main・FFA・登録地点へ移動（初回保護付き）。

## 6. ステータス・クエスト・称号・進捗

- ステータスUI：進捗率・MFL・クエスト・称号・討伐Mobタブ。討伐Mob名は日本語表示（全87種）。
- クエスト49件（デイリー/ウィークリー/マンスリー/定期/スペシャル/単発/隠し）。進捗系10件は廃止済み。報酬は2026-09改定で半額。
- 称号21種＋特殊。未解放は「???」＋達成ヒント表示。装着はタブ表示に反映。
- 進捗：mainワールドでの達成は無効（記録・報酬・称号なし、再取得可）。
- `/mf status reset` は進捗・クエスト・称号・統計を初期化し再達成可能にする。

## 7. ギア（最大3つ装備）

- メニューのギアタブから解放・装備。現行は暗視のみ（解放10,000MP、初回のみ課金）。
- 一度解放すればON/OFF（装備/解除）切替可。再ログイン・再起動後も維持。
- 旧暗視フラグは初回利用時に自動移行。

## 8. mainワールドルール

- セミクリエイティブ：Survival/Adventureで飛行可、クリエインベントリから建築ブロック・防具立て取得可（TNT・スポナー・技術系・スポーンエッグ・バケツ類は不可、configで追加可）、使用時も消費しない、掘削は即時破壊。
- 岩盤は誰も破壊・設置不可（WorldEdit除く）。
- スポーン中心の半径30円内は編集不可。
- 設置ブロックは承認制：設置→承認待ち（赤い靄）→承認で固定。空気・置換で自動解除。管理者は `/mf main approve [all|<player>]`。
- 直接建築の代替フロー：`/mf main submit <schematic> <x> <y> <z>` で申請→ `/mf main submissions` 確認→管理者が `/mf main approve-sub <id>`（WorldEdit自動設置、なければ手動手順表示）／`reject-sub` で却下。
- 落下ダメージ無効。エンティティ生成系アイテム使用禁止（防具立て除く）。
- 新規main生成は `mifron-flat`（STARTUP micro-plugin）でY=-64岩盤1層・構造物なし。`bukkit.yml` の `worlds.main.generator: MifronFlat:flat` が必要。

## 9. FFA

- ロビー→キット選択（防具立て）→参戦。キット：剣・弓・スナイパー・リボルバー等。
- リボルバーは1発ずつリロード（1発あたり設定値の1/6）、射撃で中断して発射可。
- キット選択地点は `ffa.kit-selection`（main）。`/mf ffa setcenter|setkits|createkits|removekits|leave|stats`。
- 退出時に装備・状態を復元。

## 10. その他機能（概要）

- アスレチック・ミニゲーム、棚/樽ショップ、チャンク保護・提案・投票、転生、スロット、エリートMob、ジャンプパッド、サーバーポータル、Discord連携・認証・投票・Tebex連携。
- テストサーバー機能（config既定off）：whitelist強制＋6時間再起動＋5分前警告。

## 11. 主なコマンド

- `/mf menu|balance|pay|shop・・|tutorial|status|quest|ffa|merchant|main|protect|proposal|vote|minigame|athletic|gamerules|reload`（一部管理者専用）
- `/mifron merchant spawn|spawnrare|reroll|clear`
- `/mf main approve|submit|submissions|approve-sub|reject-sub`
- `/mf ffa setcenter|setkits|createkits|removekits|leave|stats`

## 12. 設定・データ

- `plugins/mifron/config.yml`：全般・FFA・商人・クエスト倍率・ワールド設定等。
- `plugins/mifron/data.yml`：プレイヤーデータ（MP・進捗・クエスト・称号・在庫・承認等）。`queueDataSave` で遅延保存。
- `plugins/mifron/quests.yml`：クエスト定義（配布時は `src/main/resources/quests.yml` が初期配置）。
- `plugins/mifron/shop-prices.yml` / `economy-price-table.yml`：価格（後者が優先）。
- `bukkit.yml`：`worlds.main.generator: MifronFlat:flat`。
- `server.properties`：`level-type` は `default`（MifronFlat使用時）。

## 13. 運用

- ビルド：`mvn -B clean package`（テスト75件）。
- 検証フロー：`bash deploy/stage-jar.sh` → `~/mifron-staging/`（BUILD_INFO/HISTORY付き）→ ローカル検証 → 承認後のみ本番 `plugins/` へコピー＋再起動（警告・save-all・0人確認）。
- 本番 `~/MifronPlugin` は config-sync により `origin/main` 追従。未push作業は消えるため必ずpush。
- ホストRAM 7GBのため本番（4G）と重い処理の同時実行に注意（OOM実績あり）。
- 既知のログノイズ：ServerPortalFeatureのEventHandler警告2件、DiscordSRV権限WARN、Votifier外部スパム。
