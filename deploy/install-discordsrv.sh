#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="${SERVER_DIR:-$HOME/main-server}"
PLUGINS_DIR="$SERVER_DIR/plugins"
VERSION="1.30.5"
JAR_NAME="DiscordSRV-Build-${VERSION}.jar"
URL="https://github.com/DiscordSRV/DiscordSRV/releases/download/v${VERSION}/${JAR_NAME}"
EXPECTED_SHA256="ef2fa1f2eb146c7c77412b7190a7dd33f1fc91282683fe45407739554c8aefef"
TARGET="$PLUGINS_DIR/DiscordSRV.jar"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

mkdir -p "$PLUGINS_DIR"

echo "Installing DiscordSRV ${VERSION} into $PLUGINS_DIR"
curl -fL --retry 3 --connect-timeout 15 "$URL" -o "$TMP"
ACTUAL_SHA256="$(sha256sum "$TMP" | awk '{print $1}')"
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
    echo "DiscordSRV checksum mismatch" >&2
    echo "expected: $EXPECTED_SHA256" >&2
    echo "actual:   $ACTUAL_SHA256" >&2
    exit 1
fi

# Remove old versioned DiscordSRV jars so Paper cannot load duplicates.
find "$PLUGINS_DIR" -maxdepth 1 -type f -iname 'DiscordSRV*.jar' ! -path "$TARGET" -delete
install -m 0644 "$TMP" "$TARGET"

echo "Installed: $TARGET"
if systemctl list-unit-files minecraft.service >/dev/null 2>&1; then
    sudo systemctl restart minecraft
    echo "Restarted minecraft.service"
fi

echo "Check with: grep -i DiscordSRV $SERVER_DIR/logs/latest.log | tail -n 80"
