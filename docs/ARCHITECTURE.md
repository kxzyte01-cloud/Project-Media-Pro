# Architecture Guide

Project Media Pro uses a modular, additive architecture so future steps can be added by creating new files and touching at most **one registration entry** in existing files.

## Module Structure

- **`:app`** — Android app module (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`). Contains `MainActivity`, `MediaProApplication`, `AppContainer` (manual dependency container), `AppNavHost` (single-file route definitions), feature packages (`home`, `editor`, `settings`), and Android resources.
- **`:core:model`** — Pure Kotlin/JVM module (`org.jetbrains.kotlin.jvm`) with zero Android imports. Defines versioned models (`Project`, `Track`, `Clip`, `Template`, `Slot`, `SlotType`) and `ProjectSerializer` (`kotlinx.serialization` JSON with `schemaVersion` checks and `ignoreUnknownKeys = true`).
- **`:core:designsystem`** — Android library module (`com.android.library`, `compileSdk = 37`, `minSdk = 26`). Contains WCAG-compliant colors (`MediaProTokens`), spacing/shape tokens (`MediaProSpacing`, `MediaProShapes`), system-font typography (`MediaProTypography`), `ProjectMediaProTheme`, and reusable components (`MediaProTopBar`, `SectionHeader`).
- **`:core:engine` (Planned for Step 3+)** — Future Android library module wrapping AndroidX Media3 (`ExoPlayer` for preview in Step 3 and `Transformer` for export in Step 4) behind Kotlin interfaces.

## Unidirectional Data Flow (UDF) & Wiring

1. **State:** Each `ViewModel` exposes an immutable `StateFlow<UiState>` collected via `collectAsStateWithLifecycle()`.
2. **Events:** Interactive screens emit a sealed `Event` back to `viewModel.onEvent(event)`.
3. **Manual Wiring (`AppContainer`):** `MediaProApplication` initializes `AppContainer`, and `MainActivity` passes `AppContainer` into `AppNavHost(container)`. `AppNavHost` never takes feature-specific parameters.

## Additive Extension Rules ("New Files + One Registration Line")

### 1. How to Add a New Screen
1. **Add new files:** Create `<Feature>ViewModel.kt` and `<Feature>Screen.kt` under `app/src/main/kotlin/com/projectmediapro/app/<feature>/` (and add any needed repository property to `AppContainer.kt`).
2. **One registration entry:** In `AppNavHost.kt`, add the route constant to `AppRoutes` and one `composable(AppRoutes.<FEATURE>) { ... }` block (both in the same file) pulling dependencies from `container`. Existing screens are untouched.

### 2. How to Add a New Effect
1. **Add new files:** In Step 7, create `<EffectName>Spec.kt` in `:core:model` and `<EffectName>Renderer.kt` in `:core:engine`.
2. **One registration entry:** Register the effect in `EffectRegistry.kt` with a single entry. No existing effect or timeline files are rewritten.

### 3. How to Add a New Template Slot Type
1. **Add new files:** In Step 6, create the slot handler/validator file in the template feature package.
2. **One registration entry:** Add one enum entry to `SlotType` in `core/model/src/main/kotlin/com/projectmediapro/core/model/Slot.kt`. `ProjectSerializer` and `Template` handle it automatically.

## Roadmap Alignment

- **Step 0:** Foundations
- **Step 1:** App shell (done)
- **Step 2:** Project data and local save
- **Step 3:** Import (Photo Picker), preview and timeline view
- **Step 4:** Export with Media3 Transformer, tested on a real phone
- **Step 5:** Basic edits, overlay tracks (PiP, GIF), and ordered audio tracks
- **Step 6:** Templates
- **Step 7:** Effects, transitions, text
- **Step 8:** Offline speech-to-text captions (bundled model) and `.srt`/`.vtt` extras
- **Step 9:** Release hardening
