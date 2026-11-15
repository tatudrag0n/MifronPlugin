# MifronPlugin ハンドオフ（2026-11-15 09:20 JST）

## 現在の稼働状態（実機で確認済み）

- サーバー: Vultr 167.179.86.103, Minecraft 26.1.2 (Paper), SSH はポート 443 のみ
- リソパ配信: **正常稼働**（18027ポート、UFW許可済み。18026 は Minoru bridge が使用中で自動フォールバック）
  - 配信sha1: `a8e562170e5eac19d63a9d86ff433c3007f154da`（今回変更なし。`night_vision_scope` のアイテム定義/モデル/テクスチャは既に zip 内に存在）
- サーバー repo: `main@55893f2`（GitHub origin/main と一致）
  - 起動ログで `[mifron] Special craft recipes registered: 狙撃弓 / 連射式弩弓（暗視鏡は高度な作業台のレシピ）` と `Done (35.339s)!` を確認
  - 稼働 jar sha1: `1bc5c9c2b3c8dad22cd68f41e544338376657273`
  - `/var/lib/mifronplugin-deploy/last-deployed` = `55893f2`、deploy イベント `status: SUCCESS`（2026-11-15T00:07:29Z）
- ローカル repo `C:\root\repos\MifronPlugin`: 別系統（古い）。**HANDOFF の更新のみ**行う場所
- テスト: **172件全通過**（従来162 + ArtisanAnvilRulesTest 6 + NightVisionScopeRecipeTest 4）

## 今回のセッションで完了（コミット `55893f2` → デプロイ済み）

| 対象 | 内容 |
|---|---|
| 暗視鏡 | レリック `vision` を**削除**し、特殊クラフト品 `night_vision_scope` へ移行。高度な作業台で「暗視ポーション（通常/スプラッシュ/残留のいずれか）＋望遠鏡」を不定形に並べて作成。望遠鏡を**覗いている間だけ**暗視（4tick間隔・260tick付与）。アイテムモデル `mifron:special/night_vision_scope` |
| 旧レリック移行 | 参加時（メインインベントリ＋エンダーチェスト）と、手に持って右クリックした時に新アイテムへ置換 |
| 狙撃弓/連射式弩弓 | `setItemModel` を追加（リソパのカスタムモデルが効くように） |
| 古匠の金床 | 「所持して金床を使う」→「**設置して使う**」へ変更。`BlockPlaceEvent` でレリックをアンビルとして設置し位置を追跡（`plugins/mifron/artisan-anvils.yml` に永続化）。追跡アンビルだけが全エンチャント付与（XP/MP不要）と修繕+50% の対象。**破壊・爆発でレリック本体をドロップ**、ピストンは拒否、追跡アンビルは使用のたびに `ANVIL` へ戻して摩耗消滅を防止。既定の同時設置上限3（`advanced-enchanting.artisan-anvil.max-per-player`） |
| ドキュメント | README のレリック表から `vision` を削除、暗視鏡を特殊クラフト節へ追加、古匠の金床の説明を設置型に更新。`docs/SPECIFICATION.md` も同様 |

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
