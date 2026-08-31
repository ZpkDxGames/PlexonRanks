# PlexonRanks — Work Mode Build Specification

> **Repository:** https://github.com/ZpkDxGames/PlexonRanks  
> **Project family:** Plexon plugins  
> **Project owner / author identity:** Tonim / ZpkDxGames  
> **Target:** Paper 1.21.x (PlexonCraft production target: Paper 1.21.10), Java 21  
> **Working name:** PlexonRanks  
> **Suggested first release:** `1.0.0`

---

## 1. Purpose

Build **PlexonRanks** as a new, original rank-progression plugin for PlexonCraft and the wider Plexon plugin family.

The plugin may take **functional inspiration** from the Rankup plugin by comonier and from the behavior previously tested in `Rankup-1.2Fixer`, but PlexonRanks must **not** be implemented as another patch, fork, decompilation, or renamed copy.

PlexonRanks must have:

- Its own codebase.
- Its own package namespace.
- Its own configuration model.
- Its own database schema and migrations.
- Its own GUI/menu implementation.
- Its own command system.
- Its own admin tooling.
- Its own placeholders and API.
- Its own documentation and release pipeline.

The objective is to retain the useful idea of a linear rank-up progression while turning it into a modern, highly configurable Plexon plugin.

---

# 2. Product Goals

PlexonRanks must provide a polished progression system where players:

1. Start at an initial rank.
2. View the full rank ladder using `/ranks`.
3. Inspect their current progress and the next rank.
4. Meet configurable requirements.
5. Execute `/rankup`.
6. Pay/consume only the configured requirements.
7. Receive rank-specific rewards.
8. Permanently retain configured rank permissions/rewards where appropriate.
9. Continue progressing until the final configured rank.

The plugin must be suitable for a long-running survival economy server and must avoid fragile YAML-only player storage, duplicate rewards, inconsistent ranks, or expensive synchronous database work.

---

# 3. Design Principles

## 3.1 Server-first reliability

- Rank progression must never be lost because of a restart.
- Reward execution must not duplicate when a command is retried.
- Player rank state must persist in a database.
- Database operations should not block the server thread where avoidable.
- Configuration reloads must be safe.
- Invalid rank configurations must fail clearly instead of silently corrupting progression.

## 3.2 Highly configurable

Server administrators should be able to change nearly every player-facing element without recompiling:

- Rank names.
- Rank tags.
- Requirements.
- Rewards.
- GUI materials.
- GUI names.
- GUI lore.
- Status appearance.
- Menu layout.
- Sounds.
- Titles.
- Messages.
- Broadcasts.
- Commands.
- Permissions.
- Placeholder display.
- Colors.
- Page controls.
- Rank visibility.

## 3.3 Modern Plexon appearance

Use **Adventure + MiniMessage** for modern formatting.

Where practical, support legacy `&` color codes as a compatibility option, but MiniMessage should be the preferred format.

Examples:

```text
<gradient:#FFE98A:#FFB84D><bold>PRO V</bold></gradient>
<gray>Money:</gray> <gold>$250,000</gold>
```

Do not hardcode Portuguese strings or legacy section-sign formatting into Java classes.

## 3.4 Original implementation

Do not copy source code from Rankup or Rankup-1.2Fixer.

Use them only as behavioral references for concepts such as:

- sequential ranks,
- Vault money requirements,
- XP requirements,
- playtime requirements,
- reward commands,
- LuckPerms permissions,
- `/rank`,
- `/rankup`,
- a rank-list GUI.

---

# 4. Technical Baseline

## Required

- Java 21.
- Paper API 1.21.x.
- Maven project.
- Adventure / MiniMessage.
- SQLite as the default storage engine.

## Integrations

### Required integrations

- **Vault**
  - Economy abstraction.
  - Used for money requirements and optional money rewards.

- **LuckPerms**
  - Persistent permission rewards.
  - Optional rank/group synchronization.

### Optional integrations

- PlaceholderAPI.
- DiscordSRV.
- GriefPrevention.
- PlexonKeys.
- EssentialsX.
- Any other plugin via configurable console commands.

PlexonRanks must not hard-depend on optional integrations unless explicitly enabled.

---

# 5. Suggested Package Layout

Use an original namespace such as:

```text
com.zpkdxgames.plexonranks
```

Suggested modules/packages:

```text
com.zpkdxgames.plexonranks
├── PlexonRanksPlugin
├── api
├── command
│   ├── RankCommand
│   ├── RanksCommand
│   ├── RankupCommand
│   └── admin
├── config
├── database
│   ├── DatabaseManager
│   ├── migration
│   └── repository
├── integration
│   ├── VaultHook
│   ├── LuckPermsHook
│   ├── PlaceholderAPIHook
│   └── DiscordSRVHook
├── menu
│   ├── RankListMenu
│   ├── RankAdminMenu
│   └── editor
├── model
│   ├── Rank
│   ├── RankRequirement
│   ├── RankReward
│   └── PlayerRankData
├── requirement
├── reward
├── service
│   ├── RankService
│   ├── RankupService
│   ├── RewardService
│   └── ProgressService
├── placeholder
├── util
└── listener
```

Keep business logic out of command executors and GUI click handlers.

---

# 6. Rank Model

Ranks are server-configurable.

Do not assume the server will always have exactly 35 ranks.

A rank should support:

```yaml
ranks:
  newbie-1:
    order: 1
    enabled: true

    display:
      name: "<gray>Newbie I</gray>"
      short-name: "Newbie I"
      tag: "<dark_gray>[<gray>Newbie I</gray>]</dark_gray>"
      description:
        - "<gray>Your first step through PlexonCraft.</gray>"

    requirements: {}

    rewards: {}

    menu: {}
```

Use a stable string ID such as `newbie-1`, while `order` determines progression.

Do not use the numeric order as the primary database identity.

This allows ranks to be renamed/reordered without corrupting player records.

---

# 7. Rank Progression

Default behavior should be a **linear progression**.

Example:

```text
Unranked
↓
Newbie I
↓
Newbie II
↓
...
↓
PRO V
```

Support:

- Enabled/disabled ranks.
- Explicit rank order.
- First/default rank.
- Final/max rank.
- Hidden ranks if admins do not want certain entries shown.
- Optional bypass ranks for permission-controlled special cases.

The plugin must validate on startup/reload that:

- Rank IDs are unique.
- Orders are unique.
- At least one rank exists.
- A valid first rank can be determined.
- No malformed requirement/reward type exists.
- Menu materials are valid.
- Required integration-specific configuration is available.

---

# 8. Requirements System

Requirements must be modular.

Do not implement the system as three hardcoded fields inside `Rank`.

Each requirement should use a handler/interface.

Suggested built-in requirement types:

## 8.1 Money

Vault economy.

```yaml
- type: MONEY
  amount: 50000
  consume: true
```

`consume: true` means the money is withdrawn after a successful rank-up transaction.

## 8.2 XP levels

```yaml
- type: XP_LEVELS
  amount: 20
  consume: true
```

Use Minecraft levels rather than raw XP points unless explicitly configured otherwise.

Optionally add:

```yaml
- type: XP_POINTS
```

## 8.3 Playtime

```yaml
- type: PLAYTIME
  amount: 7200
  unit: MINUTES
  consume: false
```

Playtime is never consumed.

Use Paper/Minecraft statistics or a reliable internal tracker.

## 8.4 Permission requirement

```yaml
- type: PERMISSION
  permission: "plexonranks.requirement.special"
```

## 8.5 Placeholder requirement

Optional PlaceholderAPI-based comparison:

```yaml
- type: PLACEHOLDER
  placeholder: "%jobsr_user_jlevel%"
  operator: ">="
  value: "25"
```

Support operators where reasonable:

```text
=
!=
>
>=
<
<=
CONTAINS
```

## 8.6 Item requirement

Optional:

```yaml
- type: ITEM
  material: DIAMOND
  amount: 16
  consume: true
```

Do not use this by default in PlexonCraft unless explicitly configured.

---

# 9. Requirement Progress

The GUI and `/rank` output must be able to show:

- current value,
- required value,
- missing value,
- completion state.

Example placeholders:

```text
%current%
%required%
%missing%
%percent%
%completed%
```

Each requirement handler should expose a human-readable progress representation.

---

# 10. Atomic Rank-Up Flow

`/rankup` must be safe.

Required flow:

1. Load the player's current rank.
2. Determine the next valid rank.
3. Prevent concurrent rank-up attempts for the same player.
4. Evaluate every requirement.
5. If any requirement fails:
   - consume nothing,
   - execute no rewards,
   - keep rank unchanged.
6. Prepare the rank-up transaction.
7. Consume configured consumable requirements.
8. Persist the new rank.
9. Apply persistent permission/group changes.
10. Execute reward handlers.
11. Record reward completion / transaction ID.
12. Send player feedback.
13. Send optional broadcast.
14. Send optional Discord integration.
15. Release the player lock.

If a critical persistence step fails, do not continue with rewards that could be duplicated.

Use idempotency/transaction records where appropriate.

---

# 11. Rewards System

Rewards must also be modular.

A reward must have two separate concepts:

1. **Execution**
2. **Display**

Never force the GUI to expose raw console commands or raw permission nodes.

Example:

```yaml
rewards:
  - type: COMMAND
    commands:
      - "adjustbonusclaimblocks %player% 5000"

    display:
      - "<dark_gray>•</dark_gray> <green>+5,000</green> <gray>Claim Blocks</gray>"
```

---

# 12. Built-in Reward Types

## 12.1 Console commands

```yaml
- type: COMMAND
  commands:
    - "keysadmin give %player% rare 1"
```

Support:

- `%player%`
- `%uuid%`
- `%rank_id%`
- `%rank_name%`

## 12.2 Permissions

Via LuckPerms API.

```yaml
- type: PERMISSION
  permissions:
    - "essentials.hat"
    - "rank.5"
```

Permissions should persist.

Support cumulative permission behavior.

## 12.3 LuckPerms group

Optional:

```yaml
- type: LUCKPERMS_GROUP
  group: "pro"
  mode: ADD
```

Support modes such as:

```text
ADD
SET_PRIMARY
```

Do not remove unrelated server groups.

## 12.4 Money

Optional Vault reward:

```yaml
- type: MONEY
  amount: 1000
```

Do not make cash rewards the default PlexonCraft progression because rank costs are intended as an economy sink.

## 12.5 XP

Optional.

## 12.6 Items

Optional configurable item rewards.

Support:

- material,
- amount,
- custom name,
- lore,
- enchantments,
- custom model data,
- item flags.

Avoid NBT-heavy custom-item support in v1 unless necessary.

## 12.7 PlexonKeys

Prefer a proper optional API hook if PlexonKeys exposes an API in the future.

Until then, command rewards are acceptable.

Example:

```yaml
- type: COMMAND
  commands:
    - "keysadmin give %player% epic 1"
  display:
    - "<dark_gray>•</dark_gray> <light_purple>1× Epic Key</light_purple>"
```

---

# 13. Cumulative Rewards and Permissions

PlexonRanks should distinguish:

- one-time rewards,
- persistent permissions,
- cumulative permissions,
- recurring rewards if such a feature is added later.

A player joining the server must be able to recover any **persistent permission reward** earned from previous ranks if LuckPerms data was temporarily missing.

Do not re-run consumable rewards such as keys/items/commands on join.

Provide a safe admin reconciliation command:

```text
/plexonranks sync <player>
```

This may repair persistent permissions/groups only.

---

# 14. `/ranks` GUI

`/ranks` is one of the main features and must be polished.

Default aliases:

```text
/ranks
/rank
```

The GUI should display the entire rank ladder with pagination.

Suggested inventory size:

```text
54 slots
```

But make it configurable.

---

# 15. GUI Rank States

Every rank entry should have a state:

```text
COMPLETED
CURRENT
NEXT
LOCKED
```

Optionally:

```text
MAX
HIDDEN
```

Each state may override:

- material,
- custom model data,
- name,
- lore,
- glow,
- item flags.

Example:

```yaml
states:
  completed:
    material: LIME_STAINED_GLASS_PANE
    glow: false

  current:
    material: LIGHT_BLUE_STAINED_GLASS_PANE
    glow: true

  next:
    material: YELLOW_STAINED_GLASS_PANE
    glow: true

  locked:
    material: RED_STAINED_GLASS_PANE
    glow: false
```

---

# 16. Per-Rank GUI Customization

Each rank must be able to override its item completely.

Example:

```yaml
menu:
  material: DIAMOND
  amount: 1
  custom-model-data: 0
  glow: true

  name: "<gradient:#FFE98A:#FFB84D><bold>%rank_name%</bold></gradient>"

  lore:
    - "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
    - "<gray>Status:</gray> %status%"
    - ""
    - "<white><bold>Requirements</bold></white>"
    - "%requirements%"
    - ""
    - "<white><bold>Rewards</bold></white>"
    - "%rewards%"
    - ""
    - "<dark_gray>Rank #%rank_order%</dark_gray>"
    - "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
```

If a rank does not define custom menu lore, use the global template.

---

# 17. GUI Lore Placeholders

At minimum support:

```text
%rank_id%
%rank_order%
%rank_name%
%rank_short_name%
%rank_tag%
%status%
%requirements%
%rewards%
%player_rank%
%player_rank_order%
%next_rank%
```

`%requirements%` and `%rewards%` are **list-expansion placeholders**.

If the line is exactly:

```text
"%requirements%"
```

replace it with all requirement display lines.

Same behavior for:

```text
"%rewards%"
```

Do not concatenate an entire list onto one lore line.

---

# 18. Requirement Display Configuration

Requirements should expose configurable display templates.

Example:

```yaml
requirement-display:
  MONEY:
    completed: "<dark_gray>•</dark_gray> <green>✔</green> <gray>Money:</gray> <gold>$%current%</gold><dark_gray>/</dark_gray><gold>$%required%</gold>"
    incomplete: "<dark_gray>•</dark_gray> <red>✘</red> <gray>Money:</gray> <gold>$%current%</gold><dark_gray>/</dark_gray><gold>$%required%</gold>"

  XP_LEVELS:
    completed: "<dark_gray>•</dark_gray> <green>✔</green> <gray>XP:</gray> <yellow>%current%</yellow><dark_gray>/</dark_gray><yellow>%required%</yellow>"

  PLAYTIME:
    completed: "<dark_gray>•</dark_gray> <green>✔</green> <gray>Playtime:</gray> <aqua>%current_formatted%</aqua><dark_gray>/</dark_gray><aqua>%required_formatted%</aqua>"
```

---

# 19. GUI Navigation

Global menu configuration should allow:

- previous page item,
- next page item,
- close button,
- information button,
- current progression button,
- filler items,
- page indicator.

Example:

```yaml
navigation:
  previous:
    slot: 45
    material: ARROW
    name: "<yellow>Previous Page</yellow>"

  info:
    slot: 49
    material: NETHER_STAR

  next:
    slot: 53
    material: ARROW
    name: "<yellow>Next Page</yellow>"
```

Clicking locked/completed ranks should do nothing by default.

Clicking the **NEXT** rank may:

```text
RIGHT_CLICK -> attempt rankup
LEFT_CLICK -> show details
```

Make click behavior configurable.

Ensure Bedrock/Geyser users are not excluded by overly strict click checks.

---

# 20. In-Game Rank Editor

PlexonRanks should have an admin GUI instead of requiring all changes to be done manually in YAML.

Command:

```text
/plexonranks admin
```

Suggested editor pages:

## Rank list editor

- Create rank.
- Delete rank.
- Duplicate rank.
- Enable/disable rank.
- Reorder rank.
- Open rank editor.

## Rank editor

Editable properties:

- ID where safe.
- Order.
- Display name.
- Short name.
- Tag.
- Description.
- Requirements.
- Rewards.
- Menu material.
- Menu name.
- Menu lore.
- Glow.
- Custom model data.
- Visibility.
- Broadcast behavior.
- Sounds/title overrides.

## Lore editor

Allow admins to:

- view every line,
- add a line,
- edit a line,
- remove a line,
- move line up,
- move line down,
- insert blank line,
- reset to global template,
- preview rendered lore.

Input may use chat or an anvil-style text interface.

Use chat capture safely with cancellation/timeouts.

Do not keep permanent player state in static maps without cleanup.

---

# 21. Live Preview

Admin editor should support a preview item showing:

- the rank as COMPLETED,
- CURRENT,
- NEXT,
- LOCKED.

This prevents admins from repeatedly saving/reloading just to inspect formatting.

---

# 22. Config Files

Suggested files:

```text
plugins/PlexonRanks/
├── config.yml
├── ranks.yml
├── menus.yml
├── messages.yml
├── database.db
├── backups/
└── logs/               # optional diagnostic logs only
```

Avoid unnecessary fragmentation.

---

# 23. `config.yml`

Suggested responsibilities:

```yaml
plugin:
  language: en
  debug: false

formatting:
  minimessage: true
  legacy-ampersand-support: true

rankup:
  cooldown-ms: 750
  prevent-concurrent: true
  right-click-next-rank: true

permissions:
  cumulative: true
  reconcile-on-join: true

storage:
  type: SQLITE

  sqlite:
    file: database.db

  mysql:
    enabled: false
    host: localhost
    port: 3306
    database: plexonranks
    username: root
    password: ""
    pool-size: 5

integrations:
  vault: true
  luckperms: true
  placeholderapi: true
  discordsrv: false

broadcast:
  enabled: true

backup:
  enabled: true
  keep: 10
```

Never log database passwords.

---

# 24. `ranks.yml`

This file contains rank definitions only.

Example:

```yaml
schema-version: 1

ranks:
  newbie-1:
    order: 1
    enabled: true

    display:
      name: "<gradient:#D5D5D5:#FFFFFF>Newbie I</gradient>"
      short-name: "Newbie I"
      tag: "<dark_gray>[</dark_gray><white>Newbie I</white><dark_gray>]</dark_gray>"

    requirements:
      - type: MONEY
        amount: 5000
        consume: true

      - type: XP_LEVELS
        amount: 5
        consume: true

      - type: PLAYTIME
        amount: 60
        unit: MINUTES

    rewards:
      - type: COMMAND
        commands:
          - "adjustbonusclaimblocks %player% 1000"
        display:
          - "<dark_gray>•</dark_gray> <green>+1,000</green> <gray>Claim Blocks</gray>"

      - type: COMMAND
        commands:
          - "keysadmin give %player% basic 1"
        display:
          - "<dark_gray>•</dark_gray> <white>1× Basic Key</white>"

      - type: PERMISSION
        permissions:
          - "rank.1"
        display: []

    menu:
      use-global-template: true
```

---

# 25. `menus.yml`

All general GUI structure goes here.

Include:

- title,
- size,
- rank slots,
- fillers,
- navigation,
- global rank template,
- state styles,
- admin menu styles.

Allow explicit rank slots or automatic slot generation.

Example:

```yaml
rank-list:
  title: "<gradient:#FFE98A:#FFB84D><bold>Plexon Ranks</bold></gradient> <dark_gray>•</dark_gray> <gray>Page %page%</gray>"
  size: 54

  rank-slots:
    - 10
    - 11
    - 12
    - 13
    - 14
    - 15
    - 16
    - 19
    - 20
    - 21
    - 22
    - 23
    - 24
    - 25
    - 28
    - 29
    - 30
    - 31
    - 32
    - 33
    - 34
```

Use proper spacing instead of filling all 45 top slots automatically.

---

# 26. `messages.yml`

All text must be configurable.

Include sections for:

- prefix,
- generic errors,
- no permission,
- reload,
- database errors,
- max rank,
- requirements not met,
- rankup success,
- rankup broadcast,
- progress output,
- admin editor,
- confirmation prompts,
- Discord messages,
- menu state text.

Example:

```yaml
prefix: "<dark_gray>[</dark_gray><gradient:#FFE98A:#FFB84D><bold>PlexonRanks</bold></gradient><dark_gray>]</dark_gray> "

rankup:
  success: "<green>Rank upgraded to</green> %rank_name%<green>!</green>"
  max-rank: "<yellow>You have reached the maximum rank.</yellow>"
  requirements-not-met: "<red>You do not meet every requirement yet.</red>"
```

---

# 27. Player Commands

## `/rank`

Purpose:

- Display current rank.
- Display next rank.
- Display next-rank requirement progress.
- Display next-rank rewards.
- Optionally open the GUI.

Suggested behavior:

```text
/rank
```

shows compact status and opens `/ranks` if configured.

Subcommands:

```text
/rank info
/rank menu
```

## `/ranks`

Open the rank ladder.

Optional:

```text
/ranks <page>
```

## `/rankup`

Attempt exactly one rank-up.

Optional future feature:

```text
/rankup max
```

Do not implement multi-rankup in v1 unless transaction handling is robust.

---

# 28. Admin Commands

Root:

```text
/plexonranks
```

Aliases:

```text
/pranks
```

Suggested commands:

```text
/plexonranks help
/plexonranks reload
/plexonranks admin
/plexonranks info <player>
/plexonranks setrank <player> <rank>
/plexonranks resetrank <player>
/plexonranks promote <player> [amount]
/plexonranks demote <player> [amount]
/plexonranks sync <player>
/plexonranks validate
/plexonranks backup
```

`setrank`, `promote`, and `demote` must not automatically re-run one-time rewards unless an explicit flag is used.

Example:

```text
/plexonranks setrank Tonim pro-5 --grant-persistent
```

Avoid an easy `--rewards` flag that could accidentally duplicate keys/items.

---

# 29. Permissions

Suggested nodes:

```text
plexonranks.use
plexonranks.rank
plexonranks.ranks
plexonranks.rankup

plexonranks.admin
plexonranks.admin.reload
plexonranks.admin.editor
plexonranks.admin.info
plexonranks.admin.setrank
plexonranks.admin.reset
plexonranks.admin.promote
plexonranks.admin.demote
plexonranks.admin.sync
plexonranks.admin.validate
plexonranks.admin.backup
```

Do not use OP checks as the only access control.

---

# 30. PlaceholderAPI

Register a PlexonRanks expansion.

Suggested placeholders:

```text
%plexonranks_rank_id%
%plexonranks_rank_order%
%plexonranks_rank_name%
%plexonranks_rank_tag%

%plexonranks_next_id%
%plexonranks_next_name%

%plexonranks_progress_percent%
%plexonranks_is_max_rank%
```

Optional requirement-specific placeholders:

```text
%plexonranks_requirement_money_current%
%plexonranks_requirement_money_required%
%plexonranks_requirement_money_missing%

%plexonranks_requirement_xp_current%
%plexonranks_requirement_xp_required%

%plexonranks_requirement_playtime_current%
%plexonranks_requirement_playtime_required%
```

Return sane empty/fallback values when no next rank exists.

---

# 31. Public API

Expose a small API for other Plexon plugins.

Example capabilities:

```java
PlexonRanksAPI#getRank(UUID)
PlexonRanksAPI#getNextRank(UUID)
PlexonRanksAPI#getRankById(String)
PlexonRanksAPI#getRanks()
PlexonRanksAPI#canRankup(UUID)
```

Events:

```text
PlexonRankPreRankupEvent
PlexonRankupEvent
PlexonRankChangeEvent
```

`PreRankupEvent` should be cancellable.

Do not expose internal mutable collections directly.

---

# 32. Database

Use SQLite by default.

Suggested tables:

```text
pr_players
pr_rank_transactions
pr_schema
```

Example:

```sql
pr_players
- uuid TEXT PRIMARY KEY
- rank_id TEXT NOT NULL
- updated_at INTEGER NOT NULL
- first_joined_at INTEGER
```

Transaction table:

```sql
pr_rank_transactions
- id TEXT PRIMARY KEY
- player_uuid TEXT NOT NULL
- from_rank TEXT
- to_rank TEXT NOT NULL
- status TEXT NOT NULL
- created_at INTEGER NOT NULL
- completed_at INTEGER
```

This helps prevent duplicate reward execution.

Use schema migrations from the first release.

Never destructively modify the DB without a migration and backup strategy.

---

# 33. MySQL / MariaDB

Optional remote storage.

Use a connection pool such as HikariCP.

Support:

```text
SQLite
MySQL
MariaDB
```

Do not issue a new database connection for every command.

Do not run slow SQL queries on the primary server thread.

---

# 34. Backups

Before destructive migration:

- backup SQLite database,
- optionally backup YAML configs.

Configurable retention.

Example:

```text
backups/database-2026-08-31_1824.db
```

Do not create a backup on every normal player join.

---

# 35. Cache

Cache the active player's rank in memory after load.

Requirements:

- update cache immediately after rank change,
- invalidate safely,
- save authoritative data in DB,
- clear cache on quit after any pending writes,
- do not let cache become the only source of truth.

---

# 36. Join Reconciliation

On join:

- load player rank,
- verify the configured rank still exists,
- repair persistent rank permissions if enabled,
- do not replay one-time rewards,
- do not spam the player with repair messages.

If a rank was deleted:

- use a configurable migration/fallback behavior,
- log the problem,
- never silently reset every affected player to rank 0 unless configured.

---

# 37. Rank Configuration Migration

Because servers evolve, add a configuration `schema-version`.

Future releases should migrate configs deliberately.

Do not overwrite administrator changes.

When new defaults are introduced:

- merge missing keys where safe,
- preserve existing values,
- document migrations.

---

# 38. Formatting Engine

Use Adventure components internally.

Preferred:

```text
MiniMessage
```

Support:

- gradients,
- hex colors,
- hover/click where suitable,
- decorations,
- reset behavior.

For GUI lore, deserialize each line separately.

Optional compatibility:

```yaml
formatting:
  legacy-ampersand-support: true
```

If enabled, legacy strings can be converted before rendering.

---

# 39. Rank-Up Feedback

All channels configurable independently:

```yaml
feedback:
  chat: true
  title: true
  subtitle: true
  sound: true
  broadcast: true
```

Example title:

```text
RANK UP!
PRO III
```

Support per-rank overrides.

Do not use actionbar as the only important feedback mechanism.

---

# 40. Sounds

Configurable with safe fallback.

Example:

```yaml
sounds:
  rankup:
    sound: UI_TOAST_CHALLENGE_COMPLETE
    volume: 1.0
    pitch: 1.0

  denied:
    sound: BLOCK_NOTE_BLOCK_BASS
    volume: 0.8
    pitch: 0.8
```

Invalid sound names should produce a clear validation warning.

---

# 41. Discord

Prefer DiscordSRV integration when available.

Optional broadcast:

```text
Tonim ranked up to PRO V!
```

Allow:

- channel ID/name configuration,
- embed title,
- embed description,
- embed color,
- footer,
- toggle.

Do not block rank-up while waiting for Discord.

Discord delivery must be asynchronous and failure-tolerant.

A Discord outage must not revert a successful Minecraft rank-up.

---

# 42. PlexonCraft Progression Defaults

The current PlexonCraft concept uses approximately 35 progression ranks grouped into themed tiers.

The existing progression concept should be used as an initial default/example configuration, not hardcoded into Java.

Current conceptual tiers include:

```text
1–10   Newbie
11–15  Tinkerer
16–20  Technician
21–25  Inventor
26–30  Skilled
31–35  PRO
```

The exact names/colors/requirements may be imported from the current server config during implementation.

The plugin itself must work with any number of ranks.

---

# 43. Economy Philosophy for PlexonCraft

Rank progression should be primarily an **economy and time sink**, not an XP grind.

Default PlexonCraft balancing philosophy:

- Money requirement increases meaningfully with progression.
- Playtime increases steadily.
- XP remains relevant but lighter than money.
- Rewards become more valuable at milestones.
- Avoid directly refunding most of the money cost as cash rewards.
- Keys, permissions, QoL commands, cosmetics/disguises, claim blocks, and progression unlocks are preferred rewards.

This philosophy belongs in the sample/default rank configuration, not in hardcoded logic.

---

# 44. Reward Milestones

The existing PlexonCraft rank concept uses rewards such as:

- GriefPrevention claim blocks.
- Basic keys.
- Rare keys.
- Epic keys.
- Legendary keys.
- EssentialsX QoL commands.
- Disguise permissions.
- Chat/color permissions.
- Keep-XP style progression unlocks.

PlexonRanks must support these through generic reward handlers, permissions, or commands.

Do not hardcode PlexonKeys, EssentialsX, iDisguise, or GriefPrevention-specific rank numbers into Java.

---

# 45. Example Reward Presentation

The GUI should present rewards clearly:

```text
Rewards
• +10,000 Claim Blocks
• 2× Basic Keys
• 1× Rare Key
• Salmon Disguise
• /enderchest command
```

Not:

```text
adjustbonusclaimblocks %player% 10000
keysadmin give %player% basic 2
essentials.enderchest
idisguise.disguise.salmon
```

Execution details and display text are separate.

---

# 46. `/rank` Status Output

Example:

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
           PLEXON RANK
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Player: Tonim
Current: Technician IV
Next: Technician V

Requirements
✔ Money      $210,000 / $210,000
✔ XP         20 / 20
✘ Playtime   1,170 / 1,200 min

Next Rank Rewards
• +20,000 Claim Blocks
• 1× Epic Key
• /back after death

Use /rankup once all requirements are complete.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Every line must be configurable in `messages.yml`.

---

# 47. Progress Percent

Provide an overall progress percentage for display.

Do not simply average raw values of unlike units.

Suggested method:

1. Each requirement returns normalized completion from 0.0–1.0.
2. Average requirement completion.
3. Cap at 100%.

Optionally allow weights later.

---

# 48. Configuration Validation

Command:

```text
/plexonranks validate
```

Check:

- duplicate order values,
- missing rank IDs,
- invalid materials,
- malformed MiniMessage,
- invalid requirement types,
- invalid reward types,
- missing integration dependencies,
- unknown sound names,
- bad menu slot numbers,
- duplicate menu slots,
- unreachable ranks,
- invalid numeric values,
- negative requirement amounts.

Print a concise report.

Do not disable the entire plugin for non-critical cosmetic mistakes if a safe fallback exists.

---

# 49. Reload Behavior

`/plexonranks reload` should reload:

- config,
- ranks,
- menus,
- messages,
- integrations where safe.

Do not reconnect/reinitialize the whole database unless storage settings changed.

If database settings changed, warn that a restart is required unless hot migration is explicitly implemented.

Reload must be atomic:

- parse into a new configuration snapshot,
- validate,
- swap only if valid.

Do not partially replace the live config.

---

# 50. Admin Safety

Destructive commands require confirmation when reasonable.

Examples:

```text
/plexonranks resetrank Tonim
/plexonranks delete-rank pro-5
```

Admin GUI deletion should use a confirmation menu.

Log admin rank changes:

```text
[ADMIN] ZpkDxGames set Tonim from technician-2 to technician-5.
```

Avoid logging sensitive DB credentials.

---

# 51. Security / Exploit Prevention

Must protect against:

- duplicate `/rankup` execution from rapid commands,
- double clicks in GUI,
- reward duplication,
- inventory item extraction from GUIs,
- invalid navigation clicks,
- stale rank state,
- race conditions between command and GUI rankup,
- repeated transaction execution after reconnect,
- negative economy withdrawal,
- malformed commands from placeholders.

Use a per-player rankup lock/cooldown.

GUI inventories must cancel item movement/drag actions.

---

# 52. Performance

PlexonRanks should have negligible TPS impact.

Requirements:

- no per-tick rank checks,
- no scanning every online player every tick,
- no synchronous full database scans,
- cache parsed rank definitions,
- cache menu templates,
- only evaluate requirements when needed:
  - `/rank`,
  - `/ranks`,
  - `/rankup`,
  - relevant GUI refresh,
  - explicit API request.

Playtime can use Minecraft statistics instead of a custom every-second timer.

---

# 53. GUI Refresh

Do not rebuild the GUI every tick.

If dynamic progress refresh is desired:

```yaml
rank-list:
  refresh:
    enabled: true
    interval-ticks: 40
```

Only refresh for players who currently have the rank menu open.

Cancel refresh tasks when the menu closes.

---

# 54. Localization

Architecture should support:

```text
messages_en.yml
messages_pt_BR.yml
messages_es.yml
```

For v1, one primary `messages.yml` is acceptable if the localization layer is designed cleanly.

Do not embed language-specific text into Java.

---

# 55. Logging

Normal production logs should be quiet.

Log:

- startup summary,
- storage connection,
- loaded rank count,
- integration status,
- migration result,
- serious configuration warnings,
- admin actions,
- transaction failures.

Debug mode may log more.

Do not log each normal `/rank` use.

---

# 56. Startup Summary

Example:

```text
PlexonRanks 1.0.0
 • Ranks: 35
 • Storage: SQLite
 • Vault: CONNECTED
 • LuckPerms: CONNECTED
 • PlaceholderAPI: CONNECTED
 • DiscordSRV: DISABLED
 • MiniMessage: ENABLED
```

---

# 57. API Compatibility Strategy

Avoid directly coupling every feature to implementation classes.

Use interfaces for:

```text
RequirementHandler
RewardHandler
StorageProvider
RankRepository
IntegrationHook
```

This will make later Plexon-family integrations easier.

---

# 58. Testing

Include automated tests where practical.

At minimum test:

- rank ordering,
- next-rank resolution,
- requirement completion math,
- progress percentages,
- YAML parsing,
- invalid config rejection,
- reward display expansion,
- lore placeholder expansion,
- transaction/idempotency behavior,
- migration logic.

Use MockBukkit if appropriate for Paper-facing tests.

---

# 59. Manual Test Checklist

Before release, verify on a test Paper server:

1. Plugin enables.
2. SQLite database is generated.
3. First join creates player data.
4. `/rank` renders correctly.
5. `/ranks` opens.
6. Pagination works.
7. Inventory items cannot be stolen.
8. Completed/current/next/locked states render correctly.
9. Custom rank lore works.
10. `%requirements%` expands.
11. `%rewards%` expands.
12. Money requirement is detected.
13. XP requirement is detected.
14. Playtime requirement is detected.
15. Failed rank-up consumes nothing.
16. Successful rank-up deducts only configured consumables.
17. Rank persists after restart.
18. LuckPerms rewards persist.
19. Console rewards execute exactly once.
20. Spam `/rankup` does not duplicate rewards.
21. Admin editor saves correctly.
22. `/plexonranks reload` keeps valid state.
23. PlaceholderAPI placeholders work.
24. Max-rank behavior is clean.
25. Geyser/Bedrock GUI interactions remain usable.

---

# 60. Build / CI

Repository should include:

```text
.github/workflows/build.yml
```

Run on push and pull request.

Build with:

```text
Java 21
mvn clean package
```

Upload the compiled JAR as a workflow artifact.

Suggested artifact:

```text
PlexonRanks-1.0.0.jar
```

---

# 61. Releases

For tagged releases such as:

```text
v1.0.0
```

GitHub Actions should:

1. Build with Java 21.
2. Run tests.
3. Produce the plugin JAR.
4. Create/update a GitHub Release.
5. Upload the JAR as a release asset.

Do not commit compiled JARs into normal source directories.

---

# 62. `plugin.yml`

Suggested metadata:

```yaml
name: PlexonRanks
version: '${project.version}'
main: com.zpkdxgames.plexonranks.PlexonRanksPlugin
api-version: '1.21'
author: ZpkDxGames
description: Flexible rank progression for PlexonCraft and the Plexon plugin family.

depend:
  - Vault
  - LuckPerms

softdepend:
  - PlaceholderAPI
  - DiscordSRV
```

Register commands and permissions properly.

---

# 63. README

The repository README should contain:

- plugin overview,
- features,
- requirements,
- installation,
- commands,
- permissions,
- rank configuration example,
- menu customization example,
- placeholders,
- storage,
- integrations,
- build instructions,
- release downloads,
- migration/import notes,
- credits/inspiration.

Credit functional inspiration appropriately without presenting PlexonRanks as a fork.

Suggested wording:

> PlexonRanks is an original Plexon plugin inspired by conventional Minecraft rank-up systems, including the workflow explored with Rankup by comonier. PlexonRanks uses an independent implementation and configuration architecture.

---

# 64. Migration From Rankup / Rankup-1.2Fixer

Add an optional migration/import command or utility.

Target data:

- current player rank,
- rank ordering,
- requirement values,
- reward commands,
- permission rewards,
- reward display lore.

Suggested command:

```text
/plexonranks migrate rankup
```

The migration should:

1. Detect the old plugin folder.
2. Back up PlexonRanks data.
3. Parse old `ranks.yml`.
4. Map old numeric rank IDs to new stable string IDs.
5. Import player rank data if feasible.
6. Convert known requirement fields.
7. Convert command/permission rewards.
8. Preserve human-readable display lore where possible.
9. Produce a migration report.
10. Never delete the old plugin data.

Migration is desirable but does not need to block the v1.0 release if it significantly delays core stability.

---

# 65. Plexon Family Consistency

PlexonRanks should feel like the other Plexon plugins.

Use:

- consistent `Plexon...` naming,
- clean admin GUIs,
- MiniMessage formatting,
- strong configuration depth,
- useful console startup summaries,
- stable persistence,
- sensible defaults,
- no unnecessary spam,
- performance-conscious implementations,
- GitHub release automation.

The plugin should be usable outside PlexonCraft, but its default configuration can showcase PlexonCraft's progression style.

---

# 66. Features to Avoid in v1

Do not bloat the first release with:

- web dashboards,
- cross-server synchronization,
- Redis,
- rank prestige/reset systems,
- seasons,
- battle-pass mechanics,
- complex quest engines,
- per-world ranks,
- hundreds of hardcoded integration classes.

Build the core rank system correctly first.

---

# 67. Possible v1.1+ Roadmap

Potential future additions:

- prestige system,
- branchable rank trees,
- seasonal progression,
- cross-server MySQL synchronization,
- Redis cache invalidation,
- web/API integration,
- richer in-game GUI rank creation,
- command auto-completion for rank IDs,
- PlaceholderAPI requirement builder,
- custom requirement/reward registration API,
- rank animations,
- per-world display rules,
- importers for other rankup plugins.

---

# 68. Definition of Done for PlexonRanks 1.0

The initial release is complete when:

- The plugin is an original independent implementation.
- It builds cleanly under Java 21.
- It runs on the target Paper 1.21.x server.
- Vault and LuckPerms integrations are stable.
- SQLite persistence works.
- Ranks are fully configurable.
- Requirements are modular.
- Rewards are modular.
- `/rank`, `/ranks`, and `/rankup` work.
- The `/ranks` GUI supports configurable per-rank name/lore/material.
- `%requirements%` and `%rewards%` list expansion works.
- Admin rank editing is functional.
- Rank-up transactions cannot easily duplicate rewards.
- Player ranks survive restart.
- Permanent permissions reconcile safely.
- PlaceholderAPI support works.
- Config reload is validated and safe.
- Documentation is complete.
- GitHub CI builds the project.
- A downloadable release JAR is published.

---

# 69. Work Mode Implementation Instruction

When implementing this project in Work mode:

1. Work directly in:
   `https://github.com/ZpkDxGames/PlexonRanks`

2. Treat this document as the functional specification.

3. Inspect existing Plexon repositories only when useful for style/architecture consistency.

4. Do not copy Rankup/Rankup-1.2Fixer source into PlexonRanks.

5. Implement the new plugin incrementally:
   - project scaffold,
   - config models,
   - storage,
   - rank service,
   - requirements,
   - rewards,
   - commands,
   - GUI,
   - admin editor,
   - integrations,
   - tests,
   - CI,
   - documentation,
   - release.

6. Keep all code production-oriented.

7. Avoid placeholders/stubs in the final release unless clearly documented as intentionally future-facing.

8. Run build/tests before considering a phase complete.

9. Keep commits understandable and grouped by feature.

10. Preserve the author/project identity as **ZpkDxGames / PlexonRanks**.

---

# 70. First Suggested Implementation Milestones

## Milestone 1 — Core

- Maven Java 21 project.
- Plugin bootstrap.
- Config loader.
- Rank model.
- SQLite.
- Vault.
- LuckPerms.
- `/rank`.
- `/rankup`.

## Milestone 2 — GUI

- `/ranks`.
- Pagination.
- state styling.
- per-rank custom material/name/lore.
- `%requirements%`.
- `%rewards%`.
- safe clicks.

## Milestone 3 — Rewards & Integrations

- command rewards.
- permission rewards.
- optional Vault money reward.
- PlaceholderAPI.
- DiscordSRV.
- persistent permission reconciliation.

## Milestone 4 — Administration

- `/plexonranks`.
- reload.
- validation.
- setrank/promote/demote.
- rank editor GUI.
- lore editor.
- preview.

## Milestone 5 — Production Release

- tests.
- migration safeguards.
- README.
- GitHub Actions.
- release workflow.
- `v1.0.0` JAR.

---

## Final Product Direction

PlexonRanks should become the definitive progression plugin for PlexonCraft: visually polished, economy-aware, reliable, highly configurable, and extensible enough to replace the current Rankup installation without inheriting its hardcoded GUI, limited lore control, or storage/architecture constraints.

The most important feature is not simply `/rankup`; it is giving administrators full control over how ranks **behave, look, cost, reward, persist, and integrate** while keeping the player experience clear and the server implementation safe.
