package com.vibecheck.domain

object WordRules {
    private val validEnglishWordRegex = Regex("^[a-z]+(?:'[a-z]+)?$")

    fun normalize(word: String): String = word.trim().lowercase()

    fun isValidEnglishWord(word: String): Boolean = validEnglishWordRegex.matches(word)
}
