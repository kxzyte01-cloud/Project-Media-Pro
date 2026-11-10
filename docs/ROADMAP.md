# Project Media Pro Roadmap

- **Step 0 — Foundations:** Base multi-module build setup (`:app`, `:core:model`, `:core:designsystem`), versioned JSON schema, design system, unit tests, CI pipeline, and documentation.
- **Step 1 — App shell (done):** Home and Settings screens with appearance theme persistence, offline bundled privacy policy, and manual `AppContainer` dependency wiring.
- **Step 2 — Project data and local save (partial):** Create, list, reopen, and delete saved `.pmp.json` project files in app-private storage. Rename and duplicate remain.
- **Step 3 — Import and preview (partial):** Android Photo Picker import, durable media storage, and Media3 ExoPlayer preview are implemented in the app module. A dedicated engine module and timeline view remain.
- **Step 4 — Export with Media3 Transformer, tested on a real phone:** Hardware-accelerated offline MP4 export with progress reporting, cancellation, and real-device verification.
- **Step 5 — Basic edits:** Clip trimming, splitting, reordering, deletion, overlay tracks (Picture-in-Picture and animated GIF placement/timing), and multiple ordered audio tracks with volume controls.
- **Step 6 — Templates:** Save and load reusable project templates with empty typed slots (`INTRO`, `OUTRO`, `PICTURE_IN_PICTURE`, `GIF`, `ORDERED_AUDIO`) built on Step 5's tracks, plus a guided slot-filling workflow.
- **Step 7 — Effects, transitions, text:** Additive non-destructive clip effects, transitions, and text overlays registered via a single-line effect registry.
- **Step 8 — Offline captions and extras:** Automatic offline speech-to-text caption generation using a model bundled in the app (no download), with `.srt` / `.vtt` subtitle import as an extra.
- **Step 9 — Release hardening:** Accessibility audit, owner review of the privacy policy (then remove the DRAFT banner from the in-app text), corrupt-media recovery, R8/reproducible build verification, owner confirmation of final `applicationId`, and 1.0 release packaging.
