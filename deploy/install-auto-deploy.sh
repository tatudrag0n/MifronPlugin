#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/tatudrag0n/MinervaPlugin.git}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/mifron/minervaplugin}"
PLUGINS_DIR="${PLUGINS_DIR:-$HOME/server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
INTERVAL="${INTERVAL:-60}"

sudo mkdir -p "$INSTALL_ROOT"
sudo chown "$USER":"$USER" "$INSTALL_ROOT"

if [ ! -d "$INSTALL_ROOT/repo/.git" ]; then
  git clone --branch main --single-branch "$REPO_URL" "$INSTALL_ROOT/repo"
fi

cat > "$INSTALL_ROOT/deploy.sh" <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail
ROOT="__ROOT__"
PLUGINS_DIR="__PLUGINS__"
SERVICE_NAME="__SERVICE__"
cd "$ROOT/repo"
git fetch origin main
current="$(git rev-parse HEAD)"
remote="$(git rev-parse origin/main)"
[ "$current" = "$remote" ] && exit 0
git reset --hard origin/main
mvn -B -DskipTests clean package
jar="$(find target -maxdepth 1 -type f -name 'minervaplugin-*.jar' ! -name 'original-*' | head -n1)"
test -n "$jar"
mkdir -p "$PLUGINS_DIR"
target="$PLUGINS_DIR/MinervaPlugin.jar"
if [ -f "$target" ]; then cp -f "$target" "$target.bak"; fi
install -m 0644 "$jar" "$target.new"
mv -f "$target.new" "$target"
sudo systemctl restart "$SERVICE_NAME"
echo "$remote" > "$ROOT/last-deployed"
SCRIPT
sed -i "s|__ROOT__|$INSTALL_ROOT|g; s|__PLUGINS__|$PLUGINS_DIR|g; s|__SERVICE__|$SERVICE_NAME|g" "$INSTALL_ROOT/deploy.sh"
chmod +x "$INSTALL_ROOT/deploy.sh"

sudo tee /etc/systemd/system/minervaplugin-deploy.service >/dev/null <<EOF
[Unit]
Description=Deploy latest MinervaPlugin from GitHub
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=$USER
ExecStart=$INSTALL_ROOT/deploy.sh
EOF

sudo tee /etc/systemd/system/minervaplugin-deploy.timer >/dev/null <<EOF
[Unit]
Description=Check MinervaPlugin updates

[Timer]
OnBootSec=2min
OnUnitActiveSec=${INTERVAL}s
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now minervaplugin-deploy.timer

echo "Installed. Check with: systemctl status minervaplugin-deploy.timer"
