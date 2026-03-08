# Vibe Check

Vibe Check is a Kotlin Multiplatform daily word-ranking game targeting Android, iOS, and the web with Kotlin/Wasm.

## Play Online

The web version is hosted on GitHub Pages and deployed from the repository's `main` branch.

A direct link can be added here as soon as the GitHub repository URL is finalized.

## Quick Start

Prerequisites:

- Java 17
- Android Studio for Android development
- Xcode for iOS development
- a modern browser for the web build

Common commands:

- `./gradlew qualityGate`
- `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- `./gradlew :composeApp:wasmJsBrowserDistribution`

Key outputs:

- Web production bundle: `composeApp/build/dist/wasmJs/productionExecutable`
- Android debug APK: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

`qualityGate` verifies:

- Android debug APK build via `:composeApp:assembleDebug`
- web production bundle via `:composeApp:wasmJsBrowserDistribution`
- iOS simulator framework via `:composeApp:linkDebugFrameworkIosSimulatorArm64`
- shared tests and content validation

## What Is Implemented

- Shared domain model and gameplay engine for daily word ranking gameplay
- `PuzzleSource` seam with bundled JSON source (`BundledPuzzleSource`) for v1
- `SourceConfig` and `PuzzleSourceFactory` so source mode can change without touching gameplay or UI layers
- `RemotePuzzleSource` and `RemotePuzzleClient` contracts for future backend integration, while runtime defaults remain bundled-only
- Local persistence with `multiplatform-settings` for day state and basic stats
- Stats for streaks, wins and averages by model, best guesses by model, and recent solve history
- Shared Compose UI that renders model options dynamically from each day file
- Per-date puzzle caching via `CachingPuzzleSource` in app composition
- Content validator CLI in `tools/content-validator`
- 90 bundled puzzle JSON files in `content/puzzles`
- Validator enforcement for the v1 continuous UTC date window from `2026-01-01` through `2026-03-31`

## Project Layout

- `composeApp`: shared KMP app and UI
- `tools/content-validator`: JVM CLI to validate puzzle content
- `tools/lexicon-generator`: Python CLI to build the canonical lexicon, lemma map, and prune puzzle content
- `content/puzzles`: source-of-truth puzzle files
- `content/lexicon`: canonical word list and lemma map used at runtime and validation time
- `docs/puzzle-schema.md`: schema and rules

## Run Locally By Platform

Android:

- `./gradlew :composeApp:installDebug`

Web:

- `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`

iOS:

- Open `iosApp/iosApp.xcodeproj` in Xcode and run scheme `iosApp`
- Command-line example:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath build/xcode-derived CODE_SIGNING_ALLOWED=NO build
```

## Source Isolation For Future Backend

Gameplay and UI depend on `PuzzleSource` only.

Current composition uses:

- `AppConfig(sourceConfig = SourceConfig(...))` in `composeApp/src/commonMain/kotlin/com/vibecheck/app/AppContainer.kt`
- `PuzzleSourceFactory` in `composeApp/src/commonMain/kotlin/com/vibecheck/data/PuzzleSourceFactory.kt`

`SourceMode.REMOTE` exists as a v1-safe placeholder and currently falls back to bundled source unless a remote provider is supplied.
