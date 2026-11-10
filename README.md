# Project Media Pro

Project Media Pro is an open-source, offline-first native Android video editor written in Kotlin with Jetpack Compose and Material 3.

## Project Status

**Early Development (Steps 0 & 1 — Foundations & App Shell).**
This repository currently contains the foundational multi-module architecture (`:app`, `:core:model`, `:core:designsystem`), the versioned project/template JSON serialization model, the design system, and the Home and Settings screens. Video import, timeline editing, templates, and media export are planned in upcoming steps (see `docs/ROADMAP.md`).

## Privacy & Offline Guarantee

- **No network access:** The app does not request `android.permission.INTERNET` (enforced by CI).\n- **Gallery privacy:** Video selection uses Android Photo Picker; the app does not request broad gallery/storage permissions. Selected media is retained through a persistable grant or copied into app-private storage.
- **No telemetry:** Zero analytics, ads, crash reporters, remote fonts, or remote assets.
- **No cloud backups:** `android:allowBackup="false"` is set in the manifest.

## Requirements

- **JDK:** 17
- **Gradle:** 9.6.0
- **Kotlin:** 2.4.20
- **Android SDK:** `compileSdk = 37` with minor API level 0, `targetSdk = 36`, `minSdk = 26`

## How to Build and Test

Because this repository forbids checked-in binary files (including `gradle-wrapper.jar`), invoke a locally installed Gradle 9.6.0 binary directly:

```bash
# Run Android Lint, JVM + Android unit tests, and assemble Debug + unsigned Release APKs
gradle lintDebug :core:model:test :core:designsystem:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

If you wish to generate a local Gradle wrapper on your workstation (do not commit the resulting `.jar` binary):

```bash
gradle wrapper --gradle-version 9.6.0
```

> **Note on Debug APKs:** Debug APKs downloaded from different CI runs may be signed with different ephemeral debug keys. Uninstall any existing copy of Project Media Pro from your device before installing a new CI artifact.

## License

Licensed under the Apache License, Version 2.0. See `LICENSE` for the full text.
