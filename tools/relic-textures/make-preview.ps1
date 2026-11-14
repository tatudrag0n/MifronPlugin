<#
  レリックテクスチャのプレビューシートを作る（16x16 を8倍拡大して並べる）
#>
param(
  [string]$PackDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics'),
  [string]$OutFile = (Join-Path $PSScriptRoot '..\..\resourcepack\preview-relics.png'),
  [int]$Scale = 8,
  [int]$Cols = 6
)

Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $PackDir 'assets\mifron\textures\item\relic'
$files = Get-ChildItem $texDir -Filter '*.png' | Sort-Object Name
if ($files.Count -eq 0) { throw "no textures in $texDir" }

$cell = 16 * $Scale + 8
$rows = [Math]::Ceiling($files.Count / $Cols)
$pad = 8
$labelH = 14
$W = $Cols * $cell + $pad * 2
$H = $rows * ($cell + $labelH) + $pad * 2

$sheet = New-Object System.Drawing.Bitmap $W, $H
$g = [System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(255, 30, 32, 40))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font = New-Object System.Drawing.Font 'Consolas', 8

$i = 0
foreach ($f in $files) {
  $col = $i % $Cols
  $row = [Math]::Floor($i / $Cols)
  $x = $pad + $col * $cell
  $y = $pad + $row * ($cell + $labelH)

  # チェッカー背景
  $tile = 8
  for ($by = 0; $by -lt 16 * $Scale; $by += $tile) {
    for ($bx = 0; $bx -lt 16 * $Scale; $bx += $tile) {
      $c = if ((($bx / $tile) + ($by / $tile)) % 2 -eq 0) { [System.Drawing.Color]::FromArgb(255, 52, 54, 64) } else { [System.Drawing.Color]::FromArgb(255, 42, 44, 52) }
      $brush = New-Object System.Drawing.SolidBrush $c
      $g.FillRectangle($brush, $x + $bx, $y + $by, $tile, $tile)
      $brush.Dispose()
    }
  }

  $img = [System.Drawing.Image]::FromFile($f.FullName)
  $g.DrawImage($img, $x, $y, 16 * $Scale, 16 * $Scale)
  $img.Dispose()

  $label = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
  $g.DrawString($label, $font, [System.Drawing.Brushes]::WhiteSmoke, $x, $y + 16 * $Scale + 1)
  $i++
}

$g.Dispose()
$font.Dispose()
$outDir = Split-Path $OutFile -Parent
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$sheet.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()
Write-Output "preview -> $OutFile"
