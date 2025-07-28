package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import de.lambda9.ready2race.backend.app.teamTracking.entity.TeamScanType
import java.time.LocalDateTime
import java.util.*

data class CompetitionRegistrationTeamDto(
    val id: UUID,
    val name: String?,
    val clubId: UUID,
    val clubName: String,
    val optionalFees: List<CompetitionRegistrationFeeDto>,
    val namedParticipants: List<CompetitionRegistrationNamedParticipantDto>,
    val updatedAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val currentStatus: TeamScanType?,
    val lastScanAt: LocalDateTime?,
    val scannedBy: UUID?,
)