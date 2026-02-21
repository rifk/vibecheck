package com.vibecheck.data

class PuzzleSourceFactory(
    private val bundledProvider: () -> PuzzleSource = { BundledPuzzleSource() },
    private val remoteProvider: ((baseUrl: String) -> PuzzleSource)? = null
) {
    fun create(config: SourceConfig): PuzzleSource {
        return when (config.mode) {
            SourceMode.BUNDLED -> bundledProvider()
            SourceMode.REMOTE -> {
                val baseUrl = config.remoteBaseUrl?.takeIf { it.isNotBlank() }
                if (baseUrl == null || remoteProvider == null) {
                    bundledProvider()
                } else {
                    remoteProvider.invoke(baseUrl)
                }
            }
        }
    }

    companion object {
        fun withRemoteClient(remoteClient: RemotePuzzleClient): PuzzleSourceFactory {
            return PuzzleSourceFactory(
                bundledProvider = { BundledPuzzleSource() },
                remoteProvider = { baseUrl -> RemotePuzzleSource(baseUrl, remoteClient) }
            )
        }

        fun withTemplateClient(
            httpGet: suspend (url: String) -> String?,
            pathTemplate: String = "puzzles/{date}.json"
        ): PuzzleSourceFactory {
            val remoteClient = TemplateRemotePuzzleClient(
                httpGet = httpGet,
                pathTemplate = pathTemplate
            )
            return withRemoteClient(remoteClient)
        }
    }
}
