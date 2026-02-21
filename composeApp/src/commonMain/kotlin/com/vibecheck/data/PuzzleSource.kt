package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import kotlinx.datetime.LocalDate

interface PuzzleSource {
    suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle?
}

enum class SourceMode {
    BUNDLED,
    REMOTE
}

data class SourceConfig(
    val mode: SourceMode = SourceMode.BUNDLED,
    val remoteBaseUrl: String? = null
)
