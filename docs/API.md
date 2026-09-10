# PlexonRanks 3.0 Public API

PlexonRanks registers `PlexonRanksAPI` through Bukkit's ServicesManager after a successful enable. Consumers should resolve the registered service rather than constructing implementation classes.

## Authority and threading

SQLite-backed PlexonRanks state is authoritative for the current rank. LuckPerms is a managed projection of configured persistent permission/group rewards.

Online rank state is cached. The immutable current/next rank views read that cached state. `progression(UUID)` evaluates live player requirements and therefore requires an online, loaded player on the Bukkit primary thread. `history(UUID, int)` is asynchronous and returns a `CompletableFuture`; callers must not block the server thread waiting for it.

## 3.0 immutable views

Preferred methods:

```java
Optional<RankView> currentView(UUID playerId);
Optional<RankView> nextView(UUID playerId);
List<RankView> getRankViews();
Optional<ProgressionView> progression(UUID playerId);
CompletableFuture<List<RankHistoryEntry>> history(UUID playerId, int limit);
boolean canRankup(UUID playerId);
```

`RankView` exposes stable ID, order, tier, formatted display values, and terminal state without exposing mutable configuration internals.

`RequirementView` exposes type, current, required, normalized progress, completion and an immutable values map. Progress is clamped to `0.0..1.0` for presentation.

`ProgressionView` combines current rank, optional next rank, evaluated requirements, normalized overall progress, rank-up readiness and maximum-rank state.

History entries expose previous/new rank IDs, cause, transaction ID, status, timestamp and detail. The requested history limit is bounded by the persistence layer.

## 2.x compatibility surface

The following methods remain available in 3.0 for existing integrations but are deprecated in favor of immutable views:

```java
Optional<Rank> getRank(UUID playerId);
Optional<Rank> getNextRank(UUID playerId);
Optional<Rank> getRankById(String rankId);
List<Rank> getRanks();
```

They are not scheduled for removal in the 3.0 RC, but new integrations should use the immutable view methods.

## Events

`PlexonRankPreRankupEvent` is the cancellable pre-transition hook.

`PlexonRankChangeEvent` is emitted after a committed player/admin rank change is considered successful by the corresponding coordinator.

`PlexonRankupEvent` preserves the durable `transactionId` contract. Arbitrary external command rewards are an explicit irreversible boundary; if an external command fails after prior commands have executed, the authoritative rank remains advanced, the transaction is marked `EXTERNAL_REWARD_FAILED`, and PlexonRanks does not automatically replay the command sequence.

## Compatibility expectations

Consumers should treat rank IDs as stable persistence identifiers and display names/tags as presentation values. Do not infer progression solely from display text. Use `nextView`, `progression`, or the registry order exposed through immutable rank views.
