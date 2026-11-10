# Security Policy

## Supported Versions

Project Media Pro is currently in pre-release development (Step 0). Only the latest commit on the `main` branch is actively supported with security updates.

| Version | Supported |
| :--- | :--- |
| `main` (0.1.x-dev) | Yes |

## Security Guarantees

- Project Media Pro never requests `android.permission.INTERNET` and performs no network communication.
- Automatic cloud backup is disabled (`android:allowBackup="false"`).
- Project JSON files are parsed using `kotlinx.serialization` without reflection or polymorphic class loading, preventing deserialization gadget attacks.

## Reporting a Vulnerability

Please **do not** report security vulnerabilities through public GitHub issues, discussions, or pull requests.

Instead, use **GitHub Private Vulnerability Reporting** directly on this repository:
1. Navigate to the **Security** tab of this GitHub repository.
2. Click **Report a vulnerability** to open a private advisory draft visible only to the repository maintainers.
3. Include steps to reproduce the issue and the affected module (`:app`, `:core:model`, or `:core:designsystem`).

