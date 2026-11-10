# AI Agent Instructions (AGENTS.md)

You are contributing to **Project Media Pro**, an open-source native Android video editor. The project owner cannot read or write code, and every change is audited line-by-line by independent reviewers before merging. Correctness, simplicity, and honesty always take priority over cleverness.

## Non-Negotiable Engineering Rules

1. **Native Android Only:** Use Kotlin, Jetpack Compose, and Material 3. Never introduce WebViews, React Native, Flutter, or cross-platform bridges.
2. **Verified Stable Versions:** Use only stable versions of Android Gradle Plugin, Kotlin, Compose BOM, and AndroidX libraries verified against Google Maven or official release notes. If a version cannot be verified, mark it `UNVERIFIED`. Maintain `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`, and `applicationId = "com.projectmediapro.app"`.
3. **Strict Offline Privacy:** Never add `android.permission.INTERNET`. Never add analytics, telemetry, ads, crash reporters, remote fonts, or remote assets. Keep `android:allowBackup="false"`.
4. **Zero Secrets or Binaries:** Never commit API keys, tokens, `.jks`, `.keystore`, `google-services.json`, `local.properties`, `gradle-wrapper.jar`, or raster images (`.png`, `.jpg`, `.webp`). All icons must be Android Vector Drawables (`.xml`).
5. **Documented Dependencies:** Every library or plugin must be declared in `gradle/libs.versions.toml` and documented in `docs/DEPENDENCIES.md` with its name, version, Apache-2.0-compatible open-source license, and reason for inclusion. Prefer fewer dependencies.
6. **No Placeholder UI:** Only render controls that actually work. Never ship disabled "coming soon" buttons, dummy project rows, or lorem ipsum text.
7. **String Resources:** Every user-facing string must live in `app/src/main/res/values/strings.xml`. Keep copy plain and concise. The one exception is the bundled offline privacy policy document in `app/src/main/res/raw/privacy_policy.txt`.
8. **Code Quality & Size Limits:**
   - Follow official Kotlin style.
   - Never use the non-null assertion operator (`!!`).
   - Never use wildcard imports (`import foo.*`).
   - Never introduce global mutable state.
   - Never leave `TODO` comments in code unless the item is explicitly tracked in `docs/ROADMAP.md`.
   - Keep files under 200 lines and functions focused.
9. **Additive Architecture:** New screens, effects, or template slot types must be added by creating new files plus at most **one registration entry** (one enum or list entry; for a screen, one route constant plus its `composable` block in `AppNavHost.kt`) in the designated registry/enum file—never by rewriting existing features.
10. **Honesty About Execution:** Never invent APIs. Never claim code was compiled, linted, or tested unless you actually ran the command in a real build environment; mark anything not run as `UNVERIFIED`.

## Definition of Done (DoD)

Before proposing or merging any pull request, verify every item below:

- [ ] **One Change Per PR:** The diff addresses a single roadmap step or bug fix without unrelated refactoring.
- [ ] **Build & Lint Pass:** `gradle lintDebug :core:model:test testDebugUnitTest assembleDebug assembleRelease` succeeds (or is honestly marked `UNVERIFIED` / "not executed" if generated offline).
- [ ] **Unit Tests Added:** Every new model, serializer, or ViewModel behavior has a deterministic unit test with a one-line comment stating what it proves.
- [ ] **No New Permissions:** The merged manifest does not add `INTERNET` or any unapproved permission.
- [ ] **No Undocumented Dependencies:** `gradle/libs.versions.toml` and `docs/DEPENDENCIES.md` match 1:1.
- [ ] **Documentation Updated:** `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` reflect the current state of the repository.

