# MifronPlugin レリック テクスチャ生成 v3
#
# 設計方針:
#   A. 名前と見た目を一致させる（最優先）
#      バニラ素材の流用では「凋零の頭骨」が石炭、「巨人の心臓」が球になってしまう。
#      そこで各レリックの「名前が示す形」を 16x16 のピクセルマップで明示的に定義する。
#   B. バニラの画法規範を適用する（~/research/minecraft-texture-art/ の調査より）
#      - シルエット優先、誇張が正義
#      - 素材ごとに 3 段のランプ（base / shadow / highlight）
#      - 影は青紫へ、ハイライトは黄へ色相をずらす
#      - 光源は左上に固定、ハイライトは希少（1〜3ピクセル）
#      - アウトラインは局所色の暗色。光の当たる左上辺は外す
#      - 影は塊で置く（散らさない）
#   C. 明るさをバニラ水準に保つ
#      バニラの代表アイテムは平均明度 40〜60%。生成後も 45〜60% に収める。
#
# 使い方:
#   pwsh -File gen-relic-textures-v3.ps1 [-OutDir <dir>]

param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'
if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }

# ---------------------------------------------------------------------------
# 色ヘルパ
# ---------------------------------------------------------------------------
function C([int]$r, [int]$g, [int]$b) { return [System.Drawing.Color]::FromArgb(255, $r, $g, $b) }

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

# 明度を v 倍し、色相もずらす（影は青紫へ +、ハイライトは黄へ -）
# 注意: Color.GetBrightness() は HSV の V ではなく (max+min)/2 を返す。
# HSV の V = max(R,G,B)/255 を使わないと、倍率が正しく効かない。
function Tone($color, [double]$valMul, [double]$hueDeg) {
  $h = $color.GetHue() + $hueDeg
  if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
  $s = [Math]::Min(1.0, $color.GetSaturation() * 1.0)
  [double]$v = [Math]::Max($color.R, [Math]::Max($color.G, $color.B)) / 255.0
  $v = [Math]::Max(0.0, [Math]::Min(1.0, $v * $valMul))
  $rgb = Hsv-To-Rgb $h $s $v
  return [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
}

# 知覚的な明るさ（0..1）。テクスチャ全体の明度評価に使う。
function Luma($color) {
  return (0.299 * $color.R + 0.587 * $color.G + 0.114 * $color.B) / 255.0
}

function New-Canvas { return New-Object System.Drawing.Bitmap 16, 16 }

# ---------------------------------------------------------------------------
# 描画ヘルパ
# ---------------------------------------------------------------------------
function Px($bmp, [int]$x, [int]$y, $color) {
  if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $bmp.SetPixel($x, $y, $color) }
}

function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $color) {
  for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Px $bmp $x $y $color } }
}

function Draw-Line($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $color) {
  $dx = [Math]::Abs($x1 - $x0); $dy = -[Math]::Abs($y1 - $y0)
  $sx = if ($x0 -lt $x1) { 1 } else { -1 }
  $sy = if ($y0 -lt $y1) { 1 } else { -1 }
  $err = $dx + $dy
  while ($true) {
    Px $bmp $x0 $y0 $color
    if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
    $e2 = 2 * $err
    if ($e2 -ge $dy) { $err += $dy; $x0 += $sx }
    if ($e2 -le $dx) { $err += $dx; $y0 += $sy }
  }
}

function Draw-Disc($bmp, [double]$cx, [double]$cy, [double]$r, $color) {
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $d = [Math]::Sqrt([Math]::Pow($x + 0.5 - $cx, 2) + [Math]::Pow($y + 0.5 - $cy, 2))
    if ($d -le $r) { Px $bmp $x $y $color }
  } }
}

# 文字列パターン（'.' = 透明、他は palette のキー）
function Pattern($bmp, [string[]]$rows, [hashtable]$palette) {
  for ($y = 0; $y -lt $rows.Count; $y++) {
    $row = $rows[$y]
    for ($x = 0; $x -lt $row.Length -and $x -lt 16; $x++) {
      $ch = [string]$row[$x]
      if ($ch -ne '.' -and $palette.ContainsKey($ch)) { Px $bmp $x $y $palette[$ch] }
    }
  }
}

# ---------------------------------------------------------------------------
# 仕上げ（バニラ規範の適用）
# ---------------------------------------------------------------------------

# 3段ランプに量子化: 明度の下位 30% を影、上位 15% をハイライト、残りを中間色。
# 影は青紫へ、ハイライトは黄へ色相をずらす。全体の明度は落とさない。
function Apply-Ramp($bmp) {
  $out = New-Canvas
  $vals = @()
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -ne 0) { $vals += $c.GetBrightness() }
  } }
  if ($vals.Count -eq 0) { return $out }
  $sorted = @($vals | Sort-Object)
  $lo = $sorted[[int][Math]::Floor($sorted.Count * 0.30)]
  $hi = $sorted[[int][Math]::Floor($sorted.Count * 0.85)]

  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $b = $c.GetBrightness()
    if ($b -le $lo) { $col = Tone $c 0.92 16 }        # 影: わずかに暗く、青紫へ
    elseif ($b -ge $hi) { $col = Tone $c 1.22 -12 }   # ハイライト: 明るく、黄へ
    else { $col = Tone $c 1.10 0 }                    # 中間: 持ち上げる
    $out.SetPixel($x, $y, $col)
  } }
  return $out
}

# 左上光源のハイライトを 1〜3 ピクセルだけ。希少さで光を語る。
function Apply-KeyLight($bmp) {
  $out = New-Canvas
  $g = [System.Drawing.Graphics]::FromImage($out); $g.DrawImage($bmp, 0, 0, 16, 16); $g.Dispose()

  # 左上の角に最も近い「上か左が空いている」ピクセルを探す
  $anchor = $null; $best = 999
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if ($bmp.GetPixel($x, $y).A -eq 0) { continue }
    $up = if ($y -gt 0) { $bmp.GetPixel($x, $y - 1) } else { $null }
    $left = if ($x -gt 0) { $bmp.GetPixel($x - 1, $y) } else { $null }
    if (($null -eq $up -or $up.A -eq 0) -or ($null -eq $left -or $left.A -eq 0)) {
      $dist = $x + $y
      if ($dist -lt $best) { $best = $dist; $anchor = @($x, $y) }
    }
  } }
  if ($null -eq $anchor) { return $out }

  [int]$hx = $anchor[0]; [int]$hy = $anchor[1]
  $spots = New-Object System.Collections.ArrayList
  [void]$spots.Add(@($hx, $hy))
  [int]$rx = $hx + 1; [int]$dy = $hy + 1
  if ($rx -lt 16 -and $bmp.GetPixel($rx, $hy).A -ne 0) { [void]$spots.Add(@($rx, $hy)) }
  elseif ($dy -lt 16 -and $bmp.GetPixel($hx, $dy).A -ne 0) { [void]$spots.Add(@($hx, $dy)) }

  foreach ($s in $spots) {
    $c = $bmp.GetPixel($s[0], $s[1])
    $out.SetPixel($s[0], $s[1], (Tone $c 1.42 -18))
  }
  return $out
}

# アウトライン: 局所色を暗くした色。光の当たる左上辺は外す。
function Apply-Outline($bmp) {
  $mask = New-Object 'bool[,]' 16, 16
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) { $mask[$x, $y] = ($bmp.GetPixel($x, $y).A -ne 0) } }

  $out = New-Canvas
  $g = [System.Drawing.Graphics]::FromImage($out); $g.DrawImage($bmp, 0, 0, 16, 16); $g.Dispose()

  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if ($mask[$x, $y]) { continue }
    $near = $null; $lightSide = $false
    foreach ($o in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
      $nx = $x + $o[0]; $ny = $y + $o[1]
      if ($nx -ge 0 -and $nx -lt 16 -and $ny -ge 0 -and $ny -lt 16 -and $mask[$nx, $ny]) {
        $near = $bmp.GetPixel($nx, $ny)
        # 上(-1)か左(-1)に本体がある＝こちらは下辺・右辺＝影側なので線を残す
        if ($o[1] -eq -1 -or $o[0] -eq -1) { $lightSide = $true }
        break
      }
    }
    if ($null -eq $near) { continue }
    if ($lightSide) { continue }
    $out.SetPixel($x, $y, (Tone $near 0.55 14))
  } }
  return $out
}

# 明度をバニラ水準に正規化する（知覚輝度ベース）。
# 明度を上げると彩度が跳ねて蛍光色になるので、上げた分だけ彩度を抑える。
function Normalize-Brightness($bmp, [double]$target = 0.56) {
  $sum = 0.0; $n = 0
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -ne 0) { $sum += (Luma $c); $n++ }
  } }
  if ($n -eq 0) { return $bmp }
  $avg = $sum / $n
  if ($avg -le 0.01) { return $bmp }
  [double]$mul = $target / $avg
  $mul = [Math]::Max(0.6, [Math]::Min(3.5, $mul))
  # 明度を上げるほど彩度を下げる（蛍光色の防止）
  [double]$satMul = if ($mul -gt 1.0) { 1.0 / [Math]::Pow($mul, 0.55) } else { 1.0 }

  $out = New-Canvas
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $t = Tone $c $mul 0
    if ($satMul -lt 0.999) {
      $h = $t.GetHue()
      $s = [Math]::Min(1.0, $t.GetSaturation() * $satMul)
      [double]$v = [Math]::Max($t.R, [Math]::Max($t.G, $t.B)) / 255.0
      $rgb = Hsv-To-Rgb $h $s $v
      $t = [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
    }
    $out.SetPixel($x, $y, $t)
  } }
  return $out
}

function Save-Relic($bmp, [string]$id) {
  $path = Join-Path $texOut "$id.png"
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  Write-Output "  -> $id.png"
}

# 仕上げパイプライン: ランプ → キーライト → アウトライン → 明度正規化
function Finish($bmp, [string]$id) {
  $a = Apply-Ramp $bmp; $bmp.Dispose()
  $b = Apply-KeyLight $a; $a.Dispose()
  $c = Apply-Outline $b; $b.Dispose()
  $d = Normalize-Brightness $c; $c.Dispose()
  Save-Relic $d $id
}

# ---------------------------------------------------------------------------
# パレット（バニラの色調に寄せた明るめの値）
# ---------------------------------------------------------------------------
$P = @{
  gold     = C 236 190 62;  goldD = C 176 128 26;  goldL = C 255 234 140
  wood     = C 150 106 58;  woodD = C 104 70 36;   woodL = C 190 146 92
  iron     = C 206 212 222; ironD = C 132 140 152; ironL = C 244 248 255
  stone    = C 158 158 166; stoneD = C 112 112 122; stoneL = C 200 200 210
  bone     = C 236 230 208; boneD = C 180 172 148
  red      = C 216 66 56;   redD  = C 152 36 32;   redL  = C 250 130 90
  orange   = C 246 156 48;  yellow= C 252 226 96
  cyan     = C 108 232 232; cyanD = C 52 168 178;  cyanL = C 190 252 252
  blue     = C 92 146 236;  blueD = C 48 82 168;   blueL = C 164 206 255
  purple   = C 168 100 216; purpleD = C 108 56 152; purpleL = C 214 168 246
  green    = C 108 196 92;  greenD= C 60 132 64;   greenL= C 178 234 146
  paper    = C 232 218 184; paperD= C 196 178 140
  dark     = C 62 60 74;    darkD = C 36 34 46
  white    = C 250 250 255
}

Write-Output "Generating relic textures v3 (name-matched shapes + vanilla rules) -> $texOut"

# ===========================================================================
# 1. hookshot フックショット — 木の柄 + 金具 + 鉄の鉤爪
# ===========================================================================
$b = New-Canvas
Draw-Line $b 3 13 10 5 $P.wood
Draw-Line $b 4 13 11 5 $P.woodD
Px $b 3 13 $P.goldD; Px $b 4 13 $P.gold
Draw-Line $b 10 4 13 2 $P.iron
Draw-Line $b 11 3 13 5 $P.ironL
Px $b 13 2 $P.ironD; Px $b 12 6 $P.ironD
Draw-Line $b 2 12 4 14 $P.ironD
Px $b 6 9 $P.goldL
Finish $b 'hookshot'

# ===========================================================================
# 2. enchbook 全適用の魔導書 — 紫の装丁 + 金の留め具 + 光るルーン
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 3 2 12 13 $P.purpleD
Fill-Rect $b 4 3 11 12 $P.paper
Fill-Rect $b 2 3 3 12 $P.purple
Fill-Rect $b 12 3 12 12 $P.purpleD
Fill-Rect $b 3 2 12 2 $P.purple
Px $b 3 2 $P.goldL; Px $b 4 2 $P.gold; Px $b 5 2 $P.gold
Px $b 7 5 $P.purpleL; Px $b 8 5 $P.purple; Px $b 7 6 $P.purple
Px $b 9 8 $P.goldD; Px $b 6 8 $P.gold
Draw-Line $b 6 10 9 10 $P.paperD
Finish $b 'enchbook'

# ===========================================================================
# 3. jetpack ジェットパック — 鉄の胴 + 青い燃料窓 + 噴射口
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 4 2 11 12 $P.ironD
Fill-Rect $b 5 3 10 11 $P.iron
Fill-Rect $b 6 3 9 4 $P.ironL
Fill-Rect $b 4 6 4 11 $P.woodD
Fill-Rect $b 11 6 11 11 $P.woodD
Fill-Rect $b 6 6 9 8 $P.cyanD
Fill-Rect $b 7 6 8 7 $P.cyan
Px $b 7 7 $P.cyanL
Fill-Rect $b 5 13 6 14 $P.stoneD
Fill-Rect $b 9 13 10 14 $P.stoneD
Px $b 5 14 $P.orange; Px $b 6 14 $P.yellow
Px $b 9 14 $P.orange; Px $b 10 14 $P.yellow
Finish $b 'jetpack'

# ===========================================================================
# 4. skates スケートブーツ — 横から見たブーツ + 青氷のブレード
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................','................','....####........','...#@@@@#.......',
  '...#@@@@#.......','...#@@@@#.......','...#@@@@#.......','...#@@@@##......',
  '...#@@@@@@##....','...#@@@@@@@@#...','...#@@@@@@@@@#..','...###########..',
  '................','..############..','..#%%%%%%%%%%#..','..############..'
) @{ '#' = $P.ironD; '@' = $P.iron; '%' = $P.cyan }
Fill-Rect $b 4 3 4 9 $P.ironL
Fill-Rect $b 5 9 11 10 $P.ironL
Fill-Rect $b 4 14 11 14 $P.cyanL
Finish $b 'skates'

# ===========================================================================
# 5. resonance 残響の結晶 — シアンの六角結晶 + 内部の光
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................','.......##.......','......#@@#......','.....#@%%@#.....',
  '....#@%@@%@#....','...#@%@@@@@@#...','..#@%@@@@@@@%#..','..#@@@@@@@@@@#..',
  '..#@@@@@@@@@@#..','...#@@@@@@@@#...','...#@@@@@@@@#...','....#@@@@@@#....',
  '.....#@@@@#.....','......#@@#......','.......##.......','................'
) @{ '#' = $P.cyanD; '@' = $P.cyan; '%' = $P.cyanL }
Draw-Line $b 4 10 11 3 $P.cyanL
Finish $b 'resonance'

# ===========================================================================
# 6. fireball ファイアーボール — 赤→橙→黄の火球 + 青白い芯
# ===========================================================================
$b = New-Canvas
Draw-Disc $b 8 8 5.6 $P.redD
Draw-Disc $b 8 8 4.6 $P.red
Draw-Disc $b 8 7.5 3.4 $P.orange
Draw-Disc $b 7.5 7 1.9 $P.yellow
Px $b 7 6 $P.white
Px $b 4 11 $P.redL; Px $b 11 4 $P.redL
Finish $b 'fireball'

# ===========================================================================
# 7. decayed_sword 朽ちた剣 — 欠けた刀身 + 古びた柄
# ===========================================================================
$b = New-Canvas
Draw-Line $b 3 12 11 4 $P.ironD
Draw-Line $b 4 12 12 4 $P.stoneL
Draw-Line $b 4 11 11 4 $P.iron
Px $b 12 3 $P.stoneL; Px $b 11 3 $P.iron; Px $b 12 4 $P.ironD
Px $b 7 8 $P.stoneD; Px $b 8 7 $P.stoneD
Draw-Line $b 3 10 6 13 $P.goldD
Px $b 3 9 $P.woodD; Px $b 2 10 $P.woodD
Draw-Line $b 2 13 4 15 $P.wood
Px $b 4 14 $P.woodL
Px $b 12 2 $P.greenD; Px $b 13 3 $P.greenD
Finish $b 'decayed_sword'

# ===========================================================================
# 8. enchgrind 錬金の砥石 — 石の台 + 紫の魔法の斜線
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 3 3 12 12 $P.stoneD
Fill-Rect $b 4 4 11 11 $P.stone
Fill-Rect $b 4 4 11 4 $P.stoneL
Fill-Rect $b 4 4 4 11 $P.stoneL
Draw-Line $b 5 10 10 5 $P.purpleD
Draw-Line $b 6 11 11 6 $P.purple
Px $b 7 8 $P.purpleL; Px $b 9 6 $P.purpleL
Px $b 5 5 $P.gold; Px $b 10 10 $P.goldD
Finish $b 'enchgrind'

# ===========================================================================
# 9. chain 拘束の鎖 — 斜めに連なる4つのリング
# ===========================================================================
$b = New-Canvas
$ring = @('.##.','#..#','#..#','.##.')
for ($i = 0; $i -lt 4; $i++) {
  $ox = 1 + $i * 3; $oy = 1 + $i * 3
  for ($y = 0; $y -lt 4; $y++) { for ($x = 0; $x -lt 4; $x++) {
    if ($ring[$y][$x] -eq '#') {
      $c = if ($y -le 1) { $P.iron } else { $P.ironD }
      Px $b ($ox + $x) ($oy + $y) $c
    }
  } }
  Px $b ($ox + 1) ($oy + 1) $P.ironL
  Px $b ($ox + 2) ($oy + 1) $P.ironL
}
Px $b 13 13 $P.ironD; Px $b 14 14 $P.ironD
Finish $b 'chain'

# ===========================================================================
# 10. giantcore 巨人の心臓 — 赤い心臓 + 金の血管（心臓の形を明示）
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................',
  '...####..####...',
  '..#@@@@##@@@@#..',
  '.#@@@@@@@@@@@@#.',
  '.#@@@@@@@@@@@@#.',
  '.#@@@@@@@@@@@@#.',
  '.#@@@@@@@@@@@@#.',
  '..#@@@@@@@@@@#..',
  '..#@@@@@@@@@@#..',
  '...#@@@@@@@@#...',
  '...#@@@@@@@@#...',
  '....#@@@@@@#....',
  '.....#@@@@#.....',
  '......#@@#......',
  '.......##.......',
  '................'
) @{ '#' = $P.redD; '@' = $P.red }
# 心臓の立体感: 左上を明るく、右下を暗く（枕影にならないよう面で分ける）
Fill-Rect $b 4 3 7 5 $P.redL
Fill-Rect $b 10 8 12 11 $P.redD
Draw-Line $b 8 4 6 8 $P.redD
Draw-Line $b 6 8 9 12 $P.redD
Draw-Line $b 6 8 11 8 $P.redD
Px $b 5 5 $P.gold; Px $b 4 6 $P.goldL
Draw-Line $b 11 2 13 4 $P.goldD
Px $b 13 1 $P.gold; Px $b 14 3 $P.goldL
Finish $b 'giantcore'

# ===========================================================================
# 11. seaswim 空に海写す蒼球 — 青い球 + 波と光沢
# ===========================================================================
$b = New-Canvas
Draw-Disc $b 8 8 5.8 $P.blueD
Draw-Disc $b 8 8 5.2 $P.blue
Draw-Disc $b 7.5 7.5 3.8 $P.blueL
Draw-Disc $b 7 7 2.2 $P.cyanL
Draw-Line $b 4 10 11 10 $P.cyan
Draw-Line $b 5 12 10 12 $P.cyanD
Px $b 5 5 $P.white
Finish $b 'seaswim'

# ===========================================================================
# 12. dashfoot バニーホップ — 兎の足 + 跳躍の軌跡
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................','.....####.......','....#@@@@#......','....#@%@@#......',
  '...##@@@@##.....','..#@@@@@@@@#....','..#@@%@@@@@#....','..#@@@@@@@@#....',
  '..#@@@@@@@@#....','...#@@@@@@#.....','...#@@@@@@#.....','....#@@@@#......',
  '....#@@@@#......','.....#@@#.......','......##........','................'
) @{ '#' = $P.boneD; '@' = $P.bone; '%' = $P.paper }
Px $b 6 3 $P.gold; Px $b 9 4 $P.goldD
Px $b 7 7 $P.boneD; Px $b 8 9 $P.boneD
Px $b 13 6 $P.cyanL; Px $b 14 8 $P.cyan; Px $b 13 10 $P.cyanD
Finish $b 'dashfoot'

# ===========================================================================
# 13. reflect 反射の守護盾 — 盾の形 + シアンの反射紋（盾として描く）
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................',
  '..############..',
  '..#@@@@@@@@@@#..',
  '..#@%%@@@@@@@#..',
  '..#@%@@@@@@@@#..',
  '..#@@@@@@@@@@#..',
  '..#@@@%%%%@@@#..',
  '..#@@@%@@%@@@#..',
  '..#@@@%%%%@@@#..',
  '..#@@@@@@@@@@#..',
  '...#@@@@@@@@#...',
  '....#@@@@@@#....',
  '.....#@@@@#.....',
  '......#@@#......',
  '.......##.......',
  '................'
) @{ '#' = $P.dark; '@' = $P.blueD; '%' = $P.cyanL }
Fill-Rect $b 3 2 12 2 $P.blue
Fill-Rect $b 4 3 11 3 $P.blue
Draw-Line $b 4 3 11 3 $P.cyan
Finish $b 'reflect'

# ===========================================================================
# 14. nightclock 常夜渡りの時計 — 濃紺の文字盤 + シアンの針
# ===========================================================================
$b = New-Canvas
Draw-Disc $b 8 8 6.2 $P.darkD
Draw-Disc $b 8 8 5.4 $P.dark
Draw-Disc $b 8 8 4.6 $P.cyanD
Draw-Disc $b 8 8 3.8 $P.cyan
Px $b 8 5 $P.darkD; Px $b 8 6 $P.darkD
Px $b 8 7 $P.darkD; Px $b 9 8 $P.darkD; Px $b 10 8 $P.darkD
Px $b 8 8 $P.goldL
Px $b 8 1 $P.gold; Px $b 8 14 $P.goldD
Px $b 6 3 $P.cyanL
Finish $b 'nightclock'

# ===========================================================================
# 15. magnet 磁力の石 — 石 + 赤い磁極 + 磁力線
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 3 4 12 11 $P.stoneD
Fill-Rect $b 4 5 11 10 $P.stone
Fill-Rect $b 4 5 11 5 $P.stoneL
Fill-Rect $b 6 6 9 9 $P.redD
Fill-Rect $b 7 7 8 8 $P.red
Px $b 7 6 $P.gold; Px $b 8 9 $P.goldD
Draw-Line $b 2 2 2 13 $P.cyanD
Draw-Line $b 13 2 13 13 $P.cyanD
Px $b 2 2 $P.cyanL; Px $b 13 2 $P.cyanL
Px $b 2 13 $P.cyan; Px $b 13 13 $P.cyan
Finish $b 'magnet'

# ===========================================================================
# 16. shrinkstar 剝蝕矮星 — 緑の十字星 + 欠け
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................','.......##.......','.......@@.......','......#@@#......',
  '......#@@#......','.....#@@@@#.....','#####@%@@%@#####','#@@@@%@@@@%@@@@#',
  '#####@%@@%@#####','.....#@@@@#.....','......#@@#......','......#@@#......',
  '.......@@.......','.......##.......','................','................'
) @{ '#' = $P.greenD; '@' = $P.green; '%' = $P.greenL }
Px $b 8 12 $P.darkD; Px $b 7 13 $P.darkD
Finish $b 'shrinkstar'

# ===========================================================================
# 17. lightning 雷鳴の杖 — 木の杖 + 金の稲妻（杖として描く）
# ===========================================================================
$b = New-Canvas
Draw-Line $b 3 14 10 4 $P.wood
Draw-Line $b 4 14 11 4 $P.woodD
Px $b 3 14 $P.goldD; Px $b 4 14 $P.gold
Px $b 5 13 $P.woodL; Px $b 6 12 $P.woodL
Pattern $b @(
  '................','..........####..','.........#@@@#..','.........#@%#...',
  '........#@%#....','.......#@%#.....','......#@%#......','.....#@%#.......',
  '.....#@#........','.....##.........','................','................',
  '................','................','................','................'
) @{ '#' = $P.goldD; '@' = $P.yellow; '%' = $P.goldL }
Px $b 11 4 $P.gold; Px $b 12 3 $P.goldL
Finish $b 'lightning'

# ===========================================================================
# 18. blink 次元の眼 — 紫の眼 + 暗い瞳孔
# ===========================================================================
$b = New-Canvas
Draw-Disc $b 8 8 5.8 $P.purpleD
Draw-Disc $b 8 8 5.0 $P.purple
Draw-Disc $b 8 8 3.6 $P.dark
Draw-Disc $b 8 8 2.6 $P.purpleL
Draw-Disc $b 8 8 1.3 $P.darkD
Px $b 7 7 $P.cyanL; Px $b 6 6 $P.cyanL
Px $b 11 5 $P.purpleL; Px $b 5 11 $P.purpleL
Finish $b 'blink'

# ===========================================================================
# 19. totem 死不知のトーテム — 金のトーテム + 緑の目
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 5 1 10 4 $P.goldD
Fill-Rect $b 6 2 9 3 $P.gold
Fill-Rect $b 4 5 11 12 $P.gold
Fill-Rect $b 5 5 10 11 $P.goldL
# トーテムの立体感: 左を明るく右を暗く、腕を分ける
Fill-Rect $b 4 5 4 12 $P.goldL
Fill-Rect $b 11 5 11 12 $P.goldD
Fill-Rect $b 4 8 4 9 $P.goldD
Fill-Rect $b 11 8 11 9 $P.goldD
Px $b 6 7 $P.dark; Px $b 9 7 $P.dark
Px $b 6 8 $P.green; Px $b 9 8 $P.green
Draw-Line $b 7 9 8 9 $P.goldD
Draw-Line $b 6 10 9 10 $P.goldD
Fill-Rect $b 4 13 11 14 $P.goldD
Px $b 7 13 $P.green; Px $b 8 13 $P.greenL
Px $b 8 1 $P.greenL
Finish $b 'totem'

# ===========================================================================
# 20. wither 凋零の頭骨 — 頭骨の形を明示（石炭の流用をやめる）
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................',
  '....########....',
  '...#@@@@@@@@#...',
  '..#@@@@@@@@@@#..',
  '..#@@######@@#..',
  '..#@@#%%%%#@@#..',
  '..#@@#%##%#@@#..',
  '..#@@######@@#..',
  '..#@@@@@@@@@@#..',
  '...#@@@##@@@#...',
  '....#@@@@@@#....',
  '....#@#@@#@#....',
  '....#@@@@@@#....',
  '.....#@@@@#.....',
  '......####......',
  '................'
) @{ '#' = $P.darkD; '@' = $P.dark; '%' = $P.bone }
Px $b 5 5 $P.green; Px $b 10 5 $P.green
Px $b 5 6 $P.greenL; Px $b 10 6 $P.greenL
Fill-Rect $b 7 9 8 9 $P.darkD
Finish $b 'wither'

# ===========================================================================
# 21. bell 癒やしの鐘 — 金の鐘 + 緑の癒やしの光
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 6 2 9 4 $P.goldD
Fill-Rect $b 5 5 10 9 $P.gold
Fill-Rect $b 6 5 9 8 $P.goldL
Fill-Rect $b 4 10 11 11 $P.goldD
Fill-Rect $b 5 10 10 10 $P.gold
# 鐘の立体感: 左側を明るく、右側を暗く
Fill-Rect $b 5 5 5 9 $P.goldL
Fill-Rect $b 10 5 10 9 $P.goldD
Fill-Rect $b 7 5 8 6 $P.white
Px $b 7 12 $P.green; Px $b 8 12 $P.greenL
Px $b 7 13 $P.greenD
Px $b 3 3 $P.greenL; Px $b 12 3 $P.greenL
Px $b 2 5 $P.greenD; Px $b 13 5 $P.greenD
Finish $b 'bell'

# ===========================================================================
# 22. dragonbreath 竜の吐息 — 紫のブレス + 淡い先端
# ===========================================================================
$b = New-Canvas
Pattern $b @(
  '................','................','................','..........##....',
  '.........#@@#...','........#@%%@#..','.......#@%@@%@#.','......#@%@@@@%@#',
  '.....#@%@@@@@%#.','....#@%@@@@@%#..','...#@%@@@@@%#...','..#@%@@@@@%#....',
  '..#@@@@@@%#.....','..##@@@@@#......','....#####.......','................'
) @{ '#' = $P.purpleD; '@' = $P.purple; '%' = $P.purpleL }
Px $b 7 6 $P.purpleL; Px $b 8 8 $P.purpleL
Px $b 12 4 $P.cyanL
Finish $b 'dragonbreath'

# ===========================================================================
# 23. vision 暗視鏡 — 二連の暗視レンズ + シアンの光
# ===========================================================================
$b = New-Canvas
Fill-Rect $b 2 6 6 9 $P.dark
Fill-Rect $b 9 6 13 9 $P.dark
Fill-Rect $b 7 6 8 8 $P.darkD
Fill-Rect $b 3 7 5 8 $P.cyan
Fill-Rect $b 10 7 12 8 $P.cyan
Px $b 4 7 $P.cyanL; Px $b 11 7 $P.cyanL
Fill-Rect $b 3 4 5 5 $P.stoneD
Fill-Rect $b 10 4 12 5 $P.stoneD
Fill-Rect $b 2 10 6 11 $P.stoneD
Fill-Rect $b 9 10 13 11 $P.stoneD
Px $b 7 9 $P.gold; Px $b 8 9 $P.goldD
Finish $b 'vision'

Write-Output ""
Write-Output "DONE: 23 textures"
