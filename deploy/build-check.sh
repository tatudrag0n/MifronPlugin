#!/usr/bin/env bash
# GitHub -> local build pipeline (no server change!).
#
#  Timer-driven. Fetches origin, and if origin/main moved past the last built
#  commit: builds + tests with Maven and stages the jar into $STAGING_DIR
#  (BUILD_INFO.txt / HISTORY.log kept compatible with stage-jar.sh) and
#  notifies. Installing into production is a separate human decision
#  (deploy/promote-jar.sh), so the live jar is never touched here.
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

exec 9>"/var/lock/mifron-build-check.lock"
flock -n 9 || exit 0

LAST_BUILT_FILE="$STATE_DIR/last-built-commit"
check_tail() { tail -c1 "$1" 2>/dev/null | od -An -c | grep -q '\\n'; }

cd "$REPO_DIR"
fetch_ok=0
attempt=0
while [ "$attempt" -lt "$REPO_FETCH_RETRIES" ]; do
  attempt=$((attempt + 1))
  if git fetch origin main --quiet 2>"$STATE_DIR/build-check-fetch.err"; then
    fetch_ok=1
    break
  fi
  sleep 10
done
if [ "$fetch_ok" -ne 1 ]; then
  log "git fetch failed after ${attempt} attempts; keeping previous state"
  exit 0
fi

head_local="$(git rev-parse HEAD)"
origin_main="$(git rev-parse origin/main)"
last_built=""
[ -s "$LAST_BUILT_FILE" ] && last_built="$(tr -d '[:space:]' < "$LAST_BUILT_FILE")"
log "origin/main=$origin_main head=$head_local last-built=${last_built:-none}"

if [ -n "$(git status --porcelain)" ]; then
  log "working tree dirty; skipping auto-build (coordinate manually)"
  exit 0
fi
if [ -n "$last_built" ] && [ "$origin_main" = "$last_built" ]; then
  exit 0
fi

# If HEAD is behind origin, advance fast-forwardable history first.
git merge --ff-only origin/main >/dev/null 2>&1 || true
head_local="$(git rev-parse HEAD)"

change_range="${last_built:+$last_built..origin/main}"
[ -n "$change_range" ] || change_range="origin/main -1"
commit_notes="$(git log --oneline --no-decorate -20 $change_range 2>/dev/null | head -8 || true)"

mvn -B clean package >"$STATE_DIR/build.log" 2>&1 || {
  log "BUILD FAILED - see $STATE_DIR/build.log"
  bash "$(dirname "${BASH_SOURCE[0]}")/notify-event.sh" \
    --type build --title "ビルド失敗 (origin/main=$(git rev-parse --short origin/main))" \
    --commit "$(git rev-parse --short origin/main)" --source build-check \
    --body "mvnが失敗しました。サーバー側ログ: $(hostname):$STATE_DIR/build.log" \
    --skip-site >/dev/null 2>&1 || true
  exit 0
}

jar="$(find "$REPO_DIR/target" -maxdepth 1 -type f -name 'mifronplugin-*.jar' ! -name 'original-*' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n1 | cut -d' ' -f2-)"
if [ -z "$jar" ]; then
  log "build produced no jar; aborting quietly"
  exit 0
fi

built_version="$(unzip -p "$jar" plugin.yml 2>/dev/null | awk -F': *' '/^version:/ {print $2; exit}')"
test_summary="$(grep -hoE 'Tests run: [0-9]+' target/surefire-reports/*.txt 2>/dev/null | awk -F': ' '{s+=$2} END {print s}')"
jar_sha="$(sha256_of "$jar")"

mkdir -p "$STAGING_DIR"
cp -f "$jar" "$STAGING_DIR/$JAR_NAME"
{
  echo "artifact: $JAR_NAME"
  echo "commit: $(git rev-parse --short HEAD)"
  echo "built: $(now_utc)"
  echo "sha256: $jar_sha"
  echo "version: $built_version"
  echo "tests: ${test_summary:-?} passed"
} > "$STAGING_DIR/BUILD_INFO.txt"
printf '%s %s %s\n' "$(now_utc)" "$(git rev-parse --short HEAD)" "$jar_sha" >> "$STAGING_DIR/HISTORY.log"
printf '%s\n' "$origin_main" > "$LAST_BUILT_FILE"

# notify: staging event (site + optional webhook)
declare -a lines=()
while IFS= read -r note; do
  [ -n "$note" ] && lines+=("chg: $note")
done <<< "$commit_notes"
lines+=("version: ${built_version:-unknown} / tests: ${test_summary:-0} passed")
lines+=("jar sha256: ${jar_sha:0:16}… → staging directory")
lines+=("反映は deploy/promote-jar.sh --confirm で実行します")

notify_args=(
  --type build
  --title "新ビルド検出: $(git rev-parse --short origin/main) をstagingに保存中"
  --commit "$(git rev-parse --short origin/main)"
  --version "$built_version"
  --source build-check
  --site --skip-webhook
)
if [ ${#lines[@]} -gt 0 ]; then
  for note in "${lines[@]}"; do
    notify_args+=(--body "$note")
  done
fi
bash "$(dirname "${BASH_SOURCE[0]}")/notify-event.sh" "${notify_args[@]}" >/dev/null 2>&1 || true

log "staged $JAR_NAME (version=${built_version:-unknown}, sha=${jar_sha:0:12})"
