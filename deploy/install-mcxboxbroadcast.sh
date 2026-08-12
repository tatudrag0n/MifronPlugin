#!/usr/bin/env bash
set -euo pipefail

PLUGINS_DIR="${PLUGINS_DIR:-$HOME/main-server/plugins}"
SERVICE_NAME="${SERVICE_NAME:-minecraft}"
BUILD="149"
ASSET_URL="https://github.com/MCXboxBroadcast/Broadcaster/releases/download/${BUILD}/MCXboxBroadcastExtension.jar"
EXPECTED_SHA256="444cc222b3ca5d5e8064803062f78a3209dec5d306375bf3fbc2a86580f808e2"
FORCE_UPDATE="${FORCE_UPDATE:-0}"

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

if [ -f "$TARGET" ] && [ "$FORCE_UPDATE" != "1" ]; then
  current_sha="$(sha256sum "$TARGET" | awk '{print $1}')"
  if [ "$current_sha" = "$EXPECTED_SHA256" ]; then
    echo "MCXboxBroadcast Build $BUILD is already installed: $TARGET"
    exit 0
  fi
fi

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

echo "Installing MCXboxBroadcast Build $BUILD into: $EXTENSIONS_DIR"
curl --fail --location --silent --show-error "$ASSET_URL" --output "$TMP"
[ -s "$TMP" ] || { echo "ERROR: downloaded file is empty"; exit 1; }
[ "$(head -c 2 "$TMP")" = "PK" ] || { echo "ERROR: downloaded file is not a JAR"; exit 1; }

downloaded_sha="$(sha256sum "$TMP" | awk '{print $1}')"
if [ "$downloaded_sha" != "$EXPECTED_SHA256" ]; then
  echo "ERROR: SHA-256 mismatch for MCXboxBroadcast Build $BUILD"
  echo "Expected: $EXPECTED_SHA256"
  echo "Actual:   $downloaded_sha"
  exit 1
fi

if [ -f "$TARGET" ]; then
  cp -p "$TARGET" "$TARGET.bak.$(date +%Y%m%d-%H%M%S)"
fi

install -m 0644 "$TMP" "$TARGET.new"
mv -f "$TARGET.new" "$TARGET"
echo "Installed MCXboxBroadcast Build $BUILD"

if systemctl list-unit-files "$SERVICE_NAME.service" >/dev/null 2>&1; then
  sudo systemctl restart "$SERVICE_NAME"
  echo "Restarted $SERVICE_NAME"
else
  echo "WARNING: systemd service '$SERVICE_NAME' was not found. Restart the Minecraft server manually."
fi

echo
echo "After startup, authenticate the dedicated Xbox/Microsoft account (joinMifron) using the device code shown in the server log."
echo "Recent relevant log lines:"
if systemctl list-unit-files "$SERVICE_NAME.service" >/dev/null 2>&1; then
  sudo journalctl -u "$SERVICE_NAME" -n 240 --no-pager | grep -Ei 'mcxboxbroadcast|device code|microsoft.com/link|authenticate|geyser' || true
fi
