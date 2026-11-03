# E2E test report (2026-10-31, updated 2026-11-03)

## 2026-11-03 travel removal follow-up

- `/travel` was retired: Survival is the only destination, so the command only
  charged MP and applied a cooldown without moving anyone. The command, its
  config block, the `travel` bot suite and the guide page were removed.
- The guide book no longer uses yellow/gold text (headings are dark blue,
  command hints blue) and bumps its version so existing copies refresh.
- SHOP descriptions no longer mention Survival.

## 2026-11-01 player guide follow-up

The tutorial only explained one action at a time and had no lasting, player-
facing reference for proposals, voting, trade or protection. Added:

- A 12-page written book **「Mifron はじめの一歩」** covering MP/SHOP, menu,
  quests/goals, `/trade`, `/mf quest propose`, `/mf vote` (and the difference
  from the external vote site), login bonus/vote sites/referrals,
  `/mf protect chunk`, chat/Discord and the non-official
  disclaimer. The Build world is disabled and is intentionally not advertised
  in the book. The book carries a version marker; an outdated copy is replaced
  automatically on the next join (and by `/mf guide`).
- The book is handed over at graduation (and on tutorial skip), and can be
  re-obtained with `/mf guide` (also the menu's new **ガイド** entry).
- The menu now has 10 entries; the guide is inserted before チュートリアル so
  the existing SHOP/wallet/quest indices do not move.
- Tutorial stage 7 mentions the book, and the checkpoint sign now explains
  `/mf quest propose` and `/mf vote`.
- Tests: a `guide` suite verifies delivery, no duplication, drop/re-obtain and
  the 10-entry menu. `PlayerBotTest` checks the book title, page count and
  proposal content.

Full suite after the change: **14/14 PASS**, 125 Java tests pass.

## 2026-11-01 tutorial follow-up

The tutorial had drifted from the current feature set and two stages were
impossible to complete. Fixed and re-tested with a full 7-stage run:

- **Stage 4** handed out a relic but never advanced: nothing called
  `onSpecialUse`. Relic right-clicks now advance the stage.
- **Stage 5** asked the player to run `/mf relic`, which is admin-only, and
  waited for a *second* relic that a normal player cannot obtain. Replaced
  with "open Quests or Goals from the menu"; both actions advance.
- **Stage 6** advanced before the anvil transaction succeeded, and holding the
  relic/menu item blocks the anvil from opening. The advance now happens after
  the result is actually taken, and the text tells the player to hold a
  sword/book. The test accepts `anvil` / `chipped_anvil` / `damaged_anvil`
  and repairs the station if it has broken.
- Removed all scratch-card, job and `/mf relic` instructions. Stage text now
  covers SHOP(Survival only), attack timing, elite loot, relic acquisition,
  quests/goals, anvil cost and the Survival death penalty.
- The in-world guide holograms are now rebuilt from `TutorialSigns.java` on
  every start, so they cannot drift from the code again. 15 signs × 4 lines
  verified through Mineflayer.
- `PlayerBotTest` now covers the stage-4 relic-use and stage-5 guide hooks.

Full tutorial test: **PASS** (stages 1→2→3→4→5→6→7, graduation persisted).
Full suite after the change: **13/13 PASS** (`e2e-run9`).

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

**14 / 14 suites passed** (`npm test`, exit code 0).

| Suite | Result | Evidence |
|---|---|---|
| core | PASS | spawn `0.50, -60.00, 20.50`, health 20, food 20, survival |
| movement | PASS | server-side displacement 11.01 blocks |
| chat | PASS | global chat echo |
| command | PASS | `/mf`, `/mf balance`, usage fallback |
| mp | PASS | balance 11 → 111, zero amount rejected |
| tutorial | PASS | full 7 stages: auto-start, menu, 3 kills, elite, relic, quests, anvil, graduation |
| menu | PASS | 9 fan entries, wallet click returned `所持MP: 392` |
| shop | PASS | `Mifron SHOP` inventory opened in Survival |
| travel | PASS | usage, invalid destination, −500 MP fee, 59-minute cooldown |
| inventory | PASS | 5 diamonds hidden in tutorial and restored in Survival |
| persistence | PASS | UUID record present in `data.yml` |
| combat | PASS | vitals in range, no death |
| guide | PASS | book delivered, drop + `/mf guide` recovery, 10-entry menu |
| gui | PASS | menu item present, no legacy utility items |

Report artifact (final run with the guide): `mifron-bot/reports/e2e-2026-11-01T11-12-32-*.json`.

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

- **`/travel` retired (2026-11-03).** The command, config, guide page and bot
  suite were removed because Survival is the only destination.
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
