# PlexonRanks 3.0 Configuration

PlexonRanks loads `config.yml`, `ranks.yml`, `menus.yml`, and `messages.yml` as one validated runtime model. `/pranks reload` parses and validates a complete candidate before replacing the active snapshot. A rejected candidate leaves the previous live model active.

## Rank graph

Each rank keeps a stable ID and explicit `order`. Phase 2 adds optional presentation tier and explicit successor metadata:

```yaml
ranks:
  newbie-1:
    order: 1
    tier: Newbie
    next: newbie-2
    enabled: true
    visible: true
```

`next` takes precedence when present. If omitted, the next enabled rank by unique order is used. `tier` is presentation/grouping metadata only; omitting it derives a deterministic tier from the stable ID (`technician-4` → `Technician`, `unranked` → `Starter`). It does not change requirements or promotion semantics.

The resolved enabled graph must have exactly one declared default/root, one terminal rank, unique IDs/orders, forward-only valid edges, no cycle, and no unreachable enabled rank.

## Supported requirements

The audited requirement types are exactly:

- `MONEY`
- `XP_LEVELS`
- `PLAYTIME`
- `PERMISSION`
- `PLACEHOLDER`
- `ITEM`

Do not configure blocks-broken or mob-kill requirement types; they are not part of PlexonRanks 3.0.

Consumable requirement support is intentionally limited to money, XP levels and items. Playtime, permission and PlaceholderAPI requirements are observational and are never consumed.

## Supported rewards

- `COMMAND`
- `PERMISSION`
- `LUCKPERMS_GROUP`
- `MONEY`
- `XP_LEVELS`
- `ITEM`

Persistent permission/group rewards form the PlexonRanks-owned LuckPerms projection. Referenced LuckPerms groups are validated during startup/reload. Unrelated LuckPerms permissions/groups are not removed by reconciliation.

Item rewards preflight inventory capacity and fail before promotion when the configured reward cannot fit. PlexonRanks 3.0 does not drop reward overflow into the world.

External console command rewards execute last. They are inherently not generically reversible and are never automatically replayed after a restart or failed external-reward transaction.

## Storage and migration

`storage.type` remains `SQLITE`. Schema 2 preserves `pr_players` and existing transaction data and adds rank history. A schema-1 → schema-2 transition requires a valid, non-empty sibling backup named `<database filename>.pre-3.0.bak`. An invalid pre-existing migration backup causes startup to fail closed.

See `MIGRATION_3.0.0.md` for production upgrade and rollback instructions.

## Missing saved rank behavior

`join.missing-rank-fallback` controls a saved rank ID that is no longer present/enabled:

- `FAIL`: preserve the row and reject loading until the configuration/data issue is fixed. This is the 3.0 default for new installations.
- `FIRST`: explicitly repair the player to the configured default/root rank and record the repair. Existing configurations that already selected `FIRST` retain that explicit behavior.

PlexonRanks does not silently choose a migration mapping outside this configured policy.

## Integrations

Vault and a Vault-compatible economy provider are required. PlexonCraft uses TheosisEconomy through Vault; PlexonRanks depends on the Vault economy interface rather than Theosis-specific classes.

LuckPerms is required for the managed projection. PlaceholderAPI is optional unless at least one `PLACEHOLDER` requirement is configured. DiscordSRV remains optional.

PlexonCore is a shared runtime integration and is not shaded into the PlexonRanks JAR.

## Performance controls

The SQLite executor is serialized and bounded through `performance.database.queue-capacity`. PlaceholderAPI uses a short-lived online display snapshot controlled by `performance.placeholder-cache-ticks`. Neither PAPI placeholder resolution nor ordinary requirement display performs a synchronous SQLite history lookup.
