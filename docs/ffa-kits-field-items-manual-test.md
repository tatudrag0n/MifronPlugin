# FFA Kits / Field Items Manual Test

Run on a Paper test server using the jar built from the same commit.

## Common flow

1. Confirm `/mv` is not registered by Mifron and still belongs to Multiverse-Core.
2. Run `/mf ffa setcenter`, `/mf ffa setkits`, and `/mf ffa createkits` as needed.
3. Confirm the selector shows all 18 active kits and invalid saved kit data falls back to `sword`.
4. Join with every kit and compare equipment, food, armor, and enchants with `config.yml`.
5. Confirm ordinary kit food is reusable, restores hunger/saturation, and has a 15-second cooldown.
6. Confirm death exits FFA cleanly, controls remain responsive, and the player can join again.
7. Confirm `/mf ffa leave` restores the pre-FFA inventory, armor, offhand, health, hunger, game mode, and scoreboard.
8. Confirm FFA items cannot be dropped, stored, crafted, placed in frames, or carried outside FFA.
9. Confirm friendly players can damage each other and FFA projectiles are not removed unexpectedly.

## Kit-specific checks

1. `axe`: full iron armor plus the armor bonus, Sharpness II diamond axe, and Slowness I.
2. `bow`: Power II/Infinity I bow, one reusable arrow, stone sword, and configured mixed armor.
3. `spear`: iron spear with Lunge II, iron backup sword, owned horse, and Speed I.
4. `crossbow`: six shots, 0.85 damage multiplier, automatic reload after emptying, and 65-tick reload.
5. `sword`: Sharpness I iron sword and reusable golden apple with a 90-second cooldown.
6. `shield`: stone sword, offhand shield, and configured mixed armor.
7. `trident`: Loyalty III trident returns to its owner and cannot be collected by another player.
8. `mace`: starts with one wind charge; after all charges are gone, ten return after 8 seconds. Final player damage is capped at 10 HP.
9. `gambler`: outgoing damage is an integer roll from -10 to 20. Negative rolls heal the target; zero cancels damage. Incoming adjustment is an integer from -5 to 5.
10. `wizard`: all five potions are reusable. Cooldowns are slow 10s, harm 13s, poison 16s, weakness 13s, and blindness 22s. Own negative splash effects do not affect the thrower.
11. `sniper`: capacity is fixed at one shot, damage multiplier is 2.5, and right click starts a 100-tick manual reload. Slowness IV applies during reload.
12. `vampire`: no food is supplied. Weakness I is permanent. Damage restores 30% as health and hunger; every 60 accumulated damage adds 12.5% attack power, up to four tiers. Tier four adds Speed I. Direct daylight deals 2 HP every effect tick.
13. `grappler`: no weapon or armor; Speed I, Strength I, Jump Boost I, and Resistance I remain active.
14. `assassin`: fatal dagger, poison dagger, and invisibility potion are supplied. The fatal dagger is consumed and leaves a player target at 2 hearts; poison lasts 4 seconds.
15. `necromancer`: all seven eggs have independent cooldowns. There is no summon-count cap. Summons do not target their owner, drop no rewards, expire after 20 seconds, and are removed on leave/death.
16. `trapper`: explosion, web, poison, and fire traps activate for enemies. Fire trap deals 2 initial damage, burns for 6 seconds, and has a 2.5-block radius. Ability cooldown is 16 seconds.
17. `bug_mania`: Infested remains active. Verify owner immunity, enemy targeting, no block burrowing, maximum five owned / 30 global silverfish, and 15% / 20% / 10% configured trigger chances.
18. `crusher`: test outgoing and incoming hits. Distribution is 50% no explosion, 24% 4 damage, 15% 8 damage, 10% 16 damage, and 1% 32 damage. A successful explosion starts a 30-tick activation cooldown.

## Field items and events

1. Register a point with `/mf ffa fielditem spawnpoint add`; verify list and numbered removal.
2. Spawn each rarity manually and confirm only FFA players can collect it and it is removed on exit/death.
3. Confirm field-item entities resist fire, cactus, explosions, and hopper collection.
4. Start and stop every event: `rain`, `snow`, `blizzard`, `berserk`, `speed`, `iron_body`, `overdrive`, `one_shot_bow`, `mp_fever`, `sky_spear`, `time_shift`, `heal_self`, and `heal_all`.
5. Verify same-target kill rewards: 50, 25, 12, 6, 3, 1, then 0 MP. A different target or 10 minutes resets the sequence.
6. Stop with `/mf ffa fielditem stop`, reload with `/mf ffa fielditem reload`, then stop the server and confirm tasks, entities, inventories, and active events are cleaned up.
