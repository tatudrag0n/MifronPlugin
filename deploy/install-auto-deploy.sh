#!/usr/bin/env bash
set -euo pipefail

DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-$USER}}"
DEPLOY_HOME="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
REPO_DIR="${REPO_DIR:-$DEPLOY_HOME/MinervaPlugin}"
PLUGINS_DIR="${PLUGINS_DIR:-$DEPLOY_HOME/main-server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
INTERVAL="${INTERVAL:-60}"
STATE_DIR="${STATE_DIR:-/var/lib/minervaplugin-deploy}"

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "ERROR: Git checkout not found: $REPO_DIR" >&2
  echo "Clone MinervaPlugin first as $DEPLOY_USER, then rerun this installer." >&2
  exit 1
fi

# The deploy service needs a Maven executable that is available non-interactively.
# Prefer a project-local Maven/wrapper when present; otherwise install system Maven once.
if [ ! -x "$REPO_DIR/mvnw" ] \
   && [ ! -x "$REPO_DIR/apache-maven/bin/mvn" ] \
   && [ ! -x "$DEPLOY_HOME/apache-maven/bin/mvn" ] \
   && ! command -v mvn >/dev/null 2>&1; then
  echo "Maven not found; installing system Maven..."
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y maven
fi

sudo mkdir -p "$STATE_DIR"
sudo chown "$DEPLOY_USER":"$DEPLOY_USER" "$STATE_DIR"

sudo tee /usr/local/sbin/minervaplugin-deploy >/dev/null <<EOF
#!/usr/bin/env bash
set -euo pipefail
DEPLOY_USER='$DEPLOY_USER'
DEPLOY_HOME='$DEPLOY_HOME'
REPO_DIR='$REPO_DIR'
PLUGINS_DIR='$PLUGINS_DIR'
SERVICE_NAME='$SERVICE_NAME'
STATE_DIR='$STATE_DIR'

run_user() {
  sudo -u "\$DEPLOY_USER" -H "\$@"
}

build_maven() {
  if [ -x "\$REPO_DIR/mvnw" ]; then
    run_user "\$REPO_DIR/mvnw" -B -DskipTests clean package
    return
  fi
  if [ -x "\$REPO_DIR/apache-maven/bin/mvn" ]; then
    run_user "\$REPO_DIR/apache-maven/bin/mvn" -B -DskipTests clean package
    return
  fi
  if [ -x "\$DEPLOY_HOME/apache-maven/bin/mvn" ]; then
    run_user "\$DEPLOY_HOME/apache-maven/bin/mvn" -B -DskipTests clean package
    return
  fi

  MVN_BIN="\$(command -v mvn || true)"
  if [ -n "\$MVN_BIN" ] && [ -x "\$MVN_BIN" ]; then
    run_user "\$MVN_BIN" -B -DskipTests clean package
    return
  fi

  echo "ERROR: Maven executable not found for deploy." >&2
  exit 127
}

cd "\$REPO_DIR"
run_user git fetch origin main
remote="\$(run_user git rev-parse origin/main)"
target="\$PLUGINS_DIR/minervaplugin-26.1.2.jar"
last_deployed=""
if [ -f "\$STATE_DIR/last-deployed" ]; then
  last_deployed="\$(tr -d '[:space:]' < "\$STATE_DIR/last-deployed")"
fi

# Deployment state is based on the commit actually installed into the server,
# not the checkout's current HEAD. A manual git pull must never count as a deploy.
if [ "\$last_deployed" = "\$remote" ] && [ -s "\$target" ]; then
  echo "MinervaPlugin already deployed: \$remote"
  exit 0
fi

echo "Deploying MinervaPlugin: deployed=\${last_deployed:-none} -> remote=\$remote"
run_user git reset --hard origin/main
build_maven

jar="\$(find "\$REPO_DIR/target" -maxdepth 1 -type f -name 'minervaplugin-*.jar' ! -name 'original-*' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)"
test -n "\$jar"
test -s "\$jar"

mkdir -p "\$PLUGINS_DIR"
backup_dir="\$STATE_DIR/backups"
mkdir -p "\$backup_dir"

# Back up currently installed Minerva jars, then remove stale duplicates.
shopt -s nullglob
for old in "\$PLUGINS_DIR"/minervaplugin-*.jar "\$PLUGINS_DIR"/MinervaPlugin-*.jar; do
  cp -f "\$old" "\$backup_dir/\$(basename "\$old").\$(date +%Y%m%d-%H%M%S).bak"
  rm -f "\$old"
done
shopt -u nullglob

install -m 0644 "\$jar" "\$target.new"
mv -f "\$target.new" "\$target"

systemctl restart "\$SERVICE_NAME"
sleep 3
systemctl is-active --quiet "\$SERVICE_NAME"

# Only mark the commit deployed after build, install, and restart all succeeded.
printf '%s\n' "\$remote" > "\$STATE_DIR/last-deployed"
printf '%s  %s\n' "\$(sha256sum "\$target" | awk '{print \$1}')" "\$target" > "\$STATE_DIR/last-deployed-jar.sha256"
echo "MinervaPlugin deployment complete: \$remote"
EOF
sudo chmod 0755 /usr/local/sbin/minervaplugin-deploy

sudo tee /etc/systemd/system/minervaplugin-deploy.service >/dev/null <<EOF
[Unit]
Description=Deploy latest MinervaPlugin from GitHub
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
User=root
ExecStart=/usr/local/sbin/minervaplugin-deploy
EOF

sudo tee /etc/systemd/system/minervaplugin-deploy.timer >/dev/null <<EOF
[Unit]
Description=Check MinervaPlugin updates

[Timer]
OnBootSec=30s
OnUnitActiveSec=${INTERVAL}s
Persistent=true
Unit=minervaplugin-deploy.service

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now minervaplugin-deploy.timer
sudo systemctl start minervaplugin-deploy.service

echo "Installed MinervaPlugin auto deploy."
echo "Timer:  systemctl status minervaplugin-deploy.timer --no-pager"
echo "Deploy: systemctl status minervaplugin-deploy.service --no-pager"
echo "Logs:   journalctl -u minervaplugin-deploy.service -n 100 --no-pager"
