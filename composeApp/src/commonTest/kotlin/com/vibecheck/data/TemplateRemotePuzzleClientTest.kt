package com.vibecheck.data

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateRemotePuzzleClientTest {
    @Test
    fun defaultTemplate_buildsPuzzleJsonPath() = runTest {
        var capturedUrl: String? = null
        val client = TemplateRemotePuzzleClient(
            httpGet = { url ->
                capturedUrl = url
                """{"utcDate":"2026-01-01"}"""
            }
        )

        val payload = client.fetchPuzzleJson(
            baseUrl = "https://api.vibecheck.example",
            utcDate = LocalDate.parse("2026-01-01")
        )

        assertEquals("https://api.vibecheck.example/puzzles/2026-01-01.json", capturedUrl)
        assertEquals("""{"utcDate":"2026-01-01"}""", payload)
    }

    @Test
    fun trimsSlashFromBaseUrl_andFromTemplatePath() = runTest {
        var capturedUrl: String? = null
        val client = TemplateRemotePuzzleClient(
            httpGet = { url ->
                capturedUrl = url
                null
            },
            pathTemplate = "/api/puzzles/{date}.json"
        )

        client.fetchPuzzleJson(
            baseUrl = "https://api.vibecheck.example/",
            utcDate = LocalDate.parse("2026-02-10")
        )

        assertEquals("https://api.vibecheck.example/api/puzzles/2026-02-10.json", capturedUrl)
    }

    @Test
    fun supportsCustomTemplate() = runTest {
        var capturedUrl: String? = null
        val client = TemplateRemotePuzzleClient(
            httpGet = { url ->
                capturedUrl = url
                null
            },
            pathTemplate = "v2/daily/{date}"
        )

        client.fetchPuzzleJson(
            baseUrl = "https://api.vibecheck.example",
            utcDate = LocalDate.parse("2026-03-05")
        )

        assertEquals("https://api.vibecheck.example/v2/daily/2026-03-05", capturedUrl)
    }
}
