package com.vibecheck.validator

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentValidatorTest {
    private val canonicalWords = setOf(
        "serenity",
        "signal",
        "noise",
        "tone",
        "anchor",
        "chain",
        "dock",
        "port",
        "boat",
        "calm"
    )

    @Test
    fun emptyModels_areRejected() {
        val dir = createTempPuzzleDir()
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": []
            }
            """.trimIndent()
        )

        val result = ContentValidator(
            expectedDayCount = 1,
            canonicalWords = canonicalWords
        ).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("models must contain at least one model") })
    }

    @Test
    fun variableModelCounts_areAccepted() {
        val dir = createTempPuzzleDir()

        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "signal",
              "models": [
                {
                  "modelId": "solo",
                  "displayName": "Solo",
                  "rankedWords": ["signal", "noise", "tone"]
                }
              ]
            }
            """.trimIndent()
        )

        dir.resolve("2026-01-02.json").writeText(
            """
            {
              "utcDate": "2026-01-02",
              "answer": "anchor",
              "models": [
                {"modelId": "a", "displayName": "A", "rankedWords": ["anchor", "chain"]},
                {"modelId": "b", "displayName": "B", "rankedWords": ["anchor", "dock"]},
                {"modelId": "c", "displayName": "C", "rankedWords": ["anchor", "port"]},
                {"modelId": "d", "displayName": "D", "rankedWords": ["anchor", "boat"]}
              ]
            }
            """.trimIndent()
        )

        val result = ContentValidator(
            expectedDayCount = 2,
            expectedStartDate = LocalDate.parse("2026-01-01"),
            canonicalWords = canonicalWords
        ).validateDirectory(dir)

        assertTrue(result.isValid)
    }

    @Test
    fun requiresExpectedNumberOfFiles() {
        val dir = createTempPuzzleDir()
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "displayName": "A", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        val result = ContentValidator(
            expectedDayCount = 90,
            canonicalWords = canonicalWords
        ).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Expected 90 puzzle files") })
    }

    @Test
    fun rejectsNonContiguousDateWindow() {
        val dir = createTempPuzzleDir()

        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "displayName": "A", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        dir.resolve("2026-01-03.json").writeText(
            """
            {
              "utcDate": "2026-01-03",
              "answer": "signal",
              "models": [
                {"modelId": "b", "displayName": "B", "rankedWords": ["signal", "noise"]}
              ]
            }
            """.trimIndent()
        )

        val result = ContentValidator(
            expectedDayCount = 2,
            expectedStartDate = LocalDate.parse("2026-01-01"),
            canonicalWords = canonicalWords
        ).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Missing puzzle dates in expected window") })
        assertTrue(result.errors.any { it.contains("outside expected window") })
    }

    @Test
    fun rejectsWordsOutsideCanonicalLexicon() {
        val dir = createTempPuzzleDir()
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "displayName": "A", "rankedWords": ["serenity", "foobar"]}
              ]
            }
            """.trimIndent()
        )

        val result = ContentValidator(
            expectedDayCount = 1,
            canonicalWords = canonicalWords
        ).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("must exist in canonical lexicon") })
    }

    private fun createTempPuzzleDir(): Path {
        val root = createTempDirectory(prefix = "vibe-check-validator-")
        Files.createDirectories(root)
        return root
    }
}
