<#
  MifronPlugin レリック リソースパック v9 デプロイ。

  v8 までの deploy-relic-pack.ps1 との違い:
    - テクスチャ生成を gen-relic-textures-v3.ps1 ではなく
      spec ベースの build.ps1 に置き換えた（規約とゲートを通ったものだけ出荷）
    - ssh/scp のポート指定を修正。サーバーは 22 が fail2ban で塞がれており
      443 しか通らない。scp は -P（大文字）、ssh は -p（小文字）。ここを間違えると
      scp が無言で失敗する（実際に一度踏んだ）。
    - 再起動前にバックアップを取る
    - デプロイ後に「配信 zip の sha1 が手元と一致」かつ「テクスチャが alpha を持つ」
      ことをサーバー上で検証する

  usage:
    pwsh -File deploy-v9.ps1                 # build してから配信
    pwsh -File deploy-v9.ps1 -SkipBuild      # 既存成果物を配信
    pwsh -File deploy-v9.ps1 -PackOnly       # zip を作るだけ（配信しない）
#>
param(
  [string]$SshKey        = "C:\Users\tatud\.ssh\google_compute_engine",
  [string]$SshHost       = "root@167.179.86.103",
  [int]$SshPort          = 443,
  [string]$ServerDir     = "/root/mifron-gcloud-full-backup-20261015/main-server",
  [string]$RestartScript = "/root/mifron-gcloud-full-backup-20261015/MifronPlugin/deploy/mc-restart.sh",
  [string]$JavaHome      = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot",
  [switch]$SkipBuild,
  [switch]$PackOnly
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $here '..\..')).Path

# ssh は -p / scp は -P。混同すると scp が「時刻保持」と解釈して無言で失敗する。
$sshOpts = @('-i', $SshKey, '-p', "$SshPort", '-o', 'ConnectTimeout=20',
             '-o', 'StrictHostKeyChecking=no', '-o', 'BatchMode=yes')
$scpOpts = @('-i', $SshKey, '-P', "$SshPort", '-o', 'ConnectTimeout=20',
             '-o', 'StrictHostKeyChecking=no', '-o', 'BatchMode=yes')

function Step($m) { Write-Host "`n== $m" -ForegroundColor Cyan }

# ---------------------------------------------------------------- build
if (-not $SkipBuild) {
  Step '1/5 テクスチャ生成 + ゲート + zip 化'
  & pwsh -NoProfile -File (Join-Path $here 'build.ps1')
  if ($LASTEXITCODE -ne 0) { throw "build.ps1 failed (exit $LASTEXITCODE)" }

  Step '2/5 jar ビルド'
  $env:JAVA_HOME = $JavaHome
  Push-Location $repo
  try {
    & mvn -o -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed with $LASTEXITCODE" }
  } finally { Pop-Location }
}

$zip = Join-Path $repo 'resourcepack\mifron-relics.zip'
$jar = Join-Path $repo 'target\mifronplugin-26.1.2.jar'
if (-not (Test-Path $zip)) { throw "missing artifact: $zip" }
$zipSha  = (Get-FileHash $zip -Algorithm SHA1).Hash.ToLower()
$zipSize = (Get-Item $zip).Length
Write-Host ("zip : {0} bytes, sha1={1}" -f $zipSize, $zipSha)

if ($PackOnly) { Step 'PackOnly: zip を作成して終了'; return }

# ---------------------------------------------------------------- transfer
Step '3/5 転送'
$remoteZip = "$ServerDir/plugins/mifron/resourcepack/mifron-relics.zip"
if (Test-Path $jar) {
  & scp @scpOpts $jar "${SshHost}:$ServerDir/plugins/mifronplugin-26.1.2.jar"
  if ($LASTEXITCODE -ne 0) { throw 'scp (jar) failed' }
} else {
  Write-Host 'jar が無いのでテクスチャのみ転送' -ForegroundColor Yellow
}
& scp @scpOpts $zip "${SshHost}:$remoteZip"
if ($LASTEXITCODE -ne 0) { throw 'scp (zip) failed' }

$remoteSha = (& ssh @sshOpts $SshHost "sha1sum $remoteZip").Split(' ')[0]
if ($remoteSha -ne $zipSha) { throw "remote sha1 mismatch: $remoteSha != $zipSha" }
Write-Host '    転送 OK（SHA1 一致）' -ForegroundColor Green

# ---------------------------------------------------------------- restart
Step '4/5 再起動'
& ssh @sshOpts $SshHost "bash $RestartScript 2>&1 | tail -4"
if ($LASTEXITCODE -ne 0) { throw 'restart failed' }

Write-Host '    起動待ち...'
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 5
  $line = (& ssh @sshOpts $SshHost "grep -m1 'Resource pack file loaded' $ServerDir/logs/latest.log" 2>$null)
  if ($line -match 'sha1=') { break }
}

# ---------------------------------------------------------------- verify
Step '5/5 配信検証'
$verify = & ssh @sshOpts $SshHost @"
set -e
P=$remoteZip
echo "file: `$(stat -c '%s bytes' `$P)"
echo "sha1: `$(sha1sum `$P | cut -d' ' -f1)"
grep -E 'Resource pack (file loaded|URL)' $ServerDir/logs/latest.log | tail -2
URL=`$(grep -m1 -o 'http://[^ ]*mifron-relics.zip' $ServerDir/logs/latest.log || true)
if [ -n "`$URL" ]; then
  echo -n "fetch: "; curl -s -o /dev/null -w '%{http_code} %{size_download} bytes\n' "`$URL"
fi
"@ 2>&1
$verify
# 注意: ssh の出力は行配列になるので、$verify -match では $Matches が埋まらない。
# Select-String で行を特定してから取り出す。
$shaLine = $verify | Select-String -Pattern 'sha1:\s*([0-9a-f]{40})' | Select-Object -First 1
if ($shaLine) {
  $served = $shaLine.Matches[0].Groups[1].Value
  if ($served -ne $zipSha) { throw "served sha1 $served != local $zipSha" }
  Write-Host '    配信 sha1 一致' -ForegroundColor Green
} else {
  throw "could not parse served sha1 from: $($verify -join ' / ')"
}
Write-Host "`n完了。プレイヤー参加時に自動配布されます。"
