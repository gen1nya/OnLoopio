# Troubleshooting

- **Setup failed:** Return the USB storage to the PC, remove any old `OnLoopio-setup.json`, then run the setup tool again. The player remains usable offline. Check the SSID, password, server URL and optional CA certificate.
- **No online library:** Check that the Y1 is on the configured home Wi-Fi and that the Navidrome server is reachable from that network. Force offline mode must be off. You can still use cached or local music.
- **No offline music after migration:** Open a playlist or track menu and choose to keep/download it. A fresh installation does not automatically fill the cache.
- **Installer stops before flashing:** Keep the backup and logs. Do not bypass a profile or hash mismatch. Check that the package is for Type A and that the Updater installation is complete.
- **Flash interrupted:** Preserve the private readback and logs. Use the [recovery instructions](install.md) with the backup from this device. Do not repeatedly flash an unverified image.
