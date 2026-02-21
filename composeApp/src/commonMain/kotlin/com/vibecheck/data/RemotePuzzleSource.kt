package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import kotlinx.datetime.LocalDate

interface RemotePuzzleClient {
    suspend fun fetchPuzzleJson(baseUrl: String, utcDate: LocalDate): String?
}

class RemotePuzzleSource(
    private val baseUrl: String,
    private val remoteClient: RemotePuzzleClient,
    private val parser: PuzzleJsonParser = PuzzleJsonParser()
) : PuzzleSource {
    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        val payload = remoteClient.fetchPuzzleJson(baseUrl, utcDate) ?: return null
        return parser.parse(payload)
    }
}
