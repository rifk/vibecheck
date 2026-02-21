package com.vibecheck.data

import com.vibecheck.model.PersistedAppState
import com.vibecheck.model.PlayerStats
import kotlin.test.Test
import kotlin.test.assertEquals

class SafeGameStateStoreTest {
    @Test
    fun loadState_returnsFallback_whenPrimaryThrows() {
        val fallbackState = PersistedAppState(
            stats = PlayerStats(totalWins = 3)
        )
        val store = SafeGameStateStore(
            primary = ThrowingStore(),
            fallback = InMemoryGameStateStore(fallbackState)
        )

        val loaded = store.loadState()

        assertEquals(3, loaded.stats.totalWins)
    }

    @Test
    fun saveState_writesToFallback_whenPrimaryThrows() {
        val fallback = InMemoryGameStateStore()
        val store = SafeGameStateStore(
            primary = ThrowingStore(),
            fallback = fallback
        )
        val target = PersistedAppState(
            stats = PlayerStats(totalWins = 7)
        )

        store.saveState(target)

        assertEquals(7, fallback.loadState().stats.totalWins)
    }
}

private class ThrowingStore : GameStateStore {
    override fun loadState(): PersistedAppState {
        error("primary load failure")
    }

    override fun saveState(state: PersistedAppState) {
        error("primary save failure")
    }
}
