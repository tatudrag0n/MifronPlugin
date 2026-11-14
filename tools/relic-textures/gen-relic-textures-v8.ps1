# レリックテクスチャ生成 v8
#
# v7 は「バニラ素材の色を変えただけ」で、名前が示すアイテムになっていなかった。
# v8 は 2 層構成にする:
#   下地 : バニラの実テクスチャを輝度保存で色替え（Mojang の陰影・ディテールを維持）
#   上描き: 名前が示すパーツを追加する（フック、ブレード、宝石、小屋、棘 など）
# これで「マイクラの質感」と「そのアイテムらしさ」を両立させる。

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

# --- 下地: バニラ素材 + 目標色 ------------------------------------------------
$map = [ordered]@{
  'hookshot'            = @{ src = 'item/fishing_rod';            hue = 32;  sat = 0.72; val = 0.80 }
  'jetpack'             = @{ src = 'item/chainmail_chestplate';   hue = 195; sat = 0.45; val = 0.86 }
  'skates'              = @{ src = 'item/iron_boots';             hue = 205; sat = 0.40; val = 0.90 }
  'resonance'           = @{ src = 'item/echo_shard';             hue = 182; sat = 0.62; val = 0.86 }
  'fireball'            = @{ src = 'item/fire_charge';            hue = 24;  sat = 0.78; val = 0.90 }
  'decayed_sword'       = @{ src = 'item/netherite_sword';        hue = 90;  sat = 0.18; val = 0.70 }
  'enchgrind'           = @{ src = 'block/grindstone_side';       hue = 285; sat = 0.30; val = 0.80 }
  'chain'               = @{ src = 'block/iron_chain';            hue = 210; sat = 0.10; val = 0.80 }
  'giantcore'           = @{ src = 'item/heart_pottery_sherd';    hue = 356; sat = 0.66; val = 0.82 }
  'seaswim'             = @{ src = 'item/heart_of_the_sea';       hue = 215; sat = 0.66; val = 0.85 }
  'dashfoot'            = @{ src = 'item/rabbit_foot';            hue = 40;  sat = 0.22; val = 0.92 }
  'reflect'             = @{ src = 'entity/shield/base';          hue = 220; sat = 0.55; val = 0.80; overlaybase = 'entity/shield/border' }
  'magnet'              = @{ src = 'block/lodestone_top';         hue = 0;   sat = 0.46; val = 0.76 }
  'shrinkstar'          = @{ src = 'item/nether_star';            hue = 110; sat = 0.60; val = 0.88 }
  'lightning'           = @{ src = 'block/lightning_rod';         hue = 48;  sat = 0.72; val = 0.88 }
  'blink'               = @{ src = 'item/ender_eye';              hue = 285; sat = 0.60; val = 0.82 }
  'totem'               = @{ src = 'item/totem_of_undying';       hue = 45;  sat = 0.68; val = 0.88 }
  'wither'              = @{ src = 'item/skull_pottery_sherd';    hue = 130; sat = 0.16; val = 0.58 }
  'bell'                = @{ src = 'item/bell';                   hue = 46;  sat = 0.60; val = 0.86 }
  'dragonbreath'        = @{ src = 'item/dragon_breath';          hue = 290; sat = 0.62; val = 0.85 }
  'vision'              = @{ src = 'item/spyglass';               hue = 190; sat = 0.35; val = 0.82 }
  'time_clock'          = @{ src = 'item/clock_00';               hue = 44;  sat = 0.58; val = 0.86 }
  'antigravity_mace'    = @{ src = 'item/mace';                   hue = 268; sat = 0.50; val = 0.80 }
  'ancient_slash_armor' = @{ src = 'item/netherite_chestplate';   hue = 356; sat = 0.52; val = 0.72 }
  'hellfire_book'       = @{ src = 'item/enchanted_book';         hue = 6;   sat = 0.68; val = 0.80 }
  'endless_grimoire'    = @{ src = 'item/book';                   hue = 225; sat = 0.58; val = 0.70 }
  'ancient_anvil'       = @{ src = 'block/chipped_anvil_top';     hue = 45;  sat = 0.28; val = 0.78 }
  'gaybolg'             = @{ src = 'item/trident';                hue = 356; sat = 0.62; val = 0.80 }
  'excalibur_replica'   = @{ src = 'item/diamond_sword';          hue = 48;  sat = 0.30; val = 0.96 }
  'infinite_bundle'     = @{ src = 'item/bundle';                 hue = 32;  sat = 0.55; val = 0.76 }
  'noahs_ark'           = @{ src = 'item/oak_boat';               hue = 30;  sat = 0.52; val = 0.80 }
  'artemis'             = @{ src = 'item/bow';                    hue = 45;  sat = 0.62; val = 0.86 }
}

# --- 上描き用パレット（Minecraft の色調に合わせた3段＋輪郭） -------------------
# 大文字小文字を区別するため序数比較の Hashtable を使う
$P = New-Object System.Collections.Hashtable ([System.StringComparer]::Ordinal)
$P.Add('k', @(38, 34, 44))
$P.Add('w', @(150, 106, 58));  $P.Add('W', @(104, 70, 36));   $P.Add('e', @(192, 146, 92))
$P.Add('i', @(206, 212, 222)); $P.Add('I', @(132, 140, 152)); $P.Add('E', @(246, 250, 255))
$P.Add('g', @(236, 190, 62));  $P.Add('G', @(176, 128, 26));  $P.Add('H', @(255, 234, 140))
$P.Add('r', @(200, 56, 48));   $P.Add('R', @(140, 30, 28));   $P.Add('T', @(248, 124, 84))
$P.Add('b', @(92, 146, 236));  $P.Add('B', @(48, 82, 168));   $P.Add('N', @(164, 206, 255))
$P.Add('c', @(108, 232, 232)); $P.Add('C', @(52, 168, 178));  $P.Add('M', @(190, 252, 252))
$P.Add('p', @(168, 100, 216)); $P.Add('P', @(108, 56, 152));  $P.Add('Q', @(214, 168, 246))
$P.Add('v', @(108, 196, 92));  $P.Add('V', @(60, 132, 64));   $P.Add('x', @(178, 234, 146))
$P.Add('o', @(246, 156, 48));  $P.Add('O', @(176, 96, 24));   $P.Add('y', @(252, 226, 96))
$P.Add('s', @(158, 158, 166)); $P.Add('S', @(112, 112, 122)); $P.Add('z', @(200, 200, 210))
$P.Add('d', @(236, 230, 208)); $P.Add('D', @(180, 172, 148)); $P.Add('f', @(252, 252, 255))

# 上描きの点を組み立てるヘルパ
function New-Overlay { return New-Object System.Collections.ArrayList }

function O-Put($list, [int]$x, [int]$y, [string]$ch) {
  if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
    [void]$list.Add(@($x, $y, $ch))
  }
}

function O-Line($list, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$ch) {
  $dx = [Math]::Abs($x1 - $x0); $dy = -[Math]::Abs($y1 - $y0)
  $sx = if ($x0 -lt $x1) { 1 } else { -1 }
  $sy = if ($y0 -lt $y1) { 1 } else { -1 }
  $err = $dx + $dy
  while ($true) {
    O-Put $list $x0 $y0 $ch
    if ($x0 -eq $x1 -and $y0 -eq $y1) { break }
    $e2 = 2 * $err
    if ($e2 -ge $dy) { $err += $dy; $x0 += $sx }
    if ($e2 -le $dx) { $err += $dx; $y0 += $sy }
  }
}

function O-Box($list, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$ch) {
  for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { O-Put $list $x $y $ch } }
}

function O-Ring($list, [double]$cx, [double]$cy, [double]$r, [string]$ch) {
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $d = [Math]::Sqrt([Math]::Pow($x + 0.5 - $cx, 2) + [Math]::Pow($y + 0.5 - $cy, 2))
    if ($d -le $r -and $d -ge $r - 1.0) { O-Put $list $x $y $ch }
  } }
}

function O-Disc($list, [double]$cx, [double]$cy, [double]$r, [string]$ch) {
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $d = [Math]::Sqrt([Math]::Pow($x + 0.5 - $cx, 2) + [Math]::Pow($y + 0.5 - $cy, 2))
    if ($d -le $r) { O-Put $list $x $y $ch }
  } }
}

# --- 上描き定義 ---------------------------------------------------------------
$overlays = @{}

# フックショット: 釣竿の先に鉤爪、柄に巻き紐
$o = New-Overlay
O-Line $o 12 1 14 4 'k'; O-Line $o 12 2 13 3 'i'; O-Line $o 14 4 11 6 'k'
O-Put $o 12 4 'E'; O-Put $o 13 2 'E'
O-Line $o 3 13 6 13 'k'; O-Put $o 4 12 'g'; O-Put $o 5 13 'g'
$overlays['hookshot'] = $o

# ジェットパック: 下部ノズル2本 + 炎 + 側面タンク
$o = New-Overlay
O-Box $o 4 12 5 13 'k'; O-Box $o 10 12 11 13 'k'
O-Put $o 4 13 'o'; O-Put $o 5 13 'y'; O-Put $o 10 13 'o'; O-Put $o 11 13 'y'
O-Box $o 3 5 3 10 'i'; O-Box $o 12 5 12 10 'i'
O-Put $o 6 6 'M'; O-Put $o 9 6 'M'
$overlays['jetpack'] = $o

# スケートブーツ: 靴底のブレード + かかとの留め具
$o = New-Overlay
O-Line $o 2 14 13 14 'c'; O-Line $o 2 15 13 15 'C'
O-Put $o 3 14 'M'; O-Put $o 8 14 'M'; O-Put $o 12 14 'M'
O-Box $o 3 12 4 13 'k'; O-Put $o 4 12 'i'
$overlays['skates'] = $o

# 残響の結晶: 内部の facet + 外側の共鳴輪
$o = New-Overlay
O-Line $o 5 10 10 5 'M'; O-Line $o 6 11 11 6 'c'
O-Ring $o 8 8 7.2 'C'
O-Put $o 8 8 'f'; O-Put $o 7 7 'M'
$overlays['resonance'] = $o

# ファイアーボール: 右上に火炎の尾
$o = New-Overlay
O-Line $o 12 4 14 1 'o'; O-Line $o 13 5 15 2 'y'; O-Put $o 13 3 'T'
O-Put $o 11 5 'O'; O-Put $o 12 6 'o'
$overlays['fireball'] = $o

# 朽ちた剣: 刀身の錆穴 + 刃こぼれ（暗い点で欠けを表現）
$o = New-Overlay
O-Put $o 7 7 'k'; O-Put $o 8 6 'k'; O-Put $o 9 5 'k'
O-Put $o 6 8 'V'; O-Put $o 10 6 'V'; O-Put $o 5 9 'V'
O-Put $o 11 3 'S'
$overlays['decayed_sword'] = $o

# 錬金の砥石: 紫色の火花
$o = New-Overlay
O-Put $o 3 3 'Q'; O-Put $o 12 2 'Q'; O-Put $o 13 12 'P'
O-Put $o 2 9 'Q'; O-Put $o 11 3 'p'; O-Put $o 4 12 'P'
$overlays['enchgrind'] = $o

# 拘束の鎖: 上端にシャックル（輪）
$o = New-Overlay
O-Ring $o 7.5 3 2.6 'k'; O-Ring $o 7.5 3 2.0 'i'
O-Put $o 7 3 'E'
$overlays['chain'] = $o

# 巨人の心臓: 金の血管と心室の陰り
$o = New-Overlay
O-Line $o 8 3 6 8 'g'; O-Line $o 6 8 9 12 'g'; O-Line $o 6 8 11 8 'g'
O-Put $o 5 5 'H'; O-Put $o 9 9 'G'; O-Put $o 10 5 'g'
O-Disc $o 5.5 6.5 1.4 'R'
$overlays['giantcore'] = $o

# 空に海写す蒼球: 波の弧と反射光
$o = New-Overlay
O-Line $o 3 9 6 7 'M'; O-Line $o 6 7 9 9 'M'
O-Line $o 8 11 11 9 'N'
O-Put $o 5 5 'f'; O-Put $o 6 5 'f'
$overlays['seaswim'] = $o

# バニーホップ: 跳躍の軌跡（3本の弧）
$o = New-Overlay
O-Put $o 1 11 'c'; O-Put $o 2 9 'c'; O-Put $o 3 7 'C'
O-Put $o 13 11 'c'; O-Put $o 14 9 'c'; O-Put $o 15 7 'C'
O-Put $o 6 12 'M'; O-Put $o 10 12 'M'
$overlays['dashfoot'] = $o

# 反射の守護盾: 中央のシアンの要石と反射弧
$o = New-Overlay
O-Disc $o 8 8 2.2 'k'; O-Disc $o 8 8 1.6 'c'; O-Put $o 8 8 'M'
O-Line $o 4 5 6 4 'N'; O-Line $o 11 11 12 12 'Q'
$overlays['reflect'] = $o

# 磁力の石: 赤い磁極と磁力線
$o = New-Overlay
O-Box $o 6 6 9 9 'k'; O-Box $o 7 7 8 8 'r'
O-Put $o 7 6 'T'; O-Put $o 8 9 'R'
O-Line $o 2 2 2 13 'C'; O-Line $o 13 2 13 13 'C'
O-Put $o 2 2 'M'; O-Put $o 13 2 'M'
$overlays['magnet'] = $o

# 剝蝕矮星: ひび割れと欠け
$o = New-Overlay
O-Line $o 8 3 7 7 'V'; O-Line $o 7 7 9 10 'V'; O-Line $o 9 10 6 13 'V'
O-Put $o 11 6 'k'; O-Put $o 5 8 'k'
$overlays['shrinkstar'] = $o

# 雷鳴の杖: 先端に稲妻
$o = New-Overlay
O-Line $o 11 2 8 5 'y'; O-Line $o 8 5 11 6 'y'; O-Line $o 11 6 8 9 'y'
O-Put $o 10 2 'f'; O-Put $o 9 5 'f'
$overlays['lightning'] = $o

# 次元の眼: 縦長の瞳孔と次元の裂け目
$o = New-Overlay
O-Box $o 7 6 8 9 'k'; O-Put $o 7 7 'P'; O-Put $o 8 8 'P'
O-Put $o 5 4 'Q'; O-Put $o 10 11 'Q'
$overlays['blink'] = $o

# 死不知のトーテム: 緑の眼光と古の紋
$o = New-Overlay
O-Put $o 6 6 'v'; O-Put $o 9 6 'v'; O-Put $o 6 5 'x'; O-Put $o 9 5 'x'
O-Line $o 7 9 8 9 'V'; O-Put $o 8 11 'g'
$overlays['totem'] = $o

# 凋零の頭骨: 緑の眼窩と亀裂
$o = New-Overlay
O-Box $o 4 5 6 7 'k'; O-Box $o 9 5 11 7 'k'
O-Put $o 5 6 'v'; O-Put $o 10 6 'v'
O-Line $o 7 3 8 6 'k'; O-Put $o 6 10 'k'; O-Put $o 9 10 'k'
$overlays['wither'] = $o

# 癒やしの鐘: 緑の癒やしの光
$o = New-Overlay
O-Put $o 3 3 'x'; O-Put $o 12 3 'x'; O-Put $o 2 6 'v'; O-Put $o 13 6 'v'
O-Put $o 7 12 'v'; O-Put $o 8 12 'x'; O-Put $o 8 13 'V'
$overlays['bell'] = $o

# 竜の吐息: 瓶から漏れる紫の煙
$o = New-Overlay
O-Put $o 4 4 'Q'; O-Put $o 3 2 'p'; O-Put $o 5 2 'P'
O-Put $o 11 10 'Q'; O-Put $o 12 8 'p'
$overlays['dragonbreath'] = $o

# 暗視鏡: レンズの反射と暗視の光
$o = New-Overlay
O-Put $o 5 6 'M'; O-Put $o 6 6 'M'; O-Put $o 9 6 'M'; O-Put $o 10 6 'M'
O-Put $o 5 9 'c'; O-Put $o 10 9 'c'
$overlays['vision'] = $o

# ときを刻む時計: 三日月と針
$o = New-Overlay
O-Disc $o 11 5 2.0 'B'; O-Put $o 11 4 'N'
O-Line $o 8 8 8 4 'k'; O-Line $o 8 8 11 8 'k'
O-Put $o 8 8 'g'
$overlays['time_clock'] = $o

# 反重力戦鎚: 紫の核と浮遊する破片
$o = New-Overlay
O-Disc $o 8 5 1.8 'P'; O-Put $o 8 5 'Q'; O-Put $o 7 4 'Q'
O-Put $o 2 3 'Q'; O-Put $o 13 3 'Q'; O-Put $o 3 10 'P'; O-Put $o 12 10 'p'
$overlays['antigravity_mace'] = $o

# 古代の斬鎧: 胸に赤い斬撃の痕
$o = New-Overlay
O-Line $o 5 5 10 10 'r'; O-Line $o 6 5 11 10 'R'
O-Put $o 5 5 'T'; O-Put $o 10 10 'T'
O-Put $o 4 8 'R'; O-Put $o 11 7 'R'
$overlays['ancient_slash_armor'] = $o

# 煉獄の書: 表紙の炎の紋
$o = New-Overlay
O-Line $o 8 4 7 7 'y'; O-Line $o 7 7 9 8 'y'; O-Line $o 9 8 8 11 'o'
O-Put $o 8 6 'f'; O-Put $o 6 9 'o'; O-Put $o 10 6 'o'
$overlays['hellfire_book'] = $o

# 無尽の魔導書: 星のルーン
$o = New-Overlay
O-Put $o 6 5 'N'; O-Put $o 10 5 'N'; O-Put $o 8 7 'M'
O-Put $o 5 9 'N'; O-Put $o 11 9 'N'; O-Put $o 8 10 'M'
O-Line $o 8 4 8 6 'N'
$overlays['endless_grimoire'] = $o

# 古代の金床: 金の鋲と縁取り
$o = New-Overlay
O-Put $o 4 4 'g'; O-Put $o 11 4 'g'; O-Put $o 4 9 'G'; O-Put $o 11 9 'G'
O-Line $o 5 12 10 12 'g'; O-Put $o 7 12 'H'
$overlays['ancient_anvil'] = $o

# ゲイ・ボルグ: 穂先の棘
$o = New-Overlay
O-Put $o 11 2 'T'; O-Put $o 13 3 'T'; O-Put $o 12 4 'R'
O-Put $o 10 3 'r'; O-Put $o 13 5 'R'
O-Line $o 9 6 10 6 'r'
$overlays['gaybolg'] = $o

# エクスカリバーのレプリカ: 金の鍔と聖光
$o = New-Overlay
O-Line $o 4 9 8 9 'g'; O-Put $o 5 8 'H'; O-Put $o 7 8 'H'
O-Put $o 12 3 'f'; O-Put $o 13 4 'f'; O-Put $o 11 2 'M'
$overlays['excalibur_replica'] = $o

# 無限のバンドル: 無限の輝き
$o = New-Overlay
O-Ring $o 8 8 3.2 'g'; O-Put $o 6 8 'H'; O-Put $o 10 8 'H'
O-Put $o 8 6 'H'; O-Put $o 8 10 'G'
$overlays['infinite_bundle'] = $o

# ノアの方舟: 小屋とマスト
$o = New-Overlay
O-Box $o 6 5 10 7 'k'; O-Box $o 7 5 9 6 'w'
O-Put $o 9 5 'e'
O-Line $o 8 2 8 4 'W'; O-Put $o 9 2 'd'
$overlays['noahs_ark'] = $o

# アルテミス: 番えた矢と三日月の紋
$o = New-Overlay
O-Line $o 3 8 12 8 'w'; O-Put $o 13 8 'c'; O-Put $o 12 7 'c'; O-Put $o 12 9 'c'
O-Put $o 2 7 'd'; O-Put $o 2 9 'd'
O-Disc $o 12 3 1.5 'H'
$overlays['artemis'] = $o

# --- 下地の読み込みと色替え ------------------------------------------------
$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)

function Get-Vanilla([string]$path) {
  $e = $zip.GetEntry("assets/minecraft/textures/$path.png")
  if (-not $e) { return $null }
  $ms = New-Object System.IO.MemoryStream
  $s = $e.Open(); $s.CopyTo($ms); $s.Close(); $ms.Position = 0
  return [System.Drawing.Bitmap]::FromStream($ms)
}

function Fit-16($src) {
  $dst = New-Object System.Drawing.Bitmap 16, 16
  $g = [System.Drawing.Graphics]::FromImage($dst)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
  $minX = $src.Width; $minY = $src.Height; $maxX = -1; $maxY = -1
  for ($y = 0; $y -lt $src.Height; $y++) { for ($x = 0; $x -lt $src.Width; $x++) {
    if ($src.GetPixel($x, $y).A -eq 0) { continue }
    if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
    if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
  } }
  if ($maxX -lt $minX) { $g.Dispose(); return $dst }
  $w = $maxX - $minX + 1; $h = $maxY - $minY + 1
  $side = [Math]::Max($w, $h)
  $cx = [Math]::Max(0, [Math]::Min($src.Width - $side, $minX + [int](($w - $side) / 2)))
  $cy = [Math]::Max(0, [Math]::Min($src.Height - $side, $minY + [int](($h - $side) / 2)))
  $rect = New-Object System.Drawing.Rectangle $cx, $cy, $side, $side
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

function Recolor($bmp, [double]$hueDeg, [double]$satTarget, [double]$valTarget) {
  $lums = @()
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $lums += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
  } }
  if ($lums.Count -eq 0) { return $bmp }
  $sorted = @($lums | Sort-Object)
  $lo = $sorted[0]; $hi = $sorted[$sorted.Count - 1]
  if ($hi - $lo -lt 0.05) { $lo = 0.0; $hi = 1.0 }

  $out = New-Object System.Drawing.Bitmap 16, 16
  for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $c = $bmp.GetPixel($x, $y); if ($c.A -eq 0) { continue }
    $l = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
    $t = [Math]::Max(0.0, [Math]::Min(1.0, ($l - $lo) / ($hi - $lo)))
    $h = $hueDeg + (0.5 - $t) * 26.0
    if ($h -ge 360) { $h -= 360 }; if ($h -lt 0) { $h += 360 }
    $s = [Math]::Min(0.92, $satTarget * (1.05 - 0.30 * $t))
    $v = [Math]::Min(1.0, $valTarget * (0.58 + 0.62 * $t))
    $rgb = Hsv-To-Rgb $h $s $v
    $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, [int]$rgb[0], [int]$rgb[1], [int]$rgb[2]))
  } }
  return $out
}

function Paint-Overlay($bmp, $points) {
  foreach ($p in $points) {
    $rgb = $script:P[[string]$p[2]]
    if ($null -eq $rgb) { continue }
    $bmp.SetPixel([int]$p[0], [int]$p[1], [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2]))
  }
}

Write-Output ("Building " + $map.Count + " relic textures (vanilla base + drawn details) -> $texOut")
$missing = @()
foreach ($id in $map.Keys) {
  $spec = $map[$id]
  $src = Get-Vanilla $spec.src
  if ($null -eq $src) { $missing += "$id ($($spec.src))"; continue }
  $base = Fit-16 $src
  $src.Dispose()

  if ($spec.ContainsKey('overlaybase')) {
    $over = Get-Vanilla $spec.overlaybase
    if ($null -ne $over) {
      $overFit = Fit-16 $over
      $over.Dispose()
      $g = [System.Drawing.Graphics]::FromImage($base)
      $g.DrawImage($overFit, 0, 0, 16, 16)
      $g.Dispose()
      $overFit.Dispose()
    }
  }

  $colored = Recolor $base $spec.hue $spec.sat $spec.val
  $base.Dispose()

  if ($overlays.ContainsKey($id)) { Paint-Overlay $colored $overlays[$id] }

  $colored.Save((Join-Path $texOut "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $colored.Dispose()
  Write-Output "  -> $id.png"
}
$zip.Dispose()

$keep = @($map.Keys)
Get-ChildItem $texOut -Filter '*.png' | ForEach-Object {
  if ($keep -notcontains $_.BaseName) { Remove-Item $_.FullName -Force }
}

Write-Output ""
Write-Output ("DONE: " + ($map.Count - $missing.Count) + " textures")
if ($missing.Count -gt 0) { Write-Output ("MISSING: " + ($missing -join '; ')); exit 1 }

