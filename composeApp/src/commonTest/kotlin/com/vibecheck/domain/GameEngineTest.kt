package com.vibecheck.domain

import com.vibecheck.model.DayPlayState
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.GuessOutcome
import com.vibecheck.model.ModelPuzzle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun singleModelDay_canBePlayedAndSolved() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-01",
            answer = "serenity",
            models = listOf(
                ModelPuzzle(
                    modelId = "solo",
                    displayName = "Solo Model",
                    rankedWords = listOf("serenity", "calm", "peace", "quiet")
                )
            )
        )

        val initial = GameEngine.initialDayState(puzzle, priorState = null)
        val guessResult = GameEngine.submitGuess(initial, puzzle, "calm")

        val accepted = assertIs<GuessSubmissionResult.Accepted>(guessResult)
        assertEquals(2, accepted.outcome.rank)
        assertTrue(!accepted.updatedState.solved)

        val solveResult = GameEngine.submitGuess(accepted.updatedState, puzzle, "serenity")
        val solved = assertIs<GuessSubmissionResult.Accepted>(solveResult)
        assertTrue(solved.solvedNow)
        assertEquals("solo", solved.updatedState.solvedByModelId)
    }

    @Test
    fun solvingUpdatesStreakAndAverages() {
        val initial = com.vibecheck.model.PlayerStats()
        val dayOne = LocalDate.parse("2026-01-05")
        val dayTwo = LocalDate.parse("2026-01-06")

        val statsAfterFirst = GameEngine.updateStatsOnSolve(initial, dayOne, "model_a", 4)
        val statsAfterSecond = GameEngine.updateStatsOnSolve(statsAfterFirst, dayTwo, "model_a", 2)

        assertEquals(2, statsAfterSecond.totalWins)
        assertEquals(2, statsAfterSecond.currentStreak)
        assertEquals(2, statsAfterSecond.maxStreak)
        assertEquals(2, statsAfterSecond.winsByModel["model_a"])
        assertEquals(3.0, statsAfterSecond.averageGuessesByModel()["model_a"])
        assertEquals(2, statsAfterSecond.solveHistoryByDate.size)
        assertEquals("model_a", statsAfterSecond.solveHistoryByDate["2026-01-06"]?.modelId)
        assertEquals(2, statsAfterSecond.solveHistoryByDate["2026-01-06"]?.guessesToSolve)
    }

    @Test
    fun solvingSameDateTwice_doesNotMutateStatsTwice() {
        val initial = com.vibecheck.model.PlayerStats()
        val day = LocalDate.parse("2026-01-07")

        val first = GameEngine.updateStatsOnSolve(initial, day, "model_a", 3)
        val second = GameEngine.updateStatsOnSolve(first, day, "model_a", 1)

        assertEquals(first, second)
    }

    @Test
    fun separateModelHistories_arePreservedBeforeSolve() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-10",
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", "M1", listOf("signal", "noise", "tone")),
                ModelPuzzle("m2", "M2", listOf("signal", "wave", "beam"))
            )
        )

        var state = DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = "m1",
            solved = false,
            solvedByModelId = null,
            guessesByModel = emptyMap()
        )

        val m1Guess = GameEngine.submitGuess(state, puzzle, "noise") as GuessSubmissionResult.Accepted
        state = m1Guess.updatedState

        state = GameEngine.selectModel(state, puzzle, "m2")
        val m2Guess = GameEngine.submitGuess(state, puzzle, "wave") as GuessSubmissionResult.Accepted

        assertEquals(1, m2Guess.updatedState.guessesByModel["m1"]?.size)
        assertEquals(1, m2Guess.updatedState.guessesByModel["m2"]?.size)
    }

    @Test
    fun initialState_reconcilesWhenPriorSelectedModelIsMissing() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-30",
            answer = "signal",
            models = listOf(
                ModelPuzzle("newA", "New A", listOf("signal", "noise")),
                ModelPuzzle("newB", "New B", listOf("signal", "tone"))
            )
        )
        val prior = DayPlayState(
            utcDate = "2026-01-30",
            selectedModelId = "oldModel",
            solved = false,
            solvedByModelId = null,
            guessesByModel = mapOf(
                "oldModel" to listOf(GuessOutcome("signal", 1)),
                "newA" to listOf(GuessOutcome("noise", 2))
            )
        )

        val state = GameEngine.initialDayState(puzzle, prior)

        assertEquals("newA", state.selectedModelId)
        assertEquals(setOf("newA"), state.guessesByModel.keys)
    }

    @Test
    fun initialState_restoresSolvedFromRankOneGuessIfSolvedModelMissing() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-31",
            answer = "harbor",
            models = listOf(
                ModelPuzzle("m1", "M1", listOf("harbor", "port")),
                ModelPuzzle("m2", "M2", listOf("harbor", "dock"))
            )
        )
        val prior = DayPlayState(
            utcDate = "2026-01-31",
            selectedModelId = "m1",
            solved = true,
            solvedByModelId = "removed-model",
            guessesByModel = mapOf(
                "m2" to listOf(GuessOutcome("harbor", 1))
            )
        )

        val state = GameEngine.initialDayState(puzzle, prior)

        assertTrue(state.solved)
        assertEquals("m2", state.solvedByModelId)
        assertEquals("m2", state.selectedModelId)
    }
}
