# MifronPlugin レリック テクスチャ生成（バニラ改変 + ドット絵規範の適用）
#
# 設計方針（~/research/minecraft-texture-art/ の調査に基づく）:
#   1. バニラのテクスチャを土台にする（Mojang の描き込みを活かす）
#   2. 色相シフトで「別の物」にする（明度だけ変えない。影は青紫、光は黄へ）
#   3. 素材ごとに 3 段のランプ（base / shadow / highlight）に量子化する
#   4. 光源は左上に固定し、ハイライトは左上辺だけに置く（希少性で光を語る）
#   5. アウトラインは純黒でなく、局所色を暗くした色。光の当たる左上辺は外す
#   6. 1 ピクセルずつ散らさない。塊として置く（ノイズは汚れに見える）
#
# 使い方:
#   pwsh -File gen-relic-textures-vanilla.ps1 [-JarPath <1.21.4.jar>] [-OutDir <dir>]

param(
  [string]$JarPath = "$env:APPDATA\.minecraft\versions\1.21.4\1.21.4.jar",
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $JarPath)) { throw "vanilla jar not found: $JarPath" }

$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'
if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }

# レリック id -> バニラのテクスチャパス
$sources = [ordered]@{
  'hookshot'      = 'assets/minecraft/textures/item/fishing_rod.png'
  'enchbook'      = 'assets/minecraft/textures/item/enchanted_book.png'
  'jetpack'       = 'assets/minecraft/textures/item/chainmail_chestplate.png'
  'skates'        = 'assets/minecraft/textures/item/iron_boots.png'
  'resonance'     = 'assets/minecraft/textures/item/echo_shard.png'
  'fireball'      = 'assets/minecraft/textures/item/fire_charge.png'
  'decayed_sword' = 'assets/minecraft/textures/item/netherite_sword.png'
  'enchgrind'     = 'assets/minecraft/textures/block/grindstone_side.png'
  'chain'         = 'assets/minecraft/textures/item/chain.png'
  'giantcore'     = 'assets/minecraft/textures/item/heart_of_the_sea.png'
  'seaswim'       = 'assets/minecraft/textures/item/heart_of_the_sea.png'
  'dashfoot'      = 'assets/minecraft/textures/item/rabbit_foot.png'
  'reflect'       = 'assets/minecraft/textures/item/totem_of_undying.png'
  'nightclock'    = 'assets/minecraft/textures/item/clock_00.png'
  'magnet'        = 'assets/minecraft/textures/block/lodestone_top.png'
  'shrinkstar'    = 'assets/minecraft/textures/item/nether_star.png'
  'lightning'     = 'assets/minecraft/textures/block/lightning_rod.png'
  'blink'         = 'assets/minecraft/textures/item/ender_eye.png'
  'totem'         = 'assets/minecraft/textures/item/totem_of_undying.png'
  'wither'        = 'assets/minecraft/textures/item/coal.png'
  'bell'          = 'assets/minecraft/textures/item/bell.png'
  'dragonbreath'  = 'assets/minecraft/textures/item/dragon_breath.png'
  'vision'        = 'assets/minecraft/textures/item/spyglass.png'
}

# 各レリックの色設計: 色相シフト量 / 彩度倍率 / 明度倍率 / アクセント色
# アクセントは「その物体を特定する特徴」を 1 箇所だけ強調するために使う
$design = @{
  'hookshot'      = @{ hue = -12; sat = 1.25; val = 1.05; accent = @(236, 200, 90);  accentAt = 'hook' }
  'enchbook'      = @{ hue = 0;   sat = 1.20; val = 1.02; accent = @(210, 150, 255); accentAt = 'top-left' }
  'jetpack'       = @{ hue = -18; sat = 1.15; val = 1.00; accent = @(90, 220, 220);  accentAt = 'center' }
  'skates'        = @{ hue = -20; sat = 1.10; val = 1.05; accent = @(120, 235, 240); accentAt = 'bottom' }
  'resonance'     = @{ hue = -8;  sat = 1.35; val = 1.10; accent = @(170, 250, 250); accentAt = 'top-left' }
  'fireball'      = @{ hue = 0;   sat = 1.20; val = 1.08; accent = @(255, 245, 180); accentAt = 'top-left' }
  'decayed_sword' = @{ hue = 0;   sat = 0.40; val = 0.88; accent = @(120, 150, 110); accentAt = 'center' }
  'enchgrind'     = @{ hue = 40;  sat = 1.20; val = 1.00; accent = @(200, 140, 255); accentAt = 'center' }
  'chain'         = @{ hue = 0;   sat = 0.55; val = 0.95; accent = @(200, 205, 215); accentAt = 'top-left' }
  'giantcore'     = @{ hue = 140; sat = 1.35; val = 0.95; accent = @(255, 120, 110); accentAt = 'top-left' }
  'seaswim'       = @{ hue = -6;  sat = 1.25; val = 1.06; accent = @(180, 240, 255); accentAt = 'top-left' }
  'dashfoot'      = @{ hue = 0;   sat = 1.10; val = 1.05; accent = @(120, 235, 240); accentAt = 'right' }
  'reflect'       = @{ hue = -25; sat = 1.20; val = 1.00; accent = @(150, 220, 255); accentAt = 'top-left' }
  'nightclock'    = @{ hue = 0;   sat = 1.25; val = 0.92; accent = @(120, 235, 240); accentAt = 'center' }
  'magnet'        = @{ hue = 0;   sat = 1.15; val = 1.00; accent = @(240, 90, 70);   accentAt = 'center' }
  'shrinkstar'    = @{ hue = -55; sat = 1.15; val = 1.00; accent = @(170, 245, 170); accentAt = 'top-left' }
  'lightning'     = @{ hue = -25; sat = 1.20; val = 1.05; accent = @(255, 245, 140); accentAt = 'top-left' }
  'blink'         = @{ hue = 10;  sat = 1.25; val = 1.05; accent = @(200, 150, 255); accentAt = 'top-left' }
  'totem'         = @{ hue = 0;   sat = 1.10; val = 1.02; accent = @(255, 230, 120); accentAt = 'top-left' }
  'wither'        = @{ hue = 0;   sat = 0.35; val = 0.72; accent = @(120, 240, 120); accentAt = 'eyes' }
  'bell'          = @{ hue = 0;   sat = 1.12; val = 1.04; accent = @(190, 250, 190); accentAt = 'top-left' }
  'dragonbreath'  = @{ hue = 0;   sat = 1.30; val = 1.05; accent = @(220, 170, 255); accentAt = 'top-left' }
  'vision'        = @{ hue = 0;   sat = 1.20; val = 1.00; accent = @(130, 245, 245); accentAt = 'lenses' }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)

function Get-VanillaBitmap([string]$entryPath) {
  $e = $zip.GetEntry($entryPath)
  if (-not $e) { return $null }
  $ms = New-Object System.IO.MemoryStream
  $s = $e.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
  return [System.Drawing.Bitmap]::FromStream($ms)
}

# --- 色ヘルパ ---------------------------------------------------------------

function Hsv-To-Rgb([double]$h, [double]$s, [double]$v) {
  $hh = ($h % 360) / 60.0
  $i = [Math]::Floor($hh); $f = $hh - $i
  $p = $v * (1 - $s); $q = $v * (1 - $s * $f); $t = $v * (1 - $s * (1 - $f))
  switch ([int]$i) {
    0 { $r = $v; $g = $t; $b = $p }
    1 { $r = $q; $g = $v; $b = $p }
    2 { $r = $p; $g = $v; $b = $t }
    3 { $r = $p; $g = $q; $b = $v }
    4 { $r = $t; $g = $p; $b = $v }
    default { $r = $v; $g = $p; $b = $q }
  }
  return @([int][Math]::Round($r * 255), [int][Math]::Round($g * 255), [int][Math]::Round($b * 255))
}

# 明度を v 倍し、同時に色相をシフトする（影は青紫へ、光は黄へ寄せる）
function Shift-Lightness($color, [double]$valMul, [double]$hueDeg) {
  $h = $color.GetHue() + $hueDeg
  if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
  $s = $color.GetSaturation()
  $v = [Math]::Min(1.0, $color.GetBrightness() * $valMul)
  $rgb = Hsv-To-Rgb $h $s $v
  return [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
}

# --- 画像ヘルパ -------------------------------------------------------------

function New-Canvas([int]$w, [int]$h) { return New-Object System.Drawing.Bitmap $w, $h }

function Fit-16($src) {
  $dst = New-Canvas 16 16
  $g = [System.Drawing.Graphics]::FromImage($dst)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
  if ($src.Width -eq 16 -and $src.Height -eq 16) {
    $g.DrawImage($src, 0, 0, 16, 16)
  } else {
    $cw = [Math]::Min(16, $src.Width); $ch = [Math]::Min(16, $src.Height)
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, 16, 16), (New-Object System.Drawing.Rectangle 0, 0, $cw, $ch), [System.Drawing.GraphicsUnit]::Pixel)
  }
  $g.Dispose()
  return $dst
}

# 色相・彩度を寄せる（明度は後段のランプ量子化で扱う）
function Shift-Tone($bmp, [double]$hueDeg, [double]$satMul) {
  $out = New-Canvas 16 16
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $c = $bmp.GetPixel($x, $y)
      if ($c.A -eq 0) { continue }
      $h = $c.GetHue() + $hueDeg
      if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
      $s = [Math]::Min(1.0, $c.GetSaturation() * $satMul)
      $rgb = Hsv-To-Rgb $h $s $c.GetBrightness()
      $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2]))
    }
  }
  return $out
}

# 3 段ランプに量子化する（base / shadow / highlight）。
# 明度で並べ直し、下位 1/3 を影、上位 1/6 をハイライト、残りを中間色にする。
function Quantize-Ramp($bmp, [double]$valMul) {
  $out = New-Canvas 16 16
  $vals = @()
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $c = $bmp.GetPixel($x, $y)
      if ($c.A -ne 0) { $vals += $c.GetBrightness() }
    }
  }
  if ($vals.Count -eq 0) { return $out }
  $sorted = $vals | Sort-Object
  $lo = $sorted[[int]($sorted.Count * 0.34)]
  $hi = $sorted[[int]($sorted.Count * 0.84)]

  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      $c = $bmp.GetPixel($x, $y)
      if ($c.A -eq 0) { continue }
      $b = $c.GetBrightness()
      if ($b -le $lo) {
        # 影: 青紫へ寄せる
        $col = Shift-Lightness $c 0.72 18
      } elseif ($b -ge $hi) {
        # ハイライト: 黄へ寄せる
        $col = Shift-Lightness $c 1.30 -14
      } else {
        $col = Shift-Lightness $c $valMul 0
      }
      $out.SetPixel($x, $y, $col)
    }
  }
  return $out
}

# 左上光源に合わせ、左上辺の「ごく一部」だけにハイライトを置く。
# ハイライトは希少さで効くので、置く数を抑える（1〜3ピクセル程度）。
function Apply-TopLeftLight($bmp) {
  $out = New-Canvas 16 16
  $g = [System.Drawing.Graphics]::FromImage($out)
  $g.DrawImage($bmp, 0, 0, 16, 16)
  $g.Dispose()

  # 左上の角に最も近い不透明ピクセルを見つけ、そこから 2 ピクセルだけ明るくする
  $anchor = $null
  $best = 999
  for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
      if ($bmp.GetPixel($x, $y).A -eq 0) { continue }
      $up = if ($y -gt 0) { $bmp.GetPixel($x, $y - 1) } else { $null }
      $left = if ($x -gt 0) { $bmp.GetPixel($x - 1, $y) } else { $null }
      $upEmpty = ($null -eq $up -or $up.A -eq 0)
      $leftEmpty = ($null -eq $left -or $left.A -eq 0)
      if (-not ($upEmpty -or $leftEmpty)) { continue }
      $dist = $x + $y
      if ($dist -lt $best) { $best = $dist; $anchor = @($x, $y) }
    }
  }
  if ($null -eq $anchor) { return $out }

  [int]$hx = $anchor[0]
  [int]$hy = $anchor[1]
  $spots = New-Object System.Collections.ArrayList
  [void]$spots.Add(@($hx, $hy))
  # 右か下へ 1 ピクセル伸ばす（塊として読ませる）
  [int]$rx = $hx + 1
  [int]$dy = $hy + 1
  if ($rx -lt 16 -and $bmp.GetPixel($rx, $hy).A -ne 0) { [void]$spots.Add(@($rx, $hy)) }
  elseif ($dy -lt 16 -and $bmp.GetPixel($hx, $dy).A -ne 0) { [void]$spots.Add(@($hx, $dy)) }

  foreach ($s in $spots) {
    $c = $bmp.GetPixel($s[0], $s[1])
    $out.SetPixel($s[0], $s[1], (Shift-Lightness $c 1.55 -20))
  }
  return $out
}

# アウトライン: 純黒ではなく局所色を暗くした色。光の当たる左上辺は外す。
function Apply-Outline($bmp) {
  $w = 16; $h = 16
  # まず元の不透明マスクを取る
  $mask = New-Object 'bool[,]' 16, 16
  for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) { $mask[$x, $y] = ($bmp.GetPixel($x, $y).A -ne 0) } }

  $out = New-Canvas $w $h
  $g = [System.Drawing.Graphics]::FromImage($out)
  $g.DrawImage($bmp, 0, 0, $w, $h)
  $g.Dispose()

  for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
      if ($mask[$x, $y]) { continue }
      $nearColor = $null
      $fromTopLeft = $false
      foreach ($o in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
        $nx = $x + $o[0]; $ny = $y + $o[1]
        if ($nx -ge 0 -and $nx -lt $w -and $ny -ge 0 -and $ny -lt $h -and $mask[$nx, $ny]) {
          $nearColor = $bmp.GetPixel($nx, $ny)
          # 光源は左上。上か左に本体がある（＝下辺・右辺）なら線を残す
          if ($o[1] -eq -1 -or $o[0] -eq -1) { $fromTopLeft = $true }
          break
        }
      }
      if ($null -eq $nearColor) { continue }
      if ($fromTopLeft) { continue }  # 光の当たる側はアウトラインを外す
      $out.SetPixel($x, $y, (Shift-Lightness $nearColor 0.42 12))
    }
  }
  return $out
}

# アクセント: その物体を特定する特徴を「1〜3ピクセルだけ」強調する。
# 大きい塊で置くと貼り付けた四角に見えるので、点で置く。
function Apply-Accent($bmp, $rgb, [string]$where) {
  $col = [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2])
  $out = New-Canvas 16 16
  $g = [System.Drawing.Graphics]::FromImage($out)
  $g.DrawImage($bmp, 0, 0, 16, 16)
  $g.Dispose()

  $targets = @()
  switch ($where) {
    'top-left' {
      # 左上寄りの最初の不透明ピクセル 1 点だけ
      for ($y = 3; $y -lt 10; $y++) {
        for ($x = 3; $x -lt 10; $x++) {
          if ($bmp.GetPixel($x, $y).A -ne 0) { $targets += ,@($x, $y); break }
        }
        if ($targets.Count -ge 1) { break }
      }
    }
    'center' {
      for ($y = 7; $y -le 9; $y++) {
        for ($x = 7; $x -le 9; $x++) {
          if ($bmp.GetPixel($x, $y).A -ne 0) { $targets += ,@($x, $y); break }
        }
        if ($targets.Count -ge 1) { break }
      }
    }
    'bottom' {
      for ($y = 13; $y -ge 9; $y--) {
        for ($x = 3; $x -lt 13; $x++) {
          if ($bmp.GetPixel($x, $y).A -ne 0) { $targets += ,@($x, $y); break }
        }
        if ($targets.Count -ge 2) { break }
      }
    }
    'right' {
      for ($x = 13; $x -ge 9; $x--) {
        for ($y = 4; $y -lt 12; $y++) {
          if ($bmp.GetPixel($x, $y).A -ne 0) { $targets += ,@($x, $y); break }
        }
        if ($targets.Count -ge 2) { break }
      }
    }
    'eyes' {
      $targets = @(@(5, 6), @(10, 6))
    }
    'lenses' {
      $targets = @(@(4, 7), @(11, 7))
    }
    'hook' {
      $targets = @(@(11, 3), @(12, 3))
    }
  }

  foreach ($t in $targets) {
    if ($t[0] -ge 0 -and $t[0] -lt 16 -and $t[1] -ge 0 -and $t[1] -lt 16 -and $bmp.GetPixel($t[0], $t[1]).A -ne 0) {
      $out.SetPixel($t[0], $t[1], $col)
    }
  }
  return $out
}

function Save-Relic($bmp, [string]$id) {
  $path = Join-Path $texOut "$id.png"
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output "  -> $id.png"
}

# --- 生成 -------------------------------------------------------------------

Write-Output "Generating relic textures (vanilla base + pixel-art rules) -> $texOut"
$missing = @()
$made = @()

foreach ($id in $sources.Keys) {
  $vanilla = Get-VanillaBitmap $sources[$id]
  if (-not $vanilla) { $missing += "$id ($($sources[$id]))"; continue }

  $base = Fit-16 $vanilla
  $vanilla.Dispose()

  $d = $design[$id]
  # 1) 色相・彩度を寄せる
  $img = Shift-Tone $base $d.hue $d.sat
  $base.Dispose()
  # 2) 3 段ランプに量子化（影は青紫、光は黄へ）
  $img2 = Quantize-Ramp $img $d.val
  $img.Dispose()
  # 3) 左上光源のハイライト
  $img3 = Apply-TopLeftLight $img2
  $img2.Dispose()
  # 4) 局所色のアウトライン（光の当たる側は外す）
  $img4 = Apply-Outline $img3
  $img3.Dispose()
  # 5) 特徴のアクセント
  $img5 = Apply-Accent $img4 $d.accent $d.accentAt
  $img4.Dispose()

  Save-Relic $img5 $id
  $made += $id
}

$zip.Dispose()

Write-Output ""
Write-Output ("DONE: " + $made.Count + " textures")
if ($missing.Count -gt 0) {
  Write-Output ("MISSING in jar: " + ($missing -join '; '))
  exit 1
}
