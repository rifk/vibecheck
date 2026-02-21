package com.vibecheck.app

import com.russhwolf.settings.Settings
import com.vibecheck.data.BundledPuzzleSource
import com.vibecheck.data.SettingsGameStateStore
import com.vibecheck.domain.SystemUtcDateProvider

object AppContainer {
    fun createController(): GameController {
        val source = BundledPuzzleSource()
        val store = SettingsGameStateStore(Settings())
        return GameController(
            puzzleSource = source,
            gameStateStore = store,
            utcDateProvider = SystemUtcDateProvider
        )
    }
}
