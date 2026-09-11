# PlexonRanks

Premium, configuration-driven progression for PlexonCraft.

Current stable line: **3.0.0**.

## Runtime requirements

- Paper 26.2 / Java 25
- Vault
- a Vault-compatible economy provider; PlexonCraft uses TheosisEconomy through Vault
- LuckPerms
- PlexonCore 2.0.4 as the shared Plexon runtime integration; it is provided externally and is not shaded
- PlaceholderAPI only when `PLACEHOLDER` requirements are configured
- DiscordSRV optional

SQLite JDBC is bundled in the plugin JAR. Vault, LuckPerms, PlaceholderAPI, Paper and PlexonCore remain external runtime dependencies.

## Player experience

`/rank` opens the progression dashboard with current rank/tier, next rank, requirement readiness, progress, rewards, path, history, statistics, help and maximum/mastery states.

`/rank info` keeps the concise text report. `/rank menu` is a dashboard alias. `/rank path` and `/ranks [page]` open the paginated progression tree. `/rankup` attempts promotion.

Progression states are distinct: completed, current, next available, locked future and maximum/mastery. GUI routing uses custom inventory holders rather than title matching.

## Progression authority

SQLite is the authoritative player-rank store. LuckPerms is the synchronized permissions/group projection; it is not a competing rank database.

Ranks are ordered and may additionally declare `tier` and `next`. For compatibility with 2.2.1 configurations, omitted tiers are derived from stable rank IDs and omitted `next` values use the next enabled order.

Startup and reload reject invalid progression graphs, including duplicate IDs/orders, missing or backward edges, cycles, unreachable enabled ranks, multiple/no roots and multiple/no terminal ranks. Reload validates a complete candidate before replacing the active immutable registry.

## Requirements

Supported requirement types are:

- `MONEY`
- `XP_LEVELS`
- `PLAYTIME`
- `PERMISSION`
- `PLACEHOLDER`
- `ITEM`

Money, XP-level and item requirements may be consumable. Playtime, permission and PlaceholderAPI requirements are never consumed.

For transaction consistency, a rank may not configure the same Bukkit material more than once as a **consumable** `ITEM` requirement. Combine repeated consumable requirements for one material into a single entry. Repeated non-consumable item checks and consumable requirements for different materials remain valid.

Each evaluated requirement exposes current, required, missing, completed and normalized progress values; visual percentages cap at 100%.

## Rewards and transactions

Supported rewards are `COMMAND`, `PERMISSION`, `LUCKPERMS_GROUP`, `MONEY`, `XP_LEVELS` and `ITEM`.

Rank-up is guarded per player and uses a compare-and-set SQLite transition. The authoritative order is:

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

Console commands are the irreversible boundary: already executed external commands cannot be transactionally undone, so command rewards run only after all reversible gates have passed and any failure is recorded as `EXTERNAL_REWARD_FAILED`.

## Persistent LuckPerms projection

Persistent permission/group rewards are compiled into the desired state for each rank. On rank-up, demotion, reset, login reconciliation or `/pranks sync`, PlexonRanks removes only higher-rank nodes managed by PlexonRanks that are no longer desired. Unrelated LuckPerms nodes are untouched.

Configured LuckPerms groups must exist. Startup fails closed for missing groups, and reload validates candidate group references before swapping the live rank registry.

## Persistence and migration

PlexonRanks 3.0 uses SQLite schema 2. Existing `pr_players` and transaction data are preserved. Before a schema-1 database is migrated, a non-empty `database.db.pre-3.0.bak` copy is created after a WAL checkpoint. Migration is idempotent.

Schema 2 adds bounded/queryable rank history with previous rank, new rank, cause, transaction ID, status, timestamp and detail. History loading is asynchronous. See `docs/MIGRATION_3.0.0.md` before upgrading a production server.

## PlaceholderAPI

The existing `%plexonranks_*%` contract remains available. Compatibility aliases include `%plexonranks_rank%`, `%plexonranks_rank_display%`, `%plexonranks_rank_number%`, `%plexonranks_rank_tier%`, `%plexonranks_next_rank%`, `%plexonranks_next_tier%`, `%plexonranks_progress%`, money/playtime/XP current and required values, and `%plexonranks_can_rankup%`.

Placeholder resolution uses online in-memory rank/readiness snapshots and does not synchronously query SQLite.

## Administration

`/plexonranks` (`/pranks`) provides help, editor, info, history, validation, reload, diagnostics, rank mutation, reconciliation and backup commands.

Common operations include:

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

`setrank`, `promote`, `demote` and `resetrank` require repeating the same operation with `confirm` inside a 30-second window. Administrative state changes reconcile LuckPerms before reporting success and compensate the authoritative rank if target projection fails.

## Public API

The Bukkit Services API exposes `PlexonRanksAPI`. Legacy 2.x `Rank` accessors remain deprecated for compatibility while 3.0 provides immutable `RankView`, `RequirementView` and `ProgressionView` surfaces plus asynchronous history. See `docs/API.md`.

## Performance and reliability

PlexonRanks performs no SQLite query per gameplay requirement event and introduces no block/mob listeners. Online rank state is cached; the SQLite executor is single-threaded and bounded; PlaceholderAPI uses short-lived display snapshots; history is asynchronous; Bukkit inventory/player mutation stays on the primary thread.

`/pranks diagnostics` exposes DB schema, queue depth/high-water mark, DB failures, integration status, progression root/terminal, Core state and public API registration.

## Stable release verification and rollback

Build CI verifies Java 25 class major 69, a non-empty all-green test suite, SQLite inclusion, non-shading of PlexonCore/Vault/LuckPerms/PlaceholderAPI/Paper, exact `plugin.yml` version, source whitespace and SHA-256 provenance.

The stable publisher only accepts an exact current `main` commit with accepted Phase 3 ancestry and a non-prerelease project version. It rebuilds/tests that source, publishes the JAR plus checksum/test/provenance evidence, downloads the published assets and verifies the released bytes before completing.

Live PlexonCraft runtime certification remains a deployment follow-up and may be recorded as `NOT_EXECUTED` in GitHub release provenance; it does not block source/release closure.

Rollback baseline: `v2.2.1` at `8f6fd363959041faf13399cd24bd315fa55176e8`. Because 3.0 uses schema 2, production rollback must use the matching pre-3.0 database backup where required.
