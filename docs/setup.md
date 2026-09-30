# Initial USB setup

OnLoopio starts offline. It does not contain a Wi-Fi name, server address, account or certificate in its firmware image.

On the Y1, open **Settings → Share Music over USB** and connect the player to a Windows PC. From the extracted firmware ZIP, run:

```powershell
.\setup-usb.ps1 -Drive E:\
```

Use the drive letter actually assigned to the Y1. The tool asks you to confirm the drive and prompts for the Wi-Fi SSID/password, Navidrome URL and account. For a private HTTPS server you may add `-CaCertificate C:\path\to\root-ca.pem`. No credentials are passed on the command line or written to the repository.

Safely eject the Y1 storage and return it to player mode, then disconnect USB. OnLoopio reads `OnLoopio-setup.json` from the USB-visible root, removes the source file and stores the account in private app data. It connects to the named home Wi-Fi. The settings screen shows a short import status without a password. On failure it stays available offline; reconnect USB and retry.

The setup file is temporarily plain text on removable storage. Use your own computer, disconnect promptly, and do not copy that file elsewhere. A normal file deletion on FAT media is not a secure erase. The next firmware update leaves the private settings on the device, but a clean app-data reset requires running setup again.
