package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.appuser.entity.AppUserNameDto
import de.lambda9.ready2race.backend.app.participantTracking.entity.ParticipantScanType
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.time.LocalDateTime
import java.util.*

data class ParticipantForCompetitionRegistrationTeam(
    val id: UUID,
    val firstname: String,
    val lastname: String,
    val year: Int,
    val gender: Gender,
    val external: Boolean,
    val externalClubName: String?,
    val qrCodeId: String?,
    val participantRequirementsChecked: List<UUID>,
    val currentStatus: ParticipantScanType?,
    val lastScanAt: LocalDateTime?,
    val lastScanBy: AppUserNameDto?,
)