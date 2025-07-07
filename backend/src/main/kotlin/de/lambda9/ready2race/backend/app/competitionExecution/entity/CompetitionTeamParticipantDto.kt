package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.UUID

data class CompetitionTeamParticipantDto(
    val participantId: UUID,
    val namedParticipantName: String,
    val firstname: String,
    val lastname: String,
    val year: Int?,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?,
)