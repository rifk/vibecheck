# Vibe Check

Kotlin Multiplatform game targeting Android, iOS, and web (Wasm).

## What is implemented

- Shared domain model and gameplay engine for daily word ranking gameplay.
- `PuzzleSource` seam with bundled JSON source (`BundledPuzzleSource`) for v1.
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

This repo currently does not include a Gradle wrapper. Use a local Gradle installation or import in IntelliJ/Android Studio.

Suggested commands:

- `gradle :composeApp:allTests`
- `gradle :tools:content-validator:test`
- `gradle :tools:content-validator:run --args='content/puzzles'`

## Source isolation for future backend

Gameplay and UI depend on `PuzzleSource` only. Switching to backend later is expected to add a new `PuzzleSource` implementation and choose source mode in composition root without changing engine/UI code.
