package com.vibecheck.data

import com.vibecheck.model.ModelMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ModelCatalogJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun parse(payload: String): Map<String, ModelMetadata>? {
        val dto = runCatching { json.decodeFromString<ModelCatalogDto>(payload) }.getOrNull() ?: return null
        return dto.toDomain()
    }
}

@Serializable
private data class ModelCatalogDto(
    val models: List<ModelMetadataDto>
) {
    fun toDomain(): Map<String, ModelMetadata>? {
        if (models.isEmpty()) return null

        val metadata = models.mapNotNull { it.toDomain() }
        if (metadata.size != models.size) return null

        val duplicateIds = metadata.groupBy { it.modelId }.any { (_, entries) -> entries.size > 1 }
        if (duplicateIds) return null

        return metadata.associateBy { it.modelId }
    }
}

@Serializable
private data class ModelMetadataDto(
    val modelId: String,
    val title: String,
    val description: String,
    val info: String
) {
    fun toDomain(): ModelMetadata? {
        val normalizedId = modelId.trim()
        val normalizedTitle = title.trim()
        val normalizedDescription = description.trim()
        val normalizedInfo = info.trim()

        if (
            normalizedId.isBlank() ||
            normalizedTitle.isBlank() ||
            normalizedDescription.isBlank() ||
            normalizedInfo.isBlank()
        ) {
            return null
        }

        return ModelMetadata(
            modelId = normalizedId,
            title = normalizedTitle,
            description = normalizedDescription,
            info = normalizedInfo
        )
    }
}
