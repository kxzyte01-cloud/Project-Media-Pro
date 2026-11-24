# Security Policy

## Supported Versions

| Version | Supported |
| :--- | :--- |
| `1.0.x` | Yes |

## Security & Offline Privacy Guarantees

- **Offline-first editing:** Video editing, playback, captions, templates, and MP4 export run 100% locally on-device.
- **Scoped network posture:** `android.permission.INTERNET` is used exclusively for optional, user-initiated HTTPS downloads of Open Whisper speech-to-text models in Settings. Cleartext HTTP is disabled (`android:usesCleartextTraffic="false"`), `ACCESS_NETWORK_STATE` is stripped via manifest merger (`tools:node="remove"`), and zero background network calls are made.
- **Backup & storage isolation:** Automatic Android cloud backup is disabled (`android:allowBackup="false"`). Project and template media paths are canonicalized and validated against path traversal (`ProjectMediaStorage`), and `.zip` Template Packs enforce zip-slip and bounded-size checks (`TemplatePackCodec`).
- **Safe serialization:** Project and template JSON files (`schemaVersion = 1`) are parsed using compile-time `kotlinx.serialization` with `ignoreUnknownKeys = true` and no reflection-based gadget loading.

## Reporting a Vulnerability

Please **do not** report security vulnerabilities through public GitHub issues, discussions, or pull requests.

Instead, use **GitHub Private Vulnerability Reporting** directly on this repository:
1. Navigate to the **Security** tab of this GitHub repository.
2. Click **Report a vulnerability** to open a private advisory draft visible only to the repository maintainers.
3. Include steps to reproduce the issue and the affected module (`:app`, `:core:model`, or `:core:designsystem`).
