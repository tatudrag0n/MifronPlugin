#!/usr/bin/env bash
set -euo pipefail

SERVER_ROOT="${SERVER_ROOT:-/home/tatudragon0327/main-server}"
BOT_ROOT="${BOT_ROOT:-/home/tatudragon0327/minoru-bot}"
MIFRON_CONFIG="${MIFRON_CONFIG:-$SERVER_ROOT/plugins/mifron/config.yml}"
MIFRON_DATA="${MIFRON_DATA:-$SERVER_ROOT/plugins/mifron/data.yml}"
MIFRON_AUTH="${MIFRON_AUTH:-$SERVER_ROOT/plugins/MifronAuth/auth.yml}"
BOT_ENV="${BOT_ENV:-$BOT_ROOT/.env}"

if [[ ! -f "$MIFRON_CONFIG" ]]; then
  echo "ERROR: Mifron config not found: $MIFRON_CONFIG" >&2
  exit 1
fi
if [[ ! -f "$BOT_ENV" ]]; then
  echo "ERROR: Minoru .env not found: $BOT_ENV" >&2
  exit 1
fi

command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "ERROR: python3 is required" >&2; exit 1; }

SECRET="$(openssl rand -hex 32)"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp -a "$MIFRON_CONFIG" "$MIFRON_CONFIG.bak.$STAMP"
cp -a "$BOT_ENV" "$BOT_ENV.bak.$STAMP"

SECRET="$SECRET" MIFRON_CONFIG="$MIFRON_CONFIG" python3 - <<'PY'
import os
from pathlib import Path

path = Path(os.environ['MIFRON_CONFIG'])
secret = os.environ['SECRET']
lines = path.read_text(encoding='utf-8').splitlines()

out = []
server_secret_done = False
in_bridge = False
bridge_indent = None
bridge_secret_done = False
bridge_seen = False

for line in lines:
    stripped = line.strip()
    indent = len(line) - len(line.lstrip(' '))

    if indent == 0 and stripped.startswith('serverSecret:'):
        out.append(f'serverSecret: "{secret}"')
        server_secret_done = True
        continue

    if indent == 0 and stripped == 'minoru-bridge:':
        in_bridge = True
        bridge_seen = True
        bridge_indent = None
        out.append(line)
        continue

    if in_bridge:
        if stripped and indent == 0:
            if not bridge_secret_done:
                out.append(f'  secret: "{secret}"')
                bridge_secret_done = True
            in_bridge = False
        elif stripped:
            if bridge_indent is None:
                bridge_indent = indent
            if stripped.startswith('secret:'):
                out.append(' ' * max(2, indent) + f'secret: "{secret}"')
                bridge_secret_done = True
                continue

    out.append(line)

if in_bridge and not bridge_secret_done:
    out.append(f'  secret: "{secret}"')
    bridge_secret_done = True

if not server_secret_done:
    out.append(f'serverSecret: "{secret}"')

if not bridge_seen:
    out.extend([
        '',
        'minoru-bridge:',
        '  enabled: true',
        '  bind: "127.0.0.1"',
        '  port: 8123',
        f'  secret: "{secret}"',
    ])

path.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY

SECRET="$SECRET" BOT_ENV="$BOT_ENV" MIFRON_DATA="$MIFRON_DATA" MIFRON_AUTH="$MIFRON_AUTH" python3 - <<'PY'
import os
from pathlib import Path

path = Path(os.environ['BOT_ENV'])
updates = {
    'MINORU_BRIDGE_URL': 'http://127.0.0.1:8123',
    'MINORU_BRIDGE_SECRET': os.environ['SECRET'],
    'MIFRON_DATA_PATH': os.environ['MIFRON_DATA'],
    'AUTH_FILE_PATH': os.environ['MIFRON_AUTH'],
    'MINECRAFT_API_URL': 'http://127.0.0.1:8081',
}

lines = path.read_text(encoding='utf-8').splitlines()
seen = set()
out = []
for line in lines:
    if '=' in line and not line.lstrip().startswith('#'):
        key = line.split('=', 1)[0].strip()
        if key in updates:
            out.append(f'{key}={updates[key]}')
            seen.add(key)
            continue
    out.append(line)

for key, value in updates.items():
    if key not in seen:
        out.append(f'{key}={value}')

path.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY

chmod 600 "$BOT_ENV"
unset SECRET

echo "Configured Mifron <-> Minoru MP bridge on 127.0.0.1:8123."
echo "Backups:"
echo "  $MIFRON_CONFIG.bak.$STAMP"
echo "  $BOT_ENV.bak.$STAMP"
echo "Restarting Minecraft and Minoru..."
sudo systemctl restart minecraft
sudo systemctl restart minoru-bot

echo "Bridge health:"
curl -fsS http://127.0.0.1:8123/health || {
  echo
  echo "ERROR: bridge health check failed. Check Minecraft logs." >&2
  exit 1
}
echo
echo "Done. The secret was written to config/.env and was not printed."
