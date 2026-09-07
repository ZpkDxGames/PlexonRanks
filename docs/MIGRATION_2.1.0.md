# Migrating PlexonRanks 2.0.3 to 2.1.0

PlexonRanks 2.1.0 is designed as a conservative infrastructure upgrade.

## Before upgrading

1. Stop the Paper server.
2. Back up the current `PlexonRanks-2.0.3.jar`.
3. Back up the complete `plugins/PlexonRanks/` directory.
4. Keep the existing PlexonCore 1.0.0 and PlexonQuests 3.1.0 installations if they are already deployed.

## Install

In the server `plugins/` directory:

```text
KEEP   PlexonCore-1.0.0.jar
KEEP   PlexonQuests-3.1.0.jar
REMOVE PlexonRanks-2.0.3.jar
ADD    PlexonRanks-2.1.0.jar
```

Do **not** delete `plugins/PlexonRanks/`.

Start the server normally.

## Expected startup

With compatible PlexonCore 1.0.0 present, the expected Core module state is:

```text
PlexonQuests — READY
PlexonRanks  — READY
```

If an explicitly enabled optional integration is unavailable, PlexonRanks may report `DEGRADED` while the core rank engine remains usable.

If PlexonCore is absent, PlexonRanks should start in standalone compatibility mode as long as its required dependencies are available.

## Validation

Run:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/quests diagnostics
/quests validate
/plexonranks diagnostics
/rank
/ranks
```

Then perform a controlled `/rankup` with a player who legitimately satisfies the next rank requirements.

Verify:

- the correct rank transition occurs once
- money/requirements behave as before
- LuckPerms synchronization remains correct
- rewards are delivered once
- `PlexonRankupEvent` is emitted once
- PlexonQuests receives rank progress once
- rank-category/slot refresh behavior remains intact

Also test a failed rank-up and a rapid repeated `/rankup` attempt. Neither may double-charge, double-reward, duplicate quest progress, or skip a rank.

## Reload validation

Run:

```text
/plexon reload
/plexonranks reload
```

After each reload, verify that rank commands still work, PlexonRanks remains visible to Core, the public API remains available, and no duplicate PlaceholderAPI expansion/listener behavior appears.

## Restart validation

Restart the server at least twice in staging. Player ranks must persist and PlexonRanks must register cleanly each time without duplicate module/API/event registration.

## Rollback

If rollback is necessary:

1. Stop the server.
2. Remove `PlexonRanks-2.1.0.jar`.
3. Restore `PlexonRanks-2.0.3.jar`.
4. Keep the existing `plugins/PlexonRanks/` data directory.
5. Start the server.

Version 2.1.0 does not intentionally introduce a destructive database migration, so the release is designed to remain reversible to 2.0.3.
