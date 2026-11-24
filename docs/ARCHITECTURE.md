# Architecture Guide (v1.0.0)

Project Media Pro uses a clean, modular, unidirectional-data-flow (UDF) architecture designed to keep domain logic pure JVM-testable and UI screens stateless.

## Module Structure

- **`:app`** (`com.android.application`, `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`):
  - `AppContainer` — Manual dependency container initialized by `MediaProApplication` and passed into `AppNavHost`.
  - `home/` — Project list with cached thumbnails (`TimelineMediaCache`), metadata pills (`HomeMetadataFormatter`), search/sort, rename, duplicate, and delete.
  - `editor/` — Full non-destructive studio (`EditorViewModel`, `EditorStateHelpers`, `EditorScreen`, Media3 `PreviewPlayer`, `AudioPreviewPlayer`, `LiveAudioEnhanceProcessor`, `OfflineBeatDetector`, and focused editing dialogs).
  - `data/export/` — Media3 1.4.1 `Transformer` export pipeline (`ExportPlan`, `Media3VideoExporter`, shared `AudioDspChain`, `SoftClipAudioProcessor`, `OpticalFlowGlEffect`, `VideoEffectFactory`).
  - `data/project/` & `data/media/` — Atomic file persistence (`AtomicFileStore`, `FileProjectRepository`), path-traversal-safe `ProjectMediaStorage`, and `ProjectMediaHealthChecker`.
  - `data/template/` & `template/` — Rich custom templates (`ProjectTemplateSaver`, `TemplateProjectCreator`, `TemplatePackCodec`, `TemplatesScreen`, `TemplatePreviewBanner`, `UseTemplateScreen`).
  - `feature/captions/` — On-device Open Whisper JNI bridge (`whisper.cpp`), `WhisperModelManager`, `LyricsPacingEngine`, `CaptionCanvasRenderer`, `SrtCaptionCodec`, and `CaptionsSheet`.
  - `settings/` — Theme persistence (`DataStore`), `AppStorageInspector` (storage usage & 1-tap export cache cleanup), Whisper model manager UI, and bundled offline privacy policy.
- **`:core:model`** (Pure Kotlin/JVM — zero Android imports):
  - `schemaVersion = 1` backward-compatible `@Serializable` models (`Project`, `Track`, `Clip`, `Template`, `Slot`, `TemplateTextClip`, `AudioEnhanceConfig`, `ClipVisualEffects`, `OverlayVisualStyle`, `SpeedRampPreset`).
  - Pure domain engines (`ProjectTimeline`, `UndoRedoManager`, `TemplateApplier`, `ProjectToTemplateConverter`, `TemplateStyleApplier`, `ProjectRemixEngine`, `StarterTemplates`).
- **`:core:designsystem`** (`com.android.library`, `compileSdk = 34`, `minSdk = 26`):
  - Semantic Material 3 color, spacing, shape, and typography tokens (`ProjectMediaProTheme`, `MediaProSpacing`, `MediaProShapes`, `MediaProTopBar`, `SectionHeader`).

## Key Architectural Invariants
1. **Unidirectional Data Flow:** Each `ViewModel` exposes an immutable `StateFlow<UiState>` collected via `collectAsStateWithLifecycle()`, with one-shot navigation via `Channel` (`effects`).
2. **Schema Stability:** `Project` and `Template` remain on `schemaVersion = 1`; all new `@Serializable` fields carry safe defaults and `ignoreUnknownKeys = true`.
3. **Shared Preview & Export DSP:** Audio Studio EQ/noise-gate/ducking (`AudioDspChain`) and color grading/transitions (`ClipVisualEffects`) share pure math between live preview and Media3 export.
4. **Atomic Storage & Rollback:** Project/template writes use atomic temp-file replacement (`AtomicFileStore`), and failed duplicates, template saves, or remix drafts roll back cleanly.
