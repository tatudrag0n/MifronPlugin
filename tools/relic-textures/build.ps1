#!/usr/bin/env pwsh
<#
  レリックテクスチャ v9 のビルド。
    specs/*.spec  ->  resourcepack/.../textures/item/relic/*.png
                  ->  _review/v9/*.png (検品用)
                  ->  resourcepack/mifron-relics.zip

  このスクリプトは「現行配信の失敗」を二度と通さないためのゲートを持つ。
  現行 33/33 は不透明黒背景で、ゲーム内では黒い四角として描画されていた。
  そのため alpha とシルエット占有率を必須条件にしている。

  usage:
    pwsh -File build.ps1            # 全工程
    pwsh -File build.ps1 -CheckOnly # 検証だけ（パックへ書き込まない）
#>
param(
  [switch]$CheckOnly,
  [switch]$SkipPack
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $here '..\..')).Path
$specDir = Join-Path $here 'specs'
$texDir = Join-Path $repo 'resourcepack\mifron-relics\assets\mifron\textures\item\relic'
$reviewDir = Join-Path $repo '_review\v9'

function Step($msg) { Write-Host "`n== $msg" -ForegroundColor Cyan }

# ---------------------------------------------------------------- 1. render
Step '1. spec を検証して描画'
Push-Location $here
try {
  & py render.py --spec-dir $specDir --check
  if ($LASTEXITCODE -ne 0) { throw "spec validation failed (exit $LASTEXITCODE)" }

  if (-not $CheckOnly) {
    & py render.py --spec-dir $specDir --out-dir $reviewDir --quiet
    if ($LASTEXITCODE -ne 0) { throw "render failed (exit $LASTEXITCODE)" }
  }
} finally { Pop-Location }

# ---------------------------------------------------------------- 2. gate
Step '2. ゲート: alpha / サイズ / シルエット / 純黒'
$gateSrc = if ($CheckOnly) { $reviewDir } else { $reviewDir }
if (-not (Test-Path $gateSrc)) { throw "no rendered output at $gateSrc" }

Push-Location $here
try {
  & py audit.py $gateSrc --json (Join-Path $repo '_review\audit-v9.json') |
    Tee-Object -Variable auditOut | Select-Object -Last 3
  if ($LASTEXITCODE -ne 0) { throw "audit failed (exit $LASTEXITCODE)" }
} finally { Pop-Location }

$rows = Get-Content (Join-Path $repo '_review\audit-v9.json') -Raw | ConvertFrom-Json
$fail = @()
foreach ($r in $rows) {
  $bad = @($r.flags | Where-Object {
      $_ -match '^(NO_ALPHA|LOW_ALPHA|SIZE_|PURE_BLACK|FLAT_1_COLOR|SMALL_BBOX|STRAY)'
    })
  if ($bad.Count -gt 0) { $fail += "$($r.file): $($bad -join ',')" }
}
if ($fail.Count -gt 0) {
  Write-Host "GATE FAILURES:" -ForegroundColor Red
  $fail | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  throw "$($fail.Count) texture(s) failed the gate"
}
Write-Host ("gate OK: {0} textures" -f $rows.Count) -ForegroundColor Green

# ---------------------------------------------------------------- 3. install
if (-not $CheckOnly) {
  Step '3. リソースパックへ配置'
  $specs = Get-ChildItem $specDir -Filter *.spec | ForEach-Object { $_.BaseName }
  $copied = 0
  foreach ($id in $specs) {
    $src = Join-Path $reviewDir "$id.png"
    if (-not (Test-Path $src)) { throw "missing rendered png: $src" }
    Copy-Item $src (Join-Path $texDir "$id.png") -Force
    $copied++
  }
  Write-Host "installed $copied texture(s) -> $texDir"

  # 参照されているのにテクスチャが無い item を検出（nightclock がこれで壊れていた）
  Step '4. items JSON とテクスチャの整合'
  $itemsRoot = Join-Path $repo 'resourcepack\mifron-relics\assets\mifron\items\relic'
  $orphan = @()
  Get-ChildItem $itemsRoot -Filter *.json | ForEach-Object {
    $p = Join-Path $texDir ($_.BaseName + '.png')
    if (-not (Test-Path $p)) { $orphan += $_.BaseName }
  }
  if ($orphan.Count -gt 0) {
    Write-Host ("WARNING: items JSON without texture: " + ($orphan -join ', ')) -ForegroundColor Yellow
  } else {
    Write-Host 'all item models have textures'
  }
}

# ---------------------------------------------------------------- 5. pack
if (-not $CheckOnly -and -not $SkipPack) {
  Step '5. リソースパックを zip 化'
  & pwsh -NoProfile -File (Join-Path $here 'pack-resourcepack.ps1')
  if ($LASTEXITCODE -ne 0) { throw "pack failed (exit $LASTEXITCODE)" }
}

Step 'done'
