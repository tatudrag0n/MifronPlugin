#!/usr/bin/env bash
# Re-applies test-side overrides (ports, whitelist, disabled integrations,
# test-server mode). Safe to run repeatedly. Never touches main-server.
set -euo pipefail

TEST_DIR="${TEST_DIR:-$HOME/test-server}"
JAVA_PORT="${JAVA_PORT:-25566}"
BEDROCK_PORT="${BEDROCK_PORT:-19133}"
VOTE_PORT="${VOTE_PORT:-8193}"
RCON_PORT="${RCON_PORT:-25576}"

if [ ! -d "$TEST_DIR" ]; then echo "ERROR: test dir missing: $TEST_DIR"; exit 1; fi
case "$TEST_DIR" in *main-server*) echo "REFUSE: TEST_DIR looks like production"; exit 1;; esac

prop="$TEST_DIR/server.properties"
if [ -f "$prop" ]; then
  sed -i -E \
    -e "s/^server-port=.*/server-port=$JAVA_PORT/" \
    -e "s/^query\.port=.*/query.port=$JAVA_PORT/" \
    -e "s/^rcon\.port=.*/rcon.port=$RCON_PORT/" \
    -e "s/^white-list=.*/white-list=true/" \
    -e "s/^enforce-whitelist=.*/enforce-whitelist=true/" \
    "$prop"
  grep -q '^white-list=' "$prop" || echo "white-list=true" >> "$prop"
  grep -q '^enforce-whitelist=' "$prop" || echo "enforce-whitelist=true" >> "$prop"
  sed -i -E "s/^motd=.*/motd=§c[TEST] §6§lMifron Test Server/" "$prop"
fi

if [ -f "$TEST_DIR/spigot.yml" ]; then
  sed -i -E "s#^(\s*)restart-script:.*#\1restart-script: ./start.sh#" "$TEST_DIR/spigot.yml"
fi

cat > "$TEST_DIR/start.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# 2G heap: the host has 7GB total, so a 4G test heap alongside production
# triggers the OOM killer. Never raise this without adding RAM.
exec java -Xmx2G -Xms2G -jar paper.jar nogui
EOF
chmod +x "$TEST_DIR/start.sh"

geyser="$TEST_DIR/plugins/Geyser-Spigot/config.yml"
if [ -f "$geyser" ]; then
  python3 - "$geyser" "$BEDROCK_PORT" <<'PY'
import re, sys
path, port = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
text = re.sub(r"(?m)^(\s*port:\s*)\d+", r"\g<1>" + port, text, count=1)
open(path, "w", encoding="utf-8").write(text)
PY
fi

for vc in "$TEST_DIR"/plugins/Votifier/config.yml "$TEST_DIR"/plugins/nuvotifier/config.yml; do
  [ -f "$vc" ] || continue
  sed -i -E "s/^(\s*port:\s*)[0-9]+/\1$VOTE_PORT/" "$vc"
done

for jar in "$TEST_DIR"/plugins/DiscordSRV-*.jar "$TEST_DIR"/plugins/tebex-*.jar "$TEST_DIR"/plugins/MifronAuth.jar; do
  [ -e "$jar" ] || continue
  mv -f "$jar" "$jar.disabled-test"
  echo "Disabled on test: $(basename "$jar")"
done

# Minoru bridge API must not fight production for 127.0.0.1:8123.
mifron_cfg="$TEST_DIR/plugins/mifron/config.yml"
if [ -f "$mifron_cfg" ]; then
  python3 - "$mifron_cfg" <<'PY'
import sys
path = sys.argv[1]
lines = open(path, encoding="utf-8").read().split("\n")
in_bridge = False
for i, line in enumerate(lines):
    if line.startswith("minoru-bridge:"):
        in_bridge = True
        continue
    if in_bridge:
        if line.startswith(" ") or line.startswith("\t"):
            if line.strip().startswith("port:"):
                indent = line[: len(line) - len(line.lstrip())]
                lines[i] = indent + "port: 8124"
                break
        elif line.strip() == "":
            continue
        else:
            break
open(path, "w", encoding="utf-8").write("\n".join(lines))
PY
fi

mifron_cfg="$TEST_DIR/plugins/mifron/config.yml"
if [ -f "$mifron_cfg" ]; then
  python3 - "$mifron_cfg" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
def setkey(t, k, v):
    if re.search(r"(?m)^" + k + r"\s*:", t):
        return re.sub(r"(?m)^" + k + r"\s*:.*$", k + ": " + v, t)
    return t + "\n" + k + ": " + v + "\n"
for k, v in [("test-server.enabled", "true"),
              ("test-server.enforce-whitelist", "true"),
              ("test-server.auto-restart", "true")]:
    text = setkey(text, k, v)
open(path, "w", encoding="utf-8").write(text)
PY
fi
echo "Test overrides applied to $TEST_DIR"
