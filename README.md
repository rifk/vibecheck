# Vibe Check

Kotlin Multiplatform game targeting Android, iOS, and web (Wasm).

## What is implemented

- Shared domain model and gameplay engine for daily word ranking gameplay.
- `PuzzleSource` seam with bundled JSON source (`BundledPuzzleSource`) for v1.
- `SourceConfig` + `PuzzleSourceFactory` composition path so source mode can be switched later without touching game/UI layers.
- `RemotePuzzleSource` + `RemotePuzzleClient` contracts are present for backend integration, while runtime defaults remain bundled-only.
- Local persistence with `multiplatform-settings` for day state and basic stats.
- Stats include streaks, wins/averages by model, best guesses by model, and recent solve history.
- Shared Compose UI that renders model options dynamically based on each day file.
- Per-date puzzle loads are cached via `CachingPuzzleSource` in app composition.
- Content validator CLI module in `tools/content-validator`.
- 90 bundled puzzle JSON files in `content/puzzles`.
- Validator enforces the v1 continuous UTC date window (`2026-01-01` to `2026-03-31`).

## Project layout

- `composeApp`: shared KMP app and UI.
- `tools/content-validator`: JVM CLI to validate puzzle content.
- `content/puzzles`: v1 source-of-truth puzzle files.
- `docs/puzzle-schema.md`: schema and rules.

## Build and test

Suggested commands:

- `./gradlew qualityGate`
- `./gradlew :composeApp:allTests`
- `./gradlew :tools:content-validator:test`
- `./gradlew :tools:content-validator:run`

## Source isolation for future backend

Gameplay and UI depend on `PuzzleSource` only.

Current composition uses:

- `AppConfig(sourceConfig = SourceConfig(...))` in `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/app/AppContainer.kt`
- `PuzzleSourceFactory` in `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/data/PuzzleSourceFactory.kt`

`SourceMode.REMOTE` exists as a v1-safe placeholder and currently falls back to bundled source unless a remote provider is supplied.
