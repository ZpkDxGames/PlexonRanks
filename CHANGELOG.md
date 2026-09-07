# Changelog

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
