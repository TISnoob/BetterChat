# BetterChat 1.1.3

BetterChat 1.1.3 is released with major stability improvements, Paper Dialog GUI support, enhanced provider failover, and comprehensive bug fixes.

## Included in this release

- `BetterChat-Paper-1.1.3.jar` for Paper and Folia servers
- `BetterChat-Velocity-1.1.3.jar` for Velocity proxy mode
- `BetterChat-BungeeCord-1.1.3.jar` for BungeeCord proxy mode
- `BetterChat-Flags-1.1.3.zip`, the generated resource pack with country flags and Earth glyph

The release bundle also includes SHA-256 checksums for every asset and the resource pack's SHA-1 value for `server.properties` or BetterChat's resource-pack configuration.

## Highlights & Fixes in 1.1.3

- **Native Dialog GUI**: Replaced banner-based inventory GUIs with Paper's native Dialog API, rendering crisp flag glyphs and country heads directly on the buttons.
- **Lightweight Paper Jar**: Switched to Paper's native runtime library loader (`libraries:` in `plugin.yml`), dropping the Paper plugin jar from 16 MB down to 238 KB.
- **Font Inheritance Isolation**: Explicitly isolated flag glyph fonts (`betterchat:flags`) from text labels to prevent font leaking and missing-glyph boxes.
- **Fixed SQLite Driver Relocation**: Resolved native JNI linkage failure (`NoClassDefFoundError: org/sqlite/core/NativeDB`) so Paper runs out of the box with SQLite.
- **Resource Pack Delivery**: Fixed `IllegalArgumentException` by passing `null` hash when SHA-1 is unconfigured.
- **Batch Queue Resilience**: Protected the translation scheduled executor against uncaught exceptions to prevent batch timer death.
- **Failover & Multi-Model Rotation**: Corrected provider double-stepping during failovers and decoupled model rate-limit quarantining.
- **Cache Eviction Stability**: Prevented cache clearance from dropping active translation batches and resolved TTL race conditions.
- **Proxy Mode Formatter Compatibility**: Restored chat relaying and proxy message delivery on servers using chat formatting plugins.
- **Anthropic Multilingual Batches**: Scaled `max_tokens` allocation with target language count to prevent JSON truncation.
- **MySQL Compatibility**: Added `ON DUPLICATE KEY UPDATE` support for `jdbc:mysql://` connection strings.
- **Scoreboard Team Lifecycle**: Cleans up BetterChat scoreboard teams upon server disable/reload.
