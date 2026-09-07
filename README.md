# PlexonRanks

PlexonRanks is the rank progression module for the PlexonCraft ecosystem. It owns rank definitions, requirements, rank-up transactions, rewards, player rank persistence, GUIs, commands, placeholders, public APIs, and rank-domain events.

## 2.1.0 platform

- Paper 26.2
- Java 25
- Maven 3.9+
- PlexonCore 1.0.0 / Core API 1.x supported through an optional bridge

PlexonCore provides ecosystem registration, health reporting, shared integration discovery hints, and diagnostics. PlexonRanks continues to own all rank gameplay and data.

## Required plugins

- Vault, with an active economy provider
- LuckPerms

## Optional plugins

- PlexonCore 1.0.0 — enables Core module registration and ecosystem diagnostics. PlexonRanks can still start in standalone compatibility mode when Core is absent.
- PlaceholderAPI — optional unless a configured rank uses a `PLACEHOLDER` requirement.
- DiscordSRV — optional rank-up announcements when explicitly enabled.

## Player commands

- `/rank` — view current rank and progression
- `/ranks` — open the rank progression GUI
- `/rankup` — attempt the next rank

## Administration

- `/plexonranks admin` — open the visual rank editor
- `/plexonranks reload` — validate and atomically reload configuration
- `/plexonranks validate` — inspect configuration validity
- `/plexonranks diagnostics` — inspect runtime, database, providers, PlexonCore state and public API registration
- `/plexonranks info <player>` — inspect saved progression
- `/plexonranks setrank <player> <rank> [--grant-persistent]`
- `/plexonranks promote <player> [amount]`
- `/plexonranks demote <player> [amount]`
- `/plexonranks resetrank <player> [confirm]`
- `/plexonranks sync <player>`
- `/plexonranks backup`

## Core integration

When a compatible PlexonCore is available, PlexonRanks registers module ID `ranks` with supported Core API range `>=1.0 <2.0` and publishes module health as `STARTING`, `READY`, `DEGRADED`, or `FAILED` as appropriate.

When Core is absent, disabled, unavailable, or incompatible, the bridge falls back to standalone mode without moving rank logic into PlexonCore.

See [docs/PLEXONCORE.md](docs/PLEXONCORE.md).

## Persistence

PlexonRanks keeps its own SQLite database under `plugins/PlexonRanks/`. Rank-up persistence uses guarded transactions and stable transaction IDs. Version 2.1.0 does not require a database reset or destructive schema migration.

## Public API and events

PlexonRanks continues to expose `PlexonRanksAPI` through Bukkit `ServicesManager` and preserves the public rank events used by ecosystem integrations, including `PlexonRankupEvent` and its durable `transactionId()` value.

See [docs/API.md](docs/API.md).

## Configuration

Existing `config.yml`, `ranks.yml`, `messages.yml`, and menu configuration remain valid for the 2.1.0 infrastructure migration. Rank IDs, requirement math, rewards, progression balance, and player data are not intentionally changed by this release.

See [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Build

CI provisions the released PlexonCore 1.0.0 API JAR with a pinned SHA-256, installs it into Maven locally, then runs:

```bash
mvn -B -ntp clean verify
```

The final distribution check verifies that:

- exactly one `PlexonRanks-*.jar` is produced
- `plugin.yml` is present
- SQLite JDBC is included
- PlexonCore runtime classes are not shaded into PlexonRanks
- `SHA256SUMS.txt` validates successfully

## Upgrade from 2.0.3

Stop the server, replace `PlexonRanks-2.0.3.jar` with `PlexonRanks-2.1.0.jar`, keep the existing `plugins/PlexonRanks/` directory, then start the server and validate `/plexon modules`, `/plexon diagnostics`, `/plexonranks diagnostics`, `/rank`, `/ranks`, and one controlled `/rankup`.

See [docs/MIGRATION_2.1.0.md](docs/MIGRATION_2.1.0.md).

## License

See [LICENSE](LICENSE).
