package com.vibecheck.validator

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ContentValidator(
    private val expectedDayCount: Int = 90,
    private val expectedStartDate: LocalDate? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun validateDirectory(contentDirectory: Path): ValidationResult {
        val files = Files.list(contentDirectory).use { stream ->
            val collected = mutableListOf<Path>()
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.extension == "json" }
                .sorted()
                .forEach { collected.add(it) }
            collected
        }

        val errors = mutableListOf<String>()
        if (files.size != expectedDayCount) {
            errors += "Expected $expectedDayCount puzzle files, found ${files.size}."
        }

        val seenDates = mutableSetOf<String>()
        val parsedDates = mutableSetOf<LocalDate>()
        files.forEach { file ->
            val fileErrors = validateFile(file, seenDates, parsedDates)
            errors += fileErrors
        }

        if (expectedStartDate != null) {
            errors += validateExpectedDateWindow(parsedDates)
        }

        return ValidationResult(errors)
    }

    private fun validateFile(
        file: Path,
        seenDates: MutableSet<String>,
        parsedDates: MutableSet<LocalDate>
    ): List<String> {
        val errors = mutableListOf<String>()
        val payload = runCatching { file.inputStream().bufferedReader().readText() }
            .getOrElse {
                return listOf("${file.name}: unable to read file: ${it.message}")
            }

        val dayPuzzle = runCatching { json.decodeFromString<DayPuzzleFile>(payload) }
            .getOrElse {
                return listOf("${file.name}: invalid JSON schema: ${it.message}")
            }

        if (!WordRules.isNormalizedEnglishWord(dayPuzzle.answer)) {
            errors += "${file.name}: answer must be lowercase English letters and trimmed."
        }

        if (dayPuzzle.models.isEmpty()) {
            errors += "${file.name}: models must contain at least one model."
        }

        if (dayPuzzle.utcDate in seenDates) {
            errors += "${file.name}: duplicate utcDate ${dayPuzzle.utcDate}."
        } else {
            seenDates += dayPuzzle.utcDate
        }

        if (file.name != "${dayPuzzle.utcDate}.json") {
            errors += "${file.name}: file name must match utcDate (${dayPuzzle.utcDate}.json)."
        }

        val parsedDate = runCatching { LocalDate.parse(dayPuzzle.utcDate) }.getOrNull()
        if (parsedDate == null) {
            errors += "${file.name}: utcDate must be a valid ISO date (YYYY-MM-DD)."
        } else {
            parsedDates += parsedDate
        }

        val normalizedAnswer = WordRules.normalize(dayPuzzle.answer)
        val duplicateModelIds = dayPuzzle.models.groupBy { it.modelId.trim() }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
            .keys
        duplicateModelIds.forEach { modelId ->
            errors += "${file.name}: duplicate modelId '$modelId'."
        }

        dayPuzzle.models.forEachIndexed { index, model ->
            val modelPrefix = "${file.name}: models[$index]"
            if (model.modelId.isBlank()) {
                errors += "$modelPrefix modelId must be non-empty."
            }
            if (model.displayName.isBlank()) {
                errors += "$modelPrefix displayName must be non-empty."
            }
            if (model.rankedWords.isEmpty()) {
                errors += "$modelPrefix rankedWords must be non-empty."
                return@forEachIndexed
            }

            model.rankedWords.forEachIndexed { wordIndex, word ->
                if (!WordRules.isNormalizedEnglishWord(word)) {
                    errors += "$modelPrefix rankedWords[$wordIndex] must be lowercase English letters and trimmed."
                }
            }

            if (model.rankedWords.firstOrNull()?.let(WordRules::normalize) != normalizedAnswer) {
                errors += "$modelPrefix rankedWords[0] must equal answer '$normalizedAnswer'."
            }

            val normalizedWords = model.rankedWords.map(WordRules::normalize)
            if (normalizedWords.distinct().size != normalizedWords.size) {
                errors += "$modelPrefix rankedWords must not contain duplicates."
            }
        }

        return errors
    }

    private fun validateExpectedDateWindow(parsedDates: Set<LocalDate>): List<String> {
        val startDate = expectedStartDate ?: return emptyList()
        val expectedDates = (0 until expectedDayCount)
            .map { startDate.plusDays(it.toLong()) }
            .toSet()

        val errors = mutableListOf<String>()

        val missing = expectedDates.minus(parsedDates).toList().sorted()
        if (missing.isNotEmpty()) {
            errors += "Missing puzzle dates in expected window from $startDate: ${summarizeDates(missing)}."
        }

        val unexpected = parsedDates.minus(expectedDates).toList().sorted()
        if (unexpected.isNotEmpty()) {
            errors += "Found puzzle dates outside expected window from $startDate: ${summarizeDates(unexpected)}."
        }

        return errors
    }

    private fun summarizeDates(dates: List<LocalDate>): String {
        val preview = dates.take(5).joinToString(", ")
        return if (dates.size > 5) "$preview ... (${dates.size} total)" else preview
    }
}

data class ValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

@Serializable
data class DayPuzzleFile(
    val utcDate: String,
    val answer: String,
    val models: List<ModelPuzzleFile>
)

@Serializable
data class ModelPuzzleFile(
    val modelId: String,
    val displayName: String,
    val rankedWords: List<String>
)

private object WordRules {
    private val regex = Regex("^[a-z]+(?:'[a-z]+)?$")

    fun normalize(word: String): String = word.trim().lowercase()

    fun isNormalizedEnglishWord(word: String): Boolean = word == normalize(word) && regex.matches(word)
}
