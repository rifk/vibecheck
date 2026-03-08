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
    private val canonicalWordListPath: Path? = null,
    private val modelCatalogPath: Path? = null,
    private val canonicalWords: Set<String>? = null,
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

        val canonicalLookup = loadCanonicalWords()
        if (canonicalLookup.isEmpty()) {
            errors += "Canonical lexicon is empty or missing. Provide a valid common words file."
        }
        val modelCatalog = loadModelCatalog()
        errors += modelCatalog.errors

        val seenDates = mutableSetOf<String>()
        val parsedDates = mutableSetOf<LocalDate>()
        files.forEach { file ->
            val fileErrors = validateFile(
                file = file,
                seenDates = seenDates,
                parsedDates = parsedDates,
                canonicalLookup = canonicalLookup,
                validModelIds = modelCatalog.models.takeIf { modelCatalog.errors.isEmpty() }?.keys
            )
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
        parsedDates: MutableSet<LocalDate>,
        canonicalLookup: Set<String>,
        validModelIds: Set<String>?
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
            errors += "${file.name}: answer must be lowercase alphabetic (a-z), min 2 chars, and trimmed."
        } else if (dayPuzzle.answer !in canonicalLookup) {
            errors += "${file.name}: answer '${dayPuzzle.answer}' must exist in canonical lexicon."
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
            } else if (validModelIds != null && model.modelId.trim() !in validModelIds) {
                errors += "$modelPrefix modelId '${model.modelId.trim()}' must exist in model_info.json."
            }
            if (model.rankedWords.isEmpty()) {
                errors += "$modelPrefix rankedWords must be non-empty."
                return@forEachIndexed
            }

            model.rankedWords.forEachIndexed { wordIndex, word ->
                if (!WordRules.isNormalizedEnglishWord(word)) {
                    errors += "$modelPrefix rankedWords[$wordIndex] must be lowercase alphabetic (a-z), min 2 chars, and trimmed."
                } else if (word !in canonicalLookup) {
                    errors += "$modelPrefix rankedWords[$wordIndex] '$word' must exist in canonical lexicon."
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

    private fun loadCanonicalWords(): Set<String> {
        val direct = canonicalWords
        if (direct != null) return direct

        val path = canonicalWordListPath
            ?: return emptySet()
        if (!Files.exists(path)) return emptySet()

        return runCatching {
            path.inputStream().bufferedReader().useLines { lines ->
                lines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && WordRules.isNormalizedEnglishWord(it) }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    private fun loadModelCatalog(): ModelCatalogValidationResult {
        val path = modelCatalogPath ?: return ModelCatalogValidationResult(
            models = emptyMap(),
            errors = listOf("Model catalog is empty or missing. Provide a valid model_info.json file.")
        )
        if (!Files.exists(path)) {
            return ModelCatalogValidationResult(
                models = emptyMap(),
                errors = listOf("Model catalog is empty or missing. Provide a valid model_info.json file.")
            )
        }

        val payload = runCatching { path.inputStream().bufferedReader().readText() }
            .getOrElse {
                return ModelCatalogValidationResult(
                    models = emptyMap(),
                    errors = listOf("Unable to read model catalog: ${it.message}")
                )
            }

        val catalog = runCatching { json.decodeFromString<ModelCatalogFile>(payload) }
            .getOrElse {
                return ModelCatalogValidationResult(
                    models = emptyMap(),
                    errors = listOf("Model catalog has invalid JSON schema: ${it.message}")
                )
            }

        if (catalog.models.isEmpty()) {
            return ModelCatalogValidationResult(
                models = emptyMap(),
                errors = listOf("Model catalog must contain at least one model.")
            )
        }

        val errors = mutableListOf<String>()
        val normalized = catalog.models.mapIndexedNotNull { index, model ->
            val prefix = "model_info.json: models[$index]"
            val modelId = model.modelId.trim()
            val title = model.title.trim()
            val description = model.description.trim()
            val info = model.info.trim()

            if (modelId.isBlank()) {
                errors += "$prefix modelId must be non-empty."
                return@mapIndexedNotNull null
            }
            if (title.isBlank()) {
                errors += "$prefix title must be non-empty."
            }
            if (description.isBlank()) {
                errors += "$prefix description must be non-empty."
            }
            if (info.isBlank()) {
                errors += "$prefix info must be non-empty."
            }

            ValidModelCatalogEntry(
                modelId = modelId,
                title = title,
                description = description,
                info = info
            )
        }

        normalized.groupBy { it.modelId }
            .filterValues { it.size > 1 }
            .keys
            .forEach { duplicateId ->
                errors += "model_info.json: duplicate modelId '$duplicateId'."
            }

        return ModelCatalogValidationResult(
            models = normalized.associateBy { it.modelId },
            errors = errors
        )
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
    val rankedWords: List<String>
)

@Serializable
data class ModelCatalogFile(
    val models: List<ModelCatalogEntryFile>
)

@Serializable
data class ModelCatalogEntryFile(
    val modelId: String,
    val title: String,
    val description: String,
    val info: String
)

data class ModelCatalogValidationResult(
    val models: Map<String, ValidModelCatalogEntry>,
    val errors: List<String>
)

data class ValidModelCatalogEntry(
    val modelId: String,
    val title: String,
    val description: String,
    val info: String
)

private object WordRules {
    private val regex = Regex("^[a-z]{2,}$")

    fun normalize(word: String): String = word.trim().lowercase()

    fun isNormalizedEnglishWord(word: String): Boolean = word == normalize(word) && regex.matches(word)
}
