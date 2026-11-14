<#
  バニラ構造をなぞって作ったテクスチャ群をリソースパックへ入れて zip 化する。

  build.ps1 は specs/*.spec から描画するが、traced 版は spec ではなく
  「バニラ構造 + パレット置換」で作るので、build.ps1 を通すと上書きされてしまう。
  そのため traced 専用のインストーラを分けている。

  usage:
    pwsh -File build-traced.ps1                 # 既定 _review\final から入れる
    pwsh -File build-traced.ps1 -Src <dir>
    pwsh -File build-traced.ps1 -CheckOnly
#>
param(
  [string]$Src = "",
  [switch]$CheckOnly,
  [switch]$SkipPack
)

# 3D モデルとして Blockbench で作ったもの。テクスチャはモデルの UV 用パレットなので
# traced の 2D 画像で上書きしてはいけない。
$ModelIds = @('ancient_anvil', 'enchgrind', 'giantcore')

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $here '..\..')).Path
if (-not $Src) { $Src = Join-Path $repo '_review\final' }
$texDir = Join-Path $repo 'resourcepack\mifron-relics\assets\mifron\textures\item\relic'

function Step($m) { Write-Host "`n== $m" -ForegroundColor Cyan }

Step "1. 元テクスチャを検証 ($Src)"
if (-not (Test-Path $Src)) { throw "source dir not found: $Src" }
$pngs = Get-ChildItem $Src -Filter *.png | Where-Object { $ModelIds -notcontains $_.BaseName }
Write-Host "  $($pngs.Count) png (3D model ids excluded: $($ModelIds -join ','))"

Push-Location $here
try {
  & py audit.py $Src --json (Join-Path $repo '_review\audit-traced.json') | Select-Object -Last 2
} finally { Pop-Location }

$rows = Get-Content (Join-Path $repo '_review\audit-traced.json') -Raw | ConvertFrom-Json
$fail = @()
foreach ($r in $rows) {
  $bad = @($r.flags | Where-Object { $_ -match '^(NO_ALPHA|LOW_ALPHA|SIZE_|PURE_BLACK|FLAT_1_COLOR|STRAY)' })
  if ($bad.Count -gt 0) { $fail += "$($r.file): $($bad -join ',')" }
}
if ($fail.Count -gt 0) {
  $fail | ForEach-Object { Write-Host "  FAIL $_" -ForegroundColor Red }
  throw "$($fail.Count) texture(s) failed the gate"
}
Write-Host "  gate OK: $($rows.Count) textures" -ForegroundColor Green

if ($CheckOnly) { Step 'check only: done'; return }

Step "2. リソースパックへ配置"
$n = 0
foreach ($p in $pngs) {
  Copy-Item $p.FullName (Join-Path $texDir $p.Name) -Force
  $n++
}
Write-Host "  installed $n"

Step "3. items JSON とテクスチャの整合"
$itemsRoot = Join-Path $repo 'resourcepack\mifron-relics\assets\mifron\items\relic'
$orphan = @()
Get-ChildItem $itemsRoot -Filter *.json | ForEach-Object {
  if (-not (Test-Path (Join-Path $texDir ($_.BaseName + '.png')))) { $orphan += $_.BaseName }
}
if ($orphan.Count -gt 0) { Write-Host ("  WARN: no texture for: " + ($orphan -join ', ')) -ForegroundColor Yellow }
else { Write-Host '  all item models have textures' }

if (-not $SkipPack) {
  Step "4. zip 化"
  & pwsh -NoProfile -File (Join-Path $here 'pack-resourcepack.ps1')
  if ($LASTEXITCODE -ne 0) { throw "pack failed ($LASTEXITCODE)" }
}
Step 'done'
