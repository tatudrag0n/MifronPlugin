#!/usr/bin/env bash
# Consistent local backup of the production server tree.
#
#  - warns players on the live screen console
#  - save-off -> save-all flush -> snapshot (tar.zst) -> save-on
#  - SHA-256 manifest next to the archive (runbook requirement)
#  - keeps the newest KEEP_GENERATIONS archives in $MAIN_BACKUP_DIR
#  - refuses to run concurrently with itself (flock)
#
# Excluded: cache/, logs/, crash-reports/, debug/, previous *.jar backups
# (plugins themselves are inside), and other archive directories.
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

KEEP_GENERATIONS="${KEEP_GENERATIONS:-14}"
BACKUP_PREFIX="${BACKUP_PREFIX:-mifron-main-server}"
TIMESTAMP="$(now_utc)"
OUT_DIR="$MAIN_BACKUP_DIR"

mkdir -p "$OUT_DIR"
exec 9>"/var/lock/mifron-backup.lock"
flock -n 9 || { log "another backup is already running; skip"; exit 0; }

running_pid="$(java_pid_for_server_dir "$SERVER_DIR" 2>/dev/null || true)"
running_flag=0
if [ -n "${running_pid:-}" ] && screen_has_session; then
  running_flag=1
fi

if [ "$running_flag" -eq 1 ]; then
  log "announce + snapshot with saves paused"
  mc_console "say [Mifron] サーバーのデータをバックアップします。この間もログインできます。"
  mc_console "save-off"
  mc_console "save-all flush"
else
  log "server is not running; snapshot without console control"
fi

snapshot_ok=0
restore_saves() {
  if [ "$running_flag" -eq 1 ]; then
    mc_console "save-on"
    mc_console "save-all"
  fi
}
trap 'restore_saves' EXIT

OUT_FILE="$OUT_DIR/${BACKUP_PREFIX}-${TIMESTAMP}.tar.zst"
SUMMARY_FILE="$OUT_DIR/${BACKUP_PREFIX}-${TIMESTAMP}.sha256"
LIST_FILE="$OUT_DIR/${BACKUP_PREFIX}-${TIMESTAMP}.manifest.txt"

tar --use-compress-program='zstd -T2 -7' \
    --exclude='./cache' \
    --exclude='./logs' \
    --exclude='./crash-reports' \
    --exclude='./debug' \
    --exclude='./backups' \
    --exclude='./main_old' \
    --exclude='*.jar.bak-*' \
    --exclude='*.pre-*' \
    -C "$SERVER_DIR" \
    -cf - . > "$OUT_FILE"
snapshot_ok=1

{
  echo "archive: $(basename "$OUT_FILE")"
  echo "created: $TIMESTAMP"
  echo "sha256:  $(sha256_of "$OUT_FILE")"
  echo "bytes:   $(wc -c < "$OUT_FILE")"
  echo "host:    $(hostname)"
  echo "source:  $SERVER_DIR"
  echo "running: $running_flag"
} > "$SUMMARY_FILE"
sha256_of "$OUT_FILE" > "$LIST_FILE.tmp"

{ echo "--- top-level manifest ---"; } >> "$LIST_FILE.tmp"
( cd "$SERVER_DIR" && find . -maxdepth 2 -type d ! -path './cache*' ! -path './logs*' ! -path './crash-reports*' ! -path './debug*' ! -path './main_old*' | sort ) >> "$LIST_FILE.tmp"
mv -f "$LIST_FILE.tmp" "$LIST_FILE"

# retention: newest KEEP_GENERATIONS archives stay
ls -1t "$OUT_DIR/${BACKUP_PREFIX}"-*.tar.zst 2>/dev/null | tail -n +$((KEEP_GENERATIONS + 1)) | while IFS= read -r old; do
  base="${old%.tar.zst}"
  rm -f "$old" "$base.sha256" "$base.manifest.txt"
  log "rotation removed $(basename "$old")"
done

trap - EXIT
restore_saves

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$DEPLOY_DIR/notify-event.sh" \
  --type backup \
  --title "サーバーバックアップ完了 ($(basename "$OUT_FILE"))" \
  --commit "$(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)" \
  --source backup \
  --body "サイズ: $(du -h "$OUT_FILE" | cut -f1) / SHA-256: $(sha256_of "$OUT_FILE" | cut -c1-16)…" \
  --body "保存先: $(basename "$OUT_DIR") (保持${KEEP_GENERATIONS}世代)" \
  --skip-webhook >/dev/null 2>&1 || true

log "backup finished: $OUT_FILE"
