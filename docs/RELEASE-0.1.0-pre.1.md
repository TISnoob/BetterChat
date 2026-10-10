# BetterChat 0.1.0-pre.1

BetterChat 0.1.0-pre.1 is ready for testing. It brings recipient-aware multilingual chat and country flags to Paper/Folia servers, with proxy support for both Velocity and BungeeCord networks.

## Included in this pre-release

- `BetterChat-Paper-0.1.0-pre.1.jar` for Paper and compatible Folia servers
- `BetterChat-Velocity-0.1.0-pre.1.jar` for Velocity proxy mode
- `BetterChat-BungeeCord-0.1.0-pre.1.jar` for BungeeCord proxy mode
- `BetterChat-Flags-0.1.0-pre.1.zip`, the generated resource pack with 249 country flags and the Earth glyph

## Highlights

- Translate chat for each recipient's selected language using Gemini, OpenAI-compatible, Anthropic, xAI, Groq, OpenRouter, or local Ollama providers.
- Choose a language and country flag from the in-game `/bc menu`.
- Preserve existing chat formatting and integrate with PlaceholderAPI, LuckPerms, Vault, VentureChat, Essentials, ChatControl, LPC, and TAB.
- Share player preferences and relay chat across a network through MariaDB-backed proxy mode.
- Use the optional `betterchat:flags` font for crisp resource-pack flag glyphs, with Unicode fallbacks when the pack is unavailable.

## Before testing

- Java 25 is required.
- Translation is disabled by default. Configure a provider in `plugins/BetterChat/ai.yml`, then enable `chat.translation-enabled` when ready.
- For proxy mode, install the matching proxy jar and the Paper jar on every backend. Use the same 32+ character secret and MariaDB settings everywhere.
- Host `BetterChat-Flags-0.1.0-pre.1.zip` at a public HTTPS URL and configure its URL and SHA-1 before enabling glyphs. The resource pack is optional; Unicode flag fallbacks remain available without it.
- This is a pre-release. Please test on a staging server, keep backups of existing configurations, and report platform/version details with any issue.

## Feedback

Please try the standalone Paper/Folia setup and both proxy modes, especially mixed-language chat, reconnects, resource-pack delivery, and existing formatter integrations. Feedback on compatibility, translation quality, and configuration clarity will shape the first stable release.
