package com.vibecheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.vibecheck.app.AppContainer
import com.vibecheck.app.GameController
import com.vibecheck.app.GameUiState
import com.vibecheck.app.ModelUiState
import com.vibecheck.ui.theme.VibeCheckTheme
import com.vibecheck.ui.theme.VibeCheckThemeTokens
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.round

private enum class LayoutClass {
    Compact,
    Medium,
    Expanded
}

@Composable
fun App() {
    val controller = remember { AppContainer.createController() }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialized) {
            initialized = true
            controller.loadToday()
        }
    }

    VibeCheckTheme {
        GameScreen(controller)
    }
}

@Composable
private fun GameScreen(controller: GameController) {
    val uiState = controller.uiState
    val coroutineScope = rememberCoroutineScope()
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes

    var dateInput by remember(uiState.utcDate) { mutableStateOf(uiState.utcDate) }
    var dateInputError by remember { mutableStateOf<String?>(null) }

    fun loadDateFromInput() {
        val parsedDate = runCatching { LocalDate.parse(dateInput.trim()) }.getOrNull()
        if (parsedDate == null) {
            dateInputError = "Enter a valid UTC date in YYYY-MM-DD format."
        } else {
            dateInputError = null
            coroutineScope.launch {
                controller.loadDate(parsedDate)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layoutClass = when {
                maxWidth <= sizes.compactMaxWidth -> LayoutClass.Compact
                maxWidth <= sizes.mediumMaxWidth -> LayoutClass.Medium
                else -> LayoutClass.Expanded
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (layoutClass == LayoutClass.Compact) spacing.md else spacing.lg,
                        vertical = spacing.md
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                when (layoutClass) {
                    LayoutClass.Compact,
                    LayoutClass.Medium -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = sizes.contentMaxWidth),
                            verticalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            item {
                                HeaderSection(
                                    uiState = uiState,
                                    dateInput = dateInput,
                                    dateInputError = dateInputError,
                                    isCompact = layoutClass == LayoutClass.Compact,
                                    onDateInputChanged = {
                                        dateInput = it
                                        dateInputError = null
                                    },
                                    onLoadDate = ::loadDateFromInput,
                                    onLoadPrevious = { coroutineScope.launch { controller.loadPreviousDay() } },
                                    onLoadToday = { coroutineScope.launch { controller.loadToday() } },
                                    onLoadNext = { coroutineScope.launch { controller.loadNextDay() } }
                                )
                            }

                            if (uiState.isLoading) {
                                item {
                                    StatePanel(
                                        title = "Loading puzzle",
                                        detail = "Fetching puzzle and progress for ${uiState.utcDate.ifBlank { "today" }}."
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (!uiState.puzzleAvailable) {
                                item {
                                    StatePanel(
                                        title = "Puzzle unavailable",
                                        detail = uiState.message ?: "No puzzle is available for this UTC date."
                                    )
                                }
                            } else {
                                item {
                                    GameplaySection(
                                        uiState = uiState,
                                        onGuessChanged = controller::onGuessChanged,
                                        onSubmitGuess = controller::submitGuess,
                                        onModelSelected = controller::onModelSelected
                                    )
                                }
                                item {
                                    StatsCard(uiState)
                                }
                            }
                        }
                    }

                    LayoutClass.Expanded -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = sizes.contentMaxWidth),
                            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                            verticalAlignment = Alignment.Top
                        ) {
                            LazyColumn(
                                modifier = Modifier.weight(1.1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.md)
                            ) {
                                item {
                                    HeaderSection(
                                        uiState = uiState,
                                        dateInput = dateInput,
                                        dateInputError = dateInputError,
                                        isCompact = false,
                                        onDateInputChanged = {
                                            dateInput = it
                                            dateInputError = null
                                        },
                                        onLoadDate = ::loadDateFromInput,
                                        onLoadPrevious = { coroutineScope.launch { controller.loadPreviousDay() } },
                                        onLoadToday = { coroutineScope.launch { controller.loadToday() } },
                                        onLoadNext = { coroutineScope.launch { controller.loadNextDay() } }
                                    )
                                }

                                if (uiState.isLoading) {
                                    item {
                                        StatePanel(
                                            title = "Loading puzzle",
                                            detail = "Fetching puzzle and progress for ${uiState.utcDate.ifBlank { "today" }}."
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                } else if (!uiState.puzzleAvailable) {
                                    item {
                                        StatePanel(
                                            title = "Puzzle unavailable",
                                            detail = uiState.message ?: "No puzzle is available for this UTC date."
                                        )
                                    }
                                } else {
                                    item {
                                        GameplaySection(
                                            uiState = uiState,
                                            onGuessChanged = controller::onGuessChanged,
                                            onSubmitGuess = controller::submitGuess,
                                            onModelSelected = controller::onModelSelected
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(spacing.md)
                            ) {
                                if (!uiState.isLoading && uiState.puzzleAvailable) {
                                    item { StatsCard(uiState) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    uiState: GameUiState,
    dateInput: String,
    dateInputError: String?,
    isCompact: Boolean,
    onDateInputChanged: (String) -> Unit,
    onLoadDate: () -> Unit,
    onLoadPrevious: () -> Unit,
    onLoadToday: () -> Unit,
    onLoadNext: () -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text("Vibe Check", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Play by UTC date and compare model performance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("UTC Date: ${uiState.utcDate}", style = MaterialTheme.typography.titleMedium)

            val navModifier = Modifier
                .weight(1f)
                .heightIn(min = sizes.minTouchTarget)
            val navArrangement = if (isCompact) spacing.sm else spacing.md

            Row(horizontalArrangement = Arrangement.spacedBy(navArrangement)) {
                OutlinedButton(
                    modifier = navModifier.semantics { contentDescription = "Load previous UTC date" },
                    onClick = onLoadPrevious,
                    enabled = !uiState.isLoading
                ) {
                    Text("Previous")
                }
                OutlinedButton(
                    modifier = navModifier.semantics { contentDescription = "Load today in UTC" },
                    onClick = onLoadToday,
                    enabled = !uiState.isLoading
                ) {
                    Text("Today")
                }
                OutlinedButton(
                    modifier = navModifier.semantics { contentDescription = "Load next UTC date" },
                    onClick = onLoadNext,
                    enabled = !uiState.isLoading
                ) {
                    Text("Next")
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                            onLoadDate()
                            true
                        } else {
                            false
                        }
                    },
                value = dateInput,
                onValueChange = onDateInputChanged,
                enabled = !uiState.isLoading,
                singleLine = true,
                label = { Text("Load UTC date (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLoadDate() })
            )

            dateInputError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sizes.minTouchTarget)
                    .semantics { contentDescription = "Load selected UTC date" },
                onClick = onLoadDate,
                enabled = !uiState.isLoading
            ) {
                Text("Load Date")
            }
        }
    }
}

@Composable
private fun GameplaySection(
    uiState: GameUiState,
    onGuessChanged: (String) -> Unit,
    onSubmitGuess: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text("Model", style = MaterialTheme.typography.titleMedium)
            ModelSelector(
                models = uiState.availableModels,
                selectedModelId = uiState.selectedModelId,
                onSelect = onModelSelected
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { keyEvent ->
                        val isEnter = keyEvent.key == Key.Enter
                        if (keyEvent.type == KeyEventType.KeyUp && isEnter && !uiState.solved && uiState.guessInput.isNotBlank()) {
                            onSubmitGuess()
                            true
                        } else {
                            false
                        }
                    },
                value = uiState.guessInput,
                onValueChange = onGuessChanged,
                label = { Text("Guess a word") },
                enabled = !uiState.solved,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!uiState.solved && uiState.guessInput.isNotBlank()) {
                            onSubmitGuess()
                        }
                    }
                )
            )

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sizes.minTouchTarget)
                    .semantics { contentDescription = "Submit guess" },
                onClick = onSubmitGuess,
                enabled = !uiState.solved && uiState.guessInput.isNotBlank()
            ) {
                Text("Submit Guess")
            }

            if (uiState.solved) {
                Text(
                    "Status: Solved by ${uiState.solvedByModelId}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                uiState.solvedAnswer?.let { answer ->
                    Text("Answer: $answer", style = MaterialTheme.typography.bodyLarge)
                }
            }

            uiState.message?.let { message ->
                Text(
                    text = "Update: $message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "Attempts (selected model): ${uiState.guessCountForSelectedModel}",
                style = MaterialTheme.typography.bodyMedium
            )
            uiState.bestRankForSelectedModel?.let { bestRank ->
                Text("Best rank so far: #$bestRank", style = MaterialTheme.typography.bodyMedium)
            }

            GuessHistorySection(uiState)
        }
    }
}

@Composable
private fun ModelSelector(
    models: List<ModelUiState>,
    selectedModelId: String?,
    onSelect: (String) -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes

    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        items(models) { model ->
            val selected = model.modelId == selectedModelId
            val label = buildString {
                append(model.displayName)
                append("  ${model.attempts} tries")
                model.bestRank?.let { append("  best #$it") }
            }

            FilterChip(
                modifier = Modifier
                    .heightIn(min = sizes.minTouchTarget)
                    .semantics { contentDescription = "Select model ${model.displayName}" },
                selected = selected,
                onClick = { onSelect(model.modelId) },
                enabled = !model.locked,
                label = {
                    val statePrefix = when {
                        model.locked -> "Unavailable"
                        selected -> "Selected"
                        else -> "Available"
                    }
                    Text("$statePrefix: $label")
                }
            )
        }
    }
}

@Composable
private fun StatsCard(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text("Stats Summary", style = MaterialTheme.typography.titleMedium)
            Text("Wins: ${uiState.stats.totalWins}")
            Text("Current streak: ${uiState.stats.currentStreak}")
            Text("Max streak: ${uiState.stats.maxStreak}")

            if (uiState.stats.winsByModel.isNotEmpty()) {
                Text("Wins by model", style = MaterialTheme.typography.titleSmall)
                uiState.stats.winsByModel.forEach { (modelId, wins) ->
                    Text("$modelId: $wins")
                }
            }

            if (uiState.stats.averageGuessesByModel.isNotEmpty()) {
                Text("Average guesses by model", style = MaterialTheme.typography.titleSmall)
                uiState.stats.averageGuessesByModel.forEach { (modelId, avg) ->
                    val rounded = round(avg * 100.0) / 100.0
                    Text("$modelId: $rounded")
                }
            }

            if (uiState.stats.bestGuessesByModel.isNotEmpty()) {
                Text("Best solve guesses by model", style = MaterialTheme.typography.titleSmall)
                uiState.stats.bestGuessesByModel.forEach { (modelId, best) ->
                    Text("$modelId: $best")
                }
            }

            if (uiState.stats.recentSolves.isNotEmpty()) {
                Text("Recent solves", style = MaterialTheme.typography.titleSmall)
                uiState.stats.recentSolves.forEach { record ->
                    Text("${record.utcDate}: ${record.modelId} in ${record.guessesToSolve}")
                }
            }
        }
    }
}

@Composable
private fun GuessHistorySection(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text("Guess History", style = MaterialTheme.typography.titleMedium)
        if (uiState.guesses.isEmpty()) {
            Text(
                "No guesses yet for this model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.guesses.forEachIndexed { index, guess ->
                Text("${index + 1}. ${guess.guess} -> #${guess.rank}")
            }
        }
    }
}

@Composable
private fun StatePanel(
    title: String,
    detail: String,
    trailing: @Composable (() -> Unit)? = null
) {
    val spacing = VibeCheckThemeTokens.spacing

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            trailing?.invoke()
        }
    }
}
