# E2E test report (2026-10-31)

Real-server test run against an isolated Paper test server, driven by the
Mineflayer bot in `../mifron-bot`.

## Environment

| | |
|---|---|
| Paper | 26.1.2-63 |
| Plugin | `26.1.2-swimpose3-20261031` (shade jar) |
| Protocol | 775 (`BOT_VERSION=26.1`) |
| Java | 25 |
| Server | `127.0.0.1:25566`, `online-mode=false`, whitelist on |
| Bot | Mineflayer 4.39.0, offline auth, op on the test server only |

The test server was refreshed from production with
`deploy/sync-test-server.sh --full` before the run, then the test player was
reset with `mifron-bot/scripts/reset-test-player.js` (data.yml, inventory
groups and world playerdata), so the run starts as a genuine first join.

## Result

**13 / 13 suites passed** (`npm test`, exit code 0).

| Suite | Result | Evidence |
|---|---|---|
| core | PASS | spawn `0.50, -60.00, 20.50`, health 20, food 20, survival |
| movement | PASS | server-side displacement 11.01 blocks |
| chat | PASS | global chat echo |
| command | PASS | `/mf`, `/mf balance`, usage fallback |
| mp | PASS | balance 11 → 111, zero amount rejected |
| tutorial | PASS | first-join auto-start, stage 1→2 via menu, zombie kill, skip persisted |
| menu | PASS | 9 fan entries, wallet click returned `所持MP: 392` |
| shop | PASS | `Mifron SHOP` inventory opened in Survival |
| travel | PASS | usage, invalid destination, −500 MP fee, 59-minute cooldown |
| inventory | PASS | 5 diamonds hidden in tutorial and restored in Survival |
| persistence | PASS | UUID record present in `data.yml` |
| combat | PASS | vitals in range, no death |
| gui | PASS | menu item present, no legacy utility items |

Report artifact: `mifron-bot/reports/e2e-2026-10-31T16-06-05-113Z.json`.

## Issues found and fixed

1. **Test server was a stale snapshot.** Its `tutorial.spawn` was still
   `y=65` while production had moved to `y=-60`; the tutorial zombie was 125
   blocks below the bot. Added plugin config/content sync to
   `deploy/sync-test-server.sh` and refreshed the world.
2. **Test server could stop the host VM.** `auto-shutdown.enabled=true` with
   the `gcloud` provider after 10 idle minutes. `deploy/apply-test-overrides.sh`
   now forces it off.
3. **Test analytics went to production.** The test config pointed at
   `127.0.0.1:8124` (production Minoru). Overrides now clear the endpoint and
   secret.
4. **Harness raced the spawn.** Suites ran before chunks/health were ready,
   producing false movement/GUI failures. The runner now waits for chunks and
   health, applies a per-suite timeout, writes a JSON report and exits non-zero
   on failure.
5. **Obsolete tests.** The old `shop`/`gear` suites expected chest GUIs, but
   the menu is now an in-world fan of item/text/interaction entities and the
   gear system was removed. Replaced with fan-menu and Survival shop tests.

## Open issues / not covered

- **`/travel` destinations collapse.** `hub`, `survival` and `main` currently
  resolve to the same Survival spawn, so the command charges 500 MP and the
  cooldown without moving the player. Decide whether to re-point `hub` at a
  real world, make same-location travel free, or retire the command before
  public launch.
- Fan sub-screens (status tabs, quest UI, trade UI) are not fully driven yet;
  entry spawning and wallet actions are verified.
- Bedrock/Geyser UI paths are not covered by this Java bot.
- Full restart persistence (plugin/world restart) is not automated; run
  `persistence` + `inventory` after a manual restart when those paths change.
- The first load of a hand-edited config with an unquoted `time: 20:00`
  logs "invalid time; skipping" once (YAML 1.1 parses it as an integer) and
  is rewritten quoted on save. The shipped config is quoted; only hand-edited
  configs are affected.

## Reproduction

```bash
# 1. one-way refresh test server from production (production untouched)
cd ~/mifron-work
PROD_DIR=~/main-server TEST_DIR=~/test-server bash deploy/sync-test-server.sh --full

# 2. start test server
cd ~/test-server && ./start.sh

# 3. reset the test player (stop the server first)
cd ~/mifron-bot
node scripts/reset-test-player.js MifronTestBot --data ~/test-server/plugins/mifron/data.yml
# restart the test server, then:
npm test
```
