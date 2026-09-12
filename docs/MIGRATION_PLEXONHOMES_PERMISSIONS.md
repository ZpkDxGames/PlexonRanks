# PlexonHomes permission migration — PlexonRanks 3.0.1

PlexonRanks owns **which** home-capacity permission a progression rank receives. PlexonHomes owns **what** that permission means. LuckPerms remains the effective permission authority.

## Authoritative PlexonHomes contract

PlexonHomes 2.0.0 evaluates numeric `plexonhomes.limit.<N>` nodes and uses the highest effective numeric value. `plexonhomes.limit.unlimited` overrides numeric limits. If neither is present, PlexonHomes uses its own configured default.

The ordinary action nodes are `plexonhomes.sethome`, `plexonhomes.use`, `plexonhomes.delete`, `plexonhomes.rename`, and `plexonhomes.gui`. PlexonHomes declares those nodes default-true, so PlexonRanks does not redundantly grant them.

## Rank-by-rank migration

| Progression point | Legacy Essentials contract | PlexonHomes contract | Action |
| --- | --- | --- | --- |
| Base / Unranked | `essentials.sethome.multiple` with Essentials default 5 | `plexonhomes.limit.5` | move base ownership into PlexonRanks |
| Newbie I | `essentials.sethome.multiple.newbie` = 8 | `plexonhomes.limit.8` | replace |
| Tinkerer I | `essentials.sethome.multiple.tinkerer` = 12 | `plexonhomes.limit.12` | replace |
| Technician I | `essentials.sethome.multiple.technician` = 16 | `plexonhomes.limit.16` | replace |
| Inventor I | `essentials.sethome.multiple.inventor` = 20 | `plexonhomes.limit.20` | replace |
| Skilled I | `essentials.sethome.multiple.skilled` = 24 | `plexonhomes.limit.24` | replace |
| PRO I | `essentials.sethome.multiple.pro` = 30 | `plexonhomes.limit.30` | replace |

There is no authoritative unlimited progression tier, so `plexonhomes.limit.unlimited` is not granted by the bundled rank ladder.

## Cumulative permission behavior

The default PlexonRanks configuration uses cumulative permission rewards. A PRO player can therefore retain lower PlexonRanks-owned nodes such as 5, 8, 12, 16, 20 and 24 alongside 30. This is intentional: PlexonHomes deterministically selects the highest effective numeric limit. PlexonRanks does not add a second home-limit calculator or an aggressive node-cleanup subsystem.

PlexonRanks reconciliation only manages grants declared by its rank definitions. Staff, donor, manually assigned, inherited, or other-plugin permissions remain outside this ownership boundary.

## Operator cleanup after upgrade

After deploying PlexonRanks 3.0.1 with PlexonHomes 2.0.0 or newer, verify representative players with LuckPerms, then remove the obsolete Essentials home-limit grants from groups that previously supplied them. In particular, the old default `essentials.sethome.multiple` grant and the `.newbie`, `.tinkerer`, `.technician`, `.inventor`, `.skilled`, and `.pro` tiers are no longer required for home capacity.

Do not remove unrelated Essentials permissions that still back independent rank rewards.

Existing homes must never be deleted during a rank change or limit downgrade. PlexonHomes remains authoritative for over-limit existing-home behavior.
