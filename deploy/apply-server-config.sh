#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/main-server}"
REPO_DIR="${REPO_DIR:-$HOME/MifronPlugin}"
SOURCE="$REPO_DIR/server-config"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
FEATURE_DIR="$REPO_DIR/server-features"

if [ ! -d "$SERVER_DIR" ]; then
  echo "ERROR: server directory not found: $SERVER_DIR"
  exit 1
fi
if [ ! -d "$REPO_DIR/.git" ]; then
  echo "ERROR: Git repository not found: $REPO_DIR"
  exit 1
fi

cd "$REPO_DIR"
git fetch origin main
OLD_HEAD="$(git rev-parse HEAD)"
NEW_HEAD="$(git rev-parse origin/main)"

if [ "$OLD_HEAD" = "$NEW_HEAD" ]; then
  exit 0
fi

git reset --hard origin/main

# Feature management: Switch/Xbox friend-join broadcasting.
if [ -f "$FEATURE_DIR/mcxboxbroadcast.enabled" ]; then
  echo "FEATURE MCXboxBroadcast enabled"
  PLUGINS_DIR="$SERVER_DIR/plugins" SERVICE_NAME="$SERVICE_NAME" \
    bash "$REPO_DIR/deploy/install-mcxboxbroadcast.sh"
fi

if [ ! -d "$SOURCE" ]; then
  echo "No server-config directory in repository."
  exit 0
fi

changed=0

while IFS= read -r -d '' src; do
  rel="${src#$SOURCE/}"

  # Defense-in-depth: refuse to deploy anything that looks secret/runtime-only.
  case "$rel" in
    *key.pem|*.db|*.sqlite|*.mv.db|*.log|*.log.gz|*.env|*.env.*|*auth.yml|*credentials*|*token*|plugins/DiscordSRV/*|world/*|world_nether/*|world_the_end/*|logs/*|crash-reports/*)
      echo "REFUSE $rel"
      continue
      ;;
  esac

  dst="$SERVER_DIR/$rel"
  mkdir -p "$(dirname "$dst")"

  if [ ! -f "$dst" ] || ! cmp -s "$src" "$dst"; then
    cp -p "$src" "$dst"
    echo "APPLY  $rel"
    changed=1
  fi
done < <(find "$SOURCE" -type f ! -name README.md -print0)

if [ "$changed" -eq 1 ]; then
  echo "Configuration changed; restarting $SERVICE_NAME"
  sudo systemctl restart "$SERVICE_NAME"
else
  echo "Repository updated, but no managed server configuration changed."
fi
