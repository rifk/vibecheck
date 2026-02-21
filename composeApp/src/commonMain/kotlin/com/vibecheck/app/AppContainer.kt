package com.vibecheck.app

import com.russhwolf.settings.Settings
import com.vibecheck.data.PuzzleSourceFactory
import com.vibecheck.data.SettingsGameStateStore
import com.vibecheck.data.SourceConfig
import com.vibecheck.domain.SystemUtcDateProvider

object AppContainer {
    fun createController(config: AppConfig = AppConfig()): GameController {
        val source = PuzzleSourceFactory().create(config.sourceConfig)
        val store = SettingsGameStateStore(Settings())
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
