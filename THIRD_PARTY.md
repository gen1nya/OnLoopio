# Third-party components

The OnLoopio application code is independently implemented. Its MIT license does
not cover binaries obtained from other projects.

The APK bundles `org.conscrypt:conscrypt-android:2.5.2` from Maven Central under
Apache 2.0, with its ARMv7 BoringSSL native library. Upstream:
[Conscrypt](https://github.com/google/conscrypt/tree/2.5.2). Artifact SHA-256:
`42d18979caf53f5ef68548c76d4c98b41adb910a32ad9448133f9c5b20bd65a3`.
Copyright/license notices, including modified Netty and Apache Harmony code,
and BoringSSL's license text are in `app/src/main/assets/licenses/` and included
in the APK. They are not relicensed by this project's MIT license.

The firmware package contains binary parts from
[y1-stock-rom 3.1.2](https://github.com/y1-community/y1-stock-rom/releases/tag/Latest-3.1.2)
and [y1-ata-rom Type A 0.1](https://github.com/y1-community/y1-ata-rom/releases/tag/0.1).
The release tool downloads and checks those exact archives before composing BOOT
and SYSTEM. These upstream repositories do not state an explicit binary
redistribution license. This notice records provenance; it does not grant
rights to those components or relicense them as MIT. The stock firmware,
vendor files and device trademarks belong to their respective owners.

KXML 2.3.0 is used only by host test tooling and is not included in the APK.
Gradle wrapper/tooling has its own Apache 2.0 license. Android SDK, emulators,
Windows flash drivers and local development dumps are not in the repository.

The documentation screenshots are captures of a physical Y1 and may show album
art and music metadata from third parties. Those underlying works are not
licensed under the OnLoopio MIT license.
