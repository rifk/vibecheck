package com.vibecheck.domain

interface GuessLexicon {
    fun canonicalize(input: String): String?
}

interface LoadableGuessLexicon : GuessLexicon {
    suspend fun ensureLoaded()
}

object NoOpGuessLexicon : GuessLexicon {
    override fun canonicalize(input: String): String? = null
}
