# MifronPlugin レリック テクスチャ

MifronPlugin の特殊アイテム「レリック」23種のアイテムテクスチャ一式です。
**バニラ 1.21.4 のテクスチャを土台に、色調と発光だけを加えてレリックらしさを出しています。**
ドット絵を一から描かず、Mojang の描き込みをそのまま活かす方針です。

Minecraft Java Edition 1.21.4 以降の `item_model` コンポーネント方式に対応しています。

## 収録内容

| レリック | ID | ベース素材 | バニラの元テクスチャ | 加えた改変 |
|---|---|---|---|---|
| フックショット | `hookshot` | FISHING_ROD | `item/fishing_rod` | 色相-12°・彩度+25%・金具を追加 |
| 全適用の魔導書 | `enchbook` | ENCHANTED_BOOK | `item/enchanted_book` | 彩度+15%・紫のグロー |
| ジェットパック | `jetpack` | CHAINMAIL_CHESTPLATE | `item/chainmail_chestplate` | 色相-18°・青い燃料窓 |
| スケートブーツ | `skates` | IRON_BOOTS | `item/iron_boots` | 色相-20°・青氷のブレード |
| 残響の結晶 | `resonance` | ECHO_SHARD | `item/echo_shard` | 彩度+35%・シアンのグロー |
| ファイアーボール | `fireball` | FIRE_CHARGE | `item/fire_charge` | 彩度+20%・橙のグロー |
| 朽ちた剣 | `decayed_sword` | NETHERITE_SWORD | `item/netherite_sword` | 彩度-65%・明度-15%・錆の斑点 |
| 錬金の砥石 | `enchgrind` | GRINDSTONE | `block/grindstone_side` | 色相+40°・紫のグロー |
| 拘束の鎖 | `chain` | IRON_CHAIN | `item/chain` | 彩度-40%（鈍い鉄色） |
| 巨人の心臓 | `giantcore` | HEAVY_CORE | `item/heart_of_the_sea` | 色相+140°で深紅・赤のグロー |
| 空に海写す蒼球 | `seaswim` | HEART_OF_THE_SEA | `item/heart_of_the_sea` | 彩度+20%・青のグロー |
| バニーホップ | `dashfoot` | RABBIT_FOOT | `item/rabbit_foot` | 彩度+10%・跳躍の軌跡 |
| 反射の守護盾 | `reflect` | SHIELD | `item/totem_of_undying` | 色相-25°・青のグロー |
| 常夜渡りの時計 | `nightclock` | CLOCK | `item/clock_00` | 彩度+25%・シアンのグロー |
| 磁力の石 | `magnet` | LODESTONE | `block/lodestone_top` | 彩度+15%・赤い磁極 |
| 剝蝕矮星 | `shrinkstar` | NETHER_STAR | `item/nether_star` | 色相-55°で緑・緑のグロー |
| 雷鳴の杖 | `lightning` | LIGHTNING_ROD | `block/lightning_rod` | 色相-25°・稲妻の光 |
| 次元の眼 | `blink` | ENDER_EYE | `item/ender_eye` | 彩度+25%・紫のグロー |
| 死不知のトーテム | `totem` | TOTEM_OF_UNDYING | `item/totem_of_undying` | 彩度+10%・金のグロー |
| 凋零の頭骨 | `wither` | WITHER_SKELETON_SKULL | `item/coal` | 彩度-65%・明度-25%・緑の眼光 |
| 癒やしの鐘 | `bell` | BELL | `item/bell` | 彩度+12%・緑のグロー |
| 竜の吐息 | `dragonbreath` | DRAGON_BREATH | `item/dragon_breath` | 彩度+30%・紫のグロー |
| 暗視鏡 | `vision` | SPYGLASS | `item/spyglass` | 彩度+20%・シアンのレンズ光 |

すべて 16x16 PNG、透過背景です。グローは不透明ピクセルの外周に半透明を足す方式なので、
バニラの陰影とドット配置はそのまま残ります。

## ディレクトリ構成

```
mifron-relics/
├── pack.mcmeta
├── Mifron-Relic-Textures.bbmodel   # Blockbench プロジェクト（23枚を内包）
├── assets/mifron/
│   ├── items/relic/<id>.json       # item_model 定義（1.21.4+）
│   ├── models/item/relic/<id>.json # アイテムモデル（parent: item/generated）
│   └── textures/item/relic/<id>.png
```

配布用の zip は `resourcepack/mifron-relics.zip` に出力されます。

## サーバーへの配布（参加時に自動適用）

`ResourcePackFeature` がプラグイン起動時に HTTP サーバーを立て、
参加したプレイヤーへ `setResourcePack` で自動的に送ります。

1. `plugins/mifron/resourcepack/mifron-relics.zip` に zip を置く
2. `config.yml` の `resource-pack` を設定する
3. ファイアウォールで配信ポートを開放する
4. サーバーを再起動する

```yaml
resource-pack:
  enabled: true
  file: "resourcepack/mifron-relics.zip"
  host: "auto"        # サーバーのバインド先 → 公開 IP の順に自動判定
  port: 18026         # 使用中なら +1 ずつ自動でずれる（ログに実ポートが出る）
  path: "/mifron-relics.zip"
  required: true      # 参加時に自動適用（拒否不可）
  prompt: "Mifron のレリックテクスチャを読み込みます"
```

### ハマりどころ（実際に踏んだもの）

- **`pack_format` はサーバーのバージョンに合わせる。** 26.1 系は **84**、1.21.4 は 46。
  バージョンはクライアント jar の `version.json` → `pack_version.resource_major` で確認できる。
- **zip のディレクトリ構造を潰さない。** `assets/...` の階層が失われてルート直下に
  ファイルが並ぶと、クライアントは一切読めない。`pack-resourcepack.ps1` は構造を検証する。
- **ポートは 81xx を避ける。** Dynmap が 8123、他のプラグインが 81xx を動的に確保する。
  筆者の環境では 18026 すら他のプラグインに取られたため、18026〜18036 を開放している。
- **`host: "auto"` の自動判定は非同期。** 解決前に参加したプレイヤーには、
  解決後に自動で再送される。
- zip が見つからない場合、機能は無効のまま起動し、警告ログだけ出します（バニラ表示にフォールバック）。
- リソースパック未導入のクライアントでも `item_model` は無視されるだけで、表示は壊れません。
  （ただし `required: true` の場合、パックを適用できないと参加できない点に注意）
- Geyser 経由の Bedrock プレイヤーには `setResourcePack` は効きません。

### デプロイ

```powershell
pwsh -File tools/relic-textures/deploy-relic-pack.ps1
```

テクスチャ生成 → パック再構築 → zip 化 → jar ビルド → 転送（SHA1 照合）→
`mc-restart.sh` で再起動 → 配信 URL と HTTP 200 の確認まで自動で行います。

## 再生成

```powershell
# バニラ jar からテクスチャを取り出して改変する
pwsh -File tools/relic-textures/gen-relic-textures-vanilla.ps1

# pack.mcmeta / item_model / モデル定義を作り直す
pwsh -File tools/relic-textures/build-resourcepack.ps1

# zip にまとめる
pwsh -File tools/relic-textures/pack-resourcepack.ps1

# 一覧プレビュー画像を作る
pwsh -File tools/relic-textures/make-preview.ps1
```

バニラ jar の既定パスは `%APPDATA%\.minecraft\versions\1.21.4\1.21.4.jar` です。
`-JarPath` で変更できます。

## 注意

- `pack_format` は 46（1.21.4 系）を既定にしています。対象バージョンが違う場合は
  `build-resourcepack.ps1 -PackFormat <n>` で変更してください。
- 1.21.3 以前を対象にする場合は `items/` 方式が使えないため、
  `overrides` + `custom_model_data` 方式への切り替えが必要です。
- バニラテクスチャの再配布は Mojang の EULA の範囲内で扱ってください
  （サーバー内配布用のリソースパックとしての利用を想定しています）。
