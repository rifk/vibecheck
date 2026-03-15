package com.vibecheck.domain

import com.vibecheck.model.DayPlayState
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.DaySolveRecord
import com.vibecheck.model.GuessOutcome
import com.vibecheck.model.PlayerStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class GuessFailureReason {
    DAY_SOLVED,
    MODEL_LOCKED,
    INVALID_WORD_FORMAT,
    WORD_NOT_IN_ALL_MODELS,
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
        val validModelIds = puzzle.models.map { it.modelId }.toSet()
        val defaultModelId = puzzle.models.first().modelId

        if (priorState == null) {
            return DayPlayState(
                utcDate = puzzle.utcDate,
                selectedModelId = defaultModelId,
                solved = false,
                guessesByModel = emptyMap()
            )
        }

        val selectedModelId = if (priorState.selectedModelId in validModelIds) {
            priorState.selectedModelId
        } else {
            defaultModelId
        }

        val guessesByModel = pruneGuessesByModel(puzzle, priorState)
        val solved = priorState.solved || guessesByModel.values.flatten().any { it.guess == puzzle.answer }

        return DayPlayState(
            utcDate = puzzle.utcDate,
            selectedModelId = selectedModelId,
            solved = solved,
            guessesByModel = guessesByModel
        )
    }

    fun selectModel(state: DayPlayState, puzzle: DayPuzzle, modelId: String): DayPlayState {
        if (puzzle.models.none { it.modelId == modelId }) {
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

        val existingGuesses = state.guessesByModel[state.selectedModelId].orEmpty()
        if (existingGuesses.any { it.guess == normalizedGuess }) {
            return GuessSubmissionResult.Rejected(GuessFailureReason.DUPLICATE_GUESS)
        }

        val rank = model.rankForGuess(normalizedGuess)
            ?: return GuessSubmissionResult.Rejected(GuessFailureReason.WORD_NOT_IN_ALL_MODELS)

        val updatedGuesses = existingGuesses + GuessOutcome(guess = normalizedGuess, rank = rank)
        val updatedGuessesByModel = state.guessesByModel + (model.modelId to updatedGuesses)
        val solvedNow = rank == 1

        val updatedState = state.copy(
            solved = state.solved || solvedNow,
            selectedModelId = state.selectedModelId,
            guessesByModel = updatedGuessesByModel
        )

        val outcome = GuessOutcome(guess = normalizedGuess, rank = rank)
        return GuessSubmissionResult.Accepted(updatedState, outcome, solvedNow)
    }

    fun updateStatsOnSolve(
        currentStats: PlayerStats,
        solvedDate: LocalDate,
        guessesToSolve: Int
    ): PlayerStats {
        val solvedDateKey = solvedDate.toString()
        if (solvedDateKey in currentStats.solvedDates) {
            return currentStats
        }

        val solvedDates = currentStats.solvedDates + solvedDateKey
        val (currentStreak, maxStreak) = computeStreaks(solvedDates)

        val solveHistoryByDate = currentStats.solveHistoryByDate.toMutableMap().apply {
            put(
                solvedDateKey,
                DaySolveRecord(
                    guessesToSolve = guessesToSolve
                )
            )
        }

        return currentStats.copy(
            totalWins = currentStats.totalWins + 1,
            currentStreak = currentStreak,
            maxStreak = maxStreak,
            solvedDates = solvedDates,
            lastSolvedDate = solvedDates.maxOrNull(),
            solveHistoryByDate = solveHistoryByDate
        )
    }

    private fun pruneGuessesByModel(puzzle: DayPuzzle, priorState: DayPlayState): Map<String, List<GuessOutcome>> {
        if (priorState.guessesByModel.isEmpty()) return emptyMap()
        val modelsById = puzzle.models.associateBy { it.modelId }
        return priorState.guessesByModel
            .mapNotNull { (modelId, guesses) ->
                val model = modelsById[modelId] ?: return@mapNotNull null
                val filtered = guesses.mapNotNull { outcome ->
                    val rank = model.rankForGuess(outcome.guess) ?: return@mapNotNull null
                    GuessOutcome(guess = outcome.guess, rank = rank)
                }
                modelId to filtered
            }
            .toMap()
            .filterValues { it.isNotEmpty() }
    }

    private fun computeStreaks(solvedDates: Set<String>): Pair<Int, Int> {
        if (solvedDates.isEmpty()) {
            return 0 to 0
        }

        val ordered = solvedDates.map(LocalDate::parse).sorted()
        var maxStreak = 1
        var rolling = 1
        for (index in 1 until ordered.size) {
            rolling = if (ordered[index - 1].plus(DatePeriod(days = 1)) == ordered[index]) {
                rolling + 1
            } else {
                1
            }
            maxStreak = maxOf(maxStreak, rolling)
        }

        var currentStreak = 1
        for (index in ordered.lastIndex downTo 1) {
            if (ordered[index - 1].plus(DatePeriod(days = 1)) == ordered[index]) {
                currentStreak += 1
            } else {
                break
            }
        }

        return currentStreak to maxStreak
    }
}
