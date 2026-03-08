package com.vibecheck.validator

import java.nio.file.Files
import java.nio.file.Path
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
        writeModelCatalog(
            dir,
            """
            {
              "models": [
                {
                  "modelId": "solo",
                  "title": "Solo",
                  "description": "One lane",
                  "info": "Runs a single semantic lane."
                }
              ]
            }
            """.trimIndent()
        )
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": []
            }
            """.trimIndent()
        )

        val result = validator(dir).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("models must contain at least one model") })
    }

    @Test
    fun variableModelCounts_areAccepted() {
        val dir = createTempPuzzleDir()
        writeDefaultModelCatalog(dir)

        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "signal",
              "models": [
                {
                  "modelId": "solo",
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
                {"modelId": "a", "rankedWords": ["anchor", "chain"]},
                {"modelId": "b", "rankedWords": ["anchor", "dock"]},
                {"modelId": "c", "rankedWords": ["anchor", "port"]},
                {"modelId": "d", "rankedWords": ["anchor", "boat"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir, expectedDayCount = 2).validateDirectory(dir)

        assertTrue(result.isValid)
    }

    @Test
    fun requiresExpectedNumberOfFiles() {
        val dir = createTempPuzzleDir()
        writeDefaultModelCatalog(dir)
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir, expectedDayCount = 90).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Expected 90 puzzle files") })
    }

    @Test
    fun rejectsWordsOutsideCanonicalLexicon() {
        val dir = createTempPuzzleDir()
        writeDefaultModelCatalog(dir)
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "rankedWords": ["serenity", "foobar"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("must exist in canonical lexicon") })
    }

    @Test
    fun rejectsPuzzleModelIdMissingFromCatalog() {
        val dir = createTempPuzzleDir()
        writeModelCatalog(
            dir,
            """
            {
              "models": [
                {
                  "modelId": "a",
                  "title": "Atlas",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                }
              ]
            }
            """.trimIndent()
        )
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "missing", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("must exist in model_info.json") })
    }

    @Test
    fun rejectsDuplicateMetadataIds() {
        val dir = createTempPuzzleDir()
        writeModelCatalog(
            dir,
            """
            {
              "models": [
                {
                  "modelId": "a",
                  "title": "Atlas",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                },
                {
                  "modelId": "a",
                  "title": "Echo",
                  "description": "Duplicate",
                  "info": "Should fail."
                }
              ]
            }
            """.trimIndent()
        )
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("duplicate modelId 'a'") })
    }

    @Test
    fun rejectsBlankMetadataFields() {
        val dir = createTempPuzzleDir()
        writeModelCatalog(
            dir,
            """
            {
              "models": [
                {
                  "modelId": "a",
                  "title": " ",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                }
              ]
            }
            """.trimIndent()
        )
        dir.resolve("2026-01-01.json").writeText(
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {"modelId": "a", "rankedWords": ["serenity", "calm"]}
              ]
            }
            """.trimIndent()
        )

        val result = validator(dir).validateDirectory(dir)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("title must be non-empty") })
    }

    private fun validator(dir: Path, expectedDayCount: Int = 1): ContentValidator {
        return ContentValidator(
            expectedDayCount = expectedDayCount,
            canonicalWords = canonicalWords,
            modelCatalogPath = dir.resolveSibling("model_info.json")
        )
    }

    private fun writeDefaultModelCatalog(dir: Path) {
        writeModelCatalog(
            dir,
            """
            {
              "models": [
                {
                  "modelId": "solo",
                  "title": "Solo",
                  "description": "One lane",
                  "info": "Runs a single semantic lane."
                },
                {
                  "modelId": "a",
                  "title": "Atlas",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                },
                {
                  "modelId": "b",
                  "title": "Beacon",
                  "description": "Signal chaser",
                  "info": "Favors sharp local similarities."
                },
                {
                  "modelId": "c",
                  "title": "Cascade",
                  "description": "Layered reasoner",
                  "info": "Builds context through linked neighbors."
                },
                {
                  "modelId": "d",
                  "title": "Drift",
                  "description": "Wide explorer",
                  "info": "Takes longer semantic jumps."
                }
              ]
            }
            """.trimIndent()
        )
    }

    private fun writeModelCatalog(dir: Path, payload: String) {
        dir.resolveSibling("model_info.json").writeText(payload)
    }

    private fun createTempPuzzleDir(): Path {
        val root = createTempDirectory(prefix = "vibe-check-validator-")
        val puzzles = root.resolve("puzzles")
        Files.createDirectories(puzzles)
        return puzzles
    }
}
