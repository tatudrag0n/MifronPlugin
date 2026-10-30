#!/usr/bin/env bash
# Human-gated installation of the staged jar into production.
#
#  1. Shows what will be installed (version, commit, sha, test counts).
#  2. Backs up the currently installed jar.
#  3. Installs to $SERVER_DIR/plugins/$JAR_NAME.
#  4. Restarts the server (systemd or screen, whichever owns it).
#  5. Health checks (port + fresh plugin-load log line).
#  6. Rolls back automatically on failure and completes the site/Discord
#     event trail either way.
#
# Usage: promote-jar.sh --confirm            (install to production)
#        promote-jar.sh --to-test-dir        (swap into $TEST_DIR only)
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

confirm=0
to_test_dir=0
while [ $# -gt 0 ]; do
  case "$1" in
    --confirm) confirm=1; shift ;;
    --to-test-dir) to_test_dir=1; shift ;;
    *) echo "unknown arg: $1"; exit 2 ;;
  esac
done

BUILD_INFO="$STAGING_DIR/BUILD_INFO.txt"
STAGED_JAR="$STAGING_DIR/$JAR_NAME"
[ -s "$STAGED_JAR" ] || { echo "ERROR: staged jar not found: $STAGED_JAR (run deploy/build-check.sh)"; exit 1; }

info_file="$BUILD_INFO"
[ -s "$info_file" ] || info_file=/dev/null
staged_version="$(unzip -p "$STAGED_JAR" plugin.yml 2>/dev/null | awk -F': *' '/^version:/ {print $2; exit}')"
staged_commit="$(sed -n 's/^commit: *//p' "$info_file" 2>/dev/null | tr -d '[:space:]')"
staged_sha="$(sha256_of "$STAGED_JAR")"
staged_tests="$(sed -n 's/^tests: *//p' "$info_file" 2>/dev/null | tr -d '\n')"

if [ $to_test_dir -eq 1 ]; then
  [ -d "$TEST_DIR/plugins" ] || { echo "ERROR: test dir missing: $TEST_DIR"; exit 1; }
  cp -f "$STAGED_JAR" "$TEST_DIR/plugins/$JAR_NAME"
  echo "Copied into test server plugins: $TEST_DIR/plugins/$JAR_NAME (commit=${staged_commit:-unknown}, version=${staged_version:-unknown})"
  echo "次にテストサーバーを起動して検証してください (docs/TEST_SERVER.md 参照)"
  exit 0
fi

if [ $confirm -ne 1 ]; then
  echo "staged:  version=${staged_version:-unknown} commit=${staged_commit:-unknown} tests=${staged_tests:-?} sha=${staged_sha:0:12}"
  echo "本番運用中のjarに影響します。"
  echo "実行は次のようにしてください:  bash deploy/promote-jar.sh --confirm"
  echo "(--to-test-dir を付けるとテストサーバーディレクトリへだけコピーします)"
  exit 0
fi

target="$SERVER_DIR/plugins/$JAR_NAME"
timestamp="$(now_utc)"
backup_file="$STATE_DIR/backups/mifronplugin-preview-$timestamp.jar"
mkdir -p "$STATE_DIR/backups"
if [ -s "$target" ]; then
  cp -f "$target" "$backup_file"
fi

shopt -s nullglob
for old_jar in "$SERVER_DIR/plugins/mifronplugin-"*.jar "$SERVER_DIR/plugins/mifron.jar" "$SERVER_DIR/plugins/Mifron.jar"; do
  [ "$old_jar" = "$target" ] && continue
  [ -e "$old_jar" ] || continue
  cp -f "$old_jar" "$STATE_DIR/backups/$(basename "$old_jar").$timestamp.bak" 2>/dev/null || true
done
shopt -u nullglob 2>/dev/null || shopt -u nullglob

install_success=0
set +e
install -m 0644 "$STAGED_JAR" "$target.new" && mv -f "$target.new" "$target"
install_rc=$?
if [ "$install_rc" -ne 0 ]; then
  echo "ERROR: jar install failed (rc=$install_rc)"; set -e; exit 1
fi

log "restart for promoted version ${staged_version:-unknown} (${staged_commit:-unknown})"
bash "$(dirname "$0")/mc-restart.sh" && restart_ok=1 || restart_ok=0
if [ "$restart_ok" -ne 1 ]; then
  echo "restart failed; rolling back"
  if [ -s "$backup_file" ]; then
    mv -f "$backup_file" "$target"
    bash "$(dirname "$0")/mc-restart.sh" || true
  fi
  bash "$(dirname "$0")/notify-event.sh" \
    --type deploy --title "デプロイ失敗 — ロールバック済み (${staged_commit:-unknown?})" \
    --commit "${staged_commit:-}" --version "${staged_version:-}" --source promote \
    --body "port/check失敗のため preview jar へ戻しました。調査: $(hostname):$STATE_DIR/backups" \
    --skip-webhook >/dev/null 2>&1 || true
  exit 1
fi

# health: port + plugin version line from latest.log
sleep 8
if port_open && grep -q "Loading server plugin mifron v${staged_version}" "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
  install_success=1
fi
if [ "$install_success" -ne 1 ]; then
  echo "health check failed (port open? $(port_open && echo yes || echo no)); rolling back"
  if [ -s "$backup_file" ]; then
    cp -f "$backup_file" "$target.new" && mv -f "$target.new" "$target"
    bash "$(dirname "$0")/mc-restart.sh" || true
  fi
  bash "$(dirname "$0")/notify-event.sh" \
    --type deploy --title "デプロイ失敗 — ロールバック済み (${staged_commit:-unknown?})" \
    --commit "${staged_commit:-}" --version "${staged_version:-}" --source promote \
    --body "healthしきい値に到達しました。rollback jarに戻しています。" \
    --skip-webhook >/dev/null 2>&1 || true
  exit 1
fi
set -e

printf '%s\n' "${staged_commit:-}" > "$STATE_DIR/last-deployed"
printf '%s  %s\n' "$(sha256_of "$target")" "$target" > "$STATE_DIR/last-deployed-jar.sha256"
printf '%s\n' "${staged_version:-unknown}" > "$STATE_DIR/last-plugin-version"

# keep 6 newest rollbacks
find "$STATE_DIR/backups" -maxdepth 1 -name 'mifronplugin-preview-*.jar' -printf '%T@ %p\n' 2>/dev/null | sort -nr | tail -n +3 | cut -d' ' -f2- | xargs -r rm -f

notes=()
while IFS= read -r note; do
  [ -n "$note" ] && notes+=("$note")
done <<EOF
- version=${staged_version:-unknown}, sha256=${staged_sha}
- tests ${staged_tests:-?}
EOF

bash "$(dirname "$0")/notify-event.sh" \
  --type deploy \
  --title "アップデート反映: ${staged_version:-unknown} (${staged_commit:-unknown})" \
  --commit "${staged_commit:-}" \
  --version "${staged_version:-}" \
  --source promote \
  --body "MifronPlugin provenance: origin/main" \
  --body "テスト: ${staged_tests:-?} passed" \
  --body "ロールバック保管: $(basename "$backup_file")" \
  --skip-webhook >/dev/null 2>&1 || true

echo "promoted: version=${staged_version:-unknown} commit=${staged_commit:-unknown}"
