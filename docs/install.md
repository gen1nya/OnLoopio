# Install and recovery — Y1 Type A

This firmware is for the verified **Innioasis Y1 Type A** partition and boot profile. Do not use it on Type B or another player. A Windows PC, USB data cable, the Innioasis Updater/SP Flash Tool package with its drivers, and enough space for a private backup are required.

1. Download the firmware ZIP and `SHA256SUMS.txt` from [GitHub Releases](https://github.com/smbdsbrain/OnLoopio/releases). In PowerShell, run `Get-FileHash .\OnLoopio-Y1-TypeA-v0.9.0.zip -Algorithm SHA256` and compare it with the published value.
2. Extract the ZIP. Power off the Y1. Keep the included `manifest.json` and scatter file beside `install-firmware.ps1`, `boot.img`, and `system.img`.
3. Open PowerShell in that extracted folder. Run `.\install-firmware.ps1 -Updater 'C:\Path\To\Innioasis Updater'`. The default Updater location is under your local AppData. Use a USB data cable when prompted.
4. The installer verifies both image sizes, SHA-256 hashes, and headers. It reads a private profile plus BOOT, RECOVERY and SYSTEM to a dated directory under `%LOCALAPPDATA%\OnLoopio\secrets`. The profile must match the verified Type A partition map. **Keep that directory private and copy it somewhere safe.**
5. Only after backup verification does it ask for `FLASH`. It writes **BOOTIMG and ANDROID (SYSTEM) only**, then checks the SP Flash Tool completion log. Disconnect and boot the player.
6. Complete [USB setup](setup.md). Select the music you want to download again.

An interruption or missing success log must be treated as an incomplete flash. Preserve the logs and backup. To restore your saved BOOT and SYSTEM, run `.\install-firmware.ps1 -Updater 'C:\Path\To\Innioasis Updater' -RestoreBackup 'C:\Path\To\backup-YYYYMMDD-HHMMSS'`; verify that the backup belongs to the same device. The installer checks its hashes before writing. Recovery does not restore USERDATA or music files.

Moving from an older personalized development image to the public signing key requires clearing the previous OnLoopio app data, then setting up the account again. The music cache may be cleared for a clean migration; it remains empty until you deliberately select music for download. Preserve any personal music separately.
