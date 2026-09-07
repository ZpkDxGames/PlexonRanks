# PlexonRanks Configuration

PlexonRanks 2.1.0 is an infrastructure migration. Existing production configuration should remain in place.

## Existing files

Do not delete or regenerate existing live configuration solely for the 2.1.0 upgrade. The plugin continues to use its current configuration set under `plugins/PlexonRanks/`, including the rank definitions and menu/message configuration already managed by PlexonRanks.

The migration does not intentionally change:

- stable rank IDs
- rank ordering
- requirement values or progression balance
- reward definitions
- GUI layout semantics
- player rank persistence
- SQLite schema version

## Integrations

Current integration settings continue to control native PlexonRanks behavior.

### PlaceholderAPI

If PlaceholderAPI integration is enabled and available, PlexonRanks registers its own expansion.

If any rank definition uses a `PLACEHOLDER` requirement and PlaceholderAPI is unavailable, startup fails safely rather than treating that requirement as satisfied.

### DiscordSRV

DiscordSRV remains optional. If it is explicitly enabled but unavailable, the rank engine can still run and Core health may report `DEGRADED`.

### PlexonCore

PlexonCore does not require a new rank configuration schema in 2.1.0. Core mode is discovered automatically at startup through the Bukkit plugin/service environment.

A compatible PlexonCore enables module registration and diagnostics. Its absence does not move or reset any rank data.

## Storage

The configured SQLite file remains inside `plugins/PlexonRanks/`. Existing path validation prevents escaping the plugin data directory.

The database continues to use WAL mode and the existing PlexonRanks schema. Version 2.1.0 does not require destructive database migration.

## Reload

`/plexonranks reload` validates candidate configuration before replacing the active snapshot. Existing reload callbacks continue to repair cached ranks where necessary. A successful reload also refreshes PlexonCore module health when Core mode is active.

Storage changes that already require restart continue to report that requirement rather than being applied unsafely at runtime.

## Rank IDs are contracts

Internal rank IDs should be treated as persistent data/API contracts. Visual display names can be changed through normal configuration, but changing stable IDs can affect saved player data and external integrations such as PlexonQuests rank-category mapping.

## 2.1.0 non-goals

Do not use the Core migration as a reason to rebalance rank costs, rewrite requirements, replace the GUI, move data into PlexonCore, or replace Vault/LuckPerms/PlaceholderAPI with Core-owned gameplay logic.
