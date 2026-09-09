# Migrating PlexonRanks 2.1.0 → 2.2.0

PlexonRanks 2.2.0 is a performance and reliability release. It preserves the existing rank progression model and SQLite schema; no player-data reset or destructive migration is required.

## Before upgrading

1. Stop the Paper server cleanly.
2. Back up `plugins/PlexonRanks/`, especially `database.db`, `config.yml`, `ranks.yml`, `menus.yml`, and `messages.yml`.
3. Keep the existing PlexonCore, Vault/economy, LuckPerms, PlaceholderAPI, and DiscordSRV setup unless you are intentionally changing those integrations separately.

## Install

1. Remove the old `PlexonRanks-2.1.0.jar` from the server's `plugins/` directory.
2. Install `PlexonRanks-2.2.0.jar` from the GitHub 2.2.0 release.
3. Keep the existing `plugins/PlexonRanks/` data directory.
4. Start the server normally.

## Compatibility guarantees

2.2.0 preserves:

- rank stable IDs and ordering
- rank requirements/rewards and progression semantics
- existing SQLite player-rank rows and transaction schema
- rank-up transaction IDs and guarded expected-current-rank persistence
- `PlexonRanksAPI`
- `PlexonRankupEvent`, `PlexonRankChangeEvent`, and `PlexonRankPreRankupEvent`
- existing configuration schema/production rank definitions

## New optional performance settings

The default configuration now supports:

```yaml
performance:
  placeholder-cache-ticks: 4
  database:
    queue-capacity: 1024
```

`placeholder-cache-ticks` controls the short-lived display snapshot shared across repeated PlexonRanks PlaceholderAPI requests. `database.queue-capacity` bounds the serialized SQLite work queue so overload is explicit rather than allowing unbounded queued work.

Existing configurations remain valid; the safe configuration upgrader/default handling supplies compatible defaults.

## Post-upgrade checks

Run these checks after startup:

- `/plexonranks diagnostics`
- `/rank`
- `/ranks`
- one controlled `/rankup`
- reconnect one player and confirm persistent LuckPerms grants remain correct
- verify PlaceholderAPI-powered scoreboards/tab/chat still render rank values correctly

For a production server, also capture a Spark profile under normal PlaceholderAPI/GUI activity after deployment. The 2.2.0 code paths are designed to reduce repeated requirement evaluation, online-player menu scans, LuckPerms reconciliation work, and unbounded database scheduling, but production profiling remains the best verification for the exact plugin stack.

## Rollback

If rollback is necessary:

1. Stop the server.
2. Replace `PlexonRanks-2.2.0.jar` with the previous 2.1.0 JAR.
3. Keep the existing database unless you have separately modified it outside PlexonRanks.
4. Restore the pre-upgrade `plugins/PlexonRanks/` backup only if configuration files were intentionally changed during deployment.

Because 2.2.0 does not introduce a destructive database schema migration, normal JAR rollback does not require resetting player ranks.