# Online and offline behavior

OnLoopio goes online only on the configured home Wi-Fi after an authenticated Navidrome check, unless Force offline is enabled. Leaving that network closes an active online stream. Cached downloads and local music continue to work without server access.

Metadata and playlist updates are applied as complete SQLite transactions. Failed checks preserve the last good snapshot. Offline views show only tracks with a complete playable file on the device; a catalog entry alone does not count as an offline download. Download jobs persist across restarts and are processed independently of playback.

An optional user-provided CA allows HTTPS to a private Navidrome server while retaining normal chain and hostname verification. See [initial setup](setup.md) and [API connection](navidrome-api.md).
