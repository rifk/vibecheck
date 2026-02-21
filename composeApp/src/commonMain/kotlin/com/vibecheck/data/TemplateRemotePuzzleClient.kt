package com.vibecheck.data

import kotlinx.datetime.LocalDate

class TemplateRemotePuzzleClient(
    private val httpGet: suspend (url: String) -> String?,
    private val pathTemplate: String = "puzzles/{date}.json"
) : RemotePuzzleClient {
    override suspend fun fetchPuzzleJson(baseUrl: String, utcDate: LocalDate): String? {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val resolvedPath = pathTemplate
            .replace("{date}", utcDate.toString())
            .trimStart('/')
        val url = "$normalizedBaseUrl/$resolvedPath"
        return httpGet(url)
    }
}
