# Test server operation

## Instances

| | Production | Test |
|---|---|---|
| Directory | `~/main-server` | `~/test-server` |
| Java port | 25565 | 25566 |
| Bedrock (UDP) | 19132 | 19133 |
| Votifier | 8192 | 8193 |
| RCON | 25575 | 25576 |
| Whitelist | off | **on** (forced) |
| Auto-restart | off | **every 6h + 5min warning** (`test-server.*`) |
| DiscordSRV/Tebex | enabled | disabled (tokens never shared) |

Data directories are fully separate. The test server starts as a snapshot of
production and diverges afterwards; production is never written from test.

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
