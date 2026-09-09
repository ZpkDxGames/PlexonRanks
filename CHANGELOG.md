# Changelog

## 2.2.0

### Performance

- Added immutable parsed runtime settings for frequently-read configuration values.
- Added indexed rank-registry position/successor lookup and precomputed visible rank views.
- PlaceholderAPI now uses parameter-first fast paths so current-rank placeholders do not resolve next-rank requirements.
- Added short-lived per-player PlaceholderAPI display snapshots and formatted rank-text caching.
- Added recursion protection for nested `%plexonranks_*%` placeholder evaluation.
- Rank GUI refreshes now track only active viewers instead of scanning all online players.
- Rank GUI live requirement evaluation is limited to the next rank and shared across the card/navigation render pass.
- ITEM requirements now use a single inventory aggregation pass where possible.
- Persistent LuckPerms grant plans are precomputed per rank/config generation and reconciled in one user mutation.

### Reliability

- Added authoritative post-pre-event `RequirementPlan` evaluation for rank-up consumption.
- Batched ITEM consumption retains rollback support on failed rank transactions.
- Rank-up cooldown timing now uses monotonic time and clears state on player quit.
- SQLite scheduling remains serialized but is now bounded with explicit rejection/backpressure and queue instrumentation.
- Database shutdown drains queued operations before closing the SQLite connection.
- In-flight rank loading/reconciliation is deduplicated.
- Placeholder/menu/player caches are invalidated on quit, reload, and shutdown.

### Configuration

- Added `performance.placeholder-cache-ticks` for the short-lived PlaceholderAPI display cache.
- Added `performance.database.queue-capacity` for bounded serialized SQLite scheduling.
- Existing configuration schema and production rank definitions remain compatible.

### Tests and compatibility

- Added regression coverage for indexed rank views, requirement-plan invariants, and database queue instrumentation.
- Java 25 / Paper 26.2 remain the target platform.
- Existing `PlexonRanksAPI` and rank event contracts remain preserved.
- Existing SQLite schema, player rank data, transaction IDs, rank IDs/order, requirements, rewards, and progression semantics are preserved.

No player-data reset or destructive database migration is required for 2.2.0.

## 2.1.0

### Added

- PlexonCore 1.x module bridge with module ID `ranks`.
- Safe standalone fallback when PlexonCore is absent, disabled, unavailable, or incompatible.
- Core module lifecycle reporting (`STARTING`, `READY`, `DEGRADED`, `FAILED`) and clean unregistration.
- Core-aware `PLEXON_RANKS` integration capability publication.
- `/plexonranks diagnostics` runtime/provider/Core diagnostics.
- Public API/event contract tests for PlexonQuests compatibility.
- Additional rank transaction concurrency coverage.
- README and API/Core/configuration/migration documentation.
- SHA-256 distribution generation and validation in CI/release workflows.

### Changed

- Target platform moved from Java 21 / Paper 1.21.10 to Java 25 / Paper 26.2.
- Project version moved from 2.0.3 to 2.1.0.
- PlexonCore 1.0.0 is a `provided` compile dependency and is not shaded into the plugin.
- CI provisions the released PlexonCore 1.0.0 artifact using a pinned SHA-256.
- Release workflow is tag-driven (`v*`) instead of creating releases from ordinary `main` pushes.
- SQLite JDBC was aligned to a Java 25-compatible release while preserving the existing PlexonRanks schema and data location.

### Preserved

- Existing rank IDs, progression balance, requirements, rewards, GUIs, commands, and configuration semantics.
- Existing SQLite player-rank data and transaction schema.
- `PlexonRanksAPI` public methods.
- `PlexonRankupEvent`, `PlexonRankChangeEvent`, and `PlexonRankPreRankupEvent` contracts.
- Rank-up transaction IDs and exactly-once stale-state database protection.
- Vault and LuckPerms as required native providers.
- PlaceholderAPI and DiscordSRV integration behavior.

No player-data reset or destructive database migration is required for 2.1.0.
