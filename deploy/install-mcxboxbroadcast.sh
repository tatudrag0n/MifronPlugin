#!/usr/bin/env bash
set -euo pipefail

PLUGINS_DIR="${PLUGINS_DIR:-$HOME/main-server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
ASSET_URL="https://github.com/MCXboxBroadcast/Broadcaster/releases/latest/download/MCXboxBroadcastExtension.jar"

find_geyser_extensions_dir() {
  local candidates=(
    "$PLUGINS_DIR/Geyser-Spigot/extensions"
    "$PLUGINS_DIR/Geyser-Paper/extensions"
    "$PLUGINS_DIR/Geyser/extensions"
    "$HOME/main-server/config/Geyser-Fabric/extensions"
    "$HOME/main-server/config/Geyser-Velocity/extensions"
  )

  for d in "${candidates[@]}"; do
    if [ -d "$d" ]; then
      printf '%s\n' "$d"
      return 0
    fi
  done

  if compgen -G "$PLUGINS_DIR/Geyser-*.jar" >/dev/null || [ -d "$PLUGINS_DIR/Geyser-Spigot" ]; then
    printf '%s\n' "$PLUGINS_DIR/Geyser-Spigot/extensions"
    return 0
  fi

  return 1
}

if ! EXTENSIONS_DIR="$(find_geyser_extensions_dir)"; then
  echo "ERROR: Geyser installation was not found under $PLUGINS_DIR"
  exit 1
fi

mkdir -p "$EXTENSIONS_DIR"
TARGET="$EXTENSIONS_DIR/MCXboxBroadcastExtension.jar"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

curl --fail --location --silent --show-error "$ASSET_URL" --output "$TMP"
[ -s "$TMP" ] || { echo "ERROR: downloaded file is empty"; exit 1; }
[ "$(head -c 2 "$TMP")" = "PK" ] || { echo "ERROR: downloaded file is not a JAR"; exit 1; }

if [ -f "$TARGET" ]; then
  cp -p "$TARGET" "$TARGET.bak.$(date +%Y%m%d-%H%M%S)"
fi

install -m 0644 "$TMP" "$TARGET.new"
mv -f "$TARGET.new" "$TARGET"
sudo systemctl restart "$SERVICE_NAME"

sudo journalctl -u "$SERVICE_NAME" -n 160 --no-pager | grep -Ei 'mcxboxbroadcast|device code|microsoft.com/link|geyser' || true
