package com.vibecheck.data

import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.ModelPuzzle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CachingPuzzleSourceTest {
    @Test
    fun repeatedDate_usesDelegateOnceWhenPuzzleExists() = runTest {
        val date = LocalDate.parse("2026-01-01")
        val puzzle = DayPuzzle(
            utcDate = "2026-01-01",
            answer = "serenity",
            models = listOf(
                ModelPuzzle("m1", listOf("serenity", "calm"))
            )
        )
        val delegate = CountingPuzzleSource(mapOf(date to puzzle))
        val source = CachingPuzzleSource(delegate)

        val first = source.getPuzzle(date)
        val second = source.getPuzzle(date)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1, delegate.callCountByDate[date.toString()])
    }

    @Test
    fun repeatedDate_usesDelegateOnceWhenPuzzleMissing() = runTest {
        val date = LocalDate.parse("2026-01-02")
        val delegate = CountingPuzzleSource(emptyMap())
        val source = CachingPuzzleSource(delegate)

        val first = source.getPuzzle(date)
        val second = source.getPuzzle(date)

        assertNull(first)
        assertNull(second)
        assertEquals(1, delegate.callCountByDate[date.toString()])
    }
}

private class CountingPuzzleSource(
    private val data: Map<LocalDate, DayPuzzle>
) : PuzzleSource {
    val callCountByDate: MutableMap<String, Int> = mutableMapOf()

    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        val key = utcDate.toString()
        callCountByDate[key] = (callCountByDate[key] ?: 0) + 1
        return data[utcDate]
    }
}
