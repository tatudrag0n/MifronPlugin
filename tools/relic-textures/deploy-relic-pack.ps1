<#
  MifronPlugin のレリック リソースパックを本番サーバーへ一発デプロイする。

  やること:
    1. テクスチャ生成（v3: 名前一致の形 + バニラ規範）
    2. pack.mcmeta / item_model / モデル定義の再生成
    3. zip 化（ディレクトリ構造の検証つき）
    4. jar のビルド
    5. jar と zip をサーバーへ転送（SHA1 照合）
    6. mc-restart.sh で再起動
    7. 実際に配信されている URL と HTTP 200 を確認

  使い方:
    pwsh -File deploy-relic-pack.ps1               # 全部やる
    pwsh -File deploy-relic-pack.ps1 -SkipBuild    # 転送と再起動だけ
#>
param(
  [string]$SshKey   = "C:\Users\tatud\.ssh\google_compute_engine",
  [string]$SshHost  = "root@167.179.86.103",
  [string]$ServerDir = "/root/mifron-gcloud-full-backup-20261015/main-server",
  [string]$RestartScript = "/root/mifron-gcloud-full-backup-20261015/MifronPlugin/deploy/mc-restart.sh",
  [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot",
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sshOpts = @('-i', $SshKey, '-o', 'ConnectTimeout=20', '-o', 'StrictHostKeyChecking=no', '-o', 'BatchMode=yes')

if (-not $SkipBuild) {
  Write-Output '==> 1/3 テクスチャ生成'
  & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'gen-relic-textures-v3.ps1')

  Write-Output '==> 2/3 パック定義 + zip 化'
  & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'build-resourcepack.ps1')
  & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'pack-resourcepack.ps1')

  Write-Output '==> 3/3 jar ビルド'
  $env:JAVA_HOME = $JavaHome
  Push-Location $repo
  try {
    & mvn -o -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed with $LASTEXITCODE" }
  } finally { Pop-Location }
}

$zip = Join-Path $repo 'resourcepack\mifron-relics.zip'
$jar = Join-Path $repo 'target\mifronplugin-26.1.2.jar'
foreach ($p in @($zip, $jar)) { if (-not (Test-Path $p)) { throw "missing artifact: $p" } }

$zipSha = (Get-FileHash $zip -Algorithm SHA1).Hash.ToLower()
Write-Output ("zip  : {0} bytes, sha1={1}" -f (Get-Item $zip).Length, $zipSha)

Write-Output '==> 転送'
& scp @sshOpts $jar "${SshHost}:$ServerDir/plugins/mifronplugin-26.1.2.jar"
if ($LASTEXITCODE -ne 0) { throw 'scp (jar) failed' }
& scp @sshOpts $zip "${SshHost}:$ServerDir/plugins/mifron/resourcepack/mifron-relics.zip"
if ($LASTEXITCODE -ne 0) { throw 'scp (zip) failed' }

$remoteSha = (& ssh @sshOpts $SshHost "sha1sum $ServerDir/plugins/mifron/resourcepack/mifron-relics.zip").Split(' ')[0]
if ($remoteSha -ne $zipSha) { throw "remote sha1 mismatch: $remoteSha != $zipSha" }
Write-Output '    転送 OK（SHA1 一致）'

Write-Output '==> 再起動'
& ssh @sshOpts $SshHost "bash $RestartScript 2>&1 | tail -4"
if ($LASTEXITCODE -ne 0) { throw 'restart failed' }

Write-Output '==> 起動待ち'
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 5
  $line = (& ssh @sshOpts $SshHost "grep -m1 'Resource pack URL' $ServerDir/logs/latest.log" 2>$null)
  if ($line -match 'http://\S+') { break }
}

Write-Output '==> 配信確認'
& ssh @sshOpts $SshHost @"
grep -E 'Resource pack (file loaded|URL)' $ServerDir/logs/latest.log | tail -3
URL=`$(grep -m1 -o 'http://[^ ]*mifron-relics.zip' $ServerDir/logs/latest.log)
echo -n "fetch: "
curl -s -o /dev/null -w '%{http_code} %{size_download} bytes\n' "`$URL"
"@

Write-Output ''
Write-Output '完了。プレイヤーが参加すると自動で送られます。'
