package com.vibecheck.app

import com.russhwolf.settings.Settings
import com.vibecheck.data.CachingPuzzleSource
import com.vibecheck.data.BundledGuessLexicon
import com.vibecheck.data.BundledModelCatalogSource
import com.vibecheck.data.InMemoryGameStateStore
import com.vibecheck.data.ModelCatalogSource
import com.vibecheck.data.PuzzleSourceFactory
import com.vibecheck.data.SafeGameStateStore
import com.vibecheck.data.SettingsGameStateStore
import com.vibecheck.data.SourceConfig
import com.vibecheck.domain.SystemUtcDateProvider

object AppContainer {
    fun createController(config: AppConfig = AppConfig()): GameController {
        val source = CachingPuzzleSource(PuzzleSourceFactory().create(config.sourceConfig))
        val modelCatalogSource: ModelCatalogSource = BundledModelCatalogSource()
        val store = runCatching {
            SafeGameStateStore(SettingsGameStateStore(Settings()))
        }.getOrElse {
            InMemoryGameStateStore()
        }
        return GameController(
            puzzleSource = source,
            modelCatalogSource = modelCatalogSource,
            gameStateStore = store,
            utcDateProvider = SystemUtcDateProvider,
            guessLexicon = BundledGuessLexicon()
        )
    }
}

data class AppConfig(
    val sourceConfig: SourceConfig = SourceConfig()
)
