package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import kotlinx.datetime.LocalDate
import vibe_check.composeapp.generated.resources.Res

class BundledPuzzleSource(
    private val parser: PuzzleJsonParser = PuzzleJsonParser()
) : PuzzleSource {

    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        val payload = readPuzzleJsonForDate(utcDate) ?: return null
        return parser.parse(payload)
    }

    private suspend fun readPuzzleJsonForDate(utcDate: LocalDate): String? {
        val resourcePath = "files/puzzles/${utcDate}.json"
        val bytes = runCatching { Res.readBytes(resourcePath) }.getOrNull() ?: return null
        return bytes.decodeToString()
    }
}
