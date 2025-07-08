package de.lambda9.ready2race.backend.app.competitionExecution.entity

import java.util.*

data class CompetitionTeamNamedParticipantDto(
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val participants: List<CompetitionTeamParticipantDto>,
)