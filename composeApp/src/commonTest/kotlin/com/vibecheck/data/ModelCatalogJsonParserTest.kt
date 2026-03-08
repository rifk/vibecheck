package com.vibecheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelCatalogJsonParserTest {
    private val parser = ModelCatalogJsonParser()

    @Test
    fun parse_validCatalog_returnsMetadataById() {
        val payload =
            """
            {
              "models": [
                {
                  "modelId": "m1",
                  "title": "Atlas",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                },
                {
                  "modelId": "m2",
                  "title": "Signal",
                  "description": "Fast pattern matcher",
                  "info": "Prefers crisp word neighborhoods and short jumps."
                }
              ]
            }
            """.trimIndent()

        val parsed = parser.parse(payload)

        assertNotNull(parsed)
        assertEquals(2, parsed.size)
        assertEquals("Atlas", parsed.getValue("m1").title)
        assertEquals("Fast pattern matcher", parsed.getValue("m2").description)
    }

    @Test
    fun parse_duplicateModelIds_returnsNull() {
        val payload =
            """
            {
              "models": [
                {
                  "modelId": "m1",
                  "title": "Atlas",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                },
                {
                  "modelId": "m1",
                  "title": "Echo",
                  "description": "Duplicate id",
                  "info": "Should be rejected."
                }
              ]
            }
            """.trimIndent()

        assertNull(parser.parse(payload))
    }

    @Test
    fun parse_blankFields_returnsNull() {
        val payload =
            """
            {
              "models": [
                {
                  "modelId": "m1",
                  "title": " ",
                  "description": "Steady navigator",
                  "info": "Maps broad semantic terrain before narrowing in."
                }
              ]
            }
            """.trimIndent()

        assertNull(parser.parse(payload))
    }
}
