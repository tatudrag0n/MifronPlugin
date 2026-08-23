# Mifron Plugin

Mifron command aliases:

- `/mifron`
- `/mf`

`/mv` is reserved for Multiverse-Core and is not registered by Mifron. For example, use Multiverse-Core commands such as `/mv create` only for world management provided by Multiverse-Core.

Recommended Mifron world/admin command style:

- `/mf check`
- `/mf list`
- `/mf tp <worldKey>`
- `/mf gamerules <world>`
- `/mf info`
- `/mf reload`

The current plugin command implementation keeps existing `/mifron` subcommands and exposes them through `/mf` as the short alias.

## Configuration

`src/main/resources/config.yml` contains only settings that are read by the current implementation. FFA defaults are already at the latest balance version, so a first startup does not silently replace the bundled values.

Important behavior:

- `servers.*`, `hub`, `world-rules.spawn.*`: teleporter and spawn destinations. Coordinates can also be updated by commands.
- `build-world`: per-player build-world creation, border, platform, and WorldEdit boundary settings.
- `athletic.defaults`: default clear, record, and monthly ranking rewards.
- `regen.allowed-chunks`: the only chunks eligible for natural regeneration. An empty list disables regeneration targets.
- `barrel-shop`: offer and bargain slot counts.
- `auction.bid-step` / `auction.sneak-bid-step`: normal and sneaking bid increments. There is no auction fee setting in the current code.
- `ffa.kits.*`: equipment and active ability parameters. Sniper capacity is fixed to one shot, and Necromancer has no summon-count limit.
- `minoru-bridge.secret`: must remain empty in the repository and be set only in the server-side config.

Settings that do not affect runtime behavior are intentionally omitted rather than documented as configurable.

## Spawn Protection

Vanilla `spawn-protection` can conflict with custom shop interactions because it may cancel block interaction before shop logic can finish.

Recommended `server.properties` setting:

```properties
spawn-protection=0
```

Use Mifron's `ProtectionService` and central-area protection instead. Protected spawn/central chunks still block normal building, doors, trapdoors, containers, item frames, armor stands, signs, and hopper movement, while explicitly allowing Mifron shop purchases, auction bids, status-book UI, teleporter UI, and admin shop-wand actions.

## Shops

Shopified shelves and barrels must be managed with the shop wand:

- Right click: create shop
- Left click: remove shop

Normal block breaking, explosions, pistons, liquids, and burning do not break shop blocks or drop shop display/internal items.

## FFA

The current implementation provides 18 kits. Kit behavior shown in the selector is generated from the code and active config. The detailed verification procedure is in [`docs/ffa-kits-field-items-manual-test.md`](docs/ffa-kits-field-items-manual-test.md).

Notable rules:

- Sniper is single-shot and reloads manually with right click.
- Necromancer has seven independently cooled-down summon eggs, no summon-count cap, and summons expire after 20 seconds.
- Gambler rolls flat outgoing damage from -10 to 20; negative rolls heal the target. Incoming damage adjustment ranges from -5 to 5.
- Crusher rolls on both outgoing and incoming hits: 50% no explosion, 24% 4 damage, 15% 8 damage, 10% 16 damage, and 1% 32 damage.
- Vampire has no food item. Damage dealt restores health and hunger and builds an attack multiplier.

## Stored Data and Privacy

Mifron stores server-side gameplay data in the plugin data folder. Treat these files as private server data and do not publish them.

- `data.yml`: player UUIDs, names, MP balances, status/progression data, friend relationships, friend requests, and limited offline friend messages.
- `proposals.yml`: pending/reviewed proposal metadata, which may include Discord user IDs when imported by external tooling.
- `structures.yml`, `text-displays.yml`, `ffa-stats.yml`: admin-created server content, locations, generated-structure records, and FFA stats.

To remove a player's stored data, delete that player's UUID section from the relevant YAML files while the server is stopped, or use the available admin reset commands where applicable.

## Legal Notes

Mifron is an unofficial Minecraft server plugin and is not affiliated with, endorsed by, or approved by Mojang or Microsoft.

Do not sell or exchange Mifron MP or other in-game rewards for real-world money or transferable value. If the server is monetized, keep rewards compliant with the current Minecraft EULA and Usage Guidelines.
