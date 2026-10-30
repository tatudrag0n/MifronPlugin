#!/usr/bin/env bash
# Record an infrastructure event (deploy / build / backup / geyser / pipeline)
# into:  1) local JSONL event log,  2) the public site updates feed, and
#        3) optionally a direct Discord webhook (fallback channel; primary
#           Discord distribution is the minoru-bot polling the feed).
#
# Usage:
#   notify-event.sh --type deploy --title "..." [--commit SHA] [--version V]
#                   [--body "line" ...] [--source NAME]
#                   [--skip-site] [--skip-webhook] [--dry-run]
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

etype="other"
title=""
one_commit=""
one_version=""
source_name="MifronPlugin"
declare -a body_lines=()
skip_site=1
skip_webhook=0
dry_run=0

while [ $# -gt 0 ]; do
  case "$1" in
    --type) etype="$2"; shift 2 ;;
    --title) title="$2"; shift 2 ;;
    --commit) one_commit="$2"; shift 2 ;;
    --version) one_version="$2"; shift 2 ;;
    --body) body_lines+=("$2"); shift 2 ;;
    --source) source_name="$2"; shift 2 ;;
    --site) skip_site=0; shift ;;
    --skip-site) skip_site=1; shift ;;
    --force-main) push_main_flag="--force-main"; shift ;;
    --skip-webhook) skip_webhook=1; shift ;;
    --dry-run) dry_run=1; shift ;;
    *) echo "unknown arg: $1"; exit 2 ;;
  esac
done

[ -n "$title" ] || { echo "ERROR: --title required"; exit 2; }

event_json_file="$(mktemp /tmp/opencode/mifron-event-XXXXXX.json)"
trap 'rm -f "$event_json_file"' EXIT

export EVENT_TYPE="$etype" EVENT_TITLE="$title" EVENT_COMMIT="$one_commit" \
  EVENT_VERSION="$one_version" EVENT_SOURCE="$source_name" EVENT_DRY="$dry_run"

if [ ${#body_lines[@]} -gt 0 ]; then
  export EVENT_BODY_JSON="$(printf '%s\n' "${body_lines[@]}" | python3 -c 'import json,sys; print(json.dumps([line.rstrip("\n") for line in sys.stdin], ensure_ascii=False))')"
else
  export EVENT_BODY_JSON="[]"
fi

python3 - "$event_json_file" <<'PY'
import json, os, sys, uuid
from datetime import datetime, timezone

out = sys.argv[1]
dry = os.environ.get("EVENT_DRY") == "1"
body = []
try:
    body = json.loads(os.environ.get("EVENT_BODY_JSON") or "[]")
except Exception:
    body = []
event = {
    "id": f"{os.environ.get('EVENT_TYPE','other')}-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:4]}",
    "date": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "type": os.environ.get("EVENT_TYPE") or "other",
    "title": os.environ.get("EVENT_TITLE")[:180],
    "body": [line[:400] for line in body][:12],
    "commit": (os.environ.get("EVENT_COMMIT") or "")[:40],
    "version": (os.environ.get("EVENT_VERSION") or "")[:60],
    "source": os.environ.get("EVENT_SOURCE") or "MifronPlugin",
}
with open(out, "w", encoding="utf-8") as fh:
    json.dump(event, fh, ensure_ascii=False)
if dry:
    print("[dry-run] " + json.dumps(event, ensure_ascii=False, sort_keys=True))
PY

if [ "$dry_run" -eq 1 ]; then
  exit 0
fi

mkdir -p "$STATE_DIR"
cat "$event_json_file" >> "$EVENT_FILE"
echo >> "$EVENT_FILE"

if [ "$skip_webhook" -ne 1 ]; then
  msg="$(python3 - "$event_json_file" <<'PY'
import json, sys
event = json.load(open(sys.argv[1], encoding="utf-8"))
extra = " ".join(event.get("body") or [])
print(f"[MifronOps][{event['type']}] {event['title']}" + (f" - {extra}" if extra else ""))
PY
)"
  notify_webhook "$msg"
fi

if [ "$skip_site" -ne 1 ]; then
  # pass through an optional --force-main flag
  extra_args=""
  [ -n "${push_main_flag:-}" ] && extra_args="--force-main"
  # shellcheck disable=SC2086
  if bash "$(dirname "${BASH_SOURCE[0]}")/publish-site-update.sh" --event-file "$event_json_file" $extra_args; then
    log "site feed updated"
  else
    log "WARNING: site feed publish failed; fallback webhook may still have delivered"
  fi
fi
