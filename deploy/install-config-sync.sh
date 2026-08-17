#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${REPO_DIR:-$HOME/MifronPlugin}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
INTERVAL="${INTERVAL:-60}"

APPLY_SCRIPT="$REPO_DIR/deploy/apply-server-config.sh"

if [ ! -f "$APPLY_SCRIPT" ]; then
  echo "ERROR: apply script not found: $APPLY_SCRIPT"
  exit 1
fi

chmod +x "$APPLY_SCRIPT"

sudo tee /etc/systemd/system/mifron-config-sync.service >/dev/null <<EOF
[Unit]
Description=Apply Mifron managed server configuration from GitHub
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=$USER
Environment=REPO_DIR=$REPO_DIR
Environment=SERVICE_NAME=$SERVICE_NAME
ExecStart=$APPLY_SCRIPT
EOF

sudo tee /etc/systemd/system/mifron-config-sync.timer >/dev/null <<EOF
[Unit]
Description=Check GitHub for Mifron server configuration updates

[Timer]
OnBootSec=1min
OnUnitActiveSec=${INTERVAL}s
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now mifron-config-sync.timer

echo "Installed config sync timer."
echo "Status: systemctl status mifron-config-sync.timer"
echo "Run now: sudo systemctl start mifron-config-sync.service"
