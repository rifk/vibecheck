package com.vibecheck.data

import com.vibecheck.model.ModelMetadata
import vibe_check.composeapp.generated.resources.Res

interface ModelCatalogSource {
    suspend fun getCatalog(): Map<String, ModelMetadata>
}

class NoOpModelCatalogSource : ModelCatalogSource {
    override suspend fun getCatalog(): Map<String, ModelMetadata> = emptyMap()
}

class BundledModelCatalogSource(
    private val parser: ModelCatalogJsonParser = ModelCatalogJsonParser()
) : ModelCatalogSource {
    private var cachedCatalog: Map<String, ModelMetadata>? = null

    override suspend fun getCatalog(): Map<String, ModelMetadata> {
        cachedCatalog?.let { return it }

        val payload = readCatalogJson() ?: return emptyMap()
        val parsed = parser.parse(payload) ?: return emptyMap()
        cachedCatalog = parsed
        return parsed
    }

    private suspend fun readCatalogJson(): String? {
        val resourcePath = "files/models/model_info.json"
        val bytes = runCatching { Res.readBytes(resourcePath) }.getOrNull() ?: return null
        return bytes.decodeToString()
    }
}
