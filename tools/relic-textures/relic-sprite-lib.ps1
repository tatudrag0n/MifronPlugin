# レリックテクスチャ用の共通ライブラリ。
#
# 方針: 16x16 を 1 ドットずつ手で設計した ASCII グリッドを描く。
# 図形プリミティブの組み合わせでは「安っぽく」なるため、
# シルエット・3段の陰影・左上光源をすべてグリッド上で明示する。
#
# パレット（1素材 = base / shadow / highlight の3段 + 輪郭）
#   .  透明        k  輪郭（暗い紫寄りの黒）
#   w W e  木      i I E  鉄      g G H  金      r R T  赤
#   b B N  青      c C M  シアン  p P Q  紫      v V x  緑
#   o O y  橙/黄   s S z  石      d D f  骨/白   l L m  革

# PowerShell の @{} はキーの大文字小文字を区別しないため、
# パレットは序数比較の Hashtable で持つ（'w' と 'W' を別キーにするため）。
$script:Palette = New-Object System.Collections.Hashtable ([System.StringComparer]::Ordinal)
$script:Palette.Add('k', @(42, 38, 48))
$script:Palette.Add('w', @(150, 106, 58));  $script:Palette.Add('W', @(106, 72, 36));   $script:Palette.Add('e', @(192, 146, 92))
$script:Palette.Add('i', @(206, 212, 222)); $script:Palette.Add('I', @(132, 140, 152)); $script:Palette.Add('E', @(244, 248, 255))
$script:Palette.Add('g', @(236, 190, 62));  $script:Palette.Add('G', @(176, 128, 26));  $script:Palette.Add('H', @(255, 234, 140))
$script:Palette.Add('r', @(216, 66, 56));   $script:Palette.Add('R', @(152, 36, 32));   $script:Palette.Add('T', @(250, 130, 90))
$script:Palette.Add('b', @(92, 146, 236));  $script:Palette.Add('B', @(48, 82, 168));   $script:Palette.Add('N', @(164, 206, 255))
$script:Palette.Add('c', @(108, 232, 232)); $script:Palette.Add('C', @(52, 168, 178));  $script:Palette.Add('M', @(190, 252, 252))
$script:Palette.Add('p', @(168, 100, 216)); $script:Palette.Add('P', @(108, 56, 152));  $script:Palette.Add('Q', @(214, 168, 246))
$script:Palette.Add('v', @(108, 196, 92));  $script:Palette.Add('V', @(60, 132, 64));   $script:Palette.Add('x', @(178, 234, 146))
$script:Palette.Add('o', @(246, 156, 48));  $script:Palette.Add('O', @(176, 96, 24));   $script:Palette.Add('y', @(252, 226, 96))
$script:Palette.Add('s', @(158, 158, 166)); $script:Palette.Add('S', @(112, 112, 122)); $script:Palette.Add('z', @(200, 200, 210))
$script:Palette.Add('d', @(236, 230, 208)); $script:Palette.Add('D', @(180, 172, 148)); $script:Palette.Add('f', @(252, 252, 255))
$script:Palette.Add('l', @(122, 82, 48));   $script:Palette.Add('L', @(82, 52, 28));    $script:Palette.Add('m', @(168, 122, 74))

function Get-SpriteColor([string]$ch) {
  if (-not $script:Palette.ContainsKey($ch)) { return $null }
  $rgb = $script:Palette[$ch]
  return [System.Drawing.Color]::FromArgb(255, $rgb[0], $rgb[1], $rgb[2])
}

# ASCII グリッドから Bitmap を作る。'.' は透明。
function New-SpriteBitmap([string[]]$rows) {
  if ($rows.Count -lt 16) {
    $rows = @($rows) + @(1..(16 - $rows.Count) | ForEach-Object { '................' })
  }
  $bmp = New-Object System.Drawing.Bitmap 16, 16
  for ($y = 0; $y -lt 16; $y++) {
    $row = [string]$rows[$y]
    if ($row.Length -lt 16) { $row = $row.PadRight(16, '.') }
    for ($x = 0; $x -lt 16; $x++) {
      $ch = [string]$row[$x]
      if ($ch -eq '.') { continue }
      $color = Get-SpriteColor $ch
      if ($null -ne $color) { $bmp.SetPixel($x, $y, $color) }
    }
  }
  return $bmp
}

# 全スプライトを描いて PNG で保存する。
function Export-Sprites($sprites, [string]$texOut) {
  if (-not (Test-Path $texOut)) { New-Item -ItemType Directory -Path $texOut -Force | Out-Null }
  $made = @()
  foreach ($id in ($sprites.Keys | Sort-Object)) {
    $bmp = New-SpriteBitmap $sprites[$id]
    $path = Join-Path $texOut "$id.png"
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $made += $id
    Write-Output "  -> $id.png"
  }
  Write-Output ("DONE: " + $made.Count + " textures")
  return $made
}
