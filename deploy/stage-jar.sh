#!/usr/bin/env bash
# Builds the plugin and stages the jar for local verification.
# Production is NEVER touched by this script. Deploy to production only
# after explicit approval, by copying the staged jar to main-server/plugins.
set -euo pipefail

cd "$(dirname "$0")/.."
STAGE_DIR="${STAGE_DIR:-$HOME/mifron-staging}"
JAR_NAME="${JAR_NAME:-mifronplugin-26.1.2.jar}"

mvn -B clean package
mkdir -p "$STAGE_DIR"
cp -f "target/$JAR_NAME" "$STAGE_DIR/$JAR_NAME"
if [ -n "$(git status --short)" ]; then
  echo "ERROR: working tree dirty — commit first so the staged jar matches a commit."
  git status --short
  exit 1
fi
COMMIT="$(git rev-parse --short HEAD)"
DATE="$(date -u +%Y%m%dT%H%M%SZ)"
{
  echo "artifact: $JAR_NAME"
  echo "commit: $COMMIT"
  echo "built: $DATE"
  echo "md5: $(md5sum "$STAGE_DIR/$JAR_NAME" | cut -d' ' -f1)"
  echo "tests: $(grep -oE 'Tests run: [0-9]+' target/surefire-reports/*.txt 2>/dev/null | awk -F': ' '{s+=$2} END {print s}') passed"
} > "$STAGE_DIR/BUILD_INFO.txt"
echo "$DATE $COMMIT $(md5sum "$STAGE_DIR/$JAR_NAME" | cut -d' ' -f1)" >> "$STAGE_DIR/HISTORY.log"
echo "Staged: $STAGE_DIR/$JAR_NAME ($COMMIT)"
echo "Deploy (after approval ONLY): cp $STAGE_DIR/$JAR_NAME ~/main-server/plugins/$JAR_NAME"
