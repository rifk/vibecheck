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
        assertTrue(solved.updatedState.solved)
    }

    @Test
    fun solvingUpdatesStreakAndAverages() {
        val initial = com.vibecheck.model.PlayerStats()
        val dayOne = LocalDate.parse("2026-01-05")
        val dayTwo = LocalDate.parse("2026-01-06")

        val statsAfterFirst = GameEngine.updateStatsOnSolve(initial, dayOne, 4)
        val statsAfterSecond = GameEngine.updateStatsOnSolve(statsAfterFirst, dayTwo, 2)

        assertEquals(2, statsAfterSecond.totalWins)
        assertEquals(2, statsAfterSecond.currentStreak)
        assertEquals(2, statsAfterSecond.maxStreak)
        assertEquals(2, statsAfterSecond.solveHistoryByDate.size)
        assertEquals(2, statsAfterSecond.solveHistoryByDate["2026-01-06"]?.guessesToSolve)
    }

    @Test
    fun solvingSameDateTwice_doesNotMutateStatsTwice() {
        val initial = com.vibecheck.model.PlayerStats()
        val day = LocalDate.parse("2026-01-07")

        val first = GameEngine.updateStatsOnSolve(initial, day, 3)
        val second = GameEngine.updateStatsOnSolve(first, day, 1)

        assertEquals(first, second)
    }

    @Test
    fun outOfOrderSolve_keepsLatestDateAndRecomputesCurrentStreak() {
        val initial = com.vibecheck.model.PlayerStats()
        val laterDay = LocalDate.parse("2026-01-07")
        val earlierDay = LocalDate.parse("2026-01-06")

        val afterLater = GameEngine.updateStatsOnSolve(initial, laterDay, 3)
        val afterEarlier = GameEngine.updateStatsOnSolve(afterLater, earlierDay, 4)

        assertEquals("2026-01-07", afterEarlier.lastSolvedDate)
        assertEquals(2, afterEarlier.currentStreak)
        assertEquals(2, afterEarlier.maxStreak)
    }

    @Test
    @Test
    fun fillingGapLater_extendsCurrentStreakFromLatestSolvedDate() {
        val initial = com.vibecheck.model.PlayerStats()
        val dayFive = LocalDate.parse("2026-01-05")
        val daySeven = LocalDate.parse("2026-01-07")
        val daySix = LocalDate.parse("2026-01-06")

        val afterSeven = GameEngine.updateStatsOnSolve(initial, daySeven, 2)
        val withGap = GameEngine.updateStatsOnSolve(afterSeven, dayFive, 3)
        val gapFilled = GameEngine.updateStatsOnSolve(withGap, daySix, 1)

        assertEquals("2026-01-07", gapFilled.lastSolvedDate)
        assertEquals(3, gapFilled.currentStreak)
        assertEquals(3, gapFilled.maxStreak)
    }

    @Test
    fun guessesApplyToAllModels() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-10",
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "noise", "tone")),
                ModelPuzzle("m2", listOf("signal", "wave", "beam"))
            )
        )

        var state = DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = "m1",
            solved = false,
            guessesByModel = emptyMap()
        )

        val m1Guess = GameEngine.submitGuess(state, puzzle, "signal") as GuessSubmissionResult.Accepted
        state = m1Guess.updatedState

        state = GameEngine.selectModel(state, puzzle, "m2")
        val m2Guess = GameEngine.submitGuess(state, puzzle, "signal")

        assertIs<GuessSubmissionResult.Rejected>(m2Guess)
        assertEquals(1, state.guessesByModel["m1"]?.size)
        assertEquals(1, state.guessesByModel["m2"]?.size)
    }

    @Test
    fun guessMissingFromAnyModel_isRejected() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-11",
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "noise")),
                ModelPuzzle("m2", listOf("signal", "tone"))
            )
        )

        val state = DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = "m1",
            solved = false,
            guessesByModel = emptyMap()
        )

        val result = GameEngine.submitGuess(state, puzzle, "noise")
        val rejected = assertIs<GuessSubmissionResult.Rejected>(result)
        assertEquals(GuessFailureReason.WORD_NOT_IN_ALL_MODELS, rejected.reason)
    }

    @Test
    fun initialState_reconcilesWhenPriorSelectedModelIsMissing() {
        val puzzle = DayPuzzle(
            utcDate = "2026-01-30",
            answer = "signal",
            models = listOf(
                ModelPuzzle("newA", listOf("signal", "noise")),
                ModelPuzzle("newB", listOf("signal", "tone"))
            )
        )
        val prior = DayPlayState(
            utcDate = "2026-01-30",
            selectedModelId = "oldModel",
            solved = false,
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
                ModelPuzzle("m1", listOf("harbor", "port")),
                ModelPuzzle("m2", listOf("harbor", "dock"))
            )
        )
        val prior = DayPlayState(
            utcDate = "2026-01-31",
            selectedModelId = "m1",
            solved = true,
            guessesByModel = mapOf(
                "m2" to listOf(GuessOutcome("harbor", 1))
            )
        )

        val state = GameEngine.initialDayState(puzzle, prior)

        assertTrue(state.solved)
        assertEquals("m1", state.selectedModelId)
    }

    @Test
    fun solvedDay_canSwitchToAnotherModelWithoutAffectingSolveState() {
        val puzzle = DayPuzzle(
            utcDate = "2026-02-01",
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "noise")),
                ModelPuzzle("m2", listOf("signal", "tone"))
            )
        )
        val solvedState = DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = "m1",
            solved = true,
            guessesByModel = mapOf(
                "m1" to listOf(GuessOutcome("signal", 1))
            )
        )

        val switched = GameEngine.selectModel(solvedState, puzzle, "m2")

        assertEquals("m2", switched.selectedModelId)
        assertTrue(switched.solved)
    }
}
