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
LOCK_FILE="${LOCK_FILE:-$STATE_DIR/repo-sync.lock}"
EVENT_FILE="${EVENT_FILE:-$STATE_DIR/events.jsonl}"

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
HEALTH_TIMEOUT=300
# Required consecutive healthy seconds after restart.
HEALTH_STABLE_SECONDS=8
MINECRAFT_HOST=127.0.0.1
MINECRAFT_PORT=25565
# Optional stronger check. Example:
# EXTRA_HEALTHCHECK_CMD='curl -fsS http://127.0.0.1:8123/health >/dev/null'
# EVENT_FILE=/var/lib/mifronplugin-deploy/events.jsonl
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
EVENT_FILE='$EVENT_FILE'
export DEPLOY_USER DEPLOY_HOME REPO_DIR PLUGINS_DIR SERVICE_NAME STATE_DIR ENV_FILE EVENT_FILE
[ -f "\$ENV_FILE" ] && . "\$ENV_FILE"
cd "\$REPO_DIR"
if [ "\$(id -un)" = "\$DEPLOY_USER" ]; then
  git fetch origin main
  git reset --hard origin/main
else
  sudo -u "\$DEPLOY_USER" -H git fetch origin main
  sudo -u "\$DEPLOY_USER" -H git reset --hard origin/main
fi
chmod +x "\$REPO_DIR/deploy/run-auto-deploy.sh"
exec "\$REPO_DIR/deploy/run-auto-deploy.sh"
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
