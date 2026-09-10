# PlexonRanks

Premium, configuration-driven progression for PlexonCraft.

> Current Phase 2 candidate: **3.0.0-rc.1**. This is a release candidate and must remain a prerelease until PlexonCraft runtime certification is complete.

## Requirements

- Paper 26.2 / Java 25
- Vault
- a Vault-compatible economy provider (PlexonCraft uses TheosisEconomy through Vault)
- LuckPerms
- PlexonCore 2.0.4 is supported as the shared Plexon runtime integration and is not shaded into this plugin
- PlaceholderAPI is optional unless a `PLACEHOLDER` requirement is configured
- DiscordSRV is optional

## Player experience

`/rank` opens the premium progression dashboard. It shows the current rank and tier, next rank, requirement readiness, a mastery/progress bar, reward preview, rank-up action, the long-term progression path, recent history, statistics, and help.

`/rank info` keeps the concise text report. `/rank menu` is a dashboard alias. `/rank path` and `/ranks [page]` open the paginated progression tree. `/rankup` attempts promotion.

Progression states are intentionally distinct: completed, current, next available, locked future, and maximum/mastery. GUI routing uses custom inventory holders rather than title matching.

## Progression model

SQLite is the authoritative player-rank store. LuckPerms is a synchronized permissions/group projection; it is not a competing rank database.

Ranks are ordered and may additionally declare:

```yaml
ranks:
  newbie-1:
    order: 1
    tier: Newbie
    next: newbie-2
```

`tier` and `next` are optional for 2.2.1 compatibility. If `tier` is omitted, PlexonRanks derives a human-readable tier from the stable rank ID. If `next` is omitted, the next enabled order is used.

Startup and reload reject duplicate IDs/orders, missing edges, backward edges, cycles, unreachable enabled ranks, multiple/no roots, and multiple/no terminal ranks. Reload validates a complete candidate before replacing the active immutable registry.

## Requirements

The repository-supported requirement types are:

- `MONEY`
- `XP_LEVELS`
- `PLAYTIME`
- `PERMISSION`
- `PLACEHOLDER`
- `ITEM`

PlexonRanks 3.0 does not invent block-breaking or mob-kill counters. Each evaluated requirement exposes current, required, missing, completed, and normalized progress values; visual percentages cap at 100%.

Money, XP-level and item requirements may be consumable. Playtime, permission and PlaceholderAPI requirements are never consumed.

## Rewards and transactions

Supported rewards remain `COMMAND`, `PERMISSION`, `LUCKPERMS_GROUP`, `MONEY`, `XP_LEVELS`, and `ITEM`.

Rank-up is guarded per player and uses a compare-and-set SQLite transition. The order is:

1. validate current and next rank;
2. evaluate requirements;
3. fire the cancellable pre-rankup event;
4. preflight integrations and item capacity;
5. consume reversible requirements;
6. commit the authoritative rank CAS;
7. reconcile LuckPerms;
8. grant reversible money/XP/item rewards;
9. execute external console-command rewards last;
10. persist final transaction/history status and emit committed events.

Failures before the external-command boundary compensate the authoritative rank first and then refund reversible custody. A stale compensation is rejected rather than overwriting newer state. Item reward overflow is never dropped into the world.

Console commands are explicitly the irreversible boundary: already executed external commands cannot be transactionally undone, so command rewards run only after all reversible gates have passed and any failure is recorded as `EXTERNAL_REWARD_FAILED`.

## Persistent LuckPerms projection

Persistent permission/group rewards are compiled into the desired state for each rank. On rank-up, demotion, reset, login reconciliation or `/pranks sync`, PlexonRanks removes only higher-rank nodes that are managed by PlexonRanks and are no longer desired. Unrelated LuckPerms nodes are untouched.

Configured LuckPerms groups must exist. Startup fails closed for missing groups, and reload validates candidate group references before swapping the live rank registry.

## Persistence and migration

PlexonRanks 3.0 uses SQLite schema 2. Existing `pr_players` and transaction data are preserved. Before a schema-1 database is migrated, a non-empty `database.db.pre-3.0.bak` copy is created after a WAL checkpoint. Migration is idempotent.

Schema 2 adds bounded/queryable rank history with previous rank, new rank, cause, transaction ID, status, timestamp and detail. History loading is asynchronous. See `docs/MIGRATION_3.0.0.md` before upgrading a production server.

## PlaceholderAPI

The existing `%plexonranks_*%` contract remains available. Premium compatibility aliases include:

- `%plexonranks_rank%`
- `%plexonranks_rank_display%`
- `%plexonranks_rank_number%`
- `%plexonranks_rank_tier%`
- `%plexonranks_next_rank%`
- `%plexonranks_next_tier%`
- `%plexonranks_progress%`
- `%plexonranks_money_current%` / `%plexonranks_money_required%`
- `%plexonranks_playtime_current%` / `%plexonranks_playtime_required%`
- `%plexonranks_xp_current%` / `%plexonranks_xp_required%`
- `%plexonranks_can_rankup%`

Placeholder resolution uses the online in-memory rank/readiness snapshot and does not synchronously query SQLite.

## Administration

`/plexonranks` (`/pranks`) provides help, editor, info, history, validation, reload, diagnostics, rank mutation, reconciliation and backup commands.

Useful commands:

```text
/pranks info <player>
/pranks history <player> [limit]
/pranks setrank <player> <rank> [confirm]
/pranks promote <player> [amount] [confirm]
/pranks demote <player> [amount] [confirm]
/pranks resetrank <player> [confirm]
/pranks sync <player>
/pranks validate
/pranks reload
/pranks diagnostics
/pranks backup
```

`setrank`, `promote`, `demote`, and `resetrank` require repeating the same operation with `confirm` inside a 30-second window. Administrative state changes reconcile LuckPerms before reporting success and compensate the authoritative rank if target projection fails.

`plexonranks.admin` grants all admin permissions. Individual nodes include `plexonranks.admin.info`, `.history`, `.setrank`, `.promote`, `.demote`, `.reset`, `.sync`, `.validate`, `.reload`, `.diagnostics`, `.editor`, and `.backup`.

## Public API

The Bukkit Services API exposes `PlexonRanksAPI`. Legacy 2.x `Rank` accessors remain deprecated for compatibility while 3.0 adds immutable `RankView`, `RequirementView`, `ProgressionView`, and asynchronous history. See `docs/API.md`.

## Performance

PlexonRanks performs no SQLite query per gameplay requirement event and introduces no block/mob listeners in 3.0. Online rank state is cached; the SQLite executor is single-threaded and bounded; PlaceholderAPI uses short-lived display snapshots; history is asynchronous; Bukkit inventory/player mutation stays on the primary thread.

`/pranks diagnostics` exposes DB schema, queue depth/high-water mark, DB failures, integration status, progression root/terminal, Core state and public API registration.

## Release and rollback

CI verifies Java 25 class-file major version 69, the complete tests, SQLite inclusion, PlexonCore/Vault/LuckPerms/PlaceholderAPI non-shading, source whitespace, the exact candidate JAR, and SHA-256.

Do not promote `3.0.0-rc.1` to stable until the runtime checklist in `docs/PHASE2_3.0.0_PREMIUM_REBUILD.md` passes against the exact released JAR.
