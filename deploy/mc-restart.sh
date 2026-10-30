#!/usr/bin/env bash
# Restart the Minecraft server whichever supervisor is in control:
#   1. a systemd unit named $SERVICE_NAME (production VM layout), or
#   2. a detached screen session named $SCREEN_SESSION_NAME that started
#      `java ... paper.jar nogui` inside $SERVER_DIR (current host layout).
#
# Graceful path: send `stop` through the console, wait for java to exit,
# then relaunch with the exact original start command kills only escalate
# after the graceful windows pass. The launch command is remembered in
# $STATE_DIR/minecraft-start-command so a missing session can be rebuilt.
#
# Env:
#   STOP_GRACE_SECONDS (default 180)
#   START_GRACE_SECONDS (default 300)
#   HEALTH_STABLE_SECONDS (default 8)
set -euo pipefail

. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mc-common.sh"

STOP_GRACE_SECONDS="${STOP_GRACE_SECONDS:-180}"
START_GRACE_SECONDS="${START_GRACE_SECONDS:-300}"
HEALTH_STABLE_SECONDS="${HEALTH_STABLE_SECONDS:-8}"

require_screen() {
  command -v screen >/dev/null 2>&1 || { log "screen(1) not installed and no systemd unit for $SERVICE_NAME; cannot manage server"; return 1; }
}

systemd_unit_exists() {
  systemctl list-unit-files "$SERVICE_NAME.service" >/dev/null 2>&1 && \
    { [ "$(systemctl is-enabled "$SERVICE_NAME.service" 2>/dev/null || echo no)" != "no" ] || \
      systemctl is-active --quiet "$SERVICE_NAME.service" 2>/dev/null; }
}

detect_start_command() {
  local pid cmd
  pid="$(java_pid_for_server_dir "$SERVER_DIR" 2>/dev/null || true)"
  if [ -n "${pid:-}" ]; then
    cmd="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | sed 's/[[:space:]]*$//')"
    cmd="${cmd#*java }"
    MINECRAFT_START_COMMAND="${cmd:-$MINECRAFT_START_COMMAND}"
  fi
  if [ -n "$MINECRAFT_START_COMMAND" ]; then
    printf '%s\n' "$MINECRAFT_START_COMMAND"
    return 0
  fi
  printf '%s\n' "-Xmx${JAVA_HEAP} -Xms${JAVA_HEAP} -jar paper.jar nogui"
}

persist_start_command() {
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$1" > "$STATE_DIR/minecraft-start-command"
}

load_start_command() {
  [ -n "$MINECRAFT_START_COMMAND" ] && return 0
  if [ -s "$STATE_DIR/minecraft-start-command" ]; then
    MINECRAFT_START_COMMAND="$(tr -d '\r\n' < "$STATE_DIR/minecraft-start-command")"
    return 0
  fi
  return 1
}

wait_java_exit() {
  local deadline=$(( $(date +%s) + STOP_GRACE_SECONDS )) pid
  while true; do
    pid="$(java_pid_for_server_dir "$SERVER_DIR" 2>/dev/null || true)"
    [ -z "${pid:-}" ] && return 0
    if [ "$(date +%s)" -ge "$deadline" ]; then
      return 1
    fi
    sleep 3
  done
}

escalate_kill() {
  local pid="$1" stage="$2"
  case "$stage" in
    term) kill -TERM "$pid" 2>/dev/null || true ;;
    kill) kill -9 "$pid" 2>/dev/null || true ;;
  esac
}

start_via_screen() {
  require_screen || return 1
  screen -dmS "$SCREEN_SESSION_NAME" bash -c "cd '$SERVER_DIR' && ${JAVA_BIN} $1"
}

wait_port_open() {
  local deadline=$(( $(date +%s) + START_GRACE_SECONDS ))
  while true; do
    if port_open; then
      sleep "$HEALTH_STABLE_SECONDS"
      if port_open; then
        return 0
      fi
    fi
    [ "$(date +%s)" -ge "$deadline" ] && return 1
    sleep 3
  done
}

main() {
  if systemd_unit_exists; then
    log "Restarting via systemctl ($SERVICE_NAME)"
    systemctl restart "$SERVICE_NAME"
    if wait_port_open; then
      log "systemd restart OK; port ${MINECRAFT_PORT} open"
      return 0
    fi
    log "systemd restart ran but port ${MINECRAFT_PORT} never opened"
    return 1
  fi

  # screen path -------------------------------------------------------------
  local starting_cmd pid
  starting_cmd="$(load_start_command || detect_start_command)"

  if ! screen_has_session && ! java_pid_for_server_dir "$SERVER_DIR" >/dev/null 2>&1; then
    log "Server not running; starting it"
    persist_start_command "$starting_cmd"
    start_via_screen "$starting_cmd" || return 1
    if wait_port_open; then
      log "Start-up OK (screen $SCREEN_SESSION_NAME)"
      return 0
    fi
    log "Start-up failed: port ${MINECRAFT_PORT} did not open"
    return 1
  fi

  log "Graceful stop of $SCREEN_SESSION_NAME (grace ${STOP_GRACE_SECONDS}s)"
  if screen_has_session; then
    mc_console stop
  fi
  if ! wait_java_exit; then
    pid="$(java_pid_for_server_dir "$SERVER_DIR" 2>/dev/null || true)"
    if [ -n "${pid:-}" ]; then
      log "Graceful stop timed out; SIGTERM ${pid}"
      escalate_kill "$pid" term
      local term_deadline=$(( $(date +%s) + 30 ))
      while java_pid_for_server_dir "$SERVER_DIR" >/dev/null 2>&1; do
        if [ "$(date +%s)" -ge "$term_deadline" ]; then
          log "SIGTERM timed out; SIGKILL ${pid}"
          escalate_kill "$pid" kill
          sleep 2
          break
        fi
        sleep 2
      done
    fi
  fi

  if screen_has_session; then
    screen -S "$SCREEN_SESSION_NAME" -X quit >/dev/null 2>&1 || true
    sleep 1
  fi

  persist_start_command "$starting_cmd"
  start_via_screen "$starting_cmd" || return 1
  if wait_port_open; then
    log "Restart OK (screen $SCREEN_SESSION_NAME)"
    return 0
  fi
  log "Restart failed: port ${MINECRAFT_PORT} did not open in ${START_GRACE_SECONDS}s"
  return 1
}

log "$(basename "$0") invoked"
main "$@"
