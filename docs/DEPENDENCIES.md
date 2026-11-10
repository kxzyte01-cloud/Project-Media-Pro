# Dependencies

Every third-party plugin and library used in Project Media Pro is listed below. All dependencies use open-source licenses compatible with Apache License 2.0.

## Build & Compiler Plugins

| Name | Artifact / Plugin ID | Version | License | Reason |
| :--- | :--- | :--- | :--- | :--- |
| Android Gradle Plugin (Application) | `com.android.application` | `9.3.1` | Apache-2.0 | Builds and packages the `:app` Android application module (includes built-in Kotlin support). |
| Android Gradle Plugin (Library) | `com.android.library` | `9.3.1` | Apache-2.0 | Builds the `:core:designsystem` Android library module (includes built-in Kotlin support). |
| Kotlin JVM Gradle Plugin | `org.jetbrains.kotlin.jvm` | `2.4.20` | Apache-2.0 | Compiles the pure Kotlin/JVM `:core:model` module without Android dependencies. |
| Kotlin Compose Compiler Plugin | `org.jetbrains.kotlin.plugin.compose` | `2.4.20` | Apache-2.0 | Official Kotlin compiler plugin for Jetpack Compose UI generation. |
| Kotlin Serialization Compiler Plugin | `org.jetbrains.kotlin.plugin.serialization` | `2.4.20` | Apache-2.0 | Generates compile-time serializers for `@Serializable` classes in `:core:model`. |

## Runtime Libraries

| Name | Maven Coordinate | Version | License | Reason |
| :--- | :--- | :--- | :--- | :--- |
| AndroidX Core KTX | `androidx.core:core-ktx` | `1.19.0` | Apache-2.0 | Idiomatic Kotlin extensions for core Android framework APIs. |
| AndroidX Activity Compose | `androidx.activity:activity-compose` | `1.13.0` | Apache-2.0 | Hosts Jetpack Compose content inside `ComponentActivity` (`setContent`, `enableEdgeToEdge`). |
| AndroidX Lifecycle Runtime Compose | `androidx.lifecycle:lifecycle-runtime-compose` | `2.11.0` | Apache-2.0 | Provides lifecycle-aware `collectAsStateWithLifecycle` for `StateFlow`. |
| AndroidX Lifecycle ViewModel Compose | `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.11.0` | Apache-2.0 | Scopes `ViewModel` instances to Compose destinations. |
| AndroidX Navigation Compose | `androidx.navigation:navigation-compose` | `2.10.2` | Apache-2.0 | Declarative navigation between Home, Editor, and Settings screens. |
| AndroidX DataStore Preferences | `androidx.datastore:datastore-preferences` | `1.2.1` | Apache-2.0 | Persists the user's Appearance theme preference (`System`, `Light`, `Dark`) asynchronously on disk. |
| Jetpack Compose BOM | `androidx.compose:compose-bom` | `2026.09.00` | Apache-2.0 | Aligns all Jetpack Compose UI, Graphics, Tooling, and Material 3 versions. |
| Jetpack Compose UI | `androidx.compose.ui:ui` | Managed by BOM | Apache-2.0 | Core Compose layout, drawing, and input primitives. |
| Jetpack Compose UI Graphics | `androidx.compose.ui:ui-graphics` | Managed by BOM | Apache-2.0 | Color and vector graphics primitives for the design system. |
| Jetpack Compose UI Tooling Preview | `androidx.compose.ui:ui-tooling-preview` | Managed by BOM | Apache-2.0 | Preview annotations for stateless design system components. |\n| Jetpack Compose UI Tooling | `androidx.compose.ui:ui-tooling` | Managed by BOM | Apache-2.0 | Debug-only tooling required for Android Studio Compose previews. |
| Jetpack Compose Material 3 | `androidx.compose.material3:material3` | Managed by BOM | Apache-2.0 | Material 3 components (`Scaffold`, `TopAppBar`, `RadioButton`, `Surface`, `Text`, `IconButton`). |
| Kotlinx Serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.12.0` | Apache-2.0 | Reflection-free JSON serialization for `Project` and `Template` models in `:core:model`. |
| Kotlinx Coroutines Core | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.11.0` | Apache-2.0 | `StateFlow`, `Flow`, and structured concurrency for ViewModels and DataStore. |\n| Kotlinx Coroutines Android | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.11.0` | Apache-2.0 | Supplies the Android Main dispatcher used by lifecycle-scoped ViewModel coroutines. |\n| AndroidX Media3 ExoPlayer | `androidx.media3:media3-exoplayer` | `1.11.1` | Apache-2.0 | Local video playback for the editor preview. |\n| AndroidX Media3 UI | `androidx.media3:media3-ui` | `1.11.1` | Apache-2.0 | Provides the native PlayerView used to display the video preview. |

## Test-Only Libraries

| Name | Maven Coordinate | Version | License | Reason |
| :--- | :--- | :--- | :--- | :--- |
| JUnit 4 | `junit:junit` | `4.13.2` | EPL-1.0 | Standard unit testing runner and assertions for `:core:model` and `:app` JVM unit tests. |
| Kotlinx Coroutines Test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.11.0` | Apache-2.0 | Deterministic `runTest` and test dispatchers for testing Home and Settings ViewModel coroutines. |

