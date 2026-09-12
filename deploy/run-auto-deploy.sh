#!/usr/bin/env bash
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-$USER}}"
DEPLOY_HOME="$(getent passwd "$DEPLOY_USER" | cut -d: -f6 || true)"
DEPLOY_HOME="${DEPLOY_HOME:-$HOME}"
REPO_DIR="${REPO_DIR:-$DEPLOY_HOME/MifronPlugin}"
PLUGINS_DIR="${PLUGINS_DIR:-$DEPLOY_HOME/main-server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
STATE_DIR="${STATE_DIR:-/var/lib/mifronplugin-deploy}"
ENV_FILE="${ENV_FILE:-/etc/mifronplugin-deploy.env}"
EVENT_FILE="${EVENT_FILE:-$STATE_DIR/events.jsonl}"

[ -f "$ENV_FILE" ] && . "$ENV_FILE"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
HEALTH_STABLE_SECONDS="${HEALTH_STABLE_SECONDS:-8}"
MINECRAFT_HOST="${MINECRAFT_HOST:-127.0.0.1}"
MINECRAFT_PORT="${MINECRAFT_PORT:-25565}"
DISCORD_WEBHOOK_URL="${DISCORD_WEBHOOK_URL:-}"
EXTRA_HEALTHCHECK_CMD="${EXTRA_HEALTHCHECK_CMD:-}"

mkdir -p "$STATE_DIR"
deploy_remote="unknown"
installed_new=0
rollback_file=""
target="$PLUGINS_DIR/mifronplugin-26.1.2.jar"

run_user() {
  if [ "$(id -un)" = "$DEPLOY_USER" ]; then
    "$@"
  else
    sudo -u "$DEPLOY_USER" -H "$@"
  fi
}

notify() {
  local status="$1" message="$2"
  echo "[$status] $message"
  [ -z "$DISCORD_WEBHOOK_URL" ] && return 0
  local payload
  payload="$(python3 - "$status" "$message" <<'PY'
import json, sys
print(json.dumps({"content": f"[MifronPlugin deploy][{sys.argv[1]}] {sys.argv[2]}"}, ensure_ascii=False))
PY
)"
  curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "$payload" "$DISCORD_WEBHOOK_URL" >/dev/null || true
}

emit_event() {
  local status="$1" commit="$2"
  [ -n "$commit" ] || return 0
  python3 - "$status" "$commit" "$EVENT_FILE" <<'PY'
import json, os, sys
from datetime import datetime, timezone
status, commit, path = sys.argv[1:]
os.makedirs(os.path.dirname(path), exist_ok=True)
event = {"repository": "tatudrag0n/MifronPlugin", "commit": commit,
         "status": status, "timestamp": datetime.now(timezone.utc).isoformat()}
with open(path, "a", encoding="utf-8") as stream:
    stream.write(json.dumps(event, ensure_ascii=False) + "\n")
    stream.flush()
    os.fsync(stream.fileno())
PY
}

build_maven() {
  if [ -x "$REPO_DIR/mvnw" ]; then
    run_user "$REPO_DIR/mvnw" -B clean package
  elif [ -x "$REPO_DIR/apache-maven/bin/mvn" ]; then
    run_user "$REPO_DIR/apache-maven/bin/mvn" -B clean package
  elif [ -x "$DEPLOY_HOME/apache-maven/bin/mvn" ]; then
    run_user "$DEPLOY_HOME/apache-maven/bin/mvn" -B clean package
  else
    local mvn_bin
    mvn_bin="$(command -v mvn || true)"
    [ -n "$mvn_bin" ] || return 127
    run_user "$mvn_bin" -B clean package
  fi
}

port_open() {
  case "$MINECRAFT_PORT" in
    (''|*[!0-9]*) return 1 ;;
  esac
  [ "$MINECRAFT_PORT" -ge 1 ] && [ "$MINECRAFT_PORT" -le 65535 ] || return 1
  timeout 2 bash -c 'exec 3<>"/dev/tcp/$1/$2"' _ "$MINECRAFT_HOST" "$MINECRAFT_PORT" >/dev/null 2>&1
}

health_check() {
  local started
  started="$(date +%s)"
  while true; do
    if systemctl is-active --quiet "$SERVICE_NAME" && port_open; then
      if [ -n "$EXTRA_HEALTHCHECK_CMD" ]; then
        bash -lc "$EXTRA_HEALTHCHECK_CMD" || { sleep 2; continue; }
      fi
      sleep "$HEALTH_STABLE_SECONDS"
      if systemctl is-active --quiet "$SERVICE_NAME" && port_open; then
        if [ -z "$EXTRA_HEALTHCHECK_CMD" ] || bash -lc "$EXTRA_HEALTHCHECK_CMD"; then
          return 0
        fi
      else
        continue
      fi
    fi
    if [ "$(( $(date +%s) - started ))" -ge "$HEALTH_TIMEOUT" ]; then
      return 1
    fi
    sleep 2
  done
}

jar_plugin_version() {
  local file="$1"
  unzip -p "$file" plugin.yml 2>/dev/null | awk -F': *' '/^version:/ {print $2; exit}'
}

rollback() {
  [ "$installed_new" -eq 1 ] || return 1
  [ -n "$rollback_file" ] && [ -s "$rollback_file" ] || return 1
  notify ROLLBACK "Health check failed for $deploy_remote; restoring previous JAR"
  cp -f "$rollback_file" "$target.new"
  mv -f "$target.new" "$target"
  systemctl restart "$SERVICE_NAME"
  if health_check; then
    notify ROLLBACK_OK "Previous MifronPlugin restored successfully"
    return 0
  fi
  notify CRITICAL "Rollback completed but Minecraft health check still failed"
  return 1
}

on_error() {
  local code="$?"
  local event_status=FAILED
  if [ "$installed_new" -eq 1 ]; then
    if rollback; then
      event_status=ROLLED_BACK
    else
      event_status=CRITICAL
    fi
  fi
  emit_event "$event_status" "$deploy_remote" || true
  notify FAILED "Deployment failed for $deploy_remote (exit=$code)"
  exit "$code"
}
trap on_error ERR

cd "$REPO_DIR"
run_user git fetch origin main
deploy_remote="$(run_user git rev-parse origin/main)"
last_deployed=""
[ -f "$STATE_DIR/last-deployed" ] && last_deployed="$(tr -d '[:space:]' < "$STATE_DIR/last-deployed")"
current_plugin_version=""
[ -s "$target" ] && current_plugin_version="$(jar_plugin_version "$target" || true)"

echo "origin/main=$deploy_remote"
echo "last-deployed=${last_deployed:-none}"
echo "installed-jar=$target"
echo "installed-plugin-version=${current_plugin_version:-unknown}"

if [ "$last_deployed" = "$deploy_remote" ] && [ -s "$target" ]; then
  echo "MifronPlugin already deployed: $deploy_remote"
  exit 0
fi

notify START "Deploying ${last_deployed:-none} -> $deploy_remote"
run_user git reset --hard origin/main
build_maven

jar="$(find "$REPO_DIR/target" -maxdepth 1 -type f -name 'mifronplugin-*.jar' ! -name 'original-*' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)"
test -n "$jar"
test -s "$jar"
built_version="$(jar_plugin_version "$jar" || true)"
echo "built-jar=$jar"
echo "built-plugin-version=${built_version:-unknown}"

mkdir -p "$PLUGINS_DIR" "$STATE_DIR/backups"
timestamp="$(date +%Y%m%d-%H%M%S)"
if [ -s "$target" ]; then
  rollback_file="$STATE_DIR/backups/mifronplugin-previous-$timestamp.jar"
  cp -f "$target" "$rollback_file"
fi

shopt -s nullglob
for old in \
  "$PLUGINS_DIR"/mifronplugin-*.jar \
  "$PLUGINS_DIR"/MifronPlugin-*.jar \
  "$PLUGINS_DIR"/mifron.jar \
  "$PLUGINS_DIR"/Mifron.jar \
  "$PLUGINS_DIR"/mifron-*.jar
do
  [ "$old" = "$target" ] && continue
  [ -e "$old" ] || continue
  cp -f "$old" "$STATE_DIR/backups/$(basename "$old").$timestamp.bak"
  rm -f "$old"
  echo "removed-competing-jar $old"
done
shopt -u nullglob

install -m 0644 "$jar" "$target.new"
mv -f "$target.new" "$target"
installed_new=1
systemctl restart "$SERVICE_NAME"
health_check

emit_event SUCCESS "$deploy_remote" || true
printf '%s\n' "$deploy_remote" > "$STATE_DIR/last-deployed"
printf '%s  %s\n' "$(sha256sum "$target" | awk '{print $1}')" "$target" > "$STATE_DIR/last-deployed-jar.sha256"
printf '%s\n' "${built_version:-unknown}" > "$STATE_DIR/last-plugin-version"
find "$STATE_DIR/backups" -maxdepth 1 -type f -printf '%T@ %p\n' | sort -nr | tail -n +6 | cut -d' ' -f2- | xargs -r rm -f
installed_new=0
trap - ERR
notify SUCCESS "MifronPlugin deployed: $deploy_remote version=${built_version:-unknown}"
