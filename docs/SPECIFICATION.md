# MifronPlugin 仕様書

最終更新: 2026-11-03 / 対象: `main` (`a137a7c`, plugin `26.1.2`)
本プラグインは Paper 26.1.2 向けの総合サーバー管理プラグインである。
通貨単位は MP（内部的にはエメラルド残高として管理）。

## 1. ワールド構成

| ワールド | 用途 |
|---|---|
| world | 既定ワールド（Hub/待機。新規参加直後の一時スポーン） |
| survival | メインサバイバル（TerraformGenerator） |
| survival_nether / survival_the_end | Survival用ディメンション |
| tutorial | 体験チュートリアル（7段階・案内板付き・卒業で通知） |
| athletic / minigame | 設定で有効化する追加ワールド（既定 off） |

Multiverse管理ワールドの実体は `world/dimensions/minecraft/<名前>/` 配下に格納される。
ワールド削除時は必ず `world/dimensions/` を先に退避すること。

## 2. 経済（MP）

- 所持MPはプレイヤーデータに保存。メニューのウォレットにカーソルで表示、左クリックで残高確認。「メニュー」自体は旧ウォレット(emerald_bundle)と同等の棚ショップ決済アイテムで、棚ショップの購入にも使える。
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

- 通常商人は `merchant-items`（18品）を候補に、購入用・売却用の候補を各8件保存。UIは1ページ6件、クリックで1個・Shift+クリックで一括取引。
- 希少商人（金色「秘宝商人」）は特殊品限定の販売1枠・買取1枠、超高額固定価格。通常商人テーブルへの特殊品混入なし（分離）。
- スポーンは survival のランダムスポーン＋ `/mifron merchant spawn|spawnrare|reroll|clear`（管理者）。
- 商人売買も `total-trades`・ farming-submission・`mp_gained` に連携。

## 5. メニュー

- 配布される「メニュー」1個を右クリックで扇状メニュー：SHOP / ウォレット / ステータス / クエスト / 取引 / 投票 / ログボ / 目標 / ガイド / チュートリアル（10項目。ギアは廃止済み）。「ガイド」は遊び方本「Mifron はじめの一歩」を取り出す。`/mf guide` でも再取得できる。ただし「メニュー」を右手に持った棚ショップ（タグ棚・旧data.yml棚）の右クリックは、ファンメニューを開かずに取引（購入/売却）に進む。樽ショップの棚は対象外。
- 項目は目の前に扇状展開し、左クリックで選択。照準の先が合っている（発光・黄色太字ラベル）項目がそのままクリック対象になる。ステータス等の詳細は専用UIを開く。20秒・離脱で自動消滅。

## 5.5 チュートリアルと遊び方ガイド

- 初参加者は自動で `tutorial` ワールドへ移動し、7段階を進行する。途中離脱しても `data.yml` の `tutorial.stage` から再開できる。
- 段階: 1 メニュー → 2 ゾンビ3体 → 3 Elite → 4 配布レリックの使用 → 5 クエスト/目標を開く → 6 金床合成 → 7 金ブロックで卒業。
- `/mf tutorial [restart|skip]`、`/tutorial` で再開・やり直し・スキップができる。スキップは残り報酬なしでSurvivalへ出る。
- 卒業時・スキップ時に遊び方本「Mifron はじめの一歩」を配布する。`/mf guide` とメニューの「ガイド」から再取得でき、旧版(v1)は次回ログイン時に最新版(v2)へ自動更新される。
- 案内板は `TutorialSigns` が起動時にコードから再生成する。Buildワールドは既定で無効のため、ガイド・案内板では建築提案を案内しない。
- チュートリアル内の配布装備は通常系インベントリに入り、Survival系とは分離される。

## 6. ステータス・クエスト・称号・進捗

- ステータスUI：メニューからは扇状に進捗・討伐・称号・図鑑・転生の5タブを展開。討伐タブは討伐数＋エリート討伐状況を日本語Mob名（全87種）で表示。図鑑タブはレアアイテム発見状況。各タブ末尾の「追加」から公式サイトへ誘導。
- 高度な金床は維持（エンチャント拡張）。チュートリアルで説明。
- 組み込みクエスト39件（デイリー/ウィークリー/マンスリー/定期/スペシャル/単発/隠し）。`quests.yml` とサイト同期で追加でき、稼働サーバーではこれより多くなる場合がある。
- 称号21種＋特殊。未解放は「???」＋達成ヒント表示。装着はタブ表示に反映。
- 進捗：mainワールドでの達成は無効（記録・報酬・称号なし、再取得可）。
- `/mf status reset` は進捗・クエスト・称号・統計を初期化し再達成可能にする。

## 7. レリック

- レリックはPDC `relic_id` で判定する売買不可の特殊アイテム。素材の入手・クラフト時にごく低確率で変化するほか、`/give` やクリエイティブ取得時にも同じ確率判定、Elite討伐ドロップ、チュートリアル配布で入手できる。
- 変化確率は `素材の希少性(baseChance) × レリックの強さ(multiplier)`。素材が希少なほど1個あたりの確率が上がり、効果が強いレリックほど下がる。基本確率は COMMON 0.100% / UNCOMMON 0.200% / RARE 0.400% / EPIC 0.800% / LEGENDARY 1.600%、倍率は MINOR ×1.0 / MEDIUM ×0.5 / MAJOR ×0.2。残響の結晶が0.200%になるよう調整している。例: 朽ちた剣 0.320%、ジェットパック 0.040%、バニーホップ 0.050%。判定は入手/クラフト1回につき1回で、スタックでも変化するのは1個だけ。
- エリートWardenを討伐したプレイヤーには、通常のエリートドロップとは別に残響の結晶を確定で1個ドロップする。
- 旧ID（scratch/recall/phoenix/tide/swift/guardian/wind等）は使用時に新ID・新表示へ自動移行する。`/mf relic <id> [player]` で付与でき、旧IDもエイリアスとして受理する。

| ID | 名前 | 効果 |
|---|---|---|
| hookshot | フックショット | ウキを引っ掛けた場所へ急接近 |
| enchbook | 全適用の魔導書 | GUIから任意のエンチャントをオフハンドへ最大レベル付与 |
| jetpack | ジェットパック | スペース長押しで上昇&前進（ブレイズパウダー燃料）、Sで緩着地 |
| skates | スケートブーツ | 足元が青氷のように滑る（スニークでブレーキ） |
| resonance | 残響の結晶 | ソニックビーム（CD8秒） |
| fireball | ファイアーボール | ガストの火球（CD3秒） |
| decayed_sword | 朽ちた剣 | 全エンチャントMAXで真価を発揮する最レア剣 |
| enchgrind | 錬金の砥石 | 設置した砥石で剥がしたエンチャントを本にして返還 |
| chain | 拘束の鎖 | 左クリックでMOBを拘束・AI停止、右クリック（空中）で延長モード |
| giantcore | 巨人の心臓 | 25秒間 巨大化（サイズ3倍） |
| seaswim | 空に海写す蒼球 | 空中を泳ぐ（移動速度は水中泳ぎ相当・スペース上昇・離すと緩降下・泳ぎ姿勢） |
| dashfoot | バニーホップ | 前方へダッシュ |
| reflect | 反射の守護盾 | 構えている間、受けたダメージを相手へ反射 |
| nightclock | 常夜渡りの時計 | 夜を朝へスキップ |
| magnet | 磁力の石 | 周囲のアイテム/XPを引き寄せ、右クリックで一括バースト |
| shrinkstar | 剝蝕矮星 | 25秒間 縮小（サイズ0.45倍） |
| lightning | 雷鳴の杖 | 視線先へ落雷（CD4秒） |
| blink | 次元の眼 | 前方20ブロックへ瞬間移動（CD3秒） |
| totem | 死不知のトーテム | 持っている間、耐性と再生（自動復活はしない） |
| wither | 凋零の頭骨 | チャージ済みウィザースクロウ（CD4秒） |
| bell | 癒やしの鐘 | 自分と周囲の味方を回復（CD6秒） |
| dragonbreath | 竜の吐息 | 前方へ指向性ブレス（ダメージ+炎上、CD6秒） |
| vision | 暗視鏡 | 望遠鏡を覗いている間だけ暗視 |

## 8. ギア（廃止済み）

- 旧ギアシステム（暗視・軽業・水棲・疾風などの解放・装備）は削除。メニューのギアタブは撤去済みで、解錠・装備データは保存されたまま新たな解放・装備は不可。
- パッシブ・移動系の代替はレリック品（釣り・クラフト等で入手）が担う。

## 9. 建築ワールド（廃止済み）

- 旧建築ワールド群（共有main・共有Creative・プレイヤー別Buildワールド）は廃止済みです。`main-world.enabled` と `build-world.enabled` はいずれも既定 `false` で、configを有効化しない限り `/mf main`・`/mf build` は動作しません。

## 10. 安全取引（/trade）

- `/trade <player>` で申込→承諾で双方に共有UI。提示枠16枠×2＋提示MP。
- 提示変更で双方の確定リセット。双方確定でMP・アイテム同時交換。タイムアウト・閉鎖・退出時は提示を返却。
- 上限は `trade.max-amount`（既定100万MP）。

## 10.5 提案・投票

- クエスト提案: `/mf quest propose`。種類→名前→条件→必要数→報酬→依存クエスト→称号の順に選択/チャット入力し、`proposals.yml` の pending に保存する。`/mf quest cancel` で中断できる。
- 提案投票: `/mf vote`。pending提案を1ページ45件で表示し、クリックで賛成投票を付与/取消する。メニューの「投票」は VotingPlugin の外部投票サイトで、提案投票とは別機能。
- 管理者審査: `/mf proposal list|review|approve|forceapprove|reject|reload`。承認で設定へ反映できるのは `title` / `custom_item` など設定で扱う種類に限る。
- 建築提出: `/mf structure submit <name> [用途]` はBuildワールド有効時のみ。WorldEditのクリップボードをSchematicとして提出し、管理者承認後だけSurvivalへ設置する。既定の `build-world.enabled` は `false`。

## 11. その他機能（概要）

- 棚/樽ショップ、チャンク保護・提案・投票、転生、エリートMob、ジャンプパッド、Minoru連携（Discord・認証・投票）。
- テストサーバー機能（config既定off）：whitelist強制＋6時間再起動＋5分前警告。

## 12. 主なコマンド

- `/mf menu|balance|pay|tutorial|guide|status|quest|merchant|protect|proposal|vote|gamerules|reload|relic <id>`（一部管理者専用）
- `/mf quest propose|cancel|progress`、`/mf refer [code]`、`/mf event list|start <id> [分]|stop`
- `/mf structure submit|submissions|register|generate|approve|reject|install|delete`（提出はBuild有効時、審査は管理者）
- `/trade <player>|accept|deny|cancel|money <額>`
- `/mifron merchant spawn|spawnrare|reroll|clear`
- `/tutorial`（`/mf tutorial` の別名）
- 廃止: `/mshop`、`/friend`、`/mf check`、`/mf debugopen`、`/mf marchant`、`/mf special`。`/mf build` / `/mf main` は有効時のみタブ補完に出る。

## 13. 設定・データ

- `plugins/mifron/config.yml`：全般・取引・商人・クエスト倍率・ワールド設定等。
- `plugins/mifron/data.yml`：プレイヤーデータ（MP・進捗・クエスト・称号・在庫・承認等）。`queueDataSave` で遅延保存。
- `plugins/mifron/quests.yml`：クエスト定義（配布時は `src/main/resources/quests.yml` が初期配置）。
- `plugins/mifron/shop-prices.yml` / `economy-price-table.yml`：価格（後者が優先）。

## 14. 運用

- ビルド：`mvn -B clean package`（Java単体テスト125件）。
- 検証フロー：`bash deploy/stage-jar.sh` → `~/mifron-staging/`（BUILD_INFO/HISTORY付き）→ ローカル検証 → 承認後のみ本番 `plugins/` へコピー＋再起動（警告・save-all・0人確認）。
- 本番 `~/MifronPlugin` は config-sync が60秒ごとに `git fetch` し、`origin/main` と双方向同期。未pushのローカルコミットは自動でpush、リモート先行分はresetで取り込み、push失敗時はコミット保護のためresetしない（分岐時は手動マージ）。
- ホストRAM 7GBのため本番（4G）と重い処理の同時実行に注意（OOM実績あり）。
- 既知のログノイズ：DiscordSRV権限WARN、Votifier外部スパム。
