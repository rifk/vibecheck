package com.vibecheck.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vibecheck.data.GameStateStore
import com.vibecheck.data.PuzzleSource
import com.vibecheck.domain.GameEngine
import com.vibecheck.domain.GuessFailureReason
import com.vibecheck.domain.GuessSubmissionResult
import com.vibecheck.domain.UtcDateProvider
import com.vibecheck.model.DayPlayState
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.GuessOutcome
import com.vibecheck.model.PersistedAppState
import kotlinx.datetime.LocalDate

class GameController(
    private val puzzleSource: PuzzleSource,
    private val gameStateStore: GameStateStore,
    private val utcDateProvider: UtcDateProvider
) {
    var uiState by mutableStateOf(GameUiState())
        private set

    private var persistedState: PersistedAppState = gameStateStore.loadState()
    private var currentPuzzle: DayPuzzle? = null
    private var currentDayState: DayPlayState? = null
    private var currentDate: LocalDate? = null

    suspend fun loadToday() {
        loadDate(utcDateProvider.currentDate())
    }

    suspend fun loadDate(date: LocalDate) {
        uiState = uiState.copy(isLoading = true, message = null)

        val puzzle = puzzleSource.getPuzzle(date)
        if (puzzle == null) {
            currentPuzzle = null
            currentDayState = null
            currentDate = date
            uiState = uiState.copy(
                isLoading = false,
                puzzleAvailable = false,
                utcDate = date.toString(),
                availableModels = emptyList(),
                selectedModelId = null,
                guesses = emptyList(),
                solved = false,
                guessInput = "",
                message = "No puzzle is available for this UTC date."
            )
            return
        }

        val dateKey = date.toString()
        val priorState = persistedState.dayStates[dateKey]
        val initialized = GameEngine.initialDayState(puzzle, priorState)

        currentPuzzle = puzzle
        currentDate = date
        currentDayState = initialized

        persistedState = persistedState.copy(
            dayStates = persistedState.dayStates + (dateKey to initialized)
        )
        gameStateStore.saveState(persistedState)

        uiState = buildUiState(message = null)
    }

    fun onGuessChanged(rawGuess: String) {
        uiState = uiState.copy(guessInput = rawGuess)
    }

    fun onModelSelected(modelId: String) {
        val puzzle = currentPuzzle ?: return
        val state = currentDayState ?: return

        if (state.solved && state.solvedByModelId != modelId) {
            uiState = buildUiState(message = "This day is already solved. Other models are locked.")
            return
        }

        val updated = GameEngine.selectModel(state, puzzle, modelId)
        currentDayState = updated
        persistCurrentDay(updated)
        uiState = buildUiState(message = null)
    }

    fun submitGuess() {
        val puzzle = currentPuzzle ?: return
        val state = currentDayState ?: return

        val result = GameEngine.submitGuess(state, puzzle, uiState.guessInput)
        when (result) {
            is GuessSubmissionResult.Rejected -> {
                uiState = buildUiState(message = rejectionMessage(result.reason))
            }

            is GuessSubmissionResult.Accepted -> {
                currentDayState = result.updatedState
                persistCurrentDay(result.updatedState)

                if (result.solvedNow) {
                    val solvedDate = currentDate ?: return
                    val solvedModelId = result.updatedState.solvedByModelId ?: return
                    val guessCount = result.updatedState.guessesByModel[solvedModelId].orEmpty().size
                    persistedState = persistedState.copy(
                        stats = GameEngine.updateStatsOnSolve(
                            currentStats = persistedState.stats,
                            solvedDate = solvedDate,
                            solvedModelId = solvedModelId,
                            guessesToSolve = guessCount
                        )
                    )
                    gameStateStore.saveState(persistedState)
                }

                val rankMessage = "${result.outcome.guess} is ranked #${result.outcome.rank}."
                val solveMessage = if (result.solvedNow) "You solved today's Vibe Check." else null
                uiState = buildUiState(message = solveMessage ?: rankMessage, clearInput = true)
            }
        }
    }

    private fun persistCurrentDay(dayState: DayPlayState) {
        val dateKey = currentDate?.toString() ?: return
        persistedState = persistedState.copy(
            dayStates = persistedState.dayStates + (dateKey to dayState)
        )
        gameStateStore.saveState(persistedState)
    }

    private fun buildUiState(message: String?, clearInput: Boolean = false): GameUiState {
        val puzzle = currentPuzzle
        val state = currentDayState

        if (puzzle == null || state == null) {
            return uiState.copy(
                isLoading = false,
                puzzleAvailable = false,
                message = message
            )
        }

        val models = puzzle.models.map { model ->
            ModelUiState(
                modelId = model.modelId,
                displayName = model.displayName,
                locked = state.solved && state.solvedByModelId != model.modelId
            )
        }

        val guesses = state.guessesByModel[state.selectedModelId].orEmpty()

        return GameUiState(
            isLoading = false,
            puzzleAvailable = true,
            utcDate = puzzle.utcDate,
            availableModels = models,
            selectedModelId = state.selectedModelId,
            guesses = guesses,
            solved = state.solved,
            solvedByModelId = state.solvedByModelId,
            guessInput = if (clearInput) "" else uiState.guessInput,
            message = message,
            stats = StatsUiState(
                totalWins = persistedState.stats.totalWins,
                currentStreak = persistedState.stats.currentStreak,
                maxStreak = persistedState.stats.maxStreak,
                averageGuessesByModel = persistedState.stats.averageGuessesByModel()
            )
        )
    }

    private fun rejectionMessage(reason: GuessFailureReason): String = when (reason) {
        GuessFailureReason.DAY_SOLVED -> "This day is already solved."
        GuessFailureReason.MODEL_LOCKED -> "Selected model is unavailable."
        GuessFailureReason.INVALID_WORD_FORMAT -> "Use a lowercase English word."
        GuessFailureReason.WORD_NOT_IN_MODEL -> "That word is not in this model's dictionary."
        GuessFailureReason.DUPLICATE_GUESS -> "You've already guessed that word."
    }
}

data class GameUiState(
    val isLoading: Boolean = true,
    val puzzleAvailable: Boolean = true,
    val utcDate: String = "",
    val availableModels: List<ModelUiState> = emptyList(),
    val selectedModelId: String? = null,
    val guesses: List<GuessOutcome> = emptyList(),
    val solved: Boolean = false,
    val solvedByModelId: String? = null,
    val guessInput: String = "",
    val message: String? = null,
    val stats: StatsUiState = StatsUiState()
)

data class ModelUiState(
    val modelId: String,
    val displayName: String,
    val locked: Boolean
)

data class StatsUiState(
    val totalWins: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val averageGuessesByModel: Map<String, Double> = emptyMap()
)
