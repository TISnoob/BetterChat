# Installation and proxy setup

## Standalone Paper or Folia

1. Install `BetterChat-Paper-*.jar` in the server's `plugins/` directory.
2. Start once, then edit `plugins/BetterChat/config.yml`.
3. Use `proxy-mode.enabled: false` and `storage.type: sqlite` (the defaults).
4. Restart and set player options with `/bc menu`.

## BungeeCord or Velocity network

Install one matching proxy module and install the Paper module on every backend. BetterChat must be present on both sides of the proxy connection.

1. Create a MariaDB database and user. Allow connections from the proxy and all backend hosts.
2. Generate one shared secret, for example with `openssl rand -hex 32`. Keep it private and copy the same value into the proxy and every backend config.
3. In the Paper config on **every backend**, turn on proxy mode and configure the same database:

   ```yaml
   proxy-mode:
     enabled: true
     server-id: survival-1 # unique per backend
    secret: 'paste-the-shared-secret-here'
   storage:
     type: mariadb
     mariadb:
       host: 10.0.0.10
       port: 3306
       database: betterchat
       username: betterchat
       password: 'change-this'
       pool-size: 8
   ```

4. In the proxy plugin config, keep `proxy-mode.enabled: true`, set `proxy-mode.secret` to the same value, set `storage.type: mariadb`, and use those same database settings.
5. Start MariaDB, then start the proxy and the Paper backends. BetterChat creates its preference table when it connects.
6. Send one player message. The Paper backend cancels its local built-in chat and sends a signed BetterChat packet through the proxy; the proxy verifies and fans it out to installed Paper backends. Each receiver formats and translates for its local players.

Proxy mode needs MariaDB and a shared secret in each plugin config. SQLite is a local file and cannot share preferences between proxy processes and backend machines. The proxy requires a reachable MariaDB connection and a valid secret before it registers the BetterChat channel. HMAC signatures keep clients from forging backend-to-proxy or proxy-to-backend chat packets.

## Chat formatters in proxy mode

The built-in BetterChat proxy relay is used when BetterChat owns the Paper chat event. If a detected formatter takes over, BetterChat leaves that event to the formatter. VentureChat's public event adapter captures its actual local channel recipients and translates the message on the backend that emitted the event. VentureChat still handles its own Bungee/Velocity channel routing. Other channel plugins need a recipient API or their own integration for per-listener translation.

Do not enable both a formatter's cross-server chat bridge and a second cross-server chat relay for the same message. That can create duplicate messages.

## Troubleshooting

- **Proxy mode does not start:** verify every plugin config says `storage.type: mariadb`, credentials are identical, and MariaDB accepts connections from all hosts.
- **No cross-server BetterChat chat:** verify the proxy jar and Paper jar match the same BetterChat build, all backends have proxy mode enabled, `server-id` is unique, and the relevant proxy plugin-message channel is allowed by other proxy plugins.
- **Flags show letters or empty boxes:** install the resource pack and enable `flags.resource-pack.use-glyphs`; use the emoji fallback until the pack is hosted and accepted.
- **VentureChat message is not translated:** check that the player has a language preference loaded, that BetterChat has translation enabled and a working provider, and that the local channel recipients are available to VentureChat's event.
