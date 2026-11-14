# レリックテクスチャを手設計スプライトから生成する（v5）。
#
# 図形プリミティブを組み合わせる方式（v3/v4）は「安っぽい」仕上がりになったため、
# 16x16 を 1 ドットずつ ASCII グリッドで設計する方式に切り替えた。
# 陰影・光源・輪郭はすべてグリッド上で明示しているので、後処理は行わない。

param(
  [string]$OutDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

. (Join-Path $PSScriptRoot 'relic-sprite-lib.ps1')
. (Join-Path $PSScriptRoot 'sprites-part1.ps1')
. (Join-Path $PSScriptRoot 'sprites-part2.ps1')

$texOut = Join-Path $OutDir 'assets\mifron\textures\item\relic'

$all = @{}
foreach ($k in $Sprites1.Keys) { $all[$k] = $Sprites1[$k] }
foreach ($k in $Sprites2.Keys) { $all[$k] = $Sprites2[$k] }

Write-Output ("Drawing " + $all.Count + " hand-authored relic sprites -> $texOut")

# 旧方式で作られた PNG を消してから描く（同名は上書きされるが、
# 廃止したレリックの残骸がパックに混ざらないようにする）
$keep = @($all.Keys)
if (Test-Path $texOut) {
  Get-ChildItem $texOut -Filter '*.png' | ForEach-Object {
    if ($keep -notcontains $_.BaseName) { Remove-Item $_.FullName -Force }
  }
}

$made = Export-Sprites $all $texOut
Write-Output ("TOTAL: " + $made.Count)
