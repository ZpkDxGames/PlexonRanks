# PlexonRanks Public API

PlexonRanks owns rank-domain APIs and events. PlexonCore advertises the module but does not replace these contracts.

## Obtaining `PlexonRanksAPI`

```java
RegisteredServiceProvider<PlexonRanksAPI> registration =
        Bukkit.getServicesManager().getRegistration(PlexonRanksAPI.class);
if (registration == null) {
    return;
}
PlexonRanksAPI ranks = registration.getProvider();
```

The service is registered during PlexonRanks startup before the Core module is marked `READY`.

## API methods

```java
Optional<Rank> getRank(UUID playerId)
Optional<Rank> getNextRank(UUID playerId)
Optional<Rank> getRankById(String rankId)
List<Rank> getRanks()
boolean canRankup(UUID playerId)
```

These methods remain part of the 2.1.0 compatibility contract.

## `PlexonRankupEvent`

Class:

```text
com.zpkdxgames.plexonranks.event.PlexonRankupEvent
```

Important accessors:

```java
Player getPlayer()
Rank from()
Rank to()
String transactionId()
```

A successful player rank-up emits one `PlexonRankupEvent` after the saved rank transition and reward phase reaches the success-event point. Failed requirement checks, cancelled pre-rankup events, and failed rank commits do not emit the success event.

### Transaction ID semantics

`transactionId()` is the durable identifier for one logical rank-up transaction. It is generated once before the database commit and is persisted with the transaction. Integrations such as PlexonQuests can use it for deduplication.

The ID must remain non-empty and unique across independent successful rank-up transactions.

## Other events

### `PlexonRankPreRankupEvent`

A cancellable event fired before requirement consumption and commit. Consumers may cancel a player rank-up safely.

### `PlexonRankChangeEvent`

Emitted for committed player rank changes and applicable administrative transitions.

### `RankChangeCause`

Current causes include:

- `RANKUP`
- `ADMIN_SET`
- `ADMIN_PROMOTE`
- `ADMIN_DEMOTE`
- `ADMIN_RESET`

Consumers should not assume an administrative change is equivalent to a normal player `/rankup`.

## PlexonQuests interoperability

PlexonQuests continues to integrate through the PlexonRanks public API and events. The intended flow remains:

```text
/rankup
  -> PlexonRanks validates and commits
  -> PlexonRankupEvent fires once
  -> PlexonQuests receives the event
  -> matching rank objective progresses once
```

PlexonCore provides ecosystem discovery and health around this relationship; it is not a replacement event bus for rank gameplay.
