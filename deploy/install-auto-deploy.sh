#!/usr/bin/env bash
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-$USER}}"
DEPLOY_HOME="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
REPO_DIR="${REPO_DIR:-$DEPLOY_HOME/MifronPlugin}"
PLUGINS_DIR="${PLUGINS_DIR:-$DEPLOY_HOME/main-server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
INTERVAL="${INTERVAL:-60}"
STATE_DIR="${STATE_DIR:-/var/lib/mifronplugin-deploy}"
ENV_FILE="${ENV_FILE:-/etc/mifronplugin-deploy.env}"

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "ERROR: Git checkout not found: $REPO_DIR" >&2
  exit 1
fi

if [ ! -x "$REPO_DIR/mvnw" ] \
   && [ ! -x "$REPO_DIR/apache-maven/bin/mvn" ] \
   && [ ! -x "$DEPLOY_HOME/apache-maven/bin/mvn" ] \
   && ! command -v mvn >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y maven
fi

sudo mkdir -p "$STATE_DIR"
sudo chown "$DEPLOY_USER":"$DEPLOY_USER" "$STATE_DIR"

if [ ! -f "$ENV_FILE" ]; then
  sudo tee "$ENV_FILE" >/dev/null <<'EOF'
# Optional. Keep secrets on the VM only.
# DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...
HEALTH_TIMEOUT=120
MINECRAFT_HOST=127.0.0.1
MINECRAFT_PORT=25565
# Optional stronger check. Example:
# EXTRA_HEALTHCHECK_CMD='curl -fsS http://127.0.0.1:8123/health >/dev/null'
EOF
  sudo chmod 600 "$ENV_FILE"
fi

sudo tee /usr/local/sbin/mifronplugin-deploy >/dev/null <<EOF
#!/usr/bin/env bash
set -euo pipefail
DEPLOY_USER='$DEPLOY_USER'
DEPLOY_HOME='$DEPLOY_HOME'
REPO_DIR='$REPO_DIR'
PLUGINS_DIR='$PLUGINS_DIR'
SERVICE_NAME='$SERVICE_NAME'
STATE_DIR='$STATE_DIR'
ENV_FILE='$ENV_FILE'

[ -f "\$ENV_FILE" ] && . "\$ENV_FILE"
HEALTH_TIMEOUT="\${HEALTH_TIMEOUT:-120}"
MINECRAFT_HOST="\${MINECRAFT_HOST:-127.0.0.1}"
MINECRAFT_PORT="\${MINECRAFT_PORT:-25565}"
DISCORD_WEBHOOK_URL="\${DISCORD_WEBHOOK_URL:-}"
EXTRA_HEALTHCHECK_CMD="\${EXTRA_HEALTHCHECK_CMD:-}"

deploy_remote="unknown"
installed_new=0
rollback_file=""

run_user() {
  sudo -u "\$DEPLOY_USER" -H "\$@"
}

notify() {
  local status="\$1" message="\$2"
  echo "[\$status] \$message"
  [ -z "\$DISCORD_WEBHOOK_URL" ] && return 0
  local payload
  payload="\$(python3 - "\$status" "\$message" <<'PY'
import json, sys
print(json.dumps({"content": f"[MifronPlugin deploy][{sys.argv[1]}] {sys.argv[2]}"}, ensure_ascii=False))
PY
)"
  curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "\$payload" "\$DISCORD_WEBHOOK_URL" >/dev/null || true
}

build_maven() {
  if [ -x "\$REPO_DIR/mvnw" ]; then
    run_user "\$REPO_DIR/mvnw" -B -DskipTests clean package
  elif [ -x "\$REPO_DIR/apache-maven/bin/mvn" ]; then
    run_user "\$REPO_DIR/apache-maven/bin/mvn" -B -DskipTests clean package
  elif [ -x "\$DEPLOY_HOME/apache-maven/bin/mvn" ]; then
    run_user "\$DEPLOY_HOME/apache-maven/bin/mvn" -B -DskipTests clean package
  else
    local mvn_bin
    mvn_bin="\$(command -v mvn || true)"
    [ -n "\$mvn_bin" ] || return 127
    run_user "\$mvn_bin" -B -DskipTests clean package
  fi
}

port_open() {
  timeout 2 bash -c "</dev/tcp/\$MINECRAFT_HOST/\$MINECRAFT_PORT" >/dev/null 2>&1
}

health_check() {
  local started="\$(date +%s)"
  while true; do
    if systemctl is-active --quiet "\$SERVICE_NAME" && port_open; then
      if [ -n "\$EXTRA_HEALTHCHECK_CMD" ]; then
        if bash -lc "\$EXTRA_HEALTHCHECK_CMD"; then
          return 0
        fi
      else
        return 0
      fi
    fi
    if [ "\$(( \$(date +%s) - started ))" -ge "\$HEALTH_TIMEOUT" ]; then
      return 1
    fi
    sleep 2
  done
}

rollback() {
  [ "\$installed_new" -eq 1 ] || return 1
  [ -n "\$rollback_file" ] && [ -s "\$rollback_file" ] || return 1
  notify ROLLBACK "Health check failed for \$deploy_remote; restoring previous JAR"
  cp -f "\$rollback_file" "\$target.new"
  mv -f "\$target.new" "\$target"
  systemctl restart "\$SERVICE_NAME"
  if health_check; then
    notify ROLLBACK_OK "Previous MifronPlugin restored successfully"
    return 0
  fi
  notify CRITICAL "Rollback completed but Minecraft health check still failed"
  return 1
}

on_error() {
  local code="\$?"
  if [ "\$installed_new" -eq 1 ]; then
    rollback || true
  fi
  notify FAILED "Deployment failed for \$deploy_remote (exit=\$code)"
  exit "\$code"
}
trap on_error ERR

cd "\$REPO_DIR"
run_user git fetch origin main
deploy_remote="\$(run_user git rev-parse origin/main)"
target="\$PLUGINS_DIR/mifronplugin-26.1.2.jar"
last_deployed=""
[ -f "\$STATE_DIR/last-deployed" ] && last_deployed="\$(tr -d '[:space:]' < "\$STATE_DIR/last-deployed")"

if [ "\$last_deployed" = "\$deploy_remote" ] && [ -s "\$target" ]; then
  echo "MifronPlugin already deployed: \$deploy_remote"
  exit 0
fi

notify START "Deploying \${last_deployed:-none} -> \$deploy_remote"
run_user git reset --hard origin/main
build_maven

jar="\$(find "\$REPO_DIR/target" -maxdepth 1 -type f -name 'mifronplugin-*.jar' ! -name 'original-*' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)"
test -n "\$jar"
test -s "\$jar"

mkdir -p "\$PLUGINS_DIR" "\$STATE_DIR/backups"
timestamp="\$(date +%Y%m%d-%H%M%S)"
if [ -s "\$target" ]; then
  rollback_file="\$STATE_DIR/backups/mifronplugin-previous-\$timestamp.jar"
  cp -f "\$target" "\$rollback_file"
fi

shopt -s nullglob
for old in "\$PLUGINS_DIR"/mifronplugin-*.jar "\$PLUGINS_DIR"/MifronPlugin-*.jar; do
  [ "\$old" = "\$target" ] && continue
  cp -f "\$old" "\$STATE_DIR/backups/\$(basename "\$old").\$timestamp.bak"
  rm -f "\$old"
done
shopt -u nullglob

install -m 0644 "\$jar" "\$target.new"
mv -f "\$target.new" "\$target"
installed_new=1
systemctl restart "\$SERVICE_NAME"
health_check

printf '%s\n' "\$deploy_remote" > "\$STATE_DIR/last-deployed"
printf '%s  %s\n' "\$(sha256sum "\$target" | awk '{print \$1}')" "\$target" > "\$STATE_DIR/last-deployed-jar.sha256"

# Keep the five newest rollback files.
find "\$STATE_DIR/backups" -maxdepth 1 -type f -printf '%T@ %p\n' | sort -nr | tail -n +6 | cut -d' ' -f2- | xargs -r rm -f
installed_new=0
trap - ERR
notify SUCCESS "MifronPlugin deployed: \$deploy_remote"
EOF
sudo chmod 0755 /usr/local/sbin/mifronplugin-deploy

sudo tee /etc/systemd/system/mifronplugin-deploy.service >/dev/null <<EOF
[Unit]
Description=Deploy latest MifronPlugin from GitHub with rollback
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=root
ExecStart=/usr/local/sbin/mifronplugin-deploy
TimeoutStartSec=15min
EOF

sudo tee /etc/systemd/system/mifronplugin-deploy.timer >/dev/null <<EOF
[Unit]
Description=Check MifronPlugin updates

[Timer]
OnBootSec=30s
OnUnitActiveSec=${INTERVAL}s
Persistent=true
Unit=mifronplugin-deploy.service

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now mifronplugin-deploy.timer
sudo systemctl start mifronplugin-deploy.service

echo "Installed safe MifronPlugin auto deploy."
echo "Optional notifications: edit $ENV_FILE and set DISCORD_WEBHOOK_URL."
echo "Timer: systemctl status mifronplugin-deploy.timer --no-pager"
echo "Logs:  journalctl -u mifronplugin-deploy.service -n 100 --no-pager"
