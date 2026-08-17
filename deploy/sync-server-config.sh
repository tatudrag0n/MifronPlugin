#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/main-server}"
REPO_DIR="${REPO_DIR:-$HOME/MifronPlugin}"
DEST="$REPO_DIR/server-config"

if [ ! -d "$SERVER_DIR" ]; then
  echo "ERROR: server directory not found: $SERVER_DIR"
  exit 1
fi
if [ ! -d "$REPO_DIR/.git" ]; then
  echo "ERROR: Git repository not found: $REPO_DIR"
  exit 1
fi

FILES=(
  "server.properties"
  "bukkit.yml"
  "spigot.yml"
  "commands.yml"
  "permissions.yml"
  "help.yml"
  "wepif.yml"
  "config/paper-global.yml"
  "config/paper-world-defaults.yml"
  "plugins/Geyser-Spigot/config.yml"
  "plugins/LuckPerms/config.yml"
  "plugins/LuckPerms/contexts.json"
  "plugins/Multiverse-Core/anchors.yml"
  "plugins/Multiverse-Core/config.yml"
  "plugins/Multiverse-Core/worlds.yml"
  "plugins/ViaVersion/config.yml"
  "plugins/VoidGen/settings.yml"
  "plugins/WorldEdit/config.yml"
  "plugins/WorldEditSelectionVisualizer/config.yml"
  "plugins/WorldGuard/config.yml"
  "plugins/floodgate/config.yml"
  "plugins/mifron/config.yml"
  "plugins/mifron/economy-price-table.yml"
  "plugins/mifron/quests.yml"
  "plugins/mifron/structures.yml"
  "plugins/mifron/text-displays.yml"
)

mkdir -p "$DEST"

for rel in "${FILES[@]}"; do
  src="$SERVER_DIR/$rel"
  dst="$DEST/$rel"
  if [ -f "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    cp -p "$src" "$dst"
    echo "SYNC  $rel"
  else
    echo "SKIP  $rel (not found)"
  fi
done

cat > "$DEST/README.md" <<'EOF'
# Mifron managed server configuration

This directory contains only configuration files explicitly approved for Git tracking.

Never add runtime or secret data here, including:
- `plugins/floodgate/key.pem`
- DiscordSRV credentials/tokens
- `auth.yml` files
- player/user caches, ops, whitelist or ban lists
- databases (`*.db`, `*.sqlite`, `*.mv.db`)
- logs, crash reports, worlds, session files
- Microsoft/Xbox authentication data
- API keys, service-account JSON, SSH keys or `.env` files
EOF

cd "$REPO_DIR"
git status --short -- server-config

echo
echo "Files copied into $DEST."
echo "Review the diff before committing: git diff -- server-config"
echo "Then commit/push only if the contents contain no secrets."
