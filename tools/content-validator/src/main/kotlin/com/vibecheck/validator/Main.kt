package com.vibecheck.validator

import java.nio.file.Path
import java.time.LocalDate
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val directory = args.getOrNull(0) ?: "content/puzzles"
    val expectedStartDate = args.getOrNull(1)?.let(LocalDate::parse)
    val expectedDayCount = args.getOrNull(2)?.toIntOrNull() ?: 90
    val canonicalWordList = args.getOrNull(3) ?: "content/lexicon/common_words_20k.txt"
    val modelCatalog = args.getOrNull(4) ?: "content/models/model_info.json"
    val validator = ContentValidator(
        expectedDayCount = expectedDayCount,
        expectedStartDate = expectedStartDate,
        canonicalWordListPath = Path.of(canonicalWordList),
        modelCatalogPath = Path.of(modelCatalog)
    )
    val result = validator.validateDirectory(Path.of(directory))

    if (!result.isValid) {
        println("Validation failed with ${result.errors.size} issue(s):")
        result.errors.forEach { println("- $it") }
        exitProcess(1)
    }

    println("Validation succeeded: content directory is valid.")
}
