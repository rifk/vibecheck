package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PuzzleSourceFactoryTest {
    @Test
    fun bundledMode_usesBundledProvider() {
        val factory = PuzzleSourceFactory(
            bundledProvider = { MarkerSource("bundled") },
            remoteProvider = { MarkerSource("remote:$it") }
        )

        val source = factory.create(SourceConfig(mode = SourceMode.BUNDLED))

        assertIs<MarkerSource>(source)
        assertEquals("bundled", source.marker)
    }

    @Test
    fun remoteMode_withoutRemoteProvider_fallsBackToBundled() {
        val factory = PuzzleSourceFactory(
            bundledProvider = { MarkerSource("bundled") },
            remoteProvider = null
        )

        val source = factory.create(
            SourceConfig(
                mode = SourceMode.REMOTE,
                remoteBaseUrl = "https://example.com"
            )
        )

        assertIs<MarkerSource>(source)
        assertEquals("bundled", source.marker)
    }

    @Test
    fun remoteMode_withBaseUrlAndProvider_usesRemote() {
        val factory = PuzzleSourceFactory(
            bundledProvider = { MarkerSource("bundled") },
            remoteProvider = { baseUrl -> MarkerSource("remote:$baseUrl") }
        )

        val source = factory.create(
            SourceConfig(
                mode = SourceMode.REMOTE,
                remoteBaseUrl = "https://api.vibecheck.example"
            )
        )

        assertIs<MarkerSource>(source)
        assertEquals("remote:https://api.vibecheck.example", source.marker)
    }

    @Test
    fun withTemplateClient_createsRemoteSourceWhenConfigured() {
        val factory = PuzzleSourceFactory.withTemplateClient(httpGet = { null })

        val source = factory.create(
            SourceConfig(
                mode = SourceMode.REMOTE,
                remoteBaseUrl = "https://api.vibecheck.example"
            )
        )

        assertIs<RemotePuzzleSource>(source)
    }
}

private class MarkerSource(
    val marker: String
) : PuzzleSource {
    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? = null
}
