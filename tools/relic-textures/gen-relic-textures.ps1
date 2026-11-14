<#
  MifronPlugin レリック 16x16 アイテムテクスチャ生成スクリプト
  - 依存なし (System.Drawing のみ)
  - 各レリックを 16x16 のドット絵として決定的に描画する
  - 出力: <out>/assets/mifron/textures/item/relic/<id>.png
#>
param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics')
)

Add-Type -AssemblyName System.Drawing

$W = 16
$H = 16

function New-Canvas {
  return New-Object System.Drawing.Bitmap $W, $H
}

function C([int]$r, [int]$g, [int]$b, [int]$a = 255) {
  return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function Px($bmp, [int]$x, [int]$y, $color) {
  if ($x -ge 0 -and $x -lt $W -and $y -ge 0 -and $y -lt $H) { $bmp.SetPixel($x, $y, $color) }
}

# 文字列パターンを描く。'.' は透明、それ以外は palette のキー
function Draw-Pattern($bmp, [string[]]$rows, [hashtable]$palette) {
  for ($y = 0; $y -lt $rows.Count; $y++) {
    $row = $rows[$y]
    for ($x = 0; $x -lt $row.Length; $x++) {
      $ch = [string]$row[$x]
      if ($ch -ne '.' -and $palette.ContainsKey($ch)) { Px $bmp $x $y $palette[$ch] }
    }
  }
}

# 矩形塗り
function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $color) {
  for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { Px $bmp $x $y $color } }
}

# 斜線
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

# 円
function Draw-Disc($bmp, [double]$cx, [double]$cy, [double]$r, $color) {
  for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
      $d = [Math]::Sqrt([Math]::Pow($x + 0.5 - $cx, 2) + [Math]::Pow($y + 0.5 - $cy, 2))
      if ($d -le $r) { Px $bmp $x $y $color }
    }
  }
}

# 輪郭を付ける（透明マスに隣接する不透明マスを outline 色で囲む）
function Add-Outline($bmp, $outline) {
  $targets = @()
  for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
      if ($bmp.GetPixel($x, $y).A -ne 0) { continue }
      $near = $false
      foreach ($o in @(@(1,0),@(-1,0),@(0,1),@(0,-1))) {
        $nx = $x + $o[0]; $ny = $y + $o[1]
        if ($nx -ge 0 -and $nx -lt $W -and $ny -ge 0 -and $ny -lt $H) {
          if ($bmp.GetPixel($nx, $ny).A -ne 0) { $near = $true; break }
        }
      }
      if ($near) { $targets += , @($x, $y) }
    }
  }
  foreach ($t in $targets) { Px $bmp $t[0] $t[1] $outline }
}

# 左上からの光沢（不透明マスの左上端を明るく）
function Add-Highlight($bmp, $hi) {
  $targets = @()
  for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
      $c = $bmp.GetPixel($x, $y)
      if ($c.A -eq 0) { continue }
      $up = if ($y -gt 0) { $bmp.GetPixel($x, $y - 1) } else { $null }
      $left = if ($x -gt 0) { $bmp.GetPixel($x - 1, $y) } else { $null }
      if (($null -eq $up -or $up.A -eq 0) -and ($null -eq $left -or $left.A -eq 0)) { $targets += , @($x, $y) }
    }
  }
  foreach ($t in $targets) { Px $bmp $t[0] $t[1] $hi }
}

$script:made = @()

function Save-Relic($bmp, [string]$id) {
  $dir = Join-Path $OutDir "assets\mifron\textures\item\relic"
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
  $path = Join-Path $dir "$id.png"
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  $script:made += $id
  Write-Output "  -> $id.png"
}

# ---------------------------------------------------------------------------
# 共通パレット
# ---------------------------------------------------------------------------
$OUTLINE = C 20 18 24
$GOLD    = C 240 190 60
$GOLD_D  = C 176 128 32
$GOLD_L  = C 255 232 140
$WOOD    = C 122 84 48
$WOOD_D  = C 84 56 30
$WOOD_L  = C 160 116 70
$IRON    = C 196 200 208
$IRON_D  = C 128 134 144
$IRON_L  = C 236 240 246
$DARK    = C 52 50 62
$DARK_D  = C 30 28 38
$PAPER   = C 226 210 176
$PAPER_D = C 190 170 132
$RED     = C 200 52 44
$RED_D   = C 138 28 28
$RED_L   = C 246 120 70
$ORANGE  = C 240 140 40
$YELLOW  = C 250 220 90
$CYAN    = C 90 220 220
$CYAN_D  = C 40 150 160
$CYAN_L  = C 180 250 250
$BLUE    = C 70 120 220
$BLUE_D  = C 34 62 140
$BLUE_L  = C 140 190 250
$PURPLE  = C 150 80 200
$PURPLE_D= C 92 44 132
$PURPLE_L= C 200 150 240
$GREEN   = C 90 180 80
$GREEN_D = C 48 116 52
$GREEN_L = C 160 224 130
$BONE    = C 224 218 196
$BONE_D  = C 168 160 136
$STONE   = C 140 140 148
$STONE_D = C 96 96 104
$STONE_L = C 184 184 192

Write-Output "Generating relic textures -> $OutDir"

# 1. hookshot フックショット -------------------------------------------------
$b = New-Canvas
Draw-Line $b 3 12 11 4 $WOOD
Draw-Line $b 4 12 12 4 $WOOD_D
Px $b 3 12 $GOLD_D; Px $b 4 12 $GOLD
Draw-Line $b 11 3 13 5 $IRON
Draw-Line $b 12 2 14 4 $IRON_L
Px $b 13 2 $IRON; Px $b 14 3 $IRON_D
Px $b 12 6 $IRON_D; Px $b 13 6 $IRON
Draw-Line $b 2 11 4 13 $IRON_D
Px $b 6 9 $GOLD; Px $b 7 8 $GOLD_D
Add-Outline $b $OUTLINE
Save-Relic $b 'hookshot'

# 2. enchbook 全適用の魔導書 ------------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 2 12 13 $PURPLE_D
Fill-Rect $b 4 3 11 12 $PAPER
Fill-Rect $b 3 2 3 13 $PURPLE
Fill-Rect $b 2 3 2 12 $PURPLE_D
Fill-Rect $b 12 3 12 12 $PURPLE_D
Px $b 4 2 $GOLD; Px $b 3 2 $GOLD_L; Px $b 4 3 $GOLD_L
Px $b 7 5 $GOLD_L; Px $b 8 5 $GOLD; Px $b 7 6 $GOLD; Px $b 8 6 $GOLD_D
Px $b 9 7 $GOLD; Px $b 6 7 $GOLD_D
Draw-Line $b 6 9 9 9 $PURPLE_D
Draw-Line $b 6 11 8 11 $PURPLE_D
Px $b 5 4 $GOLD; Px $b 10 4 $GOLD_D
Add-Outline $b $OUTLINE
Save-Relic $b 'enchbook'

# 3. jetpack ジェットパック --------------------------------------------------
$b = New-Canvas
Fill-Rect $b 4 2 11 12 $IRON_D
Fill-Rect $b 5 3 10 11 $IRON
Fill-Rect $b 6 3 9 4 $IRON_L
Fill-Rect $b 4 5 4 11 $WOOD_D
Fill-Rect $b 11 5 11 11 $WOOD_D
Fill-Rect $b 6 6 9 8 $CYAN_D
Fill-Rect $b 7 6 8 7 $CYAN
Px $b 7 7 $CYAN_L
Fill-Rect $b 5 13 6 14 $STONE_D
Fill-Rect $b 9 13 10 14 $STONE_D
Px $b 5 14 $ORANGE; Px $b 6 14 $YELLOW
Px $b 9 14 $ORANGE; Px $b 10 14 $YELLOW
Px $b 6 3 $GOLD; Px $b 9 3 $GOLD
Add-Outline $b $OUTLINE
Save-Relic $b 'jetpack'

# 4. skates スケートブーツ ---------------------------------------------------
# 横から見たブーツ：上に脚の筒、下に足、底に青氷のブレード
$b = New-Canvas
Draw-Pattern $b @(
  '................',
  '................',
  '....####........',
  '...#@@@@#.......',
  '...#@@@@#.......',
  '...#@@@@#.......',
  '...#@@@@#.......',
  '...#@@@@##......',
  '...#@@@@@@##....',
  '...#@@@@@@@@#...',
  '...#@@@@@@@@@#..',
  '...###########..',
  '................',
  '..############..',
  '..#%%%%%%%%%%#..',
  '..############..'
) @{ '#' = $IRON_D; '@' = $IRON; '%' = $CYAN }
Fill-Rect $b 4 3 4 9 $IRON_L
Fill-Rect $b 5 9 11 10 $IRON_L
Fill-Rect $b 4 14 11 14 $CYAN_L
Px $b 6 14 $CYAN_L; Px $b 9 14 $CYAN_L
Px $b 12 10 $CYAN_D
Add-Outline $b $OUTLINE
Save-Relic $b 'skates'

# 5. resonance 残響の結晶 ----------------------------------------------------
$b = New-Canvas
Draw-Pattern $b @(
  '................',
  '.......##.......',
  '......#@@#......',
  '.....#@%%@#.....',
  '....#@%@@%@#....',
  '...#@%@@@@@@#...',
  '..#@%@@@@@@@%#..',
  '..#@@@@@@@@@@#..',
  '..#@@@@@@@@@@#..',
  '...#@@@@@@@@#...',
  '...#@@@@@@@@#...',
  '....#@@@@@@#....',
  '.....#@@@@#.....',
  '......#@@#......',
  '.......##.......',
  '................'
) @{ '#' = $CYAN_D; '@' = $CYAN; '%' = $CYAN_L }
Draw-Line $b 4 10 11 3 $CYAN_L
Add-Outline $b $OUTLINE
Save-Relic $b 'resonance'

# 6. fireball ファイアーボール -----------------------------------------------
$b = New-Canvas
Draw-Disc $b 8 8 5.4 $RED_D
Draw-Disc $b 8 8 4.4 $RED
Draw-Disc $b 8 7 3.2 $ORANGE
Draw-Disc $b 7.5 6.5 1.8 $YELLOW
Px $b 7 6 $CYAN_L
Px $b 4 11 $RED_L; Px $b 11 4 $RED_L
Px $b 11 11 $RED_L; Px $b 4 4 $RED_L
Add-Outline $b $OUTLINE
Save-Relic $b 'fireball'

# 7. decayed_sword 朽ちた剣 --------------------------------------------------
$b = New-Canvas
Draw-Line $b 3 12 11 4 $IRON_D
Draw-Line $b 4 12 12 4 $STONE_L
Draw-Line $b 4 11 11 4 $IRON
Px $b 12 3 $STONE_L; Px $b 11 3 $IRON; Px $b 12 4 $IRON_D
Px $b 7 8 $STONE_D; Px $b 8 7 $STONE_D
Px $b 6 9 $STONE_D
Draw-Line $b 3 10 6 13 $GOLD_D
Px $b 3 9 $WOOD_D; Px $b 2 10 $WOOD_D
Draw-Line $b 2 13 4 15 $WOOD
Px $b 4 14 $WOOD_L
Px $b 12 2 $GREEN_D; Px $b 13 3 $GREEN_D
Add-Outline $b $OUTLINE
Save-Relic $b 'decayed_sword'

# 8. enchgrind 錬金の砥石 ----------------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 3 12 12 $STONE_D
Fill-Rect $b 4 4 11 11 $STONE
Fill-Rect $b 4 4 11 4 $STONE_L
Fill-Rect $b 4 4 4 11 $STONE_L
Draw-Line $b 5 10 10 5 $PURPLE_D
Draw-Line $b 6 11 11 6 $PURPLE
Px $b 7 8 $PURPLE_L; Px $b 9 6 $PURPLE_L
Px $b 10 9 $PURPLE_L
Px $b 5 5 $GOLD; Px $b 10 10 $GOLD_D
Add-Outline $b $OUTLINE
Save-Relic $b 'enchgrind'

# 9. chain 拘束の鎖 ----------------------------------------------------------
# 斜めに連なるリング。1リング = 4x4 の菱形リングを 3px ずつずらして重ねる
$b = New-Canvas
$ring = @(
  '.##.',
  '#..#',
  '#..#',
  '.##.'
)
for ($i = 0; $i -lt 4; $i++) {
  $ox = 1 + $i * 3
  $oy = 1 + $i * 3
  for ($y = 0; $y -lt 4; $y++) {
    for ($x = 0; $x -lt 4; $x++) {
      if ($ring[$y][$x] -eq '#') {
        $c = if ($y -le 1) { $IRON } else { $IRON_D }
        Px $b ($ox + $x) ($oy + $y) $c
      }
    }
  }
  # 前のリングと重なる部分を明るく
  Px $b ($ox + 1) ($oy + 1) $IRON_L
  Px $b ($ox + 2) ($oy + 1) $IRON_L
}
Px $b 13 13 $IRON_D; Px $b 14 14 $IRON_D
Add-Outline $b $OUTLINE
Save-Relic $b 'chain'

# 10. giantcore 巨人の心臓 ---------------------------------------------------
$b = New-Canvas
Draw-Disc $b 7 8 5.2 $RED_D
Draw-Disc $b 6.5 7.5 4.2 $RED
Draw-Disc $b 6 7 2.6 $RED_L
Draw-Line $b 9 4 6 8 $RED_D
Draw-Line $b 6 8 9 12 $RED_D
Draw-Line $b 6 8 11 8 $RED_D
Px $b 5 5 $GOLD; Px $b 4 6 $GOLD_L
Draw-Line $b 11 2 13 4 $GOLD_D
Draw-Line $b 12 1 14 3 $GOLD
Px $b 13 1 $GOLD_L
Add-Outline $b $OUTLINE
Save-Relic $b 'giantcore'

# 11. seaswim 空に海写す蒼球 -------------------------------------------------
$b = New-Canvas
Draw-Disc $b 8 8 5.6 $BLUE_D
Draw-Disc $b 8 8 5.0 $BLUE
Draw-Disc $b 7.5 7.5 3.6 $BLUE_L
Draw-Disc $b 7 7 2.0 $CYAN_L
Draw-Line $b 4 10 11 10 $CYAN
Draw-Line $b 5 12 10 12 $CYAN_D
Px $b 5 5 $CYAN_L; Px $b 6 4 $CYAN_L
Px $b 10 11 $CYAN; Px $b 11 9 $CYAN_L
Add-Outline $b $OUTLINE
Save-Relic $b 'seaswim'

# 12. dashfoot バニーホップ ---------------------------------------------------
# 兎の足＋跳躍の軌跡
$b = New-Canvas
Draw-Pattern $b @(
  '................',
  '.....####.......',
  '....#@@@@#......',
  '....#@%@@#......',
  '...##@@@@##.....',
  '..#@@@@@@@@#....',
  '..#@@%@@@@@#....',
  '..#@@@@@@@@#....',
  '..#@@@@@@@@#....',
  '...#@@@@@@#.....',
  '...#@@@@@@#.....',
  '....#@@@@#......',
  '....#@@@@#......',
  '.....#@@#.......',
  '......##........',
  '................'
) @{ '#' = $BONE_D; '@' = $BONE; '%' = $PAPER }
Px $b 6 3 $GOLD; Px $b 9 4 $GOLD_D
Px $b 7 7 $BONE_D; Px $b 8 9 $BONE_D
Px $b 13 6 $CYAN_L; Px $b 14 8 $CYAN; Px $b 13 10 $CYAN_D
Add-Outline $b $OUTLINE
Save-Relic $b 'dashfoot'

# 13. reflect 反射の守護盾 ---------------------------------------------------
$b = New-Canvas
Draw-Pattern $b @(
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
) @{ '#' = $DARK; '@' = $BLUE_D; '%' = $CYAN_L }
Draw-Line $b 4 3 11 3 $CYAN
Add-Outline $b $OUTLINE
Save-Relic $b 'reflect'

# 14. nightclock 常夜渡りの時計 ----------------------------------------------
$b = New-Canvas
Draw-Disc $b 8 8 6.0 $DARK_D
Draw-Disc $b 8 8 5.2 $DARK
Draw-Disc $b 8 8 4.4 $CYAN_D
Draw-Disc $b 8 8 3.6 $CYAN
Px $b 8 5 $DARK_D; Px $b 8 6 $DARK_D
Px $b 8 7 $DARK_D; Px $b 9 8 $DARK_D; Px $b 10 8 $DARK_D
Px $b 8 8 $GOLD_L
Px $b 8 1 $GOLD; Px $b 8 14 $GOLD_D
Px $b 6 3 $CYAN_L; Px $b 5 4 $CYAN_L
Add-Outline $b $OUTLINE
Save-Relic $b 'nightclock'

# 15. magnet 磁力の石 --------------------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 4 12 11 $STONE_D
Fill-Rect $b 4 5 11 10 $STONE
Fill-Rect $b 4 5 11 5 $STONE_L
Fill-Rect $b 6 6 9 9 $RED_D
Fill-Rect $b 7 7 8 8 $RED
Px $b 7 6 $GOLD; Px $b 8 9 $GOLD_D
Draw-Line $b 2 2 2 13 $CYAN_D
Draw-Line $b 13 2 13 13 $CYAN_D
Px $b 2 2 $CYAN_L; Px $b 13 2 $CYAN_L
Px $b 2 13 $CYAN; Px $b 13 13 $CYAN
Add-Outline $b $OUTLINE
Save-Relic $b 'magnet'

# 16. shrinkstar 剝蝕矮星 ----------------------------------------------------
$b = New-Canvas
Draw-Pattern $b @(
  '................',
  '.......##.......',
  '.......@@.......',
  '......#@@#......',
  '......#@@#......',
  '.....#@@@@#.....',
  '#####@%@@%@#####',
  '#@@@@%@@@@%@@@@#',
  '#####@%@@%@#####',
  '.....#@@@@#.....',
  '......#@@#......',
  '......#@@#......',
  '.......@@.......',
  '.......##.......',
  '................',
  '................'
) @{ '#' = $GREEN_D; '@' = $GREEN; '%' = $GREEN_L }
Px $b 8 12 $DARK_D; Px $b 7 13 $DARK_D
Add-Outline $b $OUTLINE
Save-Relic $b 'shrinkstar'

# 17. lightning 雷鳴の杖 -----------------------------------------------------
# 杖本体（木）＋先端に稲妻の意匠
$b = New-Canvas
Draw-Line $b 3 14 10 4 $WOOD
Draw-Line $b 4 14 11 4 $WOOD_D
Px $b 3 14 $GOLD_D; Px $b 4 14 $GOLD
Px $b 5 13 $WOOD_L; Px $b 6 12 $WOOD_L
Fill-Rect $b 10 1 13 4 $GOLD_D
Draw-Pattern $b @(
  '................',
  '..........####..',
  '.........#@@@#..',
  '.........#@%#...',
  '........#@%#....',
  '.......#@%#.....',
  '......#@%#......',
  '.....#@%#.......',
  '.....#@#........',
  '.....##.........',
  '................',
  '................',
  '................',
  '................',
  '................',
  '................'
) @{ '#' = $GOLD_D; '@' = $YELLOW; '%' = $GOLD_L }
Px $b 11 4 $GOLD; Px $b 12 3 $GOLD_L
Add-Outline $b $OUTLINE
Save-Relic $b 'lightning'

# 18. blink 次元の眼 ---------------------------------------------------------
$b = New-Canvas
Draw-Disc $b 8 8 5.6 $PURPLE_D
Draw-Disc $b 8 8 4.8 $PURPLE
Draw-Disc $b 8 8 3.4 $DARK
Draw-Disc $b 8 8 2.4 $PURPLE_L
Draw-Disc $b 8 8 1.2 $DARK_D
Px $b 7 7 $CYAN_L; Px $b 6 6 $CYAN_L
Px $b 11 5 $PURPLE_L; Px $b 5 11 $PURPLE_L
Add-Outline $b $OUTLINE
Save-Relic $b 'blink'

# 19. totem 死不知のトーテム -------------------------------------------------
$b = New-Canvas
Fill-Rect $b 5 1 10 4 $GOLD_D
Fill-Rect $b 6 2 9 3 $GOLD
Fill-Rect $b 4 5 11 12 $GOLD
Fill-Rect $b 5 5 10 11 $GOLD_L
Px $b 6 7 $DARK; Px $b 9 7 $DARK
Px $b 6 8 $CYAN; Px $b 9 8 $CYAN
Draw-Line $b 7 9 8 9 $GOLD_D
Draw-Line $b 6 10 9 10 $GOLD_D
Fill-Rect $b 4 13 11 14 $GOLD_D
Px $b 7 13 $GREEN; Px $b 8 13 $GREEN_L
Px $b 8 1 $GREEN_L
Add-Outline $b $OUTLINE
Save-Relic $b 'totem'

# 20. wither 凋零の頭骨 ------------------------------------------------------
$b = New-Canvas
Fill-Rect $b 3 3 12 11 $DARK_D
Fill-Rect $b 4 4 11 10 $DARK
Fill-Rect $b 4 4 11 4 $STONE_D
Fill-Rect $b 5 6 6 7 $STONE_L
Fill-Rect $b 9 6 10 7 $STONE_L
Px $b 5 7 $GREEN; Px $b 10 7 $GREEN
Px $b 7 8 $DARK_D; Px $b 8 8 $DARK_D
Px $b 7 9 $DARK_D; Px $b 8 9 $DARK_D
Fill-Rect $b 6 11 9 13 $STONE_D
Px $b 7 12 $STONE; Px $b 8 12 $STONE
Px $b 3 3 $GREEN_D; Px $b 12 3 $GREEN_D
Add-Outline $b $OUTLINE
Save-Relic $b 'wither'

# 21. bell 癒やしの鐘 --------------------------------------------------------
$b = New-Canvas
Fill-Rect $b 6 2 9 4 $GOLD_D
Fill-Rect $b 5 5 10 9 $GOLD
Fill-Rect $b 6 5 9 8 $GOLD_L
Fill-Rect $b 4 10 11 11 $GOLD_D
Fill-Rect $b 5 10 10 10 $GOLD
Px $b 7 12 $GREEN; Px $b 8 12 $GREEN_L
Px $b 7 13 $GREEN_D
Px $b 5 6 $GOLD_L; Px $b 5 7 $GOLD_L
Px $b 3 3 $GREEN_L; Px $b 12 3 $GREEN_L
Px $b 2 5 $GREEN_D; Px $b 13 5 $GREEN_D
Add-Outline $b $OUTLINE
Save-Relic $b 'bell'

# 22. dragonbreath 竜の吐息 --------------------------------------------------
# 口元から斜め左下へ伸びる紫のブレス。根元ほど濃く、先端ほど淡く
$b = New-Canvas
Draw-Pattern $b @(
  '................',
  '................',
  '................',
  '..........##....',
  '.........#@@#...',
  '........#@%%@#..',
  '.......#@%@@%@#.',
  '......#@%@@@@%@#',
  '.....#@%@@@@@%#.',
  '....#@%@@@@@%#..',
  '...#@%@@@@@%#...',
  '..#@%@@@@@%#....',
  '..#@@@@@@%#.....',
  '..##@@@@@#......',
  '....#####.......',
  '................'
) @{ '#' = $PURPLE_D; '@' = $PURPLE; '%' = $PURPLE_L }
Px $b 7 6 $PURPLE_L; Px $b 8 8 $PURPLE_L
Px $b 12 4 $CYAN_L
Add-Outline $b $OUTLINE
Save-Relic $b 'dragonbreath'

# 23. vision 暗視鏡 ----------------------------------------------------------
$b = New-Canvas
Fill-Rect $b 2 6 6 9 $DARK
Fill-Rect $b 9 6 13 9 $DARK
Fill-Rect $b 7 6 8 8 $DARK_D
Fill-Rect $b 3 7 5 8 $CYAN
Fill-Rect $b 10 7 12 8 $CYAN
Px $b 4 7 $CYAN_L; Px $b 11 7 $CYAN_L
Fill-Rect $b 3 4 5 5 $STONE_D
Fill-Rect $b 10 4 12 5 $STONE_D
Fill-Rect $b 2 10 6 11 $STONE_D
Fill-Rect $b 9 10 13 11 $STONE_D
Px $b 7 9 $GOLD; Px $b 8 9 $GOLD_D
Add-Outline $b $OUTLINE
Save-Relic $b 'vision'

Write-Output ""
Write-Output ("DONE: " + $script:made.Count + " textures")
$script:made -join ',' | Set-Content (Join-Path $OutDir 'relic-ids.txt') -Encoding UTF8
