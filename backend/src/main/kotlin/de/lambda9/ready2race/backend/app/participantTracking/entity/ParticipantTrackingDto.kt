package de.lambda9.ready2race.backend.app.participantTracking.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.UUID

data class ParticipantTrackingDto(
    val id: UUID,
    val eventId: UUID,
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val year: Int,
    val gender: Gender,
    val clubId: UUID,
    val clubName: String,
    val external: Boolean,
    val externalClubName: String?,
    val scanType: ParticipantScanType?,
    val scannedAt: LocalDateTime?,
    val lastScanBy: AppUserNameDto?
)