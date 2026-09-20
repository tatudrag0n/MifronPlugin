#!/usr/bin/env bash
# One-way sync: production -> test server. NEVER syncs test -> production.
# Usage: sync-test-server.sh [--full]
#   default: configs + plugin jars only (worlds/player data untouched)
#   --full:  also refresh worlds from production (test progress is wiped)
set -euo pipefail

PROD_DIR="${PROD_DIR:-$HOME/main-server}"
TEST_DIR="${TEST_DIR:-$HOME/test-server}"
FULL=0
[ "${1:-}" = "--full" ] && FULL=1

if [ ! -d "$PROD_DIR" ]; then echo "ERROR: prod dir missing"; exit 1; fi
if [ ! -d "$TEST_DIR" ]; then echo "ERROR: test dir missing (run setup-test-server.sh first)"; exit 1; fi
if [ "$TEST_DIR" = "$PROD_DIR" ]; then echo "REFUSE: TEST_DIR == PROD_DIR"; exit 1; fi

# Config + jars that define server behavior (ports/whitelist stay test-side).
for rel in bukkit.yml commands.yml help.yml permissions.yml spigot.yml wepif.yml \
           paper.jar config versions; do
  if [ -e "$PROD_DIR/$rel" ]; then
    rm -rf "$TEST_DIR/$rel"
    cp -a "$PROD_DIR/$rel" "$TEST_DIR/$rel"
    echo "SYNC $rel"
  fi
done

# Plugin jars only (never *.disabled-test handling, never test->prod).
mkdir -p "$TEST_DIR/plugins"
for jar in "$PROD_DIR"/plugins/*.jar; do
  [ -e "$jar" ] || continue
  base="$(basename "$jar")"
  case "$base" in
    DiscordSRV-*|tebex-*) echo "SKIP (disabled on test): $base"; continue;;
  esac
  cp -f "$jar" "$TEST_DIR/plugins/$base"
  echo "SYNC plugins/$base"
done

# Keep test-side ports/whitelist after every sync.
TEST_DIR="$TEST_DIR" JAVA_PORT=25566 BEDROCK_PORT=19133 \
  VOTE_PORT=8193 RCON_PORT=25576 \
  bash "$(dirname "$0")/apply-test-overrides.sh"

if [ "$FULL" = "1" ]; then
  for w in world main survival survival_nether survival_the_end creative market main_old; do
    if [ -d "$PROD_DIR/$w" ]; then
      rm -rf "$TEST_DIR/$w"
      cp -a "$PROD_DIR/$w" "$TEST_DIR/$w"
      echo "SYNC world $w"
    fi
  done
  find "$TEST_DIR" -name 'session.lock' -delete 2>/dev/null || true
fi

echo "Done. Production was not modified."
