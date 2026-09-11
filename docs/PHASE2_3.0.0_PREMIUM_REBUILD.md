# PlexonRanks 3.0.0 Phase 2 Premium Rebuild

## Release boundary

- Baseline: published `v2.2.1` / `8f6fd363959041faf13399cd24bd315fa55176e8`.
- Phase 2 branch: `phase2/3.0.0-premium-rebuild`.
- Candidate version: `3.0.0-rc.1`.
- Stable `3.0.0` is forbidden until PlexonCraft runtime certification passes.
- Phase 2 PR remains draft/unmerged while runtime certification is pending.

## Authority model

SQLite remains the authoritative persisted player-rank state. The in-memory `RankService` cache is the online read model. LuckPerms is a reconciled permissions/group projection and reward integration, not a second rank database. Vault/TheosisEconomy remains the authoritative economy provider. PlaceholderAPI is read-only integration state and must never trigger synchronous SQLite work.

## Progression model

Every rank has a stable ID and unique enabled order. 3.0 adds `tier` and optional `next` metadata. Existing 2.2.1 ladders remain compatible: when `next` is absent, the next enabled order is the resolved edge. The final ordered rank with no explicit edge is terminal.

The resolved graph must pass all of the following before startup/reload can replace the active snapshot:

- unique IDs;
- unique enabled orders;
- exactly one enabled default/root rank;
- root is the lowest enabled order;
- every explicit edge targets an enabled rank;
- every edge advances to a strictly higher order;
- no self edge or cycle;
- all enabled ranks reachable from root;
- exactly one terminal rank.

Malformed configuration fails closed. Reload parses and validates a complete immutable candidate before swapping it; a rejected reload keeps the previous working snapshot.

## Requirements

3.0 preserves the real 2.2.1 requirement set only:

- `MONEY`;
- `XP_LEVELS`;
- `PLAYTIME`;
- `PERMISSION`;
- `PLACEHOLDER`;
- `ITEM`.

Blocks-broken and mob-kill counters are not part of the current repository and are not invented for feature count. Requirement progress exposes current, required, missing, normalized percentage and completion. Visual percentages are capped at 100%.

Money and XP/item requirements may be consumable. Playtime, permission and PlaceholderAPI requirements are never consumed. Requirement evaluation stays on Bukkit/Vault/PAPI cached/player state and performs no SQLite query.

## Rank-up transaction

The 3.0 transaction is single-player guarded and main-thread coordinated:

1. validate loaded/current rank;
2. resolve the next accessible graph node;
3. evaluate every requirement;
4. fire cancellable pre-rankup event;
5. create a fresh requirement plan;
6. preflight reward dependencies/capacity;
7. consume reversible requirements;
8. persist the rank compare-and-set transaction;
9. reconcile the LuckPerms projection;
10. grant reversible one-time rewards with rollback custody;
11. execute irreversible external command rewards last;
12. persist transaction/history result;
13. publish committed events/feedback.

If persistence, LuckPerms reconciliation or reversible reward execution fails before the irreversible boundary, the service compensates back to the previous rank, rolls back granted reversible rewards and refunds consumed requirements. Rollback failure is a critical diagnostic and must never be reported as success.

Console command rewards are the explicit irreversible boundary: they run only after every reversible gate succeeds. An unhandled/failed command marks the transaction as an external reward failure and is surfaced to administrators/player feedback; the plugin does not pretend that an already-executed external command can be atomically undone.

## Reward safety

Persistent permission/group rewards are reconciled through LuckPerms. Managed PlexonRanks grants not present in the desired rank plan are removed on reconciliation so demotion/reset cannot retain higher-rank managed grants indefinitely.

Item rewards are preflighted against the actual player inventory and never use world-drop overflow fallback. Runtime overflow is a transaction failure and the exact inventory snapshot is restored. Money deposits, XP and inventory mutation are reversible until the external-command boundary.

## Persistence and history

Database schema 2 adds bounded/queryable rank history without replacing existing `pr_players` or rank-transaction rows. Migration is idempotent and preserves every v2.2.1 player row. Before a schema-changing migration, the database creates a pre-3.0 backup after a WAL checkpoint. Corrupt/newer schema fails closed.

History records previous rank, new rank, cause, transaction ID, result/status, timestamp and bounded detail. GUI/API reads use asynchronous database access and never block high-frequency handlers.

## Player UX

Compatibility is retained:

- `/rank` opens the premium progression dashboard;
- `/rank info` retains the concise text progress report;
- `/rank menu` aliases the dashboard;
- `/ranks` opens the progression path;
- `/rankup` attempts the transaction.

The dashboard presents current rank, tier, next rank, total requirement completion, progress bar, reward summary, rank-up action, progression path, history, statistics and help. The existing paginated progression path remains the scalable view for completed/current/next/locked/terminal states. GUI identity is custom-holder based, never title based.

## Admin UX

Existing admin editor, inspect, set, promote, demote, reset, sync, reload, validate, backup and diagnostics are retained. Destructive state mutation requires explicit confirmation. History/transaction diagnostics expose committed and failed outcomes. Admin operations still write the same authoritative SQLite state and then reconcile projection state.

## PlaceholderAPI

Existing `%plexonranks_*%` names remain compatible. 3.0 additionally exposes clear aliases for current rank, next rank, progress, requirement current/required values and `can_rankup`. Resolution uses the existing short-lived in-memory display snapshot and never performs synchronous database IO.

## Public API and events

3.0 adds immutable API view records for current rank, next rank, requirement progress and overall progression while keeping deprecated 2.x accessors for migration. API view collections/maps are immutable copies.

Event timing:

- `PlexonRankPreRankupEvent`: synchronous, cancellable, before consumption;
- `PlexonRankChangeEvent`: synchronous, only after authoritative rank commit/cache acceptance;
- `PlexonRankupEvent`: synchronous, after the final transaction result is known.

## Performance contract

- no database query per requirement event;
- no new block/mob listeners;
- one bounded SQLite worker queue;
- no task-per-player scheduler;
- one menu refresh task only while viewers exist;
- PlaceholderAPI display snapshots remain short-lived/cached;
- all Bukkit inventory/player mutation remains on the primary thread.

## Automated release gate

The exact candidate CI must pass:

- production compilation;
- test compilation;
- complete test suite with exact executed/failure/error/skipped counts;
- progression graph contracts;
- requirement math/contracts;
- transaction/rollback contracts;
- persistence migration/history/restart contracts;
- PlaceholderAPI/API contracts;
- distribution verification;
- Java 25 class-file major version 69;
- SQLite included;
- PlexonCore, Vault, LuckPerms and PlaceholderAPI not shaded;
- source whitespace checks;
- candidate JAR generation;
- `SHA256SUMS.txt` generation and verification.

If any gate fails, fix the implementation/test infrastructure; never skip or weaken the gate.

## PlexonCraft runtime gate before stable

- existing player-rank migration from representative 2.2.1 data;
- `/rank`, `/ranks`, `/rankup` and full dashboard/path UX;
- every configured requirement type;
- insufficient requirement rejection;
- successful rank-up;
- money charge/refund;
- LuckPerms synchronization and demotion cleanup;
- reward execution and full-inventory rejection;
- maximum-rank state;
- admin mutation/confirmation/history;
- restart persistence;
- failed reload rollback;
- PlaceholderAPI aliases;
- cross-plugin Core behavior;
- Spark/MSPT comparison against 2.2.1;
- at least 30-minute soak;
- zero HIGH/CRITICAL defects.

Until these pass on the exact released RC artifact, classification is `RC RELEASED / RUNTIME PENDING`.
