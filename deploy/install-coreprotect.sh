#!/usr/bin/env bash
set -euo pipefail

# CoreProtect CE v24.0 supports Minecraft 26.1.x, which matches the Paper API
# used by Mifron. Modrinth's file endpoint is used because GitHub releases do
# not publish the CE jar as a public release asset.
VERSION="${COREPROTECT_VERSION:-24.0}"
SERVER_DIR="${SERVER_DIR:-/opt/minecraft}"
PLUGINS_DIR="$SERVER_DIR/plugins"
TARGET="$PLUGINS_DIR/CoreProtect.jar"
URL="${COREPROTECT_URL:-https://cdn.modrinth.com/data/Lu3KuzdV/versions/Kma0kBsY/CoreProtect-CE-${VERSION}.jar}"

mkdir -p "$PLUGINS_DIR"
curl -fL --retry 3 --connect-timeout 10 -o "$TARGET.new" "$URL"
test -s "$TARGET.new"
mv -f "$TARGET.new" "$TARGET"
find "$PLUGINS_DIR" -maxdepth 1 -type f -iname 'CoreProtect*.jar' ! -path "$TARGET" -delete
echo "Installed CoreProtect CE v${VERSION} at ${TARGET}. Restart Paper to load it."
