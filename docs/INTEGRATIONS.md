# Chat formats and integrations

## Built-in MiniMessage format

When BetterChat owns chat formatting, configure `chat.format` with MiniMessage tags and these placeholders:

```yaml
chat:
  built-in-format: true
  format: '<dark_gray>[<flag>] <gold><player></gold><dark_gray>: <white><message>'
```

Use `<flag>` for the sender's selected country, `<global_flag>` or `<earth_flag>` for Earth, `<player>` for the sender name, `<language>` and `<country>` for selected codes, and `<message>` for the safe text component. When `chat.format` is blank, `chat.flag-position` selects the built-in before-name or after-name layout.

Players start with the global Earth flag unless `defaults.country` is changed. They can select a different flag with `/bc flag <country-code>` or from `/bc menu`.

## PlaceholderAPI

Install PlaceholderAPI and restart. BetterChat registers `%betterchat_...%` placeholders, including flag emoji, flag glyph, country, country code, language, language name, Earth, and automatic-country state. The complete list is in the README.

The `_glyph` placeholders are private-use characters. A placeholder by itself is only text; the resource pack must be installed, and the consuming formatter must apply the `betterchat:flags` font. BetterChat can attach this font directly to components it renders. Other chat plugins may only support plain placeholder text.

## Formatter auto-detection

BetterChat checks plugin names in `chat.detected-formatters`. If a listed plugin is enabled and `chat.auto-disable-format-with-known-plugin` is true, BetterChat's built-in format turns itself off to avoid competing renderers. Add the plugin name for your formatter if it is missing.

Other formatters keep their own chat pipeline. Insert the relevant PlaceholderAPI values in their format strings. On standalone Paper/Folia, if a formatter uses Paper's modern `AsyncChatEvent` renderer, BetterChat can translate each actual viewer's message and pass the translated component through that existing renderer. Legacy formatter events that do not expose Paper's renderer or recipients need a formatter-specific adapter; in that case BetterChat leaves their chat output unchanged.

## VentureChat

When VentureChat is installed, BetterChat dynamically registers against VentureChat's public `VentureChatEvent`. It snapshots and clears the mutable local recipient set immediately before VentureChat sends, then uses those recipients to determine language targets and send translated channel chat once. VentureChat's own filtering (listening, permissions, ignore, range, and channel rules) is applied before the event and therefore determines the BetterChat audience.

The adapter uses reflection, so VentureChat is not bundled or hard-linked. Cross-server VentureChat routing still belongs to VentureChat. BetterChat translates the source backend's pre-send recipients; VentureChat's remote receive path sends its own packets outside that cancellable event, so BetterChat does not currently translate those remote channel recipients. Do not add the ordinary Paper chat formatter and a second channel bridge on top of VentureChat.

## Tab list and overhead names

`flags.tab-list.enabled` decorates the Paper player list name. `flags.nametag.enabled` adds a scoreboard prefix to overhead names, subject to `flags.nametag.worlds` and `flags.nametag.blacklist-worlds`. The nametag feature avoids taking a player out of a team another plugin already owns. If another scoreboard/nametag plugin manages teams, use its PlaceholderAPI integration instead.

## Groups

With `groups.enabled`, BetterChat can assign a language group, country group, or both. LuckPerms groups are created as needed. The Vault provider requires a live Vault permission provider that implements player group operations. BetterChat manages group names beginning with `groups.group-prefix`.
