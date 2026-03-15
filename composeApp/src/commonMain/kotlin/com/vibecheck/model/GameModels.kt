package com.vibecheck.model

import kotlinx.serialization.Serializable

@Serializable
data class GuessOutcome(
    val guess: String,
    val rank: Int
)

@Serializable
data class DayPlayState(
    val utcDate: String,
    val selectedModelId: String,
    val solved: Boolean,
    val guessesByModel: Map<String, List<GuessOutcome>>
)

@Serializable
data class PlayerStats(
    val totalWins: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val solvedDates: Set<String> = emptySet(),
    val lastSolvedDate: String? = null,
    val solveHistoryByDate: Map<String, DaySolveRecord> = emptyMap()
)

@Serializable
data class DaySolveRecord(
    val guessesToSolve: Int
)

@Serializable
data class PersistedAppState(
    val dayStates: Map<String, DayPlayState> = emptyMap(),
    val stats: PlayerStats = PlayerStats()
)
