package com.vibecheck.validator

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val directory = args.firstOrNull() ?: "content/puzzles"
    val validator = ContentValidator(expectedDayCount = 90)
    val result = validator.validateDirectory(Path.of(directory))

    if (!result.isValid) {
        println("Validation failed with ${result.errors.size} issue(s):")
        result.errors.forEach { println("- $it") }
        exitProcess(1)
    }

    println("Validation succeeded: content directory is valid.")
}
