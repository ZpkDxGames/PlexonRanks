# PlexonRanks 3.0.0

Stable GitHub repository closure of the accepted PlexonRanks Phase 2 / Phase 3 runtime line, plus one source-audit correctness guard discovered during final review.

## Progression and transaction safety

- SQLite schema 2 remains the authoritative player-rank store; LuckPerms remains a synchronized permission/group projection.
- Rank-up retains per-player processing guards, authoritative current/next-rank validation, requirement preflight, compare-and-set rank commits and bounded history.
- Reversible requirement custody is compensated on pre-command failure; persistent LuckPerms projection is reconciled before success; irreversible console-command rewards remain the final boundary.
- Administrative rank mutations retain actor/action/value/expected-rank/config-generation confirmation and compare-and-set protection.
- PlaceholderAPI remains cache-only for online progression/readiness state; no gameplay placeholder performs a synchronous SQLite query.

## Final source-audit fix

Stable configuration validation now rejects duplicate **consumable** `ITEM` requirements that resolve to the same Bukkit material within one rank. Previously, each requirement could independently report ready while batched consumption correctly required the combined quantity. Operators should combine repeated consumable requirements for the same material into one entry.

Repeated non-consumable item checks and consumable requirements for different materials remain valid.

## Phase 3 player UX

- `/rank` provides the progression dashboard with current/next rank, readiness, rewards, path, history, statistics and mastery states.
- `/rank path` and `/ranks [page]` provide the progression tree; `/rankup` remains the authoritative promotion entry point.
- GUI actions re-read authoritative state and delegate rank-up to the transaction service; GUI code does not consume requirements, mutate SQLite or directly change LuckPerms.

## Compatibility and release verification

- Java 25 / Paper 26.2.
- PlexonCore 2.0.4 is an external provided dependency and is not shaded.
- Vault, LuckPerms and PlaceholderAPI remain external; SQLite JDBC remains bundled.
- The exact final `main` source is rebuilt and tested before publication.
- Stable publication includes `PlexonRanks-3.0.0.jar`, `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`; the workflow downloads those assets and verifies checksum and exact-source provenance before completing.

Live PlexonCraft runtime certification may remain `NOT_EXECUTED` in release provenance and is a post-release deployment follow-up rather than a GitHub source/release blocker.

Rollback baseline: `v2.2.1` / `8f6fd363959041faf13399cd24bd315fa55176e8`.
