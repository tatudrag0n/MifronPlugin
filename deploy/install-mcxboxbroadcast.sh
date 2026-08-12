#!/usr/bin/env bash
set -euo pipefail

# Mifron: MCXboxBroadcast installer/updater for an existing Geyser installation.
# Usage:
#   bash deploy/install-mcxboxbroadcast.sh
# Optional overrides:
#   PLUGINS_DIR=/path/to/plugins SERVICE_NAME=minecraft bash deploy/install-mcxboxbroadcast.sh

PLUGINS_DIR="${PLUGINS_DIR:-$HOME/server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
ASSET_URL="https://github.com/MCXboxBroadcast/Broadcaster/releases/latest/download/MCXboxBroadcastExtension.jar"

find_geyser_extensions_dir() {
  local candidates=(
    "$PLUGINS_DIR/Geyser-Spigot/extensions"
    "$PLUGINS_DIR/Geyser-Paper/extensions"
    "$PLUGINS_DIR/Geyser/extensions"
    "$HOME/server/config/Geyser-Fabric/extensions"
    "$HOME/server/config/Geyser-Velocity/extensions"
  )

  for d in "${candidates[@]}"; do
    if [ -d "$d" ]; then
      printf '%s\n' "$d"
      return 0
    fi
  done

  # If Geyser is installed but the extensions directory has not been created yet,
  # prefer the usual Spigot/Paper data directory.
  if compgen -G "$PLUGINS_DIR/Geyser-*.jar" >/dev/null || [ -d "$PLUGINS_DIR/Geyser-Spigot" ]; then
    printf '%s\n' "$PLUGINS_DIR/Geyser-Spigot/extensions"
    return 0
  fi

  return 1
}

if ! EXTENSIONS_DIR="$(find_geyser_extensions_dir)"; then
  echo "ERROR: Geyser installation was not found."
  echo "Expected Geyser under: $PLUGINS_DIR"
  echo "Install/start Geyser first, or set PLUGINS_DIR to the correct server plugins directory."
  exit 1
fi

mkdir -p "$EXTENSIONS_DIR"
TARGET="$EXTENSIONS_DIR/MCXboxBroadcastExtension.jar"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

printf 'Geyser extensions directory: %s\n' "$EXTENSIONS_DIR"
echo "Downloading latest MCXboxBroadcast extension..."
curl --fail --location --silent --show-error "$ASSET_URL" --output "$TMP"

if [ ! -s "$TMP" ]; then
  echo "ERROR: downloaded file is empty."
  exit 1
fi

# Basic JAR/ZIP signature check (PK).
if [ "$(head -c 2 "$TMP")" != "PK" ]; then
  echo "ERROR: downloaded file does not look like a JAR file."
  exit 1
fi

if [ -f "$TARGET" ]; then
  BACKUP="$TARGET.bak.$(date +%Y%m%d-%H%M%S)"
  cp -p "$TARGET" "$BACKUP"
  echo "Backed up existing extension to: $BACKUP"
fi

install -m 0644 "$TMP" "$TARGET.new"
mv -f "$TARGET.new" "$TARGET"
echo "Installed: $TARGET"

echo "Restarting Minecraft service: $SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"

echo
echo "MCXboxBroadcast has been installed/updated."
echo "On the first run it will print a Microsoft device-login code in the server log."
echo "Use the dedicated Mifron Xbox/Microsoft account for that login, not a personal main account."
echo
echo "Recent MCXboxBroadcast/Geyser log lines:"
sudo journalctl -u "$SERVICE_NAME" -n 160 --no-pager | grep -Ei 'mcxboxbroadcast|device code|microsoft.com/link|geyser' || true
