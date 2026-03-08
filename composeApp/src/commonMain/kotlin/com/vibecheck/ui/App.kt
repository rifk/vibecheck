package com.vibecheck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
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
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundGlow = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    val primaryAccent = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

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

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    HeaderIntro(uiState)
                    DailySnapshot(uiState, isCompact = true)
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
                        isCompact = false,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    "Browse puzzles by UTC date",
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
                        enabled = !uiState.isLoading
                    )
                }
            }

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    DateInputField(
                        value = dateInput,
                        enabled = !uiState.isLoading,
                        onValueChange = onDateInputChanged,
                        onSubmit = onLoadDate
                    )
                    LoadDateButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        onClick = onLoadDate
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    DateInputField(
                        value = dateInput,
                        enabled = !uiState.isLoading,
                        onValueChange = onDateInputChanged,
                        onSubmit = onLoadDate,
                        modifier = Modifier.weight(1f)
                    )
                    LoadDateButton(
                        modifier = Modifier
                            .wrapContentWidth()
                            .heightIn(min = sizes.minTouchTarget),
                        enabled = !uiState.isLoading,
                        onClick = onLoadDate
                    )
                }
            }

            dateInputError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
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
    isCompact: Boolean,
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
        val contentModifier = Modifier.padding(spacing.md)
        if (isCompact) {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                SnapshotMetric(label = "UTC date", value = uiState.utcDate.ifBlank { "Waiting" })
                SnapshotMetric(label = "Models", value = uiState.availableModels.size.toString())
                SnapshotMetric(
                    label = "Board",
                    value = when {
                        uiState.isLoading -> "Loading"
                        uiState.solved -> "Solved"
                        uiState.puzzleAvailable -> "Live"
                        else -> "Offline"
                    }
                )
            }
        } else {
            Row(
                modifier = contentModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                SnapshotMetric(
                    label = "UTC date",
                    value = uiState.utcDate.ifBlank { "Waiting" },
                    modifier = Modifier.weight(1f)
                )
                SnapshotMetric(
                    label = "Models",
                    value = uiState.availableModels.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                SnapshotMetric(
                    label = "Board",
                    value = when {
                        uiState.isLoading -> "Loading"
                        uiState.solved -> "Solved"
                        uiState.puzzleAvailable -> "Live"
                        else -> "Offline"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DateInputField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text("Load UTC date (YYYY-MM-DD)") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() })
    )
}

@Composable
private fun LoadDateButton(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier
            .semantics { contentDescription = "Load selected UTC date" },
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text("Load Date")
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
    val selectedModelName = uiState.availableModels
        .firstOrNull { it.modelId == uiState.selectedModelId }
        ?.displayName
        ?: uiState.selectedModelId
        ?: "Unknown model"

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            SectionHeading(
                title = "Play the board",
                detail = "Swap models, submit guesses, and watch how each engine approaches the same puzzle."
            )

            if (isCompact) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SnapshotMetric(label = "Active model", value = selectedModelName)
                    SnapshotMetric(label = "Attempts", value = uiState.guessCountForSelectedModel.toString())
                    SnapshotMetric(
                        label = "Best rank",
                        value = uiState.bestRankForSelectedModel?.let { "#$it" } ?: "None"
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SnapshotMetric(
                        label = "Active model",
                        value = selectedModelName,
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
                .semantics { contentDescription = "Select model ${model.displayName}" }

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
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
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
                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
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
                title = "Record book",
                detail = "A cleaner snapshot of how you and each model have been performing."
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 540.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SummaryStatTile("Wins", uiState.stats.totalWins.toString(), "Total clears")
                        SummaryStatTile("Streak", uiState.stats.currentStreak.toString(), "Current run")
                        SummaryStatTile("Best", uiState.stats.maxStreak.toString(), "Longest streak")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SummaryStatTile(
                            label = "Wins",
                            value = uiState.stats.totalWins.toString(),
                            detail = "Total clears",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Streak",
                            value = uiState.stats.currentStreak.toString(),
                            detail = "Current run",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatTile(
                            label = "Best",
                            value = uiState.stats.maxStreak.toString(),
                            detail = "Longest streak",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (uiState.stats.winsByModel.isNotEmpty()) {
                StatsGroup(title = "Wins by model") {
                    uiState.stats.winsByModel.forEach { (modelId, wins) ->
                        StatRow(label = modelId, value = wins.toString())
                    }
                }
            }

            if (uiState.stats.averageGuessesByModel.isNotEmpty()) {
                StatsGroup(title = "Average guesses") {
                    uiState.stats.averageGuessesByModel.forEach { (modelId, avg) ->
                        val rounded = round(avg * 100.0) / 100.0
                        StatRow(label = modelId, value = rounded.toString())
                    }
                }
            }

            if (uiState.stats.bestGuessesByModel.isNotEmpty()) {
                StatsGroup(title = "Best solve guesses") {
                    uiState.stats.bestGuessesByModel.forEach { (modelId, best) ->
                        StatRow(label = modelId, value = best.toString())
                    }
                }
            }

            if (uiState.stats.recentSolves.isNotEmpty()) {
                StatsGroup(title = "Recent solves") {
                    uiState.stats.recentSolves.forEach { record ->
                        StatRow(
                            label = record.utcDate,
                            value = "${record.modelId} / ${record.guessesToSolve} guesses"
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
                    text = "No guesses yet for this model.",
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
            val solvedByName = uiState.availableModels
                .firstOrNull { it.modelId == uiState.solvedByModelId }
                ?.displayName
                ?: uiState.solvedByModelId
                ?: "Unknown model"
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
                    Text(
                        "Cleared by $solvedByName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
