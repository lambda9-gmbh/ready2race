package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.UUID

data class CompetitionMatchTeamWithRegistration(
    val id: UUID,
    val competitionMatch: UUID,
    val startNumber: Int,
    val place: Int?,
    val competitionRegistration: UUID,
    val clubId: UUID,
    val clubName: String,
    val registrationName: String?,
    val teamNumber: Int?,
    val participants: List<CompetitionMatchTeamParticipant>,
    val deregistered: Boolean,
    val deregistrationReason: String?,
)