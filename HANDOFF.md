# MifronPlugin ハンドオフ（2026-11-15）

## 現在の稼働状態（実機で確認済み）

- サーバー: Vultr 167.179.86.103, Minecraft 26.1.2 (Paper), SSH はポート 443 のみ
- リソパ配信: **正常稼働**（18027ポート、UFW許可済み）
  - 配信sha1: `a8e562170e5eac19d63a9d86ff433c3007f154da`
  - 48テクスチャ、3Dモデル4点（金床/砥石/避雷針/盾）+ フラット31点
  - パック適用済み・外部からダウンロード可能を確認済み
- サーバーブランチ: `main@b04f90d` + `eaf3186` + `1e4c137` + `42c81a6` + `c33957b` + `43d7774` + `91fcdff`
- ローカルブランチ: `main`（clean、コミット済み）
- テスト: **162件全通過**

## 完了済み（コミット済み）

| コミット | 内容 |
|---|---|
| `71430a0` | giantcore を心臓スプライトに戻す |
| `25c2d67` | build-traced.ps1 の上書き防止 |
| `a521eee` | 盾をフラットスプライトに |
| `421b513` | テクスチャ古び処理 + 特殊アイテム3点 |
| `02eba53` | エリート実装計画 |
| `eaf3186` | エリート第1弾（自然沸き・固有名詞・体力強化・第二形態制限） |
| `1e4c137` | エリート第2弾（腐死王復活・異空の支配者・極炎の化身） |
| `42c81a6` | 共有ワークスペース巻き戻し防止 |
| `c33957b` | ラグナロクイベント |
| `43d7774` | 狙撃弓・連射式弩弓を特殊クラフト化 |
| `91fcdff` | レリックに setItemModel 追加（34経路カバー） |

## 未完了（次回やること）

### 1. 暗視鏡をレリック→特殊クラフト品に
- `RelicFeature.java` の `VISION("vision", ...)` を削除
- `SpecialCraftFeature.java` にレシピ追加: 暗視ポーション + 望遠鏡 → 高度な作業台
- モデルキー: `mifron:special/night_vision_scope`（テクスチャは作成済み）
- 既存の狙撃弓・連射弩にも `setItemModel` を追加（`sniper_bow`, `repeating_crossbow`）

### 2. 金床を「置いて使う」タイプに
- `AdvancedAnvilFeature.java` を読んでから実装（二重実装の回避）
- 設置→金床機能、破壊→レリック本体に戻る

## 重要な gotcha

1. **サーバーのブランチが正。** ローカル `repos/MifronPlugin` は別系統（古い）。サーバー側のソースを編集してビルドする。
2. **サーバーのソースに `ResourcePackFeature` が無い** → 再ビルドするとリソパ配信が消える。`b04f90d` で復旧済みだが、再ビルド時は jar を昇格する前に機能入りか確認すること。
3. **ポート18026は Minoru bridge API**（loopback専用）。リソパは競合回避で**18027**に自動フォールバック。18026を見て「動いていない」と判断しないこと。
4. **UFW は 18027/18028 を許可済み**。新しいポートを使う場合は UFW も開ける。
5. **共有ワークスペース**: サーバー側のファイルを編集したら**即コミット**（未コミットで他の担当のコミットが巻き戻す事故があった）。
6. **`pack.mcmeta` は `min_format`/`max_format` 形式**（旧 `pack_format` では互換性なし判定になる）。
7. **ssh は `-p 443`、scp は `-P 443`**。
8. **テスト件数は 162件**（以前の「163件」は間違い）。
9. **実機検証未実施**: 第1弾・第2弾のエリート行動、狙撃弓の2倍引き絞り、連射弩の連射挙動は未確認。

## 主要ファイル

- `tools/relic-textures/` — テクスチャパイプライン（trace.py, age_and_special.py, build-traced.ps1, deploy-v9.ps1, bb_mcp.py, bb_sync.py, bb_models.py）
- `resourcepack/mifron-relics/` — リソパ本体
- `src/main/java/org/server/mifron/RelicFeature.java` — レリック定義（VISION削除予定）
- `src/main/java/org/server/mifron/SpecialCraftFeature.java` — 特殊クラフト（狙撃弓・連射弩済み、暗視鏡未）
- `src/main/java/org/server/mifron/EliteMobFeature.java` — エリート（第1・第2弾実装済み）
- `src/main/java/org/server/mifron/RagnarokFeature.java` — ラグナロク
- `src/main/java/org/server/mifron/AdvancedAnvilFeature.java` — 金床（設置型化で参照必要）

## ビルド・デプロイ手順

```bash
# サーバー上で
cd /root/mifron-gcloud-full-backup-20261015/MifronPlugin
mvn -o -q package -DskipTests   # ビルド
mvn -o test                      # テスト（162件以上）
# 昇格・再起動は deploy-v9.ps1 -SkipBuild or 手動で
```

## Notion

- knowledge: `[knowledge][deepseek] レリックテクスチャの真因は不透明背景／16x16実測規範とWindowsの罠`
- session-log: `[session-log][deepseek] 2026-11-14 レリックテクスチャv9 黒背景バグ特定と再制作`
- 追記: エリート第2弾の内容を session-log に append 済み
