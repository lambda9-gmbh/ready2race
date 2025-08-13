package de.lambda9.ready2race.backend.app.participant.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.participantRequirement.entity.CheckedParticipantRequirement
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.*

data class ParticipantForEventDto(
    val id: UUID,
    val clubId: UUID,
    val clubName: String,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
    val participantRequirementsChecked: List<CheckedParticipantRequirement>?,
    val qrCodeId: String?,
    val namedParticipantIds: List<UUID>,
    val currentStatus: ParticipantScanType?,
    val lastScanAt: LocalDateTime?,
    val lastScanBy: AppUserNameDto?,
)