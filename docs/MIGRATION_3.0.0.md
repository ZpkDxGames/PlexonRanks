# Migrating PlexonRanks 2.2.1 → 3.0.0

This guide applies to the Phase 2 `3.0.0-rc.1` candidate built from the published `v2.2.1` baseline.

## Before upgrading

1. Stop the server cleanly.
2. Back up the complete `plugins/PlexonRanks/` directory.
3. Retain the published `PlexonRanks-2.2.1.jar` for rollback.
4. Do not delete `database.db`, `ranks.yml`, `config.yml`, `menus.yml`, or `messages.yml`.
5. Confirm Vault, your economy provider, and LuckPerms are available. If `PLACEHOLDER` requirements are configured, PlaceholderAPI must also be available.

## Database migration

PlexonRanks 2.2.1 uses SQLite schema 1. PlexonRanks 3.0 uses schema 2.

On first 3.0 startup with a schema-1 database, PlexonRanks checkpoints SQLite WAL state and creates:

`database.db.pre-3.0.bak`

The migration does not rewrite existing `pr_players` ranks or remove existing transaction rows. It adds the rank-history table/index and updates the schema marker. The fixed backup filename makes the migration idempotent: a successful backup is not repeatedly replaced on later starts.

If a database advertises a schema newer than the running plugin supports, startup fails closed.

## Rank configuration compatibility

Existing 2.2.1 ordered ladders remain valid. 3.0 adds optional rank fields:

```yaml
tier: Newbie
next: newbie-2
```

If `next` is absent, progression falls back to the next enabled rank by order. If `tier` is absent, PlexonRanks derives a display tier from the stable rank ID.

Before accepting the configuration, 3.0 validates the resolved graph: one root, one terminal, unique IDs/orders, valid forward edges, no cycles, and no unreachable enabled rank. Invalid configuration does not partially replace the current registry during `/pranks reload`.

## LuckPerms changes

SQLite remains authoritative for a player's current PlexonRanks rank. LuckPerms is a synchronized projection for persistent permission/group rewards.

3.0 reconciles only nodes declared by PlexonRanks reward configuration. It does not remove unrelated permissions or groups. Missing configured LuckPerms groups fail startup/reload validation rather than being discovered after a rank transition.

After upgrading, run `/pranks sync <player>` on a representative account and verify expected nodes before broad runtime certification.

## Reward safety changes

Item reward overflow is no longer dropped into the world. A rank-up preflight rejects a reward plan that cannot fit safely.

Rank-up now performs reversible stages before external console command rewards. If a reversible stage fails, the service attempts an authoritative compare-and-set rollback before refunding requirements/rewards. External command rewards remain explicitly irreversible and execute last.

## Rollback to 2.2.1

Do not run 2.2.1 against a database that has been migrated/written by 3.0.

To rollback:

1. stop the server;
2. restore the complete pre-upgrade `plugins/PlexonRanks/` directory backup, including the schema-1 database and YAML files;
3. reinstall `PlexonRanks-2.2.1.jar`;
4. start the server and verify representative ranks/placeholders/LuckPerms state.

The automatically generated `database.db.pre-3.0.bak` is an additional safety copy, not a replacement for the full-directory backup.

## Runtime certification before stable 3.0.0

Validate existing player migration, `/rank`/`/ranks`/`/rankup`, every configured requirement, failed/successful promotion, money charge/refund, LuckPerms reconciliation, rewards/full-inventory behavior, maximum rank, admin mutation, restart persistence, failed reload rollback, PlaceholderAPI, Core integration, Spark/MSPT comparison, and at least a 30-minute soak with zero HIGH/CRITICAL defects.
