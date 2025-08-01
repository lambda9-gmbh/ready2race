package de.lambda9.ready2race.backend.app.eventInfo.entity

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.database.generated.enums.InfoViewType
import java.time.LocalDateTime
import java.util.*

data class InfoViewConfigurationDto(
    val id: UUID,
    val eventId: UUID,
    val viewType: InfoViewType,
    val displayDurationSeconds: Int,
    val dataLimit: Int,
    val filters: JsonNode?,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)