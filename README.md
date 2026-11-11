# Project Media Pro

A native Android video editor built with Kotlin, Jetpack Compose, and Google Media3.

## Requirements
- Android Studio Koala / Jellyfish or newer
- JDK 17
- Compile SDK 34 / Target SDK 34 (Minimum SDK 26)

## Architecture
- **`:app`**: UI screens, navigation, ViewModels, and integration with Media3 Player & Transformer.
- **`:core:model`**: Pure Kotlin domain models, project recipes, serialization, and undo/redo history manager.
- **`:core:designsystem`**: Design system tokens, color palettes, typography, spacing, and reusable components.