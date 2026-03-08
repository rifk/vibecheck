package com.vibecheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PuzzleJsonParserTest {
    private val parser = PuzzleJsonParser()

    @Test
    fun parse_validPayloadWithVariableModelCount_returnsPuzzle() {
        val payload =
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {
                  "modelId": "solo",
                  "rankedWords": ["serenity", "calm", "peace"]
                },
                {
                  "modelId": "alt",
                  "rankedWords": ["serenity", "quiet", "still"]
                }
              ]
            }
            """.trimIndent()

        val parsed = parser.parse(payload)

        assertNotNull(parsed)
        assertEquals("2026-01-01", parsed.utcDate)
        assertEquals("serenity", parsed.answer)
        assertEquals(2, parsed.models.size)
    }

    @Test
    fun parse_emptyModels_returnsNull() {
        val payload =
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": []
            }
            """.trimIndent()

        val parsed = parser.parse(payload)

        assertNull(parsed)
    }

    @Test
    fun parse_modelWithoutAnswerAtTop_returnsNull() {
        val payload =
            """
            {
              "utcDate": "2026-01-01",
              "answer": "serenity",
              "models": [
                {
                  "modelId": "m1",
                  "rankedWords": ["calm", "serenity"]
                }
              ]
            }
            """.trimIndent()

        val parsed = parser.parse(payload)

        assertNull(parsed)
    }
}
