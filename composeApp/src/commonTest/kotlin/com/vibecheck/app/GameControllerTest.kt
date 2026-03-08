package com.vibecheck.app

import com.vibecheck.data.GameStateStore
import com.vibecheck.data.ModelCatalogSource
import com.vibecheck.data.PuzzleSource
import com.vibecheck.domain.GuessLexicon
import com.vibecheck.domain.UtcDateProvider
import com.vibecheck.model.DayPuzzle
import com.vibecheck.model.ModelMetadata
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
                ModelPuzzle("m1", listOf("serenity", "calm")),
                ModelPuzzle("m2", listOf("serenity", "peace")),
                ModelPuzzle("m3", listOf("serenity", "quiet")),
                ModelPuzzle("m4", listOf("serenity", "still"))
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
    fun metadataCatalog_populatesTitleDescriptionAndInfo() = runTest {
        val date = LocalDate.parse("2026-01-08")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "serenity",
            models = listOf(ModelPuzzle("m1", listOf("serenity", "calm")))
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            modelCatalogSource = FakeModelCatalogSource(
                mapOf(
                    "m1" to ModelMetadata(
                        modelId = "m1",
                        title = "Atlas",
                        description = "Steady navigator",
                        info = "Maps broad semantic terrain before narrowing in."
                    )
                )
            ),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()

        val model = controller.uiState.availableModels.single()
        assertEquals("Atlas", model.title)
        assertEquals("Steady navigator", model.description)
        assertEquals("Maps broad semantic terrain before narrowing in.", model.info)
    }

    @Test
    fun missingMetadata_fallsBackToModelIdAndDefaultCopy() = runTest {
        val date = LocalDate.parse("2026-01-09")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "signal",
            models = listOf(ModelPuzzle("m1", listOf("signal", "noise")))
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            modelCatalogSource = FakeModelCatalogSource(emptyMap()),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()

        val model = controller.uiState.availableModels.single()
        assertEquals("m1", model.title)
        assertEquals("Description unavailable", model.description)
        assertEquals("No model info available yet.", model.info)
    }

    @Test
    fun solvedDay_allowsSwitchingToOtherModelsForBrowsing() = runTest {
        val date = LocalDate.parse("2026-01-02")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "noise")),
                ModelPuzzle("m2", listOf("signal", "tone"))
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
        assertEquals("signal", controller.uiState.solvedAnswer)
        controller.onModelSelected("m2")
        assertEquals("m2", controller.uiState.selectedModelId)
    }

    @Test
    fun solvedDay_rejectsNewGuessesAfterSwitchingModels() = runTest {
        val date = LocalDate.parse("2026-01-24")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "noise")),
                ModelPuzzle("m2", listOf("signal", "tone", "hum"))
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

        controller.onModelSelected("m2")
        assertEquals("m2", controller.uiState.selectedModelId)
        assertEquals(0, controller.uiState.guesses.size)

        controller.onGuessChanged("tone")
        controller.submitGuess()

        assertEquals("This day is already solved.", controller.uiState.message)
        assertEquals("m2", controller.uiState.selectedModelId)
        assertEquals(0, controller.uiState.guesses.size)
    }

    @Test
    fun historiesRemainSeparate_beforeSolve() = runTest {
        val date = LocalDate.parse("2026-01-03")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "anchor",
            models = listOf(
                ModelPuzzle("m1", listOf("anchor", "chain", "ship")),
                ModelPuzzle("m2", listOf("anchor", "dock", "port"))
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

    @Test
    fun modelProgress_reflectsAttemptsAndBestRankPerModelBeforeSolve() = runTest {
        val date = LocalDate.parse("2026-01-04")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "anchor",
            models = listOf(
                ModelPuzzle("m1", listOf("anchor", "chain", "ship")),
                ModelPuzzle("m2", listOf("anchor", "dock", "port"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()
        controller.onGuessChanged("ship")
        controller.submitGuess()

        controller.onModelSelected("m2")
        controller.onGuessChanged("port")
        controller.submitGuess()
        controller.onGuessChanged("dock")
        controller.submitGuess()

        val modelsById = controller.uiState.availableModels.associateBy { it.modelId }
        assertEquals(1, modelsById.getValue("m1").attempts)
        assertEquals(3, modelsById.getValue("m1").bestRank)
        assertEquals(2, modelsById.getValue("m2").attempts)
        assertEquals(2, modelsById.getValue("m2").bestRank)
    }

    @Test
    fun missingDate_showsUnavailableState_andCanRecoverByLoadingAvailableDate() = runTest {
        val availableDate = LocalDate.parse("2026-01-04")
        val missingDate = LocalDate.parse("2026-01-03")
        val puzzle = DayPuzzle(
            utcDate = availableDate.toString(),
            answer = "harbor",
            models = listOf(
                ModelPuzzle("m1", listOf("harbor", "port", "dock"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(availableDate to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(availableDate)
        )

        controller.loadDate(missingDate)
        assertFalse(controller.uiState.puzzleAvailable)
        assertTrue(controller.uiState.message?.contains("No puzzle is available") == true)

        controller.loadDate(availableDate)
        assertTrue(controller.uiState.puzzleAvailable)
        assertEquals("2026-01-04", controller.uiState.utcDate)
    }

    @Test
    fun sourceFailure_showsLoadErrorState_insteadOfThrowing() = runTest {
        val date = LocalDate.parse("2026-01-06")
        val controller = GameController(
            puzzleSource = ThrowingPuzzleSource(),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()

        assertFalse(controller.uiState.puzzleAvailable)
        assertEquals("2026-01-06", controller.uiState.utcDate)
        assertTrue(controller.uiState.message?.contains("Failed to load puzzle data") == true)
    }

    @Test
    fun relativeNavigation_movesAcrossUtcDays() = runTest {
        val day1 = LocalDate.parse("2026-01-10")
        val day2 = LocalDate.parse("2026-01-11")
        val day3 = LocalDate.parse("2026-01-12")

        val puzzle1 = DayPuzzle(
            utcDate = day1.toString(),
            answer = "anchor",
            models = listOf(ModelPuzzle("m1", listOf("anchor", "chain")))
        )
        val puzzle2 = DayPuzzle(
            utcDate = day2.toString(),
            answer = "signal",
            models = listOf(ModelPuzzle("m1", listOf("signal", "noise")))
        )
        val puzzle3 = DayPuzzle(
            utcDate = day3.toString(),
            answer = "harbor",
            models = listOf(ModelPuzzle("m1", listOf("harbor", "port")))
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(
                mapOf(
                    day1 to puzzle1,
                    day2 to puzzle2,
                    day3 to puzzle3
                )
            ),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(day2)
        )

        controller.loadToday()
        assertEquals("2026-01-11", controller.uiState.utcDate)
        assertFalse(controller.uiState.canLoadNext)

        controller.loadPreviousDay()
        assertEquals("2026-01-10", controller.uiState.utcDate)
        assertTrue(controller.uiState.canLoadNext)

        controller.loadNextDay()
        assertEquals("2026-01-11", controller.uiState.utcDate)
        assertFalse(controller.uiState.canLoadNext)

        controller.loadNextDay()
        assertEquals("2026-01-11", controller.uiState.utcDate)
        assertTrue(controller.uiState.puzzleAvailable)
        assertFalse(controller.uiState.canLoadNext)
        assertEquals("Future puzzles are not available yet.", controller.uiState.message)
    }

    @Test
    fun futureDate_isRejectedWithoutChangingCurrentPuzzle() = runTest {
        val today = LocalDate.parse("2026-01-11")
        val futureDate = LocalDate.parse("2026-01-12")
        val puzzle = DayPuzzle(
            utcDate = today.toString(),
            answer = "signal",
            models = listOf(ModelPuzzle("m1", listOf("signal", "noise")))
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(today to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(today)
        )

        controller.loadToday()
        controller.loadDate(futureDate)

        assertEquals("2026-01-11", controller.uiState.utcDate)
        assertTrue(controller.uiState.puzzleAvailable)
        assertFalse(controller.uiState.canLoadNext)
        assertEquals("Future puzzles are not available yet.", controller.uiState.message)
    }

    @Test
    fun futureDate_isRejectedWhenNothingHasBeenLoadedYet() = runTest {
        val today = LocalDate.parse("2026-01-11")
        val futureDate = LocalDate.parse("2026-01-12")
        val controller = GameController(
            puzzleSource = FakePuzzleSource(emptyMap()),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(today)
        )

        controller.loadDate(futureDate)

        assertFalse(controller.uiState.isLoading)
        assertFalse(controller.uiState.puzzleAvailable)
        assertEquals("", controller.uiState.utcDate)
        assertFalse(controller.uiState.canLoadNext)
        assertEquals("Future puzzles are not available yet.", controller.uiState.message)
    }

    @Test
    fun selectedModelMetrics_trackAttemptCountAndBestRank() = runTest {
        val date = LocalDate.parse("2026-01-20")
        val puzzle = DayPuzzle(
            utcDate = date.toString(),
            answer = "signal",
            models = listOf(
                ModelPuzzle("m1", listOf("signal", "tone", "noise", "echo"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(date to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(date)
        )

        controller.loadToday()
        assertEquals(0, controller.uiState.guessCountForSelectedModel)
        assertEquals(null, controller.uiState.bestRankForSelectedModel)

        controller.onGuessChanged("echo")
        controller.submitGuess()
        assertEquals(1, controller.uiState.guessCountForSelectedModel)
        assertEquals(4, controller.uiState.bestRankForSelectedModel)

        controller.onGuessChanged("noise")
        controller.submitGuess()
        assertEquals(2, controller.uiState.guessCountForSelectedModel)
        assertEquals(3, controller.uiState.bestRankForSelectedModel)
    }

    @Test
    fun persistedState_survivesControllerRestart() = runTest {
        val day = LocalDate.parse("2026-01-21")
        val puzzle = DayPuzzle(
            utcDate = day.toString(),
            answer = "harbor",
            models = listOf(
                ModelPuzzle("m1", listOf("harbor", "port", "dock"))
            )
        )

        val store = InMemoryStore()
        val source = FakePuzzleSource(mapOf(day to puzzle))
        val dateProvider = FixedDateProvider(day)

        val firstController = GameController(
            puzzleSource = source,
            gameStateStore = store,
            utcDateProvider = dateProvider
        )
        firstController.loadToday()
        firstController.onGuessChanged("port")
        firstController.submitGuess()
        firstController.onGuessChanged("harbor")
        firstController.submitGuess()

        assertTrue(firstController.uiState.solved)
        assertEquals(1, firstController.uiState.stats.totalWins)
        assertEquals(2.0, firstController.uiState.stats.averageGuesses)
        assertEquals(2, firstController.uiState.stats.lowestGuesses)

        val secondController = GameController(
            puzzleSource = source,
            gameStateStore = store,
            utcDateProvider = dateProvider
        )
        secondController.loadToday()

        assertTrue(secondController.uiState.solved)
        assertEquals("harbor", secondController.uiState.solvedAnswer)
        assertEquals(2, secondController.uiState.guessCountForSelectedModel)
        assertEquals(1, secondController.uiState.bestRankForSelectedModel)
        assertEquals(1, secondController.uiState.stats.totalWins)
        assertEquals(2.0, secondController.uiState.stats.averageGuesses)
        assertEquals(2, secondController.uiState.stats.lowestGuesses)
    }

    @Test
    fun aggregateStats_reflectWinsAverageAndLowestSolveCount() = runTest {
        val day1 = LocalDate.parse("2026-01-22")
        val day2 = LocalDate.parse("2026-01-23")
        val puzzle1 = DayPuzzle(
            utcDate = day1.toString(),
            answer = "serenity",
            models = listOf(ModelPuzzle("m1", listOf("serenity", "calm")))
        )
        val puzzle2 = DayPuzzle(
            utcDate = day2.toString(),
            answer = "signal",
            models = listOf(ModelPuzzle("m2", listOf("signal", "noise")))
        )

        val store = InMemoryStore()
        val source = FakePuzzleSource(mapOf(day1 to puzzle1, day2 to puzzle2))
        val controller = GameController(
            puzzleSource = source,
            gameStateStore = store,
            utcDateProvider = FixedDateProvider(day2)
        )

        controller.loadDate(day1)
        controller.onGuessChanged("serenity")
        controller.submitGuess()

        controller.loadDate(day2)
        controller.onGuessChanged("noise")
        controller.submitGuess()
        controller.onGuessChanged("signal")
        controller.submitGuess()

        assertEquals(2, controller.uiState.stats.totalWins)
        assertEquals(1.5, controller.uiState.stats.averageGuesses)
        assertEquals(1, controller.uiState.stats.lowestGuesses)
    }

    @Test
    fun lemmaMappedGuess_isAcceptedAsCanonicalWord() = runTest {
        val day = LocalDate.parse("2026-01-25")
        val puzzle = DayPuzzle(
            utcDate = day.toString(),
            answer = "run",
            models = listOf(
                ModelPuzzle("m1", listOf("run", "jog"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(day to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(day),
            guessLexicon = FakeGuessLexicon(mapOf("running" to "run"))
        )

        controller.loadToday()
        controller.onGuessChanged("running")
        controller.submitGuess()

        assertTrue(controller.uiState.solved)
        assertEquals("run", controller.uiState.guesses.first().guess)
    }

    @Test
    fun lemmaVariant_isRejectedAsDuplicateWhenCanonicalAlreadyGuessed() = runTest {
        val day = LocalDate.parse("2026-01-26")
        val puzzle = DayPuzzle(
            utcDate = day.toString(),
            answer = "anchor",
            models = listOf(
                ModelPuzzle("m1", listOf("anchor", "run", "jog", "sprint"))
            )
        )

        val controller = GameController(
            puzzleSource = FakePuzzleSource(mapOf(day to puzzle)),
            gameStateStore = InMemoryStore(),
            utcDateProvider = FixedDateProvider(day),
            guessLexicon = FakeGuessLexicon(mapOf("running" to "run"))
        )

        controller.loadToday()
        controller.onGuessChanged("run")
        controller.submitGuess()
        controller.onGuessChanged("running")
        controller.submitGuess()

        assertEquals("You've already guessed that word.", controller.uiState.message)
    }
}

private class FakePuzzleSource(
    private val puzzles: Map<LocalDate, DayPuzzle>
) : PuzzleSource {
    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? = puzzles[utcDate]
}

private class ThrowingPuzzleSource : PuzzleSource {
    override suspend fun getPuzzle(utcDate: LocalDate): DayPuzzle? {
        error("boom")
    }
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

private class FakeGuessLexicon(
    private val mapping: Map<String, String>
) : GuessLexicon {
    override fun canonicalize(input: String): String? = mapping[input]
}

private class FakeModelCatalogSource(
    private val metadataById: Map<String, ModelMetadata>
) : ModelCatalogSource {
    override suspend fun getCatalog(): Map<String, ModelMetadata> = metadataById
}
