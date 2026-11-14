<#
  リソースパックを zip にまとめる。
  - pack.mcmeta / assets を zip 直下に置く（フォルダを 1 段挟まない）
  - ディレクトリ構造を必ず保持する（潰れるとクライアントが読めない）
#>
param(
  [string]$PackDir = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics'),
  [string]$OutFile = (Join-Path $PSScriptRoot '..\..\resourcepack\mifron-relics.zip')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# 相対パスを安全に取るため、必ず正規化した絶対パスを使う。
# 以前は $PackDir に '..\..' が残ったまま Substring していたため、
# 全ファイルがルート直下に潰れた zip ができていた。
$PackRoot = (Resolve-Path -LiteralPath $PackDir).Path.TrimEnd('\', '/')
if (-not (Test-Path (Join-Path $PackRoot 'pack.mcmeta'))) { throw "pack.mcmeta not found in $PackRoot" }

if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

$zip = [System.IO.Compression.ZipFile]::Open($OutFile, [System.IO.Compression.ZipArchiveMode]::Create)
try {
  $mcmeta = Join-Path $PackRoot 'pack.mcmeta'
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
    $zip, $mcmeta, 'pack.mcmeta', [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null

  $files = Get-ChildItem (Join-Path $PackRoot 'assets') -Recurse -File
  foreach ($f in $files) {
    $rel = [System.IO.Path]::GetRelativePath($PackRoot, $f.FullName) -replace '\\', '/'
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
      $zip, $f.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
  }
  Write-Output ("packed {0} files" -f ($files.Count + 1))
} finally {
  $zip.Dispose()
}

$st = Get-Item $OutFile
Write-Output ("zip -> {0} ({1} bytes)" -f $st.FullName, $st.Length)

# 検証: 構造が正しいか（これが崩れるとゲーム側で一切読まれない）
$check = [System.IO.Compression.ZipFile]::OpenRead($OutFile)
try {
  $names = $check.Entries | ForEach-Object { $_.FullName }
  if ($names -notcontains 'pack.mcmeta') { throw 'pack.mcmeta is not at the zip root' }

  $markers = @(
    'assets/mifron/items/relic/hookshot.json',
    'assets/mifron/models/item/relic/hookshot.json',
    'assets/mifron/textures/item/relic/hookshot.png'
  )
  foreach ($m in $markers) {
    if ($names -notcontains $m) { throw "missing expected entry: $m" }
  }
  $flat = @($names | Where-Object { $_ -notmatch '/' })
  if ($flat.Count -ne 1) {
    throw ("files lost their directory structure at the zip root: " + (($flat | Select-Object -First 5) -join ', '))
  }
  $texCount = @($names | Where-Object { $_ -match '^assets/mifron/textures/item/relic/.*\.png$' }).Count
  if ($texCount -ne 23) { throw "expected 23 relic textures, found $texCount" }

  Write-Output ("entries: {0}, textures: {1}, structure: OK" -f $names.Count, $texCount)
} finally {
  $check.Dispose()
}
