# Project Media Pro v1.0.0 — On-Device Smoke Test Checklist

Run this 10-minute verification on a physical Android phone (API 26–34) using the APK built by GitHub Actions CI:

1. **Home Screen & Project Management**
   - Create a new project, import 2+ videos, return to Home, and confirm the project card shows its real thumbnail, aspect badge, duration, and clip count.
   - Test **Search**, **Sort (Recent / A–Z)**, **Rename**, **Duplicate** (confirm copy keeps clips & captions), and **Delete**.
2. **Core Editing, LUTs & Audio Studio**
   - Trim/split a video clip, apply a viral LUT (`Misty Moss` / `Golden Sunset` / `Crimson Mood`) + motion transition, and verify live preview playback.
   - Add an audio track, enable an Audio Studio EQ preset + **Auto-Duck**, and verify live audio preview.
   - Verify **Undo** and **Redo** restore previous states cleanly.
3. **Offline Captions & Viral Lyric Templates**
   - Open Captions, generate/import captions (or sync script), switch between viral lyric templates (e.g., `Misty Kinetic`, `Crimson Zine`), and preview on the video stage.
4. **Rich Custom Templates, Style DNA & Remix Drafts**
   - In the Editor top-bar **Templates** menu, tap **Save as custom template** (mark video Replaceable, audio Locked).
   - Open **Templates** from Home, verify the visual preview banner and category filter chips, and use your custom template with **Pick all videos at once**.
   - Back in the Editor, test **Apply style from template** (confirm 1-tap Undo reverts it) and **Create remix draft** (`Rhythm Cut` — confirm original project stays untouched).
5. **MP4 Export & Settings Storage Cleanup**
   - Export a 1080p MP4 to Gallery and play it back in the device gallery app to verify A/V sync, LUTs, transitions, and captions.
   - Open **Settings**, check **Storage & Cache**, tap **Clear export cache**, and verify the bundled Privacy Policy text.
