package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.UUID

data class TeamParticipantDto(
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val year: Int,
    val gender: Gender,
    val external: Boolean,
    val externalClubName: String?,
    val roleId: UUID,
    val role: String,
    val currentStatus: ParticipantScanType?,
    val lastScanAt: LocalDateTime?,
)