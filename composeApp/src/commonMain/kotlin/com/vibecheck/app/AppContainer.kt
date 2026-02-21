package com.vibecheck.app

import com.russhwolf.settings.Settings
import com.vibecheck.data.CachingPuzzleSource
import com.vibecheck.data.InMemoryGameStateStore
import com.vibecheck.data.PuzzleSourceFactory
import com.vibecheck.data.SafeGameStateStore
import com.vibecheck.data.SettingsGameStateStore
import com.vibecheck.data.SourceConfig
import com.vibecheck.domain.SystemUtcDateProvider

object AppContainer {
    fun createController(config: AppConfig = AppConfig()): GameController {
        val source = CachingPuzzleSource(PuzzleSourceFactory().create(config.sourceConfig))
        val store = runCatching {
            SafeGameStateStore(SettingsGameStateStore(Settings()))
        }.getOrElse {
            InMemoryGameStateStore()
        }
        return GameController(
            puzzleSource = source,
            gameStateStore = store,
            utcDateProvider = SystemUtcDateProvider
        )
    }
}

data class AppConfig(
    val sourceConfig: SourceConfig = SourceConfig()
)
