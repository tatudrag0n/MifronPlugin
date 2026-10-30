#!/usr/bin/env bash
# Automatic Geyser-Spigot updater with a safety net.
#
#   - discovers the ACTIVE Geyser jar (including non-standard names such as
#     "Geyser-Spigot (2).jar"), or falls back to the server log version
#   - asks the official download endpoint for the latest build via the
#     redirect (no heavy scrape: the URL itself embeds version + build)
#   - when newer: downloads, verifies the jar (zip + plugin.yml version),
#     renames the current jar to <name>.known-good-<ver>-<ts>, installs the
#     new one as plugins/Geyser-Spigot.jar (single canonical name), restarts
#     the server, verifies Geyser re-enabled (log + UDP port + java alive),
#     and rolls back automatically if that verification fails.
#
# Usage: update-geyser.sh          (check + auto-install when newer)
#        update-geyser.sh --check-only
#        update-geyser.sh --dry-run
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

PROJECT="${GEYSER_PROJECT:-geyser}"
DOWNLOAD_URL="https://download.geysermc.org/v2/projects/${PROJECT}/versions/latest/builds/latest/downloads/spigot"
STATE_JSON="$STATE_DIR/state-geyser.json"
BATROCK_PORT="${GEYSER_PORT:-19132}"
KEEP_KNOWN_GOOD="${GEYSER_KEEP_KNOWN_GOOD:-3}"

mode="auto"
if [ "${1:-}" = "--check-only" ]; then mode="check-only" ; shift 2>/dev/null || true; fi
if [ "${1:-}" = "--dry-run" ]; then mode="dry-run" ; shift 2>/dev/null || true; fi

mkdir -p "$STATE_DIR"
exec 9>"/var/lock/mifron-geyser.lock"
flock -n 9 || { log "geyser updater already running; skip"; exit 0; }

# --- discover the active jar ---------------------------------------------
active_jar=""
active_version=""
find_active() {
  local f
  for f in "$SERVER_DIR/plugins/Geyser-Spigot.jar" "$SERVER_DIR/plugins"/Geyser*.jar "$SERVER_DIR/plugins"/geyser*.jar; do
    [ -e "$f" ] || continue
    case "$(basename "$f")" in
      *.known-good-*|*.pre-update-*|*.pre-*) continue ;;
    esac
    if unzip -l "$f" 2>/dev/null | grep -q 'plugin.yml'; then
      active_jar="$f"
      return 0
    fi
  done
  return 1
}

version_of_jar() {
  unzip -p "$1" plugin.yml 2>/dev/null | awk -F': *' '/^version:/ {print $2; exit}' | tr -d '\r'
}

# --- fetch latest ----------------------------------------------------------
latest_download_url="$(curl -fsSL -A 'mifron-geyser-updater' -o /dev/null -w '%{url_effective}' --max-time 30 "$DOWNLOAD_URL" 2>/dev/null || true)"
latest_version=""
latest_build=""
if [ -n "$latest_download_url" ]; then
  read -r latest_version latest_build <<EOF
$(python3 - "$latest_download_url" <<'PY'
import re, sys
url = sys.argv[1]
v = re.search(r'/versions/([^/]+)/', url)
b = re.search(r'/builds/([^/]+)/', url)
print((v.group(1) if v else ''), (b.group(1) if b else ''))
PY
)
EOF
fi
[ -n "$latest_version" ] || { log "could not resolve latest version (network?)"; exit 0; }

if find_active; then
  active_version="$(version_of_jar "$active_jar")"
fi
if [ -z "$active_version" ]; then
  # fall back to the runtime version the server logged
  active_version="$(sed -nE 's/.*\[Geyser[^]]*\] Loading server plugin Geyser[^ ]* v([^[:space:]]+).*/\1/p' "$SERVER_DIR/logs/latest.log" 2>/dev/null | tail -1)"
fi

state_installed=""
[ -s "$STATE_JSON" ] && state_installed="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("installed_version",""))' "$STATE_JSON" 2>/dev/null || true)"
log "installed=${active_version:-unknown} (${active_jar:+$(basename "$active_jar")}) last-state=${state_installed:-none} latest=$latest_version (build $latest_build)"

cmp_version() {  # returns 0 when $1 < $2
  python3 - "$1" "$2" <<'PY'
import re, sys
def key(txt):
    nums = re.findall(r'\d+', txt.replace('SNAPSHOT',''))
    return [int(x) for x in nums[:3]] + [0] * (3 - len(nums[:3]))
a, b = key(sys.argv[1]), key(sys.argv[2])
sys.exit(0 if a < b else 1)
PY
}

if [ "$mode" = "check-only" ]; then
  if cmp_version "${active_version:-0.0.0}" "$latest_version"; then
    echo "UPDATE AVAILABLE: ${active_version:-unknown} -> ${latest_version} (build ${latest_build})"
    exit 0
  else
    echo "up to date (${active_version:-unknown})"
    exit 0
  fi
fi

if [ "${active_version:-0.0.0}" = "$latest_version" ] && [ "$mode" != "dry-run" ]; then
  printf '{"project":"%s","installed_version":"%s","jar":"%s","last_check":"%s","last_result":"current"}\n' \
    "$PROJECT" "$active_version" "$([ -n "$active_jar" ] && basename "$active_jar" || echo "")" "$(now_iso)" > "$STATE_JSON"
  exit 0
fi

if ! cmp_version "${active_version:-0.0.0}" "$latest_version"; then
  log "already at ${active_version:-unknown}; nothing to do"
  exit 0
fi

if [ "$mode" = "dry-run" ]; then
  echo "[dry-run] would update ${active_version:-unknown} -> $latest_version (build $latest_build)"
  echo "[dry-run] active jar: ${active_jar:-MISSING}"
  exit 0
fi

# --- install ---------------------------------------------------------------
tmp_jar="$(mktemp /tmp/opencode/geyser-XXXXXX.jar)"
trap 'rm -f "$tmp_jar"' EXIT
log "downloading geyser $latest_version (build $latest_build)"
curl -fsSL -A 'mifron-geyser-updater' -o "$tmp_jar" --max-time 180 "$DOWNLOAD_URL"
[ -s "$tmp_jar" ] || { log "download failed (empty)"; exit 1; }
download_version="$(version_of_jar "$tmp_jar")"
if [ "$download_version" != "$latest_version" ]; then
  log "verification mismatch: $download_version != $latest_version"; exit 1
fi

backup_name=""
if [ -n "$active_jar" ]; then
  known_good="${active_jar%.jar}.known-good-${active_version}-$(now_utc)"
  mv -f "$active_jar" "$known_good"
  backup_name="$(basename "$known_good")"
  log "stored previous jar as $backup_name"
fi

tmp_active="Geyser-Spigot.jar"
install -m 0644 "$tmp_jar" "$SERVER_DIR/plugins/${tmp_active}.new" && \
  mv -f "$SERVER_DIR/plugins/${tmp_active}.new" "$SERVER_DIR/plugins/$tmp_active"
log "installed $tmp_active ($latest_version)"

# --- restart + health -------------------------------------------------------
restart_ok=0
if bash "$(dirname "$0")/mc-restart.sh"; then
  restart_ok=1
fi

if [ "$restart_ok" -ne 1 ]; then
  log "restart failed; rolling back"
  restore="${SERVER_DIR}/plugins/${tmp_active}"
  if [ -n "${backup_name:-}" ] && [ -s "$SERVER_DIR/plugins/$backup_name" ]; then
    cp -f "$SERVER_DIR/plugins/$backup_name" "$restore"
    bash "$(dirname "$0")/mc-restart.sh" || true
  fi
  bash "$(dirname "$0")/notify-event.sh" \
    --type geyser --title "Geyser更新失敗 — ロールバック済み" \
    --version "$latest_version" --source geyser-update \
    --body "再起動後のhealthに失敗して known-good に戻しました" \
    --skip-webhook >/dev/null 2>&1 || true
  exit 1
fi

# health: java alive + "Enabling Geyser-Spigot v<version>" + UDP listen
healthy=0
deadline=$(( $(date +%s) + 240 ))
while true; do
  if java_pid_for_server_dir "$SERVER_DIR" >/dev/null 2>&1 && \
     grep -q "Enabling Geyser-Spigot v${latest_version}" "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
    healthy=1
    break
  fi
  [ "$(date +%s)" -ge "$deadline" ] && break
  sleep 5
done

if [ "$healthy" -ne 1 ]; then
  log "health failed after update; rolling back"
  restore="${SERVER_DIR}/plugins/${tmp_active}"
  if [ -n "${backup_name:-}" ] && [ -s "$SERVER_DIR/plugins/$backup_name" ]; then
    cp -f "$SERVER_DIR/plugins/$backup_name" "$restore"
    bash "$(dirname "$0")/mc-restart.sh" || true
  fi
  bash "$(dirname "$0")/notify-event.sh" \
    --type geyser --title "Geyser更新失敗 — ロールバック済み ($latest_version)" \
    --version "$latest_version" --source geyser-update \
    --body "healthに失敗しました: known-goodに戻しました" \
    --skip-webhook >/dev/null 2>&1 || true
  exit 1
fi

printf '{"project":"%s","installed_version":"%s","installed_build":"%s","jar":"%s","last_check":"%s","last_result":"updated"}\n' \
  "$PROJECT" "$latest_version" "$latest_build" "$tmp_active" "$(now_iso)" > "$STATE_JSON"

# keep newest KEEP_KNOWN_GOOD backups only
ls -1 "$SERVER_DIR/plugins"/Geyser*.known-good-* 2>/dev/null | sort -r | tail -n +$((KEEP_KNOWN_GOOD + 1)) | while IFS= read -r old; do rm -f "$old"; done

bash "$(dirname "$0")/notify-event.sh" \
  --type geyser \
  --title "Geyser自動更新を完了: $latest_version (build $latest_build)" \
  --version "$latest_version" \
  --source geyser-update \
  --body "旧: ${active_version:-unknown} → 新: $latest_version" \
  --body "${backup_name:-} として退避済み(既知良好バージョン)" \
  --skip-webhook >/dev/null 2>&1 || true

log "geyser updated: $latest_version"
