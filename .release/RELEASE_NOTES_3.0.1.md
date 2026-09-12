# PlexonRanks 3.0.1

Stable permission-contract migration for PlexonHomes.

## Rank-based home limits

- Replaces the legacy Essentials/EssentialsX multihome ownership model with the PlexonHomes contract `plexonhomes.limit.<N>`.
- Restores the intended progression: base 5, Newbie 8, Tinkerer 12, Technician 16, Inventor 20, Skilled 24, and PRO 30 homes.
- The exact grants are `plexonhomes.limit.5`, `.8`, `.12`, `.16`, `.20`, `.24`, and `.30`.
- No bundled progression rank receives `plexonhomes.limit.unlimited`; the authoritative top tier remains 30.
- `plexonhomes.sethome`, `plexonhomes.use`, `plexonhomes.delete`, `plexonhomes.rename`, and `plexonhomes.gui` remain default-true in PlexonHomes and are not redundantly granted by PlexonRanks.

## Migration notes

- Remove obsolete `essentials.sethome.multiple*` home-limit grants from the relevant LuckPerms groups after verifying the new PlexonHomes nodes in production.
- Unrelated Essentials permissions remain intact and must not be bulk-removed.
- Cumulative lower numeric PlexonRanks-owned home limits are safe because PlexonHomes uses the highest effective numeric `plexonhomes.limit.<N>` value.
- PlexonRanks continues to own progression and grants; PlexonHomes continues to own home persistence, GUI, teleportation, and limit interpretation; LuckPerms remains permission authority.

## Verification

The exact merged `main` source is built and tested on Java 25 / Paper 26.2 / PlexonCore 2.0.4. Stable publication includes the JAR, SHA-256 checksum, test summary, and source provenance evidence.
