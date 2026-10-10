# Configuration reference

Paper's general settings live at `plugins/BetterChat/config.yml`; chat behavior, translation providers, and AI prompts live at `plugins/BetterChat/ai.yml`. On first startup after upgrading, BetterChat migrates the old `chat` and `translation` sections into `ai.yml`. The BungeeCord and Velocity configs contain proxy mode, MariaDB, and bStats settings. YAML strings containing passwords, URLs, MiniMessage, or provider keys should be quoted when they contain `:` or `#`.

## Storage and proxy

| Setting | Default | Meaning |
| --- | --- | --- |
| `storage.type` | `sqlite` | `sqlite` for standalone Paper, `mariadb` for standalone or proxy deployments. |
| `storage.sqlite-file` | `betterchat.db` | File in the BetterChat data folder. |
| `storage.mariadb.*` | local sample values | Host, port, database, username, password, pool size. Use one database across a proxy network. |
| `proxy-mode.enabled` | `false` | Enable on every Paper backend and on the matching proxy plugin. Requires MariaDB. |
| `proxy-mode.server-id` | `paper-1` | Unique name for this backend, included in relayed message metadata. |
| `proxy-mode.secret` | empty | Required in proxy mode. Copy the same random secret of at least 32 characters to every Paper backend and the proxy config. |

## Default preferences

| Setting | Default | Meaning |
| --- | --- | --- |
| `defaults.language` | `en_us` | Language assigned to players without a stored choice. |
| `defaults.console-language` | `en_us` | Console's target language. |
| `defaults.country` | `EARTH` | Global Earth flag assigned to players without a stored choice. |

Changing a default affects players who do not already have a saved preference. Players can select any listed language from the menu, or set a custom language name or code with `/bc language <name-or-code>`. Existing MariaDB schemas are widened at startup to store Earth and custom language values.

## Chat and AI

All `chat.*` options and `translation.*` options are in `plugins/BetterChat/ai.yml`. Chat formatter details are in [Formatting and integrations](INTEGRATIONS.md); provider setup, model lists, and prompt tokens are in [Translation setup](TRANSLATION.md).

## Flags and names

| Setting | Default | Meaning |
| --- | --- | --- |
| `flags.enabled` | `true` | Show country flags in BetterChat's own format and optional decorations. |
| `flags.blacklist` | `[]` | Country codes hidden from player selection and command validation. Add `EARTH` to hide the global Earth flag. |
| `flags.auto-detect-ip` | `false` | Resolve a new player's country using the configured external lookup endpoint. |
| `flags.auto-detect-endpoint` | `https://ipapi.co/{ip}/json/` | Endpoint template; `{ip}` is URL-encoded. |
| `flags.resource-pack.enabled` | `false` | Send the configured pack on join. Paper's `server.properties` can send the pack instead. |
| `flags.resource-pack.url` | empty | Public pack ZIP URL. |
| `flags.resource-pack.sha1` | empty | Optional 40-character SHA-1 hash. Set it after uploading the pack. |
| `flags.resource-pack.prompt` | install prompt | Text displayed in BetterChat's client request. Paper's `resource-pack-prompt` applies when the pack is configured in `server.properties`. |
| `flags.resource-pack.required` | `false` | Whether the client must accept the pack. |
| `flags.resource-pack.use-glyphs` | `false` | Use the generated custom font for BetterChat-rendered components. |
| `flags.tab-list.enabled` | `false` | Add the selected flag to the tab-list name. |
| `flags.tab-list.position` | `before-name` | Place it before or after the player-list name. |
| `flags.nametag.enabled` | `false` | Add a scoreboard-team prefix to overhead names. Existing teams are respected. |
| `flags.nametag.worlds` | `[]` | Optional world allowlist; empty means all worlds. |
| `flags.nametag.blacklist-worlds` | `[]` | Worlds excluded even when allowlisted. |

## Group assignment

| Setting | Default | Meaning |
| --- | --- | --- |
| `groups.enabled` | `false` | Enable group assignment. |
| `groups.provider` | `luckperms` | `luckperms` or `vault`. Vault needs an active permission provider with group support. |
| `groups.language-groups` | `false` | Assign `betterchat_language_<locale>`. |
| `groups.nationality-groups` | `false` | Assign `betterchat_country_<code>`. Earth uses `betterchat_country_earth`. |
| `groups.group-prefix` | `betterchat_` | Prefix BetterChat owns and can remove during reassignment. |

## Language selection

| Setting | Default | Meaning |
| --- | --- | --- |
| `language.blacklist` | `[]` | Language codes or names hidden from selection and command validation. |

## Metrics

| `bstats.enabled` | `true` | Enable anonymous bStats metrics for this platform. |
