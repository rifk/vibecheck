# Source Modes

`Vibe Check` uses a source abstraction for daily puzzle content.

## Current source modes

- `BUNDLED`: reads JSON files packaged with the app resources.
- `REMOTE`: reserved for backend integration; in v1, this mode falls back to bundled unless a remote provider is explicitly wired.

## Composition path

- `AppConfig` and `AppContainer`:
  - `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/app/AppContainer.kt`
- `SourceConfig`, `SourceMode`, and factory:
  - `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/data/PuzzleSource.kt`
  - `/Users/rif/code/testProject/composeApp/src/commonMain/kotlin/com/vibecheck/data/PuzzleSourceFactory.kt`

## How to add backend later

1. Create `RemotePuzzleSource` implementing `PuzzleSource`.
2. In `AppContainer`, provide `remoteProvider` to `PuzzleSourceFactory`.
3. Set `AppConfig(sourceConfig = SourceConfig(mode = SourceMode.REMOTE, remoteBaseUrl = ...))`.
4. Keep domain/UI unchanged; only composition and data source wiring should change.
