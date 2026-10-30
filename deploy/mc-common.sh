# Shared environment for Mifron deploy pipeline scripts.
#
# Derives every path from the location of the repository so the scripts keep
# working wherever the deploy tree lives (plain $HOME layout or the
# consolidated backup tree), and can be overridden by env vars or
# /etc/mifronplugin-deploy.env.
#
# Loaded by: build-check.sh, backup-server.sh, promote-jar.sh,
# update-geyser.sh, notify-event.sh, publish-site-update.sh, mc-restart.sh.

_MIFRON_COMMON_SELF="${BASH_SOURCE[0]}"
while [ -L "$_MIFRON_COMMON_SELF" ]; do
  _MIFRON_COMMON_SELF="$(readlink "$_MIFRON_COMMON_SELF")"
done
REPO_DIR="${REPO_DIR:-"$(cd "$(dirname "$_MIFRON_COMMON_SELF")/.." && pwd)"}"
BASE_DIR="${BASE_DIR:-"$(dirname "$REPO_DIR")"}"

ENV_FILE="${ENV_FILE:-/etc/mifronplugin-deploy.env}"
STATE_DIR="${STATE_DIR:-/var/lib/mifronplugin-deploy}"
EVENT_FILE="${EVENT_FILE:-$STATE_DIR/events.jsonl}"
[ -f "$ENV_FILE" ] && . "$ENV_FILE"

BASE_DIR="${BASE_DIR}"
SERVER_DIR="${SERVER_DIR:-$BASE_DIR/main-server}"
TEST_DIR="${TEST_DIR:-$BASE_DIR/test-server}"
STAGING_DIR="${STAGING_DIR:-$BASE_DIR/mifron-staging}"
SITE_REPO_DIR="${SITE_REPO_DIR:-$BASE_DIR/officialsites-site-sync}"
MAIN_BACKUP_DIR="${MAIN_BACKUP_DIR:-$BASE_DIR/local-backups}"
DISCORD_WEBHOOK_URL="${DISCORD_WEBHOOK_URL:-}"
SCREEN_SESSION_NAME="${SCREEN_SESSION_NAME:-minecraft}"
MINECRAFT_START_COMMAND="${MINECRAFT_START_COMMAND:-}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_HEAP="${JAVA_HEAP:-4G}"
MINECRAFT_PORT="${MINECRAFT_PORT:-25565}"
MINECRAFT_HOST="${MINECRAFT_HOST:-127.0.0.1}"
JAR_NAME="${JAR_NAME:-mifronplugin-26.1.2.jar}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
REPO_FETCH_RETRIES="${REPO_FETCH_RETRIES:-3}"

export REPO_DIR BASE_DIR SERVER_DIR TEST_DIR STAGING_DIR SITE_REPO_DIR
export MAIN_BACKUP_DIR STATE_DIR EVENT_FILE SCREEN_SESSION_NAME
export DISCORD_WEBHOOK_URL JAVA_BIN JAVA_HEAP MINECRAFT_PORT MINECRAFT_HOST
export JAR_NAME SERVICE_NAME

now_utc() {
  date -u +%Y%m%dT%H%M%SZ
}

now_iso() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

log() {
  printf '[%s] %s\n' "$(now_utc)" "$*"
}

sha256_of() {
  [ -s "$1" ] && sha256sum "$1" | awk '{print $1}' || true
}

# --- Discord (optional direct webhook; the primary channel is the
# --- minoru-bot polling of the published site updates feed) ---------------
notify_webhook() {
  local message="$1"
  [ -n "$DISCORD_WEBHOOK_URL" ] || return 0
  python3 - "$message" <<'PY' 2>/dev/null | curl -fsS --max-time 10 -H 'Content-Type: application/json' -d @- "$DISCORD_WEBHOOK_URL" >/dev/null 2>&1 || true
import json, sys
print(json.dumps({"content": str(sys.argv[1])[:1900]}, ensure_ascii=False))
PY
}

# --- screen / java --------------------------------------------------------
java_pid_for_server_dir() {
  local dir="$1" pid candidate
  for pid in $(pgrep -f 'java .* -jar .*\.jar' 2>/dev/null || true); do
    candidate="$(readlink -f "/proc/$pid/cwd" 2>/dev/null || true)"
    if [ -n "$candidate" ] && [ "$candidate" = "$(readlink -f "$dir" 2>/dev/null)" ]; then
      echo "$pid"
      return 0
    fi
  done
  return 1
}

screen_has_session() {
  screen -ls 2>/dev/null | grep -qE "\.${SCREEN_SESSION_NAME//./\\.}[[:space:]]*\("
}

mc_console() {
  screen -S "$SCREEN_SESSION_NAME" -p 0 -X stuff "$*"$'\n'
}

# --- java port check ------------------------------------------------------
port_open() {
  local host="${MINECRAFT_HOST}" port="${MINECRAFT_PORT}"
  case "$port" in
    (''|*[!0-9]*) return 1 ;;
  esac
  timeout 2 bash -c 'exec 3<>"/dev/tcp/$1/$2"' _ "$host" "$port" >/dev/null 2>&1
}

udp_open() {
  local port="$1"
  case "$port" in
    (''|*[!0-9]*) return 1 ;;
  esac
  timeout 2 bash -c 'exec 3<>"/dev/udp/$1/$2"' _ 127.0.0.1 "$port" >/dev/null 2>&1
}

# Consider the server "active" under whichever supervisor owns it.
supervisor_active() {
  if systemctl is-active --quiet "$SERVICE_NAME.service" 2>/dev/null; then
    return 0
  fi
  java_pid_for_server_dir "$SERVER_DIR" >/dev/null 2>&1
}
