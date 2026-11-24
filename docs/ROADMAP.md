# Project Media Pro Roadmap

## v1.0.0 — Completed (Steps 0–42)
- **Foundations & Shell (Steps 0–4):** Multi-module Gradle setup (`:app`, `:core:model`, `:core:designsystem`), `schemaVersion = 1` JSON storage, Media3 preview & hardware MP4 export.
- **Core Editing & Templates (Steps 5–18):** Trim, split, reorder, speed, transform, crop, freeze frame, audio extraction, multi-track audio, PiP/GIF overlays, text overlays, beat detection, and offline Open Whisper (`whisper.cpp`).
- **Pro Motion, Audio Studio & Viral Visualizers (Steps 19–32):** Sound-reactive beats, custom fonts, frame snapshots, portable `.zip` Template Packs, GLES 2.0 Optical Flow, speed ramps, pro motion transitions, overlay blend modes, RBJ biquad Audio Studio with live ExoPlayer preview and auto-ducking, 6 viral lyric templates, Rain-Glass font, and 3 viral LUTs.
- **Custom Template Studio & 1.0 Release Hardening (Steps 33–42):**
  - R8/ProGuard hardening (`isMinifyEnabled = true`, `isShrinkResources = true`) and 1-tap Project Duplicate (with media & caption cloning).
  - Home Search, Sort, Rename, cached video thumbnails, and Aspect/Duration/Clip-Count metadata pills.
  - Settings Storage Diagnostics, 1-tap Export Cache cleanup, and 1.0 Privacy Policy accuracy sync.
  - Rich Custom Templates (**Save as custom template** directly from the Editor, **Apply Style DNA** with Undo, 1-tap Batch Clip Picker, visual preview cards with category filter chips, auto-applied looks on all 10 starters, and deterministic **Story / Rhythm / Minimal** Remix Drafts).
  - Missing/0-byte corrupt media detection, preview exclusion, export pre-flight guard, and 1-tap undoable cleanup.

## v1.1 — Planned Post-Release
- **Snap Cuts to Beats:** 1-tap beat-aligned clip trimming inside `BeatSyncSheet` using `OfflineBeatDetector`.
- **1-Tap Motion Presets:** Ken Burns / Slow Zoom / Pan presets for video clips and still images.
- **Portable Project Backup (`.pmp.zip`):** Export and restore full self-contained projects (clips + captions) offline.
