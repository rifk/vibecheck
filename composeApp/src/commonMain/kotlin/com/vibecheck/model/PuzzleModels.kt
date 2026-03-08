package com.vibecheck.model

import kotlinx.serialization.Serializable

@Serializable
data class DayPuzzle(
    val utcDate: String,
    val answer: String,
    val models: List<ModelPuzzle>
)

@Serializable
data class ModelPuzzle(
    val modelId: String,
    val rankedWords: List<String>
) {
    fun rankForGuess(guess: String): Int? {
        val normalized = guess.trim().lowercase()
        val rank = rankedWords.indexOf(normalized)
        return if (rank >= 0) rank + 1 else null
    }
}

data class ModelMetadata(
    val modelId: String,
    val title: String,
    val description: String,
    val info: String
)
