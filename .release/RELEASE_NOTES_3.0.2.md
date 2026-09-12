# PlexonRanks 3.0.2

Stable PlexonFamily permission-alignment update for the bundled rank ladder.

## Replaced plugin ownership

- `/workbench`: `essentials.workbench` -> `plexonutility.workbench`
- `/disposal`: `essentials.disposal` -> canonical PlexonUtility `/trash` with `plexonutility.trash`
- `/enderchest`: `essentials.enderchest` -> `plexonutility.enderchest`
- `/feed`: `essentials.feed` -> `plexonutility.feed`
- `/back` and back-after-death: Essentials nodes -> `plexontravel.back`
- colored chat: Essentials/VentureChat nodes -> `plexonchats.formatting`

## Home limits

Home capacity remains owned by PlexonHomes through `plexonhomes.limit.<N>`. PlexonUtility may surface PlexonHomes in its utility experience, but it does not define a separate home-limit permission contract.

## Preserved compatibility

Essentials rewards without a current PlexonFamily equivalent remain unchanged: head, hat, compass, depth, condense, getpos, ext, and keepxp. Existing iDisguise, PlexonKeys, and claim-block rewards are unchanged.
