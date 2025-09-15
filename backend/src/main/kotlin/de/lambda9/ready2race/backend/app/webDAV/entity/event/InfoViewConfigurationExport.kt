package de.lambda9.ready2race.backend.app.webDAV.entity.event

import de.lambda9.ready2race.backend.database.generated.enums.InfoViewType
import org.jooq.JSONB
import java.time.LocalDateTime
import java.util.*

data class InfoViewConfigurationExport(
    val id: UUID,
    val eventId: UUID,
    val viewType: InfoViewType,
    val displayDurationSeconds: Int?,
    val dataLimit: Int?,
    val filters: JSONB?,
    val sortOrder: Int?,
    val isActive: Boolean?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)