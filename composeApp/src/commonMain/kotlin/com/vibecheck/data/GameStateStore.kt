package com.vibecheck.data

import com.russhwolf.settings.Settings
import com.vibecheck.model.PersistedAppState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface GameStateStore {
    fun loadState(): PersistedAppState
    fun saveState(state: PersistedAppState)
}

class InMemoryGameStateStore(
    initialState: PersistedAppState = PersistedAppState()
) : GameStateStore {
    private var state: PersistedAppState = initialState

    override fun loadState(): PersistedAppState = state

    override fun saveState(state: PersistedAppState) {
        this.state = state
    }
}

class SafeGameStateStore(
    private val primary: GameStateStore,
    private val fallback: GameStateStore = InMemoryGameStateStore()
) : GameStateStore {
    override fun loadState(): PersistedAppState {
        return runCatching { primary.loadState() }
            .getOrElse { fallback.loadState() }
    }

    override fun saveState(state: PersistedAppState) {
        runCatching { primary.saveState(state) }
            .onFailure {
                fallback.saveState(state)
            }
    }
}

class SettingsGameStateStore(
    private val settings: Settings,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) : GameStateStore {
    override fun loadState(): PersistedAppState {
        val raw = settings.getStringOrNull(KEY_STATE) ?: return PersistedAppState()
        return runCatching { json.decodeFromString<PersistedAppState>(raw) }
            .getOrElse { PersistedAppState() }
    }

    override fun saveState(state: PersistedAppState) {
        settings.putString(KEY_STATE, json.encodeToString(state))
    }

    private companion object {
        const val KEY_STATE = "vibe_check_state"
    }
}
