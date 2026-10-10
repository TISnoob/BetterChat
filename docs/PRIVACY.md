# Privacy and data handling

BetterChat stores each player's UUID, selected language, selected country, and automatic-country preference. In proxy mode, those preferences are stored in the configured shared MariaDB database. Standalone mode stores them in SQLite or MariaDB.

When IP-based country detection is enabled, BetterChat sends the connecting IP address to the configured lookup endpoint (by default `ipapi.co`) and reads the returned country code. Private and loopback addresses are skipped. The IP address is not written to the BetterChat preferences table.

When translation is enabled, chat text is sent to the configured text-model provider(s). AI provider requests include the sender's selected language as a probable source hint and only the target languages required by recipients of that message. Providers have their own data retention, logging, billing, and processing terms; review them before enabling a service.

bStats sends anonymous plugin metrics when enabled. Set `bstats.enabled: false` in the relevant plugin config to disable it.

Server owners are responsible for disclosing these data flows to players and configuring provider keys, database access, and IP lookup in line with their privacy requirements.
