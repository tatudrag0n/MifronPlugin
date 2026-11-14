<#
  MifronPlugin レリック用リソースパックを組み立てる
  - pack.mcmeta を生成
  - 各レリックの item_model 定義 (assets/mifron/items/relic/<id>.json) を生成
  - テクスチャは gen-relic-textures.ps1 が出力済みのものを使う
#>
param(
  [string]$PackDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics'),
  # 84 = Minecraft 26.1 系 (resource_major)。1.21.4 なら 46。
  [int]$PackFormat = 84
)

$ErrorActionPreference = 'Stop'

$ids = @(
  'hookshot', 'enchbook', 'jetpack', 'skates', 'resonance', 'fireball',
  'decayed_sword', 'enchgrind', 'chain', 'giantcore', 'seaswim', 'dashfoot',
  'reflect', 'nightclock', 'magnet', 'shrinkstar', 'lightning', 'blink',
  'totem', 'wither', 'bell', 'dragonbreath', 'vision'
)

# 1. pack.mcmeta
$mcmeta = [ordered]@{
  pack = [ordered]@{
    pack_format = $PackFormat
    description = 'Mifron Relic Textures - 23 relics (16x16)'
  }
}
$mcmetaPath = Join-Path $PackDir 'pack.mcmeta'
$mcmeta | ConvertTo-Json -Depth 5 | Set-Content -Path $mcmetaPath -Encoding UTF8
Write-Output "pack.mcmeta -> $mcmetaPath"

# 2. item_model 定義（1.21.4+ の items/ 方式）
$itemDir = Join-Path $PackDir 'assets\mifron\items\relic'
if (-not (Test-Path $itemDir)) { New-Item -ItemType Directory -Path $itemDir -Force | Out-Null }

foreach ($id in $ids) {
  $def = [ordered]@{
    model = [ordered]@{
      type = 'minecraft:model'
      model = "mifron:item/relic/$id"
    }
  }
  $p = Join-Path $itemDir "$id.json"
  $def | ConvertTo-Json -Depth 6 | Set-Content -Path $p -Encoding UTF8
}

# 3. アイテムモデル定義（親 = item/generated）
$modelDir = Join-Path $PackDir 'assets\mifron\models\item\relic'
if (-not (Test-Path $modelDir)) { New-Item -ItemType Directory -Path $modelDir -Force | Out-Null }

foreach ($id in $ids) {
  $m = [ordered]@{
    parent = 'minecraft:item/generated'
    textures = [ordered]@{ layer0 = "mifron:item/relic/$id" }
  }
  $p = Join-Path $modelDir "$id.json"
  $m | ConvertTo-Json -Depth 6 | Set-Content -Path $p -Encoding UTF8
}

Write-Output "item_model defs -> $itemDir ($($ids.Count))"
Write-Output "item models     -> $modelDir ($($ids.Count))"

# 4. 検証: テクスチャが全件そろっているか
$texDir = Join-Path $PackDir 'assets\mifron\textures\item\relic'
$missing = @()
foreach ($id in $ids) {
  if (-not (Test-Path (Join-Path $texDir "$id.png"))) { $missing += $id }
}
if ($missing.Count -gt 0) { throw "missing textures: $($missing -join ', ')" }
Write-Output "textures OK ($($ids.Count))"
