package de.lambda9.ready2race.backend.app.webDAV.entity

import java.time.LocalDateTime
import java.util.UUID

data class ParticipantExport(
    val id: UUID,
    val club: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int,
    val gender: String,  // Converting Gender enum to String for JSON
    val phone: String?,
    val external: Boolean?,
    val externalClubName: String?,
    val createdAt: LocalDateTime,
    val createdBy: UUID?,
    val updatedAt: LocalDateTime,
    val updatedBy: UUID?
)