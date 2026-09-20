# Test server operation

> Policy: the test server runs **locally** (developer machine), not on this
> host. The `~/test-server` instance was removed from the host to protect
> production memory. The scripts below are retained for local use.

## Instances

| | Production (host) | Test (local) |
|---|---|---|
| Directory | `~/main-server` | `<local>/test-server` |
| Java port | 25565 | 25566 |
| Bedrock (UDP) | 19132 | 19133 |
| Votifier | 8192 | 8193 |
| RCON | 25575 | 25576 |
| Whitelist | off | **on** (forced) |
| Auto-restart | off | **every 6h + 5min warning** (`test-server.*`) |
| DiscordSRV/Tebex | enabled | disabled (tokens never shared) |
| MifronAuth | enabled | disabled (whitelist replaces Discord auth) |
| Minoru bridge | 127.0.0.1:8123 | 127.0.0.1:8125 (8124 = minoru-bot) |

Data directories are fully separate. The test server starts as a snapshot of
production and diverges afterwards; production is never written from test.

> Memory: the host has 7GB RAM. The test server runs with a 2G heap
> (`start.sh`); running 4G+4G concurrently triggers the Linux OOM killer and
> takes production down with it. For heavy load tests, stop production first
> or add RAM.

## Scripts (`deploy/`)

- `setup-test-server.sh` — first-time build: full copy prod→test, then
  `apply-test-overrides.sh`. Never writes to `main-server` (guarded).
- `apply-test-overrides.sh` — re-applies test ports/whitelist/start.sh,
  idempotent, safe to re-run.
- `sync-test-server.sh` — one-way prod→test refresh of configs + plugin jars
  (worlds/player data untouched). `--full` also refreshes worlds (wipes test
  progress).

## Workflow: GitHub -> test -> audit -> approve -> prod

1. Push changes to `origin/main` (GitHub is the source of truth; the prod
   `mifron-config-sync.timer` resets `~/MifronPlugin` to `origin/main`).
2. Build the jar and copy it to `~/test-server/plugins/`.
3. Restart the test server, exercise the change with the audit checklist
   (spec §15: whitelist, restart warning, semi-creative, flat bedrock,
   approval particles, revolver, status reset, MFL tab, night vision, shop
   cooldowns/prices/buy/sell/stock/restock, rare merchant, restart integrity).
4. On approval, copy the same jar to `~/main-server/plugins/` and restart
   production (0 players, `save-all` first).
5. Never copy `test-server` data back to production. Never run two servers
   on the same directory (session.lock).

## Notes

- `test-server.*` config defaults are all **off**, so production behavior is
  unchanged unless explicitly enabled.
- Paper restarts on test use `start.sh` (`restart-script: ./start.sh`).
- The 6h restart + 5min warning is implemented in `TestServerFeature`
  (broadcast + title + `Bukkit.restart()` with shutdown fallback).
