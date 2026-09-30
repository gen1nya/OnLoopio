# Firmware profile

The public package targets the Innioasis Y1 Type A profile verified by physical readback. It uses the stock 3.1.2 kernel, Android framework and boot ramdisk with a limited ATA Type A WLAN payload. The HOME APK replaces the stock player in SYSTEM. BOOT starts with secure properties, no authorized ADB host key, and USB mass storage mode. The image contains no Navidrome account or configuration seed.

The Windows installer verifies a legacy MBR signature and expected SYSTEM offset/length from a readback, plus BOOT and RECOVERY image headers. Its write list contains only BOOTIMG and ANDROID. It does not format partitions or write preloader, NVRAM, USERDATA or storage.

Type A refers to Y1 players that originally shipped with OS 2.0.0 or later. Upgrading a Type B player does not change its hardware type. The readback checks the storage map and installed system identity; the user must also know and confirm the original Type A model. Type B and any different storage map require a separate verified hardware profile and installer review before a release.
