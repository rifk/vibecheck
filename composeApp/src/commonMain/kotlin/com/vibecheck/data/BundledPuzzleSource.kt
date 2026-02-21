package com.vibecheck.data

import com.vibecheck.domain.WordRules
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.ModelPuzzle
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vibe_check.composeapp.generated.resources.Res

class BundledPuzzleSource(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PuzzleSource {

    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        val payload = readPuzzleJsonForDate(utcDate) ?: return null
        val dto = runCatching { json.decodeFromString<DayPuzzleDto>(payload) }.getOrNull() ?: return null

        return dto.toDomain()
    }

    private suspend fun readPuzzleJsonForDate(utcDate: LocalDate): String? {
        val resourcePath = "files/puzzles/${utcDate}.json"
        val bytes = runCatching { Res.readBytes(resourcePath) }.getOrNull() ?: return null
        return bytes.decodeToString()
    }
}

@Serializable
private data class DayPuzzleDto(
    val utcDate: String,
    val answer: String,
    val models: List<ModelPuzzleDto>
) {
    fun toDomain(): DayPuzzle? {
        if (models.isEmpty()) return null

        val normalizedAnswer = WordRules.normalize(answer)
        if (!WordRules.isValidEnglishWord(normalizedAnswer)) return null

        val modelPuzzles = models.mapNotNull { it.toDomain(normalizedAnswer) }
        if (modelPuzzles.isEmpty()) return null

        return DayPuzzle(
            utcDate = utcDate,
            answer = normalizedAnswer,
            models = modelPuzzles
        )
    }
}

@Serializable
private data class ModelPuzzleDto(
    val modelId: String,
    val displayName: String,
    val rankedWords: List<String>
) {
    fun toDomain(answer: String): ModelPuzzle? {
        if (modelId.isBlank() || displayName.isBlank() || rankedWords.isEmpty()) return null

        val normalizedWords = rankedWords.map(WordRules::normalize)
        if (normalizedWords.any { !WordRules.isValidEnglishWord(it) }) return null
        if (normalizedWords.distinct().size != normalizedWords.size) return null
        if (normalizedWords.first() != answer) return null

        return ModelPuzzle(
            modelId = modelId.trim(),
            displayName = displayName.trim(),
            rankedWords = normalizedWords
        )
    }
}
