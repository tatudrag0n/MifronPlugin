#!/usr/bin/env bash
# Writes a failure event for a failed systemd pipeline unit into the shared
# event log and (when configured) the fallback Discord webhook. Used by the
# mifron-notify-failure.service OnFailure hook.
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

exec bash "$(dirname "${BASH_SOURCE[0]}")/notify-event.sh" \
  --type pipeline \
  --title "パイプライン単位の失敗: ${UNIT:-unknown}" \
  --source systemd \
  --body "systemd unit ${UNIT:-unknown} failed (${JOB_UNIT:-})" \
  --skip-webhook
