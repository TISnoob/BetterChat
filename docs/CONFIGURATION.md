# Configuration reference

The Paper configuration lives at `plugins/BetterChat/config.yml`. The BungeeCord and Velocity configs contain proxy mode, MariaDB, and bStats settings. YAML strings containing passwords, URLs, MiniMessage, or provider keys should be quoted when they contain `:` or `#`.

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

## Chat

| Setting | Default | Meaning |
| --- | --- | --- |
| `chat.enabled` | `true` | Whether BetterChat's chat listener is active. |
| `chat.translation-enabled` | `false` | Enable recipient-specific translation. |
| `chat.built-in-format` | `true` | Let BetterChat render local chat when a known formatter is not active. |
| `chat.auto-disable-format-with-known-plugin` | `true` | Turn off BetterChat's formatter when a listed plugin is enabled. |
| `chat.detected-formatters` | VentureChat, EssentialsChat, ChatControl, LPC | Plugin names to detect. Add formatter names as needed. |
| `chat.format` | empty | MiniMessage template. Empty selects the built-in format. |
| `chat.flag-position` | `before-name` | `before-name` or `after-name` for the built-in format. |
| `chat.show-original-on-translation-failure` | `true` | Send the original text if translation is unavailable. |
| `chat.translate-console` | `true` | Include console's selected locale in translation batches. |
| `chat.console-format` | `[<language>] <player>: <message>` | Prefix used for original and translated console lines. |

## Flags and names

| Setting | Default | Meaning |
| --- | --- | --- |
| `flags.enabled` | `true` | Show country flags in BetterChat's own format and optional decorations. |
| `flags.blacklist` | `[]` | Country codes hidden from player selection and command validation. Add `EARTH` to hide the global Earth flag. |
| `flags.auto-detect-ip` | `false` | Resolve a new player's country using the configured external lookup endpoint. |
| `flags.auto-detect-endpoint` | `https://ipapi.co/{ip}/json/` | Endpoint template; `{ip}` is URL-encoded. |
| `flags.resource-pack.enabled` | `false` | Send the configured pack on join. |
| `flags.resource-pack.url` | empty | Public pack ZIP URL. |
| `flags.resource-pack.sha1` | empty | Optional 40-character SHA-1 hash. Set it after uploading the pack. |
| `flags.resource-pack.prompt` | install prompt | Text displayed in the client request. |
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

## Language and queue controls

| Setting | Default | Meaning |
| --- | --- | --- |
| `language.blacklist` | `[]` | Language codes or names hidden from selection and command validation. |
| `translation.batch-window-ms` | `45` | Time to collect nearby chat requests before sending a provider batch. |
| `translation.batch-size` | `32` | Maximum messages per AI batch. |
| `translation.queue-capacity` | `2048` | Maximum queued messages before BetterChat falls back to original text. |
| `translation.timeout-seconds` | `35` | Per-request timeout for translation HTTP adapters. |
| `translation.temperature` | `0.25` | AI response temperature; Google Cloud's translation API does not use a temperature. |
| `translation.provider-order` | provider list | Provider/key fallback order. |
| `translation.providers.<id>` | disabled providers | Enable providers, configure endpoint/model, and add multiple `api-keys`. See [Translation setup](TRANSLATION.md). |

| `bstats.enabled` | `true` | Enable anonymous bStats metrics for this platform. |
