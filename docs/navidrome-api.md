# Navidrome connection

OnLoopio talks to Navidrome through its Subsonic API. The account is supplied during [USB setup](setup.md), never embedded in the public image. Each API request uses a fresh salt and token. The app uses Conscrypt for TLS 1.2 on Android 4.2.2, validates the hostname and certificate chain, and does not follow redirects. An explicitly supplied CA can be used for a private HTTPS server; it does not disable certificate validation.

Online access is restricted to the configured home Wi-Fi and a successful authenticated server check. Away from that Wi-Fi, the client shows saved audio and local music. See [sync behavior](sync-model.md).
