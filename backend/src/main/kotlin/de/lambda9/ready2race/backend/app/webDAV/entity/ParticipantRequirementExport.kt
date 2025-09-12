package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.*

data class ParticipantRequirementExport(
    val id: UUID,
    val name: String,
    val description: String?,
    val optional: Boolean,
    val checkInApp: Boolean?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)