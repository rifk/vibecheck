package com.vibecheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibecheck.app.AppContainer
import com.vibecheck.app.GameController
import com.vibecheck.app.ModelUiState
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.round

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

    MaterialTheme {
        GameScreen(controller)
    }
}

@Composable
private fun GameScreen(controller: GameController) {
    val uiState = controller.uiState
    val coroutineScope = rememberCoroutineScope()
    var dateInput by remember(uiState.utcDate) { mutableStateOf(uiState.utcDate) }
    var dateInputError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Vibe Check", style = MaterialTheme.typography.headlineSmall)
        Text("UTC Date: ${uiState.utcDate}", style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { coroutineScope.launch { controller.loadPreviousDay() } },
                enabled = !uiState.isLoading
            ) {
                Text("Previous")
            }
            Button(
                onClick = { coroutineScope.launch { controller.loadToday() } },
                enabled = !uiState.isLoading
            ) {
                Text("Today")
            }
            Button(
                onClick = { coroutineScope.launch { controller.loadNextDay() } },
                enabled = !uiState.isLoading
            ) {
                Text("Next")
            }
        }

        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Load UTC date (YYYY-MM-DD)") },
            enabled = !uiState.isLoading
        )

        Button(
            onClick = {
                val parsedDate = runCatching { LocalDate.parse(dateInput.trim()) }.getOrNull()
                if (parsedDate == null) {
                    dateInputError = "Enter a valid UTC date in YYYY-MM-DD format."
                } else {
                    dateInputError = null
                    coroutineScope.launch {
                        controller.loadDate(parsedDate)
                    }
                }
            },
            enabled = !uiState.isLoading
        ) {
            Text("Load Date")
        }

        dateInputError?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        if (uiState.isLoading) {
            CircularProgressIndicator()
            return
        }

        if (!uiState.puzzleAvailable) {
            Text(uiState.message ?: "No puzzle available")
            return
        }

        Text("Model", style = MaterialTheme.typography.titleMedium)
        ModelSelector(
            models = uiState.availableModels,
            selectedModelId = uiState.selectedModelId,
            onSelect = controller::onModelSelected
        )

        OutlinedTextField(
            value = uiState.guessInput,
            onValueChange = controller::onGuessChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Guess a word") },
            enabled = !uiState.solved
        )

        Button(onClick = controller::submitGuess, enabled = !uiState.solved && uiState.guessInput.isNotBlank()) {
            Text("Submit")
        }

        if (uiState.solved) {
            Text("Solved by model: ${uiState.solvedByModelId}")
            uiState.solvedAnswer?.let { Text("Answer: $it") }
        }

        uiState.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            "Attempts (selected model): ${uiState.guessCountForSelectedModel}",
            style = MaterialTheme.typography.bodyMedium
        )
        uiState.bestRankForSelectedModel?.let { bestRank ->
            Text("Best rank so far: #$bestRank", style = MaterialTheme.typography.bodyMedium)
        }

        StatsCard(
            totalWins = uiState.stats.totalWins,
            currentStreak = uiState.stats.currentStreak,
            maxStreak = uiState.stats.maxStreak,
            winsByModel = uiState.stats.winsByModel,
            averages = uiState.stats.averageGuessesByModel
        )

        Text("Guesses", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uiState.guesses) { guess ->
                Text("${guess.guess} -> #${guess.rank}")
            }
        }
    }
}

@Composable
private fun ModelSelector(
    models: List<ModelUiState>,
    selectedModelId: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            val label = buildString {
                append(model.displayName)
                if (model.modelId == selectedModelId) append(" (selected)")
                if (model.locked) append(" (locked)")
            }
            Button(onClick = { onSelect(model.modelId) }, enabled = !model.locked) {
                Text(label)
            }
        }
    }
}

@Composable
private fun StatsCard(
    totalWins: Int,
    currentStreak: Int,
    maxStreak: Int,
    winsByModel: Map<String, Int>,
    averages: Map<String, Double>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Stats", style = MaterialTheme.typography.titleMedium)
            Text("Wins: $totalWins")
            Text("Current streak: $currentStreak")
            Text("Max streak: $maxStreak")

            if (winsByModel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Wins by model")
                winsByModel.forEach { (modelId, wins) ->
                    Text("$modelId: $wins")
                }
            }

            if (averages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Average guesses per model")
                averages.forEach { (modelId, avg) ->
                    val rounded = round(avg * 100.0) / 100.0
                    Text("$modelId: $rounded")
                }
            }
        }
    }
}
