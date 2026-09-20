#!/usr/bin/env bash
# Builds ~/test-server as an isolated copy of the production server.
# One-way only: production -> test. This script NEVER writes to main-server.
set -euo pipefail

PROD_DIR="${PROD_DIR:-$HOME/main-server}"
TEST_DIR="${TEST_DIR:-$HOME/test-server}"
JAVA_PORT="${JAVA_PORT:-25566}"
BEDROCK_PORT="${BEDROCK_PORT:-19133}"
VOTE_PORT="${VOTE_PORT:-8193}"
RCON_PORT="${RCON_PORT:-25576}"

if [ ! -d "$PROD_DIR" ]; then echo "ERROR: prod dir missing: $PROD_DIR"; exit 1; fi
if [ "$TEST_DIR" = "$PROD_DIR" ]; then echo "REFUSE: TEST_DIR == PROD_DIR"; exit 1; fi

mkdir -p "$TEST_DIR"
echo "Copying $PROD_DIR -> $TEST_DIR (excluding live/session data)..."

# Full copy first (worlds included so terrain/rules match production).
cp -a "$PROD_DIR/." "$TEST_DIR/"

# Drop production runtime state that must never be shared.
rm -rf "$TEST_DIR/logs" "$TEST_DIR/crash-reports" "$TEST_DIR/debug"
find "$TEST_DIR" -name 'session.lock' -delete 2>/dev/null || true
mkdir -p "$TEST_DIR/logs" "$TEST_DIR/crash-reports"

# --- Test-side overrides (ports, whitelist, disabled integrations) --------
TEST_DIR="$TEST_DIR" JAVA_PORT="$JAVA_PORT" BEDROCK_PORT="$BEDROCK_PORT" \
  VOTE_PORT="$VOTE_PORT" RCON_PORT="$RCON_PORT" \
  bash "$(dirname "$0")/apply-test-overrides.sh"

echo "Test server ready at $TEST_DIR"
echo "  java=:$JAVA_PORT bedrock=:$BEDROCK_PORT/udp vote=:$VOTE_PORT whitelist=on"
echo "Start:  screen -DmS test java -Xmx4G -Xms4G -jar paper.jar nogui  (in $TEST_DIR)"
