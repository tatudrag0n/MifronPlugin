# 追加レリック（正式版で増えた12種）のテクスチャを描く。
#
# 方針は gen-relic-textures-v3.ps1 と同じ:
#   シルエット優先 / 3段ランプ / 左上光源 / 局所色アウトライン / 影は塊
# 出力先も同じなので、build-resourcepack.ps1 が自動的に拾う。

param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'
if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }

function C([int]$r, [int]$g, [int]$b) { return [System.Drawing.Color]::FromArgb(255, $r, $g, $b) }

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

function Tone($color, [double]$valMul, [double]$hueDeg) {
  $h = $color.GetHue() + $hueDeg
  if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
  [double]$v = [Math]::Max($color.R, [Math]::Max($color.G, $color.B)) / 255.0
  $v = [Math]::Max(0.0, [Math]::Min(1.0, $v * $valMul))
  $rgb = Hsv-To-Rgb $h $color.GetSaturation() $v
  return [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
}

function New-Canvas { return New-Object System.Drawing.Bitmap 16, 16 }

function Px($bmp, [int]$x, [int]$y, $color) {
  if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) { $bmp.SetPixel($x, $y, $color) }
}
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $color) {
  for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Px $bmp $x $y $color } }
}
function Pattern($bmp, [string[]]$rows, [hashtable]$palette) {
  for ($y = 0; $y -lt $rows.Count; $y++) {
    $row = $rows[$y]
    for ($x = 0; $x -lt $row.Length -and $x -lt 16; $x++) {
      $ch = [string]$row[$x]
      if ($ch -ne '.' -and $palette.ContainsKey($ch)) { Px $bmp $x $y $palette[$ch] }
    }
  }
}

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
    if ($b -le $lo) { $col = Tone $c 0.92 16 }
    elseif ($b -ge $hi) { $col = Tone $c 1.22 -12 }
    else { $col = Tone $c 1.10 0 }
    $out.SetPixel($x, $y, $col)
  } }
  return $out
}

function Apply-KeyLight($bmp) {
  $out = New-Canvas
  $g = [System.Drawing.Graphics]::FromImage($out); $g.DrawImage($bmp, 0, 0, 16, 16); $g.Dispose()
  $anchor = $null; $best = 999
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    if ($bmp.GetPixel($x, $y).A -eq 0) { continue }
    $up = if ($y -gt 0) { $bmp.GetPixel($x, $y - 1) } else { $null }
    $left = if ($x -gt 0) { $bmp.GetPixel($x - 1, $y) } else { $null }
    if (($null -eq $up -or $up.A -eq 0) -or ($null -eq $left -or $left.A -eq 0)) {
      $d = $x + $y
      if ($d -lt $best) { $best = $d; $anchor = @($x, $y) }
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
    $out.SetPixel($s[0], $s[1], (Tone $bmp.GetPixel($s[0], $s[1]) 1.42 -18))
  }
  return $out
}

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

function Normalize-Brightness($bmp, [double]$target = 0.56) {
  $sum = 0.0; $n = 0
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -ne 0) {
      $sum += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0; $n++
    }
  } }
  if ($n -eq 0) { return $bmp }
  $avg = $sum / $n; if ($avg -le 0.01) { return $bmp }
  [double]$mul = [Math]::Max(0.6, [Math]::Min(3.5, $target / $avg))
  [double]$satMul = if ($mul -gt 1.0) { 1.0 / [Math]::Pow($mul, 0.55) } else { 1.0 }
  $out = New-Canvas
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $t = Tone $c $mul 0
    if ($satMul -lt 0.999) {
      $s = [Math]::Min(1.0, $t.GetSaturation() * $satMul)
      [double]$v = [Math]::Max($t.R, [Math]::Max($t.G, $t.B)) / 255.0
      $rgb = Hsv-To-Rgb $t.GetHue() $s $v
      $t = [System.Drawing.Color]::FromArgb(255, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2])
    }
    $out.SetPixel($x, $y, $t)
  } }
  return $out
}

function Finish($bmp, [string]$id) {
  $a = Apply-Ramp $bmp; $bmp.Dispose()
  $b = Apply-KeyLight $a; $a.Dispose()
  $c = Apply-Outline $b; $b.Dispose()
  $d = Normalize-Brightness $c; $c.Dispose()
  $path = Join-Path $texOut "$id.png"
  $d.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $d.Dispose()
  Write-Output "  -> $id.png"
}

$P = @{
  gold = C 236 190 62; goldD = C 176 128 26; goldL = C 255 234 140
  wood = C 150 106 58; woodD = C 104 70 36; woodL = C 190 146 92
  iron = C 206 212 222; ironD = C 132 140 152; ironL = C 244 248 255
  stone = C 158 158 166; stoneD = C 112 112 122; stoneL = C 200 200 210
  red = C 216 66 56; redD = C 152 36 32; redL = C 250 130 90
  orange = C 246 156 48; yellow = C 252 226 96
  cyan = C 108 232 232; cyanD = C 52 168 178; cyanL = C 190 252 252
  blue = C 92 146 236; blueD = C 48 82 168; blueL = C 164 206 255
  purple = C 168 100 216; purpleD = C 108 56 152; purpleL = C 214 168 246
  green = C 108 196 92; greenD = C 60 132 64; greenL = C 178 234 146
  white = C 250 250 255; dark = C 62 60 74; darkD = C 36 34 46
  paper = C 232 218 184; paperD = C 196 178 140
}

Write-Output "Drawing extra relic textures -> $texOut"

# 1. time_clock ときを刻む時計 ----------------------------------------------
$b = New-Canvas
$rows = @(
  '................','.......##.......','......#%%#......','....##@@@@##....',
  '...#@@@@@@@@#...','..#@@######@@#..','..#@#@@@@@@#@#..','..#@#@@##@@#@#..',
  '..#@#@@##@@#@#..','..#@#@@@@@@#@#..','..#@@######@@#..','...#@@@@@@@@#...',
  '....##@@@@##....','......#%%#......','.......##.......','................'
)
Pattern $b $rows @{ '#' = $P.goldD; '@' = $P.gold; '%' = $P.goldL }
Fill-Rect $b 7 2 8 2 $P.goldL
Fill-Rect $b 8 6 8 8 $P.darkD
Fill-Rect $b 8 8 10 8 $P.darkD
Px $b 8 8 $P.red
Finish $b 'time_clock'

# 2. antigravity_mace 反重力戦鎚 --------------------------------------------
$b = New-Canvas
$rows = @(
  '................','....########....','...#@@@@@@@@#...','..#@@##@@##@@#..',
  '..#@@@@@@@@@@#..','..#@@##@@##@@#..','...##########...','.......##.......',
  '.......##.......','......####......','.......##.......','......####......',
  '.......##.......','......####......','.......##.......','................'
)
Pattern $b $rows @{ '#' = $P.ironD; '@' = $P.iron }
Fill-Rect $b 4 2 6 3 $P.ironL
Fill-Rect $b 7 7 8 8 $P.purple
Px $b 8 9 $P.purpleL
Finish $b 'antigravity_mace'

# 3. ancient_slash_armor 古代の斬鎧 -----------------------------------------
$b = New-Canvas
$rows = @(
  '................','...##......##...','..#@@#....#@@#..','.#@@@@####@@@@#.',
  '.#@@@@@@@@@@@@#.','.#@@########@@#.','.#@@#@@@@@@#@@#.','.#@@#@@##@@#@@#.',
  '.#@@#@@@@@@#@@#.','.#@@########@@#.','.#@@@@@@@@@@@@#.','..#@@@@@@@@@@#..',
  '..#@@@@@@@@@@#..','..#@@@@@@@@@@#..','...##########...','................'
)
Pattern $b $rows @{ '#' = $P.darkD; '@' = $P.redD }
Fill-Rect $b 4 4 5 5 $P.redL
Fill-Rect $b 7 7 8 8 $P.red
Finish $b 'ancient_slash_armor'

# 4. hellfire_book 煉獄の書 -------------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 2 12 13 $P.redD
Fill-Rect $b 4 3 11 12 $P.red
Fill-Rect $b 2 3 3 12 $P.redD
Fill-Rect $b 12 3 12 12 $P.darkD
Fill-Rect $b 5 5 10 6 $P.orange
Fill-Rect $b 6 7 9 8 $P.yellow
Fill-Rect $b 7 9 8 10 $P.orange
Px $b 5 10 $P.redL
Px $b 3 2 $P.gold
Finish $b 'hellfire_book'

# 5. endless_grimoire 無尽の魔導書 ------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 2 12 13 $P.blueD
Fill-Rect $b 4 3 11 12 $P.dark
Fill-Rect $b 2 3 3 12 $P.purpleD
Fill-Rect $b 12 3 12 12 $P.blueD
Px $b 6 5 $P.white; Px $b 9 5 $P.white
Px $b 5 8 $P.cyanL; Px $b 10 8 $P.cyanL
Px $b 7 7 $P.purpleL; Px $b 8 7 $P.purpleL
Px $b 3 2 $P.goldL
Finish $b 'endless_grimoire'

# 6. ancient_anvil 古代の金床 -------------------------------------------------
$b = New-Canvas
$rows = @(
  '................','...##########...','..#@@@@@@@@@@#..','..#@@##@@##@@#..',
  '..#@@@@@@@@@@#..','...#@@@@@@@@#...','....########....','.....######.....',
  '.....#@@@@#.....','.....#@@@@#.....','....#@@@@@@#....','...#@@@@@@@@#...',
  '..#@@@@@@@@@@#..','..############..','................','................'
)
Pattern $b $rows @{ '#' = $P.stoneD; '@' = $P.stone }
Fill-Rect $b 4 2 7 3 $P.stoneL
Px $b 7 8 $P.gold; Px $b 8 10 $P.goldD
Finish $b 'ancient_anvil'

# 7. gaybolg ゲイ・ボルグ ---------------------------------------------------
$b = New-Canvas
for ($i = 0; $i -lt 11; $i++) { Px $b (4 + $i) (12 - $i) $P.wood }
for ($i = 0; $i -lt 11; $i++) { Px $b (5 + $i) (12 - $i) $P.woodD }
Px $b 3 13 $P.goldD
# ギザギザの穂先
Pattern $b @(
  '................','...........###..','..........#@@#..','.........#@@#...',
  '........#@@#....','.......#@@#.....','......#@@#......','.....#@@#.......',
  '....##@@#.......','....###........','................','................',
  '................','................','................','................'
) @{ '#' = $P.redD; '@' = $P.red }
Px $b 11 3 $P.redL
Finish $b 'gaybolg'

# 8. excalibur_replica エクスカリバーのレプリカ -----------------------------
$b = New-Canvas
for ($i = 0; $i -lt 12; $i++) { Px $b (3 + $i) (13 - $i) $P.iron }
for ($i = 0; $i -lt 12; $i++) { Px $b (4 + $i) (13 - $i) $P.ironL }
Px $b 3 13 $P.goldD; Px $b 4 13 $P.gold
Fill-Rect $b 3 10 6 11 $P.gold
Px $b 2 9 $P.goldL
Px $b 13 2 $P.cyanL; Px $b 12 3 $P.cyan
Finish $b 'excalibur_replica'

# 9. infinite_bundle 無限のバンドル -----------------------------------------
$b = New-Canvas
$rows = @(
  '................','......####......','.....#@@@@#.....','....#@@@@@@#....',
  '...#@@@@@@@@#...','...#@@@@@@@@#...','..#@@@@@@@@@@#..','..#@@##@@##@@#..',
  '..#@#@@@@@@#@#..','..#@@##@@##@@#..','..#@@@@@@@@@@#..','..#@@@@@@@@@@#..',
  '...#@@@@@@@@#...','....#@@@@@@#....','.....######.....','................'
)
Pattern $b $rows @{ '#' = $P.woodD; '@' = $P.wood }
Fill-Rect $b 6 7 9 8 $P.goldL
Px $b 5 5 $P.woodL
Finish $b 'infinite_bundle'

# 10. noahs_ark ノアの方舟 --------------------------------------------------
$b = New-Canvas
Pattern $b @(
  '................','................','......####......','.....#@@@@#.....',
  '....#@@@@@@#....','...#@@@@@@@@#...','..############..','..#@@@@@@@@@@#..',
  '..#@@##@@##@@#..','..#@@@@@@@@@@#..','..#@@##@@##@@#..','..#@@@@@@@@@@#..',
  '..############..','...##########...','................','................'
) @{ '#' = $P.woodD; '@' = $P.wood }
Fill-Rect $b 6 2 9 3 $P.woodL
Fill-Rect $b 7 4 8 5 $P.paper
Px $b 8 5 $P.cyanL
Finish $b 'noahs_ark'

# 11. artemis アルテミス ----------------------------------------------------
$b = New-Canvas
# 三日月の弓
for ($y = 2; $y -le 13; $y++) { Px $b 3 $y $P.gold }
for ($y = 2; $y -le 13; $y++) { Px $b 4 $y $P.goldD }
Px $b 3 2 $P.goldL; Px $b 3 13 $P.goldL
Px $b 5 3 $P.goldD; Px $b 5 12 $P.goldD
Px $b 6 4 $P.goldD; Px $b 6 11 $P.goldD
# 弦
for ($y = 2; $y -le 13; $y++) { Px $b 7 $y $P.ironL }
# 矢
for ($i = 0; $i -lt 9; $i++) { Px $b (6 + $i) (8 - [Math]::Floor($i * 0.4)) $P.wood }
Px $b 12 6 $P.cyanL; Px $b 13 5 $P.cyan; Px $b 12 7 $P.cyan
Px $b 5 8 $P.greenL
Finish $b 'artemis'

Write-Output "DONE: extra relic textures"
