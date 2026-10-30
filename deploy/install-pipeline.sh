#!/usr/bin/env bash
# One-shot installer for the Mifron ops pipeline on the current host:
#
#   /etc/mifronplugin-deploy.env        env shared by every script
#   /var/lib/mifronplugin-deploy/       state, events, backups
#   systemd units + timers:
#     mifron-config-sync.timer    (60s)   GitHub -> server config apply
#     mifron-build-check.timer    (5min)  GitHub -> staging + updates event
#     mifron-backup.timer         (daily) local server backup (runbook SHA-256)
#     mifron-geyser-update.timer  (daily) Geyser auto-update w/ rollback
#
# Run once as root. Re-running refreshes units; the server itself is NOT
# restarted by the installer.
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

ENV_PATH="$ENV_FILE"
[ "$(id -u)" -eq 0 ] || { echo "ERROR: run as root"; exit 1; }

mkdir -p "$STATE_DIR"
chmod 755 "$STATE_DIR"

# --- env file --------------------------------------------------------------
if [ ! -s "$ENV_PATH" ]; then
  cat > "$ENV_PATH" <<EOF
# Shared by all Mifron deploy pipeline scripts. Secrets are NOT stored here.
BASE_DIR=$BASE_DIR
SCREEN_SESSION_NAME=$SCREEN_SESSION_NAME
JAVA_HEAP=$JAVA_HEAP
MINECRAFT_PORT=$MINECRAFT_PORT
SERVICE_NAME=$SERVICE_NAME
STAGING_DIR=$STAGING_DIR
SITE_REPO_DIR=$SITE_REPO_DIR
MAIN_BACKUP_DIR=$MAIN_BACKUP_DIR
# Optional direct-Discord fallback channel (bot polling of the site feed is
# the primary notification channel). Leave empty to disable.
DISCORD_WEBHOOK_URL=
EOF
fi

# --- make scripts executable ----------------------------------------------
for file in mc-restart.sh mc-common.sh build-check.sh promote-jar.sh \
            notify-event.sh publish-site-update.sh update-geyser.sh \
            backup-server.sh install-updates-page-layout.sh log-failed-unit.sh; do
  [ -f "$REPO_DIR/deploy/$file" ] && chmod +x "$REPO_DIR/deploy/$file"
done

# --- systemd units ---------------------------------------------------------
cat > /etc/systemd/system/mifron-config-sync.service <<EOF
[Unit]
Description=Apply Mifron managed server configuration from GitHub
After=network-online.target
Wants=network-online.target
OnFailure=mifron-notify-failure.service

[Service]
Type=oneshot
WorkingDirectory=$REPO_DIR
Environment=REPO_DIR=$REPO_DIR
ExecStart=/usr/bin/flock -x /var/lock/mifron-config-sync.lock $REPO_DIR/deploy/apply-server-config.sh
EOF

cat > /etc/systemd/system/mifron-config-sync.timer <<'EOF'
[Unit]
Description=60s managed config sync

[Timer]
OnCalendar=*-*-* *:*:00
AccuracySec=15s

[Install]
WantedBy=timers.target
EOF

cat > /etc/systemd/system/mifron-build-check.service <<EOF
[Unit]
Description=Fetch origin and stage a fresh MifronPlugin build
After=network-online.target
OnFailure=mifron-notify-failure.service

[Service]
Type=oneshot
WorkingDirectory=$REPO_DIR
Environment=REPO_DIR=$REPO_DIR
ExecStart=/usr/bin/flock -x /var/lock/mifron-build-check.lock $REPO_DIR/deploy/build-check.sh
EOF

cat > /etc/systemd/system/mifron-build-check.timer <<'EOF'
[Unit]
Description=periodic fetch + build + stage check

[Timer]
OnCalendar=*:0/5
AccuracySec=30s

[Install]
WantedBy=timers.target
EOF

cat > /etc/systemd/system/mifron-backup.service <<EOF
[Unit]
Description=Consistent local backup of the Mifron production server
After=network-online.target
OnFailure=mifron-notify-failure.service

[Service]
Type=oneshot
WorkingDirectory=$REPO_DIR
Environment=REPO_DIR=$REPO_DIR
ExecStart=/usr/bin/flock -x /var/lock/mifron-backup.lock $REPO_DIR/deploy/backup-server.sh
EOF

cat > /etc/systemd/system/mifron-backup.timer <<'EOF'
[Unit]
Description=daily server backup (Asia/Tokyo)

[Timer]
OnCalendar=*-*-* 20:00:00 Asia/Tokyo
Persistent=true
AccuracySec=1min
RandomizedDelaySec=45

[Install]
WantedBy=timers.target
EOF

cat > /etc/systemd/system/mifron-geyser-update.service <<EOF
[Unit]
Description=Automatic Geyser-Spigot update + health verification + rollback
After=network-online.target
OnFailure=mifron-notify-failure.service

[Service]
Type=oneshot
WorkingDirectory=$REPO_DIR
Environment=REPO_DIR=$REPO_DIR
ExecStart=/usr/bin/flock -x /var/lock/mifron-geyser.lock $REPO_DIR/deploy/update-geyser.sh
EOF

cat > /etc/systemd/system/mifron-geyser-update.timer <<'EOF'
[Unit]
Description=nightly automatic Geyser update (Asia/Tokyo)

[Timer]
OnCalendar=*-*-* 19:30:00 Asia/Tokyo
Persistent=true
AccuracySec=1min
RandomizedDelaySec=45

[Install]
WantedBy=timers.target
EOF

cat > /etc/systemd/system/mifron-notify-failure.service <<EOF
[Unit]
Description=Log a failed Mifron pipeline unit into the ops event stream

[Service]
Type=oneshot
Environment=UNIT=%n
Environment=JOB_UNIT=%p
ExecStart=$REPO_DIR/deploy/log-failed-unit.sh
EOF

systemctl daemon-reload

# site updates page should exist BEFORE the first publish
if [ -d "$SITE_REPO_DIR/.git" ] && [ ! -s "$SITE_REPO_DIR/mifron/updates/index.html" ]; then
  bash "$REPO_DIR/deploy/install-updates-page-layout.sh" "$SITE_REPO_DIR"
  ( cd "$SITE_REPO_DIR" && git add -A mifron/updates && \
    git -c user.name="tatudrag0n" -c user.email="tatudragon0327@gmail.com" \
        commit -m "docs(updates): add update feed page" --no-verify --quiet || true )
  echo "site updates page committed."
fi

systemctl enable mifron-config-sync.timer mifron-build-check.timer \
                 mifron-backup.timer mifron-geyser-update.timer >/dev/null
systemctl start  mifron-config-sync.timer mifron-build-check.timer \
                 mifron-backup.timer mifron-geyser-update.timer

echo
echo "=== installed timers ==="
systemctl list-timers --all "mifron-*" --no-pager | head -12
echo
echo "config:  $ENV_PATH"
echo "state:   $STATE_DIR"
echo "production jar promotion (manual gate) is deploy/promote-jar.sh --confirm"
