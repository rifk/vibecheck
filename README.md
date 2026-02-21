# Vibe Check

Kotlin Multiplatform game targeting Android, iOS, and web (Wasm).

## What is implemented

- Shared domain model and gameplay engine for daily word ranking gameplay.
- `PuzzleSource` seam with bundled JSON source (`BundledPuzzleSource`) for v1.
- `SourceConfig` + `PuzzleSourceFactory` composition path so source mode can be switched later without touching game/UI layers.
- Local persistence with `multiplatform-settings` for day state and basic stats.
- Shared Compose UI that renders model options dynamically based on each day file.
- Content validator CLI module in `tools/content-validator`.
- 90 bundled puzzle JSON files in `content/puzzles`.

## Project layout

- `composeApp`: shared KMP app and UI.
- `tools/content-validator`: JVM CLI to validate puzzle content.
- `content/puzzles`: v1 source-of-truth puzzle files.
- `docs/puzzle-schema.md`: schema and rules.

## Build and test

Suggested commands:

- `./gradlew :composeApp:allTests`
- `./gradlew :tools:content-validator:test`
- `./gradlew :tools:content-validator:run --args='/Users/rif/code/testProject/content/puzzles'`

## Source isolation for future backend

Gameplay and UI depend on `PuzzleSource` only.

Current composition uses:

- `AppConfig(sourceConfig = SourceConfig(...))` in `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/app/AppContainer.kt`
- `PuzzleSourceFactory` in `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/data/PuzzleSourceFactory.kt`

`SourceMode.REMOTE` exists as a v1-safe placeholder and currently falls back to bundled source unless a remote provider is supplied.
