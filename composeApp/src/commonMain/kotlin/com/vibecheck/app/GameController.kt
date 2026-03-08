package com.vibecheck.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vibecheck.data.GameStateStore
import com.vibecheck.data.ModelCatalogSource
import com.vibecheck.data.PuzzleSource
import com.vibecheck.domain.GameEngine
import com.vibecheck.domain.GuessLexicon
import com.vibecheck.domain.GuessFailureReason
import com.vibecheck.domain.GuessSubmissionResult
import com.vibecheck.domain.LoadableGuessLexicon
import com.vibecheck.domain.NoOpGuessLexicon
import com.vibecheck.domain.UtcDateProvider
import com.vibecheck.domain.WordRules
import com.vibecheck.model.DayPlayState
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.GuessOutcome
import com.vibecheck.model.ModelMetadata
import com.vibecheck.model.PersistedAppState
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class GameController(
    private val puzzleSource: PuzzleSource,
    private val modelCatalogSource: ModelCatalogSource = object : ModelCatalogSource {
        override suspend fun getCatalog(): Map<String, ModelMetadata> = emptyMap()
    },
    private val gameStateStore: GameStateStore,
    private val utcDateProvider: UtcDateProvider,
    private val guessLexicon: GuessLexicon = NoOpGuessLexicon
) {
    private companion object {
        const val FUTURE_PUZZLE_MESSAGE = "Future puzzles are not available yet."
        const val FALLBACK_DESCRIPTION = "Description unavailable"
        const val FALLBACK_INFO = "No model info available yet."
    }

    var uiState by mutableStateOf(GameUiState())
        private set

    private var persistedState: PersistedAppState = gameStateStore.loadState()
    private var currentPuzzle: DayPuzzle? = null
    private var currentModelCatalog: Map<String, ModelMetadata> = emptyMap()
    private var currentDayState: DayPlayState? = null
    private var currentDate: LocalDate? = null

    suspend fun loadToday() {
        loadDate(utcDateProvider.currentDate())
    }

    suspend fun loadPreviousDay() {
        val reference = currentDate ?: utcDateProvider.currentDate()
        loadDate(reference.plus(DatePeriod(days = -1)))
    }

    suspend fun loadNextDay() {
        val reference = currentDate ?: utcDateProvider.currentDate()
        loadDate(reference.plus(DatePeriod(days = 1)))
    }

    suspend fun loadDate(date: LocalDate) {
        if (guessLexicon is LoadableGuessLexicon) {
            guessLexicon.ensureLoaded()
        }

        uiState = uiState.copy(isLoading = true, message = null)

        if (date > utcDateProvider.currentDate()) {
            uiState = buildUiState(message = FUTURE_PUZZLE_MESSAGE)
            return
        }

        val puzzle = runCatching { puzzleSource.getPuzzle(date) }
            .getOrElse {
                currentPuzzle = null
                currentModelCatalog = emptyMap()
                currentDayState = null
                currentDate = date
                uiState = uiState.copy(
                    isLoading = false,
                    puzzleAvailable = false,
                    utcDate = date.toString(),
                    canLoadNext = date < utcDateProvider.currentDate(),
                    availableModels = emptyList(),
                    selectedModelId = null,
                    guesses = emptyList(),
                    solved = false,
                    guessInput = "",
                    message = "Failed to load puzzle data for this UTC date."
                )
                return
            }
        if (puzzle == null) {
            currentPuzzle = null
            currentModelCatalog = emptyMap()
            currentDayState = null
            currentDate = date
            uiState = uiState.copy(
                isLoading = false,
                puzzleAvailable = false,
                utcDate = date.toString(),
                canLoadNext = date < utcDateProvider.currentDate(),
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
        val modelCatalog = runCatching { modelCatalogSource.getCatalog() }.getOrDefault(emptyMap())

        currentPuzzle = puzzle
        currentModelCatalog = modelCatalog
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

        val updated = GameEngine.selectModel(state, puzzle, modelId)
        currentDayState = updated
        persistCurrentDay(updated)
        uiState = buildUiState(message = null)
    }

    fun submitGuess() {
        val puzzle = currentPuzzle ?: return
        val state = currentDayState ?: return

        val normalizedGuess = WordRules.normalize(uiState.guessInput)
        val dayWordSet = puzzle.models.asSequence()
            .flatMap { it.rankedWords.asSequence() }
            .toSet()
        val canonicalGuess = when {
            normalizedGuess in dayWordSet -> normalizedGuess
            else -> guessLexicon.canonicalize(normalizedGuess) ?: normalizedGuess
        }

        val result = GameEngine.submitGuess(state, puzzle, canonicalGuess)
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
                val solveGuessCount = result.updatedState.guessesByModel[result.updatedState.selectedModelId].orEmpty().size
                val solveMessage = if (result.solvedNow) {
                    "You solved today's Vibe Check in $solveGuessCount guesses."
                } else {
                    null
                }
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
                solvedAnswer = null,
                canLoadNext = currentDate?.let { it < utcDateProvider.currentDate() } ?: false,
                message = message
            )
        }

        val models = puzzle.models.map { model ->
            val modelGuesses = state.guessesByModel[model.modelId].orEmpty()
            val metadata = currentModelCatalog[model.modelId]
            ModelUiState(
                modelId = model.modelId,
                title = metadata?.title ?: model.modelId,
                description = metadata?.description ?: FALLBACK_DESCRIPTION,
                info = metadata?.info ?: FALLBACK_INFO,
                attempts = modelGuesses.size,
                bestRank = modelGuesses.minOfOrNull { it.rank },
                locked = false
            )
        }

        val guesses = state.guessesByModel[state.selectedModelId].orEmpty()
        val bestRank = guesses.minOfOrNull { it.rank }
        val solveHistory = persistedState.stats.solveHistoryByDate.values
        val averageGuesses = if (solveHistory.isEmpty()) {
            null
        } else {
            solveHistory.sumOf { it.guessesToSolve }.toDouble() / solveHistory.size.toDouble()
        }
        val lowestGuesses = solveHistory.minOfOrNull { it.guessesToSolve }

        return GameUiState(
            isLoading = false,
            puzzleAvailable = true,
            utcDate = puzzle.utcDate,
            canLoadNext = currentDate?.let { it < utcDateProvider.currentDate() } ?: false,
            availableModels = models,
            selectedModelId = state.selectedModelId,
            guesses = guesses,
            solved = state.solved,
            solvedByModelId = state.solvedByModelId,
            solvedAnswer = if (state.solved) puzzle.answer else null,
            guessInput = if (clearInput) "" else uiState.guessInput,
            message = message,
            guessCountForSelectedModel = guesses.size,
            bestRankForSelectedModel = bestRank,
            stats = StatsUiState(
                totalWins = persistedState.stats.totalWins,
                averageGuesses = averageGuesses,
                lowestGuesses = lowestGuesses
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
    val canLoadNext: Boolean = false,
    val availableModels: List<ModelUiState> = emptyList(),
    val selectedModelId: String? = null,
    val guesses: List<GuessOutcome> = emptyList(),
    val solved: Boolean = false,
    val solvedByModelId: String? = null,
    val solvedAnswer: String? = null,
    val guessInput: String = "",
    val message: String? = null,
    val guessCountForSelectedModel: Int = 0,
    val bestRankForSelectedModel: Int? = null,
    val stats: StatsUiState = StatsUiState()
)

data class ModelUiState(
    val modelId: String,
    val title: String,
    val description: String,
    val info: String,
    val attempts: Int,
    val bestRank: Int?,
    val locked: Boolean
)

data class StatsUiState(
    val totalWins: Int = 0,
    val averageGuesses: Double? = null,
    val lowestGuesses: Int? = null
)
