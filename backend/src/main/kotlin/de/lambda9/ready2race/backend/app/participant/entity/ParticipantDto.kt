package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.*

data class ParticipantDto(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val phone: String?,
    val external: Boolean?,
    val externalClubName: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)