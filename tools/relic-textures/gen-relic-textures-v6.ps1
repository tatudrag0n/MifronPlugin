# レリックテクスチャ生成 v6
#
# v5 までは素材ごとに 3 色（base / shadow / highlight）のベタ塗りだったため、
# Minecraft 特有の「色のグラデーション」が無く、のっぺりして見えた。
#
# v6 では手設計したシルエット（どのピクセルがどの素材か）を保ったまま、
# 次の 2 段を追加する:
#   1. 素材ごとに 5 段のランプを HSV で生成する
#      （影は青紫へ、ハイライトは黄へ色相をずらす = Minecraft の陰影の作り方）
#   2. 各ピクセルの段を「左上光源からの位置」と「輪郭からの距離」で選ぶ
#      → 大きな面ほど滑らかに明るさが変わり、段差がグラデーションとして読める
#
# ノイズは撒かない（散らした 1px は汚れに見えるため）。

param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics'),
  [int]$RampSteps = 5
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

. (Join-Path $PSScriptRoot 'relic-sprite-lib.ps1')
. (Join-Path $PSScriptRoot 'sprites-part1.ps1')
. (Join-Path $PSScriptRoot 'sprites-part2.ps1')

$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'
if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }

# --- 文字 -> 素材 と 段の基準 -------------------------------------------------
# 大文字小文字を区別するため序数比較の Hashtable を使う。
$MaterialOf = New-Object System.Collections.Hashtable ([System.StringComparer]::Ordinal)
$ToneOf = New-Object System.Collections.Hashtable ([System.StringComparer]::Ordinal)

function Add-Mat([string]$chars, [string]$mat, [int[]]$tones) {
  for ($i = 0; $i -lt $chars.Length; $i++) {
    $ch = [string]$chars[$i]
    $script:MaterialOf[$ch] = $mat
    $script:ToneOf[$ch] = $tones[$i]
  }
}

Add-Mat 'wWe' 'wood'    @(2, 1, 3)
Add-Mat 'iIE' 'iron'    @(2, 1, 3)
Add-Mat 'gGH' 'gold'    @(2, 1, 3)
Add-Mat 'rRT' 'red'     @(2, 1, 3)
Add-Mat 'bBN' 'blue'    @(2, 1, 3)
Add-Mat 'cCM' 'cyan'    @(2, 1, 3)
Add-Mat 'pPQ' 'purple'  @(2, 1, 3)
Add-Mat 'vVx' 'green'   @(2, 1, 3)
Add-Mat 'oOy' 'orange'  @(2, 1, 3)
Add-Mat 'sSz' 'stone'   @(2, 1, 3)
Add-Mat 'dDf' 'bone'    @(2, 1, 3)
Add-Mat 'lLm' 'leather' @(2, 1, 3)

# 各素材の基準色（段 2 = 中間色）
$BaseColor = @{
  wood = @(150, 106, 58); iron = @(196, 202, 214); gold = @(236, 190, 62)
  red = @(186, 50, 44);   blue = @(92, 146, 236);  cyan = @(96, 222, 226)
  purple = @(138, 78, 188); green = @(104, 190, 90); orange = @(240, 148, 44)
  stone = @(150, 150, 158); bone = @(232, 226, 204); leather = @(122, 82, 48)
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

# 1 素材ぶんの 5 段ランプを作る。
#   影側: 明度を落としつつ色相を青紫へ（+）、彩度を少し上げる
#   光側: 明度を上げつつ色相を黄へ（-）、彩度を少し落とす
function New-Ramp([int[]]$base, [int]$steps = 5) {
  $c = [System.Drawing.Color]::FromArgb(255, $base[0], $base[1], $base[2])
  # Minecraft のパレットは彩度が控えめ。基準彩度を落としてからランプを作る。
  $h0 = $c.GetHue()
  $s0 = [Math]::Min(0.78, $c.GetSaturation() * 0.88)
  $v0 = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)) / 255.0
  $ramp = @()
  $mid = [Math]::Floor($steps / 2)
  for ($i = 0; $i -lt $steps; $i++) {
    $k = $i - $mid                    # -2..+2
    if ($k -lt 0) {
      $v = $v0 * (1.0 + 0.17 * $k)    # 影: 0.66, 0.83 倍
      $s = [Math]::Min(1.0, $s0 * (1.0 - 0.05 * $k))
      $h = $h0 + (14.0 * -$k)         # 青紫へ
    } elseif ($k -gt 0) {
      $v = [Math]::Min(1.0, $v0 * (1.0 + 0.07 * $k))
      $s = [Math]::Max(0.0, $s0 * (1.0 - 0.09 * $k))
      $h = $h0 - (8.0 * $k)          # 黄へ
    } else {
      $v = $v0; $s = $s0; $h = $h0
    }
    if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
    $rgb = Hsv-To-Rgb $h $s $v
    $ramp += ,@($rgb[0], $rgb[1], $rgb[2])
  }
  return ,$ramp
}

$Ramps = @{}
foreach ($mat in $BaseColor.Keys) { $Ramps[$mat] = New-Ramp $BaseColor[$mat] $RampSteps }
$OutlineColor = [System.Drawing.Color]::FromArgb(255, 40, 36, 46)

function Clamp-Tone([int]$t) {
  if ($t -lt 0) { return 0 }
  if ($t -ge $script:RampSteps) { return $script:RampSteps - 1 }
  return $t
}

# 手設計グリッドにグラデーションを乗せる
function Render-Sprite([string[]]$rows) {
  $chars = New-Object 'string[,]' 16, 16
  $solid = New-Object 'bool[,]' 16, 16
  for ($y = 0; $y -lt 16; $y++) {
    $row = if ($y -lt $rows.Count) { [string]$rows[$y] } else { '' }
    if ($row.Length -lt 16) { $row = $row.PadRight(16, '.') }
    for ($x = 0; $x -lt 16; $x++) {
      $ch = [string]$row[$x]
      $chars[$x, $y] = $ch
      $solid[$x, $y] = ($ch -ne '.')
    }
  }

  # シルエットの外接矩形（光の勾配の基準）
  $minX = 15; $maxX = 0; $minY = 15; $maxY = 0
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if (-not $solid[$x, $y]) { continue }
    if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
    if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
  } }
  if ($maxX -lt $minX) { return (New-Object System.Drawing.Bitmap 16, 16) }

  $spanX = [Math]::Max(1, $maxX - $minX)
  $spanY = [Math]::Max(1, $maxY - $minY)

  $bmp = New-Object System.Drawing.Bitmap 16, 16
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if (-not $solid[$x, $y]) { continue }
    $ch = $chars[$x, $y]
    if ($ch -eq 'k') { $bmp.SetPixel($x, $y, $OutlineColor); continue }
    if (-not $script:MaterialOf.ContainsKey($ch)) { continue }

    $mat = $script:MaterialOf[$ch]
    $anchor = [int]$script:ToneOf[$ch]

    # 左上光源: 左上ほど明るく、右下ほど暗い
    $nx = ($x - $minX) / [double]$spanX
    $ny = ($y - $minY) / [double]$spanY
    $light = 1.0 - (0.55 * $nx + 0.45 * $ny)     # 1.0 (左上) .. 0.0 (右下)

    # 輪郭に近いほど段を振る（光側は +、影側は -）
    $openUp = ($y -eq 0) -or (-not $solid[$x, ($y - 1)])
    $openLeft = ($x -eq 0) -or (-not $solid[($x - 1), $y])
    $openDown = ($y -eq 15) -or (-not $solid[$x, ($y + 1)])
    $openRight = ($x -eq 15) -or (-not $solid[($x + 1), $y])
    $edge = 0.0
    if ($openUp) { $edge += 0.30 }
    if ($openLeft) { $edge += 0.30 }
    if ($openDown) { $edge -= 0.22 }
    if ($openRight) { $edge -= 0.22 }

    # 段の増減: 位置と輪郭から決める（大きな面ほど滑らかに変化する）
    $shift = [Math]::Round((($light - 0.55) * 1.6 + $edge) * 1.0)
    $tone = Clamp-Tone ($anchor + [int]$shift)

    $rgb = $script:Ramps[$mat][$tone]
    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
  } }

  # 全体の明度をバニラ水準（約52%）に揃える。明るい側に寄りすぎた色も落ち着く。
  $sum = 0.0; $n = 0
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if (-not $solid[$x, $y]) { continue }
    $c = $bmp.GetPixel($x, $y)
    $sum += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0; $n++
  } }
  if ($n -gt 0 -and $sum -gt 0.01) {
    $avg = $sum / $n
    [double]$mul = [Math]::Max(0.85, [Math]::Min(1.15, 0.48 / $avg))
    if ([Math]::Abs($mul - 1.0) -gt 0.01) {
      for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
        if (-not $solid[$x, $y]) { continue }
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -eq 40 -and $c.G -eq 36 -and $c.B -eq 46) { continue }
        $r = [Math]::Min(255, [int][Math]::Round($c.R * $mul))
        $g = [Math]::Min(255, [int][Math]::Round($c.G * $mul))
        $b = [Math]::Min(255, [int][Math]::Round($c.B * $mul))
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
      } }
    }
  }
  return $bmp
}

$all = @{}
foreach ($k in $Sprites1.Keys) { $all[$k] = $Sprites1[$k] }
foreach ($k in $Sprites2.Keys) { $all[$k] = $Sprites2[$k] }

Write-Output ("Rendering " + $all.Count + " sprites with " + $RampSteps + "-step gradients -> $texOut")

$keep = @($all.Keys)
Get-ChildItem $texOut -Filter '*.png' -ErrorAction SilentlyContinue | ForEach-Object {
  if ($keep -notcontains $_.BaseName) { Remove-Item $_.FullName -Force }
}

foreach ($id in ($all.Keys | Sort-Object)) {
  $bmp = Render-Sprite $all[$id]
  $bmp.Save((Join-Path $texOut "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output "  -> $id.png"
}
Write-Output ("DONE: " + $all.Count + " textures")


