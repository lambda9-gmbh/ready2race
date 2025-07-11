package de.lambda9.ready2race.backend.app.competitionExecution.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import java.util.UUID

data class CompetitionMatchTeamParticipant(
    val competitionRegistrationId: UUID,
    val namedParticipantId: UUID,
    val namedParticipantName: String,
    val participantId: UUID,
    val firstName: String,
    val lastName: String,
    val year: Int,
    val gender: Gender,
    val external: Boolean?,
    val externalClubName: String?
)