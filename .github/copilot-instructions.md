# Copilot Instructions — MifronPlugin

## Project overview
MifronPlugin is a **Minecraft Paper 26.1.2 server plugin** (Java, Maven). It integrates the Mifron server economy/job/trade system into Minecraft. Package root: `src/main/java`. Resources/config: `src/main/resources`.

## Build
- Build tool: **Maven** (`pom.xml`).
- Target: Paper 26.1.2 API. Java version per `pom.xml` (check before changing).
- Build command: `mvn -q -DskipTests package` (or `mvn clean package`).
- Do **not** commit `target/` or server data files (`data.yml`, `quests.yml`, `structures.yml`, etc.).

## Key domain concepts
- **MP (Mifron Points)**: in-game currency. Never treat emeralds as MP (emeralds are not converted).
- **Jobs**: 7 jobs (hunter, farmer, enchanter, merchant, fisher, miner, adventurer) + none. Single selection with 24h cooldown. Each grants income bonuses and unique perks.
- **Trade**: `/trade <player>` shared UI, simultaneous exchange, reset on change, refund on exit/timeout. Max MP per trade `trade.max-amount` (default 1,000,000).
- **Travel**: `/travel <hub|survival|main>`, free + 10min CD for adventurer.
- **Shelf shops**: global shared stock per material; wallet (right-click) to buy, sell by holding same item and right-clicking shelf. Barrel shops on empty barrel.
- **Chat**: global only. DM commands (`/tell`, `/msg`, `/w`, `/whisper`, `/mail`, `/team msg`, etc.) are blocked → redirect to Discord.
- **Protection chunks**: doors/buttons/containers/frames/signs/hoppers + explosions/pistons/liquids/fire limited; normal block break/place not blocked in central chunks.

## Configuration
- All config in `src/main/resources/config.yml`. Only settings actually referenced by code. Keys: `jobs.*`, `trade.*`, `build-world.*`, `world-rules.*`, `regen.*`, `barrel-shop.*`, `auction.*`, `minoru-bridge.*`.
- `spawn-protection=0` recommended (Vanilla protection interferes with shop operations).

## Conventions
- Commands: main `/mifron` (alias `/mf`); admin subcommands require `mifron.admin` or specific perms.
- Permission nodes follow `mifron.<feature>` / `mifron.admin`.
- Data persistence: YAML files under plugin data folder; never commit server data.
- Minoru bridge: local API for MP sync with web (DiscordBot-Minoru). `serverSecret` fallback from old config.
- Legal: not affiliated with Mojang/Microsoft; follow Minecraft EULA for monetization; no real-money exchange of MP.

## Don't
- Don't add speculative config keys not read by code.
- Don't enable DM-style chat commands.
- Don't commit `data.yml`/`quests.yml`/`structures.yml`/`proposals.yml`/`inventory-groups/*.dat`.
- Don't break the EULA note in README.

## Testing
- No automated tests present; verify by building (`mvn package`) and loading on a Paper 26.1.2 test server. Check logs for errors on enable.
