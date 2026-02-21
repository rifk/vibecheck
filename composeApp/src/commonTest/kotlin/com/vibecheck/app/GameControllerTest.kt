package com.vibecheck.app

import com.vibecheck.data.GameStateStore
import com.vibecheck.data.PuzzleSource
import com.vibecheck.domain.UtcDateProvider
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.ModelPuzzle
import com.vibecheck.model.PersistedAppState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameControllerTest {
    @Test
    fun dayWithFourModels_rendersAllAvailableModels() = runTest {
        val date = LocalDate.parse("2026-01-01")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "serenity",
            models = listOf(
                ModelPuzzle("m1", "Model 1", listOf("serenity", "calm")),
                ModelPuzzle("m2", "Model 2", listOf("serenity", "peace")),
                ModelPuzzle("m3", "Model 3", listOf("serenity", "quiet")),
                ModelPuzzle("m4", "Model 4", listOf("serenity", "still"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()

        assertEquals(4, controller.uiState.availableModels.size)
        assertEquals("m1", controller.uiState.selectedModelId)
    }

    @Test
    fun solveLocksOtherModels_forThatDay() = runTest {
        val date = LocalDate.parse("2026-01-02")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", "Model 1", listOf("signal", "noise")),
                ModelPuzzle("m2", "Model 2", listOf("signal", "tone"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()
        controller.onGuessChanged("signal")
        controller.submitGuess()

        assertTrue(controller.uiState.solved)
        controller.onModelSelected("m2")
        assertEquals("m1", controller.uiState.selectedModelId)
    }

    @Test
    fun historiesRemainSeparate_beforeSolve() = runTest {
        val date = LocalDate.parse("2026-01-03")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "anchor",
            models = listOf(
                ModelPuzzle("m1", "Model 1", listOf("anchor", "chain", "ship")),
                ModelPuzzle("m2", "Model 2", listOf("anchor", "dock", "port"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()
        controller.onGuessChanged("chain")
        controller.submitGuess()

        controller.onModelSelected("m2")
        controller.onGuessChanged("dock")
        controller.submitGuess()

        assertFalse(controller.uiState.solved)
        controller.onModelSelected("m1")
        assertEquals(1, controller.uiState.guesses.size)
    }
}

private class FakePuzzleSource(
    private val puzzles: Map<LocalDate, DayPuzzle>
) : PuzzleSource {
    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? = puzzles[utcDate]
}

private class InMemoryStore : GameStateStore {
    private var state: PersistedAppState = PersistedAppState()

    override fun loadState(): PersistedAppState = state

    override fun saveState(state: PersistedAppState) {
        this.state = state
    }
}

private class FixedDateProvider(
    private val date: LocalDate
) : UtcDateProvider {
    override fun currentDate(): LocalDate = date
}
