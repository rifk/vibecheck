package com.vibecheck.data

import com.vibecheck.domain.LoadableGuessLexicon
import com.vibecheck.domain.WordRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import vibe_check.composeapp.generated.resources.Res

class BundledGuessLexicon(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val readBytes: suspend (String) -> ByteArray = { path -> Res.readBytes(path) },
    private val canonicalWordsPath: String = "files/lexicon/common_words_20k.txt",
    private val lemmaMapPath: String = "files/lexicon/lemma_map.json"
) : LoadableGuessLexicon {
    private val loadMutex = Mutex()
    private var loaded = false

    private var canonicalWords: Set<String> = emptySet()
    private var lemmaMap: Map<String, String> = emptyMap()

    override suspend fun ensureLoaded() {
        if (loaded) return

        loadMutex.withLock {
            if (loaded) return

            val words = loadCanonicalWords()
            val map = loadLemmaMap()
            canonicalWords = words
            lemmaMap = map.filterValues { it in words }
            loaded = true
        }
    }

    override fun canonicalize(input: String): String? {
        val normalized = WordRules.normalize(input)
        if (!WordRules.isValidEnglishWord(normalized)) {
            return null
        }

        if (normalized in canonicalWords) {
            return normalized
        }

        val mapped = lemmaMap[normalized] ?: return null
        return mapped.takeIf { it in canonicalWords }
    }

    private suspend fun loadCanonicalWords(): Set<String> {
        val payload = runCatching { readBytes(canonicalWordsPath).decodeToString() }
            .getOrElse { return emptySet() }
        return payload
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && WordRules.isValidEnglishWord(it) }
            .toSet()
    }

    private suspend fun loadLemmaMap(): Map<String, String> {
        val payload = runCatching { readBytes(lemmaMapPath).decodeToString() }
            .getOrElse { return emptyMap() }
        val decoded = runCatching { json.decodeFromString<Map<String, String>>(payload) }
            .getOrElse { return emptyMap() }
        return decoded.mapNotNull { (key, value) ->
            val normalizedKey = WordRules.normalize(key)
            val normalizedValue = WordRules.normalize(value)
            if (!WordRules.isValidEnglishWord(normalizedKey) || !WordRules.isValidEnglishWord(normalizedValue)) {
                null
            } else if (normalizedKey == normalizedValue) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }.toMap()
    }
}
