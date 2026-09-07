# PlexonCore Integration

PlexonRanks 2.1.0 is a Core-aware module while remaining the owner of all rank-domain behavior.

## Module identity

- Module ID: `ranks`
- Display name: `PlexonRanks`
- Supported Core API range: `>=1.0 <2.0`
- Core runtime used for 2.1.0: PlexonCore 1.0.0

## Core mode

When PlexonCore is installed, enabled, exposes its API service, and is compatible with `>=1.0 <2.0`, PlexonRanks registers through `PlexonCoreAPI.modules()`.

Typical lifecycle:

```text
resolve Core
  -> register STARTING
  -> load config
  -> initialize SQLite
  -> resolve Vault and LuckPerms
  -> resolve optional integrations
  -> initialize rank services
  -> register commands/listeners
  -> register PlexonRanksAPI
  -> register PlaceholderAPI expansion when available
  -> load online rank profiles
  -> READY or DEGRADED
```

`DEGRADED` is used only for recoverable optional integration loss, such as an integration that is explicitly enabled but unavailable. Critical startup failures publish `FAILED` before safe shutdown.

## Standalone mode

PlexonRanks 2.1.0 retains standalone compatibility. Core linkage is isolated behind:

```text
com.zpkdxgames.plexonranks.integration.core.CoreBridge
com.zpkdxgames.plexonranks.integration.core.CoreBridgeFactory
com.zpkdxgames.plexonranks.integration.core.PlexonCoreBridge
com.zpkdxgames.plexonranks.integration.core.StandaloneCoreBridge
```

`CoreBridgeFactory` checks the plugin state first and only loads the Core-specific bridge reflectively when PlexonCore is present and enabled. This avoids optional-classloading failures when Core is absent.

If Core is unavailable or incompatible, rank gameplay continues in standalone mode when all native PlexonRanks requirements are healthy.

## What PlexonCore owns

- module registration and module health
- ecosystem diagnostics
- shared provider discovery hints
- shared infrastructure that can be adopted later when functionally equivalent

## What PlexonRanks owns

- rank definitions and stable IDs
- rank ordering and categories
- requirements and requirement math
- rank-up checks and transactions
- economy consumption
- rewards
- LuckPerms synchronization
- player rank data and SQLite database
- configuration and validation
- `/rank`, `/ranks`, `/rankup`, and administration commands
- rank GUIs and editor sessions
- PlaceholderAPI expansion
- DiscordSRV rank notifications
- `PlexonRanksAPI`
- `PlexonRankupEvent`, `PlexonRankChangeEvent`, and `PlexonRankPreRankupEvent`

## Provider discovery

Core integration state is treated as a hint only. PlexonRanks still validates provider-specific APIs directly:

- Vault must expose an actual economy provider.
- LuckPerms must expose its API service.
- PlaceholderAPI must be available when configured rank definitions require it.

Core reporting never makes a missing domain provider safe to ignore.

## Capabilities

The Core module descriptor advertises rank capabilities including rank progression, requirements, rewards, public API/events, SQLite persistence, Vault, LuckPerms, and PlaceholderAPI support.

When the module is healthy it also publishes `PLEXON_RANKS` integration capabilities such as:

- `rank-api`
- `rankup-event`
- `rank-change-event`
- `pre-rankup-event`
- `rankup-transaction-id`
- `luckperms-sync`

## Reload behavior

`/plexon reload` refreshes Core configuration and integration discovery without replacing the module registry, so an already registered PlexonRanks module remains registered.

`/plexonranks reload` continues to use PlexonRanks' own atomic configuration reload and rank repair path. After a successful reload the Core health state is republished.

No polling or per-rankup Core rediscovery is used.

## Shutdown

PlexonRanks unregisters its PlaceholderAPI expansion, Bukkit service registrations, SQLite resources, and Core module registration during plugin disable. Core should not retain a stale `READY` module after shutdown.
