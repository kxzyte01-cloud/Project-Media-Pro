# Project Media Pro (v1.0.0)

An offline-first, native Android video editor built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Highlights (v1.0.0)
- **Multi-Track Non-Destructive Editor:** Video, Audio, Image/GIF Overlay (PiP with blend modes, borders, corner radius, opacity), Text overlays, and Captions with full Undo/Redo.
- **Color Grading & Pro Motion:** 15 visual filters including 3 viral LUTs (`Misty Moss`, `Golden Sunset`, `Crimson Mood`), pro motion transitions (`Fade`, `Zoom`, `Whip Pan`, `Slide Up`), speed ramps, keyframe animation, freeze frames, frame snapshots, and GLES 2.0 Optical Flow smoothing.
- **Audio Studio:** Real-time preview & export DSP chain with RBJ biquad EQ presets (`Vocal Clarity`, `Bass Boost`, `Studio Warmth`, `Lo-Fi Radio`), noise gate, smooth fades, and automatic music ducking under speech/captions.
- **Offline Captions & Viral Lyric Visualizers:** On-device Open Whisper (`whisper.cpp` JNI — optional `Tiny`, `Base`, or `Small` model download over HTTPS), `.srt` import/export, Rain-Glass glyph font, and 6 viral lyric templates (`Misty Kinetic`, `Video Mask`, `Crimson Zine`, `Cold Invert`, `Code Poem`, `Blackout`).
- **Rich Custom Templates & Remix Drafts:** Build templates directly in the real Editor (**Save as custom template** with per-clip Replaceable/Locked toggles), **Apply Style DNA** with full Undo, create isolated **Story / Rhythm / Minimal** remix drafts, batch-pick clips, and share portable `.zip` Template Packs.
- **Home & Release Hardening:** Visual project cards with cached thumbnails and duration/aspect/clip-count pills, search & sort, 1-tap rename & duplicate (with isolated media & caption cloning), missing/corrupt media detection & cleanup, storage diagnostics with 1-tap export cache cleanup, and R8/ProGuard shrinking.

## Privacy & Permissions
- Editing, playback, captions, and MP4 export run **100% offline on-device**.
- `android.permission.INTERNET` is used **exclusively** when you explicitly tap Download in Settings to fetch an optional Open Whisper speech-to-text model over HTTPS (`usesCleartextTraffic="false"`, `allowBackup="false"`, `ACCESS_NETWORK_STATE` removed).
- Zero telemetry, zero analytics, zero ad SDKs.

## Toolchain & Requirements
- **Android Gradle Plugin:** `8.5.2` | **Gradle:** `8.7` | **JDK:** `17`
- **Kotlin:** `1.9.24` | **Compose Compiler:** `1.5.14` | **Compose BOM:** `2024.06.00`
- **AndroidX Media3:** `1.4.1`
- **SDK:** `minSdk 26` | `compileSdk 34` | `targetSdk 34` | ABIs: `arm64-v8a`, `armeabi-v7a`

## Module Structure
- **`:app`** — Android application, Compose UI screens (`home`, `editor`, `template`, `settings`, `feature/captions`), ViewModels, Media3 preview/export, JNI `whisper.cpp` bridge, and local storage repositories.
- **`:core:model`** — Pure Kotlin/JVM module with zero Android imports (`Project`, `Track`, `Clip`, `Template`, `Slot`, `AudioEnhance`, `SpeedRamp`, `ProjectTimeline`, `TemplateApplier`, `TemplateStyleApplier`, `ProjectRemixEngine`, `UndoRedoManager`, and `schemaVersion = 1` JSON serialization).
- **`:core:designsystem`** — Material 3 theme tokens (`MediaProTokens`, `MediaProSpacing`, `MediaProShapes`, `MediaProTypography`) and shared UI components.

## Build & Verification
```bash
./gradlew :core:model:testDebugUnitTest :app:testDebugUnitTest lintDebug assembleDebug assembleRelease
```
