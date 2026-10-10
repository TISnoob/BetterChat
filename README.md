# BetterChat

<p align="center">
  <img src="assets/BetterChat-banner.svg" alt="BetterChat" width="760">
</p>

<h3 align="center">One chat. Every language. A little more of home.</h3>

<p align="center">
  <img src="https://img.shields.io/badge/Paper-26.2%20%7C%2026.3-fff?logo=papermc&logoColor=black" alt="Paper 26.2 and 26.3">
  <img src="https://img.shields.io/badge/Folia-supported-fff?logo=papermc&logoColor=black" alt="Folia supported">
  <img src="https://img.shields.io/badge/Velocity-4.2-fff?logo=velocity&logoColor=black" alt="Velocity">
  <img src="https://img.shields.io/badge/BungeeCord-26.1-fff?logo=minecraft&logoColor=black" alt="BungeeCord">
  <img src="https://img.shields.io/badge/Java-25-fff?logo=openjdk&logoColor=black" alt="Java 25">
  <img src="https://img.shields.io/badge/Adventure-components-fff?logo=papermc&logoColor=black" alt="Adventure">
</p>

**BetterChat** translates player chat into each listener's chosen language and displays a country flag beside player names. It runs its own chat format when no formatter is active, or exposes placeholders and a VentureChat listener adapter when another chat plugin owns formatting.

## Features

- Recipient-aware chat translation through ordinary text-generation models: Gemini, OpenAI-compatible APIs, Anthropic, xAI, Groq, OpenRouter, and local Ollama endpoints.
- Minecraft locales, Java's ISO-639 language list, and custom language names or codes. For non-Minecraft locales, AI providers use Latin-letter romanization (for example, Bengali as `Ekhane Asho`).
- One-off `/translate <target-language> <text>` translations use the same queue, cache, and provider settings as chat.
- Multiple keys per provider and multiple model fallbacks. Keys rotate across concurrent batches; a key receiving HTTP 429 is skipped until restart.
- Short-window batch queues, per-message translation caching, source-language hints, and target lists built from the actual local chat recipients.
- Country flag selection for ISO countries and **Earth** (the default flag), plus optional IP-country detection, language and country blacklists, and paged `/bc menu` screens.
- A generated Java resource pack containing 249 country glyphs and a rectangular Earth flag, using a custom Adventure font and Twemoji country artwork.
- MiniMessage chat formats, tab-list flags, optional scoreboard nametag prefixes, world allow/deny lists, PlaceholderAPI, LuckPerms groups, and Vault permission-provider groups.
- Standalone SQLite storage or shared MariaDB storage in proxy mode.
- Three output jars: Paper/Folia, BungeeCord, and Velocity. bStats is included with plugin IDs 34609, 34611, and 34610 respectively.

## Requirements

- Java 25
- Paper 26.2 or 26.3, or compatible Folia builds, for the server plugin
- MariaDB for proxy mode; SQLite is supported for a standalone Paper server
- An optional translation provider key, or a reachable local Ollama endpoint

PlaceholderAPI, LuckPerms, Vault, and VentureChat are optional integrations. BetterChat uses Adventure components for chat it formats itself.

## Install

### One Paper or Folia server

1. Download `BetterChat-Paper-0.1.0-pre.1.jar` from the pre-release assets or build it with Gradle.
2. Put it in `plugins/` and start the server once.
3. Edit `plugins/BetterChat/config.yml` for storage and flags. Keep `proxy-mode.enabled: false` and `storage.type: sqlite` for a standalone server.
4. Edit `plugins/BetterChat/ai.yml` for chat behavior and AI provider keys. Enable `chat.translation-enabled` if you want chat translated, then restart.
5. Use `/bc menu` to choose a language and flag.

### BungeeCord or Velocity network

Install the matching proxy jar on the proxy and the Paper jar on **every backend**. Set `proxy-mode.enabled: true` in each BetterChat config. Set `storage.type: mariadb` and use the same MariaDB database credentials in every config. Generate a secret with `openssl rand -hex 32` and copy it to `proxy-mode.secret` everywhere. Give each Paper backend a unique `proxy-mode.server-id`.

Proxy mode refuses to start BetterChat if the shared MariaDB connection cannot be initialized. The proxy relays BetterChat chat packets with plugin messaging; MariaDB is required for shared player preferences and is also initialized by the proxy plugin. See [Proxy installation](docs/PROXY.md).

## Resource pack

The pack is generated at [`resource-pack/BetterChat-Flags.zip`](resource-pack/BetterChat-Flags.zip). Upload it to a public HTTPS host, then configure it in Paper's `server.properties` (`resource-pack`, `resource-pack-sha1`, and `resource-pack-prompt`) or use BetterChat's `flags.resource-pack` settings. Enable `use-glyphs` after the pack is available to players. BetterChat avoids sending a second prompt when both routes use the same URL. See [Flag pack guide](docs/RESOURCE-PACK.md) for both setups.

Country flags use a namespaced Adventure font (`betterchat:flags`); Unicode flag emoji remain the fallback. For other plugins, configure the resource pack's glyph font through that plugin's own font support. Resource-pack source, generation, hosting, and Twemoji attribution are in [Flag pack guide](docs/RESOURCE-PACK.md).

## Commands

| Command | Description |
| --- | --- |
| `/bc` or `/bc menu` | Open the language and flag menus |
| `/bc language <language>` | Select a language by code or name, for example `en_us`, `bn`, or `Toki Pona` |
| `/bc flag <country-code\|country-name\|earth\|global>` | Select a country or the global Earth flag |
| `/translation <on\|off>` | Choose whether you receive translated chat; off always shows the original |
| `/translate <target-language> <text>` | Translate one message to a language code or one-word name |
| `/bc auto [on\|off]` | Toggle IP-based country detection |
| `/bc set <online-player> <language\|flag> <value>` | Admin change to a player's preference |
| `/bc reload` | Reload BetterChat configuration |

`betterchat.use` and `betterchat.translate` are granted to everyone by default. `/bc set` and `/bc reload` require `betterchat.admin` (op by default). `/translate` works whenever a provider is configured, even when automatic chat translation is turned off. The `/translation` preference is saved per player; opted-out players do not contribute their language to a chat request unless another opted-in recipient needs it.

## Placeholders

With PlaceholderAPI installed, BetterChat registers the `betterchat` expansion:

| Placeholder | Value |
| --- | --- |
| `%betterchat_flag%` | Unicode flag emoji for the selected country |
| `%betterchat_flag_glyph%` | Resource-pack private-use glyph |
| `%betterchat_nation%`, `%betterchat_nationality%`, `%betterchat_country%` | Country name |
| `%betterchat_country_code%`, `%betterchat_nation_code%` | Country code, including `EARTH` |
| `%betterchat_global_flag%`, `%betterchat_earth_flag%` | Earth emoji |
| `%betterchat_global_flag_glyph%`, `%betterchat_earth_flag_glyph%` | Earth resource-pack glyph |
| `%betterchat_language%`, `%betterchat_language_code%` | Selected language code or custom language name |
| `%betterchat_language_name%` | English display name, including custom languages |
| `%betterchat_automatic_country%` | Whether IP detection is enabled for that player |

For internal MiniMessage format strings, use `<player>`, `<flag>`, `<global_flag>`, `<earth_flag>`, `<language>`, `<country>`, and `<message>`. See [Formatting and integrations](docs/INTEGRATIONS.md).

## Chat formatting and channels

With no recognized formatter active, BetterChat renders chat using `chat.format` or its built-in format. `chat.flag-position` selects before-name or after-name placement. These chat settings live in `plugins/BetterChat/ai.yml`. Owners can set a full MiniMessage format or turn `chat.built-in-format` off.

When a configured formatter is detected, BetterChat automatically leaves its format alone and keeps placeholders available. On standalone Paper/Folia, BetterChat can translate through modern Paper `ChatRenderer`s while preserving their per-viewer format. The optional VentureChat adapter listens to VentureChat's public pre-send event and translates its actual **local** channel recipients. This adapter is reflection-based: BetterChat does not bundle VentureChat classes or copy its GPL-3.0 code. VentureChat's own proxy channel relay remains responsible for cross-server delivery; translation for its remote channel recipients is not currently intercepted.

Other formatters can use PlaceholderAPI values in their format. Their private-message, channel, ignore, and recipient rules remain owned by that formatter. Legacy formatters that do not expose Paper's renderer or their recipient set need a formatter-specific adapter for recipient-specific translation.

Tab-list decoration is independent of chat formatting. Optional overhead nametag flags use a scoreboard team prefix. BetterChat avoids taking over an entry already assigned to another team and logs when that prevents decoration; a dedicated nametag plugin placeholder is safer where teams are already managed.

## Translation providers

Translation is off by default. Enable `chat.translation-enabled` and add keys under `translation.providers` in `plugins/BetterChat/ai.yml`. Provider order, model lists, timeouts, batch window, batch size, and queue capacity are configurable there; the translation system instruction is fixed in the plugin. On first startup after upgrading, BetterChat moves existing `chat` and `translation` settings from `config.yml` into the new `ai.yml` automatically.

AI providers receive the sender's selected language as a **probable** source-language hint and only the target languages needed by the active recipient batch. BetterChat sends chat-completion text to ordinary text models with a plugin-owned system instruction; Gemini uses its native text-generation API. Busy bursts are coalesced into batches (32 messages by default), sent with IDs, and mapped back from ID-tagged model results. The instruction asks models to preserve names, commands, URLs, formatting, and placeholders. Targets outside Minecraft's locale list are written in Latin letters using conventional romanization.

If an HTTP 429 is returned, BetterChat disables that API key for the remainder of the process lifetime and tries other configured keys/providers. Untranslated recipients can fall back to the original message, controlled by `chat.show-original-on-translation-failure`. Console output includes an original and a configured-language line when the built-in translation path is active.

See [Translation setup and provider examples](docs/TRANSLATION.md). Provider API access, billing, and terms are managed by the server owner.

## Build

```shell
./gradlew assemble
```

This compiles the three plugin jars and regenerates the flag pack. Outputs:

```text
paper/build/libs/BetterChat-Paper-0.1.0-pre.1.jar
bungeecord/build/libs/BetterChat-BungeeCord-0.1.0-pre.1.jar
velocity/build/libs/BetterChat-Velocity-0.1.0-pre.1.jar
resource-pack/BetterChat-Flags.zip
```

`python3` and Java 25 are required for the pack generator. The pack graphics are checked in, so regular Gradle builds do not need network access to fetch flags.

To collect the pre-release assets and checksums in one directory, run:

```shell
./gradlew preparePreRelease
```

The bundle is written to `build/pre-release/0.1.0-pre.1/` and contains the Paper, BungeeCord, and Velocity jars, the versioned flag pack, `RELEASE-NOTES.md`, `SHA256SUMS`, and the pack's `RESOURCE-PACK-SHA1.txt` for Minecraft server configuration.

## Documentation

- [Paper/Folia and proxy installation](docs/PROXY.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Translation providers and batching](docs/TRANSLATION.md)
- [Chat formats, commands, placeholders, and integrations](docs/INTEGRATIONS.md)
- [Resource pack generation and hosting](docs/RESOURCE-PACK.md)
- [Privacy and data handling](docs/PRIVACY.md)

## Credits

Flag and globe images are from [Twemoji](https://github.com/twitter/twemoji) and are licensed under CC BY 4.0. The complete license and attribution are included in the pack and [`resource-pack/THIRD_PARTY_NOTICES.md`](resource-pack/THIRD_PARTY_NOTICES.md). BetterChat is not affiliated with Twemoji or VentureChat.

## References

- [Paper documentation](https://docs.papermc.io/paper/)
- [Velocity documentation](https://docs.papermc.io/velocity/)
- [Adventure documentation](https://docs.papermc.io/adventure/)
- [Folia support](https://docs.papermc.io/paper/dev/folia-support/)
- [VentureChat repository](https://github.com/Aust1n46/VentureChat)
