package com.vibecheck.domain

import com.vibecheck.model.DayPlayState
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.GuessOutcome
import com.vibecheck.model.PlayerStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class GuessFailureReason {
    DAY_SOLVED,
    MODEL_LOCKED,
    INVALID_WORD_FORMAT,
    WORD_NOT_IN_MODEL,
    DUPLICATE_GUESS
}

sealed class GuessSubmissionResult {
    data class Accepted(
        val updatedState: DayPlayState,
        val outcome: GuessOutcome,
        val solvedNow: Boolean
    ) : GuessSubmissionResult()

    data class Rejected(val reason: GuessFailureReason) : GuessSubmissionResult()
}

object GameEngine {
    fun initialDayState(puzzle: DayPuzzle, priorState: DayPlayState?): DayPlayState {
        if (priorState != null) {
            return priorState
        }

        val defaultModelId = puzzle.models.first().modelId
        return DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = defaultModelId,
            solved = false,
            solvedByModelId = null,
            guessesByModel = emptyMap()
        )
    }

    fun selectModel(state: DayPlayState, puzzle: DayPuzzle, modelId: String): DayPlayState {
        if (puzzle.models.none { it.modelId == modelId }) {
            return state
        }
        if (state.solved && state.solvedByModelId != modelId) {
            return state
        }
        return state.copy(selectedModelId = modelId)
    }

    fun submitGuess(state: DayPlayState, puzzle: DayPuzzle, inputGuess: String): GuessSubmissionResult {
        if (state.solved) {
            return GuessSubmissionResult.Rejected(GuessFailureReason.DAY_SOLVED)
        }

        val model = puzzle.models.firstOrNull { it.modelId == state.selectedModelId }
            ?: return GuessSubmissionResult.Rejected(GuessFailureReason.MODEL_LOCKED)

        val normalizedGuess = WordRules.normalize(inputGuess)
        if (!WordRules.isValidEnglishWord(normalizedGuess)) {
            return GuessSubmissionResult.Rejected(GuessFailureReason.INVALID_WORD_FORMAT)
        }

        val existingGuesses = state.guessesByModel[model.modelId].orEmpty()
        if (existingGuesses.any { it.guess == normalizedGuess }) {
            return GuessSubmissionResult.Rejected(GuessFailureReason.DUPLICATE_GUESS)
        }

        val rank = model.rankForGuess(normalizedGuess)
            ?: return GuessSubmissionResult.Rejected(GuessFailureReason.WORD_NOT_IN_MODEL)

        val outcome = GuessOutcome(guess = normalizedGuess, rank = rank)
        val updatedGuesses = existingGuesses + outcome
        val solvedNow = rank == 1

        val updatedState = state.copy(
            solved = solvedNow,
            solvedByModelId = if (solvedNow) model.modelId else null,
            selectedModelId = if (solvedNow) model.modelId else state.selectedModelId,
            guessesByModel = state.guessesByModel + (model.modelId to updatedGuesses)
        )

        return GuessSubmissionResult.Accepted(updatedState, outcome, solvedNow)
    }

    fun updateStatsOnSolve(
        currentStats: PlayerStats,
        solvedDate: LocalDate,
        solvedModelId: String,
        guessesToSolve: Int
    ): PlayerStats {
        val solvedDateKey = solvedDate.toString()
        if (solvedDateKey in currentStats.solvedDates) {
            return currentStats
        }

        val priorLastDate = currentStats.lastSolvedDate?.let(LocalDate::parse)
        val expectedNextDate = priorLastDate?.plus(DatePeriod(days = 1))
        val nextStreak = if (expectedNextDate == solvedDate) currentStats.currentStreak + 1 else 1
        val nextMaxStreak = maxOf(currentStats.maxStreak, nextStreak)

        val totalGuessesByModel = currentStats.totalGuessesByModel.toMutableMap().apply {
            put(solvedModelId, (this[solvedModelId] ?: 0) + guessesToSolve)
        }

        val winsByModel = currentStats.winsByModel.toMutableMap().apply {
            put(solvedModelId, (this[solvedModelId] ?: 0) + 1)
        }

        return currentStats.copy(
            totalWins = currentStats.totalWins + 1,
            currentStreak = nextStreak,
            maxStreak = nextMaxStreak,
            solvedDates = currentStats.solvedDates + solvedDateKey,
            lastSolvedDate = solvedDateKey,
            totalGuessesByModel = totalGuessesByModel,
            winsByModel = winsByModel
        )
    }
}
