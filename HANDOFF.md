# MifronPlugin ハンドオフ（2026-11-16 20:20 JST）

## 現在の稼働状態

**2026-11-16 に Vultr から AWS EC2（東京）へ移行済み。旧ホスト 167.179.86.103 は停止しており参照しない。**

- ホスト: **13.113.193.5**（EC2 `i-095c7b9774de1c9ff` / t4g.small / 東京）、SSH は **22番**
- リポジトリ: `/srv/mifron/full-backup/MifronPlugin`（旧 `/root/mifron-gcloud-full-backup-*/` は無い）
- 稼働管理: `mifron.service`（systemd）。再起動は `sudo systemctl restart mifron`、状態は `systemctl status mifron`
- GitHub `origin/main` = `e55d970`（特殊クラフト削減）。EC2 側は要 `git pull` + ビルド + jar差し替え（未実施）
- テスト: **186件 0失敗**（ローカル JDK25 + `mvn -o test` で確認。`mvn -o` は依存キャッシュがあるのでオフラインで通る）
- ローカル repo `C:\root\repos\MifronPlugin` は別系統（HANDOFF のみの更新場所）。コードは `origin/main` の worktree で作業する

### ローカルでのビルド手順（EC2に入れない時の検証）

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
cd C:\root\tmp\aws-main   # origin/main の worktree（git worktree add C:\root\tmp\aws-main origin/main --detach）
mvn -o test              # 186件
mvn -o -q package -DskipTests
```

## 今回のセッションで完了（コミット `55893f2` → デプロイ済み）

| 対象 | 内容 |
|---|---|
| 暗視鏡 | レリック `vision` を**削除**し、特殊クラフト品 `night_vision_scope` へ移行。高度な作業台で「暗視ポーション（通常/スプラッシュ/残留のいずれか）＋望遠鏡」を不定形に並べて作成。望遠鏡を**覗いている間だけ**暗視（4tick間隔・260tick付与）。アイテムモデル `mifron:special/night_vision_scope` |
| 旧レリック移行 | 参加時（メインインベントリ＋エンダーチェスト）と、手に持って右クリックした時に新アイテムへ置換 |
| 狙撃弓/連射式弩弓 | `setItemModel` を追加（リソパのカスタムモデルが効くように） |
| 古匠の金床 | 「所持して金床を使う」→「**設置して使う**」へ変更。`BlockPlaceEvent` でレリックをアンビルとして設置し位置を追跡（`plugins/mifron/artisan-anvils.yml` に永続化）。追跡アンビルだけが全エンチャント付与（XP/MP不要）と修繕+50% の対象。**破壊・爆発でレリック本体をドロップ**、ピストンは拒否、追跡アンビルは使用のたびに `ANVIL` へ戻して摩耗消滅を防止。既定の同時設置上限3（`advanced-enchanting.artisan-anvil.max-per-player`） |
| ドキュメント | README のレリック表から `vision` を削除、暗視鏡を特殊クラフト節へ追加、古匠の金床の説明を設置型に更新。`docs/SPECIFICATION.md` も同様 |

### 追記: 特殊クラフト22種（コミット `618e694` / `956fca8` → デプロイ済み）

レリックより弱い「暮らしの道具」を20種追加した。材料と機能を対応させ、すべて**高度な作業台でのみ**作れる（通常の作業台では成立しない）。

- **狙撃弓・連射式弩弓も高度な作業台専用へ移行**（`956fca8`）。従来は `SpecialCraftFeature.registerRecipes()` がバニラレシピとして登録していたため通常作業台でも作れた。今はカタログ（22件）に載せ、`SpecialCraftBenchFeature` が高度な作業台でのみ結果を返す。リソースパックのアイテムモデルは `SpecialCraftCatalog.itemModel()` で維持。`registerRecipes()` は削除し、`MifronPart1x1` の起動ステップからも外した。
- 装備（パッシブ・5）: 耐火の外套（炎35%減）/ 登山の命綱（落下40%減）/ 職人の前掛け（Haste I）/ 深海の兜（水中呼吸）/ 風読みの羽根（低速落下）
- 道具（7）: 携帯作業台 / 携帯エンダーチェスト / 野営セット（焚き火設置）/ 収穫の鎌（3x3収穫）/ 折り畳みボート / 足場の束 / 光のしずく（35秒だけ光を置く）
- 戦闘補助（5・すべてMOB専用でプレイヤーには無効）: 吹き矢（3ダメ＋毒I）/ 投網（前方8×横4の鈍足III）/ 煙幕玉（盲目）/ 蜘蛛の目鏡（発光）/ 氷結の瓶（粉末雪＋鈍足II）
- 回復・整備（3）: 応急包帯（+4HP）/ 毒消しの蜂蜜（毒・衰弱解除＋再生I）/ 名工の金槌（防具4部位の耐久+15）

実装の分担（すべて `src/main/java/org/server/mifron/`）:
- `SpecialCraftCatalog.java` … 20種の定義（完成品・表示名・説明・レシピ）
- `SpecialCraftRecipeRules.java` … レシピ照合の純粋関数（形の正規化・不定形の多重集合）
- `SpecialCraftKit.java` … 生成・PDC識別・クールダウン・耐久・`opensOwnUi`（チェスト等の操作を邪魔しないガード）
- `SpecialCraftBenchFeature.java` … 高度な作業台の `PrepareItemCraftEvent` で結果を差し替え
- `SpecialCraftWearableFeature` / `SpecialCraftToolFeature` / `SpecialCraftCombatFeature` / `SpecialCraftConsumableFeature` … テーマ別の効果
- 配線: `MifronBase.java` にフィールド追加、`MifronPart1x1.java` に `registerEvents` の起動ステップ追加、`SpecialCraftingFeature.isAtAdvancedTable` を package-private 化
- テスト: `SpecialCraftRecipeRulesTest`（8）と `SpecialCraftBonusRulesTest`（6、MockBukkit でプラグインをロード）

主な変更ファイル（サーバー repo）:
- `src/main/java/org/server/mifron/RelicFeature.java`（VISION削除、古匠の金床の設置許可・右クリック素通し、説明文更新）
- `src/main/java/org/server/mifron/SpecialCraftFeature.java`（`nightVisionScope()`、暗視ループ、旧レリック移行、`matchesNightVisionScope` ルール）
- `src/main/java/org/server/mifron/SpecialCraftingFeature.java`（高度な作業台の暗視鏡レシピ、`slotKind`/`isNightVisionPotion`）
- `src/main/java/org/server/mifron/AdvancedAnvilFeature.java`（設置アンビル追跡・使用判定・返却・永続化・摩耗対策）
- `src/main/java/org/server/mifron/ArtisanAnvilRules.java`（新規・純粋ルール）
- `src/test/java/org/server/mifron/ArtisanAnvilRulesTest.java` / `NightVisionScopeRecipeTest.java`（新規）

## 未完了（次回やること）

### 1. プレイヤー操作での実機検証（最優先・未実施）
サーバー起動とレシピ登録ログまでは確認済みだが、実際のアイテム操作は未検証。以下をゲーム内で確認する。
- 高度な作業台で**特殊クラフト20種が実際に作れるか**（通常の作業台では作れないこと）
- 各道具の右クリック挙動: 携帯作業台のGUIが開くか / エンダーチェスト / 焚き火設置 / 鎌の3x3収穫 / ボート / 足場 / 光のしずくが35秒で消えるか
- 装備5種のパッシブが実際に効くか（特に落下軽減はメインワールドで `MainWorldFeature` が HIGHEST で落下ダメージを処理しているため効かない可能性あり）
- クールダウン表示と、チェスト・ドアを右クリックしたときに通常操作が妨げられないこと
- 高度な作業台で暗視鏡が作れるか（通常の作業台では作れないこと）
- 暗視鏡を覗いている間だけ暗視が付き、離すと消えるか
- 古匠の金床を**設置**でき、その金床でのみXP/MP不要の全エンチャントが通り、**壊すとレリックが戻る**か。通常の金床が今まで通り動くか
- `plugins/mifron/artisan-anvils.yml` が生成され、再起動後も追跡が復元されるか
- 併せて前回からの持ち越し: エリート第1弾・第2弾の行動、狙撃弓の2倍引き絞り、連射弩の連射挙動

### 2. 旧暗視鏡レリックの移行範囲
参加時と手に持った時にしか移行しない。**チェスト/シュルカーボックス/額縁/地面ドロップ**内の旧アイテムは未移行（未知IDのレリックとして残る。NPEリスクは低い見込み）。必要ならコンテナ走査の移行を追加する。

### 3. 金床まわりの残課題
- 外部（`/setblock` やワールド編集）で置換された追跡エントリの自動掃除はない
- 破壊時に返るレリックは**新品（耐久リセット）**になる
- シルクタッチ無し・素手・クリエイティブでもレリックが戻る仕様は意図的
- 起動時明示ロードにしたい場合は `MifronPart1x1.java` の起動ステップに `advancedAnvilFeature.loadArtisanAnvils()` 相当を追加（現状は遅延ロードで配線変更不要）

## 重要な gotcha

1. **サーバーのブランチが正。** ローカル `repos/MifronPlugin` は別系統（古い）。サーバー側のソースを編集してビルドする。
2. **`ResourcePackFeature` が jar に入っているか必ず確認**（今回も 1 件存在を確認）。無いまま昇格するとリソパ配信が消える。
3. **ポート18026は Minoru bridge API**（loopback専用）。リソパは競合回避で**18027**へ自動フォールバック。18026を見て「動いていない」と判断しないこと。
4. **UFW は 18027/18028 を許可済み**。新しいポートを使う場合は UFW も開ける。
5. **共有ワークスペース**: サーバー側のファイルを編集したら**即コミット**（未コミットで他の担当のコミットが巻き戻す事故があった）。
6. **`pack.mcmeta` は `min_format`/`max_format` 形式**（旧 `pack_format` では互換性なし判定になる）。
7. **ssh は `-p 443`、scp は `-P 443`**。
8. **テスト件数は 172件**（162 + 今回の10件）。
9. **デプロイは GitHub `origin/main` 駆動。** `mifron-build-check.timer`（5分ごとに fetch/build、commit を origin へ push することもある）、`mifronplugin-deploy.timer`（毎日 21:00 UTC = 06:00 JST）。手動昇格は `systemctl start mifronplugin-deploy.service`（build→test→昇格→`mc-restart.sh` で再起動、ヘルスチェック失敗時は自動ロールバック）。
10. **この実行環境（DSH）の出力要約**: `read`/`pwsh` の出力は自動で要約され artifact に逃げることがある。**`run_in_background: true` で起動して `job_output` で読むと全文が取れる**（小さい出力はそのまま通る）。調査はこの方法で行うと速い。

## 主要ファイル

- `src/main/java/org/server/mifron/RelicFeature.java` — レリック定義（VISION 削除済み、古匠の金床は設置型）
- `src/main/java/org/server/mifron/SpecialCraftFeature.java` — 特殊クラフト（狙撃弓・連射弩・**暗視鏡**、旧レリック移行）
- `src/main/java/org/server/mifron/SpecialCraftCatalog.java` — **特殊クラフト20種の定義**（完成品・材料・レシピ）
- `src/main/java/org/server/mifron/SpecialCraftRecipeRules.java` / `SpecialCraftKit.java` / `SpecialCraftBenchFeature.java` — レシピ照合（純粋関数）/ 生成・識別・CD / 高度な作業台フック
- `src/main/java/org/server/mifron/SpecialCraftWearableFeature.java` / `SpecialCraftToolFeature.java` / `SpecialCraftCombatFeature.java` / `SpecialCraftConsumableFeature.java` — 20種の効果（テーマ別）
- `src/main/java/org/server/mifron/SpecialCraftingFeature.java` — 高度な作業台/鋼鉄かまど/**暗視鏡レシピ**（`advanced-tables.yml` に設置を永続化）
- `src/main/java/org/server/mifron/AdvancedAnvilFeature.java` — 高度な金床＋**設置型古匠の金床**（`artisan-anvils.yml`）
- `src/main/java/org/server/mifron/ArtisanAnvilRules.java` — 設置アンビルの純粋ルール
- `src/main/java/org/server/mifron/EliteMobFeature.java` — エリート（第1・第2弾実装済み）
- `src/main/java/org/server/mifron/RagnarokFeature.java` — ラグナロク
- `tools/relic-textures/` — テクスチャパイプライン（ローカル repo 側）
- `resourcepack/mifron-relics/` — リソパ素材（ローカル repo 側。配信中 zip は `main-server/plugins/mifron/resourcepack/mifron-relics.zip`）

## ビルド・デプロイ手順

```bash
# サーバー上で
cd /root/mifron-gcloud-full-backup-20261015/MifronPlugin
git fetch origin main && git reset --hard origin/main   # 起点を揃える
mvn -o -q package -DskipTests
mvn -o test                                              # 172件以上

# 昇格（推奨・自動化された経路）
systemctl start mifronplugin-deploy.service
#   → origin/main を fetch/build/test して昇格、再起動、ヘルスチェック、失敗時ロールバック
# 状態確認
cat /var/lib/mifronplugin-deploy/last-deployed
tail -3 /var/lib/mifronplugin-deploy/events.jsonl
```

## Notion

- knowledge: `[knowledge][deepseek] レリックテクスチャの真因は不透明背景／16x16実測規範とWindowsの罠`
- session-log: `[session-log][deepseek] 2026-11-14 レリックテクスチャv9 黒背景バグ特定と再制作`
- 今回分: knowledge/session-log に「暗視鏡の特殊クラフト化／古匠の金床の設置型化／DSH出力要約の回避策」を追記済み
- 追加分（特殊クラフト20種）: `[knowledge][deepseek] 高度な作業台の特殊クラフトを量産する設計（カタログ+純粋ルール+テーマ別Listener）` と同名の session-log を保存済み

## 追記: 特殊クラフトの削減（2026-11-16 / コミット `e55d970`）

20種追加した特殊クラフトを棚卸しし、**17種を削除して3種に絞った**（狙撃弓・連射弩と合わせて特殊クラフトは計8種）。

- 削除: 装備5（耐火の外套・登山の命綱・職人の前掛け・深海の兜・風読みの羽根＝既存エンチャントの代替）、バニラ代替5（携帯作業台・携帯エンダーチェスト・野営セット・折り畳みボート・足場の束）、重複4（吹き矢・蜘蛛の目鏡・氷結の瓶・毒消しの蜂蜜）、死に機能1（煙幕玉＝MOBに盲目は実質無効）、経済破壊2（応急包帯＝無コスト回復、名工の金槌＝無コスト修理）
- 残置: 収穫の鎌（3x3収穫）・光のしずく（35秒の一時光源）・投網（前方8×横4の範囲鈍足）＋狙撃弓・連射弩。**特殊クラフトは定義上すべて高度な作業台限定**（狙撃弓・連射弩もこの定義に合わせて移行済み）
- `SpecialCraftWearableFeature` / `SpecialCraftConsumableFeature` を削除、`SpecialCraftToolFeature` / `SpecialCraftCombatFeature` / `SpecialCraftCatalog` を圧縮、`MifronBase` / `MifronPart1x1` の配線も削除
- `/mf specialcraft` の一覧はカタログから自動生成のため、**25種 → 8種**に自動追随（コマンド側の修正は不要）
- 検証: ローカルで186テスト0失敗・jar生成（`ResourcePackFeature` 同梱確認）・削除クラスが jar に無いことを確認。GitHub `origin/main` へ push 済み
- **未実施: EC2 への反映**（`git pull` → `mvn -o test` → jar差し替え → `sudo systemctl restart mifron`）。このマシンから EC2 へ SSH できない（移行時の一時鍵が削除済み）ため、`~/.ssh/mifron_aws_ed25519.pub` を EC2 の `authorized_keys` に追加してもらう必要がある
