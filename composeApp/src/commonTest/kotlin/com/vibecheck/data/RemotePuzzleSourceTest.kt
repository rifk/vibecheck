package com.vibecheck.data

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemotePuzzleSourceTest {
    @Test
    fun returnsParsedPuzzle_whenClientReturnsValidJson() = runTest {
        val client = FakeRemotePuzzleClient(
            payloadByDate = mapOf(
                "2026-01-02" to
                    """
                    {
                      "utcDate": "2026-01-02",
                      "answer": "signal",
                      "models": [
                        {
                          "modelId": "m1",
                          "displayName": "Model 1",
                          "rankedWords": ["signal", "noise", "tone"]
                        }
                      ]
                    }
                    """.trimIndent()
            )
        )

        val source = RemotePuzzleSource(
            baseUrl = "https://api.example",
            remoteClient = client
        )

        val puzzle = source.getPuzzle(LocalDate.parse("2026-01-02"))

        assertEquals("https://api.example", client.lastBaseUrl)
        assertEquals("2026-01-02", client.lastDate)
        assertNotNull(puzzle)
        assertEquals("signal", puzzle.answer)
    }

    @Test
    fun returnsNull_whenClientReturnsNull() = runTest {
        val client = FakeRemotePuzzleClient(emptyMap())
        val source = RemotePuzzleSource(
            baseUrl = "https://api.example",
            remoteClient = client
        )

        val puzzle = source.getPuzzle(LocalDate.parse("2026-01-03"))

        assertNull(puzzle)
    }
}

private class FakeRemotePuzzleClient(
    private val payloadByDate: Map<String, String>
) : RemotePuzzleClient {
    var lastBaseUrl: String? = null
    var lastDate: String? = null

    override suspend fun fetchPuzzleJson(baseUrl: String, utcDate: LocalDate): String? {
        lastBaseUrl = baseUrl
        lastDate = utcDate.toString()
        return payloadByDate[utcDate.toString()]
    }
}
