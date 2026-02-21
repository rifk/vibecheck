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
    val solvedByModelId: String?,
    val guessesByModel: Map<String, List<GuessOutcome>>
)

@Serializable
data class PlayerStats(
    val totalWins: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val solvedDates: Set<String> = emptySet(),
    val lastSolvedDate: String? = null,
    val totalGuessesByModel: Map<String, Int> = emptyMap(),
    val winsByModel: Map<String, Int> = emptyMap()
) {
    fun averageGuessesByModel(): Map<String, Double> {
        if (winsByModel.isEmpty()) return emptyMap()
        return winsByModel.mapNotNull { (modelId, wins) ->
            if (wins <= 0) {
                null
            } else {
                val totalGuesses = totalGuessesByModel[modelId] ?: 0
                modelId to (totalGuesses.toDouble() / wins.toDouble())
            }
        }.toMap()
    }
}

@Serializable
data class PersistedAppState(
    val dayStates: Map<String, DayPlayState> = emptyMap(),
    val stats: PlayerStats = PlayerStats()
)
