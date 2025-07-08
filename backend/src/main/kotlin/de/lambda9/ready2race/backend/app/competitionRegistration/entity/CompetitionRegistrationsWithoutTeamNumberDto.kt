package de.lambda9.ready2race.backend.app.competitionRegistration.entity

import java.util.UUID

data class CompetitionRegistrationsWithoutTeamNumberDto(
    val competitionId: UUID,
    val competitionIdentifier: String,
    val competitionName: String,
    val registrationId: UUID,
    val registrationClub: String,
    val registrationName: String?
)