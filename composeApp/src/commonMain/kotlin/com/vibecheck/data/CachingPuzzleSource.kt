package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import kotlinx.datetime.LocalDate

class CachingPuzzleSource(
    private val delegate: PuzzleSource
) : PuzzleSource {
    private val cache = mutableMapOf<String, DayPuzzle?>()

    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        val key = utcDate.toString()
        if (cache.containsKey(key)) {
            return cache[key]
        }

        val puzzle = delegate.getPuzzle(utcDate)
        cache[key] = puzzle
        return puzzle
    }
}
