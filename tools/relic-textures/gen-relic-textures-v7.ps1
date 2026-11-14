# レリックテクスチャ生成 v7
#
# 方針:
#   手描きでは Mojang の職人技に勝てない。そこでバニラの実テクスチャを土台にし、
#   「名前と形が一致する素材」を選んだうえで、色だけを置き換える。
#
#   色の置き換えは輝度保存（luminance-preserving）:
#     バニラの各ピクセルの明暗（＝描き込みの陰影・ディテール）をそのまま使い、
#     色相・彩度だけを対象の色へ写す。影は青紫、ハイライトは黄へ寄せる。
#   これで「マイクラ本来の質感」と「グラデーション」が同時に残る。
#
# 使い方: pwsh -File gen-relic-textures-v7.ps1

param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics'),
  [string]$JarPath = "$env:APPDATA\.minecraft\versions\26.1\26.1.jar"
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $JarPath)) { throw "vanilla jar not found: $JarPath" }
$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'
if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }

# relic id -> @{ src = バニラパス; hue = 目標色相; sat = 目標彩度; val = 目標明度; crop = @(x,y,w,h) 任意 }
$map = [ordered]@{
  'hookshot'            = @{ src = 'item/fishing_rod';            hue = 32;  sat = 0.72; val = 0.80 }
  'jetpack'             = @{ src = 'item/chainmail_chestplate';   hue = 195; sat = 0.45; val = 0.86 }
  'skates'              = @{ src = 'item/iron_boots';             hue = 205; sat = 0.40; val = 0.90 }
  'resonance'           = @{ src = 'item/echo_shard';             hue = 182; sat = 0.62; val = 0.86 }
  'fireball'            = @{ src = 'item/fire_charge';            hue = 24;  sat = 0.78; val = 0.90 }
  'decayed_sword'       = @{ src = 'item/netherite_sword';        hue = 90;  sat = 0.18; val = 0.70 }
  'enchgrind'           = @{ src = 'block/grindstone_side';       hue = 285; sat = 0.30; val = 0.80 }
  'chain'               = @{ src = 'block/iron_chain';                 hue = 210; sat = 0.10; val = 0.80 }
  'giantcore'           = @{ src = 'item/heart_pottery_sherd';    hue = 356; sat = 0.66; val = 0.82 }
  'seaswim'             = @{ src = 'item/heart_of_the_sea';       hue = 215; sat = 0.66; val = 0.85 }
  'dashfoot'            = @{ src = 'item/rabbit_foot';            hue = 40;  sat = 0.22; val = 0.92 }
  'reflect'             = @{ src = 'entity/shield/base'; hue = 220; sat = 0.55; val = 0.80; overlay = 'entity/shield/border' }
  'magnet'              = @{ src = 'block/lodestone_top';         hue = 0;   sat = 0.35; val = 0.80 }
  'shrinkstar'          = @{ src = 'item/nether_star';            hue = 110; sat = 0.60; val = 0.88 }
  'lightning'           = @{ src = 'block/lightning_rod';         hue = 48;  sat = 0.72; val = 0.88 }
  'blink'               = @{ src = 'item/ender_eye';              hue = 285; sat = 0.60; val = 0.82 }
  'totem'               = @{ src = 'item/totem_of_undying';       hue = 45;  sat = 0.68; val = 0.88 }
  'wither'              = @{ src = 'item/skull_pottery_sherd';  hue = 130; sat = 0.16; val = 0.58 }
  'bell'                = @{ src = 'item/bell';                   hue = 46;  sat = 0.60; val = 0.86 }
  'dragonbreath'        = @{ src = 'item/dragon_breath';          hue = 290; sat = 0.62; val = 0.85 }
  'vision'              = @{ src = 'item/spyglass';               hue = 190; sat = 0.35; val = 0.82 }
  'time_clock'          = @{ src = 'item/clock_00';               hue = 44;  sat = 0.58; val = 0.86 }
  'antigravity_mace'    = @{ src = 'item/mace';                   hue = 268; sat = 0.50; val = 0.80 }
  'ancient_slash_armor' = @{ src = 'item/netherite_chestplate';   hue = 356; sat = 0.52; val = 0.72 }
  'hellfire_book'       = @{ src = 'item/enchanted_book';         hue = 6;   sat = 0.68; val = 0.80 }
  'endless_grimoire'    = @{ src = 'item/book';                   hue = 225; sat = 0.58; val = 0.70 }
  'ancient_anvil'       = @{ src = 'block/chipped_anvil_top';                 hue = 45;  sat = 0.28; val = 0.78 }
  'gaybolg'             = @{ src = 'item/trident';                hue = 356; sat = 0.62; val = 0.80 }
  'excalibur_replica'   = @{ src = 'item/diamond_sword';          hue = 48;  sat = 0.30; val = 0.96 }
  'infinite_bundle'     = @{ src = 'item/bundle';                 hue = 32;  sat = 0.55; val = 0.76 }
  'noahs_ark'           = @{ src = 'item/oak_boat';               hue = 30;  sat = 0.52; val = 0.80 }
  'artemis'             = @{ src = 'item/bow';                    hue = 45;  sat = 0.62; val = 0.86 }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)

function Get-Vanilla([string]$path) {
  $e = $zip.GetEntry("assets/minecraft/textures/$path.png")
  if (-not $e) { return $null }
  $ms = New-Object System.IO.MemoryStream
  $s = $e.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
  return [System.Drawing.Bitmap]::FromStream($ms)
}

# 16x16 に揃える。
#   crop 指定があればその領域、無ければ「不透明部分の外接矩形」を使う。
#   バニラの 64x64 テクスチャ（盾など）は絵が中央に小さく描かれているため、
#   外接矩形で切り出さないと 16x16 に縮めたとき余白まみれになる。
function Fit-16($src, $crop) {
  $dst = New-Object System.Drawing.Bitmap 16, 16
  $g = [System.Drawing.Graphics]::FromImage($dst)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

  $rect = $null
  if ($null -ne $crop) {
    $rect = New-Object System.Drawing.Rectangle $crop[0], $crop[1], $crop[2], $crop[3]
  } else {
    $minX = $src.Width; $minY = $src.Height; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $src.Height; $y++) {
      for ($x = 0; $x -lt $src.Width; $x++) {
        if ($src.GetPixel($x, $y).A -eq 0) { continue }
        if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
      }
    }
    if ($maxX -ge $minX -and $maxY -ge $minY) {
      # 縦横比を保つため正方形に広げる
      $w = $maxX - $minX + 1
      $h = $maxY - $minY + 1
      $side = [Math]::Max($w, $h)
      $cx = $minX + [int](($w - $side) / 2)
      $cy = $minY + [int](($h - $side) / 2)
      $cx = [Math]::Max(0, [Math]::Min($src.Width - $side, $cx))
      $cy = [Math]::Max(0, [Math]::Min($src.Height - $side, $cy))
      $rect = New-Object System.Drawing.Rectangle $cx, $cy, $side, $side
    } else {
      $rect = New-Object System.Drawing.Rectangle 0, 0, $src.Width, $src.Height
    }
  }
  $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, 16, 16), $rect, [System.Drawing.GraphicsUnit]::Pixel)
  $g.Dispose()
  return $dst
}

function Hsv-To-Rgb([double]$h, [double]$s, [double]$v) {
  $hh = ($h % 360) / 60.0
  $i = [Math]::Floor($hh); $f = $hh - $i
  $p = $v * (1 - $s); $q = $v * (1 - $s * $f); $t = $v * (1 - $s * (1 - $f))
  switch ([int]$i) {
    0 { $r = $v; $g = $t; $b = $p } 1 { $r = $q; $g = $v; $b = $p }
    2 { $r = $p; $g = $v; $b = $t } 3 { $r = $p; $g = $q; $b = $v }
    4 { $r = $t; $g = $p; $b = $v } default { $r = $v; $g = $p; $b = $q }
  }
  return @([int][Math]::Round($r * 255), [int][Math]::Round($g * 255), [int][Math]::Round($b * 255))
}

# 輝度保存の色置換。
#   バニラの明暗（L）を保ち、色相・彩度だけ対象色へ写す。
#   影は色相を +、ハイライトは - して Minecraft らしい色の転びを作る。
function Recolor($bmp, [double]$hueDeg, [double]$satTarget, [double]$valTarget) {
  # 不透明ピクセルの輝度レンジを測る（バニラの陰影をフルに使う）
  $lums = @()
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $lums += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
  } }
  if ($lums.Count -eq 0) { return $bmp }
  $sorted = @($lums | Sort-Object)
  $lo = $sorted[0]
  $hi = $sorted[$sorted.Count - 1]
  if ($hi - $lo -lt 0.05) { $lo = 0.0; $hi = 1.0 }

  $out = New-Object System.Drawing.Bitmap 16, 16
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y)
    if ($c.A -eq 0) { continue }
    $l = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
    $t = [Math]::Max(0.0, [Math]::Min(1.0, ($l - $lo) / ($hi - $lo)))

    # 影(t=0)は青紫へ、ハイライト(t=1)は黄へ寄せる
    $h = $hueDeg + (0.5 - $t) * 26.0
    if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
    $s = [Math]::Min(0.92, $satTarget * (1.05 - 0.30 * $t))
    $v = [Math]::Min(1.0, $valTarget * (0.58 + 0.62 * $t))

    $rgb = Hsv-To-Rgb $h $s $v
    $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2]))
  } }
  return $out
}

Write-Output ("Recolouring " + $map.Count + " vanilla-based relic textures -> $texOut")
$missing = @()
foreach ($id in $map.Keys) {
  $spec = $map[$id]
  $src = Get-Vanilla $spec.src
  if ($null -eq $src) { $missing += "$id ($($spec.src))"; continue }
  $crop = if ($spec.ContainsKey('crop')) { $spec.crop } else { $null }
  $fitted = Fit-16 $src $crop
  $src.Dispose()
  if ($spec.ContainsKey('overlay')) {
    $over = Get-Vanilla $spec.overlay
    if ($null -ne $over) {
      $overFit = Fit-16 $over $null
      $over.Dispose()
      $g = [System.Drawing.Graphics]::FromImage($fitted)
      $g.DrawImage($overFit, 0, 0, 16, 16)
      $g.Dispose()
      $overFit.Dispose()
    }
  }
  $result = Recolor $fitted $spec.hue $spec.sat $spec.val
  $fitted.Dispose()
  $result.Save((Join-Path $texOut "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $result.Dispose()
  Write-Output "  -> $id.png"
}
$zip.Dispose()

# 使わなくなった PNG を消す
$keep = @($map.Keys)
Get-ChildItem $texOut -Filter '*.png' | ForEach-Object {
  if ($keep -notcontains $_.BaseName) { Remove-Item $_.FullName -Force }
}

Write-Output ""
Write-Output ("DONE: " + ($map.Count - $missing.Count) + " textures")
if ($missing.Count -gt 0) { Write-Output ("MISSING: " + ($missing -join '; ')); exit 1 }



