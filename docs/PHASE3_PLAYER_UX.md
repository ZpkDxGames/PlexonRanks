# PlexonRanks Phase 3 — Player UX Product Layer

## Accepted boundary

Phase 3 was originally based on the Phase 2 `3.0.0` premium rebuild and was published through RC1/RC2 before final integration. The accepted Phase 3 implementation head is `0e5023c6e838ad064da58cf6ac8c57e06c02c5c7`; stable `3.0.0` preserves that product architecture plus the final repository-audit validation guard for duplicate consumable item materials.

Phase 3 does not replace the progression engine. SQLite schema 2, rank-state persistence, graph validation, requirement evaluation/consumption, Vault transactions, LuckPerms projection, reward execution/compensation, admin compare-and-set mutation, API views, PlaceholderAPI caches and transactional reload remain authoritative.

## Player journey

Before Phase 3, `/rank` already opened a premium dashboard, but progression was split across independent menus. The path could rank up through an undisclosed right-click, History exposed implementation-oriented values, asynchronous History completion could open over a newer menu, and the rank list redrew active viewers periodically.

Phase 3 presents one hierarchy:

```text
RANK DASHBOARD
  -> NEXT RANK
       -> REQUIREMENTS
       -> REWARDS
       -> explicit RANK UP when ready
  -> PROGRESSION PATH
       -> RANK DETAILS
  -> HISTORY
  -> STATISTICS
  -> HELP
```

The dashboard answers current rank, next rank, readiness, the most useful remaining blocker, reward summary and the next useful action. At maximum rank it presents `MAXIMUM RANK • MASTERED` and routes the primary action to progression mastery rather than a dead Rank Up control.

## Explicit Rank Up

Rank Up is never a hidden click-type secret in Phase 3. The path and Rank Details use a labelled primary action. The legacy `rankup.right-click-next-rank` setting remains parsed for configuration compatibility, but no Phase 3 player menu calls rank-up from right-click.

Every GUI Rank Up path re-reads current authoritative rank, next rank and requirement progress before delegating to `RankupService.attempt(player)`. The GUI does not consume requirements, withdraw money, grant rewards, mutate LuckPerms, write SQLite or advance rank state itself. Phase 2 cooldown, processing guard, preflight, compare-and-set commit, compensation and irreversible-command boundaries remain authoritative.

## Requirements

All six existing requirement types remain evaluated by `RequirementEngine` through `RenderService.progress`: Money, XP levels, Playtime, Permission/access, Placeholder-backed objectives and Items.

`RequirementCard` receives an already evaluated `RequirementProgress` and maps it to player text, current/required values, progress where meaningful and a next-step summary. Permission nodes and raw placeholder keys are not shown by default Phase 3 surfaces. The dashboard displays one useful blocker plus `+N more requirements` when additional blockers remain.

Stable configuration validation rejects multiple consumable `ITEM` requirements resolving to the same Bukkit material within one rank. This keeps displayed/evaluated readiness consistent with batched item consumption. Repeated non-consumable item checks and consumable requirements for different materials remain supported.

## Rewards

Reward rendering never executes or deserializes rewards. It reads configured display metadata. When safe display metadata is absent, known reward types receive a player-facing fallback; COMMAND rewards never fall back to executable command strings. Exact ItemStack delivery and fail-closed overflow remain in `RewardEngine`.

## Progression Path and Rank Details

The path retains configured rank materials, names, slots and templates where compatible. States are presented as `COMPLETED`, `CURRENT RANK`, `NEXT RANK`, `READY TO RANK UP`, `LOCKED`, and `MAXIMUM RANK • MASTERED`.

Clicking a rank opens Rank Details instead of relying on click-type secrets. Details show display name, state, progression position, requirements, rewards and relationship to current progression. Locked ranks state that earlier progression cannot be skipped. Back restores the exact originating path page.

## History lifecycle safety

History stays asynchronous. Opening it immediately shows `LOADING`; completion returns to the primary thread and updates only if the player is online, the exact same History inventory is still open, the holder is still History, and its request UUID still matches. A stale completion therefore cannot reopen History, replace another menu, or overwrite a newer request.

History maps known causes/statuses into player language and degrades unknown values to safe generic labels. Raw transaction IDs, SQLite wording, CAS terminology and internal rank IDs are not rendered.

## Refresh and performance

Phase 2 used one shared repeating RankList refresh task. It was bounded, but each redraw could reevaluate next-rank requirements including a Vault balance read.

Phase 3 removes periodic path redraw entirely. Legacy refresh settings remain parse-compatible, while presentation refresh occurs only on meaningful actions: open, page change, explicit Refresh, details navigation, dashboard navigation, rank action or config reload. No per-viewer repeating task is introduced.

Requirement evaluation already batches money/playtime/item reads inside `RequirementEngine`; Phase 3 adds no database reads, LuckPerms storage calls, filesystem I/O or serialization passes to cosmetic redraws.

## View models and compatibility

Presentation flow is authoritative current rank + next accessible rank + `RequirementProgress` → `RankDashboardViewModel` → `RequirementCard`/reward/state presentation → current GUI. Holders carry navigation and stale-view tokens only; they do not mirror persistence or transaction state machines.

Schema versions remain unchanged. Existing rank definitions, database and LuckPerms groups are not reset. Existing menu materials and rank slots continue to be read where compatible. Old right-click and refresh keys remain parseable and are documented as compatibility settings whose Phase 3 behavior is explicit/event-driven. Admin diagnostics, migrations and 30-second mutation confirmations remain separate.

## Runtime validation plan

1. `/rank` Dashboard and current/next rank.
2. Incomplete requirements and blocker summary.
3. READY TO RANK UP and explicit Rank Up.
4. Rapid duplicate Rank Up.
5. Vault/Theosis charge/refund correctness.
6. Item, XP, playtime, money and placeholder/statistic requirements.
7. Reward delivery and full-inventory fail-closed behavior.
8. LuckPerms projection.
9. Progression Path states/pagination.
10. Rank Details and exact Back page restoration.
11. Locked/future rank cannot skip progression.
12. Maximum-rank mastery state.
13. History Loading/completion and navigation away during load.
14. PlaceholderAPI/TAB usage.
15. Reconnect, reload rollback and restart persistence.
16. Repeated Dashboard/Path/Refresh navigation under Spark/MSPT.
17. Multiple simultaneous path viewers; verify no repeating viewer task.
18. Integrated >=30-minute soak.

Live runtime certification remains separate from source CI and is a post-release deployment follow-up. It does not block GitHub stable source/release closure.
