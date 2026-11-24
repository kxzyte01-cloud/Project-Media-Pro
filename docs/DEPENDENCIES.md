# Dependencies (v1.0.0)

Every third-party plugin and runtime library pinned in `gradle/libs.versions.toml` is listed below. All dependencies use open-source licenses compatible with Apache License 2.0.

## Build & Compiler Plugins

| Name | Plugin / Artifact | Version | License | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| Android Gradle Plugin | `com.android.application` / `com.android.library` | `8.5.2` | Apache-2.0 | Builds `:app` and `:core:designsystem` Android modules. |
| Kotlin Gradle Plugin | `org.jetbrains.kotlin.android` / `jvm` | `1.9.24` | Apache-2.0 | Compiles Kotlin sources across `:app`, `:core:model`, and `:core:designsystem`. |
| Kotlin Serialization Plugin | `org.jetbrains.kotlin.plugin.serialization` | `1.9.24` | Apache-2.0 | Generates compile-time JSON serializers for `schemaVersion = 1` models. |
| Compose Compiler Extension | `androidx.compose.compiler` | `1.5.14` | Apache-2.0 | Jetpack Compose compiler extension paired with Kotlin `1.9.24`. |

## Runtime Libraries

| Name | Artifact Group | Version | License | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| AndroidX Core KTX | `androidx.core:core-ktx` | `1.13.1` | Apache-2.0 | Idiomatic Kotlin extensions for Android framework APIs. |
| Jetpack Compose BOM | `androidx.compose:compose-bom` | `2024.06.00` | Apache-2.0 | Material 3 UI, Foundation, Graphics, and Runtime. |
| Activity Compose | `androidx.activity:activity-compose` | `1.9.1` | Apache-2.0 | Compose Activity hosting and Photo Picker / SAF contracts. |
| Lifecycle Runtime & Compose | `androidx.lifecycle:*` | `2.8.4` | Apache-2.0 | `ViewModel` and `collectAsStateWithLifecycle()`. |
| Navigation Compose | `androidx.navigation:navigation-compose` | `2.7.7` | Apache-2.0 | Single-activity Compose navigation graph (`AppNavHost`). |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | `1.1.1` | Apache-2.0 | Local theme preference persistence. |
| AndroidX Media3 | `androidx.media3:*` | `1.4.1` | Apache-2.0 | `ExoPlayer` live preview, `Transformer` export, and GL/Audio effects. |
| KotlinX Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-*` | `1.8.1` | Apache-2.0 | Structured concurrency for I/O, export, and background tasks. |
| KotlinX Serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.6.3` | Apache-2.0 | Schema-versioned local JSON persistence for projects, templates, and captions. |
| whisper.cpp (Native JNI) | Bundled C/C++ in `app/src/main/cpp/` | Vendored | MIT | Optional offline on-device speech-to-text transcription. |

## Test Libraries

| Name | Artifact | Version | License | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| JUnit 4 | `junit:junit` | `4.13.2` | EPL-1.0 | Pure JVM unit tests in `:core:model` and `:app`. |
| Coroutines Test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.8.1` | Apache-2.0 | Deterministic `runTest` / `UnconfinedTestDispatcher` ViewModel tests. |
| AndroidX Test / Espresso | `androidx.test.ext:junit` / `espresso-core` | `1.2.1` / `3.6.1` | Apache-2.0 | Instrumentation test runner configuration. |
