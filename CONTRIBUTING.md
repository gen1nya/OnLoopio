# Contributing

Issues and pull requests are welcome, especially for accessibility, documentation and independently verified hardware profiles. Please keep personal device logs, credentials, serials, firmware dumps and private server details out of pull requests. Use neutral examples. `local/` is ignored for working notes; keep private keys and dumps outside the repository.

Build the app with JDK 17, Android SDK 35 and `./gradlew :app:assembleDebug test` (or `gradlew.bat` on Windows). The release firmware is built only by GitHub Actions. Describe any Y1 hardware validation and recovery steps with a firmware change. Type B support requires its own readback, partition verification and real-device testing; do not assume Type A images are compatible.

For security reports, follow [SECURITY.md](SECURITY.md). Code contributions are licensed under the repository's MIT license; third-party firmware binaries retain their separate rights.
