package com.vibecheck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibecheck.app.AppContainer
import com.vibecheck.app.GameController
import com.vibecheck.app.GameUiState
import com.vibecheck.app.ModelUiState
import com.vibecheck.ui.theme.VibeCheckTheme
import com.vibecheck.ui.theme.VibeCheckThemeTokens
import kotlinx.coroutines.launch
import kotlin.math.round

private enum class LayoutClass {
    Compact,
    Medium,
    Expanded
}

@Composable
fun App() {
    val controller = remember { AppContainer.createController() }

    LaunchedEffect(Unit) {
        controller.loadToday()
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
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundGlow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    val primaryAccent = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundGlow,
                            backgroundColor
                        )
                    )
                )
                .drawBehind {
                    val maxRadius = size.minDimension * 0.42f
                    drawCircle(
                        color = primaryAccent,
                        radius = maxRadius,
                        center = Offset(size.width * 0.9f, size.height * 0.12f)
                    )
                }
        ) {
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
                                        isCompact = layoutClass == LayoutClass.Compact,
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
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                            isCompact = layoutClass == LayoutClass.Compact,
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
                                    modifier = Modifier.weight(1.08f),
                                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                                ) {
                                    item {
                                        HeaderSection(
                                            uiState = uiState,
                                            isCompact = false,
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
                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                                                isCompact = false,
                                                onGuessChanged = controller::onGuessChanged,
                                                onSubmitGuess = controller::submitGuess,
                                                onModelSelected = controller::onModelSelected
                                            )
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(0.92f),
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
}

@Composable
private fun HeaderSection(
    uiState: GameUiState,
    isCompact: Boolean,
    onLoadPrevious: () -> Unit,
    onLoadToday: () -> Unit,
    onLoadNext: () -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    HeaderIntro(uiState)
                    DailySnapshot(uiState)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        HeaderIntro(uiState)
                    }
                    DailySnapshot(
                        uiState = uiState,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    "Browse today's and past UTC puzzles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) spacing.sm else spacing.md)
                ) {
                    NavigationButton(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = sizes.minTouchTarget)
                            .semantics { contentDescription = "Load previous UTC date" },
                        label = "Previous",
                        onClick = onLoadPrevious,
                        enabled = !uiState.isLoading
                    )
                    NavigationButton(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = sizes.minTouchTarget)
                            .semantics { contentDescription = "Load today in UTC" },
                        label = "Today",
                        onClick = onLoadToday,
                        enabled = !uiState.isLoading
                    )
                    NavigationButton(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = sizes.minTouchTarget)
                            .semantics { contentDescription = "Load next UTC date" },
                        label = "Next",
                        onClick = onLoadNext,
                        enabled = !uiState.isLoading && uiState.canLoadNext
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIntro(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        EyebrowPill("Daily semantic duel")
        Text("Vibe Check", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Keep the rules simple, but make the board feel like an event. Pick a model, test the neighborhood, and chase the answer.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusBanner(uiState)
    }
}

@Composable
private fun DailySnapshot(
    uiState: GameUiState,
    modifier: Modifier = Modifier
) {
    val spacing = VibeCheckThemeTokens.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                "UTC date",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                uiState.utcDate.ifBlank { "Waiting" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun NavigationButton(
    modifier: Modifier,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GameplaySection(
    uiState: GameUiState,
    isCompact: Boolean,
    onGuessChanged: (String) -> Unit,
    onSubmitGuess: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sizes = VibeCheckThemeTokens.sizes
    val selectedModel = uiState.availableModels
        .firstOrNull { it.modelId == uiState.selectedModelId }
    var infoExpanded by remember(uiState.utcDate, uiState.selectedModelId) { mutableStateOf(false) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            SectionHeading(
                title = "Play the board",
                detail = "Swap models, submit guesses, and watch how each engine approaches the same puzzle."
            )

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ActiveModelMetric(
                        model = selectedModel,
                        expanded = infoExpanded,
                        onToggleExpanded = { infoExpanded = !infoExpanded },
                        onDismiss = { infoExpanded = false }
                    )
                    SnapshotMetric(label = "Attempts", value = uiState.guessCountForSelectedModel.toString())
                    SnapshotMetric(
                        label = "Best rank",
                        value = uiState.bestRankForSelectedModel?.let { "#$it" } ?: "None"
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ActiveModelMetric(
                        model = selectedModel,
                        expanded = infoExpanded,
                        onToggleExpanded = { infoExpanded = !infoExpanded },
                        onDismiss = { infoExpanded = false },
                        modifier = Modifier.weight(1.3f)
                    )
                    SnapshotMetric(
                        label = "Attempts",
                        value = uiState.guessCountForSelectedModel.toString(),
                        modifier = Modifier.weight(0.7f)
                    )
                    SnapshotMetric(
                        label = "Best rank",
                        value = uiState.bestRankForSelectedModel?.let { "#$it" } ?: "None",
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Choose a model", style = MaterialTheme.typography.labelLarge)
                ModelSelector(
                    models = uiState.availableModels,
                    selectedModelId = uiState.selectedModelId,
                    onSelect = onModelSelected
                )
            }

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
                enabled = !uiState.solved && uiState.guessInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Submit Guess")
            }

            SolvedOrMessageBanner(uiState)
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
            val modifier = Modifier
                .heightIn(min = sizes.minTouchTarget)
                .semantics { contentDescription = "Select model ${model.title}" }

            val summary = buildString {
                append("${model.attempts} tries")
                model.bestRank?.let {
                    append(" / best #$it")
                }
            }

            if (selected) {
                Button(
                    modifier = modifier,
                    onClick = { onSelect(model.modelId) },
                    enabled = !model.locked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(model.title, style = MaterialTheme.typography.titleSmall)
                        Text(summary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                OutlinedButton(
                    modifier = modifier,
                    onClick = { onSelect(model.modelId) },
                    enabled = !model.locked,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f))
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(model.title, style = MaterialTheme.typography.titleSmall)
                        Text(summary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            SectionHeading(
                title = "Stats",
                detail = "Your overall results across completed puzzles."
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 540.dp
                val averageGuesses = uiState.stats.averageGuesses
                    ?.let { round(it * 100.0) / 100.0 }
                    ?.toString()
                    ?: "-"
                val lowestGuesses = uiState.stats.lowestGuesses?.toString() ?: "-"
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SummaryStatTile("Wins", uiState.stats.totalWins.toString(), "Completed puzzles")
                        SummaryStatTile("Average", averageGuesses, "Guesses per win")
                        SummaryStatTile("Lowest", lowestGuesses, "Best solve")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SummaryStatTile(
                            label = "Wins",
                            value = uiState.stats.totalWins.toString(),
                            detail = "Completed puzzles",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Average",
                            value = averageGuesses,
                            detail = "Guesses per win",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Lowest",
                            value = lowestGuesses,
                            detail = "Best solve",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuessHistorySection(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing
    val lastGuess = uiState.guesses.lastOrNull()
    val bestFirstGuesses = uiState.guesses.sortedBy { it.rank }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeading(
            title = "Guess history",
            detail = "Lower ranks are closer to the answer."
        )

        if (uiState.guesses.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Text(
                    modifier = Modifier.padding(spacing.md),
                    text = "No guesses yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            lastGuess?.let { guess ->
                SnapshotMetric(
                    label = "Last guess",
                    value = "#${guess.rank} ${guess.guess}"
                )
            }

            bestFirstGuesses.forEach { guess ->
                GuessHistoryRow(rank = guess.rank, word = guess.guess)
            }
        }
    }
}

@Composable
private fun SolvedOrMessageBanner(uiState: GameUiState) {
    val spacing = VibeCheckThemeTokens.spacing

    when {
        uiState.solved -> {
            val solvedAnswer = uiState.solvedAnswer ?: "Unknown"

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(
                        "Solved",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        solvedAnswer,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        !uiState.message.isNullOrBlank() -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Text(
                    modifier = Modifier.padding(spacing.md),
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    content: @Composable () -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing
    val sheenColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                sheenColor,
                                Color.Transparent
                            )
                        )
                    )
                }
                .padding(spacing.lg)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    detail: String
) {
    val spacing = VibeCheckThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EyebrowPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.secondary
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun StatusBanner(uiState: GameUiState) {
    val label = when {
        uiState.isLoading -> "Building board"
        uiState.solved -> "Puzzle cleared"
        uiState.puzzleAvailable -> "Ready to play"
        else -> "No board loaded"
    }

    val tone = when {
        uiState.solved -> MaterialTheme.colorScheme.tertiary
        uiState.puzzleAvailable -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = tone.copy(alpha = 0.12f),
        contentColor = tone
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ActiveModelMetric(
    model: ModelUiState?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = VibeCheckThemeTokens.spacing
    val title = model?.title ?: model?.modelId ?: "Unknown model"
    val description = model?.description ?: "Description unavailable"
    val value = "$title - $description"

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Text(
                        "Active model",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Show active model info" }
                        .clickable(onClick = onToggleExpanded),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("i", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        DropdownMenu(
            expanded = expanded && model != null,
            onDismissRequest = onDismiss
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        model?.info ?: "No model info available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val spacing = VibeCheckThemeTokens.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    val spacing = VibeCheckThemeTokens.spacing

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val spacing = VibeCheckThemeTokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            content()
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    val spacing = VibeCheckThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = MaterialTheme.shapes.medium
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun GuessHistoryRow(
    rank: Int,
    word: String
) {
    val spacing = VibeCheckThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                shape = MaterialTheme.shapes.medium
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                text = "#$rank",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Text(
            text = word,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatePanel(
    title: String,
    detail: String,
    trailing: @Composable (() -> Unit)? = null
) {
    val spacing = VibeCheckThemeTokens.spacing

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}
