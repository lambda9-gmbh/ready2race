package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.*

data class FeeExport(
    val id: UUID,
    val name: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)