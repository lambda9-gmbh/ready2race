package de.lambda9.ready2race.backend.app.participantTracking.entity

import java.util.*

data class TeamForScanOverviewDto(
    val competitionRegistrationId: UUID,
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionName: String,
    val clubId: UUID,
    val clubName: String,
    val teamName: String?,
    val participants: List<TeamParticipantDto>,
)